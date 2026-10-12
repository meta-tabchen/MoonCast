#!/usr/bin/env python3
"""Validated generated receiver inputs; never sweep a workspace or include signing material."""
import hashlib
import json
from pathlib import Path, PurePosixPath
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ABIS = {'arm64-v8a', 'armeabi-v7a', 'x86_64'}

def digest(path):
    result = hashlib.sha256()
    with Path(path).open('rb') as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b''):
            result.update(chunk)
    return result.hexdigest()

def safe_relative(value):
    path = PurePosixPath(value)
    if path.is_absolute() or '..' in path.parts or not path.parts or '\\' in value:
        raise ValueError('Unsafe generated input path')
    return path.as_posix()

def generated_inputs(root=ROOT):
    """Return exact archive-name -> (path, checksum) map and verify every input byte.

    OpenSSL's public upstream test keys are legitimate corresponding source, not user
    credentials. They enter only through the committed upstream inventory, never a glob.
    """
    root = Path(root)
    result = {}
    def add(name, checksum):
        name = safe_relative(name)
        if len(checksum) != 64 or any(c not in '0123456789abcdef' for c in checksum):
            raise ValueError('Invalid generated-input digest')
        path = root / name
        if not path.is_file() or path.is_symlink() or not path.resolve().is_relative_to(root.resolve()):
            raise ValueError('Missing/unsafe generated input: ' + name)
        if digest(path) != checksum:
            raise ValueError('Generated input hash mismatch: ' + name)
        if name in result and result[name][1] != checksum:
            raise ValueError('Conflicting generated input: ' + name)
        result[name] = (path, checksum)
    airplay = json.loads((root / 'native/airplay/prebuilt-manifest.json').read_text())
    for prefix, key in [('native/airplay/', 'sources'), ('tv/src/main/jniLibs/', 'libraries')]:
        for name, checksum in airplay[key].items():
            add(prefix + safe_relative(name), checksum)
    core = root / 'moonlight-core'
    lock = json.loads((core / 'SOURCE_LOCK.json').read_text())
    manifest_file = core / 'native-build-manifest.json'
    build = json.loads(manifest_file.read_text())
    if build['source_lock_sha256'] != digest(core / 'SOURCE_LOCK.json'):
        raise ValueError('GameStream source lock differs from actual build record')
    if build['toolchain']['ndk'] != '29.0.14206865':
        raise ValueError('Unexpected GameStream NDK')
    if len(build['binaries']) != 3 or {r['abi'] for r in build['binaries']} != ABIS:
        raise ValueError('GameStream build must contain exactly three ABI outputs')
    add('moonlight-core/native-build-manifest.json', digest(manifest_file))
    for row in build['binaries']:
        expected = f"src/main/jniLibs/{row['abi']}/libmoonlight-core.so"
        if row['path'] != expected:
            raise ValueError('Unexpected GameStream runtime path')
        add('moonlight-core/' + expected, row['sha256'])
    for filename, key in [('SOURCE-INPUTS-SHA256SUMS', 'source_input_manifest_sha256'),
                          ('JAVA-SOURCES.json', 'java_sources_manifest_sha256')]:
        if digest(core / filename) != lock[key]:
            raise ValueError('Corresponding-source inventory differs from lock')
    for line in (core / 'SOURCE-INPUTS-SHA256SUMS').read_text().splitlines():
        checksum, name = line.split('  ', 1)
        add('moonlight-core/native/' + safe_relative(name), checksum)
    for row in json.loads((core / 'JAVA-SOURCES.json').read_text()):
        if PurePosixPath(row['file']).name != row['file']:
            raise ValueError('Invalid Java-source archive filename')
        add('moonlight-core/native/java-sources/' + row['file'], row['sha256'])
    # Portable recipes are committed; retain the generated copies required by their
    # no-Git source-overlay layout and compare them to those committed recipes.
    for target, source in [('BUILDING.md', 'SOURCE-BUILDING.md'),
                           ('dependency-rebuild/rebuild.sh', 'rebuild_moonlight_dependencies.sh'),
                           ('full-source-native/relink.sh', 'relink_moonlight_native.sh'),
                           ('full-source-native/exports.map', 'exports.map')]:
        add('moonlight-core/native/' + target, digest(core / 'scripts' / source))
    source_manifest = core / 'native/native-sources/SOURCE-FILES-SHA256SUMS'
    expected = ''.join(line.split('  ', 1)[0] + '  ' + line.split('  ', 1)[1][len('native-sources/'):] + '\n'
                       for line in (core / 'SOURCE-INPUTS-SHA256SUMS').read_text().splitlines()
                       if line.split('  ', 1)[1].startswith('native-sources/'))
    add('moonlight-core/native/native-sources/SOURCE-FILES-SHA256SUMS', hashlib.sha256(expected.encode()).hexdigest())
    return result

def pack(destination, root=ROOT):
    inputs = generated_inputs(root)
    with zipfile.ZipFile(destination, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for name, (path, _) in sorted(inputs.items()):
            info = zipfile.ZipInfo(name, date_time=(2020, 1, 1, 0, 0, 0))
            info.external_attr = 0o100644 << 16
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, path.read_bytes())
    return len(inputs)

def restore(source, root=ROOT):
    root = Path(root)
    with zipfile.ZipFile(source) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)) or sum(i.file_size for i in archive.infolist()) > 1024**3:
            raise ValueError('Invalid build-input archive')
        # Derive the exact whitelist from the checkout, before writing anything. An
        # archive must never replace the committed inventories used to authenticate it.
        airplay = json.loads((root / 'native/airplay/prebuilt-manifest.json').read_text())
        allowed = {'native/airplay/' + safe_relative(n) for n in airplay['sources']}
        allowed |= {'tv/src/main/jniLibs/' + safe_relative(n) for n in airplay['libraries']}
        core = root / 'moonlight-core'
        allowed |= {'moonlight-core/native/' + safe_relative(line.split('  ', 1)[1])
                    for line in (core / 'SOURCE-INPUTS-SHA256SUMS').read_text().splitlines()}
        allowed |= {'moonlight-core/native/java-sources/' + safe_relative(r['file'])
                    for r in json.loads((core / 'JAVA-SOURCES.json').read_text())}
        allowed |= {'moonlight-core/src/main/jniLibs/' + abi + '/libmoonlight-core.so' for abi in ABIS}
        allowed |= {'moonlight-core/native-build-manifest.json'}
        allowed |= {'moonlight-core/native/' + n for n in ['BUILDING.md',
                    'dependency-rebuild/rebuild.sh', 'full-source-native/relink.sh',
                    'full-source-native/exports.map', 'native-sources/SOURCE-FILES-SHA256SUMS']}
        if set(names) != allowed:
            raise ValueError('Build-input archive does not match the committed path inventory')
        for info in archive.infolist():
            name = safe_relative(info.filename)
            if not name.startswith(('moonlight-core/native/', 'moonlight-core/src/main/jniLibs/',
                                    'native/airplay/', 'tv/src/main/jniLibs/')) and name != 'moonlight-core/native-build-manifest.json':
                raise ValueError('Unexpected generated archive entry')
            if (info.external_attr >> 16) & 0o170000 not in (0, 0o100000):
                raise ValueError('Nonregular generated archive entry')
            target = root / name
            if target.is_symlink() or not target.resolve().is_relative_to(root.resolve()):
                raise ValueError('Unsafe extraction target')
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(archive.read(info))
    expected = generated_inputs(root)
    if set(names) != set(expected):
        raise ValueError('Build-input archive contains missing or unexpected files')
    return len(expected)

if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    modes = parser.add_mutually_exclusive_group(required=True)
    modes.add_argument('--pack', type=Path)
    modes.add_argument('--restore', type=Path)
    args = parser.parse_args()
    count = pack(args.pack) if args.pack else restore(args.restore)
    print(f'Validated {count} generated runtime/corresponding-source inputs.')
