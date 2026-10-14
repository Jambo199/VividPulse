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
        /// window (testing); "--once &lt;folder&gt;" writes everything it would send as JSON files and stops.
        /// </summary>
        [STAThread]
        static int Main(string[] args)
        {
            var flags = args.Select(a => a.ToLowerInvariant()).ToList();
            int once = flags.IndexOf("--once");
            if (once >= 0) return Once(once + 1 < args.Length ? args[once + 1] : "pc-out");
            bool headless = flags.Contains("--headless");
            bool quietly = flags.Contains("--startup");

            using (var single = new Mutex(true, @"Local\VividPulsePC", out bool first))
            {
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
                var server = new Server(config, live);
                try
                {
                    server.Start();
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
                    var tray = new Tray(config, server, showWindow: !quietly);
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

        /// <summary>Everything the phone would be sent, as files (for the automated test).</summary>
        static int Once(string folder)
        {
            try
            {
                Directory.CreateDirectory(folder);
                var config = Config.Load();
                var live = new LiveSampler();
                live.Start();
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
