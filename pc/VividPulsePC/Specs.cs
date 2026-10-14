using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using System.Management;
using Microsoft.Win32;

namespace VividPulsePC
{
    /// <summary>The PC's fixed specs, from Windows' management data (WMI) and the registry.</summary>
    static class Specs
    {
        public static JObject Read()
        {
            var cs = First("SELECT Manufacturer, Model, TotalPhysicalMemory FROM Win32_ComputerSystem");
            var os = First("SELECT Caption, BuildNumber, InstallDate FROM Win32_OperatingSystem");
            var cpu = First("SELECT Name, NumberOfCores, NumberOfLogicalProcessors, MaxClockSpeed, L2CacheSize, L3CacheSize, SocketDesignation FROM Win32_Processor");
            var board = First("SELECT Manufacturer, Product FROM Win32_BaseBoard");
            var bios = First("SELECT Manufacturer, SMBIOSBIOSVersion FROM Win32_BIOS");
            string displayVersion = null, ubr = null;
            try
            {
                using (var nt = Registry.LocalMachine.OpenSubKey(@"SOFTWARE\Microsoft\Windows NT\CurrentVersion"))
                {
                    displayVersion = nt?.GetValue("DisplayVersion") as string;
                    ubr = nt?.GetValue("UBR")?.ToString();
                }
            }
            catch (Exception) { }

            return new JObject
            {
                { "name", Environment.MachineName },
                { "maker", Str(cs, "Manufacturer") },
                { "model", Str(cs, "Model") },
                {
                    "os", new JObject
                    {
                        { "name", Str(os, "Caption") },
                        { "version", displayVersion },
                        { "build", Str(os, "BuildNumber") + (ubr != null ? "." + ubr : "") },
                        { "installed", Date(Str(os, "InstallDate")) },
                    }
                },
                {
                    "cpu", new JObject
                    {
                        { "name", Str(cpu, "Name")?.Trim() },
                        { "cores", Num(cpu, "NumberOfCores") },
                        { "threads", Num(cpu, "NumberOfLogicalProcessors") },
                        { "maxMhz", Num(cpu, "MaxClockSpeed") },
                        { "l2Kb", Num(cpu, "L2CacheSize") },
                        { "l3Kb", Num(cpu, "L3CacheSize") },
                        { "socket", Str(cpu, "SocketDesignation") },
                    }
                },
                { "gpus", Gpus() },
                { "memory", new JObject { { "totalBytes", Num(cs, "TotalPhysicalMemory") }, { "sticks", Sticks() } } },
                {
                    "board", new JObject
                    {
                        { "maker", Str(board, "Manufacturer") },
                        { "model", Str(board, "Product") },
                        { "bios", (Str(bios, "Manufacturer") + " " + Str(bios, "SMBIOSBIOSVersion")).Trim() },
                    }
                },
                { "drives", Drives() },
                { "volumes", Volumes() },
                { "monitors", Monitors() },
            };
        }

        static List<JObject> Gpus()
        {
            var vram = VramByName();
            return All("SELECT Name, AdapterRAM, DriverVersion, CurrentHorizontalResolution, CurrentVerticalResolution, CurrentRefreshRate FROM Win32_VideoController")
                .Where(g => !string.IsNullOrEmpty(Str(g, "Name")))
                .Select(g =>
                {
                    var name = Str(g, "Name");
                    object bytes = vram.TryGetValue(name, out long v) ? (object)v : Num(g, "AdapterRAM");
                    return new JObject
                    {
                        { "name", name },
                        { "vramBytes", bytes },
                        { "driver", Str(g, "DriverVersion") },
                        { "width", Num(g, "CurrentHorizontalResolution") },
                        { "height", Num(g, "CurrentVerticalResolution") },
                        { "refreshHz", Num(g, "CurrentRefreshRate") },
                    };
                }).ToList();
        }

        /// <summary>Graphics memory from the driver's registry entry: Windows' own count stops at 4 GB.</summary>
        static Dictionary<string, long> VramByName()
        {
            var result = new Dictionary<string, long>(StringComparer.OrdinalIgnoreCase);
            try
            {
                using (var cls = Registry.LocalMachine.OpenSubKey(@"SYSTEM\CurrentControlSet\Control\Class\{4d36e968-e325-11ce-bfc1-08002be10318}"))
                {
                    if (cls == null) return result;
                    foreach (var sub in cls.GetSubKeyNames())
                    {
                        try
                        {
                            using (var k = cls.OpenSubKey(sub))
                            {
                                var desc = k?.GetValue("DriverDesc") as string;
                                if (desc == null) continue;
                                var q = k.GetValue("HardwareInformation.qwMemorySize") ?? k.GetValue("HardwareInformation.MemorySize");
                                long bytes = q is long l ? l : q is int i ? (uint)i : q is byte[] b && b.Length >= 4 ? (b.Length >= 8 ? BitConverter.ToInt64(b, 0) : BitConverter.ToUInt32(b, 0)) : 0;
                                if (bytes > 0) result[desc] = bytes;
                            }
                        }
                        catch (Exception) { }
                    }
                }
            }
            catch (Exception) { }
            return result;
        }

        static readonly Dictionary<int, string> MemoryTypes = new Dictionary<int, string>
        {
            { 20, "DDR" }, { 21, "DDR2" }, { 24, "DDR3" }, { 26, "DDR4" }, { 30, "LPDDR4" }, { 34, "DDR5" }, { 35, "LPDDR5" },
        };

        static List<JObject> Sticks() =>
            All("SELECT Capacity, Speed, ConfiguredClockSpeed, SMBIOSMemoryType, Manufacturer, PartNumber, DeviceLocator FROM Win32_PhysicalMemory")
                .Select(m => new JObject
                {
                    { "bytes", Num(m, "Capacity") },
                    { "speedMts", Num(m, "Speed") },
                    { "configuredMts", Num(m, "ConfiguredClockSpeed") },
                    { "type", MemoryTypes.TryGetValue((int)(Num(m, "SMBIOSMemoryType") ?? 0), out var t) ? t : null },
                    { "maker", Str(m, "Manufacturer")?.Trim() },
                    { "part", Str(m, "PartNumber")?.Trim() },
                    { "slot", Str(m, "DeviceLocator") },
                }).ToList();

        static readonly Dictionary<int, string> MediaTypes = new Dictionary<int, string> { { 3, "HDD" }, { 4, "SSD" }, { 5, "SCM" } };
        static readonly Dictionary<int, string> BusTypes = new Dictionary<int, string>
        {
            { 1, "SCSI" }, { 3, "ATA" }, { 7, "USB" }, { 8, "RAID" }, { 10, "SAS" }, { 11, "SATA" }, { 12, "SD" }, { 17, "NVMe" },
        };

        static List<JObject> Drives()
        {
            // Windows' storage view knows SSD or HDD and the connection; match its disks to the drives by name.
            var physical = new Dictionary<string, ManagementBaseObject>(StringComparer.OrdinalIgnoreCase);
            foreach (var p in All("SELECT FriendlyName, MediaType, BusType FROM MSFT_PhysicalDisk", @"root\Microsoft\Windows\Storage"))
            {
                var n = Str(p, "FriendlyName");
                if (n != null) physical[n] = p;
            }
            return All("SELECT Model, Size, InterfaceType FROM Win32_DiskDrive").Select(d =>
            {
                var model = Str(d, "Model");
                physical.TryGetValue(model ?? "", out var p);
                return new JObject
                {
                    { "model", model },
                    { "bytes", Num(d, "Size") },
                    { "kind", p != null && MediaTypes.TryGetValue((int)(Num(p, "MediaType") ?? 0), out var k) ? k : null },
                    { "bus", p != null && BusTypes.TryGetValue((int)(Num(p, "BusType") ?? 0), out var b) ? b : Str(d, "InterfaceType") },
                };
            }).ToList();
        }

        static List<JObject> Volumes() =>
            All("SELECT DeviceID, VolumeName, Size, FreeSpace FROM Win32_LogicalDisk WHERE DriveType=3")
                .Select(v => new JObject
                {
                    { "letter", Str(v, "DeviceID") },
                    { "label", Str(v, "VolumeName") },
                    { "bytes", Num(v, "Size") },
                    { "freeBytes", Num(v, "FreeSpace") },
                }).ToList();

        static List<JObject> Monitors() =>
            All("SELECT UserFriendlyName, ManufacturerName FROM WmiMonitorID", @"root\wmi")
                .Select(m => new JObject { { "name", Chars(m, "UserFriendlyName") }, { "maker", Chars(m, "ManufacturerName") } })
                .Where(m => !string.IsNullOrEmpty(m.Get("name") as string))
                .ToList();

        static string Chars(ManagementBaseObject o, string prop)
        {
            try
            {
                return o[prop] is ushort[] codes ? new string(codes.Where(c => c != 0).Select(c => (char)c).ToArray()).Trim() : null;
            }
            catch (Exception) { return null; }
        }

        static string Date(string wmi)
        {
            if (string.IsNullOrEmpty(wmi) || wmi.Length < 8) return null;
            return wmi.Substring(0, 4) + "-" + wmi.Substring(4, 2) + "-" + wmi.Substring(6, 2);
        }

        public static List<ManagementBaseObject> All(string query, string scope = @"root\cimv2")
        {
            try
            {
                using (var searcher = new ManagementObjectSearcher(scope, query))
                {
                    return searcher.Get().Cast<ManagementBaseObject>().ToList();
                }
            }
            catch (Exception e)
            {
                Log.Write("WMI " + query + ": " + e.Message);
                return new List<ManagementBaseObject>();
            }
        }

        public static ManagementBaseObject First(string query, string scope = @"root\cimv2") => All(query, scope).FirstOrDefault();

        public static string Str(ManagementBaseObject o, string prop)
        {
            try { return o?[prop]?.ToString(); }
            catch (Exception) { return null; }
        }

        public static long? Num(ManagementBaseObject o, string prop)
        {
            try
            {
                var v = o?[prop];
                return v == null ? (long?)null : Convert.ToInt64(v, CultureInfo.InvariantCulture);
            }
            catch (Exception) { return null; }
        }
    }
}
