"""Run portable behavior tests with JDK 17; no device or Android SDK needed."""
from pathlib import Path
import os
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
java_root = root / 'app/src/main/java/com/mooncast/host'
java_home = os.environ.get('JAVA_HOME')
bin_path = Path(java_home) / 'bin' if java_home else None
suffix = '.exe' if os.name == 'nt' else ''
javac = str(bin_path / ('javac' + suffix)) if bin_path else 'javac'
java = str(bin_path / ('java' + suffix)) if bin_path else 'java'
sources = [java_root / (name + '.java') for name in ('CropGeometry', 'AutoCropTracker', 'CinemaCropTracker', 'MediaVolumeSession', 'FrameStatistics', 'TrafficRate')]
sources += [root / 'tests/CropGeometryTest.java', root / 'tests/MediaVolumeSessionTest.java', root / 'tests/StatisticsTest.java']
with tempfile.TemporaryDirectory(prefix='mooncast-tests-') as directory:
    subprocess.run([javac, '--release', '17', '-encoding', 'UTF-8', '-d', directory, *map(str, sources)], check=True)
    for test in ('CropGeometryTest', 'MediaVolumeSessionTest', 'StatisticsTest'):
        subprocess.run([java, '-cp', directory, 'com.mooncast.host.' + test], check=True)
