#!/usr/bin/env python3
"""A stand-in for Vivid Pulse PC in the emulator test: answers like the real one, from JSON files.

  fake-pc.py --dir ci/pc-sample [--port 47800] [--code 123456]

/hello needs no code; everything else needs the X-Pulse-Code header. /live moves its numbers a
little each time, like a real PC. Each request is logged to stdout.
"""
import argparse
import json
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
    return o


class Handler(BaseHTTPRequestHandler):
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
        if self.headers.get("X-Pulse-Code") != opts.code:
            return self.answer(401, {"error": "Wrong code"})
        if url.path == "/specs":
            return self.answer(200, load("specs.json"))
        if url.path == "/live":
            return self.answer(200, live())
        if url.path == "/steam":
            return self.answer(200, load("steam.json"))
        if url.path == "/launch":
            app = parse_qs(url.query).get("appid", [""])[0]
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
