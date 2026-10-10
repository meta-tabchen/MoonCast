#!/usr/bin/env python3
"""Run Annex-B regressions against the actual Android receiver parser, on the host JVM.

Requires JDK 17 and Android SDK platform 35. Set JAVA_HOME and ANDROID_HOME (or
ANDROID_SDK_ROOT), or pass --android-jar. Does not exercise MediaCodec/JNI/network
interoperability and must not be reported as a device casting test.
"""
from pathlib import Path
import argparse
import os
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def java_tool(name):
    home = os.environ.get('JAVA_HOME')
    if home:
        candidate = Path(home) / 'bin' / (name + ('.exe' if os.name == 'nt' else ''))
        if candidate.is_file():
            return str(candidate)
    candidate = shutil.which(name)
    if not candidate:
        raise SystemExit(f'{name} not found; install JDK 17 or set JAVA_HOME')
    return candidate


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--android-jar', type=Path)
    args = parser.parse_args()
    android_jar = args.android_jar
    if android_jar is None:
        sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
        if sdk:
            android_jar = Path(sdk) / 'platforms/android-35/android.jar'
    if android_jar is None or not android_jar.is_file():
        raise SystemExit('Android SDK platform 35 missing; set ANDROID_HOME or pass --android-jar')
    android_jar = android_jar.resolve()
    source = ROOT / 'tv/src/main/java/com/mooncast/tv/airplay/MirrorDecoder.java'
    test = ROOT / 'native/airplay/tests/NalUnitTest.java'
    with tempfile.TemporaryDirectory(prefix='mooncast-airplay-test-') as temporary:
        subprocess.run([
            java_tool('javac'), '-Xlint:all', '-source', '17', '-target', '17',
            '-cp', str(android_jar), '-d', temporary, str(source), str(test),
        ], check=True)
        subprocess.run([
            java_tool('java'), '-cp', os.pathsep.join([temporary, str(android_jar)]),
            'com.mooncast.tv.airplay.NalUnitTest',
        ], check=True)


if __name__ == '__main__':
    main()
