using System;
using System.Collections.Generic;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;

namespace VividPulsePC
{
    /// <summary>
    /// Answers Vivid Pulse on the phone over the home network: plain HTTP on port 47800 (every
    /// answer but /hello needs the pairing code), and "who's there?" broadcasts on UDP 47801.
    /// </summary>
    sealed class Server
    {
        public const int Port = 47800;
        public const int DiscoveryPort = 47801;
        public const string Version = "2.0";

        readonly Config config;
        readonly LiveSampler live;
        readonly object specsGate = new object();
        JObject specs;
        TcpListener tcp;
        UdpClient udp;

        /// <summary>When a phone with the right code last asked something.</summary>
        public DateTime LastPhoneAt { get; private set; } = DateTime.MinValue;

        /// <summary>A phone with the right code asked for the first time since the program started.</summary>
        public event Action<string> PhoneConnected;

        readonly HashSet<string> phones = new HashSet<string>();

        public Server(Config config, LiveSampler live)
        {
            this.config = config;
            this.live = live;
        }

        public JObject Hello() => new JObject
        {
            { "app", "VividPulsePC" },
            { "version", Version },
            { "name", Environment.MachineName },
            { "port", Port },
            { "features", new[] { "downloads", "launch" } },
        };

        public JObject SpecsNow()
        {
            lock (specsGate) return specs ?? (specs = Specs.Read());
        }

        /// <summary>Starts answering; throws when port 47800 is taken (another copy running).</summary>
        public void Start()
        {
            tcp = new TcpListener(IPAddress.Any, Port);
            tcp.Start();
            new Thread(AcceptLoop) { IsBackground = true, Name = "Phone requests" }.Start();
            try
            {
                udp = new UdpClient(DiscoveryPort) { EnableBroadcast = true };
                new Thread(DiscoveryLoop) { IsBackground = true, Name = "Finding" }.Start();
            }
            catch (SocketException e)
            {
                Log.Write("Discovery port " + DiscoveryPort + " is busy (" + e.Message + "); the phone can still use the address");
            }
            // Keep an eye on Steam's downloads even while no phone is asking, so a download that
            // finishes while the phone is asleep is still reported as finished later.
            new Thread(() =>
            {
                while (true)
                {
                    try { Steam.Downloads(); }
                    catch (Exception e) { Log.Write("Downloads: " + e.Message); }
                    Thread.Sleep(20000);
                }
            }) { IsBackground = true, Name = "Steam downloads", Priority = ThreadPriority.BelowNormal }.Start();
            // Read the specs now, so the phone's first question is answered at once.
            ThreadPool.QueueUserWorkItem(_ => { try { SpecsNow(); } catch (Exception e) { Log.Write("Specs: " + e.Message); } });
            Log.Write("Vivid Pulse PC " + Version + " listening on port " + Port);
        }

        void DiscoveryLoop()
        {
            var hello = Encoding.UTF8.GetBytes(Json.Write(Hello()));
            while (true)
            {
                try
                {
                    var from = new IPEndPoint(IPAddress.Any, 0);
                    var bytes = udp.Receive(ref from);
                    if (Encoding.ASCII.GetString(bytes) == "VIVIDPULSE_DISCOVER") udp.Send(hello, hello.Length, from);
                }
                catch (Exception e)
                {
                    Log.Write("Discovery: " + e.Message);
                    Thread.Sleep(1000);
                }
            }
        }

        void AcceptLoop()
        {
            while (true)
            {
                try
                {
                    var client = tcp.AcceptTcpClient();
                    ThreadPool.QueueUserWorkItem(_ => Handle(client));
                }
                catch (Exception e)
                {
                    Log.Write("Accepting: " + e.Message);
                    Thread.Sleep(500);
                }
            }
        }

        void Handle(TcpClient client)
        {
            using (client)
            {
                string from = "?";
                try
                {
                    from = (client.Client.RemoteEndPoint as IPEndPoint)?.Address.ToString() ?? "?";
                    client.ReceiveTimeout = 5000;
                    client.SendTimeout = 15000;
                    var stream = client.GetStream();
                    var reader = new StreamReader(stream, Encoding.ASCII, false, 1024, true);
                    string request = reader.ReadLine();
                    // A connection that sends nothing (a port check): nothing to answer.
                    if (string.IsNullOrEmpty(request)) return;
                    string code = null;
                    while (true)
                    {
                        string line = reader.ReadLine();
                        if (string.IsNullOrEmpty(line)) break;
                        var m = Regex.Match(line, @"^X-Pulse-Code:\s*(\d+)", RegexOptions.IgnoreCase);
                        if (m.Success) code = m.Groups[1].Value;
                    }
                    var parts = request.Split(' ');
                    string path = parts.Length >= 2 ? parts[1] : "/";
                    string route = path.Split('?')[0];
                    string query = path.Contains("?") ? path.Substring(path.IndexOf('?') + 1) : "";

                    if (route == "/hello") { Respond(stream, 200, Hello()); return; }
                    if (code != config.Code)
                    {
                        Log.Write("Wrong code from " + from);
                        Respond(stream, 401, new JObject { { "error", "Wrong code" } });
                        return;
                    }
                    LastPhoneAt = DateTime.UtcNow;
                    bool first;
                    lock (phones) first = phones.Add(from);
                    if (first) PhoneConnected?.Invoke(from);

                    switch (route)
                    {
                        case "/specs":
                            Respond(stream, 200, SpecsNow());
                            break;
                        case "/live":
                            Respond(stream, 200, live.Latest);
                            break;
                        case "/steam":
                            Respond(stream, 200, Steam.Library(query.Contains("refresh=1")));
                            break;
                        case "/downloads":
                            Respond(stream, 200, Steam.Downloads());
                            break;
                        case "/launch":
                            var app = Regex.Match(query, @"appid=(\d+)");
                            if (!app.Success) { Respond(stream, 404, new JObject { { "error", "No game" } }); break; }
                            try
                            {
                                Steam.Launch(int.Parse(app.Groups[1].Value));
                                Log.Write("Starting game " + app.Groups[1].Value + " (asked from " + from + ")");
                                Respond(stream, 200, new JObject { { "started", int.Parse(app.Groups[1].Value) } });
                            }
                            catch (Exception e)
                            {
                                Log.Write("Couldn't start game " + app.Groups[1].Value + ": " + e.Message);
                                Respond(stream, 500, new JObject { { "error", "Steam couldn't start it" } });
                            }
                            break;
                        default:
                            Respond(stream, 404, new JObject { { "error", "Not found" } });
                            break;
                    }
                }
                catch (Exception e)
                {
                    Log.Write("Answering " + from + ": " + e.Message);
                }
            }
        }

        static void Respond(Stream stream, int status, object body)
        {
            var bytes = Encoding.UTF8.GetBytes(Json.Write(body));
            string reason = status == 200 ? "OK" : status == 401 ? "Unauthorized" : status == 404 ? "Not Found" : "Error";
            var head = Encoding.ASCII.GetBytes(
                "HTTP/1.1 " + status + " " + reason + "\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: " + bytes.Length + "\r\nConnection: close\r\n\r\n");
            stream.Write(head, 0, head.Length);
            stream.Write(bytes, 0, bytes.Length);
            stream.Flush();
        }

        /// <summary>This PC's addresses on the local network, for typing into the phone.</summary>
        public static List<string> LanAddresses()
        {
            var list = new List<string>();
            try
            {
                foreach (var ni in System.Net.NetworkInformation.NetworkInterface.GetAllNetworkInterfaces())
                {
                    if (ni.OperationalStatus != System.Net.NetworkInformation.OperationalStatus.Up) continue;
                    if (ni.NetworkInterfaceType == System.Net.NetworkInformation.NetworkInterfaceType.Loopback) continue;
                    var props = ni.GetIPProperties();
                    // Real network cards have a gateway; virtual ones (Hyper-V, VPNs, WSL) mostly don't.
                    bool gateway = props.GatewayAddresses.Count > 0;
                    foreach (var a in props.UnicastAddresses)
                    {
                        if (a.Address.AddressFamily != AddressFamily.InterNetwork) continue;
                        var text = a.Address.ToString();
                        if (text.StartsWith("169.254.")) continue;
                        if (gateway) list.Insert(0, text); else list.Add(text);
                    }
                }
            }
            catch (Exception e) { Log.Write("Addresses: " + e.Message); }
            return list;
        }
    }
}
