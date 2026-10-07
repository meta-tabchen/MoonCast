"""Build the small session-stop adapter; does not rebuild or alter pinned Sunshine."""
from pathlib import Path
import argparse, hashlib, json, subprocess

parser=argparse.ArgumentParser()
parser.add_argument('--ndk',type=Path,required=True)
parser.add_argument('--output',type=Path,required=True)
args=parser.parse_args()
root=Path(__file__).resolve().parents[1]
source=root/'native/sessions/session_bridge.cpp'
toolchain=args.ndk/'toolchains/llvm/prebuilt/linux-x86_64/bin'
targets={'arm64-v8a':'aarch64-linux-android26','armeabi-v7a':'armv7a-linux-androideabi26','x86_64':'x86_64-linux-android26'}
libraries={}
for abi,target in targets.items():
    folder=args.output/abi;folder.mkdir(parents=True,exist_ok=True)
    library=folder/'libmooncast_sessions.so'
    subprocess.run([str(toolchain/(target+'-clang++')),'-std=c++17','-O2','-fPIC','-shared','-fno-exceptions','-fno-rtti','-nostdlib++','-Wl,-z,max-page-size=16384','-Wl,-soname,libmooncast_sessions.so',str(source),'-ldl','-o',str(library)],check=True)
    libraries[f'{abi}/{library.name}']=hashlib.sha256(library.read_bytes()).hexdigest()
manifest={'source':'native/sessions/session_bridge.cpp','sourceSha256':hashlib.sha256(source.read_bytes()).hexdigest(),'ndk':'27.0.12077973','api':26,'toolchainHost':'linux-x86_64','commandSource':'scripts/build_session_bridge.py','commandSourceSha256':hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),'libraries':libraries}
(args.output/'session-bridge-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
print('Built session bridge for:',', '.join(targets))
