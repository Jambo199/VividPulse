using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using Microsoft.Win32;

namespace VividPulsePC
{
    /// <summary>The Steam install on this PC: its library (kept for a few minutes), downloads, and starting games.</summary>
    static class Steam
    {
        static readonly object Gate = new object();
        static JObject library;
        static DateTime libraryAt;
        static readonly DownloadTracker Tracker = new DownloadTracker();

        public static string FindRoot()
        {
            try
            {
                using (var key = Registry.CurrentUser.OpenSubKey(@"Software\Valve\Steam"))
                {
                    var path = key?.GetValue("SteamPath") as string;
                    if (string.IsNullOrEmpty(path)) return null;
                    path = path.Replace('/', '\\');
                    return Directory.Exists(path) ? path : null;
                }
            }
            catch (Exception) { return null; }
        }

        /// <summary>The Steam game being played now; 0 for none.</summary>
        public static int RunningAppId()
        {
            try
            {
                using (var key = Registry.CurrentUser.OpenSubKey(@"Software\Valve\Steam"))
                {
                    return key?.GetValue("RunningAppID") is int id ? id : 0;
                }
            }
            catch (Exception) { return 0; }
        }

        public static JObject Library(bool refresh)
        {
            lock (Gate)
            {
                if (library != null && !refresh && DateTime.UtcNow - libraryAt < TimeSpan.FromMinutes(5)) return library;
                var root = FindRoot();
                library = root == null
                    ? new JObject { { "installed", false }, { "games", new List<object>() }, { "collections", new List<object>() }, { "libraryAppIds", new List<object>() } }
                    : new SteamReader(root).Library();
                libraryAt = DateTime.UtcNow;
                return library;
            }
        }

        public static JObject Downloads()
        {
            var root = FindRoot();
            var list = root == null ? new List<JObject>() : Tracker.Read(new SteamReader(root));
            return new JObject { { "at", Clock.Now() }, { "downloads", list }, { "finished", Tracker.Finished() } };
        }

        public static void Launch(int appId) =>
            Process.Start(new ProcessStartInfo("steam://rungameid/" + appId) { UseShellExecute = true });
    }

    /// <summary>Steam's downloads, with how fast each is going (worked out from one look to the next).</summary>
    sealed class DownloadTracker
    {
        sealed class Seen
        {
            public long Bytes;
            public DateTime At;
            public double Rate;
            public DateTime MovedAt;
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

        public List<JObject> Read(SteamReader reader)
        {
            var now = DateTime.UtcNow;
            var result = new List<JObject>();
            lock (seen)
            {
                var current = new HashSet<int>();
                var manifests = reader.Manifests();
                foreach (var m in manifests)
                {
                    var state = SteamReader.DownloadState(m);
                    if (state == null || SteamReader.IsTool(m.Name)) continue;
                    current.Add(m.AppId);
                    long done = m.BytesDownloaded + m.BytesStaged;
                    if (!seen.TryGetValue(m.AppId, out var s))
                    {
                        s = new Seen { Bytes = done, At = now, MovedAt = DateTime.MinValue };
                        seen[m.AppId] = s;
                    }
                    else if (done > s.Bytes && (now - s.At).TotalSeconds > 0.5)
                    {
                        double rate = (done - s.Bytes) / (now - s.At).TotalSeconds;
                        s.Rate = s.Rate <= 0 ? rate : s.Rate * 0.5 + rate * 0.5;
                        s.Bytes = done;
                        s.At = now;
                        s.MovedAt = now;
                    }
                    else if ((now - s.MovedAt).TotalSeconds > 30)
                    {
                        s.Rate = 0;
                    }
                    // Steam only marks the one it's working on; any game whose bytes are moving is downloading.
                    if (state == "queued" && (now - s.MovedAt).TotalSeconds < 30) state = "downloading";
                    result.Add(new JObject
                    {
                        { "appId", m.AppId },
                        { "name", m.Name },
                        { "state", state },
                        { "bytesDone", m.BytesDownloaded },
                        { "bytesTotal", m.BytesToDownload },
                        { "stagedDone", m.BytesStaged },
                        { "stagedTotal", m.BytesToStage },
                        { "bytesPerSec", (long)s.Rate },
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
                    }
                }
            }
            // Downloading first, then installing, paused and queued.
            result.Sort((a, b) => Order(a.Get("state") as string).CompareTo(Order(b.Get("state") as string)));
            return result;
        }

        static int Order(string state) => state == "downloading" ? 0 : state == "installing" ? 1 : state == "paused" ? 2 : 3;
    }

    static class Clock
    {
        static readonly DateTime Epoch = new DateTime(1970, 1, 1, 0, 0, 0, DateTimeKind.Utc);

        public static long Now() => (long)(DateTime.UtcNow - Epoch).TotalMilliseconds;
    }
}
