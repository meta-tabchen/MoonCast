#!/usr/bin/env python3
"""Offline QR roundtrip tests against both the pinned JAR and rebuilt bundled Java sources."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
LOCK = json.loads((ROOT / 'native/browser-qr/SOURCE_LOCK.json').read_text())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, help='Exact already-downloaded core-3.5.3.jar; no network fetch')
    args = parser.parse_args()
    jar = args.jar
    if jar is None:
        cache = Path(os.environ.get('GRADLE_USER_HOME', str(Path.home() / '.gradle')))
        jar = next((cache / 'caches/modules-2/files-2.1/com.google.zxing/core/3.5.3').glob('*/*.jar'), None)
    if jar is None or not jar.is_file():
        parser.error('Pass --jar with the pinned downloaded core JAR, or resolve the Gradle dependency first')
    if hashlib.sha256(jar.read_bytes()).hexdigest() != LOCK['sha256']:
        raise ValueError('QR binary does not match source lock')
    for row in LOCK['bundled']:
        if hashlib.sha256((ROOT / row['path']).read_bytes()).hexdigest() != row['sha256']:
            raise ValueError('QR bundled input differs: ' + row['path'])
    java_home = os.environ.get('JAVA_HOME')
    javac = str(Path(java_home) / 'bin/javac') if java_home else shutil.which('javac')
    java = str(Path(java_home) / 'bin/java') if java_home else shutil.which('java')
    if not javac or not java:
        raise RuntimeError('JDK 17 is required')
    with tempfile.TemporaryDirectory(prefix='mooncast-qr-') as temporary:
        work = Path(temporary)
        classes = work / 'test-classes'
        classes.mkdir()
        test_inputs = [ROOT / 'tests/browser_qr_stubs/android/graphics/Bitmap.java',
                       ROOT / 'app/src/main/java/com/mooncast/host/BrowserQrCode.java',
                       ROOT / 'tests/BrowserQrCodeTest.java']
        subprocess.run([javac, '-d', str(classes), '-cp', str(jar), *map(str, test_inputs)], check=True)
        subprocess.run([java, '-cp', os.pathsep.join([str(classes), str(jar)]),
                        'com.mooncast.host.BrowserQrCodeTest'], check=True)
        sources = work / 'source'
        with zipfile.ZipFile(ROOT / LOCK['source']['archive_path']) as archive:
            for name in archive.namelist():
                if not name.endswith('.java'):
                    continue
                target = (sources / name).resolve()
                if sources.resolve() not in target.parents:
                    raise ValueError('Unexpected source archive path')
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(archive.read(name))
        rebuilt = work / 'rebuilt-core'
        rebuilt.mkdir()
        production_sources = sorted(sources.rglob('*.java'))
        subprocess.run([javac, '-source', '8', '-target', '8', '-Xlint:-options', '-d', str(rebuilt),
                        *map(str, production_sources)], check=True)
        subprocess.run([java, '-cp', os.pathsep.join([str(classes), str(rebuilt)]),
                        'com.mooncast.host.BrowserQrCodeTest'], check=True)
        print(f'Bundled ZXing sources compiled offline: {len(production_sources)} Java files; no runtime dependencies')


if __name__ == '__main__':
    main()
