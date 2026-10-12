#!/usr/bin/env python3
"""Static payload/provenance checks; not host or device interoperability tests."""
from pathlib import Path
import hashlib,json,subprocess,sys
root=Path(sys.argv[1]) if len(sys.argv)>1 else Path(__file__).resolve().parents[1]
lock=json.loads((root/'native-build-manifest.json').read_text())
assert lock['source_lock_sha256']==hashlib.sha256((root/'SOURCE_LOCK.json').read_bytes()).hexdigest()
expected={'arm64-v8a','armeabi-v7a','x86_64'}
paths=list((root/'src/main/jniLibs').glob('*/*.so'))
assert {p.parent.name for p in paths}==expected
assert len(paths)==3
assert len(lock['binaries'])==3 and {r['abi'] for r in lock['binaries']}==expected
for row in lock['binaries']:
 p=root/row['path'];assert p.stat().st_size==row['bytes'];assert hashlib.sha256(p.read_bytes()).hexdigest()==row['sha256']
 symbols=subprocess.check_output(['readelf','--wide','--dyn-syms',str(p)],text=True)
 for line in symbols.splitlines():
  fields=line.split()
  if len(fields)>=8 and fields[4] in ('GLOBAL','WEAK') and fields[6]!='UND':
   assert fields[7].startswith('Java_com_limelight_nvstream_jni_MoonBridge_')
 for name in ['EVP_','CRYPTO_','OPENSSL_','opus_']:
  assert name not in symbols
 for name in ['init','startConnection','stopConnection','interruptConnection']:
  assert 'Java_com_limelight_nvstream_jni_MoonBridge_'+name in symbols
 dynamic=subprocess.check_output(['readelf','-d',str(p)],text=True)
 assert 'libevdev' not in dynamic and 'libc++' not in dynamic
 for shared in ['libcrypto.so','libssl.so','libopus.so']:assert shared not in dynamic
 relocations=subprocess.check_output(['readelf','-rW',str(p)],text=True)
 for name in ['EVP_','CRYPTO_','OPENSSL_','opus_']:assert name not in relocations
 segments=subprocess.check_output(['readelf','-lW',str(p)],text=True)
 for line in segments.splitlines():
  if line.strip().startswith('LOAD '):assert int(line.split()[-1],16)>=(4096 if p.parent.name=='armeabi-v7a' else 16384)
assert 'STREAM_CFG_LOCAL' in (root/'src/main/java/com/mooncast/receiver/MoonlightHostClient.java').read_text()
assert 'System.loadLibrary("moonlight-core")' in (root/'src/main/java/com/limelight/nvstream/jni/MoonBridge.java').read_text()
print('PASS: three ABI hashes, JNI exports, 16KB 64-bit ELF load alignment, no root helper, and local-stream facade.')
