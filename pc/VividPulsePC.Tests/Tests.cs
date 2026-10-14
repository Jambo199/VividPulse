using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text;
using System.Threading;

namespace VividPulsePC
{
    /// <summary>A pretend Steam install, read the way Vivid Pulse PC reads a real one.</summary>
    static class Tests
    {
        static int failures;

        static void Check(bool ok, string what)
        {
            Console.WriteLine((ok ? "ok   " : "FAIL ") + what);
            if (!ok) failures++;
        }

        static int Main()
        {
            var root = Path.Combine(Path.GetTempPath(), "vp-steam-" + Guid.NewGuid().ToString("N"));
            var lib2 = Path.Combine(Path.GetTempPath(), "vp-lib-" + Guid.NewGuid().ToString("N"));
            try
            {
                MakeSteam(root, lib2);
                JsonAndVdf();
                Library(root);
                Downloads(root);
                LiveDownloads(root);
            }
            finally
            {
                try { Directory.Delete(root, true); Directory.Delete(lib2, true); } catch (Exception) { }
            }
            Console.WriteLine(failures == 0 ? "\nAll checks passed" : "\n" + failures + " check(s) failed");
            return failures == 0 ? 0 : 1;
        }

        static void MakeSteam(string root, string lib2)
        {
            const string account = "39734273";
            Directory.CreateDirectory(Path.Combine(root, "steamapps"));
            Directory.CreateDirectory(Path.Combine(root, "config"));
            Directory.CreateDirectory(Path.Combine(lib2, "steamapps"));
            var user = Path.Combine(root, "userdata", account, "config");
            Directory.CreateDirectory(Path.Combine(user, "cloudstorage"));
            foreach (var id in new[] { "1091500", "271590", "570" }) Directory.CreateDirectory(Path.Combine(root, "appcache", "librarycache", id));
            File.WriteAllText(Path.Combine(root, "appcache", "librarycache", "730_library_600x900.jpg"), "x");
            string Esc(string p) => p.Replace("\\", "\\\\");
            File.WriteAllText(Path.Combine(root, "steamapps", "libraryfolders.vdf"),
                "\"libraryfolders\"\n{\n\t\"0\"\n\t{\n\t\t\"path\"\t\t\"" + Esc(root) + "\"\n\t}\n\t\"1\"\n\t{\n\t\t\"path\"\t\t\"" + Esc(lib2) + "\"\n\t\t\"apps\" { \"271590\" \"1\" }\n\t}\n}\n");
            void Manifest(string dir, int id, string name, long size, long flags, long toDownload = 0, long downloaded = 0) =>
                File.WriteAllText(Path.Combine(dir, "steamapps", "appmanifest_" + id + ".acf"),
                    "\"AppState\"\n{\n\t\"appid\"\t\t\"" + id + "\"\n\t\"name\"\t\t\"" + name + "\"\n\t\"StateFlags\"\t\t\"" + flags +
                    "\"\n\t\"SizeOnDisk\"\t\t\"" + size + "\"\n\t\"LastUpdated\"\t\t\"1759000000\"\n\t\"BytesToDownload\"\t\t\"" + toDownload +
                    "\"\n\t\"BytesDownloaded\"\t\t\"" + downloaded + "\"\n\t\"UserConfig\"\n\t{\n\t\t\"language\"\t\t\"english\"\n\t}\n}\n");
            Manifest(root, 1091500, "Cyberpunk 2077", 74512345678, 4);
            Manifest(root, 228980, "Steamworks Common Redistributables", 123456, 4);
            Manifest(root, 1808500, "ARC Raiders", 1000, 1026 | 256, 36_000_000_000, 9_000_000_000);
            Manifest(root, 730, "Counter-Strike 2", 38_000_000_000, 6, 1_200_000_000, 0);
            Manifest(lib2, 271590, "Grand Theft Auto V Legacy", 105000000000, 4);
            File.WriteAllText(Path.Combine(root, "config", "loginusers.vdf"),
                "\"users\"\n{\n\t\"76561198000000001\"\n\t{\n\t\t\"AccountName\"\t\t\"citester\"\n\t\t\"PersonaName\"\t\t\"CI \\\"Tester\\\"\"\n\t\t\"MostRecent\"\t\t\"1\"\n\t}\n}\n");
            File.WriteAllText(Path.Combine(user, "localconfig.vdf"),
                "\"UserLocalConfigStore\"\n{\n\t\"Software\"\n\t{\n\t\t\"valve\"\n\t\t{\n\t\t\t\"Steam\"\n\t\t\t{\n\t\t\t\t\"apps\"\n\t\t\t\t{\n" +
                "\t\t\t\t\t\"1091500\"\n\t\t\t\t\t{\n\t\t\t\t\t\t\"LastPlayed\"\t\t\"1760300000\"\n\t\t\t\t\t\t\"Playtime\"\t\t\"6120\"\n\t\t\t\t\t\t\"cloud\" { \"last_sync_state\" \"synchronized\" }\n\t\t\t\t\t}\n" +
                "\t\t\t\t\t\"570\"\n\t\t\t\t\t{\n\t\t\t\t\t\t\"Playtime\"\t\t\"120\"\n\t\t\t\t\t}\n\t\t\t\t}\n\t\t\t}\n\t\t}\n\t}\n}\n");
            string Entry(string key, string value) =>
                "[\"user-collections." + key + "\",{\"key\":\"user-collections." + key + "\",\"timestamp\":1700000000,\"version\":\"1\"," +
                (value == null ? "\"is_deleted\":true}]" : "\"value\":\"" + value.Replace("\\", "\\\\").Replace("\"", "\\\"") + "\"}]");
            File.WriteAllBytes(Path.Combine(user, "shortcuts.vdf"), Shortcuts(
                ("My Emulator", "\"C:\\Emu\\emu.exe\"", -1234567890, 0),
                ("Old Launcher", "\"C:\\Old\\launcher.exe\"", null, 0),
                ("Hidden Thing", "\"C:\\hidden.exe\"", 5, 1)));
            File.WriteAllText(Path.Combine(user, "cloudstorage", "cloud-storage-namespace-1.json"), "[" + string.Join(",",
                Entry("favorite", "{\"id\":\"favorite\",\"name\":\"Favorites\",\"added\":[1091500],\"removed\":[]}"),
                Entry("uc-AAAA", "{\"id\":\"uc-AAAA\",\"name\":\"Purchased\",\"added\":[1091500,271590,570,1808500],\"removed\":[570]}"),
                Entry("uc-BBBB", "{\"id\":\"uc-BBBB\",\"name\":\"Installed\",\"added\":[],\"removed\":[],\"filterSpec\":{\"nFormatVersion\":2}}"),
                Entry("uc-CCCC", null)) + "]");
        }

        /// <summary>A shortcuts.vdf the way Steam writes it (binary KeyValues).</summary>
        static byte[] Shortcuts(params (string name, string exe, int? appId, int hidden)[] items)
        {
            var ms = new MemoryStream();
            void Text(string t) { var b = Encoding.UTF8.GetBytes(t); ms.Write(b, 0, b.Length); ms.WriteByte(0); }
            void Str(string key, string value) { ms.WriteByte(1); Text(key); Text(value); }
            void Int(string key, int value) { ms.WriteByte(2); Text(key); ms.Write(BitConverter.GetBytes(value), 0, 4); }
            ms.WriteByte(0); Text("shortcuts");
            for (int i = 0; i < items.Length; i++)
            {
                ms.WriteByte(0); Text(i.ToString());
                if (items[i].appId.HasValue) Int("appid", items[i].appId.Value);
                Str("AppName", items[i].name);
                Str("Exe", items[i].exe);
                Int("IsHidden", items[i].hidden);
                Int("LastPlayTime", 1760000000);
                ms.WriteByte(0); Text("tags"); Str("0", "favorite"); ms.WriteByte(8);
                ms.WriteByte(8);
            }
            ms.WriteByte(8);
            ms.WriteByte(8);
            return ms.ToArray();
        }

        static void JsonAndVdf()
        {
            var o = new JObject { { "a", 1 }, { "b", "x\"y\n" }, { "c", new List<object> { 1.5, true, null } }, { "d", new JObject { { "e", 12345678901L } } } };
            var text = Json.Write(o);
            Check(text == "{\"a\":1,\"b\":\"x\\\"y\\n\",\"c\":[1.5,true,null],\"d\":{\"e\":12345678901}}", "JSON written: " + text);
            var back = Json.Parse(text) as Dictionary<string, object>;
            Check(back != null && (string)back["b"] == "x\"y\n" && (double)((Dictionary<string, object>)back["d"])["e"] == 12345678901d, "JSON read back");
            var v = Vdf.Parse("// comment\n\"A\" { \"Path\" \"C:\\\\Steam\" \"B\" { \"c\" \"1\" } }");
            Check(v.Block("a", "b").Text("C") == "1", "VDF keys ignore case");
            Check(v.Block("A").Text("path") == "C:\\Steam", "VDF escapes: " + v.Block("A").Text("path"));
        }

        static void Library(string root)
        {
            var reader = new SteamReader(root);
            var lib = reader.Library();
            Check(reader.Libraries().Count == 2, "two libraries");
            var user = lib.Get("user") as JObject;
            Check(user != null && (string)user.Get("name") == "CI \"Tester\"", "signed in: " + (user?.Get("name")));
            var games = ((List<JObject>)lib.Get("games")).ToDictionary(g => (int)g.Get("appId"));
            Check(games.ContainsKey(1091500) && (bool)games[1091500].Get("installed") && (long)games[1091500].Get("minutes") == 6120, "Cyberpunk: installed, 6120 minutes");
            Check(games.ContainsKey(271590) && (bool)games[271590].Get("installed"), "GTA V in the second library");
            Check(games.ContainsKey(570) && !(bool)games[570].Get("installed") && (long)games[570].Get("minutes") == 120, "Dota 2 played, not installed");
            Check(!games.ContainsKey(228980), "Steam's redistributables left out");
            Check(games.ContainsKey(1808500) && !(bool)games[1808500].Get("installed"), "ARC Raiders, on its first download, isn't installed yet");
            Check(games.ContainsKey(730) && (bool)games[730].Get("installed"), "Counter-Strike 2, waiting for an update, is installed");
            var shortcuts = (List<JObject>)lib.Get("shortcuts");
            Check(shortcuts.Count == 2 && (string)shortcuts[0].Get("name") == "My Emulator" && (int)shortcuts[0].Get("appId") == -1234567890,
                "non-Steam games: " + string.Join(", ", shortcuts.Select(x => x.Get("name") + " " + x.Get("appId"))));
            Check(shortcuts.Count == 2 && (string)shortcuts[1].Get("name") == "Old Launcher" && (int)shortcuts[1].Get("appId") < 0
                && unchecked((uint)(int)shortcuts[1].Get("appId")) == SteamReader.ShortcutId("\"C:\\Old\\launcher.exe\"", "Old Launcher"), "an old non-Steam game gets its worked-out ID");
            Directory.CreateDirectory(Path.Combine(root, "userdata", "39734273", "config", "grid"));
            File.WriteAllText(Path.Combine(root, "userdata", "39734273", "config", "grid", "3060399406p.png"), "png");
            Check(reader.ShortcutArt(39734273, unchecked((uint)-1234567890))?.EndsWith("3060399406p.png") == true, "a non-Steam game's picture");
            Check((string)lib.Get("stamp") == reader.Stamp(reader.Manifests()), "the library's stamp stays the same while nothing changes");
            var collections = ((List<JObject>)lib.Get("collections")).ToDictionary(c => (string)c.Get("name"));
            Check(collections.Count == 3, "three collections (deleted one left out)");
            Check(string.Join(",", (List<int>)collections["Purchased"].Get("appIds")) == "1091500,271590,1808500", "Purchased: " + string.Join(",", (List<int>)collections["Purchased"].Get("appIds")));
            Check((bool)collections["Installed"].Get("dynamic") && !(bool)collections["Purchased"].Get("dynamic"), "Installed is dynamic");
            Check(string.Join(",", (List<int>)lib.Get("libraryAppIds")) == "570,730,271590,1091500", "library app IDs");
            Console.WriteLine("     " + Json.Write(lib).Substring(0, 300) + "…");
        }

        static void Downloads(string root)
        {
            var reader = new SteamReader(root);
            var tracker = new DownloadTracker();
            var first = tracker.Read(reader);
            Check(first.Count == 2, "two downloads: " + string.Join(", ", first.Select(d => d.Get("name") + " " + d.Get("state"))));
            Check((string)first[0].Get("name") == "ARC Raiders" && (string)first[0].Get("state") == "downloading", "ARC Raiders downloading, first");
            Check((string)first[1].Get("state") == "queued", "Counter-Strike 2 update queued");
            // Bytes move: a speed appears.
            Thread.Sleep(1100);
            var path = Path.Combine(root, "steamapps", "appmanifest_1808500.acf");
            File.WriteAllText(path, File.ReadAllText(path).Replace("\"9000000000\"", "\"9500000000\""));
            var second = tracker.Read(reader);
            long rate = (long)second[0].Get("bytesPerSec");
            Check(rate > 100_000_000 && rate < 600_000_000, "download speed worked out: " + rate);
            // Finished: gone from the list.
            File.WriteAllText(path, File.ReadAllText(path).Replace("\"1282\"", "\"4\"").Replace("\"9500000000\"", "\"36000000000\""));
            var third = tracker.Read(reader);
            Check(third.Count == 1 && (int)third[0].Get("appId") == 730, "finished download leaves the list");
            var done = tracker.Finished();
            Check(done.Count == 1 && (int)done[0].Get("appId") == 1808500 && (string)done[0].Get("name") == "ARC Raiders", "and is listed as finished");
            // A cancelled download (its manifest gone) isn't "finished".
            File.Delete(Path.Combine(root, "steamapps", "appmanifest_730.acf"));
            tracker.Read(reader);
            Check(tracker.Finished().Count == 1, "a cancelled download isn't listed as finished");
            Check(SteamReader.DownloadState(new Manifest { StateFlags = 1026 | 512, BytesToDownload = 10, BytesDownloaded = 5 }) == "paused", "paused");
            Check(SteamReader.DownloadState(new Manifest { StateFlags = 4 }) == null, "installed and up to date: no download");
            Check(SteamReader.DownloadState(new Manifest { StateFlags = 4, BytesToDownload = 10, BytesDownloaded = 5 }) == null, "installed, with old numbers left in the manifest: no download");
            Check(SteamReader.State(1026 | 256 | 1048576 | 2097152) == "downloading", "downloading while unpacking is downloading");
            Check(SteamReader.State(1026 | 256 | 2097152) == "installing", "only unpacking is installing");
        }

        /// <summary>A network that receives 50 MB a second.</summary>
        sealed class FakeNetwork : INetwork
        {
            public long? ReceivedBetween(DateTime fromUtc, DateTime toUtc) => (long)(Math.Max(0, (toUtc - fromUtc).TotalSeconds) * 50_000_000);
            public long? DownRate => 50_000_000;
        }

        static void LiveDownloads(string root)
        {
            var apps = Path.Combine(root, "steamapps");
            void Manifest(int id, string name, long flags, long total, long done, DateTime written)
            {
                var file = Path.Combine(apps, "appmanifest_" + id + ".acf");
                File.WriteAllText(file, "\"AppState\"\n{\n\t\"appid\"\t\t\"" + id + "\"\n\t\"name\"\t\t\"" + name + "\"\n\t\"StateFlags\"\t\t\"" + flags
                    + "\"\n\t\"BytesToDownload\"\t\t\"" + total + "\"\n\t\"BytesDownloaded\"\t\t\"" + done + "\"\n}\n");
                File.SetLastWriteTimeUtc(file, written);
            }
            // Steam wrote ARC Raiders' manifest ten minutes ago, without saying it's running.
            Manifest(1808500, "ARC Raiders", 1026, 36_000_000_000, 9_000_000_000, DateTime.UtcNow.AddMinutes(-10));
            Manifest(730, "Counter-Strike 2", 6, 1_200_000_000, 0, DateTime.UtcNow.AddMinutes(-10));
            var reader = new SteamReader(root);
            var net = new FakeNetwork();

            var plain = new DownloadTracker().Read(reader.Manifests(), null, null, net);
            Check(plain.All(d => (string)d.Get("state") == "queued"), "the manifests alone: both look queued");

            var tracker = new DownloadTracker();
            Func<int, bool?> updating = id => id == 1808500;
            var first = tracker.Read(reader.Manifests(), null, updating, net);
            Check((string)first[0].Get("name") == "ARC Raiders" && (string)first[0].Get("state") == "downloading", "Steam marks ARC Raiders as updating: downloading");
            Check((long)first[0].Get("bytesDone") == 9_000_000_000 && (long)first[0].Get("bytesPerSec") == 50_000_000, "at the manifest's 9 GB, at the PC's speed");
            Check((string)first[1].Get("state") == "queued" && (long)first[1].Get("bytesPerSec") == 0, "Counter-Strike 2 still queued");
            Thread.Sleep(1200);
            long moving = (long)tracker.Read(reader.Manifests(), null, updating, net)[0].Get("bytesDone");
            Check(moving > 9_040_000_000 && moving < 9_300_000_000, "between Steam's notes it moves with what arrives: " + moving);
            Manifest(1808500, "ARC Raiders", 1026, 36_000_000_000, 9_500_000_000, DateTime.UtcNow);
            long noted = (long)tracker.Read(reader.Manifests(), null, updating, net)[0].Get("bytesDone");
            Check(noted >= 9_500_000_000 && noted < 9_600_000_000, "Steam's next note sets it right: " + noted);

            // Steam's log says it's paused, after the manifest was written.
            Directory.CreateDirectory(Path.Combine(root, "logs"));
            string at = DateTime.Now.AddSeconds(2).ToString("yyyy-MM-dd HH:mm:ss");
            string earlier = DateTime.Now.AddMinutes(-20).ToString("yyyy-MM-dd HH:mm:ss");
            File.WriteAllText(Path.Combine(root, "logs", "content_log.txt"),
                "[" + earlier + "] AppID 730 state changed : Fully Installed,Update Required,Update Running,Update Started,Downloading,\r\n" +
                "[" + earlier + "] Current download rate: 12.000 Mbps\r\n" +
                "[" + at + "] AppID 1808500 state changed : Update Required,Update Paused,Update Started,\r\n" +
                "[" + at + "] Current download rate: 92.519 Mbps\r\n");
            var log = ContentLog.Read(root);
            Check(log.Rate == 11_564_875 && log.States.Count == 2, "content log: " + log.States.Count + " games, " + log.Rate + " bytes a second");
            Check(log.States[1808500].Flags == (2 | 512 | 1024) && log.States[1808500].Text == "Update Required,Update Paused,Update Started", "content log state: " + log.States[1808500].Text);
            var paused = tracker.Read(reader.Manifests(), log, updating, net);
            var arc = paused.First(d => (int)d.Get("appId") == 1808500);
            var cs = paused.First(d => (int)d.Get("appId") == 730);
            Check((string)arc.Get("state") == "paused" && (long)arc.Get("bytesPerSec") == 0 && (long)arc.Get("bytesDone") >= 9_500_000_000, "the log's newer word wins: paused, kept at " + arc.Get("bytesDone"));
            Check((string)cs.Get("state") == "queued", "an old log line doesn't beat a newer manifest: " + cs.Get("state"));
        }
    }
}
