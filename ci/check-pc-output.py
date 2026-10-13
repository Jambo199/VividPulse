#!/usr/bin/env python3
"""Checks what Vivid Pulse PC wrote on the Windows runner (ci/pc-test.ps1): real specs and live
readings from the runner, and the Steam library from the pretend Steam install made there.

  check-pc-output.py pc-out
"""
import json
import os
import sys

out = sys.argv[1]
problems = []
notes = []


def load(name):
    with open(os.path.join(out, name), encoding="utf-8-sig") as f:
        return json.load(f)


def check(ok, what):
    print(("ok   " if ok else "FAIL ") + what)
    if not ok:
        problems.append(what)


def as_list(value, what):
    """Windows PowerShell can send one item, or {"value": [...], "Count": n}, where a list was meant; the app copes."""
    if isinstance(value, list):
        return value
    if isinstance(value, dict) and "value" in value and "Count" in value:
        notes.append(f"{what} came as {{value, Count}}")
        return value["value"]
    if value is None:
        return []
    notes.append(f"{what} came as a single item")
    return [value]


hello = load("hello.json")
check(hello.get("app") == "VividPulsePC" and hello.get("port") == 47800, f"hello: {hello}")

specs = load("specs.json")
cpu = specs.get("cpu") or {}
check(bool(specs.get("name")), f"PC name: {specs.get('name')}")
check(bool(cpu.get("name")) and (cpu.get("cores") or 0) > 0, f"CPU: {cpu.get('name')} with {cpu.get('cores')} cores, {cpu.get('threads')} threads")
check(((specs.get("memory") or {}).get("totalBytes") or 0) > 1e9, f"memory: {(specs.get('memory') or {}).get('totalBytes')} bytes")
check("Windows" in ((specs.get("os") or {}).get("name") or ""), f"Windows: {specs.get('os')}")
gpus = as_list(specs.get("gpus"), "gpus")
print(f"     graphics: {[g.get('name') for g in gpus]}")
print(f"     sticks: {len(as_list((specs.get('memory') or {}).get('sticks'), 'sticks'))}, drives: {[d.get('model') for d in as_list(specs.get('drives'), 'drives')]}")
print(f"     volumes: {[v.get('letter') for v in as_list(specs.get('volumes'), 'volumes')]}, monitors: {as_list(specs.get('monitors'), 'monitors')}")
print(f"     board: {specs.get('board')}")

live = load("live.json")
check(isinstance(live.get("cpuPercent"), (int, float)) and 0 <= live["cpuPercent"] <= 100, f"CPU use: {live.get('cpuPercent')}%")
check(len(as_list(live.get("cpuCores"), "cpuCores")) >= 1, f"per-thread use: {live.get('cpuCores')}")
check((live.get("memoryTotalBytes") or 0) > 1e9 and 0 < (live.get("memoryUsedBytes") or 0) <= live["memoryTotalBytes"], f"memory in use: {live.get('memoryUsedBytes')} of {live.get('memoryTotalBytes')}")
check((live.get("uptimeSec") or 0) > 0, f"uptime: {live.get('uptimeSec')} s")
check(live.get("playingAppId") == 1091500, f"playing: {live.get('playingAppId')}")
print(f"     clock {live.get('cpuMhz')} MHz, gpu {live.get('gpu')}, down {live.get('downBytesPerSec')} up {live.get('upBytesPerSec')}")

steam = load("steam.json")
check(steam.get("installed") is True, "Steam found")
check((steam.get("user") or {}).get("name") == "CI Tester", f"signed in: {steam.get('user')}")
games = {g["appId"]: g for g in as_list(steam.get("games"), "games")}
print(f"     games: { {k: (g.get('name'), g.get('installed'), g.get('minutes')) for k, g in games.items()} }")
cp = games.get(1091500) or {}
check(cp.get("installed") is True and cp.get("name") == "Cyberpunk 2077" and cp.get("minutes") == 6120 and cp.get("sizeBytes") == 74512345678,
      "Cyberpunk 2077: installed, 6120 minutes, size")
check((games.get(271590) or {}).get("installed") is True, "GTA V found in the second library")
check((games.get(570) or {}).get("installed") is False and (games.get(570) or {}).get("minutes") == 120, "Dota 2: played, not installed")
check(228980 not in games, "Steam's redistributables left out")
collections = {c.get("name"): c for c in as_list(steam.get("collections"), "collections")}
print(f"     collections: {collections}")
purchased = collections.get("Purchased") or {}
check(as_list(purchased.get("appIds"), "Purchased appIds") == [1091500, 271590, 1808500] and purchased.get("dynamic") is False,
      "Purchased collection: 3 games (one removed)")
check((collections.get("Installed") or {}).get("dynamic") is True, "Installed collection is dynamic")
check(len(collections) == 3, "the deleted collection left out")
library = set(as_list(steam.get("libraryAppIds"), "libraryAppIds"))
check({730, 570, 1091500, 271590, 1808500} <= library, f"library app IDs: {sorted(library)}")

for n in notes:
    print("note " + n)
if problems:
    print(f"\n{len(problems)} problem(s)")
    sys.exit(1)
print("\nAll output checks passed")
