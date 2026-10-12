"""Portable endpoint and split-manifest policy checks; optional real APK inputs."""
from pathlib import Path
import os
import subprocess
import sys
import tempfile

root = Path(__file__).resolve().parents[1]
java_home = os.environ.get('JAVA_HOME')
suffix = '.exe' if os.name == 'nt' else ''
def tool(name):
    return str(Path(java_home) / 'bin' / (name + suffix)) if java_home else name

flags = ['-source', '8', '-target', '8'] if os.environ.get('MOONCAST_TEST_JAVAC_SOURCE_ONLY') == '1' else ['--release', '8']
sources = [root / 'app/src/main/java/com/mooncast/host' / (name + '.java')
           for name in ('ApkEndpoint', 'ApkSingleManifest')]
sources.append(root / 'tests/ApkTransferPolicyTest.java')
sources += sorted((root / 'app/src/main/java/com/mooncast/transfer').glob('*.java'))
with tempfile.TemporaryDirectory(prefix='mooncast-apk-policy-') as directory:
    subprocess.run([tool('javac'), *flags, '-encoding', 'UTF-8', '-d', directory,
                    *map(str, sources)], check=True)
    subprocess.run([tool('java'), '-Xmx64m', '-cp', directory,
                    'com.mooncast.host.ApkTransferPolicyTest', *sys.argv[1:]], check=True, timeout=60)
