using System;
using System.Collections.Generic;
using System.Globalization;
using System.Text;

namespace VividPulsePC
{
    /// <summary>
    /// Valve's KeyValues format (Steam's .vdf and .acf files): "key" "value" pairs and
    /// "key" { ... } blocks, or the binary form. Keys ignore case, as Steam treats them.
    /// </summary>
    static class Vdf
    {
        public static Dictionary<string, object> Parse(string text)
        {
            var root = NewBlock();
            var stack = new Stack<Dictionary<string, object>>();
            stack.Push(root);
            string key = null;
            int i = 0;
            while (true)
            {
                string token = Next(text, ref i, out bool quoted);
                if (token == null) break;
                if (!quoted && token == "{")
                {
                    var parent = stack.Peek();
                    var block = parent.TryGetValue(key ?? "", out var existing) && existing is Dictionary<string, object> d ? d : NewBlock();
                    parent[key ?? ""] = block;
                    stack.Push(block);
                    key = null;
                }
                else if (!quoted && token == "}")
                {
                    if (stack.Count > 1) stack.Pop();
                    key = null;
                }
                else if (key == null)
                {
                    key = token;
                }
                else
                {
                    if (!stack.Peek().ContainsKey(key)) stack.Peek()[key] = token;
                    key = null;
                }
            }
            return root;
        }

        static Dictionary<string, object> NewBlock() => new Dictionary<string, object>(StringComparer.OrdinalIgnoreCase);

        static string Next(string s, ref int i, out bool quoted)
        {
            quoted = false;
            while (i < s.Length)
            {
                char c = s[i];
                if (char.IsWhiteSpace(c) || c == '﻿') { i++; continue; }
                if (c == '/' && i + 1 < s.Length && s[i + 1] == '/')
                {
                    while (i < s.Length && s[i] != '\n') i++;
                    continue;
                }
                if (c == '{' || c == '}') { i++; return c.ToString(); }
                if (c == '"')
                {
                    quoted = true;
                    i++;
                    var sb = new StringBuilder();
                    while (i < s.Length && s[i] != '"')
                    {
                        if (s[i] == '\\' && i + 1 < s.Length)
                        {
                            char e = s[i + 1];
                            sb.Append(e == 'n' ? '\n' : e == 't' ? '\t' : e);
                            i += 2;
                        }
                        else
                        {
                            sb.Append(s[i++]);
                        }
                    }
                    i++;
                    return sb.ToString();
                }
                int start = i;
                while (i < s.Length && !char.IsWhiteSpace(s[i]) && s[i] != '{' && s[i] != '}' && s[i] != '"') i++;
                return s.Substring(start, i - start);
            }
            return null;
        }

        /// <summary>
        /// The binary form Steam uses for shortcuts.vdf (non-Steam games): a type byte, a
        /// null-terminated key, then the value. Numbers are kept as text, like the text form's.
        /// </summary>
        public static Dictionary<string, object> ParseBinary(byte[] data)
        {
            int i = 0;
            return ReadBlock(data, ref i);
        }

        static Dictionary<string, object> ReadBlock(byte[] d, ref int i)
        {
            var block = NewBlock();
            while (i < d.Length)
            {
                byte type = d[i++];
                if (type == 0x08) break;
                string key = ReadString(d, ref i);
                object value;
                switch (type)
                {
                    case 0x00: value = ReadBlock(d, ref i); break;
                    case 0x01: value = ReadString(d, ref i); break;
                    case 0x02: value = Take(d, ref i, 4) ? BitConverter.ToInt32(d, i - 4).ToString(CultureInfo.InvariantCulture) : null; break;
                    case 0x03: value = Take(d, ref i, 4) ? BitConverter.ToSingle(d, i - 4).ToString(CultureInfo.InvariantCulture) : null; break;
                    case 0x07: value = Take(d, ref i, 8) ? BitConverter.ToUInt64(d, i - 8).ToString(CultureInfo.InvariantCulture) : null; break;
                    case 0x0A: value = Take(d, ref i, 8) ? BitConverter.ToInt64(d, i - 8).ToString(CultureInfo.InvariantCulture) : null; break;
                    default: throw new FormatException("Unknown value type " + type + " at " + (i - 1));
                }
                if (value == null) throw new FormatException("The file ends early");
                if (!block.ContainsKey(key)) block[key] = value;
            }
            return block;
        }

        static bool Take(byte[] d, ref int i, int n)
        {
            if (i + n > d.Length) return false;
            i += n;
            return true;
        }

        static string ReadString(byte[] d, ref int i)
        {
            int start = i;
            while (i < d.Length && d[i] != 0) i++;
            var s = Encoding.UTF8.GetString(d, start, i - start);
            i++;
            return s;
        }

        /// <summary>The block at <paramref name="path"/>, or null.</summary>
        public static Dictionary<string, object> Block(this Dictionary<string, object> d, params string[] path)
        {
            var at = d;
            foreach (var p in path)
            {
                if (at == null || !at.TryGetValue(p, out var v)) return null;
                at = v as Dictionary<string, object>;
            }
            return at;
        }

        public static string Text(this Dictionary<string, object> d, string key) =>
            d != null && d.TryGetValue(key, out var v) ? v as string : null;

        public static long Number(this Dictionary<string, object> d, string key) =>
            long.TryParse(d.Text(key), out long n) ? n : 0;
    }
}
