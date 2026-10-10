"""Generated PCM offset tests using JDK 17; no Android/device or A/V latency claim."""
from pathlib import Path
import os
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
java_home = os.environ.get('JAVA_HOME')
bin_path = Path(java_home) / 'bin' if java_home else None
suffix = '.exe' if os.name == 'nt' else ''
javac = str(bin_path / ('javac' + suffix)) if bin_path else 'javac'
java = str(bin_path / ('java' + suffix)) if bin_path else 'java'
sources = [root / 'tv/src/main/java/com/mooncast/tv/media/PcmOffsetBuffer.java',
           root / 'tests/TvAudioOffsetTest.java']
with tempfile.TemporaryDirectory(prefix='mooncast-tv-audio-tests-') as directory:
    subprocess.run([javac, '--release', '17', '-encoding', 'UTF-8', '-d', directory,
                    *map(str, sources)], check=True)
    subprocess.run([java, '-cp', directory, 'com.mooncast.tv.media.TvAudioOffsetTest'],
                   check=True, timeout=90)
