#!/usr/bin/env python3
"""Static TV delivery checks. These do not establish real-device interoperability."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ABIS = {'arm64-v8a', 'armeabi-v7a', 'x86_64'}
RUNTIMES = {'libairplay_native.so', 'libcrypto.so', 'liboboe.so', 'libc++_shared.so'}


def digest(data):
    return hashlib.sha256(data).hexdigest()


def require(condition, message):
    if not condition:
        raise ValueError(message)


def elf_exports(data):
    require(data[:4] == b'\x7fELF' and data[5] == 1, 'Expected little-endian Android ELF')
    is64 = data[4] == 2
    offset = struct.unpack_from('<Q' if is64 else '<I', data, 40 if is64 else 32)[0]
    size, count = struct.unpack_from('<HH', data, 58 if is64 else 46)
    sections = [struct.unpack_from('<IIQQQQIIQQ' if is64 else '<IIIIIIIIII', data,
                                   offset + i * size) for i in range(count)]
    exports = set()
    for section in sections:
        if section[1] != 11:  # SHT_DYNSYM
            continue
        strings = sections[section[6]]
        names = data[strings[4]:strings[4] + strings[5]]
        for index in range(section[4], section[4] + section[5], section[9]):
            symbol = struct.unpack_from('<IBBHQQ' if is64 else '<IIIBBH', data, index)
            name, section_index = symbol[0], symbol[3] if is64 else symbol[5]
            if name and section_index:
                exports.add(names[name:names.index(b'\0', name)].decode())
    return exports


def verify(apk, sdk):
    manifest_path = ROOT / 'native/airplay/prebuilt-manifest.json'
    manifest = json.loads(manifest_path.read_text())
    expected = manifest['libraries']
    require(set(expected) == {f'{abi}/{name}' for abi in ABIS for name in RUNTIMES},
            'Runtime manifest must pin all four expected AirPlay libraries on all three ABIs')
    require(re.fullmatch(r'[0-9a-f]{40}', manifest['commit']), 'Missing pinned upstream source commit')
    sources = manifest.get('sources', {})
    require(sources, 'Runtime manifest must pin the corresponding-source archives')
    for name, checksum in sources.items():
        path = (manifest_path.parent / name).resolve()
        require(path.is_relative_to(manifest_path.parent.resolve()), 'Unsafe source archive path')
        require(path.is_file() and digest(path.read_bytes()) == checksum,
                f'Corresponding-source archive missing or SHA-256 mismatch: {name}')

    bridge = (ROOT / 'tv/src/main/java/io/github/jqssun/airplay/bridge/NativeBridge.java').read_text()
    native_methods = re.findall(r'\bnative\s+[^;()]+?\s+(\w+)\s*\(', bridge)
    require(native_methods, 'JNI adapter declares no native methods')

    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        require(len(names) == len(set(names)), 'Duplicate APK ZIP entries')
        libraries = {n[4:] for n in names if n.startswith('lib/') and n.endswith('.so')}
        require(libraries == set(expected), f'Unexpected or missing native payload: {libraries ^ set(expected)}')
        for name, checksum in expected.items():
            data = archive.read('lib/' + name)
            require(data.startswith(b'\x7fELF'), f'Not an ELF runtime: {name}')
            abi = name.split('/')[0]
            expected_class, expected_machine = {'arm64-v8a': (2, 183), 'armeabi-v7a': (1, 40),
                                                'x86_64': (2, 62)}[abi]
            require(data[4] == expected_class and data[5] == 1
                    and struct.unpack_from('<H', data, 18)[0] == expected_machine,
                    f'ELF architecture does not match the packaged ABI: {name}')
            require(digest(data) == checksum, f'Pinned runtime SHA-256 mismatch: {name}')
            if name.endswith('/libairplay_native.so'):
                exports = elf_exports(data)
                for method in native_methods:
                    require('Java_io_github_jqssun_airplay_bridge_NativeBridge_' + method in exports,
                            f'Missing JNI export {method} in {name}')
            local = ROOT / 'tv/src/main/jniLibs' / name
            require(local.is_file() and digest(local.read_bytes()) == checksum,
                    f'Vendored runtime SHA-256 mismatch: {name}')
        require('classes.dex' in names and 'AndroidManifest.xml' in names, 'Incomplete APK')
        dex = b''.join(archive.read(n) for n in names if re.fullmatch(r'classes\d*\.dex', n))
        require(b'Lcom/mooncast/tv/' in dex, 'Receiver classes missing')
        require(b'Lio/github/jqssun/airplay/bridge/NativeBridge;' in dex, 'AirPlay JNI adapter missing')
        require(b'Lcom/mooncast/host/' not in dex, 'Phone host classes leaked into the TV app')
        require(not any('libsunshine' in n or 'libmooncast_sessions' in n or 'libgojni' in n for n in names),
                'Unrelated upstream/phone runtime payload')

    aapt = sdk / 'build-tools/35.0.0/aapt'
    if os.name == 'nt':
        aapt = aapt.with_suffix('.exe')
    require(aapt.is_file(), 'Set ANDROID_HOME to an SDK containing Build Tools 35.0.0')
    badging = subprocess.check_output([str(aapt), 'dump', 'badging', str(apk)], text=True)
    require("package: name='com.mooncast.tv'" in badging, 'Wrong receiver application ID')
    version = re.search(r"versionName\s+'([^']+)'", (ROOT / 'tv/build.gradle').read_text()).group(1)
    require(f"versionName='{version}'" in badging, 'APK version does not match receiver source')
    require("sdkVersion:'26'" in badging, 'Unexpected receiver minSdk')
    require("targetSdkVersion:'35'" in badging, 'Unexpected receiver targetSdk')
    require('leanback-launchable-activity:' in badging, 'Android TV launcher activity missing')
    require("uses-feature: name='android.hardware.touchscreen'" not in badging,
            'TV receiver must not require a touchscreen')
    require("uses-feature: name='android.hardware.wifi'" not in badging,
            'TV receiver must remain available to Ethernet-only TVs')
    require("uses-permission: name='android.permission.INTERNET'" in badging, 'Network permission missing')
    require('application-debuggable' not in badging, 'Release APK must not be debuggable')
    return {'apk': apk.name, 'sha256': digest(apk.read_bytes()), 'applicationId': 'com.mooncast.tv',
            'abis': sorted(ABIS), 'nativeLibraries': len(libraries),
            'sourceArchives': len(sources), 'upstreamSourceCommit': manifest['commit'],
            'jniMethodsPerAbi': len(native_methods),
            'status': 'static payload and TV metadata checks passed',
            'notTested': 'Physical-device AirPlay/DLNA interoperability, decoding, audio and latency'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=Path)
    parser.add_argument('--sdk', type=Path, default=Path(os.environ.get('ANDROID_HOME', os.environ.get('ANDROID_SDK_ROOT', ''))))
    args = parser.parse_args()
    try:
        print(json.dumps(verify(args.apk.resolve(), args.sdk.resolve()), indent=2))
    except (ValueError, KeyError, OSError, subprocess.CalledProcessError, zipfile.BadZipFile) as error:
        parser.exit(1, f'TV APK verification failed: {error}\n')


if __name__ == '__main__':
    main()
