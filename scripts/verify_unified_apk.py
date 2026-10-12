#!/usr/bin/env python3
"""Verify every unified native payload byte, JNI graph and standalone APK identity."""
from pathlib import Path
import argparse,hashlib,json,os,re,subprocess,tempfile,zipfile
R=Path(__file__).resolve().parents[1]
def sha(b):return hashlib.sha256(b).hexdigest()
p=argparse.ArgumentParser();p.add_argument('apk',type=Path);p.add_argument('--abi',required=True,choices=['arm64-v8a','armeabi-v7a','x86_64']);p.add_argument('--debug',action='store_true');a=p.parse_args()
expected={}
for path in (R/'app/src/main/jniLibs'/a.abi).glob('*.so'):expected[path.name]=sha(path.read_bytes())
receiver=json.loads((R/'native/unified-receiver-manifest.json').read_text())
for name,row in receiver['libraries'].items():
 if name.startswith(a.abi+'/'):
  filename=name.split('/')[1];assert filename not in expected,'duplicate native name';expected[filename]=row['sha256']
moon=json.loads((R/'moonlight-core/native-build-manifest.json').read_text())
for row in moon['binaries']:
 if row['abi']==a.abi:expected['libmoonlight-core.so']=row['sha256']
with zipfile.ZipFile(R/'app/libs/airplaylib.aar') as z:expected['libgojni.so']=sha(z.read('jni/'+a.abi+'/libgojni.so'))
with zipfile.ZipFile(a.apk) as z,tempfile.TemporaryDirectory() as d:
 names=[n for n in z.namelist() if n.startswith('lib/') and n.endswith('.so')]
 assert len(names)==len(expected)==10,(len(names),len(expected))
 assert set(names)=={'lib/'+a.abi+'/'+n for n in expected}
 for name,h in expected.items():
  data=z.read('lib/'+a.abi+'/'+name);assert sha(data)==h,name
  f=Path(d)/name;f.write_bytes(data)
  dyn=subprocess.check_output(['readelf','-d',f],text=True)
  for dep in re.findall(r'NEEDED.*\[(.*?)\]',dyn):
   assert dep in expected or dep in {'libandroid.so','libmediandk.so','liblog.so','libm.so','libdl.so','libc.so','libz.so','libOpenSLES.so'},(name,dep)
  if name=='libairplay_native.so':assert '[libtvcryp.so]' in dyn and '[libcrypto.so]' not in dyn
  if name=='libtvcryp.so':assert 'SONAME' in dyn and '[libtvcryp.so]' in dyn
 dex=b''.join(z.read(n) for n in z.namelist() if re.fullmatch(r'classes\d*\.dex',n))
 for cls in ['com/mooncast/host/MainActivity','com/mooncast/tv/TvActivity','com/mooncast/unified/RoleActivity','com/mooncast/transfer/ApkTransfer']:
  assert ('L'+cls+';').encode() in dex,cls
 if not a.debug:assert b'Lcom/mooncast/unified/SenderNativeProbe;' not in dex,'Debug probe leaked'
 sdk=Path(os.environ['ANDROID_HOME']);aapt=sdk/'build-tools/35.0.0/aapt'
 badge=subprocess.check_output([aapt,'dump','badging',a.apk],text=True)
 assert "package: name='com.mooncast.unified.preview' versionCode='50' versionName='0.5.0-unified-preview'" in badge
 assert "sdkVersion:'26'" in badge and "targetSdkVersion:'35'" in badge
 assert "application-label:'MoonCast'" in badge and 'leanback-launchable-activity:' in badge
 if not a.debug:assert 'application-debuggable' not in badge
print(json.dumps({'status':'PASS static native byte/ELF/dependency/APK identity checks','apk':a.apk.name,'abi':a.abi,'sha256':sha(a.apk.read_bytes()),'nativeLibraries':len(expected),'notEstablished':'Physical protocol interoperability or real capture/playback'},indent=2))
