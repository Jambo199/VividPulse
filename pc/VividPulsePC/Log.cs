using System;
using System.IO;

namespace VividPulsePC
{
    /// <summary>A short log in %APPDATA%\VividPulsePC\log.txt, for working out what went wrong.</summary>
    static class Log
    {
        static readonly object Gate = new object();

        public static string FilePath => Path.Combine(Config.Folder, "log.txt");

        public static void Write(string message)
        {
            try
            {
                lock (Gate)
                {
                    Directory.CreateDirectory(Config.Folder);
                    var file = new FileInfo(FilePath);
                    if (file.Exists && file.Length > 256 * 1024)
                    {
                        var text = File.ReadAllText(FilePath);
                        File.WriteAllText(FilePath, text.Substring(text.Length / 2));
                    }
                    File.AppendAllText(FilePath, DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss") + "  " + message + Environment.NewLine);
                }
            }
            catch (Exception) { }
        }
    }
}
