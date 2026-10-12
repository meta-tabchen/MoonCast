#!/usr/bin/env python3
"""One bounded startup investigation on a fresh disposable API35 emulator.

Build only debug/androidTest from source 27967, then use --prepare-only to retain
both APKs and their verified metadata BEFORE booting the emulator. Run again with
the same arguments and --serial. This script never builds, signs, uninstalls,
clears storage, changes permissions, or retries failed instrumentation. Optional
--ui-after-success uses the existing real UI tests after all three startup checks.
"""
from __future__ import annotations
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
import uuid
import zipfile

import run_community_device_checks as shared

CERTIFICATE = '290e4372fff33c00848758b12275936900b2eccad053dc63c18622e1a1222bb2'
SOURCE_COMMIT = '27967d778c55042bb2adc926074bc3b1d94e19b9'
ORIGINAL_HASHES = {
    'debug': '18142c09116fdab2bebcc26cd92de2117c68d648cfc7c81ae7e71600d1d3d4fd',
    'test': 'd5bfa343ffa6f7aff01765cad300c660cac6b498c3e1ac92123442d1a7106361',
}
STARTUP_PHASES = (('verify', 'system'), ('select', 'system'), ('verify', 'system'))
UI_PHASES = (('flows', 'en'), ('phone', 'en'), ('tv', 'en'), ('phone', 'zh'), ('tv', 'zh'))


def utc():
    return datetime.now(timezone.utc).isoformat()


def write_json(path, value):
    temporary = path.with_name(path.name + '.tmp')
    temporary.write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    temporary.replace(path)


def validate_debug_fixtures(artifacts):
    # The shared release validator additionally requires baseline/candidate APKs.
    # Retain its real debug/test guards here without inventing release fixtures.
    for key, package in (('debug', shared.PACKAGE), ('test', shared.TEST_PACKAGE)):
        item = artifacts[key]
        if item['applicationId'] != package or item['certificateSha256'] != CERTIFICATE:
            raise RuntimeError(f'{key}: wrong package or pinned community certificate')
        if not item['debuggable']:
            raise RuntimeError(f'{key}: expected a debuggable fixture')
    if artifacts['debug']['versionCode'] != 51:
        raise RuntimeError('Debug target must retain versionCode 51')
    if artifacts['debug']['abis'] != ['x86_64'] or artifacts['test']['abis'] not in ([], ['x86_64']):
        raise RuntimeError('Only the reconstructed x86_64 fixtures are allowed')


def comparison(artifacts):
    return {key: {'originalSha256': original, 'reconstructedSha256': artifacts[key]['sha256'],
                  'matchesOriginalBytes': original == artifacts[key]['sha256']}
            for key, original in ORIGINAL_HASHES.items()}


def instrumentation_passed(result, phase):
    output = result.stdout + '\n' + result.stderr
    return (result.returncode == 0
            and re.search(r'\bCOMMUNITY_LOCALE_PASSED\s+' + re.escape(phase) + r'\b', output) is not None
            and not any(marker in output for marker in ('COMMUNITY_LOCALE_FAILED', 'INSTRUMENTATION_FAILED', 'Process crashed.')))


def has_main_trace(output):
    thread = re.search(r'^"main"[^\n]*\btid=\d+[^\n]*\n(.*?)(?=^"|\Z)', output, re.M | re.S)
    return bool(thread and re.search(r'^\s+at\s+\S+', thread[1], re.M))


def has_target_trace(output):
    # DropBox ANRs may include other processes/threads. Do not skip bugreport
    # merely because a system_server or worker-thread stack was present.
    return any(re.search(r'^Cmd line: ' + re.escape(shared.PACKAGE) + r'\s*$', block, re.M)
               and has_main_trace(block)
               for block in re.split(r'(?m)^----- (?:pid|end) \d+[^\n]*\n', output))


def prepare(args):
    if args.source_commit != SOURCE_COMMIT:
        raise RuntimeError('--source-commit must be the full original 27967… build-source commit')
    identity = json.loads(args.identity.read_text(encoding='utf-8'))
    if identity.get('applicationId') != shared.PACKAGE or identity.get('certificateSha256', '').lower() != CERTIFICATE:
        raise RuntimeError('Public identity metadata does not match the pinned community identity')
    artifacts = {}
    for key in ORIGINAL_HASHES:
        apk = getattr(args, key)
        artifacts[key] = shared.inspect_apk(apk, args.aapt, args.apksigner)
        with zipfile.ZipFile(apk) as archive:
            artifacts[key]['abis'] = sorted({name.split('/')[1] for name in archive.namelist()
                                             if re.fullmatch(r'lib/[^/]+/[^/]+\.so', name)})
    validate_debug_fixtures(artifacts)
    args.output.mkdir(parents=True, exist_ok=True)
    manifest_path = args.output / 'manifest.json'
    if manifest_path.exists():
        manifest = json.loads(manifest_path.read_text(encoding='utf-8'))
        if manifest.get('state') != 'prepared' or manifest.get('sourceCommitReportedByBuild') != args.source_commit:
            raise RuntimeError('Only an untouched prepared run may be resumed; do not rerun until green')
        for key in ORIGINAL_HASHES:
            saved = args.output / 'apks' / (key + '.apk')
            if manifest['artifacts'][key] != artifacts[key] or shared.digest(saved) != artifacts[key]['sha256']:
                raise RuntimeError('Prepared fixture bytes/metadata changed')
        return manifest
    if any(args.output.iterdir()):
        raise RuntimeError('Use an empty output directory or this script\'s untouched prepared run')
    (args.output / 'apks').mkdir()
    for key in ORIGINAL_HASHES:
        saved = args.output / 'apks' / (key + '.apk')
        shutil.copyfile(getattr(args, key), saved)
        if shared.digest(saved) != artifacts[key]['sha256']:
            raise RuntimeError('Retained fixture hash mismatch')
    manifest = {
        'scope': 'One API35 debug startup diagnostic; no release upgrade, UI, casting or physical-device proof unless separately recorded below',
        'sourceCommitReportedByBuild': args.source_commit,
        'sourceProvenanceNote': 'The build workflow must check out this commit; this harness independently verifies APK identity and bytes, not source-to-binary equivalence',
        'originalCiRun': '38203238592', 'runId': uuid.uuid4().hex,
        'runner': shared.RUNNER, 'pinnedCertificateSha256': CERTIFICATE,
        'artifacts': artifacts, 'originalByteComparison': comparison(artifacts),
        'preparedAt': utc(), 'state': 'prepared', 'startupPassed': False, 'diagnosticPassed': False,
        'ui': {'requested': False, 'passed': None}, 'phases': [], 'timeline': [],
        'screenshots': [], 'passed': False,
    }
    write_json(manifest_path, manifest)
    return manifest


def run(args, manifest):
    if not args.serial or not re.fullmatch(r'emulator-\d+', args.serial):
        raise RuntimeError('An explicit disposable emulator serial is required')
    adb_base = [args.adb, '-s', args.serial]
    path = args.output / 'manifest.json'
    logcat = None
    log_handles = []
    display_changed = False
    device_confirmed = False
    start = time.monotonic()

    def save():
        write_json(path, manifest)

    def event(label, **fields):
        manifest['timeline'].append({'event': label, 'utc': utc(), 'elapsedSeconds': round(time.monotonic() - start, 3), **fields})
        save()

    def adb(*items, timeout=30):
        return shared.command([*adb_base, *items], timeout=timeout)

    def capture(name, items, timeout=20):
        result = {'file': name, 'command': items, 'startedAt': utc()}
        try:
            completed = shared.command([*adb_base, *items], timeout=timeout, check=False)
            output = completed.stdout + '\n' + completed.stderr
            result['returnCode'] = completed.returncode
        except subprocess.TimeoutExpired as error:
            def decode(value):
                return value.decode('utf-8', errors='replace') if isinstance(value, bytes) else (value or '')
            output = decode(error.stdout) + '\n' + decode(error.stderr)
            result['error'] = f'Timed out after {timeout} seconds'
        except OSError as error:
            output = ''
            result['error'] = str(error)
        (args.output / name).write_text(output, encoding='utf-8')
        result['finishedAt'] = utc()
        manifest.setdefault('diagnosticCommands', []).append(result)
        save()
        return output

    def snapshot(label):
        capture(label + '-cpuinfo.txt', ['shell', 'dumpsys', 'cpuinfo'], timeout=10)
        capture(label + '-pressure.txt', ['shell', 'cat', '/proc/pressure/cpu'], timeout=10)

    def pull(destination):
        result = subprocess.run([*adb_base, 'exec-out', 'run-as', shared.PACKAGE, 'tar', '-C',
                                 f'files/community-locale/{manifest["runId"]}', '-cf', '-', '.'],
                                capture_output=True, timeout=30)
        if result.returncode:
            raise RuntimeError('Evidence pull failed: ' + result.stderr.decode('utf-8', errors='replace'))
        shared.extract_evidence(result.stdout, destination)

    def failure_diagnostics():
        # Run once after the FIRST failure. A later diagnostic failure never erases it.
        event('first-failure-diagnostics')
        anr = capture('failure-lastanr.txt', ['shell', 'dumpsys', 'activity', 'lastanr'])
        capture('failure-exit-info.txt', ['shell', 'dumpsys', 'activity', 'exit-info', shared.PACKAGE])
        anr += capture('failure-dropbox-anr.txt', ['shell', 'dumpsys', 'dropbox', '--print', 'data_app_anr'])
        capture('failure-logcat-all.txt', ['logcat', '-b', 'all', '-d', '-v', 'threadtime'])
        snapshot('failure')
        manifest['mainThreadTraceObserved'] = has_target_trace(anr)
        if args.bugreport_if_needed and not manifest['mainThreadTraceObserved']:
            capture('bugreport-command.txt', ['bugreport', str(args.output / 'startup-bugreport.zip')], timeout=120)
        save()

    def instrument(index, phase, language, category):
        if logcat is None or logcat.poll() is not None:
            raise RuntimeError('Continuous logcat is not running; refusing an unrecorded phase')
        label = f'{index:02d}-{phase}-{language}'
        row = {'phase': phase, 'language': language, 'category': category, 'passed': False, 'startedAt': utc()}
        manifest['phases'].append(row)
        event('phase-start', phase=label)
        snapshot(label + '-before')
        if category == 'ui':
            adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
            adb('shell', 'wm', 'dismiss-keyguard')
        command = [*adb_base, 'shell', 'am', 'instrument', '-w', '-r', '-e', 'suite', 'locale',
                   '-e', 'phase', phase, '-e', 'language', language,
                   '-e', 'run_id', manifest['runId'], shared.RUNNER]
        print(f'Running {label}; no instrumentation retry', flush=True)
        try:
            try:
                result = shared.command(command, timeout=600, check=False)
            except subprocess.TimeoutExpired as error:
                (args.output / (label + '-instrumentation.txt')).write_bytes(
                    (error.stdout if isinstance(error.stdout, bytes) else (error.stdout or '').encode()) + b'\n' +
                    (error.stderr if isinstance(error.stderr, bytes) else (error.stderr or '').encode()))
                raise RuntimeError(f'{label}: instrumentation exceeded 600 seconds') from error
            (args.output / (label + '-instrumentation.txt')).write_text(result.stdout + '\n' + result.stderr, encoding='utf-8')
            row['returnCode'] = result.returncode
            if not instrumentation_passed(result, phase):
                raise RuntimeError(f'{label}: instrumentation failed')
            evidence = args.output / (label + '-evidence')
            pull(evidence)
            report_file = evidence / f'{phase}-{language}-report.json'
            report = json.loads(report_file.read_text(encoding='utf-8'))
            if report.get('passed') is not True or report.get('phase') != phase or report.get('language') != language or not report.get('checks'):
                raise RuntimeError(f'{label}: missing/mismatched/failed device report')
            row['deviceReport'] = str(report_file.relative_to(args.output))
            row['deviceReportSha256'] = shared.digest(report_file)
            for shot in report.get('screenshots', []):
                shared.png_info(evidence / shot['file'])
            row['passed'] = True
        except Exception as error:
            row['failure'] = str(error)
            raise
        finally:
            row['finishedAt'] = utc()
            event('phase-end', phase=label, passed=row['passed'])

    manifest['state'] = 'running'
    manifest['ui']['requested'] = args.ui_after_success
    save()
    try:
        if adb('shell', 'getprop', 'ro.kernel.qemu').stdout.strip() != '1':
            raise RuntimeError('Refusing a non-emulator target')
        if adb('shell', 'getprop', 'ro.build.version.sdk').stdout.strip() != '35':
            raise RuntimeError('This single diagnostic is restricted to API35')
        if adb('shell', 'getprop', 'sys.boot_completed').stdout.strip() != '1':
            raise RuntimeError('Emulator has not completed boot')
        for probe in range(2):
            packages = adb('shell', 'cmd', 'package', 'list', 'packages', '--user', '0').stdout.splitlines()
            if 'package:android' not in packages:
                raise RuntimeError('Package service is not ready')
            if any('package:' + package in packages for package in (shared.PACKAGE, shared.TEST_PACKAGE)):
                raise RuntimeError('Use a fresh emulator without either diagnostic package')
            if probe == 0:
                time.sleep(3)
        device_confirmed = True
        manifest['sdk'] = '35'
        manifest['emulatorFingerprint'] = adb('shell', 'getprop', 'ro.build.fingerprint').stdout.strip()
        event('fresh-api35-emulator-confirmed')
        log_handles = [(args.output / name).open('wb') for name in ('logcat-all.txt', 'logcat-stderr.txt')]
        logcat = subprocess.Popen([*adb_base, 'logcat', '-b', 'all', '-v', 'threadtime'], stdout=log_handles[0], stderr=log_handles[1])
        event('continuous-logcat-started')
        snapshot('before-install')
        for key, package in (('debug', shared.PACKAGE), ('test', shared.TEST_PACKAGE)):
            apk = args.output / 'apks' / (key + '.apk')
            event('install-start', fixture=key)
            result = shared.command([*adb_base, 'install', '--no-streaming', *shared.installation_flags(package), apk], timeout=240, check=False)
            (args.output / (key + '-install.txt')).write_text(result.stdout + '\n' + result.stderr, encoding='utf-8')
            if result.returncode or 'Success' not in result.stdout:
                raise RuntimeError(f'{key}: installation failed; no retry')
            paths = adb('shell', 'pm', 'path', package).stdout.strip().splitlines()
            if len(paths) != 1 or not paths[0].startswith('package:'):
                raise RuntimeError('Expected one installed standalone APK')
            if adb('shell', 'sha256sum', paths[0][len('package:'):]).stdout.split()[0] != manifest['artifacts'][key]['sha256']:
                raise RuntimeError('Installed APK differs from retained fixture')
            event('install-verified', fixture=key)
        if logcat.poll() is not None:
            raise RuntimeError('Continuous logcat exited before instrumentation')
        for index, (phase, language) in enumerate(STARTUP_PHASES, 1):
            instrument(index, phase, language, 'startup')
        manifest['startupPassed'] = True
        manifest['diagnosticPassed'] = True
        event('all-three-startup-checks-passed')
        if args.ui_after_success:
            for index, (phase, language) in enumerate(UI_PHASES, 4):
                display_changed = True
                adb('shell', 'wm', 'size', '1920x1080' if phase == 'tv' else '1080x1920')
                adb('shell', 'wm', 'density', '240' if phase == 'tv' else '360')
                instrument(index, phase, language, 'ui')
            manifest['ui']['passed'] = True
        pull(args.output / 'screens')
        if args.ui_after_success:
            for role, names in (('phone', ('01-phone-home', '03-phone-crop-options')),
                                ('tv', ('01-tv-home', '02-tv-crop-settings'))):
                for language in ('en', 'zh'):
                    for name in names:
                        shared.png_info(args.output / 'screens' / f'{role}-{language}-{name}-debug-emulator.png')
        if logcat.poll() is not None:
            raise RuntimeError('Continuous logcat ended unexpectedly during the diagnostic')
        manifest['screenshots'] = [shared.png_info(p) for p in sorted((args.output / 'screens').glob('*.png'))]
        manifest['passed'] = True
        manifest['state'] = 'completed'
        event('diagnostic-completed', startupPassed=True, uiPassed=manifest['ui']['passed'])
        print('PASS: three startup/resource checks. UI proof: ' + ('existing UI phases passed' if args.ui_after_success else 'not attempted'), flush=True)
    except BaseException as error:
        manifest['failure'] = str(error)
        manifest['state'] = 'failed'
        if args.ui_after_success and manifest['startupPassed']:
            manifest['ui']['passed'] = False
        event('first-failure', error=str(error))
        if device_confirmed:
            try:
                failure_diagnostics()
            except Exception as diagnostic_error:
                manifest['diagnosticFailure'] = str(diagnostic_error)
            try:
                pull(args.output / 'screens')
            except Exception as pull_error:
                manifest['evidencePullFailure'] = str(pull_error)
        raise
    finally:
        if logcat is not None:
            if logcat.poll() is None:
                logcat.terminate()
                try:
                    logcat.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    logcat.kill()
                    logcat.wait(timeout=5)
            manifest['logcatExitCode'] = logcat.returncode
        for handle in log_handles:
            handle.close()
        if display_changed:
            capture('restore-display-size.txt', ['shell', 'wm', 'size', 'reset'])
            capture('restore-display-density.txt', ['shell', 'wm', 'density', 'reset'])
        manifest['finishedAt'] = utc()
        manifest['files'] = {str(p.relative_to(args.output)): shared.digest(p)
                             for p in sorted(args.output.rglob('*')) if p.is_file() and p != path and p.suffix != '.tmp'}
        save()


def self_test():
    import copy
    import io
    import tarfile
    import tempfile
    import unittest
    from unittest.mock import patch

    class Fixtures(unittest.TestCase):
        def fixture(self):
            return {key: {'applicationId': package, 'certificateSha256': CERTIFICATE,
                          'debuggable': True, 'versionCode': version, 'abis': abi,
                          'sha256': ORIGINAL_HASHES[key]}
                    for key, package, version, abi in (('debug', shared.PACKAGE, 51, ['x86_64']),
                                                       ('test', shared.TEST_PACKAGE, None, []))}

        def test_valid_optional_test_version(self):
            validate_debug_fixtures(self.fixture())

        def test_identity_and_debug_guards(self):
            for key in ('debug', 'test'):
                for field, value in (('applicationId', 'wrong'), ('certificateSha256', '0' * 64), ('debuggable', False)):
                    bad = copy.deepcopy(self.fixture()); bad[key][field] = value
                    with self.subTest(key=key, field=field), self.assertRaises(RuntimeError):
                        validate_debug_fixtures(bad)

        def test_version_and_abi_guards(self):
            for key, field, value in (('debug', 'versionCode', None), ('debug', 'versionCode', 50),
                                       ('debug', 'abis', []), ('debug', 'abis', ['arm64-v8a']),
                                       ('test', 'abis', ['armeabi-v7a'])):
                bad = copy.deepcopy(self.fixture()); bad[key][field] = value
                with self.subTest(key=key, field=field), self.assertRaises(RuntimeError):
                    validate_debug_fixtures(bad)

        def test_hash_mismatch_is_reported_not_hidden(self):
            rows = self.fixture()
            self.assertTrue(all(r['matchesOriginalBytes'] for r in comparison(rows).values()))
            rows['debug']['sha256'] = 'f' * 64
            self.assertFalse(comparison(rows)['debug']['matchesOriginalBytes'])
            validate_debug_fixtures(rows)

        def test_phase_success_requires_actual_marker(self):
            for code, output, expected in ((0, 'COMMUNITY_LOCALE_PASSED verify', True),
                                          (0, 'COMMUNITY_LOCALE_PASSED select', False),
                                          (0, 'Process crashed.', False),
                                          (1, 'COMMUNITY_LOCALE_PASSED verify', False),
                                          (0, 'COMMUNITY_LOCALE_PASSED verify\nCOMMUNITY_LOCALE_FAILED', False)):
                self.assertEqual(instrumentation_passed(subprocess.CompletedProcess([], code, output, ''), 'verify'), expected)

        def test_trace_detection_needs_frames(self):
            self.assertFalse(has_main_trace('ANR CPU 100%'))
            self.assertFalse(has_main_trace('"main" prio=5 tid=1 Native'))
            self.assertTrue(has_main_trace('"main" prio=5 tid=1 Native\n  at android.os.BinderProxy.transact(Native Method)'))
            self.assertFalse(has_main_trace('"main" prio=5 tid=1 Native\n  native: #00 poll\n"other" prio=5 tid=2\n  at unrelated.method(File.java:1)'))
            stack = '"main" prio=5 tid=1 Native\n  at android.os.BinderProxy.transact(Native Method)'
            self.assertTrue(has_target_trace('Cmd line: ' + shared.PACKAGE + '\n' + stack))
            self.assertFalse(has_target_trace('Cmd line: system_server\n' + stack))
            self.assertFalse(has_target_trace('Cmd line: ' + shared.PACKAGE + '\n----- end 1 -----\n----- pid 2 -----\nCmd line: system_server\n' + stack))

        def test_order_and_atomic_manifest(self):
            self.assertEqual(STARTUP_PHASES, (('verify', 'system'), ('select', 'system'), ('verify', 'system')))
            with tempfile.TemporaryDirectory() as directory:
                file = Path(directory) / 'manifest.json'
                write_json(file, {'passed': False}); write_json(file, {'passed': True})
                self.assertEqual(json.loads(file.read_text()), {'passed': True})
                self.assertFalse(file.with_name('manifest.json.tmp').exists())

        def exercise_run(self, fail_at=None, ui=False, pull_timeout=False):
            with tempfile.TemporaryDirectory() as directory:
                output = Path(directory)
                (output / 'apks').mkdir()
                for key in ('debug', 'test'):
                    (output / 'apks' / (key + '.apk')).write_bytes(b'fixture')
                artifacts = self.fixture()
                for key in artifacts:
                    artifacts[key]['sha256'] = shared.digest(output / 'apks' / (key + '.apk'))
                manifest = {'runId': 'a' * 32, 'artifacts': artifacts, 'phases': [], 'timeline': [],
                            'state': 'prepared', 'startupPassed': False, 'diagnosticPassed': False,
                            'ui': {'requested': False, 'passed': None}, 'passed': False}
                args = argparse.Namespace(output=output, serial='emulator-5554', adb='fake-adb',
                                          ui_after_success=ui, bugreport_if_needed=False)
                calls = []
                current = ['verify', 'system']
                phase_calls = []

                def command(items, **kwargs):
                    parts = list(items[3:]); calls.append(parts)
                    text = ''
                    if parts[:2] == ['shell', 'getprop']:
                        text = {'ro.kernel.qemu': '1', 'ro.build.version.sdk': '35', 'sys.boot_completed': '1',
                                'ro.build.fingerprint': 'disposable-fixture'}[parts[2]]
                    elif parts[:4] == ['shell', 'cmd', 'package', 'list']:
                        text = 'package:android\n'
                    elif parts[0] == 'install':
                        text = 'Success'
                    elif parts[:3] == ['shell', 'pm', 'path']:
                        text = 'package:/data/app/base.apk'
                    elif parts[:2] == ['shell', 'sha256sum']:
                        text = artifacts['debug']['sha256'] + ' base.apk'
                    elif parts[:3] == ['shell', 'am', 'instrument']:
                        current[:] = [parts[parts.index('phase') + 1], parts[parts.index('language') + 1]]
                        phase_calls.append(tuple(current))
                        text = 'Process crashed.' if len(phase_calls) == fail_at else 'COMMUNITY_LOCALE_PASSED ' + current[0]
                    return subprocess.CompletedProcess(items, 0, text, '')

                def pull(*unused, **kwargs):
                    if pull_timeout:
                        raise subprocess.TimeoutExpired('evidence tar', 30, output=b'partial tar bytes')
                    data = io.BytesIO()
                    content = json.dumps({'phase': current[0], 'language': current[1], 'passed': True,
                                          'checks': [{'passed': True}], 'screenshots': []}).encode()
                    with tarfile.open(fileobj=data, mode='w') as archive:
                        entry = tarfile.TarInfo(f'{current[0]}-{current[1]}-report.json')
                        entry.size = len(content); archive.addfile(entry, io.BytesIO(content))
                    return subprocess.CompletedProcess([], 0, data.getvalue(), b'')

                class Logcat:
                    returncode = None
                    def poll(self): return self.returncode
                    def terminate(self): self.returncode = -15
                    def wait(self, **unused): return self.returncode
                    def kill(self): self.returncode = -9

                with patch.object(shared, 'command', side_effect=command), patch.object(subprocess, 'run', side_effect=pull), \
                     patch.object(subprocess, 'Popen', return_value=Logcat()), patch.object(time, 'sleep'):
                    if fail_at or pull_timeout:
                        with self.assertRaises(Exception): run(args, manifest)
                    else:
                        run(args, manifest)
                saved = json.loads((output / 'manifest.json').read_text())
                self.assertEqual(saved['passed'], not (fail_at or pull_timeout))
                if fail_at:
                    self.assertEqual(len(phase_calls), fail_at)
                    self.assertEqual(sum(parts[:4] == ['shell', 'dumpsys', 'dropbox', '--print'] for parts in calls), 1)
                if pull_timeout:
                    self.assertIn('COMMUNITY_LOCALE_PASSED verify', (output / '01-verify-system-instrumentation.txt').read_text())
                    self.assertNotIn('600 seconds', saved['failure'])
                if not fail_at and not pull_timeout:
                    self.assertEqual(phase_calls, list(STARTUP_PHASES))
                    self.assertTrue((output / '01-verify-system-evidence/verify-system-report.json').is_file())
                    self.assertTrue((output / '03-verify-system-evidence/verify-system-report.json').is_file())
                return saved

        def test_three_successes_preserve_both_verify_reports(self):
            result = self.exercise_run()
            self.assertTrue(result['diagnosticPassed'])
            self.assertIsNone(result['ui']['passed'])

        def test_first_failure_stops_without_ui_or_retry(self):
            result = self.exercise_run(fail_at=2, ui=True)
            self.assertFalse(result['diagnosticPassed'])

        def test_ui_failure_does_not_erase_startup_result(self):
            result = self.exercise_run(fail_at=4, ui=True)
            self.assertTrue(result['diagnosticPassed'])
            self.assertFalse(result['ui']['passed'])

        def test_pull_timeout_preserves_successful_instrumentation(self):
            self.exercise_run(pull_timeout=True)

    result = unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(Fixtures))
    return 0 if result.wasSuccessful() else 1


def main():
    if sys.argv[1:] == ['--self-test']:
        return self_test()
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--debug', type=Path, required=True)
    parser.add_argument('--test', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--source-commit', required=True)
    parser.add_argument('--identity', type=Path, default=shared.ROOT / 'signing/community/identity.json',
                        help='Public identity metadata only; no signing material is read or copied')
    parser.add_argument('--serial')
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--aapt', default='aapt')
    parser.add_argument('--apksigner', default='apksigner')
    parser.add_argument('--prepare-only', action='store_true')
    parser.add_argument('--ui-after-success', action='store_true')
    parser.add_argument('--bugreport-if-needed', action='store_true')
    args = parser.parse_args()
    manifest = prepare(args)
    print(json.dumps({'retainedFixtures': manifest['artifacts'], 'originalByteComparison': manifest['originalByteComparison']}, sort_keys=True), flush=True)
    if args.prepare_only:
        print('PREPARED: APKs and identity/hash manifest retained before emulator use.', flush=True)
    else:
        run(args, manifest)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
