#!/usr/bin/env python3
"""Pure receiver queue/Annex-B contract tests; not Android/device interoperability evidence."""
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
base = root / 'tv/src/main/java/com/mooncast/tv/gamestream/render'
sources = [base / f'{name}.java' for name in ('DecodeUnitAssembler', 'VideoIngress', 'PcmQueue', 'GameStreamTiming')]
sources.append(root / 'tests/GameStreamRenderContractTest.java')
# Explicit opt-in for stripped local JDK facades without ct.sym; CI keeps --release 17.
flags = ['-source', '17', '-target', '17'] if os.environ.get('MOONCAST_TEST_JAVAC_SOURCE_ONLY') == '1' else ['--release', '17']
with tempfile.TemporaryDirectory(prefix='mooncast-gamestream-render-tests-') as directory:
    subprocess.run([javac, *flags, '-encoding', 'UTF-8', '-d', directory, *map(str, sources)], check=True, timeout=90)
    subprocess.run([java, '-ea', '-cp', directory, 'com.mooncast.tv.gamestream.render.GameStreamRenderContractTest'], check=True, timeout=90)

# Explicit synthetic Android fakes exercise adapter lifecycle, not actual playback.
with tempfile.TemporaryDirectory(prefix='mooncast-gamestream-lifecycle-tests-') as directory:
    lifecycle = sorted((root / 'tests/gamestream_render_stubs').rglob('*.java'))
    lifecycle += sorted(base.glob('*.java'))
    lifecycle.append(root / 'tests/GameStreamRenderLifecycleTest.java')
    subprocess.run([javac, *flags, '-encoding', 'UTF-8', '-d', directory, *map(str, lifecycle)], check=True, timeout=90)
    subprocess.run([java, '-ea', '-cp', directory, 'com.mooncast.tv.gamestream.render.GameStreamRenderLifecycleTest'], check=True, timeout=90)
