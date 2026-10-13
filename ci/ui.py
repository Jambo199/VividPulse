"""Prints the centre of the UI node best matching a label. Usage: ui.py dump.xml "Label"

Compose merges a clickable row's texts into one node ("Music, 2 folders • 6 songs"), so a node
matches exactly when any of its comma/newline separated parts equals the label.
"""
import re
import sys
import xml.etree.ElementTree as ET

path, target = sys.argv[1], sys.argv[2]
nodes = list(ET.parse(path).getroot().iter("node"))

if target == "--texts":
    # Print every visible label top-to-bottom, for checking what a screen shows.
    seen = []
    for n in nodes:
        for label in ((n.get("text") or "").strip(), (n.get("content-desc") or "").strip()):
            if label and label not in seen:
                seen.append(label)
    print(" | ".join(seen)[:1500])
    sys.exit(0)


def parts(n):
    text = (n.get("text") or "") + "\n" + (n.get("content-desc") or "")
    return [p.strip() for p in re.split(r"[,\n]", text) if p.strip()]


def area(n):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
    return (x2 - x1) * (y2 - y1)


exact = [n for n in nodes if target in parts(n) and area(n) > 0]
loose = [n for n in nodes if any(target.lower() in p.lower() for p in parts(n)) and area(n) > 0]
# "exact" refuses near misses (e.g. "Stop" inside a sentence about stopping).
candidates = exact if (len(sys.argv) > 3 and sys.argv[3] == "exact") else (exact or loose)
if not candidates:
    sys.exit(1)
def top(n):
    return int(re.findall(r"\d+", n.get("bounds"))[1])


# Prefer clickable nodes, then the smallest one (the most specific element); with "top", the
# highest one on screen (so the mini player at the bottom doesn't win).
if len(sys.argv) > 3 and sys.argv[3] == "top":
    candidates.sort(key=lambda n: (n.get("clickable") != "true", top(n)))
else:
    candidates.sort(key=lambda n: (n.get("clickable") != "true", area(n)))
x1, y1, x2, y2 = map(int, re.findall(r"\d+", candidates[0].get("bounds")))
print((x1 + x2) // 2, (y1 + y2) // 2)
