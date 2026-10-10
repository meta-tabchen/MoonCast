#!/usr/bin/env python3
"""Package a TV preview from a clean, committed checkout, without changing any Git refs.

Run only after build, lint, behavior, payload, signature and alignment checks. This
helper packages artifacts; the workflow owns the checks and GitHub publication.
"""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
FORBIDDEN_PARTS = {'.git', '.gradle', '.cxx', '.idea', '__pycache__', 'build',
                   'local.properties', '.env', 'host-cert.pem', 'host-key.pem',
                   'paired-clients.json'}
FORBIDDEN_SUFFIXES = {'.jks', '.keystore', '.pyc', '.log'}


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT)


def safe_source_name(name):
    path = PurePosixPath(name)
    return (not any(p in FORBIDDEN_PARTS or p.startswith('.env.') for p in path.parts)
            and path.suffix not in FORBIDDEN_SUFFIXES
            and path.parts[:4] != ('app', 'src', 'debug', 'jniLibs'))


def sha256(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(chunk)
    return digest.hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    apk = args.apk.resolve()
    if not apk.is_file():
        parser.error('APK does not exist')
    if git('status', '--porcelain', '--untracked-files=no').strip():
        parser.error('Commit tracked source changes before creating a corresponding-source archive')
    revision = git('rev-parse', 'HEAD').decode().strip()
    if os.environ.get('GITHUB_SHA') and os.environ['GITHUB_SHA'] != revision:
        parser.error('Checkout commit does not match this workflow run')
    version_text = git('show', 'HEAD:tv/build.gradle').decode()
    match = re.search(r"versionName\s+'([A-Za-z0-9.\-]+)'", version_text)
    if not match:
        parser.error('Cannot read a safe receiver version from the committed module')
    version = match.group(1)
    run = os.environ.get('GITHUB_RUN_NUMBER', 'local')
    attempt = os.environ.get('GITHUB_RUN_ATTEMPT', '1')
    if not re.fullmatch(r'(?:local|\d+)', run) or not attempt.isdigit():
        parser.error('Unexpected CI run identifier')
    tag = f'tv-v{version}.{run}.{attempt}-{revision[:12]}'
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    if any(output.iterdir()):
        parser.error('Output directory must be empty; historical assets must not be overwritten')

    archived = git('archive', '--format=zip', 'HEAD')
    with zipfile.ZipFile(io.BytesIO(archived)) as original:
        names = [n for n in original.namelist() if not n.endswith('/') and safe_source_name(n)]
        required = {'LICENSE', 'tv/build.gradle', 'tv/src/main/AndroidManifest.xml',
                    'scripts/verify_tv_apk.py', 'scripts/package_tv_release.py',
                    'native/airplay/prebuilt-manifest.json'}
        missing = required - set(names)
        if missing or not any(n.startswith('native/airplay/') for n in names):
            parser.error(f'Incomplete committed receiver/runtime source: {sorted(missing)}')
        runtime = json.loads(original.read('native/airplay/prebuilt-manifest.json'))
        if not runtime.get('sources'):
            parser.error('Runtime corresponding-source archives are not pinned')
        # Large runtime/source payloads are restored by committed preparation scripts.
        # Only explicitly pinned files may augment the committed Git archive.
        extras = {}
        for prefix, pinned in (('native/airplay/', runtime['sources']),
                               ('tv/src/main/jniLibs/', runtime['libraries'])):
            for name, checksum in pinned.items():
                relative = PurePosixPath(name)
                if relative.is_absolute() or '..' in relative.parts:
                    parser.error(f'Unsafe pinned payload path: {name}')
                archived_name = prefix + name
                local = ROOT / archived_name
                if not local.is_file() or local.is_symlink() or sha256(local) != checksum:
                    parser.error(f'Missing or mismatched manifest-pinned release input: {archived_name}')
                if archived_name in names:
                    if hashlib.sha256(original.read(archived_name)).hexdigest() != checksum:
                        parser.error(f'Committed runtime input differs from its manifest: {archived_name}')
                else:
                    extras[archived_name] = local
        source = output / f'MoonCast-TV-{version}-{revision[:12]}-source.zip'
        with zipfile.ZipFile(source, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as target:
            for name in sorted(names):
                data = original.read(name)
                info = original.getinfo(name)
                info.filename = 'MoonCast/' + name
                target.writestr(info, data)
            for name, local in sorted(extras.items()):
                info = zipfile.ZipInfo('MoonCast/' + name, (1980, 1, 1, 0, 0, 0))
                info.external_attr = 0o100644 << 16
                info.compress_type = zipfile.ZIP_DEFLATED
                target.writestr(info, local.read_bytes())

    target_apk = output / f'MoonCast-TV-{version}-{revision[:12]}-debug-key-preview.apk'
    shutil.copyfile(apk, target_apk)
    info = {
        'applicationId': 'com.mooncast.tv', 'versionName': version,
        'sourceCommit': revision, 'tag': tag, 'signing': 'ephemeral Android debug key',
        'distribution': 'experimental prerelease; not production-signed',
        'workflowRun': os.environ.get('GITHUB_RUN_ID'),
        'verificationScope': 'Build, lint, portable behavior, APK payload, signature and ZIP alignment checks are CI gates. These are not physical-device interoperability tests.',
        'deviceTests': 'Not run by this workflow. Android TV, iOS AirPlay and DLNA sender compatibility need real-device validation.',
        'apkSha256': sha256(target_apk), 'sourceSha256': sha256(source),
    }
    (output / 'BUILD_INFO.json').write_text(json.dumps(info, indent=2) + '\n')
    notes = f'''# MoonCast TV {version} preview

Standalone Android TV receiver (`com.mooncast.tv`), Android 8.0/API 26 or newer.
Source commit: `{revision}`.

**Experimental, debug-key-signed preview.** This runner's ephemeral signing key
can differ from earlier previews; Android may require uninstalling an older TV
preview before installation, which removes that app's saved preferences.
The phone sender application has a separate package name.

CI gates: debug/release builds, Android lint, portable sender/receiver tests,
native payload checks, APK signature and ZIP alignment. The Gradle unit-test
task has no separate Android test sources; behavioral coverage is from the
portable test runners.
The corresponding-source archive includes retained native runtime source,
provenance and licenses. `SHA256SUMS` covers the APK, source and build metadata.

No physical Android TV or iOS sender was exercised by this workflow. Real AirPlay
and DLNA interoperability, reconnect/stop behavior, audio/video synchronization,
decoder compatibility and sustained performance remain device-test requirements.
AirPlay support is experimental; protected/DRM playback and universal iOS/app
compatibility are not promised. Use only on a trusted local network.

See `docs/tv-receiver.md` in the source archive for setup and current limits.
'''
    (output / 'RELEASE_NOTES.md').write_text(notes)
    assets = [target_apk, source, output / 'BUILD_INFO.json']
    (output / 'SHA256SUMS').write_text(''.join(f'{sha256(p)}  {p.name}\n' for p in sorted(assets)))
    if os.environ.get('GITHUB_OUTPUT'):
        with open(os.environ['GITHUB_OUTPUT'], 'a') as stream:
            stream.write(f'tag={tag}\n')
    print(json.dumps({'tag': tag, 'output': str(output), 'sourceCommit': revision}, indent=2))


if __name__ == '__main__':
    main()
