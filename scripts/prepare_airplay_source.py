#!/usr/bin/env python3
"""Materialize full GPL corresponding native source, pinned independently of moving branches.
Generated archives are release inputs, not committed binaries. No upstream code is executed.
Usage: python scripts/prepare_airplay_source.py [--upstream-dir CHECKOUT] [--oboe-dir CHECKOUT]
"""
from pathlib import Path
import argparse
import gzip
import hashlib
import json
import shutil
import subprocess
import tarfile
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / 'native/airplay'
CACHE = ROOT / '.gradle/airplay-source'
UPSTREAM = 'c8defdd70d7e6a04f4f1b71d353653682d594106'
OBOE = 'b15f5e39c01a7ada306d959e5129620b145fb8b4'
SUBMODULES = {
    'app/src/main/cpp/third_party/UxPlay': '462153392f2e30937424922039ff9f0cda5e7b1a',
    'app/src/main/cpp/third_party/ffmpeg': '38b88335f99e76ed89ff3c93f877fdefce736c13',
    'app/src/main/cpp/third_party/libplist': 'f41b1ea67045e0c09339974d83e389972d84f166',
    'app/src/main/cpp/third_party/openssl-cmake': '4edd36a8dab5f85a8f92650b5bdf6e0cab13aab8',
}
OPENSSL_URL = 'https://github.com/openssl/openssl/releases/download/openssl-3.4.4/openssl-3.4.4.tar.gz'
OPENSSL_SHA256 = '7bdf55ac20f2779e99e5eca306f824fad2b37dee5a06cc35ed5a8b85a6060010'

def git(path, *args):
    return subprocess.check_output(['git', '-C', str(path), *args], text=True).strip()

def checkout(path, url, commit):
    if not path.exists():
        path.mkdir(parents=True)
        subprocess.run(['git', 'init', str(path)], check=True, stdout=subprocess.DEVNULL)
        git(path, 'remote', 'add', 'origin', url)
        git(path, 'fetch', '--depth', '1', 'origin', commit)
        git(path, 'checkout', '--detach', 'FETCH_HEAD')
    if git(path, 'rev-parse', 'HEAD') != commit:
        raise SystemExit(f'Wrong source revision: {path}')
    if git(path, 'status', '--porcelain', '--untracked-files=all'):
        raise SystemExit(f'Dirty source tree: {path}')

def archive_tree(source, target, prefix):
    # Stable metadata makes hash verification reproducible across machines/checkouts.
    with target.open('wb') as raw, gzip.GzipFile(filename='', mode='wb', fileobj=raw, mtime=0, compresslevel=9) as gz:
        with tarfile.open(fileobj=gz, mode='w') as archive:
            for path in sorted(source.rglob('*')):
                relative = path.relative_to(source)
                if '.git' in relative.parts or '__pycache__' in relative.parts:
                    continue
                name = prefix + '/' + relative.as_posix()
                info = archive.gettarinfo(str(path), arcname=name)
                info.uid = info.gid = 0; info.uname = info.gname = ''; info.mtime = 0
                # Preserve executable source/build scripts; canonical permissions otherwise.
                info.mode = 0o755 if info.isdir() or info.mode & 0o111 else 0o644
                if info.isfile():
                    with path.open('rb') as stream: archive.addfile(info, stream)
                else: archive.addfile(info)

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--upstream-dir', type=Path)
    parser.add_argument('--oboe-dir', type=Path)
    args = parser.parse_args()
    CACHE.mkdir(parents=True, exist_ok=True); DEST.mkdir(parents=True, exist_ok=True)
    upstream = args.upstream_dir or CACHE / 'android-airplay-server'
    oboe = args.oboe_dir or CACHE / 'oboe'
    checkout(upstream, 'https://github.com/jqssun/android-airplay-server.git', UPSTREAM)
    subprocess.run(['git', '-C', str(upstream), 'submodule', 'update', '--init', '--recursive', '--depth', '1'], check=True)
    for path, commit in SUBMODULES.items():
        if git(upstream / path, 'rev-parse', 'HEAD') != commit or git(upstream / path, 'status', '--porcelain'):
            raise SystemExit(f'Wrong or modified dependency revision: {path}')
    checkout(oboe, 'https://github.com/google/oboe.git', OBOE)
    archive_tree(upstream, DEST / 'upstream-source.tar.gz', 'android-airplay-server')
    archive_tree(oboe, DEST / 'oboe-source.tar.gz', 'oboe-1.9.3')
    openssl = DEST / 'openssl-3.4.4.tar.gz'
    if not openssl.exists(): urllib.request.urlretrieve(OPENSSL_URL, openssl)
    if digest(openssl) != OPENSSL_SHA256:
        raise SystemExit('OpenSSL source SHA-256 mismatch')
    sources = {name: digest(DEST / name) for name in ['upstream-source.tar.gz', 'oboe-source.tar.gz', 'openssl-3.4.4.tar.gz']}
    manifest_path = DEST / 'prebuilt-manifest.json'
    manifest = json.loads(manifest_path.read_text())
    if manifest.get('sources') and manifest['sources'] != sources:
        raise SystemExit('Corresponding-source archive hash mismatch; do not publish')
    manifest['sources'] = sources
    manifest['sourceCommits'] = {'android-airplay-server': UPSTREAM, 'oboe': OBOE, **SUBMODULES}
    manifest_path.write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    print('Verified/materialized corresponding AirPlay source:', sources)

if __name__ == '__main__':
    main()
