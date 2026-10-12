#!/usr/bin/env python3
"""Real controller/session coordinators with deterministic fakes; not Android/network evidence."""
from pathlib import Path
import argparse
import os
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
scenarios = (
    'double-connect-identity', 'focus-audio-release', 'stop-prepare', 'stop-start',
    'late-callbacks', 'recreation-barrier', 'failed-release-barrier', 'focus-denied',
    'focus-loss-release', 'error-failed-release', 'main-thread-contract', 'hosts-cancel-pair', 'hosts-cancel-save', 'hosts-corrupt-store',
)
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('scenarios', nargs='*', metavar='SCENARIO', help='Run selected isolated scenarios (default: all)')
args = parser.parse_args()
unknown = sorted(set(args.scenarios) - set(scenarios))
if unknown:
    parser.error('Unknown scenario(s): ' + ', '.join(unknown) + '. Choose from: ' + ', '.join(scenarios))
java_home = os.environ.get('JAVA_HOME')
bin_path = Path(java_home) / 'bin' if java_home else None
suffix = '.exe' if os.name == 'nt' else ''
javac = str(bin_path / ('javac' + suffix)) if bin_path else 'javac'
java = str(bin_path / ('java' + suffix)) if bin_path else 'java'
# Stripped local JDKs can opt in; normal JDK/CI retains the Java 17 API compatibility gate.
flags = ['-source', '17', '-target', '17'] if os.environ.get('MOONCAST_TEST_JAVAC_SOURCE_ONLY') == '1' else ['--release', '17']
core = root / 'moonlight-core/src/main/java'
tv = root / 'tv/src/main/java/com/mooncast/tv/gamestream'
sources = sorted((root / 'tests/gamestream_controller_stubs').rglob('*.java'))
sources += [tv / f'{name}.java' for name in ('GameStreamController', 'GameStreamHosts')]
sources += [core / 'com/mooncast/receiver' / f'{name}.java'
            for name in ('PreparedDesktop', 'ReceiverSession', 'RendererRelease', 'StreamSpec')]
sources += [core / 'com/limelight/nvstream/NvConnectionListener.java', root / 'tests/GameStreamControllerContractTest.java']
with tempfile.TemporaryDirectory(prefix='mooncast-gamestream-controller-tests-') as directory:
    subprocess.run([javac, *flags, '-encoding', 'UTF-8', '-d', directory, *map(str, sources)], check=True, timeout=90)
    # Fresh processes isolate intentionally poisoned process-wide release barriers without
    # reflection, production reset hooks, or resetting any ownership algorithm in the harness.
    for scenario in args.scenarios or scenarios:
        subprocess.run([java, '-ea', '-cp', directory,
                        'com.mooncast.tv.gamestream.GameStreamControllerContractTest', scenario],
                       check=True, timeout=25)
print('All selected production GameStream controller/host lifecycle scenarios passed.')
