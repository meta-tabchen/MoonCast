"""Run production NSD discovery against deterministic callback adapters, no Android device.
Optional --baseline-ref REV substitutes production files from that revision for red/green proof.
Adapters simulate Android APIs only; they do not duplicate any discovery application logic.
"""
import argparse
import os
from pathlib import Path
import subprocess
import tempfile
root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--baseline-ref')
args = parser.parse_args()
java_home = os.environ.get('JAVA_HOME')
javac = str(Path(java_home) / 'bin/javac') if java_home else 'javac'
java = str(Path(java_home) / 'bin/java') if java_home else 'java'
flags = ['-source', '17', '-target', '17'] if os.environ.get('MOONCAST_TEST_JAVAC_SOURCE_ONLY') == '1' else ['--release', '17']
with tempfile.TemporaryDirectory(prefix='my-tv-nsd-tests-') as directory:
    temp = Path(directory)
    sources = list((root / 'tests/nsd_stubs').rglob('*.java'))
    for name in ('AirPlayEndpoint', 'ApkEndpoint', 'MyTvDevice', 'MyTvRegistry', 'MyTvDiscovery'):
        relative = f'app/src/main/java/com/mooncast/host/{name}.java'
        if args.baseline_ref:
            source = temp / (name + '.java')
            source.write_bytes(subprocess.check_output(['git', 'show', f'{args.baseline_ref}:{relative}'], cwd=root))
        else:
            source = root / relative
        sources.append(source)
    sources.append(root / 'tests/MyTvDiscoveryCallbacksTest.java')
    subprocess.run([javac, *flags, '-encoding', 'UTF-8', '-d', directory, *map(str, sources)], check=True)
    subprocess.run([java, '-cp', directory, 'com.mooncast.host.MyTvDiscoveryCallbacksTest'], check=True)
