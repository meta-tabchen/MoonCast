#!/usr/bin/env python3
"""Archive a clean unified commit plus only hash-pinned receiver source inputs.

Does not build, sign, upload or publish. Public upstream crypto test fixtures are
included only through the existing exact corresponding-source inventory.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path
import zipfile
from package_tv_release import committed_source_archive, require_committed_sources, git
from tv_release_inputs import generated_inputs
from restore_release_inputs import load_manifest, safe_target, digest

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    require_committed_sources()
    if args.output.exists():
        parser.error('Output already exists; do not overwrite historical archives')
    pinned = generated_inputs(ROOT)
    for name, checksum, size, mode in load_manifest(ROOT)['files']:
        file = safe_target(ROOT, name)
        if not file.is_file() or digest(file) != checksum:
            raise ValueError('Missing or modified pinned release input: ' + name)
        pinned[name] = (file, checksum)
    identity = json.loads((ROOT / 'signing/community/identity.json').read_text())
    # Signing material is not required corresponding source. Never copy it into a
    # release archive. Community signing uses the owner-published Git fixture;
    # the full source ZIP supports explicit unsigned verification builds.
    certificate = ROOT / 'signing/community/certificate.pem'
    pinned['signing/community/certificate.pem'] = (certificate, digest(certificate))
    commit = git('rev-parse', 'HEAD').decode().strip()
    with zipfile.ZipFile(io.BytesIO(committed_source_archive())) as committed:
        names = set(committed.namelist()) - {'CORRESPONDING-SOURCE.json'}
        def content(name):
            return committed.read(name) if name in names else pinned[name][0].read_bytes()
        available = names | set(pinned)
        required = {'unified/build.gradle', 'phone-ui/build.gradle', 'tv-ui/build.gradle',
                    'transfer-core/build.gradle', 'app/libs/airplaylib.aar',
                    'native/unified-receiver-manifest.json', 'native/airplay-sender/SOURCE_LOCK.json',
                    'native/webrtc/SOURCE_LOCK.json', 'native/webrtc/README.md',
                    'gradle/browser-webrtc.gradle', 'docs/browser-receiver.md',
                    'native/browser-qr/SOURCE_LOCK.json', 'native/browser-qr/README.md',
                    'gradle/browser-qr.gradle', 'scripts/run_browser_qr_tests.py',
                    'scripts/prepare_sender_build_inputs.py', 'native/sender-build-inputs/source-input-manifest.json',
                    'README.md', 'README.zh-CN.md', 'LICENSE',
                    'native/sender-build-inputs/openssl-3.5.5.tar.gz',
                    'native/sender-build-inputs/openssl-3.5.5.tar.gz.sha256',
                    'native/sender-build-inputs/boost-1.86.0-cmake.tar.xz',
                    'native/sender-build-inputs/boost-1.86.0-cmake.tar.xz.txt'}
        receiver = json.loads(committed.read('native/unified-receiver-manifest.json'))
        for name, row in receiver['libraries'].items():
            file = 'unified/native-receiver/' + name
            required.add(file)
            if file not in available or hashlib.sha256(content(file)).hexdigest() != row['sha256']:
                raise ValueError('Missing or mismatched unified receiver binary: ' + file)
        sender_inputs = json.loads(committed.read('native/sender-build-inputs/source-input-manifest.json'))
        for row in sender_inputs['archives']:
            file = 'native/sender-build-inputs/' + row['file']
            if file not in available or hashlib.sha256(content(file)).hexdigest() != row['sha256']:
                raise ValueError('Missing or mismatched sender source archive: ' + file)
        qr_inputs = json.loads(committed.read('native/browser-qr/SOURCE_LOCK.json'))
        for row in qr_inputs['bundled']:
            file = row['path']
            required.add(file)
            if file not in available or hashlib.sha256(content(file)).hexdigest() != row['sha256']:
                raise ValueError('Missing or mismatched browser QR source/notice: ' + file)
        if required - available:
            raise ValueError('Incomplete unified source: ' + str(sorted(required - available)))
        for name, (_, checksum) in pinned.items():
            if name in names and hashlib.sha256(committed.read(name)).hexdigest() != checksum:
                raise ValueError('Committed source differs from exact upstream pin: ' + name)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        inventory = {}
        with zipfile.ZipFile(args.output, 'x', zipfile.ZIP_DEFLATED, compresslevel=6) as out:
            for name in sorted(names | set(pinned)):
                data = committed.read(name) if name in names else pinned[name][0].read_bytes()
                inventory[name] = hashlib.sha256(data).hexdigest()
                info = zipfile.ZipInfo('MoonCast/' + name, (1980, 1, 1, 0, 0, 0))
                info.external_attr = (committed.getinfo(name).external_attr if name in names else 0o100644 << 16)
                info.compress_type = zipfile.ZIP_DEFLATED
                out.writestr(info, data)
            provenance = {'sourceCommit': commit, 'applicationId': 'com.mooncast.community',
                          'version': '0.6.0', 'files': inventory,
                          'externalWebRtc': {'lock': 'native/webrtc/SOURCE_LOCK.json',
                                            'sourceGuide': 'native/webrtc/README.md',
                                            'nativeSourceVendored': False,
                                            'nativeSdkLocallyRebuilt': False,
                                            'note': 'This archive is not a self-contained WebRTC/Chromium native source distribution.'},
                          'signing': 'No signing key included. Fixed community public certificate is recorded; owner-published test fixture remains in Git. Certificate: '+identity['certificateSha256']}
            out.writestr('MoonCast/CORRESPONDING-SOURCE.json', json.dumps(provenance, indent=2) + '\n')
    with zipfile.ZipFile(args.output) as archive:
        if archive.testzip() is not None:
            raise ValueError('Archive integrity check failed')
        for name, checksum in inventory.items():
            if hashlib.sha256(archive.read('MoonCast/' + name)).hexdigest() != checksum:
                raise ValueError('Archive verification failed: ' + name)
    print(json.dumps({'sourceCommit': commit, 'files': len(inventory), 'bytes': args.output.stat().st_size,
                      'sha256': hashlib.sha256(args.output.read_bytes()).hexdigest(),
                      'output': str(args.output)}, indent=2))


if __name__ == '__main__':
    main()
