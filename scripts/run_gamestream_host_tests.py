"""Pure GameStream address, pin-store codec, and discovery lifecycle fixtures.

Uses only checked-in public CA certificates. No private-key access/generation,
AndroidKeyStore operation, sockets, real discovery, or device pairing is performed.
Requires a complete JDK 17+. --source-target-only supports stripped compiler
installations but is a weaker language/bytecode check than --release 17.
"""
from pathlib import Path
import argparse
import os
import subprocess
import tempfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--source-target-only', action='store_true')
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
source = root / 'tv/src/main/java/com/mooncast/tv'
java_home = os.environ.get('JAVA_HOME')
bin_path = Path(java_home) / 'bin' if java_home else None
suffix = '.exe' if os.name == 'nt' else ''
javac = str(bin_path / ('javac' + suffix)) if bin_path else 'javac'
java = str(bin_path / ('java' + suffix)) if bin_path else 'java'
hosts = source / 'gamestream/hosts'
sources = [source / 'net/LanAccess.java'] + [hosts / (name + '.java') for name in
    ('HostEndpoint', 'HostRecord', 'DiscoveredHost', 'HostStoreCodec', 'DiscoveryLedger')]
sources += [root / 'tests' / (name + '.java') for name in
    ('GameStreamHostStoreTest', 'GameStreamDiscoveryLedgerTest')]
flags = ['-source', '17', '-target', '17'] if args.source_target_only else ['--release', '17']
with tempfile.TemporaryDirectory(prefix='mooncast-gamestream-host-tests-') as directory:
    subprocess.run([javac, *flags, '-encoding', 'UTF-8', '-d', directory, *map(str, sources)], check=True)
    for test in ('GameStreamHostStoreTest', 'GameStreamDiscoveryLedgerTest'):
        subprocess.run([java, '-cp', directory, 'com.mooncast.tv.gamestream.hosts.' + test], check=True, timeout=20)
