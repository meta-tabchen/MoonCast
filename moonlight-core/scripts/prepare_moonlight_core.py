#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Restore pinned public source; optionally build all native inputs and record actual bytes.
No accounts, credentials, downloaded helper scripts, or root/system changes are used.
"""
import argparse, hashlib, json, os, pathlib, platform, shutil, subprocess, urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
PINS = [
 ('upstream','https://github.com/moonlight-stream/moonlight-android.git','b48494cb96bff23d8886c4775cc4f39a1075495d'),
 ('upstream/app/src/main/jni/moonlight-core/moonlight-common-c','https://github.com/moonlight-stream/moonlight-common-c.git','874ac9548f1bd6f095ef2b435c42cdde460e7821'),
 ('upstream/app/src/main/jni/moonlight-core/moonlight-common-c/enet','https://github.com/cgutman/enet.git','aca87840b57f045a1f7f9299e4b1b9b8e2a5e2f1'),
 ('upstream/app/src/main/jni/moonlight-core/moonlight-common-c/nanors','https://github.com/sleepybishop/nanors.git','b1e3c22ca0cdc0bb83e3cd6ed1a2fc77869ed99a'),
 ('native-sources/opus','https://github.com/xiph/opus.git','22244de5a79bd1d6d623c32e72bf1954b56235be'),
 ('native-sources/openssl','https://github.com/openssl/openssl.git','f089acdf4bc7ba94a79f4bf6eb7362c3e7d14aa9'),
]
ABIS=('arm64-v8a','armeabi-v7a','x86_64')
def sha(path): return hashlib.sha256(pathlib.Path(path).read_bytes()).hexdigest()
def canonical_build_environment(native, ndk, cmake, inherited=None):
    """Do not let ambient source/compiler overrides falsify recorded provenance."""
    inherited = os.environ if inherited is None else inherited
    env = {key: inherited[key] for key in
           ('PATH', 'HOME', 'TMPDIR', 'TEMP', 'TMP', 'LANG', 'LC_ALL', 'LC_CTYPE', 'TZ')
           if key in inherited}
    jobs = inherited.get('JOBS', '3')
    if not str(jobs).isdigit() or not 1 <= int(jobs) <= 64:
        raise ValueError('JOBS must be an integer from 1 to 64')
    native = pathlib.Path(native).resolve()
    sources = native / 'native-sources'
    env.update(NDK=str(ndk), CMAKE=str(cmake), JOBS=str(jobs),
               UPSTREAM_JNI=str(native / 'upstream/app/src/main/jni'),
               DEPS=str(native / 'dependency-rebuild/artifacts'),
               SOURCE_ROOT=str(sources),
               SOURCE_MANIFEST=str(sources / 'SOURCE-FILES-SHA256SUMS'),
               OPUS_SOURCE_DIR=str(sources / 'opus'),
               OPENSSL_SOURCE_DIR=str(sources / 'openssl'))
    return env

def run(args, **kw): subprocess.run([str(a) for a in args], check=True, **kw)
def capture(args): return subprocess.check_output([str(a) for a in args], text=True).strip()
def verify_source_lock():
    lock=json.loads((ROOT/'SOURCE_LOCK.json').read_text())
    for manifest,key in [('SOURCE-INPUTS-SHA256SUMS','source_input_manifest_sha256'),('JAVA-SOURCES.json','java_sources_manifest_sha256')]:
        if sha(ROOT/manifest)!=lock[key]:raise RuntimeError('Source manifest/lock disagreement: '+manifest)
    keys=['moonlight_android','moonlight_common_c','enet','nanors','opus_commit','openssl_commit']
    for key,(_,_,revision) in zip(keys,PINS):
        actual=lock[key]['commit'] if key=='moonlight_android' else lock[key]
        if actual!=revision: raise RuntimeError('Script/source lock disagreement: '+key)

def restore(path,url,revision,native):
    dest=native/path
    if (dest/'.git').exists():
        actual=capture(['git','-C',dest,'rev-parse','HEAD'])
        if actual!=revision: raise RuntimeError('Refusing to overwrite different source revision: '+str(dest))
    elif dest.exists() and any(dest.iterdir()):
        # A delivered source overlay has no Git metadata. Validate its locked inventory.
        manifest=ROOT/'SOURCE-INPUTS-SHA256SUMS'
        entries={}
        for line in manifest.read_text().splitlines():
            digest,name=line.split('  ',1)
            if name.startswith(path+'/'): entries[name[len(path)+1:]]=digest
        actual={p.relative_to(dest).as_posix() for p in dest.rglob('*') if p.is_file()}
        if actual!=set(entries): raise RuntimeError('Source inventory mismatch: '+str(dest))
        for name,digest in entries.items():
            if sha(dest/name)!=digest:raise RuntimeError('Source hash mismatch: '+str(dest/name))
        print('Verified source archive:',path,flush=True)
        return
    else:
        dest.mkdir(parents=True,exist_ok=True)
        run(['git','init','-q',dest]);run(['git','-C',dest,'remote','add','origin',url])
        run(['git','-C',dest,'fetch','--depth','1','origin',revision])
        run(['git','-C',dest,'checkout','-q','--detach','FETCH_HEAD'])
    if capture(['git','-C',dest,'status','--porcelain']):
        raise RuntimeError('Refusing non-pristine source: '+str(dest))
    if capture(['git','-C',dest,'rev-parse','HEAD'])!=revision:raise RuntimeError('Wrong fetched commit')
    print('Verified fixed source:',path,revision,flush=True)

def verify_cpufeatures(directory):
    prefix='native-sources/android-cpufeatures-ndk29/'
    expected={name[len(prefix):]:digest for digest,name in
        (line.split('  ',1) for line in (ROOT/'SOURCE-INPUTS-SHA256SUMS').read_text().splitlines())
        if name.startswith(prefix)}
    actual={p.relative_to(directory).as_posix() for p in directory.rglob('*') if p.is_file()}
    if len(expected)!=5 or actual!=set(expected):raise RuntimeError('cpu-features source/notice inventory mismatch: '+str(directory))
    for name,digest in expected.items():
        if sha(directory/name)!=digest:raise RuntimeError('cpu-features source hash mismatch: '+name)

def restore_cpufeatures(native,ndk):
    dest=native/'native-sources/android-cpufeatures-ndk29'
    if ndk is not None:
        # ndk-build imports this exact source. Verify the installed inputs as well
        # as the redistributed copy; source.properties alone is not sufficient.
        verify_cpufeatures(ndk/'sources/android/cpufeatures')
        if not dest.exists():shutil.copytree(ndk/'sources/android/cpufeatures',dest)
    if not dest.exists():raise RuntimeError('Complete source restoration requires --ndk <NDK29>, or an extracted source bundle containing cpu-features')
    verify_cpufeatures(dest)
    print('Verified Android NDK29 cpu-features source and notices',flush=True)

def restore_java_sources(native):
    out=native/'java-sources';out.mkdir(parents=True,exist_ok=True)
    entries=json.loads((ROOT/'JAVA-SOURCES.json').read_text())
    for row in entries:
        if 'error' in row:raise RuntimeError('Unresolved Java source: '+row['file'])
        if pathlib.PurePosixPath(row['file']).name!=row['file']:raise RuntimeError('Unsafe Java source filename')
        p=out/row['file']
        if not p.exists():
            data=urllib.request.urlopen(row['url'],timeout=90).read()
            if hashlib.sha256(data).hexdigest()!=row['sha256']:raise RuntimeError('Downloaded source hash mismatch')
            p.write_bytes(data)
        if sha(p)!=row['sha256']:raise RuntimeError('Java source hash mismatch: '+str(p))
    shutil.copyfile(ROOT/'JAVA-SOURCES.json',out/'SOURCES.json')

def verify_native(path,abi,ndk):
    import struct
    header=path.read_bytes()[:64]
    if header[:4]!=b'\x7fELF' or header[5]!=1:raise RuntimeError('Expected little-endian ELF')
    machine=struct.unpack_from('<H',header,18)[0]
    if machine!={'arm64-v8a':183,'armeabi-v7a':40,'x86_64':62}[abi]:raise RuntimeError('ABI/ELF architecture mismatch')
    if struct.unpack_from('<H',header,16)[0]!=3:raise RuntimeError('Expected shared ELF object')
    readelf=ndk/'toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf'
    dynamic=capture([readelf,'--dyn-syms','--wide',path]);exports=[]
    for line in dynamic.splitlines():
        fields=line.split()
        if len(fields)>=8 and fields[4] in ('GLOBAL','WEAK') and fields[6]!='UND':exports.append(fields[7])
    expected_prefix='Java_com_limelight_nvstream_jni_MoonBridge_'
    if any(not(s.startswith(expected_prefix) or s=='JNI_OnLoad') for s in exports):
        raise RuntimeError('Unexpected dynamic export: '+repr(exports))
    for method in ['init','startConnection','stopConnection','interruptConnection']:
        if expected_prefix+method not in exports:raise RuntimeError('Missing JNI export: '+method)
    reloc=capture([readelf,'--relocations','--wide',path])
    if any(p in reloc for p in ['EVP_','OPENSSL_','CRYPTO_','opus_']):raise RuntimeError('Interposable static dependency relocation')
    needed=capture([readelf,'--dynamic',path])
    if 'libcrypto.so' in needed or 'libssl.so' in needed or 'libopus.so' in needed:raise RuntimeError('Unexpected shared crypto/audio dependency')
    for line in capture([readelf,'--program-headers','--wide',path]).splitlines():
        if line.strip().startswith('LOAD ') and int(line.split()[-1],16)<(4096 if abi=='armeabi-v7a' else 16384):raise RuntimeError('Bad ELF load alignment')
    return exports

def record_manifest(ndk,cmake,deps_root,output=None):
    binaries=[]
    for abi in ABIS:
        p=ROOT/'src/main/jniLibs'/abi/'libmoonlight-core.so'
        exports=verify_native(p,abi,ndk)
        binaries.append({'abi':abi,'path':p.relative_to(ROOT).as_posix(),'bytes':p.stat().st_size,'sha256':sha(p),'jni_exports':len(exports)})
    archives=[]
    for p in sorted(deps_root.glob('*/*.a')):
        archives.append({'path':p.relative_to(deps_root).as_posix(),'sha256':sha(p),'bytes':p.stat().st_size})
    record={'schema':1,'source_lock_sha256':sha(ROOT/'SOURCE_LOCK.json'),
        'source_revisions':[{'path':p,'url':u,'commit':r} for p,u,r in PINS],
        'toolchain':{'ndk':'29.0.14206865','cmake':capture([cmake,'--version']).splitlines()[0],'dependency_api':21,'jni_api':26,'configuration':'release','python':platform.python_version()},
        'binaries':binaries,'static_archives':archives,
        'validation':'architectures via build target; exact JNI exports; crypto/Opus symbols hidden; no shared crypto/Opus dependency; 64-bit 16KB ELF alignment. Not device interoperability.'}
    target=pathlib.Path(output) if output else ROOT/'native-build-manifest.json'
    target.write_text(json.dumps(record,indent=2)+'\n');print('Recorded actual build manifest:',target,flush=True)
    return record

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--build',action='store_true',help='Build every native dependency from fixed source, then record actual output hashes')
    p.add_argument('--ndk',default=os.environ.get('NDK') or os.environ.get('ANDROID_NDK_HOME'))
    p.add_argument('--cmake',default=os.environ.get('CMAKE') or shutil.which('cmake'))
    p.add_argument('--native-manifest',type=pathlib.Path,help='Override output manifest path (with --build)')
    args=p.parse_args();verify_source_lock();native=ROOT/'native';native.mkdir(exist_ok=True)
    ndk=pathlib.Path(args.ndk).resolve() if args.ndk else None
    if ndk is not None and 'Pkg.Revision = 29.0.14206865' not in (ndk/'source.properties').read_text():raise RuntimeError('NDK29.0.14206865 required')
    restore_cpufeatures(native,ndk)
    for path,url,revision in PINS:restore(path,url,revision,native)
    # Recheck the parent after all recursive gitlinks are materialized.
    if (native/'upstream/.git').exists() and capture(['git','-C',native/'upstream','status','--porcelain']):raise RuntimeError('Moonlight source/gitlink mismatch')
    restore_java_sources(native)
    source_manifest=[]
    for line in (ROOT/'SOURCE-INPUTS-SHA256SUMS').read_text().splitlines():
        digest,name=line.split('  ',1)
        if name.startswith('native-sources/'):
            source_manifest.append(digest+'  '+name[len('native-sources/'):])
    (native/'native-sources/SOURCE-FILES-SHA256SUMS').write_text('\n'.join(source_manifest)+'\n')
    shutil.copyfile(ROOT/'scripts/SOURCE-BUILDING.md',native/'BUILDING.md')
    for dest,source in [('dependency-rebuild/rebuild.sh','rebuild_moonlight_dependencies.sh'),('full-source-native/relink.sh','relink_moonlight_native.sh'),('full-source-native/exports.map','exports.map')]:
        target=native/dest;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(ROOT/'scripts'/source,target)
    if not args.ndk:
        if args.build:raise RuntimeError('--ndk or NDK is required')
        print('Sources restored. Supply --build --ndk ... --cmake ... to create native artifacts.');return
    if not args.build:return
    if not args.cmake:raise RuntimeError('CMake3.31.10 is required')
    cmake=pathlib.Path(args.cmake).resolve()
    if capture([cmake,'--version']).splitlines()[0]!='cmake version 3.31.10':raise RuntimeError('CMake3.31.10 required for pinned build')
    env=canonical_build_environment(native,ndk,cmake)
    run(['bash',native/'dependency-rebuild/rebuild.sh'],env=env)
    run(['bash',native/'full-source-native/relink.sh'],env=env)
    for abi in ABIS:
        out=ROOT/'src/main/jniLibs'/abi;out.mkdir(parents=True,exist_ok=True)
        shutil.copyfile(native/'full-source-native/jniLibs'/abi/'libmoonlight-core.so',out/'libmoonlight-core.so')
    record_manifest(ndk,cmake,native/'dependency-rebuild/artifacts',args.native_manifest)
if __name__=='__main__':main()
