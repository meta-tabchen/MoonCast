"""Run the audio adapter and PCM-only sink against cached, real Media3 1.5.1 classes.

Run a TV Gradle build first to resolve dependencies. Requires JDK 17+, Android SDK 35,
JAVA_HOME, ANDROID_HOME, and optionally GRADLE_USER_HOME. No network downloads, Android
AudioTrack, codec, display, or actual speaker output occur in this JVM-only test.
"""
from pathlib import Path
import argparse
import os
import shutil
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def java_tool(name):
    home = os.environ.get('JAVA_HOME')
    if home:
        candidate = Path(home) / 'bin' / (name + ('.exe' if os.name == 'nt' else ''))
        if candidate.is_file():
            return str(candidate)
    candidate = shutil.which(name)
    if not candidate:
        raise SystemExit(f'{name} missing; install JDK 17 or set JAVA_HOME')
    return candidate


def require_file(candidates, description):
    paths = sorted(candidates)
    if not paths:
        raise SystemExit(f'{description} missing; resolve the TV Gradle dependencies first')
    return paths[0]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--android-jar', type=Path)
    parser.add_argument('--gradle-user-home', type=Path)
    args = parser.parse_args()
    sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    android_jar = args.android_jar or (Path(sdk) / 'platforms/android-35/android.jar' if sdk else None)
    if android_jar is None or not android_jar.is_file():
        raise SystemExit('Android SDK 35 missing; set ANDROID_HOME or --android-jar')
    gradle_home = args.gradle_user_home or Path(os.environ.get('GRADLE_USER_HOME', Path.home() / '.gradle'))
    cache = gradle_home / 'caches/modules-2/files-2.1'
    artifacts = []
    for name in ('common', 'exoplayer', 'extractor', 'container', 'datasource', 'decoder', 'database'):
        artifact = 'media3-' + name
        artifacts.append(require_file((cache / 'androidx.media3' / artifact / '1.5.1').glob('*/*.aar'), artifact))
    artifacts.append(require_file((cache / 'androidx.annotation/annotation-experimental/1.3.1').glob('*/*.aar'),
                                  'annotation-experimental'))
    guava = require_file((cache / 'com.google.guava/guava/33.3.1-android').glob('*/*.jar'), 'Media3 Guava')
    with tempfile.TemporaryDirectory(prefix='mooncast-media3-audio-tests-') as directory:
        directory = Path(directory)
        jars = [android_jar.resolve(), guava.resolve()]
        for artifact in artifacts:
            target = directory / (artifact.stem + '.jar')
            with zipfile.ZipFile(artifact) as archive:
                target.write_bytes(archive.read('classes.jar'))
            jars.append(target)
        classpath = os.pathsep.join(map(str, jars))
        sources = sorted((ROOT / 'tv/src/main/java/com/mooncast/tv/media').glob('*.java'))
        sources.append(ROOT / 'tests/TvMedia3AudioProcessorTest.java')
        sources.append(ROOT / 'tests/MediaPlaybackBarrierTest.java')
        sources.append(ROOT / 'tests/MediaStopRegistryTest.java')
        subprocess.run([java_tool('javac'), '-source', '17', '-target', '17', '-encoding', 'UTF-8',
                        '-cp', classpath, '-d', str(directory), *map(str, sources)], check=True)
        subprocess.run([java_tool('java'), '-cp', os.pathsep.join([str(directory), classpath]),
                        'com.mooncast.tv.media.TvMedia3AudioProcessorTest'], check=True, timeout=90)
        subprocess.run([java_tool('java'), '-cp', os.pathsep.join([str(directory), classpath]),
                        'com.mooncast.tv.media.MediaPlaybackBarrierTest'], check=True, timeout=90)
        subprocess.run([java_tool('java'), '-cp', os.pathsep.join([str(directory), classpath]),
                        'com.mooncast.tv.media.MediaStopRegistryTest'], check=True, timeout=90)


if __name__ == '__main__':
    main()
