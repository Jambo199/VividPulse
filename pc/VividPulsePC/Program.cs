using System;
using System.IO;
using System.Linq;
using System.Net.Sockets;
using System.Threading;
using System.Windows.Forms;

namespace VividPulsePC
{
    static class Program
    {
        /// <summary>
        /// Vivid Pulse PC. With no arguments it opens its window and sits by the clock; "--startup"
        /// starts it quietly (as Windows does at sign-in); "--headless" answers the phone with no
        /// window (testing); "--once &lt;folder&gt;" writes everything it would send as JSON files and stops;
        /// "--update" updates it from the release page with no window, then starts the new one.
        /// </summary>
        [STAThread]
        static int Main(string[] args)
        {
            var flags = args.Select(a => a.ToLowerInvariant()).ToList();
            int once = flags.IndexOf("--once");
            if (once >= 0) return Once(once + 1 < args.Length ? args[once + 1] : "pc-out");
            bool headless = flags.Contains("--headless");
            bool quietly = flags.Contains("--startup");
            bool afterUpdate = flags.Contains("--after-update");
            if (flags.Contains("--update")) return UpdateNow(headless);

            using (var single = new Mutex(true, @"Local\VividPulsePC", out bool first))
            {
                if (!first && afterUpdate)
                {
                    // Just updated: the old version is closing.
                    try { first = single.WaitOne(TimeSpan.FromSeconds(20)); }
                    catch (AbandonedMutexException) { first = true; }
                }
                if (!first)
                {
                    // Already running: bring its window up instead.
                    try { EventWaitHandle.OpenExisting(@"Local\VividPulsePC.show").Set(); }
                    catch (Exception) { }
                    return 0;
                }
                var config = Config.Load();
                var live = new LiveSampler();
                live.Start();
                Steam.Network = live;
                var server = new Server(config, live);
                if (afterUpdate) ThreadPool.QueueUserWorkItem(_ => Updater.CleanUp());
                try
                {
                    // Just updated, the old version may let go of the port a moment late.
                    for (int attempt = 1; ; attempt++)
                    {
                        try
                        {
                            server.Start();
                            break;
                        }
                        catch (SocketException) when (afterUpdate && attempt < 15)
                        {
                            Thread.Sleep(1000);
                        }
                    }
                }
                catch (SocketException e)
                {
                    Log.Write("Couldn't open port " + Server.Port + ": " + e.Message);
                    if (!headless)
                    {
                        MessageBox.Show(
                            "Vivid Pulse PC couldn't start: something else is using port " + Server.Port + ".\n\nIf the older Vivid Pulse PC window (the black one) is still open, close it and try again.",
                            "Vivid Pulse PC", MessageBoxButtons.OK, MessageBoxIcon.Warning);
                    }
                    return 1;
                }
                if (headless)
                {
                    Thread.Sleep(Timeout.Infinite);
                    return 0;
                }
                Application.EnableVisualStyles();
                Application.SetCompatibleTextRenderingDefault(false);
                using (var show = new EventWaitHandle(false, EventResetMode.AutoReset, @"Local\VividPulsePC.show"))
                {
                    var tray = new Tray(config, server, showWindow: !quietly, updated: afterUpdate);
                    new Thread(() =>
                    {
                        while (true)
                        {
                            show.WaitOne();
                            tray.ShowWindowFromAnyThread();
                        }
                    }) { IsBackground = true }.Start();
                    Application.Run(tray);
                }
            }
            return 0;
        }

        /// <summary>Updates from the release page with no window: 0 updated (and the new one started), 2 already the latest, 1 failed.</summary>
        static int UpdateNow(bool headless)
        {
            try
            {
                var release = Updater.Check();
                if (release == null)
                {
                    Log.Write("Update: " + Updater.Version + " is the latest");
                    return 2;
                }
                var exe = Updater.Install(release, null);
                Updater.Restart(exe, headless ? "--headless" : "--startup");
                return 0;
            }
            catch (Exception e)
            {
                Log.Write("Update failed: " + e.Message);
                return 1;
            }
        }

        /// <summary>Everything the phone would be sent, as files (for the automated test).</summary>
        static int Once(string folder)
        {
            try
            {
                Directory.CreateDirectory(folder);
                var config = Config.Load();
                var live = new LiveSampler();
                live.Start();
                Steam.Network = live;
                var server = new Server(config, live);
                File.WriteAllText(Path.Combine(folder, "hello.json"), Json.Write(server.Hello()));
                File.WriteAllText(Path.Combine(folder, "specs.json"), Json.Write(Specs.Read()));
                File.WriteAllText(Path.Combine(folder, "steam.json"), Json.Write(Steam.Library(true)));
                Steam.Downloads();
                Thread.Sleep(2500);
                File.WriteAllText(Path.Combine(folder, "downloads.json"), Json.Write(Steam.Downloads()));
                File.WriteAllText(Path.Combine(folder, "live.json"), Json.Write(live.Latest));
                return 0;
            }
            catch (Exception e)
            {
                File.WriteAllText(Path.Combine(folder, "error.txt"), e.ToString());
                return 1;
            }
        }
    }
}
