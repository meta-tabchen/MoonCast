#!/usr/bin/env python3
"""Verify/extract pinned GPLv3 AirPlay JNI runtime. Never downloads at device runtime.
Usage: python scripts/prepare_airplay_native.py [upstream.apk]
Corresponding native source and notices are in native/airplay/.
"""
from pathlib import Path
import hashlib
import json
import sys
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
URL = 'https://github.com/jqssun/android-airplay-server/releases/download/v0.0.31/app-release.apk'
SHA256 = 'c5dce5c29ab52157bdaa406e485f9bfaab7d8d59c523d93622a33b253a28f410'
COMMIT = 'c8defdd70d7e6a04f4f1b71d353653682d594106'
ABIS = ['arm64-v8a', 'armeabi-v7a', 'x86_64']
LIBRARIES = ['libairplay_native.so', 'libcrypto.so', 'liboboe.so', 'libc++_shared.so']

def main():
    apk = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / '.gradle/airplay-v0.0.31.apk'
    if not apk.exists():
        apk.parent.mkdir(parents=True, exist_ok=True)
        urllib.request.urlretrieve(URL, apk)
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    if digest != SHA256:
        raise SystemExit(f'Upstream APK SHA-256 mismatch: {digest}')
    manifest = {'upstream': URL, 'commit': COMMIT, 'apkSha256': digest, 'libraries': {}}
    existing = ROOT / 'native/airplay/prebuilt-manifest.json'
    if existing.exists():
        previous = json.loads(existing.read_text())
        for field in ('sources', 'sourceCommits'):
            if field in previous:
                manifest[field] = previous[field]
    with zipfile.ZipFile(apk) as archive:
        for abi in ABIS:
            for filename in LIBRARIES:
                key = f'{abi}/{filename}'
                data = archive.read('lib/' + key)
                if not data.startswith(b'\x7fELF'):
                    raise SystemExit(f'Not an ELF library: {key}')
                target = ROOT / 'tv/src/main/jniLibs' / key
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(data)
                manifest['libraries'][key] = hashlib.sha256(data).hexdigest()
    path = ROOT / 'native/airplay/prebuilt-manifest.json'
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    print(f'Verified/extracted {len(manifest["libraries"])} AirPlay runtime libraries')

if __name__ == '__main__':
    main()
