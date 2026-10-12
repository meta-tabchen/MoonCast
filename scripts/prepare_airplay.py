#!/usr/bin/env python3
"""Verify the pinned AirPlay input set or prepare an isolated offline source build.

No downloads, APK extraction, credential access, or SDK license acceptance occur here.
"""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
NATIVE = ROOT / 'native/airplay-sender'


def digest(path):
    with path.open('rb') as f:
        return hashlib.file_digest(f, 'sha256').hexdigest()


def verify_inputs():
    lock = json.loads((NATIVE / 'SOURCE_LOCK.json').read_text())
    actual = {str(p.relative_to(ROOT)) for folder in ['source', 'module-proxy', 'licenses', 'patches', 'tests']
              for p in (NATIVE / folder).rglob('*') if p.is_file()}
    actual.update(str(p.relative_to(ROOT)) for p in [NATIVE / 'go.mod', NATIVE / 'go.sum',
                  NATIVE / 'modules-lock.json', ROOT / 'scripts/prepare_airplay.py', ROOT / 'scripts/build_airplay.sh'])
    if actual != set(lock['files']):
        raise ValueError('AirPlay input inventory changed: ' + str(sorted(actual.symmetric_difference(lock['files']))))
    for relative, expected in lock['files'].items():
        path = ROOT / relative
        if not path.is_file() or digest(path) != expected:
            raise ValueError(f'AirPlay pinned source mismatch: {relative}')
    return lock


def replace_once(path, old, new, count=1):
    text = path.read_text()
    actual = text.count(old)
    if actual != count:
        raise ValueError(f'{path.name}: expected {count} patch matches, got {actual}')
    path.write_text(text.replace(old, new))


def prepare(destination):
    """Apply the v0.0.33 build.sh transformations, with one privacy hardening change."""
    destination = destination.resolve()
    if destination.exists() and any(destination.iterdir()):
        raise ValueError(f'Build source destination must be empty: {destination}')
    source = NATIVE / 'source'
    shutil.copytree(source / 'doubletake', destination, dirs_exist_ok=True)
    wrapper = source / 'android-display-mirror'
    shutil.copytree(wrapper / 'airplaylib', destination / 'airplaylib')
    airplay = destination / 'internal/airplay'
    for name in ['patches.go', 'airplay1.go']:
        shutil.copy2(wrapper / name, airplay / name)
    replace_once(airplay / 'mirror.go', '*ScreenCapture', 'io.Reader')
    replace_once(airplay / 'client.go',
                 'respBody, respHeaders, err := c.readHTTPResponse()\n\tif err != nil {\n\t\treturn nil, nil, err',
                 'respBody, respHeaders, err := c.readHTTPResponse()\n\tif err != nil {\n\t\treturn nil, respHeaders, err')
    replace_once(airplay / 'fairplay.go',
                 'deriveStreamMasterKey(c.fpAesKey, sharedSecret(c.PairKeys), c.encrypted)',
                 'deriveStreamMasterKey(c.fpAesKey, sharedSecret(c.PairKeys), true)')
    replace_once(airplay / 'fairplay.go', 'if c.encrypted && len(sharedSecret(c.PairKeys)) > 0 {',
                 'if len(sharedSecret(c.PairKeys)) > 0 {')
    old = 'audioSetupBody, err2 := plist.Marshal(audioSetupPlist, plist.BinaryFormat)'
    new = ('if ep, err0 := c.SetupSession(audioURI, clientDeviceID, sessionUUID, timingPort); err0 != nil '
           '{audioCtrlConn.Close(); audioDataConn.Close(); return nil, fmt.Errorf("SETUP phase 0 (session): %w", err0)} '
           'else if ep > 0 {receiverEventPort = ep}\n' + old)
    replace_once(airplay / 'mirror.go', old, new)
    replace_once(airplay / 'client.go', '[]byte        `plist', 'pkBytes       `plist')
    replace_once(airplay / 'client.go', 'int `plist', 'plistNum `plist', 4)
    replace_once(airplay / 'client.go', 'bool          `plist', 'plistBool     `plist', 2)
    replace_once(airplay / 'client.go', 'return w, h', 'return int(w), int(h)')
    # Upstream debug mode dumps transport keys and packet bytes. Never enable it in a preview.
    replace_once(destination / 'airplaylib/airplaylib.go', 'airplay.DebugMode = true', 'airplay.DebugMode = false')
    for name in ['go.mod', 'go.sum']:
        shutil.copy2(NATIVE / name, destination / name)
    for patch in sorted((NATIVE / 'patches').glob('*.patch')):
        subprocess.run(['patch', '--batch', '--forward', '-p1', '-i', str(patch)], cwd=destination, check=True)
    shutil.copy2(NATIVE / 'tests/airplaylib_test.go', destination / 'airplaylib/airplaylib_test.go')
    return destination


def normalize_archive(source, destination):
    """Canonicalize outer AAR and nested JAR metadata, retaining actual compiled bytes."""
    import io
    def canonical(data):
        result = io.BytesIO()
        with zipfile.ZipFile(io.BytesIO(data)) as src, zipfile.ZipFile(result, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as dst:
            for name in sorted(src.namelist()):
                body = src.read(name)
                if name.endswith('.jar'):
                    body = canonical(body)
                info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                info.create_system = 3
                info.external_attr = (0o100644 << 16)
                dst.writestr(info, body, compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
        return result.getvalue()
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_bytes(canonical(source.read_bytes()))


def verify_aar(path):
    manifest = json.loads((NATIVE / 'prebuilt-manifest.json').read_text())
    if digest(NATIVE / 'SOURCE_LOCK.json') != manifest['source_lock_sha256']:
        raise ValueError('AirPlay manifest references a different source lock')
    if digest(path) != manifest['aar']['sha256']:
        raise ValueError(f'AirPlay AAR checksum mismatch: {path}')
    with zipfile.ZipFile(path) as archive:
        native_names = sorted(n for n in archive.namelist() if n.endswith('.so'))
        if native_names != sorted(manifest['jni']):
            raise ValueError(f'Unexpected AirPlay native libraries: {native_names}')
        for name, entry in manifest['jni'].items():
            body = archive.read(name)
            if hashlib.sha256(body).hexdigest() != entry['sha256'] or not body.startswith(b'\x7fELF'):
                raise ValueError(f'AirPlay JNI checksum/ELF mismatch: {name}')
        if hashlib.sha256(archive.read('classes.jar')).hexdigest() != manifest['classes_jar_sha256']:
            raise ValueError('AirPlay Java binding mismatch')
    print(f'AirPlay v0.0.33 sources and AAR verified: {path.name}')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--prepare', type=Path, help='prepare source in a new/empty directory')
    parser.add_argument('--verify-sources', action='store_true', help='check pinned sources only')
    parser.add_argument('--verify-aar', type=Path, help='check an AAR against the pinned manifest')
    parser.add_argument('--normalize', nargs=2, type=Path, metavar=('INPUT', 'OUTPUT'), help='normalize AAR/JAR ZIP metadata')
    args = parser.parse_args()
    verify_inputs()
    if args.prepare:
        print(prepare(args.prepare))
    elif args.normalize:
        normalize_archive(*args.normalize)
    elif args.verify_sources:
        print('AirPlay pinned source and module archives verified')
    else:
        verify_aar(args.verify_aar or ROOT / 'app/libs/airplaylib.aar')


if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError, KeyError, zipfile.BadZipFile, subprocess.CalledProcessError) as error:
        sys.exit(str(error))
