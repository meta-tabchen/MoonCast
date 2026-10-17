#!/usr/bin/env python3
"""Rebuild pinned AirPlay inputs plus reviewed local patches, outside checkout.
Requires Python 3.12+, git, make, perl, CMake 3.22.1/Ninja on PATH and
ANDROID_NDK_HOME pointing to NDK 27.0.12077973. No downloads or signing.
Usage: python build_airplay_receiver_candidate.py --source /path/to/MoonCast --work /new/empty/dir
"""
import argparse, hashlib, json, os, pathlib, shutil, subprocess, tarfile
p=argparse.ArgumentParser();p.add_argument('--source',type=pathlib.Path,required=True);p.add_argument('--work',type=pathlib.Path,required=True);p.add_argument('--abi',action='append',choices=['armeabi-v7a','arm64-v8a','x86_64']);a=p.parse_args()
src=a.source.resolve()/'native/airplay';w=a.work.resolve();ndk=pathlib.Path(os.environ['ANDROID_NDK_HOME']).resolve()
if w.exists() and any(w.iterdir()): raise SystemExit('Work directory must be empty; existing outputs are never removed')
w.mkdir(parents=True,exist_ok=True)
def sha(f): return hashlib.file_digest(open(f,'rb'),'sha256').hexdigest()
def run(args,**kw): subprocess.run([str(x) for x in args],check=True,**kw)
manifest=json.loads((src/'prebuilt-manifest.json').read_text());record={'sourceArchives':{},'patches':{},'sourceFiles':{},'commands':[],'libraries':{}}
for name,expected in manifest['sources'].items():
 actual=sha(src/name)
 if actual!=expected: raise SystemExit('Source checksum mismatch: '+name)
 record['sourceArchives'][name]=actual
for name in ['upstream-source.tar.gz','oboe-source.tar.gz']:
 with tarfile.open(src/name) as t:t.extractall(w,filter='data')
cpp=w/'android-airplay-server/app/src/main/cpp';ux=cpp/'third_party/UxPlay'
for name in ['raop_buffer.c','raop_rtp.c']:record['sourceFiles'][name]={'archive':sha(ux/'lib'/name)}
for patch in sorted((cpp/'patches/UxPlay').glob('*.patch')):
 run(['git','apply','--unidiff-zero',patch],cwd=ux);record['patches']['upstream/'+patch.name]=sha(patch)
for name in record['sourceFiles']:record['sourceFiles'][name]['afterUpstreamPatches']=sha(ux/'lib'/name)
for patch in sorted((src/'patches').glob('*.patch')):
 run(['git','apply','--unidiff-zero',patch],cwd=ux);record['patches']['mooncast/'+patch.name]=sha(patch)
for name in record['sourceFiles']:record['sourceFiles'][name]['afterMoonCastPatches']=sha(ux/'lib'/name)
for patch in sorted((src/'bridge-patches').glob('*.patch')):
 run(['git','apply','--unidiff-zero',patch],cwd=cpp);record['patches']['bridge/'+patch.name]=sha(patch)
record['sourceFiles']['android_raop_callbacks.c']={'afterMoonCastPatches':sha(cpp/'android_raop_callbacks.c')}
# Build-only changes: source URL becomes checksum-verified local archive; cap jobs;
# build exact pinned Oboe sources shared instead of resolving its prefab AAR.
f=cpp/'third_party/openssl-cmake/cmake/BuildOpenSSL.cmake';s=f.read_text();s=s.replace('URL https://mirror.viaduck.org/openssl/openssl-${OPENSSL_BUILD_VERSION}.tar.gz',f'URL {src}/openssl-3.4.4.tar.gz').replace('ProcessorCount(NUM_JOBS)','set(NUM_JOBS 4)');f.write_text(s)
f=cpp/'cmake/BuildFFmpeg.cmake';f.write_text(f.read_text().replace('ProcessorCount(_ffmpeg_jobs)','set(_ffmpeg_jobs 4)'))
conf=w/'oboe-config';conf.mkdir();(conf/'oboeConfig.cmake').write_text(f'set(BUILD_SHARED_LIBS ON)\nadd_subdirectory("{w}/oboe-1.9.3" "${{CMAKE_BINARY_DIR}}/oboe")\nadd_library(oboe::oboe ALIAS oboe)\n')
if 'Pkg.Revision = 27.0.12077973' not in (ndk/'source.properties').read_text():raise SystemExit('Wrong NDK version')
record['toolVersions']={x:subprocess.check_output([x,'--version'],text=True).splitlines()[0] for x in ['cmake','ninja']};record['toolVersions']['ndk']='27.0.12077973'
if record['toolVersions']['cmake']!='cmake version 3.22.1':raise SystemExit('Wrong CMake version')
triples={'armeabi-v7a':'arm-linux-androideabi','arm64-v8a':'aarch64-linux-android','x86_64':'x86_64-linux-android'}
for abi in a.abi or list(triples):
 b=w/f'build-{abi}';cmd=['cmake','-S',str(cpp),'-B',str(b),'-G','Ninja',f'-DCMAKE_TOOLCHAIN_FILE={ndk}/build/cmake/android.toolchain.cmake',f'-DANDROID_ABI={abi}','-DANDROID_PLATFORM=android-24','-DANDROID_STL=c++_shared','-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON','-DCMAKE_BUILD_TYPE=Release',f'-Doboe_DIR={conf}',f"-DOPENSSL_BUILD_HASH={record['sourceArchives']['openssl-3.4.4.tar.gz']}"]
 record['commands'].append(cmd);run(cmd);run(['cmake','--build',b,'--target','airplay_native','--parallel','4'])
 out=w/'outputs'/abi;out.mkdir(parents=True)
 inputs=[b/'libairplay_native.so',b/'oboe/liboboe.so',b/'third_party/openssl-cmake/openssl-build/usr/local/lib/libcrypto.so',ndk/f'toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/{triples[abi]}/libc++_shared.so']
 for f in inputs:
  dest=out/f.name;shutil.copy2(f,dest)
  run([ndk/'toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip','--strip-unneeded',dest]);record['libraries'][f'{abi}/{f.name}']=sha(dest)
 (w/'BUILD_RECORD.json').write_text(json.dumps(record,indent=2)+'\n')
print('Completed native outputs:',w/'outputs')
