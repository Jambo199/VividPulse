using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Text;
using System.Text.RegularExpressions;

namespace VividPulsePC
{
    /// <summary>The PC's network, for following a Steam download between Steam's own notes about it.</summary>
    interface INetwork
    {
        /// <summary>Bytes the PC received between two moments (UTC); null when not known.</summary>
        long? ReceivedBetween(DateTime fromUtc, DateTime toUtc);

        /// <summary>The PC's download speed over the last few seconds, in bytes a second; null when not known.</summary>
        long? DownRate { get; }
    }

    /// <summary>
    /// Steam's logs\content_log.txt. Steam notes each game's download state there the moment it
    /// changes, and its download speed about once a minute; its manifests are written less often.
    /// </summary>
    sealed class ContentLog
    {
        public sealed class Line
        {
            /// <summary>UTC.</summary>
            public DateTime At;
            public long Flags;
            public string Text;
        }

        /// <summary>Each game's latest state.</summary>
        public readonly Dictionary<int, Line> States = new Dictionary<int, Line>();

        /// <summary>Steam's latest download speed, in bytes a second, and when it was noted (UTC).</summary>
        public long? Rate;
        public DateTime RateAt;

        static readonly Regex StateLine = new Regex(@"^\[(\d{4}-\d\d-\d\d \d\d:\d\d:\d\d)\] AppID (\d+) state changed : (.*)$", RegexOptions.Multiline);
        static readonly Regex RateLine = new Regex(@"^\[(\d{4}-\d\d-\d\d \d\d:\d\d:\d\d)\] Current download rate: ([\d.]+) Mbps", RegexOptions.Multiline);

        // The state names Steam writes, as the StateFlags bits (EAppState).
        static readonly Dictionary<string, long> Names = new Dictionary<string, long>
        {
            { "uninstalled", 1 }, { "updaterequired", 2 }, { "fullyinstalled", 4 }, { "encrypted", 8 }, { "locked", 16 },
            { "filesmissing", 32 }, { "apprunning", 64 }, { "filescorrupt", 128 }, { "updaterunning", 256 }, { "updatepaused", 512 },
            { "updatestarted", 1024 }, { "uninstalling", 2048 }, { "backuprunning", 4096 }, { "reconfiguring", 65536 },
            { "validating", 131072 }, { "addingfiles", 262144 }, { "preallocating", 524288 }, { "downloading", 1048576 },
            { "staging", 2097152 }, { "committing", 4194304 }, { "updatestopping", 8388608 },
        };

        public static long Flags(string names)
        {
            long flags = 0;
            foreach (var part in names.Split(','))
            {
                if (Names.TryGetValue(part.Replace(" ", "").Trim().ToLowerInvariant(), out long bit)) flags |= bit;
            }
            return flags;
        }

        public static ContentLog Parse(string text)
        {
            var log = new ContentLog();
            foreach (Match m in StateLine.Matches(text))
            {
                if (!int.TryParse(m.Groups[2].Value, out int id) || !Time(m.Groups[1].Value, out var at)) continue;
                var names = m.Groups[3].Value.Trim().TrimEnd(',');
                log.States[id] = new Line { At = at, Flags = Flags(names), Text = names };
            }
            foreach (Match m in RateLine.Matches(text))
            {
                if (!Time(m.Groups[1].Value, out var at) || !double.TryParse(m.Groups[2].Value, NumberStyles.Float, CultureInfo.InvariantCulture, out double mbps)) continue;
                log.Rate = (long)(mbps * 1_000_000 / 8);
                log.RateAt = at;
            }
            return log;
        }

        // Steam writes the times in the PC's own time.
        static bool Time(string text, out DateTime utc)
        {
            bool ok = DateTime.TryParseExact(text, "yyyy-MM-dd HH:mm:ss", CultureInfo.InvariantCulture, DateTimeStyles.AssumeLocal, out var local);
            utc = ok ? local.ToUniversalTime() : default;
            return ok;
        }

        static readonly object Gate = new object();
        static string readPath;
        static long readLength = -1;
        static ContentLog read;

        /// <summary>The end of the log (it grows to megabytes), read again only when it has grown.</summary>
        public static ContentLog Read(string steamRoot)
        {
            var file = Path.Combine(steamRoot, "logs", "content_log.txt");
            try
            {
                if (!File.Exists(file)) return new ContentLog();
                lock (Gate)
                {
                    // Steam keeps it open: its size comes from the open file, not the folder listing.
                    using (var stream = new FileStream(file, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete))
                    {
                        if (read != null && readPath == file && readLength == stream.Length) return read;
                        long start = Math.Max(0, stream.Length - 256 * 1024);
                        stream.Seek(start, SeekOrigin.Begin);
                        var bytes = new byte[stream.Length - start];
                        int n = 0;
                        while (n < bytes.Length)
                        {
                            int got = stream.Read(bytes, n, bytes.Length - n);
                            if (got <= 0) break;
                            n += got;
                        }
                        read = Parse(Encoding.UTF8.GetString(bytes, 0, n));
                        readPath = file;
                        readLength = stream.Length;
                        return read;
                    }
                }
            }
            catch (Exception e) when (e is IOException || e is UnauthorizedAccessException) { return new ContentLog(); }
        }
    }

    /// <summary>
    /// Steam's downloads, followed as they happen. Steam writes a game's manifest (with how much
    /// it has downloaded) only now and then, so between those notes the game Steam is working on
    /// moves on with what the PC receives, and its speed is the PC's download speed.
    /// </summary>
    sealed class DownloadTracker
    {
        sealed class Seen
        {
            // The manifest's numbers last time, for its own speed.
            public long Done = -1, Total = -1, Bytes;
            public DateTime At;
            public double Rate;
            public DateTime MovedAt = DateTime.MinValue;
            // Bytes downloaded as shown: the manifest's, plus what has arrived since.
            public long Shown;
            public DateTime ActiveSince = DateTime.MinValue;
            public string NoteKey;
            public DateTime NotedAt = DateTime.MinValue;
        }

        sealed class Row
        {
            public Manifest M;
            public Seen S;
            public string State;
            public bool? Marked;
            public ContentLog.Line Line;
            public bool Moved;
        }

        readonly Dictionary<int, Seen> seen = new Dictionary<int, Seen>();
        readonly List<JObject> finished = new List<JObject>();

        /// <summary>Downloads that finished in the last day (the phone tells you about each once).</summary>
        public List<JObject> Finished()
        {
            lock (seen)
            {
                long dayAgo = Clock.Now() - 24L * 3600 * 1000;
                finished.RemoveAll(f => (long)f.Get("at") < dayAgo);
                return new List<JObject>(finished);
            }
        }

        public List<JObject> Read(SteamReader reader) => Read(reader.Manifests(), null, null, null);

        /// <param name="log">Steam's content log, or null.</param>
        /// <param name="updating">Whether Steam marks a game as updating right now (its registry flag); null when it doesn't say.</param>
        /// <param name="network">The PC's network, or null.</param>
        public List<JObject> Read(List<Manifest> manifests, ContentLog log, Func<int, bool?> updating, INetwork network)
        {
            var now = DateTime.UtcNow;
            var rows = new List<Row>();
            var result = new List<JObject>();
            lock (seen)
            {
                var current = new HashSet<int>();
                foreach (var m in manifests)
                {
                    var state = SteamReader.DownloadState(m);
                    if (state == null || SteamReader.IsTool(m.Name)) continue;
                    current.Add(m.AppId);
                    // The log is written the moment the state changes; the manifest maybe minutes later.
                    ContentLog.Line line = null;
                    if (log != null && log.States.TryGetValue(m.AppId, out var l) && l.At >= m.WrittenAt.AddSeconds(-1))
                    {
                        line = l;
                        // No longer needing an update: Steam is finishing off.
                        state = (l.Flags & (SteamReader.UpdateRequired | SteamReader.UpdateStarted)) == 0 && (l.Flags & SteamReader.FullyInstalled) != 0
                            ? "installing"
                            : SteamReader.State(l.Flags);
                    }
                    bool? marked = updating?.Invoke(m.AppId);
                    if (!seen.TryGetValue(m.AppId, out var s))
                    {
                        s = new Seen();
                        seen[m.AppId] = s;
                    }
                    long bytes = m.BytesDownloaded + m.BytesStaged;
                    bool moved = false;
                    if (s.Done < 0 || m.BytesToDownload != s.Total || m.BytesDownloaded < s.Done)
                    {
                        // First look, or a new download.
                        if (s.Done >= 0) moved = true;
                        s.Shown = m.BytesDownloaded;
                        s.Bytes = bytes;
                        s.At = now;
                        s.Rate = 0;
                    }
                    else
                    {
                        moved = m.BytesDownloaded != s.Done;
                        if (bytes > s.Bytes && (now - s.At).TotalSeconds > 0.5)
                        {
                            double rate = (bytes - s.Bytes) / (now - s.At).TotalSeconds;
                            s.Rate = s.Rate <= 0 ? rate : s.Rate * 0.5 + rate * 0.5;
                            s.Bytes = bytes;
                            s.At = now;
                            s.MovedAt = now;
                        }
                        else if ((now - s.MovedAt).TotalSeconds > 30)
                        {
                            s.Rate = 0;
                        }
                    }
                    s.Done = m.BytesDownloaded;
                    s.Total = m.BytesToDownload;
                    // Steam doesn't always mark the one it's working on: one it calls updating, or
                    // whose bytes are moving, is downloading.
                    if (state == "queued" && (marked == true || (now - s.MovedAt).TotalSeconds < 30)) state = "downloading";
                    rows.Add(new Row { M = m, S = s, State = state, Marked = marked, Line = line, Moved = moved });
                }

                // Steam downloads one game at a time; the PC's downloading goes to that one.
                var active = rows.Where(r => r.State == "downloading")
                    .OrderByDescending(r => r.Marked == true)
                    .ThenByDescending(r => r.Line != null)
                    .ThenByDescending(r => r.S.MovedAt)
                    .ThenByDescending(r => r.M.WrittenAt)
                    .FirstOrDefault();
                foreach (var r in rows)
                {
                    var m = r.M;
                    var s = r.S;
                    long rate = (long)s.Rate;
                    if (r == active)
                    {
                        if (s.ActiveSince == DateTime.MinValue) s.ActiveSince = now;
                        // What arrived since Steam last wrote it down (and since it started here).
                        var from = m.WrittenAt > s.ActiveSince ? m.WrittenAt : s.ActiveSince;
                        long? extra = network?.ReceivedBetween(from, now);
                        if (extra.HasValue)
                        {
                            long estimate = m.BytesDownloaded + extra.Value;
                            s.Shown = r.Moved ? estimate : Math.Max(s.Shown, estimate);
                        }
                        long? live = network?.DownRate;
                        if (live.HasValue) rate = live.Value;
                        else if (log?.Rate != null && (now - log.RateAt).TotalSeconds < 150) rate = Math.Max(rate, log.Rate.Value);
                    }
                    else
                    {
                        s.ActiveSince = DateTime.MinValue;
                        if (r.Moved) s.Shown = m.BytesDownloaded;
                        if (r.State != "downloading" && r.State != "installing") rate = 0;
                    }
                    s.Shown = Math.Max(s.Shown, m.BytesDownloaded);
                    if (m.BytesToDownload > 0) s.Shown = Math.Min(s.Shown, m.BytesToDownload);
                    Note(r, rate, now);
                    result.Add(new JObject
                    {
                        { "appId", m.AppId },
                        { "name", m.Name },
                        { "state", r.State },
                        { "bytesDone", s.Shown },
                        { "bytesTotal", m.BytesToDownload },
                        { "stagedDone", m.BytesStaged },
                        { "stagedTotal", m.BytesToStage },
                        { "bytesPerSec", rate },
                        { "sizeOnDisk", m.SizeOnDisk },
                    });
                }

                foreach (var gone in new List<int>(seen.Keys))
                {
                    if (current.Contains(gone)) continue;
                    seen.Remove(gone);
                    // Left the list and installed (not cancelled or uninstalled): finished.
                    var m = manifests.Find(x => x.AppId == gone);
                    if (m != null && SteamReader.IsInstalled(m))
                    {
                        finished.RemoveAll(f => (int)f.Get("appId") == gone);
                        finished.Add(new JObject { { "appId", gone }, { "name", m.Name }, { "at", Clock.Now() } });
                        Log.Write("Download finished: " + m.Name + " (" + gone + ")");
                    }
                }
            }
            // Downloading first, then installing, paused and queued.
            result.Sort((a, b) => Order(a.Get("state") as string).CompareTo(Order(b.Get("state") as string)));
            return result;
        }

        /// <summary>What Steam said about a download, in the log when it changes (for working out what went wrong).</summary>
        static void Note(Row r, long rate, DateTime now)
        {
            var m = r.M;
            var s = r.S;
            string key = r.State + "|" + m.StateFlags + "|" + r.Marked + "|" + r.Line?.Text;
            if (key == s.NoteKey && (!r.Moved || (now - s.NotedAt).TotalSeconds < 60)) return;
            s.NoteKey = key;
            s.NotedAt = now;
            Log.Write("Download " + m.Name + " (" + m.AppId + "): " + r.State + ", showing " + Gb(s.Shown) + " of " + Gb(m.BytesToDownload) + " GB at " + (rate / 1e6).ToString("0.0", CultureInfo.InvariantCulture) + " MB/s"
                + " | manifest: flags " + m.StateFlags + ", " + Gb(m.BytesDownloaded) + " GB downloaded, " + Gb(m.BytesStaged) + " of " + Gb(m.BytesToStage) + " GB staged, written " + m.WrittenAt.ToLocalTime().ToString("HH:mm:ss", CultureInfo.InvariantCulture)
                + " | updating flag: " + (r.Marked.HasValue ? r.Marked.Value ? "yes" : "no" : "none")
                + " | content log: " + (r.Line != null ? r.Line.Text + " at " + r.Line.At.ToLocalTime().ToString("HH:mm:ss", CultureInfo.InvariantCulture) : "nothing newer"));
        }

        static string Gb(long bytes) => (bytes / 1e9).ToString("0.00", CultureInfo.InvariantCulture);

        static int Order(string state) => state == "downloading" ? 0 : state == "installing" ? 1 : state == "paused" ? 2 : 3;
    }
}
