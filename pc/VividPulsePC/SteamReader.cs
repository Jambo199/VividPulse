using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Text.RegularExpressions;

namespace VividPulsePC
{
    /// <summary>One game's appmanifest: installed, or being downloaded or updated.</summary>
    sealed class Manifest
    {
        public int AppId;
        public string Name;
        public string Library;
        public long SizeOnDisk;
        public long LastUpdated;
        public long StateFlags;
        public long BytesToDownload;
        public long BytesDownloaded;
        public long BytesToStage;
        public long BytesStaged;
    }

    /// <summary>
    /// Reads a Steam install's own files: libraries, installed games, playtime, collections and
    /// downloads. Only files: nothing here talks to Steam or needs it running.
    /// </summary>
    sealed class SteamReader
    {
        public const long FirstSteamId = 76561197960265728;

        // AppState StateFlags bits (Steam's EAppState).
        const long UpdateRequired = 2, UpdateRunning = 256, UpdatePaused = 512, UpdateStarted = 1024;
        const long AddingFiles = 262144, Preallocating = 524288, Downloading = 1048576, Staging = 2097152, Committing = 4194304;

        readonly string root;

        public SteamReader(string root) { this.root = root; }

        public string Root => root;

        public List<string> Libraries()
        {
            var list = new List<string> { root };
            var file = Path.Combine(root, "steamapps", "libraryfolders.vdf");
            if (File.Exists(file))
            {
                var folders = Vdf.Parse(File.ReadAllText(file)).Block("libraryfolders");
                if (folders != null)
                {
                    foreach (var v in folders.Values)
                    {
                        var path = (v as Dictionary<string, object>).Text("path");
                        if (!string.IsNullOrEmpty(path) && !list.Any(p => string.Equals(Norm(p), Norm(path), StringComparison.OrdinalIgnoreCase))) list.Add(path);
                    }
                }
            }
            return list;
        }

        static string Norm(string p) => p.Replace('/', '\\').TrimEnd('\\');

        /// <summary>Who's signed in to Steam (the most recent account).</summary>
        public JObject User()
        {
            var file = Path.Combine(root, "config", "loginusers.vdf");
            if (!File.Exists(file)) return null;
            var users = Vdf.Parse(File.ReadAllText(file)).Block("users");
            if (users == null) return null;
            JObject best = null;
            foreach (var kv in users)
            {
                if (!(kv.Value is Dictionary<string, object> u) || !long.TryParse(kv.Key, out _)) continue;
                bool recent = u.Text("MostRecent") == "1";
                if (best == null || recent)
                {
                    best = new JObject
                    {
                        { "steamId", kv.Key },
                        { "name", u.Text("PersonaName") },
                        { "account", u.Text("AccountName") },
                        { "recent", recent },
                    };
                }
            }
            return best;
        }

        public static long AccountId(string steamId) =>
            long.TryParse(steamId, out long id) && id > FirstSteamId ? id - FirstSteamId : 0;

        /// <summary>Minutes played and when last played, by app, from the account's localconfig.vdf.</summary>
        public Dictionary<int, long[]> Played(long accountId)
        {
            var result = new Dictionary<int, long[]>();
            var file = Path.Combine(root, "userdata", accountId.ToString(CultureInfo.InvariantCulture), "config", "localconfig.vdf");
            if (!File.Exists(file)) return result;
            var apps = Vdf.Parse(File.ReadAllText(file)).Block("UserLocalConfigStore", "Software", "Valve", "Steam", "apps");
            if (apps == null) return result;
            foreach (var kv in apps)
            {
                if (!int.TryParse(kv.Key, out int id) || !(kv.Value is Dictionary<string, object> a)) continue;
                long minutes = a.Number("Playtime"), last = a.Number("LastPlayed");
                if (minutes > 0 || last > 0) result[id] = new[] { minutes, last };
            }
            return result;
        }

        /// <summary>
        /// The account's collections (Favorites and ones you made), from the JSON file Steam syncs.
        /// A collection you fill by hand lists its games; a dynamic one (made from filters) doesn't.
        /// </summary>
        public List<JObject> Collections(long accountId)
        {
            var result = new List<JObject>();
            var file = Path.Combine(root, "userdata", accountId.ToString(CultureInfo.InvariantCulture), "config", "cloudstorage", "cloud-storage-namespace-1.json");
            if (!File.Exists(file)) return result;
            List<object> entries;
            try { entries = Json.Parse(File.ReadAllText(file)) as List<object>; }
            catch (FormatException) { return result; }
            if (entries == null) return result;
            foreach (var e in entries)
            {
                if (!(e is List<object> pair) || pair.Count < 2) continue;
                var key = pair[0] as string ?? "";
                if (!key.StartsWith("user-collections.", StringComparison.Ordinal) || !(pair[1] is Dictionary<string, object> data)) continue;
                if (data.TryGetValue("is_deleted", out var deleted) && deleted is bool b && b) continue;
                if (!data.TryGetValue("value", out var raw) || !(raw is string valueJson)) continue;
                Dictionary<string, object> c;
                try { c = Json.Parse(valueJson) as Dictionary<string, object>; }
                catch (FormatException) { continue; }
                if (c == null) continue;
                var removed = new HashSet<int>(Ints(c, "removed"));
                result.Add(new JObject
                {
                    { "id", c.TryGetValue("id", out var id) ? id as string : null },
                    { "name", c.TryGetValue("name", out var name) ? name as string : null },
                    { "appIds", Ints(c, "added").Where(a => !removed.Contains(a)).Distinct().ToList() },
                    { "dynamic", c.ContainsKey("filterSpec") && c["filterSpec"] != null },
                });
            }
            return result;
        }

        static IEnumerable<int> Ints(Dictionary<string, object> d, string key) =>
            d.TryGetValue(key, out var v) && v is List<object> list ? list.OfType<double>().Select(x => (int)x) : Enumerable.Empty<int>();

        /// <summary>Every app in the Steam library on this PC: the client keeps artwork for each one, named after its app ID.</summary>
        public List<int> LibraryAppIds()
        {
            var ids = new HashSet<int>();
            var dir = Path.Combine(root, "appcache", "librarycache");
            if (Directory.Exists(dir))
            {
                foreach (var entry in Directory.EnumerateFileSystemEntries(dir))
                {
                    var m = Regex.Match(Path.GetFileName(entry), @"^(\d+)");
                    if (m.Success && int.TryParse(m.Groups[1].Value, out int id)) ids.Add(id);
                }
            }
            return ids.OrderBy(x => x).ToList();
        }

        public List<Manifest> Manifests()
        {
            var list = new List<Manifest>();
            var seen = new HashSet<int>();
            foreach (var lib in Libraries())
            {
                var apps = Path.Combine(lib, "steamapps");
                if (!Directory.Exists(apps)) continue;
                foreach (var file in Directory.EnumerateFiles(apps, "appmanifest_*.acf"))
                {
                    Dictionary<string, object> s;
                    try { s = Vdf.Parse(File.ReadAllText(file)).Block("AppState"); }
                    catch (IOException) { continue; }
                    if (s == null || !int.TryParse(s.Text("appid"), out int id) || !seen.Add(id)) continue;
                    list.Add(new Manifest
                    {
                        AppId = id,
                        Name = s.Text("name"),
                        Library = lib,
                        SizeOnDisk = s.Number("SizeOnDisk"),
                        LastUpdated = s.Number("LastUpdated"),
                        StateFlags = s.Number("StateFlags"),
                        BytesToDownload = s.Number("BytesToDownload"),
                        BytesDownloaded = s.Number("BytesDownloaded"),
                        BytesToStage = s.Number("BytesToStage"),
                        BytesStaged = s.Number("BytesStaged"),
                    });
                }
            }
            return list;
        }

        /// <summary>Steam's own tools (runtimes, redistributables) aren't games.</summary>
        public static bool IsTool(string name) =>
            name != null && Regex.IsMatch(name, @"^(Steamworks Common Redistributables|Steam Linux Runtime|Proton|SteamVR)");

        /// <summary>The whole library, as the phone's PC tab shows it.</summary>
        public JObject Library()
        {
            var user = User();
            long account = user != null ? AccountId(user.Get("steamId") as string) : 0;
            var played = account > 0 ? Played(account) : new Dictionary<int, long[]>();
            var games = new Dictionary<int, JObject>();
            foreach (var m in Manifests())
            {
                if (IsTool(m.Name)) continue;
                played.TryGetValue(m.AppId, out var p);
                games[m.AppId] = Game(m.AppId, m.Name, true, m.SizeOnDisk, m.LastUpdated, m.Library, p);
            }
            foreach (var kv in played)
            {
                if (!games.ContainsKey(kv.Key)) games[kv.Key] = Game(kv.Key, null, false, 0, 0, null, kv.Value);
            }
            return new JObject
            {
                { "installed", true },
                { "user", user },
                { "libraries", Libraries() },
                { "games", games.Values.ToList() },
                { "collections", account > 0 ? Collections(account) : new List<JObject>() },
                { "libraryAppIds", LibraryAppIds() },
            };
        }

        static JObject Game(int id, string name, bool installed, long size, long updated, string library, long[] played) => new JObject
        {
            { "appId", id },
            { "name", name },
            { "installed", installed },
            { "sizeBytes", size },
            { "updated", updated },
            { "library", library },
            { "minutes", played != null ? played[0] : 0 },
            { "lastPlayed", played != null ? played[1] : 0 },
        };

        /// <summary>Fully installed and up to date.</summary>
        public static bool IsInstalled(Manifest m) => (m.StateFlags & 4) != 0 && DownloadState(m) == null;

        /// <summary>What a manifest says about a download: null when the game is up to date.</summary>
        public static string DownloadState(Manifest m)
        {
            bool pending = (m.StateFlags & (UpdateRequired | UpdateStarted)) != 0
                || (m.BytesToDownload > 0 && m.BytesDownloaded < m.BytesToDownload);
            if (!pending) return null;
            if ((m.StateFlags & UpdatePaused) != 0) return "paused";
            if ((m.StateFlags & (Staging | Committing | AddingFiles | Preallocating)) != 0) return "installing";
            if ((m.StateFlags & (UpdateRunning | Downloading)) != 0) return "downloading";
            return "queued";
        }
    }
}
