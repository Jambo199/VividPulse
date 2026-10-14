#!/usr/bin/env python3
"""A stand-in for Vivid Pulse PC in the emulator test: answers like the real one, from JSON files.

  fake-pc.py --dir ci/pc-sample [--port 47800] [--code 123456]

/hello needs no code; everything else needs the X-Pulse-Code header. /live moves its numbers a
little each time, like a real PC, and so do downloads. For the test, /_finish ends the first
download (the game is then installed, and the library's stamp changes) and /_hot makes the graphics
card hot (no code needed). Each request is logged to stdout.
"""
import argparse
import json
import struct
import zlib
import os
import random
import sys
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

args = argparse.ArgumentParser()
args.add_argument("--dir", required=True)
args.add_argument("--port", type=int, default=47800)
args.add_argument("--code", default="123456")
opts = args.parse_args()


def load(name):
    with open(os.path.join(opts.dir, name), encoding="utf-8-sig") as f:
        return json.load(f)


state = {"hot": False, "finished": [], "downloads": None, "steam": None}


def steam():
    if state["steam"] is None:
        state["steam"] = load("steam.json")
    return state["steam"]


def cover():
    """A tall picture, as a non-Steam game's own picture in Steam."""
    w, h = 60, 90
    rows = b"".join(b"\0" + bytes(c for x in range(w) for c in (40 + x * 3, 30 + y, 160 + y)) for y in range(h))
    chunk = lambda kind, data: struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(rows)) + chunk(b"IEND", b"")


def downloads():
    if state["downloads"] is None:
        try:
            state["downloads"] = load("downloads.json").get("downloads", [])
        except FileNotFoundError:
            state["downloads"] = []
    for d in state["downloads"]:
        if d.get("state") == "downloading" and d.get("bytesTotal"):
            d["bytesDone"] = min(d["bytesTotal"], d["bytesDone"] + int(d.get("bytesPerSec", 0) * random.uniform(1.5, 2.5)))
    return {"at": int(time.time() * 1000), "downloads": state["downloads"], "finished": state["finished"], "libraryStamp": steam().get("stamp")}


def live():
    o = load("live.json")
    o["at"] = int(time.time() * 1000)
    jiggle = lambda v, spread: max(0.0, min(100.0, (v or 0) + random.uniform(-spread, spread)))
    o["cpuPercent"] = round(jiggle(o.get("cpuPercent"), 8), 1)
    cores = o.get("cpuCores") or []
    if isinstance(cores, list):
        o["cpuCores"] = [round(jiggle(c, 15), 1) for c in cores]
    gpu = o.get("gpu")
    if isinstance(gpu, dict) and gpu.get("loadPercent") is not None:
        gpu["loadPercent"] = round(jiggle(gpu["loadPercent"], 6))
    if isinstance(gpu, dict) and state["hot"]:
        gpu["tempC"] = 91
    return o


class Handler(BaseHTTPRequestHandler):
    def picture(self, data):
        self.send_response(200)
        self.send_header("Content-Type", "image/png")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(data)

    def answer(self, status, body):
        data = json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(data)

    def route(self):
        url = urlparse(self.path)
        print(f"{time.strftime('%H:%M:%S')} {self.command} {self.path} code={self.headers.get('X-Pulse-Code')}", flush=True)
        if url.path == "/hello":
            return self.answer(200, load("hello.json"))
        if url.path == "/_finish":
            downloads()
            if state["downloads"]:
                d = state["downloads"].pop(0)
                state["finished"].append({"appId": d["appId"], "name": d.get("name"), "at": int(time.time() * 1000)})
                for g in steam().get("games", []):
                    if g.get("appId") == d["appId"]:
                        g["installed"] = True
                steam()["stamp"] = f"finished-{d['appId']}"
                print(f"finished {d['appId']}", flush=True)
            return self.answer(200, {"ok": True})
        if url.path == "/_hot":
            state["hot"] = True
            print("hot", flush=True)
            return self.answer(200, {"ok": True})
        code = self.headers.get("X-Pulse-Code") or parse_qs(url.query).get("code", [None])[0]
        if code != opts.code:
            return self.answer(401, {"error": "Wrong code"})
        if url.path == "/specs":
            return self.answer(200, load("specs.json"))
        if url.path == "/live":
            return self.answer(200, live())
        if url.path == "/steam":
            return self.answer(200, steam())
        if url.path == "/art":
            print(f"art {parse_qs(url.query).get('shortcut', [''])[0]}", flush=True)
            return self.picture(cover())
        if url.path == "/downloads":
            return self.answer(200, downloads())
        if url.path == "/launch":
            q = parse_qs(url.query)
            app = q.get("appid", q.get("shortcut", [""]))[0]
            print(f"launch {app}", flush=True)
            return self.answer(200, {"started": int(app or 0)})
        return self.answer(404, {"error": "Not found"})

    do_GET = route
    do_POST = route

    def log_message(self, *_):
        pass


print(f"fake Vivid Pulse PC on port {opts.port} from {opts.dir}", flush=True)
sys.stdout.flush()
ThreadingHTTPServer(("0.0.0.0", opts.port), Handler).serve_forever()
