#!/usr/bin/env python3
"""Validate real community APK updates and bilingual UI on a disposable emulator.

Requires official Android SDK adb, aapt and apksigner. Uses already-built, already-
signed APKs only; this script never signs, edits an APK, uninstalls, or clears app
storage. The emulator must not already contain com.mooncast.community. The release
50 -> release 51 check runs first, then matching-signed debug 51 replaces release
51 for actual instrumentation/screenshots. No screen capture permission is granted
and no remote cast/decoder interoperability is claimed.
"""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import re
import struct
import subprocess
import uuid

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = 'com.mooncast.community'
TEST_PACKAGE = PACKAGE + '.test'
RUNNER = TEST_PACKAGE + '/com.mooncast.unified.CommunityLocaleInstrumentation'


def digest(path: Path) -> str:
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def command(items, *, timeout=180, check=True):
    result = subprocess.run([str(item) for item in items], capture_output=True, text=True, timeout=timeout)
    if check and result.returncode:
        raise RuntimeError(f'Command failed ({result.returncode}): {items}\n{result.stdout}\n{result.stderr}')
    return result


def inspect_apk(path: Path, aapt: str, apksigner: str) -> dict:
    if not path.is_file():
        raise FileNotFoundError(path)
    badging = command([aapt, 'dump', 'badging', path]).stdout
    package = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']*)'", badging, re.M)
    if not package:
        raise RuntimeError(f'aapt could not identify {path}')
    verification = command([apksigner, 'verify', '--verbose', '--print-certs', path]).stdout
    certificates = re.findall(r'^Signer #\d+ certificate SHA-256 digest:\s*([a-fA-F0-9]+)', verification, re.M)
    if len(certificates) != 1:
        raise RuntimeError(f'Expected one verified signing certificate: {path}')
    return {'filename': path.name, 'sha256': digest(path), 'applicationId': package[1],
            'versionCode': int(package[2]), 'versionName': package[3],
            'debuggable': bool(re.search(r'^application-debuggable', badging, re.M)),
            'certificateSha256': certificates[0].lower()}


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
    parser.add_argument('--baseline', type=Path, required=True, help='Community release APK, versionCode 50')
    parser.add_argument('--candidate', type=Path, required=True, help='Community release APK, versionCode 51')
    parser.add_argument('--debug', type=Path, required=True, help='Same source/community certificate, debug APK versionCode 51')
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
                'upgrade': {'baselineScope': 'Generated versionCode-50 compatibility baseline, not a claim about an earlier published release'}, 'phases': [], 'screenshots': [], 'passed': False}
    remote = f'/sdcard/Android/data/{PACKAGE}/files/community-locale/{marker}'

    def install(apk: Path, package: str):
        result = adb('install', '-r', apk, timeout=240)
        if 'Success' not in result.stdout:
            raise RuntimeError(f'install -r did not succeed: {apk}\n{result.stdout}\n{result.stderr}')
        paths = adb('shell', 'pm', 'path', package).stdout.strip().splitlines()
        if len(paths) != 1 or not paths[0].startswith('package:'):
            raise RuntimeError(f'Expected one installed standalone APK: {package}')
        installed_hash = adb('shell', 'sha256sum', paths[0][len('package:'):]).stdout.split()[0]
        if installed_hash != digest(apk):
            raise RuntimeError(f'Installed bytes do not match {apk}')
        return {'operation': 'adb install -r', 'success': True, 'installedSha256': installed_hash}

    def instrument(phase: str, language='en', version=None):
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
        return None

    def pull_evidence(required: bool):
        (args.output / 'screens').mkdir(exist_ok=True)
        result = adb('pull', remote + '/.', args.output / 'screens', timeout=180, check=False)
        (args.output / 'pull.txt').write_text(result.stdout + '\n' + result.stderr, encoding='utf-8')
        if required and result.returncode:
            raise RuntimeError('Device evidence could not be downloaded')

    try:
        artifacts = {name: inspect_apk(getattr(args, name), args.aapt, args.apksigner)
                     for name in ('baseline', 'candidate', 'debug', 'test')}
        manifest['artifacts'] = artifacts
        for name, code in (('baseline', 50), ('candidate', 51), ('debug', 51)):
            item = artifacts[name]
            if item['applicationId'] != PACKAGE or item['versionCode'] != code or item['certificateSha256'] != certificate:
                raise RuntimeError(f'{name} has the wrong package/version/community certificate')
        if artifacts['baseline']['debuggable'] or artifacts['candidate']['debuggable'] or not artifacts['debug']['debuggable']:
            raise RuntimeError('Baseline/candidate must be release APKs; debug must be a debuggable APK')
        if artifacts['test']['applicationId'] != TEST_PACKAGE or artifacts['test']['certificateSha256'] != certificate:
            raise RuntimeError('androidTest package/signature must match the community debug target')
        manifest['upgrade']['baselineInstall'] = install(args.baseline, PACKAGE)
        manifest['testInstall'] = install(args.test, TEST_PACKAGE)
        before = instrument('upgrade-seed', version=50)
        manifest['upgrade']['before'] = before
        manifest['upgrade']['candidateInstall'] = install(args.candidate, PACKAGE)
        after = instrument('upgrade-verify', version=51)
        manifest['upgrade']['after'] = after
        if before['marker'] != after['marker'] or before['firstInstallTime'] != after['firstInstallTime']:
            raise RuntimeError('Upgrade did not preserve the original installation and private data')
        if before['certificateSha256'] != certificate or after['certificateSha256'] != certificate:
            raise RuntimeError('On-device signing identity does not match the pinned community certificate')
        manifest['upgrade']['passed'] = True
        manifest['upgrade']['uninstallUsed'] = False
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
        print('PASS: release 50 -> 51 install -r preserved private data and signing identity; actual English/Chinese debug-emulator phone/TV screenshots and locale tests passed.')
    except BaseException as failure:
        manifest['failure'] = str(failure)
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
