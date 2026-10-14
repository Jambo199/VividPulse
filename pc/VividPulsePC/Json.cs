using System;
using System.Collections;
using System.Collections.Generic;
using System.Globalization;
using System.Text;

namespace VividPulsePC
{
    /// <summary>A JSON object that keeps its keys in the order they were added.</summary>
    sealed class JObject : List<KeyValuePair<string, object>>
    {
        public void Add(string key, object value) => Add(new KeyValuePair<string, object>(key, value));

        public object Get(string key)
        {
            foreach (var kv in this) if (kv.Key == key) return kv.Value;
            return null;
        }
    }

    /// <summary>Writes what the phone asks for, and reads the few JSON files Steam and the config keep.</summary>
    static class Json
    {
        public static string Write(object value)
        {
            var sb = new StringBuilder();
            WriteValue(sb, value);
            return sb.ToString();
        }

        static void WriteValue(StringBuilder sb, object v)
        {
            switch (v)
            {
                case null:
                    sb.Append("null");
                    break;
                case string s:
                    WriteString(sb, s);
                    break;
                case bool b:
                    sb.Append(b ? "true" : "false");
                    break;
                case double d:
                    sb.Append(double.IsNaN(d) || double.IsInfinity(d) ? "null" : d.ToString("R", CultureInfo.InvariantCulture));
                    break;
                case float f:
                    WriteValue(sb, (double)f);
                    break;
                case int _:
                case long _:
                case uint _:
                case ulong _:
                case short _:
                case ushort _:
                case byte _:
                    sb.Append(Convert.ToString(v, CultureInfo.InvariantCulture));
                    break;
                case JObject o:
                    sb.Append('{');
                    for (int i = 0; i < o.Count; i++)
                    {
                        if (i > 0) sb.Append(',');
                        WriteString(sb, o[i].Key);
                        sb.Append(':');
                        WriteValue(sb, o[i].Value);
                    }
                    sb.Append('}');
                    break;
                case IDictionary dict:
                    sb.Append('{');
                    bool first = true;
                    foreach (DictionaryEntry e in dict)
                    {
                        if (!first) sb.Append(',');
                        first = false;
                        WriteString(sb, Convert.ToString(e.Key, CultureInfo.InvariantCulture));
                        sb.Append(':');
                        WriteValue(sb, e.Value);
                    }
                    sb.Append('}');
                    break;
                case IEnumerable list:
                    sb.Append('[');
                    bool firstItem = true;
                    foreach (var item in list)
                    {
                        if (!firstItem) sb.Append(',');
                        firstItem = false;
                        WriteValue(sb, item);
                    }
                    sb.Append(']');
                    break;
                default:
                    WriteString(sb, Convert.ToString(v, CultureInfo.InvariantCulture));
                    break;
            }
        }

        static void WriteString(StringBuilder sb, string s)
        {
            sb.Append('"');
            foreach (char c in s)
            {
                switch (c)
                {
                    case '"': sb.Append("\\\""); break;
                    case '\\': sb.Append("\\\\"); break;
                    case '\n': sb.Append("\\n"); break;
                    case '\r': sb.Append("\\r"); break;
                    case '\t': sb.Append("\\t"); break;
                    default:
                        if (c < 0x20) sb.Append("\\u").Append(((int)c).ToString("x4"));
                        else sb.Append(c);
                        break;
                }
            }
            sb.Append('"');
        }

        /// <summary>Objects come back as dictionaries, arrays as lists, numbers as doubles.</summary>
        public static object Parse(string text) => new Parser(text).Document();

        sealed class Parser
        {
            readonly string s;
            int i;

            public Parser(string text) { s = text; }

            public object Document()
            {
                var v = Value();
                Skip();
                return v;
            }

            void Skip()
            {
                while (i < s.Length && (char.IsWhiteSpace(s[i]) || s[i] == '﻿')) i++;
            }

            object Value()
            {
                Skip();
                if (i >= s.Length) throw new FormatException("Unexpected end of JSON");
                char c = s[i];
                if (c == '{') return Obj();
                if (c == '[') return Arr();
                if (c == '"') return Str();
                if (s.Length - i >= 4 && string.CompareOrdinal(s, i, "true", 0, 4) == 0) { i += 4; return true; }
                if (s.Length - i >= 5 && string.CompareOrdinal(s, i, "false", 0, 5) == 0) { i += 5; return false; }
                if (s.Length - i >= 4 && string.CompareOrdinal(s, i, "null", 0, 4) == 0) { i += 4; return null; }
                int start = i;
                while (i < s.Length && "+-0123456789.eE".IndexOf(s[i]) >= 0) i++;
                if (start == i) throw new FormatException("Unexpected '" + c + "' in JSON");
                return double.Parse(s.Substring(start, i - start), NumberStyles.Float, CultureInfo.InvariantCulture);
            }

            Dictionary<string, object> Obj()
            {
                var d = new Dictionary<string, object>(StringComparer.Ordinal);
                i++;
                Skip();
                if (i < s.Length && s[i] == '}') { i++; return d; }
                while (true)
                {
                    Skip();
                    string key = Str();
                    Skip();
                    if (i >= s.Length || s[i] != ':') throw new FormatException("Expected ':' in JSON");
                    i++;
                    d[key] = Value();
                    Skip();
                    if (i < s.Length && s[i] == ',') { i++; continue; }
                    if (i < s.Length && s[i] == '}') { i++; return d; }
                    throw new FormatException("Expected ',' or '}' in JSON");
                }
            }

            List<object> Arr()
            {
                var list = new List<object>();
                i++;
                Skip();
                if (i < s.Length && s[i] == ']') { i++; return list; }
                while (true)
                {
                    list.Add(Value());
                    Skip();
                    if (i < s.Length && s[i] == ',') { i++; continue; }
                    if (i < s.Length && s[i] == ']') { i++; return list; }
                    throw new FormatException("Expected ',' or ']' in JSON");
                }
            }

            string Str()
            {
                if (i >= s.Length || s[i] != '"') throw new FormatException("Expected a string in JSON");
                i++;
                var sb = new StringBuilder();
                while (i < s.Length && s[i] != '"')
                {
                    char c = s[i++];
                    if (c != '\\') { sb.Append(c); continue; }
                    if (i >= s.Length) break;
                    char e = s[i++];
                    switch (e)
                    {
                        case 'n': sb.Append('\n'); break;
                        case 't': sb.Append('\t'); break;
                        case 'r': sb.Append('\r'); break;
                        case 'b': sb.Append('\b'); break;
                        case 'f': sb.Append('\f'); break;
                        case 'u':
                            sb.Append((char)int.Parse(s.Substring(i, 4), NumberStyles.HexNumber, CultureInfo.InvariantCulture));
                            i += 4;
                            break;
                        default: sb.Append(e); break;
                    }
                }
                i++;
                return sb.ToString();
            }
        }
    }
}
