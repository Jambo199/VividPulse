using System;
using System.Collections.Generic;
using System.IO;
using System.Security.Cryptography;

namespace VividPulsePC
{
    /// <summary>
    /// The pairing code a phone needs, kept in %APPDATA%\VividPulsePC\config.json (the same file the
    /// first, PowerShell version used, so phones stay paired).
    /// </summary>
    sealed class Config
    {
        public static string Folder => Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "VividPulsePC");

        static string FilePath => Path.Combine(Folder, "config.json");

        public string Code { get; private set; }

        /// <summary>True until the window has been shown once.</summary>
        public bool FirstRun { get; private set; }

        public static Config Load()
        {
            var c = new Config();
            try
            {
                if (File.Exists(FilePath) && Json.Parse(File.ReadAllText(FilePath)) is Dictionary<string, object> d)
                {
                    c.Code = d.TryGetValue("code", out var code) ? code as string : null;
                    c.FirstRun = !(d.TryGetValue("welcomed", out var w) && w is bool b && b);
                }
            }
            catch (Exception e) { Log.Write("Config: " + e.Message); }
            if (string.IsNullOrEmpty(c.Code) || c.Code.Length != 6)
            {
                c.Code = NewCodeText();
                c.FirstRun = true;
                c.Save();
            }
            return c;
        }

        public void NewCode()
        {
            Code = NewCodeText();
            Save();
        }

        public void Welcomed()
        {
            FirstRun = false;
            Save();
        }

        void Save()
        {
            try
            {
                Directory.CreateDirectory(Folder);
                File.WriteAllText(FilePath, Json.Write(new JObject { { "code", Code }, { "welcomed", !FirstRun } }));
            }
            catch (Exception e) { Log.Write("Saving config: " + e.Message); }
        }

        static string NewCodeText()
        {
            var bytes = new byte[4];
            using (var rng = RandomNumberGenerator.Create()) rng.GetBytes(bytes);
            return (BitConverter.ToUInt32(bytes, 0) % 1000000).ToString("D6");
        }
    }
}
