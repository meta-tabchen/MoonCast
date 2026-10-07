"""Extract only the Sunshine runtime from the pinned upstream release, with SHA-256 verification.

Usage: python scripts/prepare_native.py [path/to/upstream.apk]
This is a development bootstrap. No downloads happen on an Android device at runtime.
"""
from pathlib import Path
import hashlib
import json
import sys
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
URL = "https://github.com/jqssun/android-display-mirror/releases/download/v0.0.34/app-release.apk"
SHA256 = "e9261755e9c2f9b3b299da58ee5f0d84f75afea76994df65bd0a9dd2515b8f43"
apk = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / ".gradle" / "mirror-v0.0.34.apk"
if not apk.exists():
    apk.parent.mkdir(parents=True, exist_ok=True)
    urllib.request.urlretrieve(URL, apk)
actual = hashlib.sha256(apk.read_bytes()).hexdigest()
if actual != SHA256:
    raise SystemExit(f"SHA-256 mismatch: {actual}; do not use this file")
manifest = {"upstream": URL, "commit": "d7eea1b9e44eea38850bffa32ed0623506a2aa9a", "apkSha256": actual, "libraries": {}}
with zipfile.ZipFile(apk) as z:
    # Exclude Go/AirPlay, DisplayLink, and the upstream Java/UI application.
    libs = [n for n in z.namelist() if n.startswith("lib/") and n.endswith(".so")
            and Path(n).name in {"libsunshine.so", "libc++_shared.so", "libssl.so", "libcrypto.so"}]
    if not any(n.endswith("/libsunshine.so") for n in libs):
        raise SystemExit("Pinned APK has no Sunshine core")
    for entry in libs:
        abi, filename = entry.split("/")[1:]
        dest = ROOT / "app/src/main/jniLibs" / abi / filename
        dest.parent.mkdir(parents=True, exist_ok=True)
        data = z.read(entry)
        dest.write_bytes(data)
        manifest["libraries"][f"{abi}/{filename}"] = hashlib.sha256(data).hexdigest()
(ROOT / "native/prebuilt-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
print(f"Verified pinned release; extracted {len(libs)} runtime libraries")
