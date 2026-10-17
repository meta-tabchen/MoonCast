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
sources = [java_root / (name + '.java') for name in ('BrowserQuality', 'CropGeometry', 'AutoCropTracker', 'CinemaCropTracker', 'MediaVolumeSession', 'FrameStatistics', 'TrafficRate', 'OriginalMediaServer', 'BrowserSignalingServer', 'DisplayPowerLease', 'AirPlayH264', 'AirPlayDiagnosticSnapshot', 'SenderOwnership', 'CastSessionCoordinator', 'AirPlayEndpoint', 'SenderLiveness', 'OutputSizePolicy')]
sources += [root / 'tests/BrowserQualityTest.java', root / 'tests/CropGeometryTest.java', root / 'tests/MediaVolumeSessionTest.java', root / 'tests/StatisticsTest.java', root / 'tests/OriginalMediaServerTest.java', root / 'tests/BrowserSignalingServerTest.java', root / 'tests/DisplayPowerLeaseTest.java', root / 'tests/AirPlayH264Test.java', root / 'tests/AirPlayDiagnosticSnapshotTest.java', root / 'tests/CastSessionCoordinatorTest.java', root / 'tests/AirPlayEndpointTest.java', root / 'tests/SenderLivenessTest.java', root / 'tests/OutputSizePolicyTest.java']
sources += [root / 'tv/src/main/java/com/mooncast/tv/airplay/ReceiverTimeline.java', root / 'tests/ReceiverTimelineTest.java']
flags = ['-source', '17', '-target', '17'] if os.environ.get('MOONCAST_TEST_JAVAC_SOURCE_ONLY') == '1' else ['--release', '17']
with tempfile.TemporaryDirectory(prefix='mooncast-tests-') as directory:
    subprocess.run([javac, *flags, '-encoding', 'UTF-8', '-d', directory, *map(str, sources)], check=True)
    for test in ('BrowserQualityTest', 'CropGeometryTest', 'MediaVolumeSessionTest', 'StatisticsTest', 'OriginalMediaServerTest', 'BrowserSignalingServerTest', 'DisplayPowerLeaseTest', 'AirPlayH264Test', 'AirPlayDiagnosticSnapshotTest', 'CastSessionCoordinatorTest', 'AirPlayEndpointTest', 'SenderLivenessTest', 'OutputSizePolicyTest'):
        subprocess.run([java, '-cp', directory, 'com.mooncast.host.' + test], check=True)

    subprocess.run([java, '-cp', directory, 'com.mooncast.tv.airplay.ReceiverTimelineTest'], check=True)
