#!/usr/bin/env python3
"""Build Sunshine from this exact source with local SHA256-pinned official dependencies.
NDK27 + Android SDK CMake3.31.1; all ABIs. No downloads, keys or identity changes.
"""
import argparse,hashlib,json,os,pathlib,re,shutil,subprocess
p=argparse.ArgumentParser();p.add_argument('--work',type=pathlib.Path,required=True);a=p.parse_args();r=pathlib.Path(__file__).resolve().parents[1];w=a.work.resolve();ndk=pathlib.Path(os.environ['ANDROID_NDK_HOME']).resolve()
def require(value,message):
 if not value:raise ValueError(message)
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def run(args,**kw):subprocess.run([str(x) for x in args],check=True,**kw)
require(not w.exists() or not any(w.iterdir()),'work directory must be empty');w.mkdir(parents=True,exist_ok=True)
require('Pkg.Revision = 27.0.12077973' in (ndk/'source.properties').read_text(),'NDK revision')
cmake=subprocess.check_output(['cmake','--version'],text=True).splitlines()[0];require(re.fullmatch(r'cmake version 3\.31\.1(?:-[A-Za-z0-9.-]+)?',cmake),'CMake revision: '+cmake)
require('Pkg.Revision = 3.31.1' in (pathlib.Path(shutil.which('cmake')).resolve().parent.parent/'source.properties').read_text(),'official SDK CMake package')
record={'sourceGitCommit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=r,text=True).strip(),'toolchain':{'ndk':'27.0.12077973','cmake':cmake,'api':26},'sources':{},'archives':{},'libraries':{},'commands':[],'recipeSha256':sha(pathlib.Path(__file__))}
for folder in ['sunshine','sessions']:
 for source in sorted((r/'native'/folder).rglob('*')):
  if source.is_file():record['sources'][str(source.relative_to(r))]=sha(source)
 shutil.copytree(r/'native'/folder,w/'source'/folder)
inputs=r/'native/sender-build-inputs';locks=json.loads((inputs/'source-input-manifest.json').read_text())['archives']
for row in locks:
 require(sha(inputs/row['file'])==row['sha256'],'source archive '+row['file']);record['archives'][row['file']]=row['sha256']
s=w/'source/sunshine'
p=s/'dependencies/Boost_Sunshine.cmake';text=p.read_text().replace('https://github.com/boostorg/boost/releases/download/boost-1.86.0/boost-1.86.0-cmake.tar.xz',str(inputs/'boost-1.86.0-cmake.tar.xz')).replace('MD5=D02759931CEDC02ADED80402906C5EB6','SHA256='+record['archives']['boost-1.86.0-cmake.tar.xz']);p.write_text(text)
p=s/'dependencies/openssl-cmake/cmake/BuildOpenSSL.cmake';text=p.read_text().replace('URL https://mirror.viaduck.org/openssl/openssl-${OPENSSL_BUILD_VERSION}.tar.gz','URL '+str(inputs/'openssl-3.5.5.tar.gz')).replace('ProcessorCount(NUM_JOBS)','set(NUM_JOBS 4)');p.write_text(text)
for abi in ['arm64-v8a','armeabi-v7a','x86_64']:
 b=w/('build-'+abi);cmd=['cmake','-S',s,'-B',b,'-G','Ninja',f'-DCMAKE_TOOLCHAIN_FILE={ndk}/build/cmake/android.toolchain.cmake',f'-DANDROID_ABI={abi}','-DANDROID_PLATFORM=android-26','-DANDROID_STL=c++_static','-DCMAKE_CXX_STANDARD=20','-DCMAKE_BUILD_TYPE=Release',f"-DOPENSSL_BUILD_HASH={record['archives']['openssl-3.5.5.tar.gz']}"]
 record['commands'].append([str(x) for x in cmd]);run(cmd);run(['cmake','--build',b,'--target','sunshine','--parallel','4'])
 out=w/'outputs'/abi;out.mkdir(parents=True)
 for source in [b/'libsunshine.so',b/'dependencies/openssl-cmake/openssl-build/usr/local/lib/libcrypto.so',b/'dependencies/openssl-cmake/openssl-build/usr/local/lib/libssl.so']:
  target=out/source.name;shutil.copy2(source,target);run([ndk/'toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip','--strip-unneeded',target]);record['libraries'][abi+'/'+source.name]=sha(target)
 (w/'BUILD_RECORD.json').write_text(json.dumps(record,indent=2)+'\n')
# Install only after every ABI succeeds, preserving the prior pins in provenance.
manifest_path=r/'native/prebuilt-manifest.json';manifest=json.loads(manifest_path.read_text());record['previousLibraries']=manifest['libraries']
for name,digest in record['libraries'].items():require(sha(r/'app/src/main/jniLibs'/name)==manifest['libraries'][name],'unexpected original '+name)
for name in record['libraries']:shutil.copy2(w/'outputs'/name,r/'app/src/main/jniLibs'/name)
record_path=r/'native/sunshine-candidate-build-record.json';record_path.write_text(json.dumps(record,indent=2)+'\n');(w/'BUILD_RECORD.json').write_text(json.dumps(record,indent=2)+'\n')
manifest['libraries']=record['libraries'];manifest['localBuild']={'recipe':'scripts/build_sunshine_candidate.py','recipeSha256':record['recipeSha256'],'record':'native/sunshine-candidate-build-record.json','recordSha256':sha(record_path)};manifest_path.write_text(json.dumps(manifest,indent=2)+'\n')
print('Built and installed 9 Sunshine runtime libraries with source/recipe/ABI provenance')
