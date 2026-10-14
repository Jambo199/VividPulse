using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
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
            File.WriteAllText(Path.Combine(user, "cloudstorage", "cloud-storage-namespace-1.json"), "[" + string.Join(",",
                Entry("favorite", "{\"id\":\"favorite\",\"name\":\"Favorites\",\"added\":[1091500],\"removed\":[]}"),
                Entry("uc-AAAA", "{\"id\":\"uc-AAAA\",\"name\":\"Purchased\",\"added\":[1091500,271590,570,1808500],\"removed\":[570]}"),
                Entry("uc-BBBB", "{\"id\":\"uc-BBBB\",\"name\":\"Installed\",\"added\":[],\"removed\":[],\"filterSpec\":{\"nFormatVersion\":2}}"),
                Entry("uc-CCCC", null)) + "]");
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
        }
    }
}
