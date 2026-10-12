#!/usr/bin/env python3
"""Restore only exact pinned release dependencies; never replace edited files."""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import tempfile
import time
import urllib.error
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def digest(path):
    h = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(chunk)
    return h.hexdigest()


def safe_target(root, name):
    rel = PurePosixPath(name)
    if rel.is_absolute() or '..' in rel.parts or '\\' in name or not rel.parts:
        raise ValueError('Unsafe dependency path: ' + name)
    target = root / name
    if target.is_symlink() or not target.resolve().is_relative_to(root.resolve()):
        raise ValueError('Unsafe dependency destination: ' + name)
    return target


def restore(archive, root, manifest, verify_only=False):
    if archive.stat().st_size != manifest['archiveBytes'] or digest(archive) != manifest['archiveSha256']:
        raise ValueError('Complete-source archive size or SHA256 does not match this release')
    with zipfile.ZipFile(archive) as source:
        names = source.namelist()
        if len(names) != len(set(names)):
            raise ValueError('Duplicate archive entries')
        pending = []
        for name, checksum, size, mode in manifest['files']:
            target = safe_target(root, name)
            info = source.getinfo('MoonCast/' + name)
            if info.file_size != size or ((info.external_attr >> 16) & 0o170000) not in (0, 0o100000):
                raise ValueError('Invalid dependency archive entry: ' + name)
            if target.exists():
                if not target.is_file() or digest(target) != checksum:
                    raise ValueError('Refusing to overwrite modified existing file: ' + name)
                continue
            pending.append((name, checksum, size, mode))
        # Validate every missing payload before writing any of them.
        for name, checksum, size, mode in pending:
            if hashlib.sha256(source.read('MoonCast/' + name)).hexdigest() != checksum:
                raise ValueError('Dependency hash mismatch: ' + name)
        if not verify_only:
            for name, checksum, size, mode in pending:
                target = safe_target(root, name)
                target.parent.mkdir(parents=True, exist_ok=True)
                # Exclusive creation avoids replacing a file created since preflight.
                with target.open('xb') as output:
                    output.write(source.read('MoonCast/' + name))
                target.chmod(0o755 if mode == '100755' else 0o644)
    print(f'Verified {len(manifest["files"])} pinned inputs; ' +
          (f'{len(pending)} missing (verification only).' if verify_only else f'restored {len(pending)}.'))


def download(url, destination, expected_size):
    # A newly published source branch can briefly precede its public Release.
    for attempt in range(6):
        try:
            request = urllib.request.Request(url, headers={'User-Agent': 'MoonCast-source-restore'})
            with urllib.request.urlopen(request, timeout=60) as response, destination.open('wb') as output:
                total = 0
                while True:
                    chunk = response.read(1024 * 1024)
                    if not chunk:
                        break
                    total += len(chunk)
                    if total > expected_size:
                        raise ValueError('Download exceeds pinned archive size')
                    output.write(chunk)
            return
        except urllib.error.HTTPError as error:
            if error.code != 404 or attempt == 5:
                raise
            print('Release asset is not public yet; retrying in 60 seconds.', flush=True)
            time.sleep(60)


def load_manifest(root):
    manifest = json.loads((root / 'scripts/release-inputs.json').read_text())
    files = []
    seen = set()
    for shard in manifest['shards']:
        path = safe_target(root, shard['path'])
        if path.stat().st_size != shard['bytes'] or digest(path) != shard['sha256']:
            raise ValueError('Release input shard size or SHA256 mismatch: ' + shard['path'])
        rows = json.loads(path.read_text())
        if len(rows) != shard['entries']:
            raise ValueError('Release input shard count mismatch')
        for row in rows:
            name, checksum, size, mode = row
            safe_target(root, name)
            if name in seen or len(checksum) != 64 or any(c not in '0123456789abcdef' for c in checksum):
                raise ValueError('Duplicate input or invalid checksum: ' + name)
            if not isinstance(size, int) or size < 0 or mode not in ('100644', '100755'):
                raise ValueError('Invalid input size or mode: ' + name)
            seen.add(name)
            files.append(row)
    if len(files) != manifest['inputCount']:
        raise ValueError('Release input total count mismatch')
    manifest['files'] = files
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--archive', type=Path, help='Use an already downloaded exact complete-source ZIP')
    parser.add_argument('--verify-only', action='store_true')
    args = parser.parse_args()
    manifest = load_manifest(ROOT)
    if args.archive:
        restore(args.archive.resolve(), ROOT, manifest, args.verify_only)
    else:
        url = ('https://github.com/' + manifest['repository'] + '/releases/download/' +
               manifest['tag'] + '/' + manifest['asset'])
        with tempfile.TemporaryDirectory(prefix='mooncast-source-') as folder:
            archive = Path(folder) / 'complete-source.zip'
            download(url, archive, manifest['archiveBytes'])
            restore(archive, ROOT, manifest, args.verify_only)


if __name__ == '__main__':
    main()
