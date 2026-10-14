using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Reflection;
using System.Windows.Forms;
using Microsoft.Win32;

namespace VividPulsePC
{
    /// <summary>The icon by the clock: the code at a glance, the window, and the few settings there are.</summary>
    sealed class Tray : ApplicationContext
    {
        static readonly Color Background = Color.FromArgb(0x11, 0x13, 0x1F);
        static readonly Color Panel = Color.FromArgb(0x1B, 0x1E, 0x2E);
        static readonly Color Cyan = Color.FromArgb(0x22, 0xD3, 0xEE);
        static readonly Color Green = Color.FromArgb(0x4A, 0xDE, 0x80);
        static readonly Color Muted = Color.FromArgb(0xA3, 0xA8, 0xC3);

        readonly Config config;
        readonly Server server;
        readonly NotifyIcon icon;
        readonly Control ui = new Control();
        readonly ToolStripMenuItem codeItem, startItem;
        Form window;
        bool toldAboutTray;

        public Tray(Config config, Server server, bool showWindow)
        {
            this.config = config;
            this.server = server;
            ui.CreateControl();

            var menu = new ContextMenuStrip();
            codeItem = new ToolStripMenuItem { Font = new Font(menu.Font, FontStyle.Bold) };
            codeItem.Click += (s, e) => ShowWindow();
            menu.Items.Add(codeItem);
            menu.Items.Add("Open Vivid Pulse PC", null, (s, e) => ShowWindow());
            menu.Items.Add(new ToolStripSeparator());
            startItem = new ToolStripMenuItem("Start with Windows") { CheckOnClick = true };
            startItem.Click += (s, e) => Startup.Set(startItem.Checked);
            menu.Items.Add(startItem);
            menu.Items.Add("Allow through firewall…", null, (s, e) => Firewall.Allow());
            menu.Items.Add("New code", null, (s, e) => NewCode());
            menu.Items.Add("Open log", null, (s, e) => OpenLog());
            menu.Items.Add(new ToolStripSeparator());
            menu.Items.Add("Quit", null, (s, e) => Quit());
            menu.Opening += (s, e) =>
            {
                codeItem.Text = "Code  " + config.Code;
                startItem.Checked = Startup.Enabled;
            };

            icon = new NotifyIcon
            {
                Icon = AppIcon(SystemInformation.SmallIconSize),
                Text = "Vivid Pulse PC · code " + config.Code,
                ContextMenuStrip = menu,
                Visible = true,
            };
            icon.MouseClick += (s, e) => { if (e.Button == MouseButtons.Left) ShowWindow(); };
            server.PhoneConnected += address => OnUi(() =>
                icon.ShowBalloonTip(4000, "Phone connected", "Vivid Pulse on your phone (" + address + ") can see this PC.", ToolTipIcon.Info));

            // The first time: show the code. Starting with Windows waits for the tick box.
            if (config.FirstRun)
            {
                config.Welcomed();
                showWindow = true;
            }
            // The first, PowerShell version started from a Startup-folder shortcut: this replaces it.
            if (Startup.RemoveOldShortcut()) Startup.Set(true);
            if (showWindow) ShowWindow();
        }

        public void ShowWindowFromAnyThread() => OnUi(ShowWindow);

        void OnUi(Action action)
        {
            try { ui.BeginInvoke(action); }
            catch (Exception) { }
        }

        void ShowWindow()
        {
            if (window == null || window.IsDisposed) window = BuildWindow();
            window.Show();
            if (window.WindowState == FormWindowState.Minimized) window.WindowState = FormWindowState.Normal;
            window.Activate();
        }

        void NewCode()
        {
            var answer = MessageBox.Show(
                "Make a new code? Your phone will need the new one to connect again.",
                "Vivid Pulse PC", MessageBoxButtons.OKCancel, MessageBoxIcon.Question);
            if (answer != DialogResult.OK) return;
            config.NewCode();
            icon.Text = "Vivid Pulse PC · code " + config.Code;
            if (window != null && !window.IsDisposed) { window.Close(); window.Dispose(); window = null; ShowWindow(); }
        }

        static void OpenLog()
        {
            try { Process.Start(new ProcessStartInfo("notepad.exe", "\"" + Log.FilePath + "\"") { UseShellExecute = true }); }
            catch (Exception) { }
        }

        void Quit()
        {
            icon.Visible = false;
            icon.Dispose();
            if (window != null && !window.IsDisposed) window.Dispose();
            ExitThread();
            Environment.Exit(0);
        }

        static Icon AppIcon(Size size)
        {
            using (var stream = Assembly.GetExecutingAssembly().GetManifestResourceStream("icon.ico"))
            {
                return stream != null ? new Icon(stream, size) : SystemIcons.Application;
            }
        }

        Form BuildWindow()
        {
            var f = new Form
            {
                Text = "Vivid Pulse PC",
                Icon = AppIcon(new Size(32, 32)),
                FormBorderStyle = FormBorderStyle.FixedSingle,
                MaximizeBox = false,
                StartPosition = FormStartPosition.CenterScreen,
                BackColor = Background,
                ForeColor = Color.White,
                Font = new Font("Segoe UI", 10f),
                AutoScaleMode = AutoScaleMode.Dpi,
                AutoScaleDimensions = new SizeF(96f, 96f),
                ClientSize = new Size(460, 470),
                Padding = new Padding(24),
            };
            int x = 28, y = 22, width = 404;

            Label Add(string text, Font font, Color color, int height)
            {
                var label = new Label { Text = text, Font = font, ForeColor = color, Location = new Point(x, y), Size = new Size(width, height), BackColor = Color.Transparent };
                f.Controls.Add(label);
                y += height;
                return label;
            }

            Add("Vivid Pulse PC", new Font("Segoe UI Semibold", 17f), Color.White, 36);
            Add("Lets Vivid Pulse on your phone see this PC: specs, live use, your Steam library and downloads.", new Font("Segoe UI", 9.5f), Muted, 40);
            y += 10;

            var panel = new Panel { Location = new Point(x, y), Size = new Size(width, 150), BackColor = Panel };
            f.Controls.Add(panel);
            panel.Controls.Add(new Label { Text = "YOUR CODE", Font = new Font("Segoe UI Semibold", 8.5f), ForeColor = Muted, Location = new Point(18, 14), AutoSize = true });
            panel.Controls.Add(new Label
            {
                Text = config.Code.Substring(0, 3) + " " + config.Code.Substring(3),
                Font = new Font("Segoe UI", 34f, FontStyle.Bold),
                ForeColor = Cyan,
                Location = new Point(12, 32),
                AutoSize = true,
            });
            var addresses = Server.LanAddresses();
            panel.Controls.Add(new Label
            {
                Text = "Address  " + (addresses.Count > 0 ? string.Join("   ", addresses.GetRange(0, Math.Min(2, addresses.Count))) : "not connected to a network"),
                Font = new Font("Segoe UI", 10f),
                ForeColor = Color.White,
                Location = new Point(18, 112),
                AutoSize = true,
            });
            y += 150 + 14;

            var status = Add("", new Font("Segoe UI Semibold", 10f), Muted, 26);
            Add("On your phone, open Vivid Pulse › PC › Find my PC, pick this PC and type the code. Phone and PC need to be on the same Wi-Fi.",
                new Font("Segoe UI", 9.5f), Muted, 44);
            y += 6;

            var start = new CheckBox
            {
                Text = "Start with Windows, so your phone can always reach it",
                Checked = Startup.Enabled,
                ForeColor = Color.White,
                Location = new Point(x, y),
                Size = new Size(width, 28),
            };
            start.CheckedChanged += (s, e) => Startup.Set(start.Checked);
            f.Controls.Add(start);
            y += 40;

            Button MakeButton(string text, int left, int w, EventHandler click)
            {
                var b = new Button
                {
                    Text = text,
                    Location = new Point(left, y),
                    Size = new Size(w, 36),
                    FlatStyle = FlatStyle.Flat,
                    BackColor = Panel,
                    ForeColor = Color.White,
                    Cursor = Cursors.Hand,
                };
                b.FlatAppearance.BorderColor = Color.FromArgb(0x33, 0x37, 0x4D);
                b.Click += click;
                f.Controls.Add(b);
                return b;
            }

            MakeButton("Allow through firewall", x, 170, (s, e) => Firewall.Allow());
            MakeButton("New code", x + 178, 104, (s, e) => NewCode());
            var hide = MakeButton("Hide", x + 290, 114, (s, e) => f.Close());
            hide.BackColor = Color.FromArgb(0x0E, 0x74, 0x90);
            y += 50;
            Add("Closing this window keeps Vivid Pulse PC running by the clock. Right-click its icon to quit.", new Font("Segoe UI", 8.5f), Muted, 36);

            var timer = new Timer { Interval = 1000 };
            timer.Tick += (s, e) =>
            {
                var ago = DateTime.UtcNow - server.LastPhoneAt;
                if (ago < TimeSpan.FromSeconds(15))
                {
                    status.Text = "●  Phone connected";
                    status.ForeColor = Green;
                }
                else if (server.LastPhoneAt != DateTime.MinValue)
                {
                    status.Text = "●  Phone last connected " + (ago.TotalMinutes < 60 ? (int)ago.TotalMinutes + " min ago" : DateTime.Now.Subtract(ago).ToString("t"));
                    status.ForeColor = Muted;
                }
                else
                {
                    status.Text = "●  Waiting for your phone";
                    status.ForeColor = Muted;
                }
            };
            timer.Start();
            timer.Tick += (s, e) => { if (f.IsDisposed) timer.Stop(); };

            f.FormClosing += (s, e) =>
            {
                if (e.CloseReason != CloseReason.UserClosing) return;
                e.Cancel = true;
                f.Hide();
                if (!toldAboutTray)
                {
                    toldAboutTray = true;
                    icon.ShowBalloonTip(4000, "Still running", "Vivid Pulse PC keeps going by the clock. Right-click its icon to quit.", ToolTipIcon.Info);
                }
            };
            return f;
        }
    }

    /// <summary>Starting with Windows: a value under the current user's Run key.</summary>
    static class Startup
    {
        const string RunKey = @"Software\Microsoft\Windows\CurrentVersion\Run";
        const string Name = "Vivid Pulse PC";

        public static bool Enabled
        {
            get
            {
                try
                {
                    using (var k = Registry.CurrentUser.OpenSubKey(RunKey)) return k?.GetValue(Name) != null;
                }
                catch (Exception) { return false; }
            }
        }

        public static void Set(bool on)
        {
            try
            {
                using (var k = Registry.CurrentUser.CreateSubKey(RunKey))
                {
                    if (on) k.SetValue(Name, "\"" + Application.ExecutablePath + "\" --startup");
                    else k.DeleteValue(Name, false);
                }
            }
            catch (Exception e) { Log.Write("Start with Windows: " + e.Message); }
        }

        /// <summary>Removes the first version's Startup-folder shortcut; true when there was one.</summary>
        public static bool RemoveOldShortcut()
        {
            try
            {
                var link = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Startup), "Vivid Pulse PC.lnk");
                if (!File.Exists(link)) return false;
                File.Delete(link);
                return true;
            }
            catch (Exception) { return false; }
        }
    }

    /// <summary>
    /// Windows Firewall's own "allowed apps" page, where VividPulsePC can be allowed on private
    /// networks. (It doesn't change firewall settings itself: Windows asks the first time it runs.)
    /// </summary>
    static class Firewall
    {
        public static void Allow()
        {
            MessageBox.Show(
                "Windows Firewall's list of allowed apps opens next.\n\nClick \"Change settings\", find VividPulsePC (or click \"Allow another app\" and pick it), and tick \"Private\".",
                "Vivid Pulse PC", MessageBoxButtons.OK, MessageBoxIcon.Information);
            try
            {
                Process.Start(new ProcessStartInfo("control.exe", "/name Microsoft.WindowsFirewall /page pageConfigureApps") { UseShellExecute = true });
            }
            catch (Exception e) { Log.Write("Firewall page: " + e.Message); }
        }
    }
}
