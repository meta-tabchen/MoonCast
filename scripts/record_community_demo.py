#!/usr/bin/env python3
"""Optional authentic UI video after successful disposable-emulator verification.

Never installs, clears data, grants permissions, changes system settings or touches
physical devices. Replays the existing phone locale instrumentation only on the
verified debug APK. Recording failures write status='not recorded' and exit zero;
this helper must not replace or mask the main validation job's result.
"""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import time
import uuid

PACKAGE = 'com.mooncast.community'
RUNNER = PACKAGE + '.test/com.mooncast.unified.CommunityLocaleInstrumentation'


def command(items, timeout=30):
    result = subprocess.run([str(x) for x in items], capture_output=True, text=True, timeout=timeout)
    if result.returncode:
        raise RuntimeError(f'Command failed ({result.returncode}): {result.stderr.strip() or result.stdout.strip()}')
    return result.stdout


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True, help='Existing disposable emulator-NNNN only')
    parser.add_argument('--verification-manifest', type=Path, required=True,
                        help='Passed manifest from run_community_device_checks.py in this job')
    parser.add_argument('--output', type=Path, required=True, help='New or empty local directory')
    parser.add_argument('--language', choices=('en', 'zh'), default='en')
    parser.add_argument('--seconds', type=int, choices=range(8, 16), default=15)
    parser.add_argument('--adb', default='adb')
    args = parser.parse_args()
    # Refuse to overwrite previous evidence. This is a configuration error rather
    # than a recording failure; the publisher should run the optional step safely.
    if args.output.exists() and any(args.output.iterdir()):
        parser.error('Use a new or empty demo output directory')
    args.output.mkdir(parents=True, exist_ok=True)
    status = {'status': 'not recorded', 'language': args.language, 'phase': 'phone',
              'scope': 'Continuous debug-APK UI recording on a disposable Android emulator. '
                       'Instrumentation-driven settings navigation, not manual interaction, '
                       'screen-share permission, media streaming, crop accuracy or physical-device evidence.',
              'review': 'Do not publish before visual and privacy review.'}
    recorder = None
    log_stream = None
    started = None
    try:
        if not re.fullmatch(r'emulator-\d+', args.serial):
            raise RuntimeError('Only a disposable emulator-NNNN target is allowed')
        manifest = json.loads(args.verification_manifest.read_text())
        if manifest.get('passed') is not True:
            raise RuntimeError('Main community verification has not passed')
        if not any(p.get('phase') == 'phone' and p.get('language') == args.language and p.get('passed') is True
                   for p in manifest.get('phases', [])):
            raise RuntimeError('Requested phone/language phase has not already passed')
        fixture = manifest.get('artifacts', {}).get('debug', {})
        expected_hash = fixture.get('sha256', '')
        if (fixture.get('applicationId') != PACKAGE or fixture.get('debuggable') is not True
                or not re.fullmatch('[a-f0-9]{64}', expected_hash)):
            raise RuntimeError('Passed manifest has no valid community debug fixture')
        adb = [args.adb, '-s', args.serial]
        def shell(*parts, timeout=30):
            return command([*adb, 'shell', *parts], timeout=timeout)
        if shell('getprop', 'ro.kernel.qemu').strip() != '1':
            raise RuntimeError('Non-emulator target rejected')
        if shell('getprop', 'sys.boot_completed').strip() != '1':
            raise RuntimeError('Emulator is not ready')
        sdk = shell('getprop', 'ro.build.version.sdk').strip()
        if sdk != str(manifest.get('sdk', '')):
            raise RuntimeError('Emulator SDK differs from the verified job')
        paths = shell('pm', 'path', PACKAGE).strip().splitlines()
        if len(paths) != 1 or not paths[0].startswith('package:'):
            raise RuntimeError('Expected the already-installed standalone debug APK')
        installed = shell('sha256sum', paths[0][len('package:'):]).split()[0]
        if installed != expected_hash:
            raise RuntimeError('Installed APK does not match the successfully verified debug APK')
        # run-as itself enforces the installed app's debuggable status; no permission grant.
        if not re.search(r'\buid=\d+', shell('run-as', PACKAGE, 'id')):
            raise RuntimeError('Installed APK is not accessible as a debug target')
        instrumentation = shell('pm', 'list', 'instrumentation')
        if 'instrumentation:' + RUNNER not in instrumentation:
            raise RuntimeError('Existing locale test APK/runner is missing')
        marker = uuid.uuid4().hex
        remote_dir = '/data/local/tmp/mooncast-demo-' + marker
        remote_video = remote_dir + '/phone-' + args.language + '.mp4'
        shell('mkdir', remote_dir)
        video = args.output / ('mooncast-phone-' + args.language + '-debug-emulator.mp4')
        log_stream = (args.output / 'screenrecord.txt').open('w')
        recorder = subprocess.Popen([*adb, 'shell', 'screenrecord', '--time-limit', str(args.seconds),
                                     '--size', '720x1280', '--bit-rate', '1000000', remote_video],
                                    stdout=log_stream, stderr=subprocess.STDOUT, text=True)
        started = time.monotonic()
        time.sleep(.8)
        if recorder.poll() is not None:
            raise RuntimeError('screenrecord exited before UI replay; see screenrecord.txt')
        # Uses only the existing, previously successful phone UI steps. This runner
        # checks interactive/unlocked preconditions; it does not bypass a device lock.
        ui = command([*adb, 'shell', 'am', 'instrument', '-w', '-r',
                      '-e', 'suite', 'locale', '-e', 'phase', 'phone',
                      '-e', 'language', args.language, '-e', 'run_id', marker,
                      '-e', 'marker', marker, RUNNER], timeout=120)
        (args.output / 'instrumentation.txt').write_text(ui)
        if ('COMMUNITY_LOCALE_PASSED phone' not in ui or 'COMMUNITY_LOCALE_FAILED' in ui
                or 'INSTRUMENTATION_FAILED' in ui):
            raise RuntimeError('Optional UI replay failed; do not publish this video')
        recorder.wait(timeout=max(5, args.seconds + 10 - (time.monotonic() - started)))
        log_stream.close()
        log_stream = None
        if recorder.returncode != 0:
            raise RuntimeError('screenrecord failed; see screenrecord.txt')
        command([*adb, 'pull', remote_video, video], timeout=30)
        if video.stat().st_size < 4096:
            raise RuntimeError('Recording is empty or implausibly small')
        with video.open('rb') as stream:
            header = stream.read(64)
        if b'ftyp' not in header:
            raise RuntimeError('Recording is not an MP4 container')
        with video.open('rb') as stream:
            sha = hashlib.file_digest(stream, 'sha256').hexdigest()
        status.update(status='recorded; visual review pending', filename=video.name,
                      sha256=sha, bytes=video.stat().st_size, sdk=sdk,
                      apkVersionName=fixture.get('versionName'), apkVersionCode=fixture.get('versionCode'),
                      apkSha256=expected_hash, recordingTimeLimitSeconds=args.seconds,
                      source='adb screenrecord concurrent with CommunityLocaleInstrumentation phone phase')
    except Exception as error:
        status['reason'] = str(error)
    finally:
        if recorder is not None and recorder.poll() is None:
            # Let only our own bounded recorder finish; never use killall or stop
            # other device processes. Time-limited remote screenrecord self-stops.
            try:
                recorder.wait(timeout=max(5, args.seconds + 10 - (time.monotonic() - started)))
            except subprocess.TimeoutExpired:
                recorder.terminate()
                try:
                    recorder.wait(timeout=3)
                except subprocess.TimeoutExpired:
                    recorder.kill()
                    recorder.wait(timeout=3)
        if log_stream is not None:
            log_stream.close()
        (args.output / 'recording-status.json').write_text(json.dumps(status, indent=2) + '\n')
        print(json.dumps(status, indent=2), flush=True)
    # Optional recording must not make a successful main verification fail.
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
