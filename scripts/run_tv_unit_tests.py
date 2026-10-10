"""Portable generated-input TV protocol/framing tests; requires JDK 17, no Android SDK.

Runs actual loopback TCP/UDP services, including SSDP port 1900. These are protocol
fixtures, not physical Android TV, AirPlay sender, decoder or Wi-Fi interoperability.
"""
from pathlib import Path
import os
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
source = root / 'tv/src/main/java/com/mooncast/tv'
java_home = os.environ.get('JAVA_HOME')
bin_path = Path(java_home) / 'bin' if java_home else None
suffix = '.exe' if os.name == 'nt' else ''
javac = str(bin_path / ('javac' + suffix)) if bin_path else 'javac'
java = str(bin_path / ('java' + suffix)) if bin_path else 'java'
sources = sorted(p for p in (source / 'net').glob('*.java') if p.name != 'ReceiverMediaSource.java')
sources += [source / 'CropGeometry.java', source / 'CinemaCropTracker.java',
            root / 'tests/TvReceiverProtocolTest.java', root / 'tests/TvGeometryTest.java', root / 'tests/TvMediaHttpTest.java']
with tempfile.TemporaryDirectory(prefix='mooncast-tv-tests-') as directory:
    subprocess.run([javac, '--release', '17', '-encoding', 'UTF-8', '-d', directory,
                    *map(str, sources)], check=True)
    for test in ('com.mooncast.tv.net.TvReceiverProtocolTest', 'com.mooncast.tv.TvGeometryTest', 'com.mooncast.tv.net.TvMediaHttpTest'):
        subprocess.run([java, '-cp', directory, test], check=True, timeout=90)
