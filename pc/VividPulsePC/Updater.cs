using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Net;
using System.Reflection;
using System.Security.Cryptography;
using System.Text;
using System.Threading;

namespace VividPulsePC
{
    /// <summary>A newer Vivid Pulse PC on the release page.</summary>
    sealed class Release
    {
        public int Build;
        public string Version;
        public string Zip;
        public string Sha256;
        public string Notes;
    }

    /// <summary>
    /// Keeps Vivid Pulse PC up to date from its release page. It looks for a newer build now and
    /// then (the build only goes up when the PC program itself changes, not with every phone app
    /// update), and when asked downloads it, checks it, swaps the program file and starts again.
    /// </summary>
    static class Updater
    {
        const string DefaultBase = "https://github.com/Jambo199/VividPulse/releases/download/vivid-pulse-latest/";

        /// <summary>The release page, for updating by hand.</summary>
        public const string Page = "https://github.com/Jambo199/VividPulse/releases/tag/vivid-pulse-latest";

        // Another address for the release (the automated test serves one on the PC itself).
        static string Base
        {
            get
            {
                var other = Environment.GetEnvironmentVariable("VIVIDPULSE_UPDATE_URL");
                return string.IsNullOrEmpty(other) ? DefaultBase : other.TrimEnd('/') + "/";
            }
        }

        /// <summary>This program's build: how many times the PC program has changed.</summary>
        public static int Build => Assembly.GetExecutingAssembly().GetName().Version.Minor;

        public static string Version => "2." + Build;

        /// <summary>A newer build, when the last look found one.</summary>
        public static Release Available { get; private set; }

        /// <summary>When it last looked (UTC); MinValue for not yet.</summary>
        public static DateTime CheckedAt { get; private set; } = DateTime.MinValue;

        /// <summary>A newer build turned up (on the checking thread).</summary>
        public static event Action<Release> Found;

        /// <summary>Looks shortly after starting, then every 6 hours (sooner again after a failed look).</summary>
        public static void StartChecking()
        {
            new Thread(() =>
            {
                Thread.Sleep(TimeSpan.FromSeconds(20));
                while (true)
                {
                    bool ok = true;
                    try { Check(); }
                    catch (Exception e)
                    {
                        ok = false;
                        Log.Write("Looking for an update: " + e.Message);
                    }
                    Thread.Sleep(ok ? TimeSpan.FromHours(6) : TimeSpan.FromMinutes(15));
                }
            }) { IsBackground = true, Name = "Updates", Priority = ThreadPriority.BelowNormal }.Start();
        }

        /// <summary>Looks now: the newer build, or null when this is the latest. Throws when the release can't be reached.</summary>
        public static Release Check()
        {
            var text = Encoding.UTF8.GetString(Download(Base + "pc-version.json", null)).TrimStart('﻿');
            if (!(Json.Parse(text) is Dictionary<string, object> d)) throw new FormatException("pc-version.json isn't what was expected");
            var r = new Release
            {
                Build = d.TryGetValue("build", out var b) && b is double n ? (int)n : 0,
                Version = d.TryGetValue("version", out var v) ? v as string : null,
                Zip = d.TryGetValue("zip", out var z) && z is string zip && zip.Length > 0 ? zip : "VividPulsePC.zip",
                Sha256 = d.TryGetValue("sha256", out var s) ? s as string : null,
                Notes = d.TryGetValue("notes", out var t) ? t as string : null,
            };
            if (r.Version == null) r.Version = "2." + r.Build;
            CheckedAt = DateTime.UtcNow;
            var was = Available;
            Available = r.Build > Build && !string.IsNullOrEmpty(r.Sha256) ? r : null;
            if (Available != null && (was == null || was.Build != r.Build))
            {
                Log.Write("Update available: " + r.Version + " (this is " + Version + ")" + (r.Notes != null ? ": " + r.Notes : ""));
                Found?.Invoke(r);
            }
            return Available;
        }

        static byte[] Download(string url, Action<int> percent)
        {
            ServicePointManager.SecurityProtocol |= SecurityProtocolType.Tls12;
            var request = (HttpWebRequest)WebRequest.Create(url);
            request.UserAgent = "VividPulsePC/" + Version;
            request.Timeout = 30000;
            request.ReadWriteTimeout = 30000;
            request.CachePolicy = new System.Net.Cache.RequestCachePolicy(System.Net.Cache.RequestCacheLevel.NoCacheNoStore);
            using (var response = (HttpWebResponse)request.GetResponse())
            using (var stream = response.GetResponseStream())
            using (var all = new MemoryStream())
            {
                long total = response.ContentLength;
                var buffer = new byte[81920];
                int read, last = -1;
                while ((read = stream.Read(buffer, 0, buffer.Length)) > 0)
                {
                    all.Write(buffer, 0, read);
                    if (percent != null && total > 0)
                    {
                        int now = (int)(all.Length * 100 / total);
                        if (now != last) percent(last = now);
                    }
                }
                return all.ToArray();
            }
        }

        static string Folder => Path.GetDirectoryName(Assembly.GetExecutingAssembly().Location);

        static string OldCopy => Path.Combine(Folder, "VividPulsePC.old.exe");

        /// <summary>
        /// Downloads <paramref name="r"/>, checks it against the release's checksum and puts it in
        /// place of this program (which keeps running until it closes); returns the program file to
        /// start. Throws when it can't, leaving the program as it was.
        /// </summary>
        public static string Install(Release r, Action<int> percent)
        {
            var bytes = Download(Base + r.Zip, percent);
            string sha;
            using (var hash = SHA256.Create()) sha = BitConverter.ToString(hash.ComputeHash(bytes)).Replace("-", "").ToLowerInvariant();
            if (!sha.Equals(r.Sha256.Trim(), StringComparison.OrdinalIgnoreCase)) throw new InvalidDataException("the download didn't match the release's checksum");

            // The new files, unpacked next to this one first.
            var exe = Assembly.GetExecutingAssembly().Location;
            var unpacked = new Dictionary<string, byte[]>(StringComparer.OrdinalIgnoreCase);
            using (var archive = new ZipArchive(new MemoryStream(bytes), ZipArchiveMode.Read))
            {
                foreach (var entry in archive.Entries)
                {
                    if (entry.Name != "VividPulsePC.exe" && entry.Name != "VividPulsePC.exe.config" && entry.Name != "README.txt") continue;
                    using (var from = entry.Open())
                    using (var to = new MemoryStream())
                    {
                        from.CopyTo(to);
                        unpacked[entry.Name] = to.ToArray();
                    }
                }
            }
            if (!unpacked.TryGetValue("VividPulsePC.exe", out var program) || program.Length < 10000) throw new InvalidDataException("the download has no program in it");
            var fresh = exe + ".new";
            File.WriteAllBytes(fresh, program);

            // A running program's file can be renamed (not overwritten): this one steps aside.
            try
            {
                if (File.Exists(OldCopy)) File.Delete(OldCopy);
            }
            catch (Exception)
            {
                File.Delete(fresh);
                throw new IOException("the copy left by the last update is still in use; try again in a moment");
            }
            File.Move(exe, OldCopy);
            try
            {
                File.Move(fresh, exe);
            }
            catch (Exception)
            {
                File.Move(OldCopy, exe);
                try { File.Delete(fresh); } catch (Exception) { }
                throw;
            }
            foreach (var name in new[] { "VividPulsePC.exe.config", "README.txt" })
            {
                if (!unpacked.TryGetValue(name, out var data)) continue;
                var to = name == "README.txt" ? Path.Combine(Folder, name) : exe + ".config";
                try { File.WriteAllBytes(to, data); }
                catch (Exception e) { Log.Write("Update: couldn't write " + name + ": " + e.Message); }
            }
            Log.Write("Updated the program to " + r.Version + " (was " + Version + "); starting it");
            return exe;
        }

        /// <summary>Starts the new program; it waits for this one to close.</summary>
        public static void Restart(string exe, string arguments) =>
            Process.Start(new ProcessStartInfo(exe, ("--after-update " + arguments).Trim()) { UseShellExecute = false, WorkingDirectory = Path.GetDirectoryName(exe) });

        /// <summary>After an update: the old program file goes once it has closed.</summary>
        public static void CleanUp()
        {
            for (int i = 0; i < 40 && File.Exists(OldCopy); i++)
            {
                try { File.Delete(OldCopy); }
                catch (Exception) { Thread.Sleep(500); }
            }
            Log.Write("Vivid Pulse PC " + Version + " started after updating" + (File.Exists(OldCopy) ? " (the old copy is still there)" : ""));
        }
    }
}
