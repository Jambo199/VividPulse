using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using Microsoft.Win32;

namespace VividPulsePC
{
    /// <summary>The Steam install on this PC: its library, downloads, and starting games.</summary>
    static class Steam
    {
        static readonly object Gate = new object();
        static JObject library;
        static DateTime libraryAt;
        static readonly DownloadTracker Tracker = new DownloadTracker();

        /// <summary>The PC's network readings, for following downloads live (set when they start).</summary>
        public static INetwork Network;

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

        /// <summary>
        /// The library, kept until something in it changes (a game installed or removed, collections
        /// edited) or for half an hour; <paramref name="refresh"/> reads it again anyway.
        /// </summary>
        public static JObject Library(bool refresh)
        {
            lock (Gate)
            {
                var root = FindRoot();
                if (root == null)
                {
                    library = null;
                    return new JObject { { "installed", false }, { "games", new List<object>() }, { "collections", new List<object>() }, { "libraryAppIds", new List<object>() } };
                }
                var reader = new SteamReader(root);
                var manifests = reader.Manifests();
                var stamp = reader.Stamp(manifests);
                if (library != null && !refresh && stamp == library.Get("stamp") as string && DateTime.UtcNow - libraryAt < TimeSpan.FromMinutes(30)) return library;
                bool changed = library == null || stamp != library.Get("stamp") as string;
                library = reader.Library(manifests);
                libraryAt = DateTime.UtcNow;
                if (changed) Describe(library);
                return library;
            }
        }

        /// <summary>What the library holds, in the log (for when a count looks wrong).</summary>
        static void Describe(JObject lib)
        {
            var games = (List<JObject>)lib.Get("games");
            var shortcuts = (List<JObject>)lib.Get("shortcuts");
            var leftOut = (List<string>)lib.Get("leftOut");
            int installed = games.Count(g => (bool)g.Get("installed"));
            var downloading = games.Where(g => !(bool)g.Get("installed") && (long)g.Get("sizeBytes") > 0).Select(g => g.Get("name") as string).ToList();
            Log.Write("Steam library: " + installed + " games installed"
                + (downloading.Count > 0 ? ", " + downloading.Count + " on their first download (" + string.Join(", ", downloading) + ")" : "")
                + ", " + shortcuts.Count + " non-Steam games" + (shortcuts.Count > 0 ? " (" + string.Join(", ", shortcuts.Select(x => x.Get("name"))) + ")" : "")
                + (leftOut.Count > 0 ? "; Steam's own tools left out: " + string.Join(", ", leftOut) : ""));
        }

        public static JObject Downloads()
        {
            var root = FindRoot();
            if (root == null) return new JObject { { "at", Clock.Now() }, { "downloads", new List<JObject>() }, { "finished", Tracker.Finished() } };
            var reader = new SteamReader(root);
            var manifests = reader.Manifests();
            var list = Tracker.Read(manifests, ContentLog.Read(root), Updating, Network);
            return new JObject
            {
                { "at", Clock.Now() },
                { "downloads", list },
                { "finished", Tracker.Finished() },
                // Changes when the library does, so the phone knows to fetch it again.
                { "libraryStamp", reader.Stamp(manifests) },
            };
        }

        /// <summary>Whether Steam marks a game as being updated right now (HKCU\Software\Valve\Steam\Apps\id, "Updating").</summary>
        static bool? Updating(int appId)
        {
            try
            {
                using (var key = Registry.CurrentUser.OpenSubKey(@"Software\Valve\Steam\Apps\" + appId))
                {
                    return key?.GetValue("Updating") is int v ? v != 0 : (bool?)null;
                }
            }
            catch (Exception) { return null; }
        }

        public static void Launch(int appId) =>
            Process.Start(new ProcessStartInfo("steam://rungameid/" + appId) { UseShellExecute = true });

        /// <summary>Starts a non-Steam game through Steam (its game ID is the shortcut's ID, then 0x02000000).</summary>
        public static void LaunchShortcut(int id) =>
            Process.Start(new ProcessStartInfo("steam://rungameid/" + (((ulong)unchecked((uint)id) << 32) | 0x02000000UL)) { UseShellExecute = true });

        /// <summary>The picture you gave a non-Steam game in Steam, or null.</summary>
        public static string ShortcutArt(int id)
        {
            var root = FindRoot();
            if (root == null) return null;
            var reader = new SteamReader(root);
            var user = reader.User();
            long account = user != null ? SteamReader.AccountId(user.Get("steamId") as string) : 0;
            return account > 0 ? reader.ShortcutArt(account, unchecked((uint)id)) : null;
        }
    }

    static class Clock
    {
        static readonly DateTime Epoch = new DateTime(1970, 1, 1, 0, 0, 0, DateTimeKind.Utc);

        public static long Now() => (long)(DateTime.UtcNow - Epoch).TotalMilliseconds;
    }
}
