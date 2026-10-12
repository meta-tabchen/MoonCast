#!/usr/bin/env python3
"""Verify vendored sender dependencies; prepare a fresh isolated offline source tree.

This does not invoke CMake, install tools, compile code, or change pinned sources.
Requires Python 3.12+ for tarfile's explicit data extraction filter.
"""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import shutil
import tarfile

ROOT = Path(__file__).resolve().parents[1]
INPUTS = ROOT / 'native/sender-build-inputs'
ARCHIVES = {
    'openssl-3.5.5.tar.gz': ('b28c91532a8b65a1f983b4c28b7488174e4a01008e29ce8e69bd789f28bc2a89', 'openssl-3.5.5', '.sha256'),
    'boost-1.86.0-cmake.tar.xz': ('2c5ec5edcdff47ff55e27ed9560b0a0b94b07bd07ed9928b476150e16b0efc57', 'boost-1.86.0', '.txt'),
}
PATCH_FILE = Path('dependencies/openssl-cmake/cmake/BuildOpenSSL.cmake')
PATCH_INPUT_SHA256 = 'ed4970f344dae3c02b7fe8913df42d46b604263fe7d870bf3744d5c0bd9b576c'
BOOST_RECIPE = Path('dependencies/Boost_Sunshine.cmake')
BOOST_RECIPE_SHA256 = 'a51b89a8d8b39e650d1758d24a8981b90310cf92171c20e2cabcad591edbbf8c'

def digest(path):
    with path.open('rb') as f:
        return hashlib.file_digest(f, 'sha256').hexdigest()

def verify():
    for name, (expected, _, suffix) in ARCHIVES.items():
        if digest(INPUTS / name) != expected:
            raise ValueError('Archive SHA256 mismatch: ' + name)
        if (INPUTS / (name + suffix)).read_text().split()[0] != expected:
            raise ValueError('Official checksum sidecar mismatch: ' + name)
    if digest(ROOT / 'native/sunshine' / PATCH_FILE) != PATCH_INPUT_SHA256:
        raise ValueError('Pinned Sunshine OpenSSL recipe changed; review patch before use')
    if digest(ROOT / 'native/sunshine' / BOOST_RECIPE) != BOOST_RECIPE_SHA256:
        raise ValueError('Pinned Sunshine Boost recipe changed; review patch before use')

def safe_extract(archive, destination, expected_root):
    with tarfile.open(archive, 'r:*') as tf:
        members = tf.getmembers()
        if len(members) > 100000 or sum(m.size for m in members) > 2 * 1024**3:
            raise ValueError('Archive exceeds extraction limits')
        seen = set()
        for member in members:
            path = PurePosixPath(member.name)
            if (path.is_absolute() or '..' in path.parts or not path.parts
                    or path.parts[0] != expected_root or '\\' in member.name
                    or not (member.isfile() or member.isdir())):
                raise ValueError('Unsafe archive member: ' + member.name)
            if path in seen:
                raise ValueError('Duplicate archive member: ' + member.name)
            seen.add(path)
        tf.extractall(destination, members=members, filter='data')

def prepare(destination):
    # Refuse existing destinations, including symlinks, and anything inside the repo.
    if destination.exists() or destination.is_symlink():
        raise ValueError('Work directory must not exist')
    destination = destination.resolve()
    if destination == ROOT or ROOT in destination.parents:
        raise ValueError('Work directory must be outside the source repository')
    verify()
    destination.mkdir(parents=True)
    for name, (_, root, _) in ARCHIVES.items():
        safe_extract(INPUTS / name, destination, root)
    sunshine = destination / 'sunshine'
    # Copy source only; the pinned repository itself is never patched.
    shutil.copytree(ROOT / 'native/sunshine', sunshine,
                    ignore=shutil.ignore_patterns('.git', 'build', '.cxx', '__pycache__'))
    recipe = sunshine / PATCH_FILE
    old = '        URL https://mirror.viaduck.org/openssl/openssl-${OPENSSL_BUILD_VERSION}.tar.gz\n        ${OPENSSL_CHECK_HASH}'
    new = '        SOURCE_DIR "${MOONCAST_OPENSSL_SOURCE_DIR}"\n        DOWNLOAD_COMMAND ""'
    original = recipe.read_text()
    if original.count(old) != 1:
        raise ValueError('Unexpected OpenSSL recipe; refusing ambiguous patch')
    recipe.write_text(original.replace(old, new))
    boost_recipe = sunshine / BOOST_RECIPE
    boost_original = boost_recipe.read_text()
    boost_find = 'find_package(Boost CONFIG ${BOOST_VERSION} COMPONENTS ${BOOST_COMPONENTS})'
    if boost_original.count(boost_find) != 1:
        raise ValueError('Unexpected Boost recipe')
    # Disable only initial system discovery. Later required find_package calls
    # must remain enabled and use FetchContent's OVERRIDE_FIND_PACKAGE redirect.
    boost_recipe.write_text(boost_original.replace(boost_find, 'set(Boost_FOUND FALSE) # use verified local FetchContent source'))
    def cmake_path(path):
        text = str(path)
        if any(c in text for c in [';', '\n', '\r', ']', '$']):
            raise ValueError('Use a simple work-directory path without CMake metacharacters')
        return '[[' + text + ']]'
    cache = destination / 'sender-inputs.cmake'
    cache.write_text(
        '# Generated verified local inputs; use once per ABI in a fresh work directory.\n'
        'set(FETCHCONTENT_SOURCE_DIR_BOOST ' + cmake_path(destination / 'boost-1.86.0') + ' CACHE PATH "Pinned Boost" FORCE)\n'
        'set(FETCHCONTENT_FULLY_DISCONNECTED TRUE CACHE BOOL "No FetchContent downloads" FORCE)\n'
        'set(MOONCAST_OPENSSL_SOURCE_DIR ' + cmake_path(destination / 'openssl-3.5.5') + ' CACHE PATH "Verified OpenSSL" FORCE)\n')
    record = {'sunshine_source': str(sunshine), 'cmake_initial_cache': str(cache),
              'patched_file': str(PATCH_FILE), 'original_recipe_sha256': PATCH_INPUT_SHA256,
              'patched_recipe_sha256': digest(recipe),
              'boost_recipe_original_sha256': BOOST_RECIPE_SHA256,
              'boost_recipe_patched_sha256': digest(boost_recipe),
              'archives': {n: v[0] for n, v in ARCHIVES.items()},
              'native_compilation_performed': False}
    (destination / 'prepared-inputs.json').write_text(json.dumps(record, indent=2) + '\n')
    print(json.dumps(record, indent=2))

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--workdir', type=Path, help='New external work directory to prepare')
    parser.add_argument('--verify-only', action='store_true')
    args = parser.parse_args()
    if bool(args.workdir) == args.verify_only:
        parser.error('Choose exactly one of --verify-only or --workdir')
    verify()
    if args.verify_only:
        print('Verified two official source archives, sidecars and pinned OpenSSL recipe')
    else:
        prepare(args.workdir)

if __name__ == '__main__':
    main()
