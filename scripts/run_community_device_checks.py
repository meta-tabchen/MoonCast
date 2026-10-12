#!/usr/bin/env python3
"""Validate real community APK updates and bilingual UI on a disposable emulator.

Requires official Android SDK adb, aapt and apksigner. Uses already-built, already-
signed APKs only; this script never signs, edits an APK, uninstalls, or clears app
storage. The emulator must not already contain com.mooncast.community. The release
55 -> release 56 check runs first, then matching-signed debug 56 replaces release
56 for actual instrumentation/screenshots. No screen capture permission is granted
and no remote cast/decoder interoperability is claimed.
"""
from __future__ import annotations
import argparse
import hashlib
import io
import json
from pathlib import Path, PurePosixPath
import re
import struct
import subprocess
import time
import tarfile
import uuid

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = 'com.mooncast.community'
TEST_PACKAGE = PACKAGE + '.test'
RUNNER = TEST_PACKAGE + '/com.mooncast.unified.CommunityLocaleInstrumentation'
BASELINE_VERSION_CODE = 55
CANDIDATE_VERSION_CODE = 56
VERSION_NAME = '0.6.0'


def digest(path: Path) -> str:
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def command(items, *, timeout=180, check=True):
    result = subprocess.run([str(item) for item in items], capture_output=True, text=True, timeout=timeout)
    if check and result.returncode:
        raise RuntimeError(f'Command failed ({result.returncode}): {items}\n{result.stdout}\n{result.stderr}')
    return result


def parse_badging(badging: str) -> dict | None:
    # Android test APKs may legitimately omit versionCode/versionName. Application
    # fixtures still must match exact numeric versions in main() before installation.
    line = re.search(r"^package: name='([^']+)'([^\n]*)", badging, re.M)
    if not line:
        return None
    code = re.search(r"\bversionCode='(\d*)'", line[2])
    name = re.search(r"\bversionName='([^']*)'", line[2])
    return {'applicationId': line[1],
            'versionCode': int(code[1]) if code and code[1] else None,
            'versionName': name[1] if name else ''}


def inspect_apk(path: Path, aapt: str, apksigner: str) -> dict:
    if not path.is_file():
        raise FileNotFoundError(path)
    badging = command([aapt, 'dump', 'badging', path]).stdout
    package = parse_badging(badging)
    if not package:
        raise RuntimeError(f'aapt could not identify {path}: {badging.splitlines()[:2]}')
    verification = command([apksigner, 'verify', '--verbose', '--print-certs', path]).stdout
    certificates = re.findall(r'^Signer #\d+ certificate SHA-256 digest:\s*([a-fA-F0-9]+)', verification, re.M)
    if len(certificates) != 1:
        raise RuntimeError(f'Expected one verified signing certificate: {path}')
    return {'filename': path.name, 'sha256': digest(path), 'applicationId': package['applicationId'],
            'versionCode': package['versionCode'], 'versionName': package['versionName'],
            'debuggable': bool(re.search(r'^application-debuggable', badging, re.M)),
            'certificateSha256': certificates[0].lower()}


def validate_fixtures(artifacts: dict, certificate: str) -> None:
    for name, code in (('baseline', BASELINE_VERSION_CODE), ('candidate', CANDIDATE_VERSION_CODE), ('debug', CANDIDATE_VERSION_CODE)):
        item = artifacts[name]
        if (item['applicationId'] != PACKAGE or item['versionCode'] != code
                or item.get('versionName') != VERSION_NAME or item['certificateSha256'] != certificate):
            raise RuntimeError(f'{name} has the wrong package/version/community certificate')
    if artifacts['baseline']['debuggable'] or artifacts['candidate']['debuggable'] or not artifacts['debug']['debuggable']:
        raise RuntimeError('Baseline/candidate must be release APKs; debug must be a debuggable APK')
    if artifacts['test']['applicationId'] != TEST_PACKAGE or artifacts['test']['certificateSha256'] != certificate:
        raise RuntimeError('androidTest package/signature must match the community debug target')


def installation_flags(package: str) -> list[str]:
    if package not in (PACKAGE, TEST_PACKAGE):
        raise ValueError('Unexpected install target')
    return ['-r', *(['-t'] if package == TEST_PACKAGE else [])]


def retry_stream_error(return_code: int, stdout: str, stderr: str) -> bool:
    message = stdout + stderr
    return bool(return_code and 'Broken pipe' in message and 'INSTALL_FAILED' not in message)


def extract_evidence(data: bytes, destination: Path) -> None:
    # run-as reads only this run's private debug-test files; never extract arbitrary
    # paths, links, device entries, modes or a general app-data backup.
    if len(data) > 128 * 1024 * 1024:
        raise RuntimeError('Evidence archive exceeds 128 MiB')
    with tarfile.open(fileobj=io.BytesIO(data), mode='r:') as archive:
        members = archive.getmembers()
        if len(members) > 128:
            raise RuntimeError('Too many evidence entries')
        seen = set()
        total = 0
        for member in members:
            if member.isdir() and member.name in ('.', './'):
                continue
            path = PurePosixPath(member.name)
            if not member.isreg() or len(path.parts) != 1 or not re.fullmatch(r'[A-Za-z0-9_.-]{1,160}', path.name) or path.name in ('.', '..') or path.name in seen:
                raise RuntimeError('Unsafe evidence archive entry: ' + member.name)
            total += member.size
            if member.size > 32 * 1024 * 1024 or total > 128 * 1024 * 1024:
                raise RuntimeError('Evidence payload exceeds size limit')
            seen.add(path.name)
        destination.mkdir(parents=True, exist_ok=True)
        for member in members:
            if member.isreg():
                stream = archive.extractfile(member)
                if stream is None:
                    raise RuntimeError('Evidence entry could not be read')
                content = stream.read()
                if len(content) != member.size:
                    raise RuntimeError('Truncated evidence entry')
                (destination / PurePosixPath(member.name).name).write_bytes(content)


def png_info(path: Path) -> dict:
    data = path.read_bytes()
    if len(data) < 24 or data[:8] != b'\x89PNG\r\n\x1a\n' or data[12:16] != b'IHDR':
        raise RuntimeError(f'Not a real PNG screenshot: {path}')
    width, height = struct.unpack('>II', data[16:24])
    if width < 100 or height < 100:
        raise RuntimeError(f'Invalid screenshot dimensions: {path}')
    return {'filename': path.name, 'sha256': digest(path), 'width': width, 'height': height,
            'source': 'Actual UiAutomation screenshot from debug APK on emulator; unmodified pixels'}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--baseline', type=Path, required=True, help=f'Synthetic community release baseline, versionCode {BASELINE_VERSION_CODE}, versionName {VERSION_NAME}')
    parser.add_argument('--candidate', type=Path, required=True, help=f'Community release APK, versionCode {CANDIDATE_VERSION_CODE}, versionName {VERSION_NAME}')
    parser.add_argument('--debug', type=Path, required=True, help=f'Same source/community certificate, debug APK versionCode {CANDIDATE_VERSION_CODE}, versionName {VERSION_NAME}')
    parser.add_argument('--test', type=Path, required=True, help='Matching androidTest APK containing the locale runner')
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--identity', type=Path, default=ROOT / 'signing/community/identity.json', help='Pinned public identity metadata produced by the user-initialized signing workflow')
    parser.add_argument('--serial', required=True, help='Disposable emulator serial, for example emulator-5554')
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--aapt', default='aapt')
    parser.add_argument('--apksigner', default='apksigner')
    args = parser.parse_args()
    identity = json.loads(args.identity.read_text(encoding='utf-8'))
    certificate = identity.get('certificateSha256', '').lower()
    if identity.get('applicationId') != PACKAGE or not re.fullmatch(r'[a-f0-9]{64}', certificate):
        raise SystemExit('Pinned public identity metadata is missing or invalid; initialize it through the approved workflow first')
    args.output.mkdir(parents=True, exist_ok=True)
    if any(args.output.iterdir()):
        raise SystemExit('Use an empty evidence directory so old screenshots cannot count as new results')
    adb_command = [args.adb, '-s', args.serial]

    def adb(*items, timeout=180, check=True):
        return command([*adb_command, *items], timeout=timeout, check=check)

    if adb('shell', 'getprop', 'ro.kernel.qemu').stdout.strip() != '1':
        raise SystemExit('Refusing installation/testing on a non-emulator device')
    if adb('shell', 'getprop', 'sys.boot_completed').stdout.strip() != '1':
        raise SystemExit('Emulator has not finished booting')
    if adb('shell', 'pm', 'path', PACKAGE, check=False).stdout.strip():
        raise SystemExit('Use a fresh disposable emulator; this script never uninstalls an existing community app')
    marker = uuid.uuid4().hex
    manifest = {'scope': 'Real APK upgrade and debug-emulator UI; not physical-phone/TV, capture, streaming, latency or decoder interoperability evidence',
                'runId': marker, 'runner': RUNNER, 'pinnedCertificateSha256': certificate, 'sdk': adb('shell', 'getprop', 'ro.build.version.sdk').stdout.strip(),
                'emulatorFingerprint': adb('shell', 'getprop', 'ro.build.fingerprint').stdout.strip(),
                'upgrade': {'baselineScope': f'Generated versionCode-{BASELINE_VERSION_CODE} compatibility baseline from {VERSION_NAME} source, not a claim about an earlier published release',
                            'baselineVersionCode': BASELINE_VERSION_CODE, 'candidateVersionCode': CANDIDATE_VERSION_CODE, 'versionName': VERSION_NAME}, 'phases': [], 'screenshots': [], 'passed': False}
    remote = f'files/community-locale/{marker}'

    def install(apk: Path, package: str):
        print(f"Installing {apk.name} on disposable {args.serial}", flush=True)
        flags = installation_flags(package)
        deadline = time.monotonic() + 90
        ready = 0
        readiness = []
        while time.monotonic() < deadline and ready < 2:
            try:
                probe = adb('shell', 'cmd', 'package', 'list', 'packages', '--user', '0', 'android', timeout=min(10, max(1, deadline-time.monotonic())), check=False)
                ok = probe.returncode == 0 and 'package:android' in probe.stdout.splitlines()
                readiness.append({'ok': ok, 'stdout': probe.stdout, 'stderr': probe.stderr})
            except subprocess.TimeoutExpired:
                ok = False
                readiness.append({'ok': False, 'error': 'Package-service readiness probe timed out'})
            ready = ready + 1 if ok else 0
            if ready < 2:
                time.sleep(min(3, max(0, deadline-time.monotonic())))
        (args.output / f'{apk.stem}-package-readiness.json').write_text(json.dumps(readiness, indent=2), encoding='utf-8')
        if ready < 2:
            raise RuntimeError('Emulator package service did not respond consistently within 90 seconds; application installation was not attempted')
        result = adb('install', *flags, apk, timeout=240, check=False)
        attempts = [{'mode': 'streamed-default', 'returnCode': result.returncode,
                     'stdout': result.stdout, 'stderr': result.stderr}]
        if retry_stream_error(result.returncode, result.stdout, result.stderr):
            # One standard file-transfer retry for the exact same validated APK.
            # Android's installer/signature checks remain intact; no downgrade,
            # permission grants, verification bypass, uninstall or data clearing.
            print(f'Stream transport broke; retrying {apk.name} once with --no-streaming', flush=True)
            result = adb('install', '--no-streaming', *flags, apk, timeout=240, check=False)
            attempts.append({'mode': 'no-streaming', 'returnCode': result.returncode,
                             'stdout': result.stdout, 'stderr': result.stderr})
        (args.output / f'{apk.stem}-install.json').write_text(json.dumps(attempts, indent=2), encoding='utf-8')
        if result.returncode or 'Success' not in result.stdout:
            raise RuntimeError(f'install -r did not succeed: {apk}\n{result.stdout}\n{result.stderr}')
        paths = adb('shell', 'pm', 'path', package).stdout.strip().splitlines()
        if len(paths) != 1 or not paths[0].startswith('package:'):
            raise RuntimeError(f'Expected one installed standalone APK: {package}')
        installed_hash = adb('shell', 'sha256sum', paths[0][len('package:'):]).stdout.split()[0]
        if installed_hash != digest(apk):
            raise RuntimeError(f'Installed bytes do not match {apk}')
        return {'operation': 'adb install -r -t' if package == TEST_PACKAGE else 'adb install -r', 'success': True, 'installedSha256': installed_hash}

    def instrument(phase: str, language='en', version=None):
        print(f"Running phase {phase}, language {language}", flush=True)
        if phase in ('flows', 'phone', 'tv'):
            # Only after the emulator/fresh-install assertions above; no credentials
            # or user device are involved. Boot-completed alone does not dismiss its UI.
            adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
            adb('shell', 'wm', 'dismiss-keyguard')
            policy = adb('shell', 'dumpsys', 'window', 'policy', check=False)
            (args.output / f'{phase}-{language}-window-policy.txt').write_text(policy.stdout, encoding='utf-8')
        options = ['shell', 'am', 'instrument', '-w', '-r', '-e', 'suite', 'locale', '-e', 'phase', phase,
                   '-e', 'language', language, '-e', 'run_id', marker, '-e', 'marker', marker]
        if version is not None:
            options += ['-e', 'expected_version', str(version)]
        result = adb(*options, RUNNER, timeout=600)
        text = result.stdout + '\n' + result.stderr
        (args.output / f'{phase}-{language}-instrumentation.txt').write_text(text, encoding='utf-8')
        success = 'COMMUNITY_UPGRADE_PASSED' if phase.startswith('upgrade-') else 'COMMUNITY_LOCALE_PASSED'
        if success not in text or 'COMMUNITY_LOCALE_FAILED' in text or 'INSTRUMENTATION_FAILED' in text:
            raise RuntimeError(f'{phase}/{language} failed. Inspect its instrumentation output; no screenshots are substituted.')
        if phase.startswith('upgrade-'):
            for line in text.splitlines():
                if '"result":"COMMUNITY_UPGRADE_PASSED"' in line:
                    return json.loads(line[line.index('{'):])
            raise RuntimeError('Upgrade marker was not returned as structured data')
        manifest['phases'].append({'phase': phase, 'language': language, 'passed': True})
        (args.output / 'manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
        pull_evidence(required=False)
        return None

    def pull_evidence(required: bool):
        # Android's run-as is available only for the installed debug variant. All
        # emulator/identity checks above still apply; no chmod or storage grant.
        try:
            result = subprocess.run([*adb_command, 'exec-out', 'run-as', PACKAGE,
                                     'tar', '-C', remote, '-cf', '-', '.'],
                                    capture_output=True, timeout=180)
            (args.output / 'pull.txt').write_text(result.stderr.decode('utf-8', errors='replace'), encoding='utf-8')
            if result.returncode:
                if required:
                    raise RuntimeError('Private debug-test evidence could not be downloaded with run-as')
                return
            extract_evidence(result.stdout, args.output / 'screens')
        except Exception as error:
            (args.output / 'pull.txt').write_text(str(error), encoding='utf-8')
            if required:
                raise

    try:
        artifacts = {name: inspect_apk(getattr(args, name), args.aapt, args.apksigner)
                     for name in ('baseline', 'candidate', 'debug', 'test')}
        manifest['artifacts'] = artifacts
        print(json.dumps({'fixtureMetadata': artifacts}, sort_keys=True), flush=True)
        validate_fixtures(artifacts, certificate)
        manifest['upgrade']['baselineInstall'] = install(args.baseline, PACKAGE)
        manifest['testInstall'] = install(args.test, TEST_PACKAGE)
        before = instrument('upgrade-seed', version=BASELINE_VERSION_CODE)
        manifest['upgrade']['before'] = before
        manifest['upgrade']['candidateInstall'] = install(args.candidate, PACKAGE)
        after = instrument('upgrade-verify', version=CANDIDATE_VERSION_CODE)
        manifest['upgrade']['after'] = after
        if before['marker'] != after['marker'] or before['firstInstallTime'] != after['firstInstallTime']:
            raise RuntimeError('Upgrade did not preserve the original installation and private data')
        if before['certificateSha256'] != certificate or after['certificateSha256'] != certificate:
            raise RuntimeError('On-device signing identity does not match the pinned community certificate')
        manifest['upgrade']['passed'] = True
        manifest['upgrade']['uninstallUsed'] = False
        (args.output / 'manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
        manifest['debugInstall'] = install(args.debug, PACKAGE)
        # Restore a regular phone-sized display after TV runs even when a test fails.
        adb('shell', 'wm', 'size', '1080x1920')
        adb('shell', 'wm', 'density', '360')
        for language in ('system', 'en', 'zh'):
            instrument('select', language)
            # am instrument starts a new target process. This second invocation checks
            # actual persisted language, not an in-memory value from the selecting run.
            instrument('verify', language)
        instrument('flows', 'en')
        for language in ('en', 'zh'):
            adb('shell', 'wm', 'size', '1080x1920')
            adb('shell', 'wm', 'density', '360')
            instrument('phone', language)
            adb('shell', 'wm', 'size', '1920x1080')
            adb('shell', 'wm', 'density', '240')
            instrument('tv', language)
        pull_evidence(required=True)
        screenshots = args.output / 'screens'
        required = [f'{role}-{lang}-{name}-debug-emulator.png'
                    for role, names in (('phone', ('01-phone-home', '03-phone-crop-options')),
                                        ('tv', ('01-tv-home', '02-tv-crop-settings')))
                    for lang in ('en', 'zh') for name in names]
        for name in required:
            if not (screenshots / name).is_file():
                raise RuntimeError(f'Mandatory actual screenshot missing: {name}')
        required_reports = [f'{phase}-{lang}-report.json' for phase in ('select', 'verify') for lang in ('system', 'en', 'zh')]
        required_reports += ['flows-en-report.json'] + [f'{role}-{lang}-report.json' for role in ('phone', 'tv') for lang in ('en', 'zh')]
        for name in required_reports:
            if not (screenshots / name).is_file():
                raise RuntimeError(f'Mandatory device report missing: {name}')
        for report_path in screenshots.glob('*-report.json'):
            report = json.loads(report_path.read_text(encoding='utf-8'))
            if report.get('passed') is not True:
                raise RuntimeError(f'Device reported an incomplete or failed phase: {report_path.name}')
            for item in report.get('screenshots', []):
                if not (screenshots / item['file']).is_file():
                    raise RuntimeError(f'Device report references an absent screenshot: {item["file"]}')
        manifest['screenshots'] = [png_info(path) for path in sorted(screenshots.glob('*.png'))]
        manifest['passed'] = True
        print(f'PASS: release {BASELINE_VERSION_CODE} -> {CANDIDATE_VERSION_CODE} install -r preserved private data and signing identity; actual English/Chinese debug-emulator phone/TV screenshots and locale tests passed.')
    except BaseException as failure:
        manifest['failure'] = str(failure)
        for label, arguments in (
            ('android-errors', ('logcat', '-d', '-t', '300', 'AndroidRuntime:E', 'ActivityManager:E', '*:S')),
            ('package-service', ('shell', 'service', 'check', 'package')),
            ('device-space', ('shell', 'df', '/data'))):
            try:
                diagnostic = adb(*arguments, timeout=20, check=False)
                (args.output / f'{label}.txt').write_text(diagnostic.stdout + '\n' + diagnostic.stderr, encoding='utf-8')
            except Exception as diagnostic_error:
                (args.output / f'{label}.txt').write_text(str(diagnostic_error), encoding='utf-8')
        pull_evidence(required=False)
        raise
    finally:
        adb('shell', 'wm', 'size', 'reset', check=False)
        adb('shell', 'wm', 'density', 'reset', check=False)
        manifest['files'] = {str(path.relative_to(args.output)): digest(path) for path in sorted(args.output.rglob('*'))
                             if path.is_file() and path.name != 'manifest.json'}
        (args.output / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__':
    main()
