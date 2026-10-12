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
from tv_release_inputs import generated_inputs

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


def verify(apk, sdk, coexist=False, selected_abi=None, audiofix=False):
    packaged_abis = {selected_abi} if selected_abi else ABIS
    require(packaged_abis <= ABIS, "Unsupported packaged ABI")
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

    local_build = manifest.get('localBuild')
    if local_build:
        pinned_inputs = dict(local_build['patches'])
        pinned_inputs[local_build['recipe']] = local_build['recipeSha256']
        pinned_inputs[local_build['record']] = local_build['recordSha256']
        for name, checksum in pinned_inputs.items():
            path = (ROOT / name).resolve()
            require(path.is_relative_to(ROOT), 'Unsafe local build input')
            require(path.is_file() and digest(path.read_bytes()) == checksum,
                    f'Patched native build input changed: {name}')

    generated = generated_inputs(ROOT)
    game_build = json.loads((ROOT / 'moonlight-core/native-build-manifest.json').read_text())
    game_expected = {row['abi'] + '/libmoonlight-core.so': row['sha256'] for row in game_build['binaries']}
    all_expected = {name: checksum for name, checksum in {**expected, **game_expected}.items()
                    if name.split("/")[0] in packaged_abis}
    game_bridge = (ROOT / 'moonlight-core/src/main/java/com/limelight/nvstream/jni/MoonBridge.java').read_text()
    game_methods = re.findall(r'\bnative\s+[^;()]+?\s+(\w+)\s*\(', game_bridge)
    require(game_methods, 'GameStream JNI adapter declares no native methods')
    bridge = (ROOT / 'tv/src/main/java/io/github/jqssun/airplay/bridge/NativeBridge.java').read_text()
    native_methods = re.findall(r'\bnative\s+[^;()]+?\s+(\w+)\s*\(', bridge)
    require(native_methods, 'JNI adapter declares no native methods')

    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        require(len(names) == len(set(names)), 'Duplicate APK ZIP entries')
        libraries = {n[4:] for n in names if n.startswith('lib/') and n.endswith('.so')}
        require(libraries == set(all_expected), f'Unexpected or missing native payload: {libraries ^ set(all_expected)}')
        for name, checksum in all_expected.items():
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
            if name.endswith('/libmoonlight-core.so'):
                exports = elf_exports(data)
                prefix = 'Java_com_limelight_nvstream_jni_MoonBridge_'
                require(all(e.startswith(prefix) or e == 'JNI_OnLoad' for e in exports),
                        f'GameStream must not export interposable crypto/audio symbols: {name}')
                for method in game_methods:
                    require(prefix + method in exports, f'Missing GameStream JNI export {method}: {name}')
            local = ROOT / ('moonlight-core/src/main/jniLibs' if name in game_expected else 'tv/src/main/jniLibs') / name
            require(local.is_file() and digest(local.read_bytes()) == checksum,
                    f'Vendored runtime SHA-256 mismatch: {name}')
        require('classes.dex' in names and 'AndroidManifest.xml' in names, 'Incomplete APK')
        dex = b''.join(archive.read(n) for n in names if re.fullmatch(r'classes\d*\.dex', n))
        require(b'Lcom/mooncast/tv/' in dex, 'Receiver classes missing')
        require(b'Lio/github/jqssun/airplay/bridge/NativeBridge;' in dex, 'AirPlay JNI adapter missing')
        require(b'Lcom/mooncast/tv/gamestream/GameStreamController;' in dex, 'GameStream controller missing')
        require(b'Lcom/limelight/nvstream/jni/MoonBridge;' in dex, 'GameStream JNI adapter missing')
        require(b'Lcom/mooncast/host/' not in dex, 'Phone host classes leaked into the TV app')
        require(not any('libsunshine' in n or 'libmooncast_sessions' in n or 'libgojni' in n for n in names),
                'Unrelated upstream/phone runtime payload')

    aapt = sdk / 'build-tools/35.0.0/aapt'
    if os.name == 'nt':
        aapt = aapt.with_suffix('.exe')
    require(aapt.is_file(), 'Set ANDROID_HOME to an SDK containing Build Tools 35.0.0')
    badging = subprocess.check_output([str(aapt), 'dump', 'badging', str(apk)], text=True)
    application_id = "com.mooncast.tv.audiofix.preview" if audiofix else ("com.mooncast.tv.preview" if coexist else "com.mooncast.tv")
    require(f"package: name='{application_id}'" in badging, 'Wrong receiver application ID')
    version = re.search(r"versionName\s+'([^']+)'", (ROOT / 'tv/build.gradle').read_text()).group(1)
    if coexist: version += "-coexist"
    if audiofix: version += "-experimental"
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
    return {'apk': apk.name, 'sha256': digest(apk.read_bytes()), 'applicationId': application_id,
            'abis': sorted(packaged_abis), 'nativeLibraries': len(libraries),
            'sourceArchives': len(sources), 'upstreamSourceCommit': manifest['commit'],
            'airplayJniMethodsPerAbi': len(native_methods), 'gameStreamJniMethodsPerAbi': len(game_methods),
            'generatedSourceAndRuntimeInputs': len(generated),
            'gameStreamSourceCommit': json.loads((ROOT / 'moonlight-core/SOURCE_LOCK.json').read_text())['moonlight_android']['commit'],
            'status': 'static payload and TV metadata checks passed',
            'notTested': 'Physical-device AirPlay/DLNA/GameStream interoperability and end-to-end latency'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=Path)
    parser.add_argument('--sdk', type=Path, default=Path(os.environ.get('ANDROID_HOME', os.environ.get('ANDROID_SDK_ROOT', ''))))
    parser.add_argument('--coexist-preview', action='store_true', help='Verify only the distinct co-installable preview package')
    parser.add_argument("--abi", choices=sorted(ABIS), help="Verify a standalone single-ABI APK; default requires all three")
    parser.add_argument("--audiofix", action="store_true", help="Verify the separately signed audio experiment")
    args = parser.parse_args()
    if args.audiofix and args.coexist_preview: parser.error("Select only one package variant")
    try:
        print(json.dumps(verify(args.apk.resolve(), args.sdk.resolve(), args.coexist_preview, args.abi, args.audiofix), indent=2))
    except (ValueError, KeyError, OSError, subprocess.CalledProcessError, zipfile.BadZipFile) as error:
        parser.exit(1, f'TV APK verification failed: {error}\n')


if __name__ == '__main__':
    main()
