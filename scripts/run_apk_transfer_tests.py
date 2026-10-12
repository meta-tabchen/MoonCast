"""Verify the shared protocol snapshot and run generated loopback tests only."""
from pathlib import Path
import hashlib
import json
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SHARED = ROOT / 'shared/apk-transfer'
lock = json.loads((SHARED / 'SOURCE_LOCK.json').read_text())
for name, expected in lock['sha256'].items():
    content = (SHARED / name).read_bytes()
    assert hashlib.sha256(content).hexdigest() == expected, 'Shared core source mismatch: ' + name
    if name.startswith('src/'):
        packaged = ROOT / 'app/src/main/java' / name.removeprefix('src/')
        assert packaged.read_bytes() == content, 'Android/shared core divergence: ' + str(packaged)

java_home = os.environ.get('JAVA_HOME')
suffix = '.exe' if os.name == 'nt' else ''
java_bin = Path(java_home) / 'bin' if java_home else None
def tool(name):
    return str(java_bin / (name + suffix)) if java_bin else name

flags = ['-source', '8', '-target', '8'] if os.environ.get('MOONCAST_TEST_JAVAC_SOURCE_ONLY') == '1' else ['--release', '8']
sources = sorted((SHARED / 'src').rglob('*.java')) + sorted((SHARED / 'test').rglob('*.java'))
with tempfile.TemporaryDirectory(prefix='mooncast-apk-transfer-tests-') as directory:
    subprocess.run([tool('javac'), *flags, '-encoding', 'UTF-8', '-d', directory,
                    *map(str, sources)], check=True)
    subprocess.run([tool('java'), '-Xmx64m', '-cp', directory, 'com.mooncast.transfer.TransferTest'], check=True, timeout=180)
