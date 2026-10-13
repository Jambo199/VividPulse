#!/usr/bin/env python3
"""Builds the in-app changelog from the git history.

The release job tags every published build v1.0.<build> (ci/changelog-builds.txt lists the ones
from before it did). A build's entry is made from the commits
since the build before it: the newest commit's title, the "- " lines of every commit (the same
list the app shows as "What's new"), and how big an update it was. That's worked out from how much
of the app's code changed, unless a commit says so itself with an "Update-size: major|minor|small"
line. Builds from before the app had its own repo come from ci/changelog-history.json, which can
also correct any entry.

Usage: changelog.py BUILD OUT.json
"""
import json
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
HISTORY = os.path.join(HERE, "changelog-history.json")
BUILDS = os.path.join(HERE, "changelog-builds.txt")
TRAILER = re.compile(r"^(Co-Authored-By|Claude-Session|Signed-off-by|Update-size):", re.I)
SIZE_LINE = re.compile(r"^Update-size:\s*(major|minor|small)\s*$", re.I | re.M)
SKIP_CI = re.compile(r"\s*\[skip ci\]\s*", re.I)


def git(*args):
    return subprocess.run(["git", *args], check=True, capture_output=True, text=True).stdout


def bullets(body):
    """The "- " lines of a commit message, with their wrapped continuation lines joined on."""
    items, current = [], None
    for raw in body.splitlines():
        line = raw.strip()
        if TRAILER.match(line):
            current = None
        elif line.startswith(("- ", "* ")):
            current = [line[2:].strip()]
            items.append(current)
        elif line and current is not None and raw[:1] in (" ", "\t"):
            current.append(line)
        else:
            current = None
    return [" ".join(parts) for parts in items]


def commits(revisions):
    """Commits in a range, oldest first, following the main line only."""
    out = git("log", "--first-parent", "--reverse", "--format=%H%x1f%s%x1f%cI%x1f%b%x1e", revisions)
    for record in out.split("\x1e"):
        if not record.strip():
            continue
        sha, subject, date, body = record.lstrip("\n").split("\x1f", 3)
        yield {"sha": sha, "subject": subject.strip(), "date": date.strip(), "body": body}


def churn(old, new):
    """Lines of the app's own code added and removed between two commits."""
    if not old:
        return 0
    stat = git("diff", "--shortstat", f"{old}..{new}", "--", "app/src/main")
    return sum(int(n) for n in re.findall(r"(\d+) (?:insertion|deletion)", stat))


def size_of(changed, lines, changes):
    for c in reversed(changed):
        said = SIZE_LINE.search(c["body"])
        if said:
            return said.group(1).lower()
    if lines >= 500:
        return "major"
    if lines >= 150 or len(changes) >= 4:
        return "minor"
    return "small"


def entry(build, changed, previous):
    changed = list(changed)
    if not changed:
        return None
    shown = [c for c in changed if not SKIP_CI.search(c["subject"])] or changed
    newest = shown[-1]
    changes = []
    for c in changed:
        for b in bullets(c["body"]):
            if b not in changes:
                changes.append(b)
    return {
        "version": f"1.0.{build}",
        "code": build,
        "date": newest["date"][:10],
        "title": SKIP_CI.sub("", newest["subject"]).strip(),
        "size": size_of(changed, churn(previous, changed[-1]["sha"]), changes),
        "changes": changes,
    }


def main():
    build, out = int(sys.argv[1]), sys.argv[2]
    built = {}
    with open(BUILDS, encoding="utf-8") as f:
        for line in f:
            parts = line.split("#")[0].split()
            if len(parts) == 2:
                built[int(parts[0])] = git("rev-parse", parts[1]).strip()
    for tag in git("tag", "-l", "v1.0.*").split():
        m = re.fullmatch(r"v1\.0\.(\d+)", tag)
        if m:
            built[int(m.group(1))] = git("rev-list", "-n", "1", tag).strip()
    tags = sorted((n, sha) for n, sha in built.items() if n <= build)
    entries, previous = {}, None
    for number, sha in tags:
        e = entry(number, commits(f"{previous}..{sha}" if previous else sha), previous)
        if e:
            entries[number] = e
        previous = sha
    head = git("rev-parse", "HEAD").strip()
    if build not in entries and head != previous:
        e = entry(build, commits(f"{previous}..{head}" if previous else head), previous)
        if e:
            entries[build] = e
    with open(HISTORY, encoding="utf-8") as f:
        for h in json.load(f)["entries"]:
            entries[h["code"]] = {**entries.get(h["code"], {}), **h, "version": f"1.0.{h['code']}"}
    result = sorted(entries.values(), key=lambda e: -e["code"])
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        json.dump({"entries": result}, f, indent=1, ensure_ascii=False)
    for e in result:
        print(f"{e['version']:>9}  {e['date']}  {e['size']:<6} {len(e['changes']):>2} changes  {e['title']}")


if __name__ == "__main__":
    main()
