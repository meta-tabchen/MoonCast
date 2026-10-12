#!/usr/bin/env python3
"""Exercise the actual AirPlay MirrorDecoder with synthetic Android lifecycle fakes.

Requires JDK 17 or newer, not Android SDK/JNI. These are host failure/ownership
regressions, not evidence of Android MediaCodec or AirPlay device interoperability.
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
    if candidate is None:
        raise SystemExit(f'{name} not found; install JDK 17 or set JAVA_HOME')
    return candidate


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        '--source-target', action='store_true',
        help='Use -source/-target 17 for a stripped local JDK without ct.sym; '
             'the default is --release 17. Also enabled by MOONCAST_TEST_JAVAC_SOURCE_ONLY=1.')
    args = parser.parse_args()
    source_only = args.source_target or os.environ.get('MOONCAST_TEST_JAVAC_SOURCE_ONLY') == '1'
    flags = ['-source', '17', '-target', '17'] if source_only else ['--release', '17']
    source_root = ROOT / 'tv/src/main/java/com/mooncast/tv/airplay'
    sources = sorted((ROOT / 'tests/airplay_decoder_stubs').rglob('*.java'))
    sources += [source_root / 'MirrorDecoder.java', source_root / 'VideoTiming.java',
                ROOT / 'tests/AirPlayDecoderReleaseTest.java']
    with tempfile.TemporaryDirectory(prefix='mooncast-airplay-decoder-test-') as temporary:
        subprocess.run([
            java_tool('javac'), *flags, '-Xlint:all', '-encoding', 'UTF-8',
            '-d', temporary, *map(str, sources),
        ], check=True, timeout=90)
        subprocess.run([
            java_tool('java'), '-ea', '-cp', temporary,
            'com.mooncast.tv.airplay.AirPlayDecoderReleaseTest',
        ], check=True, timeout=90)


if __name__ == '__main__':
    main()
