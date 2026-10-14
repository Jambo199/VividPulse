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
        /// <summary>When Steam last wrote the file (UTC).</summary>
        public DateTime WrittenAt;
    }

    /// <summary>
    /// Reads a Steam install's own files: libraries, installed games, playtime, collections and
    /// downloads. Only files: nothing here talks to Steam or needs it running.
    /// </summary>
    sealed class SteamReader
    {
        public const long FirstSteamId = 76561197960265728;

        // AppState StateFlags bits (Steam's EAppState).
        public const long UpdateRequired = 2, FullyInstalled = 4, UpdateRunning = 256, UpdatePaused = 512, UpdateStarted = 1024;
        public const long Validating = 131072, AddingFiles = 262144, Preallocating = 524288, Downloading = 1048576, Staging = 2097152, Committing = 4194304;

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
                        // Steam always writes it; a file without it is taken as installed.
                        StateFlags = s.Text("StateFlags") != null ? s.Number("StateFlags") : FullyInstalled,
                        BytesToDownload = s.Number("BytesToDownload"),
                        BytesDownloaded = s.Number("BytesDownloaded"),
                        BytesToStage = s.Number("BytesToStage"),
                        BytesStaged = s.Number("BytesStaged"),
                        WrittenAt = File.GetLastWriteTimeUtc(file),
                    });
                }
            }
            return list;
        }

        /// <summary>Steam's own tools (runtimes, redistributables) aren't games.</summary>
        public static bool IsTool(string name) =>
            name != null && Regex.IsMatch(name, @"^(Steamworks Common Redistributables|Steam Linux Runtime|Proton|SteamVR)");

        /// <summary>
        /// Games added to Steam that aren't from Steam ("Add a Non-Steam Game"), from the account's
        /// shortcuts.vdf. Hidden ones are left out, as Steam's library leaves them out.
        /// </summary>
        public List<JObject> Shortcuts(long accountId)
        {
            var result = new List<JObject>();
            var file = ShortcutsFile(accountId);
            if (!File.Exists(file)) return result;
            Dictionary<string, object> list;
            try { list = Vdf.ParseBinary(File.ReadAllBytes(file)).Block("shortcuts"); }
            catch (Exception e) when (e is FormatException || e is IOException || e is ArgumentException) { return result; }
            if (list == null) return result;
            foreach (var v in list.Values)
            {
                if (!(v is Dictionary<string, object> s)) continue;
                string name = s.Text("AppName"), exe = s.Text("Exe");
                if (string.IsNullOrWhiteSpace(name) || s.Number("IsHidden") != 0) continue;
                uint id = int.TryParse(s.Text("appid"), NumberStyles.Integer, CultureInfo.InvariantCulture, out int signed) && signed != 0
                    ? unchecked((uint)signed)
                    : ShortcutId(exe, name);
                result.Add(new JObject
                {
                    { "appId", unchecked((int)id) },
                    { "name", name.Trim() },
                    { "lastPlayed", s.Number("LastPlayTime") },
                });
            }
            return result;
        }

        string ShortcutsFile(long accountId) =>
            Path.Combine(root, "userdata", accountId.ToString(CultureInfo.InvariantCulture), "config", "shortcuts.vdf");

        /// <summary>The ID Steam gave non-Steam games before it saved one: a CRC of the program and name, top bit set.</summary>
        public static uint ShortcutId(string exe, string name)
        {
            uint crc = 0xFFFFFFFF;
            foreach (byte b in System.Text.Encoding.UTF8.GetBytes((exe ?? "") + name))
            {
                crc ^= b;
                for (int k = 0; k < 8; k++) crc = (crc & 1) != 0 ? (crc >> 1) ^ 0xEDB88320 : crc >> 1;
            }
            return ~crc | 0x80000000;
        }

        /// <summary>The picture you gave a non-Steam game in Steam (its tall cover), or null.</summary>
        public string ShortcutArt(long accountId, uint id)
        {
            var grid = Path.Combine(root, "userdata", accountId.ToString(CultureInfo.InvariantCulture), "config", "grid");
            foreach (var suffix in new[] { "p", "" })
            {
                foreach (var ext in new[] { ".png", ".jpg", ".jpeg", ".webp" })
                {
                    var file = Path.Combine(grid, id.ToString(CultureInfo.InvariantCulture) + suffix + ext);
                    if (File.Exists(file)) return file;
                }
            }
            return null;
        }

        /// <summary>
        /// Changes whenever what the library shows would: a game installed, finished or removed,
        /// collections edited, non-Steam games added, someone else signing in. Cheap to work out.
        /// </summary>
        public string Stamp(List<Manifest> manifests)
        {
            ulong hash = 14695981039346656037;
            void Add(string text)
            {
                foreach (char c in text)
                {
                    hash ^= c;
                    hash *= 1099511628211;
                }
            }
            foreach (var m in manifests.OrderBy(x => x.AppId)) Add(m.AppId + ((m.StateFlags & FullyInstalled) != 0 ? "i;" : "d;"));
            Add(Changed(Path.Combine(root, "config", "loginusers.vdf")));
            var userdata = Path.Combine(root, "userdata");
            if (Directory.Exists(userdata))
            {
                foreach (var account in Directory.EnumerateDirectories(userdata).OrderBy(x => x))
                {
                    Add(Changed(Path.Combine(account, "config", "cloudstorage", "cloud-storage-namespace-1.json")));
                    Add(Changed(Path.Combine(account, "config", "shortcuts.vdf")));
                }
            }
            return hash.ToString("x16", CultureInfo.InvariantCulture);
        }

        static string Changed(string file) => File.Exists(file) ? File.GetLastWriteTimeUtc(file).Ticks.ToString(CultureInfo.InvariantCulture) + ";" : "-;";

        /// <summary>The whole library, as the phone's PC tab shows it.</summary>
        public JObject Library() => Library(Manifests());

        public JObject Library(List<Manifest> manifests)
        {
            var user = User();
            long account = user != null ? AccountId(user.Get("steamId") as string) : 0;
            var played = account > 0 ? Played(account) : new Dictionary<int, long[]>();
            var games = new Dictionary<int, JObject>();
            var tools = new List<string>();
            foreach (var m in manifests)
            {
                if (IsTool(m.Name)) { tools.Add(m.Name); continue; }
                played.TryGetValue(m.AppId, out var p);
                // A game still on its first download isn't installed yet (an update doesn't count).
                games[m.AppId] = Game(m.AppId, m.Name, (m.StateFlags & FullyInstalled) != 0, m.SizeOnDisk, m.LastUpdated, m.Library, p);
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
                { "shortcuts", account > 0 ? Shortcuts(account) : new List<JObject>() },
                { "collections", account > 0 ? Collections(account) : new List<JObject>() },
                { "libraryAppIds", LibraryAppIds() },
                { "leftOut", tools },
                { "stamp", Stamp(manifests) },
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
        public static bool IsInstalled(Manifest m) => (m.StateFlags & FullyInstalled) != 0 && DownloadState(m) == null;

        /// <summary>What a manifest says about a download: null when the game is up to date.</summary>
        public static string DownloadState(Manifest m)
        {
            bool pending = (m.StateFlags & (UpdateRequired | UpdateStarted | UpdateRunning | UpdatePaused)) != 0
                || ((m.StateFlags & FullyInstalled) == 0 && m.BytesToDownload > 0 && m.BytesDownloaded < m.BytesToDownload);
            return pending ? State(m.StateFlags) : null;
        }

        /// <summary>A download's state from Steam's flags: downloading, installing, paused or queued.</summary>
        public static string State(long flags)
        {
            if ((flags & UpdatePaused) != 0) return "paused";
            // Steam unpacks while it downloads, so Downloading wins over Staging.
            if ((flags & Downloading) != 0) return "downloading";
            if ((flags & (Staging | Committing | AddingFiles | Preallocating | Validating)) != 0) return "installing";
            if ((flags & UpdateRunning) != 0) return "downloading";
            return "queued";
        }
    }
}
