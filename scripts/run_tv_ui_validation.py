#!/usr/bin/env python3
"""Install the real debug APK and collect framework-instrumentation screenshots.

This intentionally runs only on a disposable Android emulator. It clears this test
app's data between languages, never installs on a phone/TV, and never changes the
release APK. Supply a generated MP4 fixture to cover decoded-video Back/crop flows.
"""
import argparse
import functools
import hashlib
import http.server
import json
import pathlib
import subprocess
import threading

ROOT = pathlib.Path(__file__).resolve().parents[1]
PARSER = argparse.ArgumentParser(description=__doc__)
PARSER.add_argument('--adb', default='adb')
PARSER.add_argument('--serial', required=True)
PARSER.add_argument('--apk', type=pathlib.Path, default=ROOT/'tv/build/outputs/apk/debug/tv-debug.apk')
PARSER.add_argument('--test-apk', type=pathlib.Path, default=ROOT/'tv/build/outputs/apk/androidTest/debug/tv-debug-androidTest.apk')
PARSER.add_argument('--output', type=pathlib.Path, required=True)
PARSER.add_argument('--offline', action='store_true', help='Skip LAN/decode requirements; label session navigation as test-only simulated state')
PARSER.add_argument('--skip-install', action='store_true', help='Use the already installed exact APKs after independently checking their hashes')
PARSER.add_argument('--locales', nargs='+', default=['en-US', 'zh-CN'])
PARSER.add_argument('--fixture', type=pathlib.Path, help='Generated, non-personal H.264 MP4 to serve to the emulator')
PARSER.add_argument('--fixture-port', type=int, default=8866)
PARSER.add_argument('--allow-clear-emulator-app-data', action='store_true', required=True)

def main():
    args = PARSER.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    command = [args.adb, '-s', args.serial]
    def adb(*items, timeout=180):
        result = subprocess.run([*command, *items], capture_output=True, text=True, timeout=timeout)
        if result.returncode:
            raise RuntimeError(f'ADB failed ({result.returncode}): {items}\n{result.stdout}\n{result.stderr}')
        return result.stdout.strip()
    if adb('shell', 'getprop', 'ro.kernel.qemu') != '1':
        raise SystemExit('Refusing to clear data or install tests on a non-emulator device')
    if adb('shell', 'getprop', 'sys.boot_completed') != '1':
        raise SystemExit('Android has not finished booting')
    for apk in (args.apk, args.test_apk):
        if not apk.is_file():
            raise SystemExit(f'Missing {apk}; build :tv:assembleDebug :tv:assembleDebugAndroidTest first')
        if not args.skip_install: print(adb('install', '-r', str(apk)), flush=True)
        package = 'com.mooncast.tv' if apk == args.apk else 'com.mooncast.tv.test'
        installed_path = adb('shell', 'pm', 'path', package).splitlines()[0].removeprefix('package:')
        installed_hash = adb('shell', 'sha256sum', installed_path).split()[0]
        if installed_hash != hashlib.sha256(apk.read_bytes()).hexdigest():
            raise RuntimeError(f'Installed APK hash differs for {package}')
    manifest = {'scope': 'Actual APK on disposable Android emulator; not physical-TV or remote-sender interoperability evidence',
                'mode': 'offline UI and simulated session state; LAN/decode NOT tested' if args.offline else 'LAN listener and optional decoded fixture',
                'apk_sha256': hashlib.sha256(args.apk.read_bytes()).hexdigest(),
                'test_apk_sha256': hashlib.sha256(args.test_apk.read_bytes()).hexdigest(),
                'fingerprint': adb('shell', 'getprop', 'ro.build.fingerprint'),
                'display_size': adb('shell', 'wm', 'size'), 'display_density': adb('shell', 'wm', 'density'),
                'locales': args.locales, 'results': []}
    server = None
    fixture_url = None
    if args.fixture and not args.offline:
        fixture = args.fixture.resolve()
        if not fixture.is_file(): raise SystemExit(f'Missing fixture: {fixture}')
        handler = functools.partial(http.server.SimpleHTTPRequestHandler, directory=str(fixture.parent))
        server = http.server.ThreadingHTTPServer(('127.0.0.1', args.fixture_port), handler)
        threading.Thread(target=server.serve_forever, daemon=True).start()
        fixture_url = f'http://10.0.2.2:{args.fixture_port}/{fixture.name}'
        manifest['fixture_sha256'] = hashlib.sha256(fixture.read_bytes()).hexdigest()
        manifest['fixture'] = 'Locally generated letterbox test pattern, served only to the emulator'
    try:
        for locale in args.locales:
            print(f'Validating {locale}', flush=True)
            adb('shell', 'pm', 'clear', 'com.mooncast.tv')
            adb('shell', 'cmd', 'locale', 'set-app-locales', 'com.mooncast.tv', '--user', '0', '--locales', locale)
            options = ['shell', 'am', 'instrument', '-w', '-r', '-e', 'label', locale]
            if args.offline: options += ['-e', 'offline', 'true']
            if fixture_url: options += ['-e', 'fixtureUrl', fixture_url]
            options += ['com.mooncast.tv.test/com.mooncast.tv.TvUiInstrumentation']
            output = adb(*options, timeout=900)
            (args.output/f'{locale}-instrumentation.txt').write_text(output+'\n')
            pull = subprocess.run([*command, 'pull', '/sdcard/Android/data/com.mooncast.tv/files/visual-validation/.', str(args.output)], capture_output=True, text=True)
            (args.output/f'{locale}-pull.txt').write_text(pull.stdout+'\n'+pull.stderr)
            if pull.returncode: print(pull.stderr, flush=True)
            passed = ('TV_UI_OFFLINE_VALIDATION_PASSED' if args.offline else 'TV_UI_VALIDATION_PASSED') in output
            manifest['results'].append({'locale':locale, 'passed':passed})
            print(output, flush=True)
            if not passed:
                (args.output/f'{locale}-logcat.txt').write_text(adb('shell','logcat','-d','-t','1000'))
                raise RuntimeError(f'{locale} UI validation failed; inspect screenshot/report')
    finally:
        if server: server.shutdown()
        manifest['files'] = {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(args.output.iterdir()) if p.is_file() and p.name != 'manifest.json'}
        (args.output/'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2)+'\n')
    print('Actual APK screenshot and UI validation finished', flush=True)

if __name__ == '__main__': main()
