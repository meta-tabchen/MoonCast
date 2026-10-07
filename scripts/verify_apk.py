"""Static delivery checks: pinned ELF bytes, dependencies, JNI exports, ABIs and unwanted payloads.

This does not substitute for MediaProjection / Moonlight device tests.
"""
from pathlib import Path
import hashlib
import json
import re
import struct
import sys
import zipfile

root = Path(__file__).resolve().parents[1]
apk = Path(sys.argv[1]) if len(sys.argv) > 1 else root / "app/build/outputs/apk/release/app-release.apk"
manifest = json.loads((root / "native/prebuilt-manifest.json").read_text())
sessions = json.loads((root / "native/session-bridge-manifest.json").read_text())
assert hashlib.sha256((root / sessions['source']).read_bytes()).hexdigest() == sessions['sourceSha256'], 'session bridge source mismatch'
assert hashlib.sha256((root / sessions['commandSource']).read_bytes()).hexdigest() == sessions['commandSourceSha256'], 'session bridge build recipe mismatch'

def elf_exports(data):
    assert data[:4] == b"\x7fELF"
    assert data[5] == 1, "Expected little-endian Android ELF"
    is64 = data[4] == 2
    shoff = struct.unpack_from("<Q" if is64 else "<I", data, 40 if is64 else 32)[0]
    entsize, count = struct.unpack_from("<HH", data, 58 if is64 else 46)
    fmt = "<IIQQQQIIQQ" if is64 else "<IIIIIIIIII"
    sections = [struct.unpack_from(fmt, data, shoff + i * entsize) for i in range(count)]
    exports = set()
    for section in sections:
        if section[1] != 11:  # SHT_DYNSYM
            continue
        strings = sections[section[6]]
        names = data[strings[4]:strings[4]+strings[5]]
        for offset in range(section[4], section[4]+section[5], section[9]):
            sym = struct.unpack_from("<IBBHQQ" if is64 else "<IIIBBH", data, offset)
            nameoff, shndx = sym[0], sym[3] if is64 else sym[5]
            if nameoff and shndx:
                exports.add(names[nameoff:names.index(b"\0", nameoff)].decode())
    return exports

bridge = (root / "app/src/main/java/io/github/jqssun/displaymirror/sunshine/SunshineServer.java").read_text(encoding="utf-8")
native_methods = re.findall(r"native \w+ (\w+)\(", bridge)
prefix = "Java_io_github_jqssun_displaymirror_sunshine_SunshineServer_"
with zipfile.ZipFile(apk) as z:
    if 'release' in apk.name.lower():
        assert not any('libmoonlight-core' in n for n in z.namelist()), 'test-client payload in release'
        assert b'com/mooncast/host/testclient' not in z.read('classes.dex'), 'test-client classes in release'
    assert not any("libgojni" in n or "displaylink" in n.lower() for n in z.namelist())
    for relative, expected in manifest["libraries"].items():
        data = z.read("lib/" + relative)
        assert hashlib.sha256(data).hexdigest() == expected, relative
        if relative.endswith("libsunshine.so"):
            exports = elf_exports(data)
            for name in native_methods:
                assert prefix + name in exports, (relative, name)
            for callback in (b"onPinRequested", b"createVirtualDisplay", b"stopVirtualDisplay", b"showEncoderError"):
                assert callback in data, (relative, callback)
    assert "classes.dex" in z.namelist()
    for relative, expected in sessions['libraries'].items():
        data=z.read('lib/'+relative)
        assert hashlib.sha256(data).hexdigest()==expected, relative
        exports=elf_exports(data)
        for method in ('availableNative','stopNative'):
            assert 'Java_io_github_jqssun_displaymirror_sunshine_NativeSessions_'+method in exports, (relative,method)
    for abi in ('arm64-v8a','armeabi-v7a','x86_64'):
        assert '_ZN6stream7session4stopERNS_9session_tE' in elf_exports(z.read('lib/'+abi+'/libsunshine.so')), 'session stop export missing'
print(json.dumps({"apk": apk.name, "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
    "abis": ["arm64-v8a", "armeabi-v7a", "x86_64"], "nativeLibraries": len(manifest["libraries"])+len(sessions['libraries']),
    "jniMethodsPerAbi": len(native_methods), "status": "static checks passed"}, indent=2))
