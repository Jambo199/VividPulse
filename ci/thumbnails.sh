#!/usr/bin/env bash
# Prints small JPEG previews of every screenshot as base64 (one line each) so they can be
# reviewed from the job log.
set -uo pipefail
DIR="${1:-screenshots}"
python3 -m pip install --quiet --disable-pip-version-check pillow > /dev/null 2>&1 || true
python3 - "$DIR" <<'PY'
import base64, io, pathlib, sys
from PIL import Image
for f in sorted(pathlib.Path(sys.argv[1]).glob("*.png")):
    try:
        im = Image.open(f).convert("RGB")
        im.thumbnail((300, 1000))
        buf = io.BytesIO()
        im.save(buf, "JPEG", quality=55)
        data = buf.getvalue()
        print(f"THUMB {f.stem} {len(data)} {base64.b64encode(data).decode()}")
    except Exception as e:
        print(f"THUMB {f.stem} failed: {e}")
PY
