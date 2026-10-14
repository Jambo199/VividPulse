using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Runtime.InteropServices;
using System.Text.RegularExpressions;
using System.Threading;

namespace VividPulsePC
{
    /// <summary>
    /// How the PC is doing right now, sampled every second in the background so the phone's
    /// question gets an instant answer: CPU (total and per thread), clock, memory, GPU, network.
    /// </summary>
    sealed class LiveSampler : INetwork
    {
        volatile JObject latest;
        PerformanceCounter cpuTotal, cpuPerformance;
        List<PerformanceCounter> cpuCores = new List<PerformanceCounter>();
        List<PerformanceCounter> netDown = new List<PerformanceCounter>(), netUp = new List<PerformanceCounter>();
        DateTime netListedAt = DateTime.MinValue;
        long baseMhz;
        string nvidiaSmi;
        JObject gpu;
        int tick;

        // What the PC has received, added up a reading at a time, for the last 15 minutes.
        readonly object receivedGate = new object();
        readonly List<KeyValuePair<DateTime, double>> received = new List<KeyValuePair<DateTime, double>>();
        double receivedTotal;

        /// <summary>The latest readings (waiting a moment for the first ones just after starting).</summary>
        public JObject Latest
        {
            get
            {
                for (int i = 0; i < 40 && latest == null; i++) Thread.Sleep(100);
                return latest ?? new JObject { { "at", Clock.Now() }, { "playingAppId", Steam.RunningAppId() } };
            }
        }

        public void Start()
        {
            var thread = new Thread(Run) { IsBackground = true, Name = "Live readings", Priority = ThreadPriority.BelowNormal };
            thread.Start();
        }

        void Run()
        {
            try { Setup(); }
            catch (Exception e) { Log.Write("Live readings setup: " + e.Message); }
            while (true)
            {
                try { latest = Sample(); }
                catch (Exception e) { Log.Write("Live readings: " + e.Message); }
                Thread.Sleep(1000);
            }
        }

        void Setup()
        {
            baseMhz = Specs.Num(Specs.First("SELECT MaxClockSpeed FROM Win32_Processor"), "MaxClockSpeed") ?? 0;
            // "Processor Information" is what Task Manager shows; plain "Processor" on older Windows.
            if (PerformanceCounterCategory.Exists("Processor Information"))
            {
                var cat = new PerformanceCounterCategory("Processor Information");
                string counter = cat.CounterExists("% Processor Utility") ? "% Processor Utility" : "% Processor Time";
                cpuTotal = new PerformanceCounter("Processor Information", counter, "_Total", true);
                if (cat.CounterExists("% Processor Performance")) cpuPerformance = new PerformanceCounter("Processor Information", "% Processor Performance", "_Total", true);
                cpuCores = cat.GetInstanceNames()
                    .Where(n => Regex.IsMatch(n, @"^\d+,\d+$"))
                    .OrderBy(n => int.Parse(n.Split(',')[0], CultureInfo.InvariantCulture)).ThenBy(n => int.Parse(n.Split(',')[1], CultureInfo.InvariantCulture))
                    .Select(n => new PerformanceCounter("Processor Information", counter, n, true))
                    .ToList();
            }
            else
            {
                cpuTotal = new PerformanceCounter("Processor", "% Processor Time", "_Total", true);
                cpuCores = new PerformanceCounterCategory("Processor").GetInstanceNames()
                    .Where(n => n != "_Total").OrderBy(n => int.TryParse(n, out int x) ? x : 0)
                    .Select(n => new PerformanceCounter("Processor", "% Processor Time", n, true)).ToList();
            }
            nvidiaSmi = new[]
            {
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.System), "nvidia-smi.exe"),
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "NVIDIA Corporation", "NVSMI", "nvidia-smi.exe"),
            }.FirstOrDefault(File.Exists);
            // The first reading of a rate is always 0; take it now.
            Read(cpuTotal);
            Read(cpuPerformance);
            cpuCores.ForEach(c => Read(c));
        }

        JObject Sample()
        {
            tick++;
            if (DateTime.UtcNow - netListedAt > TimeSpan.FromMinutes(1)) ListNetwork();
            if (tick % 2 == 1 || gpu == null) gpu = ReadGpu();
            var mem = new MemoryStatus();
            GlobalMemoryStatusEx(mem);
            double? performance = Read(cpuPerformance);
            long down = (long)netDown.Sum(c => Read(c) ?? 0);
            AddReceived(down);
            return new JObject
            {
                { "at", Clock.Now() },
                { "cpuPercent", Clamp(Read(cpuTotal)) },
                { "cpuCores", cpuCores.Select(c => Clamp(Read(c)) ?? 0).ToList() },
                { "cpuMhz", performance.HasValue && baseMhz > 0 ? (object)(long)Math.Round(baseMhz * performance.Value / 100) : null },
                { "memoryUsedBytes", (long)(mem.ullTotalPhys - mem.ullAvailPhys) },
                { "memoryTotalBytes", (long)mem.ullTotalPhys },
                { "gpu", gpu },
                { "downBytesPerSec", down },
                { "upBytesPerSec", (long)netUp.Sum(c => Read(c) ?? 0) },
                { "uptimeSec", (long)(GetTickCount64() / 1000) },
                { "playingAppId", Steam.RunningAppId() },
            };
        }

        void AddReceived(long bytesPerSec)
        {
            var now = DateTime.UtcNow;
            lock (receivedGate)
            {
                if (received.Count > 0)
                {
                    double seconds = Math.Min(5, (now - received[received.Count - 1].Key).TotalSeconds);
                    receivedTotal += bytesPerSec * Math.Max(0, seconds);
                }
                received.Add(new KeyValuePair<DateTime, double>(now, receivedTotal));
                while (received.Count > 0 && now - received[0].Key > TimeSpan.FromMinutes(15)) received.RemoveAt(0);
            }
        }

        /// <summary>The running total at a moment, between the readings either side of it.</summary>
        double ReceivedAt(DateTime t)
        {
            if (t <= received[0].Key) return received[0].Value;
            var last = received[received.Count - 1];
            if (t >= last.Key) return last.Value;
            int i = received.FindIndex(r => r.Key >= t);
            var a = received[i - 1];
            var b = received[i];
            double part = (t - a.Key).TotalSeconds / Math.Max(0.001, (b.Key - a.Key).TotalSeconds);
            return a.Value + (b.Value - a.Value) * part;
        }

        public long? ReceivedBetween(DateTime fromUtc, DateTime toUtc)
        {
            lock (receivedGate)
            {
                if (received.Count < 2 || toUtc <= fromUtc) return received.Count < 2 ? (long?)null : 0;
                return (long)Math.Max(0, ReceivedAt(toUtc) - ReceivedAt(fromUtc));
            }
        }

        public long? DownRate
        {
            get
            {
                lock (receivedGate)
                {
                    if (received.Count < 2) return null;
                    var last = received[received.Count - 1];
                    if (DateTime.UtcNow - last.Key > TimeSpan.FromSeconds(10)) return null;
                    var from = last.Key - TimeSpan.FromSeconds(4);
                    double seconds = (last.Key - (from < received[0].Key ? received[0].Key : from)).TotalSeconds;
                    return seconds > 0.5 ? (long)((last.Value - ReceivedAt(from)) / seconds) : (long?)null;
                }
            }
        }

        void ListNetwork()
        {
            netListedAt = DateTime.UtcNow;
            try
            {
                var names = new PerformanceCounterCategory("Network Interface").GetInstanceNames();
                netDown = names.Select(n => new PerformanceCounter("Network Interface", "Bytes Received/sec", n, true)).ToList();
                netUp = names.Select(n => new PerformanceCounter("Network Interface", "Bytes Sent/sec", n, true)).ToList();
                netDown.ForEach(c => Read(c));
                netUp.ForEach(c => Read(c));
            }
            catch (Exception e) { Log.Write("Network counters: " + e.Message); }
        }

        /// <summary>NVIDIA cards through nvidia-smi (temperature, power, fan and all); others through Windows' GPU counters.</summary>
        JObject ReadGpu()
        {
            if (nvidiaSmi != null)
            {
                try
                {
                    var psi = new ProcessStartInfo(nvidiaSmi, "--query-gpu=name,temperature.gpu,utilization.gpu,memory.used,memory.total,power.draw,clocks.gr,fan.speed --format=csv,noheader,nounits")
                    {
                        UseShellExecute = false,
                        RedirectStandardOutput = true,
                        CreateNoWindow = true,
                    };
                    using (var p = Process.Start(psi))
                    {
                        string line = p.StandardOutput.ReadLine();
                        p.WaitForExit(3000);
                        if (!string.IsNullOrWhiteSpace(line))
                        {
                            var f = line.Split(',').Select(x => x.Trim()).ToArray();
                            return new JObject
                            {
                                { "name", f[0] },
                                { "tempC", N(f, 1) },
                                { "loadPercent", N(f, 2) },
                                { "vramUsedMb", N(f, 3) },
                                { "vramTotalMb", N(f, 4) },
                                { "powerW", N(f, 5) },
                                { "clockMhz", N(f, 6) },
                                { "fanPercent", N(f, 7) },
                            };
                        }
                    }
                }
                catch (Exception e) { Log.Write("nvidia-smi: " + e.Message); }
            }
            var engines = Specs.All("SELECT Name, UtilizationPercentage FROM Win32_PerfFormattedData_GPUPerformanceCounters_GPUEngine")
                .Where(e => (Specs.Str(e, "Name") ?? "").Contains("engtype_3D")).ToList();
            if (engines.Count == 0) return null;
            double load = Math.Min(100, engines.Sum(e => Specs.Num(e, "UtilizationPercentage") ?? 0));
            double used = Specs.All("SELECT DedicatedUsage FROM Win32_PerfFormattedData_GPUPerformanceCounters_GPUAdapterMemory").Sum(m => Specs.Num(m, "DedicatedUsage") ?? 0);
            return new JObject
            {
                { "name", null }, { "tempC", null }, { "loadPercent", load }, { "vramUsedMb", Math.Round(used / 1048576) },
                { "vramTotalMb", null }, { "powerW", null }, { "clockMhz", null }, { "fanPercent", null },
            };
        }

        static object N(string[] f, int i) =>
            i < f.Length && double.TryParse(f[i], NumberStyles.Float, CultureInfo.InvariantCulture, out double d) ? (object)d : null;

        static double? Read(PerformanceCounter c)
        {
            if (c == null) return null;
            try { return c.NextValue(); }
            catch (Exception) { return null; }
        }

        static double? Clamp(double? v) => v.HasValue ? Math.Round(Math.Max(0, Math.Min(100, v.Value)), 1) : (double?)null;

        [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Auto)]
        sealed class MemoryStatus
        {
            public uint dwLength = (uint)Marshal.SizeOf(typeof(MemoryStatus));
            public uint dwMemoryLoad;
            public ulong ullTotalPhys;
            public ulong ullAvailPhys;
            public ulong ullTotalPageFile;
            public ulong ullAvailPageFile;
            public ulong ullTotalVirtual;
            public ulong ullAvailVirtual;
            public ulong ullAvailExtendedVirtual;
        }

        [DllImport("kernel32.dll", CharSet = CharSet.Auto, SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        static extern bool GlobalMemoryStatusEx([In, Out] MemoryStatus buffer);

        [DllImport("kernel32.dll")]
        static extern ulong GetTickCount64();
    }
}
