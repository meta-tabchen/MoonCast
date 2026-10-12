#!/usr/bin/env python3
"""Package a TV preview from a clean, committed checkout, without changing any Git refs.

Run only after build, lint, behavior, payload and alignment checks. This helper
requires explicit release packaging and independently verifies the APK signature
against an externally selected certificate fingerprint. It never creates keys or
publishes assets; the workflow owns the other checks and GitHub publication.
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
from tv_release_inputs import generated_inputs

ROOT = Path(__file__).resolve().parents[1]
FORBIDDEN_PARTS = {'.git', '.gradle', '.cxx', '.idea', '__pycache__', 'build',
                   'local.properties', '.env', 'host-cert.pem', 'host-key.pem',
                   'paired-clients.json'}
FORBIDDEN_SUFFIXES = {'.jks', '.keystore', '.jceks', '.p12', '.pfx', '.pem', '.key',
                      '.crt', '.cer', '.der',
                      '.pyc', '.log'}


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT)


def require_committed_sources():
    if git('status', '--porcelain', '--untracked-files=all').strip():
        raise ValueError('Commit all source changes, including untracked files, before creating a corresponding-source archive')


def committed_source_archive():
    """Preserve every eligible Git blob, including export-ignore build inputs."""
    entries = []
    for record in git('ls-tree', '-r', '-z', 'HEAD').split(b'\0'):
        if not record:
            continue
        metadata, raw_name = record.split(b'\t', 1)
        mode, kind, oid = metadata.decode('ascii').split()
        name = raw_name.decode('utf-8')
        if not safe_source_name(name):
            continue
        if kind != 'blob' or mode not in ('100644', '100755'):
            raise ValueError('Unsupported committed source entry: ' + name)
        entries.append((name, mode, oid))
    data = subprocess.check_output(['git', 'cat-file', '--batch'], cwd=ROOT,
                                   input=''.join(oid + '\n' for _, _, oid in entries).encode('ascii'))
    output = io.BytesIO()
    offset = 0
    with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for name, mode, oid in entries:
            end = data.index(b'\n', offset)
            header = data[offset:end].decode('ascii').split()
            if len(header) != 3 or header[:2] != [oid, 'blob']:
                raise ValueError('Unexpected Git source object: ' + name)
            size = int(header[2]); offset = end + 1
            content = data[offset:offset + size]; offset += size
            if len(content) != size or data[offset:offset + 1] != b'\n':
                raise ValueError('Truncated Git source object: ' + name)
            offset += 1
            info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            info.external_attr = int(mode, 8) << 16
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, content)
    return output.getvalue()


def safe_source_name(name):
    path = PurePosixPath(name)
    return (not any(p in FORBIDDEN_PARTS or p.startswith('.env.') for p in path.parts)
            and path.suffix.lower() not in FORBIDDEN_SUFFIXES
            and path.parts[:4] != ('app', 'src', 'debug', 'jniLibs'))


def sha256(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(chunk)
    return digest.hexdigest()


def normalized_fingerprint(value):
    fingerprint = value.strip().replace(':', '').lower()
    if not re.fullmatch(r'[0-9a-f]{64}', fingerprint):
        raise ValueError('Expected signing certificate SHA-256 must be 64 hexadecimal digits (colons are optional)')
    return fingerprint


def check_signer_report(report, expected_fingerprint):
    """Validate apksigner's public-certificate report; no private key is inspected."""
    expected = normalized_fingerprint(expected_fingerprint)
    counts = re.findall(r'^Number of signers:\s*(\d+)\s*$', report, re.MULTILINE)
    fingerprints = re.findall(r'^Signer #(\d+) certificate SHA-256 digest:\s*([0-9a-fA-F:]+)\s*$',
                              report, re.MULTILINE)
    subjects = re.findall(r'^Signer #(\d+) certificate DN:\s*(.+)$', report, re.MULTILINE)
    if counts != ['1'] or len(fingerprints) != 1 or len(subjects) != 1:
        raise ValueError('Release APK must have exactly one verified signing certificate')
    if fingerprints[0][0] != '1' or subjects[0][0] != '1':
        raise ValueError('Unexpected release APK signer identity')
    if re.search(r'android\s+debug', subjects[0][1], re.IGNORECASE):
        raise ValueError('Android Debug signing identities cannot be packaged for publication')
    actual = normalized_fingerprint(fingerprints[0][1])
    if actual != expected:
        raise ValueError('APK signing certificate does not match the externally pinned SHA-256')
    return {'mode': 'external-keystore', 'certificateSha256': actual}


def verify_release_signing(apk, expected_fingerprint, sdk):
    # Validate the pin before invoking any external program.
    expected = normalized_fingerprint(expected_fingerprint)
    apksigner = sdk / 'build-tools/35.0.0' / ('apksigner.bat' if os.name == 'nt' else 'apksigner')
    if not apksigner.is_file():
        raise ValueError('Set ANDROID_HOME or --sdk to an SDK containing Build Tools 35.0.0')
    result = subprocess.run([str(apksigner), 'verify', '--verbose', '--print-certs', str(apk)],
                            capture_output=True, text=True, check=False)
    if result.returncode != 0:
        raise ValueError('APK signature verification failed; unsigned or invalid APKs cannot be packaged')
    return check_signer_report(result.stdout, expected)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--release-package', action='store_true',
                        help='Explicitly opt in to packaging a verified, externally signed release')
    parser.add_argument('--expected-signing-cert-sha256', default='')
    parser.add_argument('--sdk', type=Path,
                        default=Path(os.environ.get('ANDROID_HOME', os.environ.get('ANDROID_SDK_ROOT', ''))))
    args = parser.parse_args()
    if not args.release_package:
        parser.error('Release packaging is opt-in: pass --release-package and --expected-signing-cert-sha256')
    apk = args.apk.resolve()
    if not apk.is_file():
        parser.error('APK does not exist')
    try:
        signing = verify_release_signing(apk, args.expected_signing_cert_sha256, args.sdk.resolve())
    except (ValueError, OSError) as error:
        parser.error(str(error))
    try:
        require_committed_sources()
    except ValueError as error:
        parser.error(str(error))
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

    archived = committed_source_archive()
    with zipfile.ZipFile(io.BytesIO(archived)) as original:
        names = [n for n in original.namelist() if not n.endswith('/') and safe_source_name(n)]
        required = {'LICENSE', 'tv/build.gradle', 'tv/src/main/AndroidManifest.xml',
                    'gradle/tv-signing.gradle', 'docs/tv-signing.md',
                    'scripts/verify_tv_apk.py', 'scripts/package_tv_release.py',
                    'native/airplay/prebuilt-manifest.json', 'moonlight-core/SOURCE_LOCK.json',
                    'moonlight-core/SOURCE-INPUTS-SHA256SUMS', 'moonlight-core/JAVA-SOURCES.json',
                    'moonlight-core/scripts/prepare_moonlight_core.py', 'scripts/tv_release_inputs.py'}
        missing = required - set(names)
        if missing or not any(n.startswith('native/airplay/') for n in names):
            parser.error(f'Incomplete committed receiver/runtime source: {sorted(missing)}')
        runtime = json.loads(original.read('native/airplay/prebuilt-manifest.json'))
        if not runtime.get('sources'):
            parser.error('Runtime corresponding-source archives are not pinned')
        # Large runtime/source payloads are restored by committed preparation scripts.
        # Only explicitly pinned files may augment the committed Git archive.
        extras = {}
        for archived_name, (local, checksum) in generated_inputs(ROOT).items():
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

    target_apk = output / f'MoonCast-TV-{version}-{revision[:12]}-externally-signed-preview.apk'
    shutil.copyfile(apk, target_apk)
    info = {
        'applicationId': 'com.mooncast.tv', 'versionName': version,
        'sourceCommit': revision, 'tag': tag, 'signing': signing,
        'distribution': 'experimental prerelease; externally signed with a pinned certificate',
        'workflowRun': os.environ.get('GITHUB_RUN_ID'),
        'verificationScope': 'Release verification requires Java 17 build/lint, portable protocol/framing/session/PCM behavior, actual Media3 pipeline/sink, APK payload, signature and ZIP alignment checks. This packaging helper independently verifies the APK signature and pinned certificate; CI owns the other checks. They are not physical-device interoperability or A/V synchronization tests.',
        'deviceTests': 'Not run by this workflow. Android TV, iOS AirPlay and DLNA sender compatibility need real-device validation.',
        'apkSha256': sha256(target_apk), 'sourceSha256': sha256(source),
    }
    (output / 'BUILD_INFO.json').write_text(json.dumps(info, indent=2) + '\n')
    notes = f'''# MoonCast TV {version} preview

Standalone Android TV receiver (`com.mooncast.tv`), Android 8.0/API 26 or newer.
Source commit: `{revision}`.

**Experimental, externally signed preview.** The APK's verified signing certificate
SHA-256 is `{signing['certificateSha256']}`. Future upgrades must keep the same
signing identity and a compatible version code. Earlier ephemeral-debug-key
previews have a different identity; moving from those may still require a one-time
uninstall, which removes saved preferences and receiver pairing identity. This
workflow does not migrate an old signing identity or recover its private key.
The phone sender application has a separate package name. See `docs/tv-signing.md`
for user-managed signing and its limits; experimental status is unchanged.

## Receiver behavior and limits

- Open the app and AirPlay/DLNA reception starts automatically. AirPlay requires no PIN; only
  use it on a trusted home LAN because other reachable devices can connect.
- View-only MoonCast / GameStream client with explicit first-time TV PIN pairing,
  private host-certificate pinning, H.264 SDR and 48 kHz stereo. Install the phone
  MoonCast sender and approve screen capture there; this is not Android system
  Cast-list compatibility. Actual phone/TV interoperability remains unverified.
- GameStream uses the same framing controls with real 720p60/15 Mbps,
  1080p60/30 Mbps or 1080p30/20 Mbps requests. Low-latency output only;
  signed audio offsets are unavailable. Local Forget is not remote revocation.
- Redesigned TV home with clear remote focus and diagnostics in Advanced.
  Back during playback opens controls focused on Return to playback; Back
  again returns fullscreen. Crop-editor Back still cancels the edit.
- DLNA search bursts and large valid MX values are handled with bounded
  response scheduling. This does not add Miracast/Google Cast system mirroring;
  see docs/tv-android-system-casting.md for platform/OEM limits.
- Auto black-bar removal is the first-use default. Your chosen mode and manual
  adjustments are saved app-wide across sources/orientations. Detected video
  coordinates remain transient and are recalculated for each video/size.

- Live remote framing over the visible picture: four independent 0–45% crop
  edges (at least 20% retained per dimension), 100–300% proportional zoom and
  horizontal/vertical position. Preview, save, cancel and full-image reset;
  one app-wide preference. Auto never forces a portrait image to fill the TV.
- Conservative cinema crop supports wider borders and expands locked crops
  when repeated newly visible border content suggests captions or overlays.
  This remains a heuristic; manual crop can hide burned-in subtitles.
- Actual Media3 PCM audio calibration, −500…+500 ms in 50 ms steps. Positive
  values insert silence; negative values discard leading audio. Applying
  recreates media playback. PCM-only processing disables encoded passthrough,
  offload and other bypass paths; some HDMI-only formats may no longer play.
- Separate native AirPlay timing: movie defaults to 150 ms audio prebuffer
  with PTS-paced video; low latency defaults to adaptive audio buffering and
  immediate video output. Native signed audio offset is unavailable.
- Disconnected-mirror recovery defaults to
  15 seconds only after observed control TCP closure and continued video
  silence; static/paused screens with open connections remain valid. Decoder
  stalls are checked separately. Not every Wi-Fi failure is detected in 15
  seconds, and the receiver cannot force phone reconnection.
- Ordinary recovery keeps receiver identity. Only an explicitly confirmed
  global identity reset deletes the receiver key; there is no native paired
  device list or per-device revoke. AirPlay does not request a PIN; GameStream first-time pairing does.

## Verification and distribution

Required release gates: Java 17 debug/release builds, Android lint, portable
sender/receiver protocol, framing, session and PCM tests, the actual Media3
1.5.1 pipeline/sink check, native payload checks, APK signature and ZIP
alignment. This note lists the verification scope; it does not independently
attest a CI result. The packaging helper verifies the APK signature and certificate
pin; it does not run the other checks. The Moonlight core includes JVM lifecycle/protocol-selection tests; additional
host behavioral coverage is supplied by the portable test runners. CI also compiles the separate UI instrumentation
APK; compilation alone does not mean its Android tests ran. The scoped local
Android UI evidence and environment limitations are recorded in
`docs/tv-ui-validation-v03.md`; injected offline session navigation is not a
network playback or decoder test.
The corresponding-source archive includes retained native runtime source,
provenance and licenses. AirPlay/UxPlay remains pinned. GameStream JNI, common-C, OpenSSL and Opus
are built from fixed source in CI; the source archive includes the actual-build
manifest and corresponding source. Cross-path byte-identical native builds are
not claimed. GPLv3 obligations and dependency licenses are retained. `SHA256SUMS` covers the APK, source
and build metadata. Historical preview assets must not be overwritten.

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
