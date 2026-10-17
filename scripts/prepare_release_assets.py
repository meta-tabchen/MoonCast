#!/usr/bin/env python3
"""Verify complete build/source correspondence and make the explicit six release assets.
No credentials, signing material, publication or arbitrary workspace files are copied.
"""
import argparse,hashlib,io,json,os,pathlib,re,subprocess,urllib.request,zipfile
from elf_dependencies import elf_exports
from release_asset_policy import build_id,legal_paths
R=pathlib.Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--directory',type=pathlib.Path,required=True);p.add_argument('--commit',required=True);a=p.parse_args();d=a.directory
NATIVE_COMMIT='ab57723605c8e26fc0575f848f022121d0cbcc1e'
PREVIOUS={'arm64-v8a':'1f8f33aad642c4afea44ee0c02aad4edeb2b121f7f5fd8380980208f5801b4f1','armeabi-v7a':'bb6863d5e4d71fb9eeccdad903efb7ead77294c6474358a5e29a3274e44fd20a'}
def require(ok,why):
 if not ok:raise ValueError(why)
def sha(data):return hashlib.sha256(data).hexdigest()
def run(*cmd):return subprocess.check_output(list(map(str,cmd)),cwd=R,text=True)
def file_info(path):return {'bytes':path.stat().st_size,'sha256':sha(path.read_bytes())}
require(re.fullmatch('[0-9a-f]{40}',a.commit),'exact source commit')
require(run('git','rev-parse','HEAD').strip()==a.commit,'build HEAD matches source commit')
source=d/'MoonCast-0.9-source.zip';identities={};native={};notices=[]
with zipfile.ZipFile(source) as z:
 require(z.testzip() is None,'source CRC');names=z.namelist();require(len(names)==len(set(names)),'unique source entries')
 for name in names:
  parts=pathlib.PurePosixPath(name).parts
  require(parts and parts[0]=='MoonCast' and '..' not in parts and '\\' not in name and not name.startswith('/'),'safe source path')
  require(not name.endswith(('.p12','.pfx','.jks','.keystore')) or not name.startswith('MoonCast/signing/'),'app signing key excluded')
 m=json.loads(z.read('MoonCast/CORRESPONDING-SOURCE.json'));require(m['sourceCommit']==a.commit and m['version']=='0.9','exact final source identity')
 require(set(names)=={'MoonCast/'+x for x in m['files']}|{'MoonCast/CORRESPONDING-SOURCE.json'},'complete source inventory')
 for name,digest in m['files'].items():require(sha(z.read('MoonCast/'+name))==digest,'source hash '+name)
 def read(name):return z.read('MoonCast/'+name)
 receiver=json.loads(read('native/airplay/receiver-candidate-build-record.json'));sunshine=json.loads(read('native/sunshine-candidate-build-record.json'))
 require(len(receiver['libraries'])==12 and len(sunshine['libraries'])==9,'all rebuilt native ABI outputs')
 require(sunshine['sourceGitCommit']==NATIVE_COMMIT,'original Sunshine build provenance retained')
 for name,digest in sunshine['libraries'].items():require(sha(read('app/src/main/jniLibs/'+name))==digest,'Sunshine output mapping '+name)
 for name,digest in receiver['libraries'].items():require(sha(read('tv/src/main/jniLibs/'+name))==digest,'receiver output mapping '+name)
 isolated=json.loads(read('native/unified-receiver-manifest.json'))['libraries']
 require(len(isolated)==12,'all isolated receiver libraries')
 for build in ('app/build.gradle','phone-ui/build.gradle'):
  require(re.search(r"buildConfigField\s+['\"]boolean['\"]\s*,\s*['\"]PHYSICAL_SCREEN_OFF_VALIDATED['\"]\s*,\s*['\"]false['\"]",read(build).decode()) is not None,'physical gate remains disabled: '+build)
 for name,row in isolated.items():
  require(row['sourceSha256']==receiver['libraries'][row['source']],'receiver isolation source link '+name)
  require(sha(read('unified/native-receiver/'+name))==row['sha256'],'archived isolated receiver bytes '+name)
 moon=json.loads(read('moonlight-core/native-build-manifest.json'));webrtc=json.loads(read('native/webrtc/SOURCE_LOCK.json'))
 for abi,old_hash in PREVIOUS.items():
  apk=d/('MoonCast-0.9-'+abi+'.apk');expected={pathlib.PurePosixPath(n).name:sha(read(n)) for n in m['files'] if n.startswith('app/src/main/jniLibs/'+abi+'/') and n.endswith('.so')}
  expected.update({n.split('/')[1]:row['sha256'] for n,row in isolated.items() if n.startswith(abi+'/')})
  for row in moon['binaries']:
   if row['abi']==abi:expected['libmoonlight-core.so']=row['sha256']
  with zipfile.ZipFile(io.BytesIO(read('app/libs/airplaylib.aar'))) as aar:expected['libgojni.so']=sha(aar.read('jni/'+abi+'/libgojni.so'))
  expected['libjingle_peerconnection_so.so']=webrtc['libraries'][abi+'/libjingle_peerconnection_so.so']
  with zipfile.ZipFile(apk) as az:
   require(az.testzip() is None,'APK CRC');require(len(az.namelist())==len(set(az.namelist())),'unique APK entries');libs={n for n in az.namelist() if n.startswith('lib/') and n.endswith('.so')}
   require(len(expected)==11 and libs=={'lib/'+abi+'/'+n for n in expected},'exact 11-library ABI payload')
   for name,digest in expected.items():require(sha(az.read('lib/'+abi+'/'+name))==digest,'APK/source native mapping '+name)
   require('_ZN6stream7session4stopERNS_9session_tE' in elf_exports(az.read('lib/'+abi+'/libsunshine.so')),'session-stop source ABI export')
  old=pathlib.Path(os.environ.get('RUNNER_TEMP','/tmp'))/('verified-MoonCast-0.8-'+abi+'.apk')
  with urllib.request.urlopen('https://github.com/meta-tabchen/MoonCast/releases/download/v0.8/MoonCast-0.8-'+abi+'.apk',timeout=120) as response:old.write_bytes(response.read())
  require(sha(old.read_bytes())==old_hash,'actual historical APK hash')
  identities[abi]=json.loads(run('python3','scripts/verify_community_identity.py',apk,'--previous',old))
  tools=pathlib.Path(os.environ['ANDROID_HOME'])/'build-tools/35.0.0';manifest=run(tools/'aapt','dump','xmltree',apk,'AndroidManifest.xml')
  require(build_id(manifest)==a.commit,'APK embeds exact BUILD_ID metadata')
  require(identities[abi]['versionCode']==66,'exact version code')
  native[abi]=expected
 notices=legal_paths(m['files'],webrtc,json.loads(read('native/browser-qr/SOURCE_LOCK.json')))
 with zipfile.ZipFile(d/'MoonCast-0.9-notices.zip','x',zipfile.ZIP_DEFLATED) as nz:
  def notice_entry(name,data):
   info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.external_attr=0o100644<<16;info.compress_type=zipfile.ZIP_DEFLATED;nz.writestr(info,data)
  for name in sorted(notices):notice_entry('MoonCast/'+name,read(name))
  notice_entry('NOTICE-INVENTORY.json',json.dumps({n:m['files'][n] for n in sorted(notices)},indent=2)+'\n')
 with zipfile.ZipFile(d/'MoonCast-0.9-notices.zip') as nz:
  require(nz.testzip() is None,'notices CRC')
  require(set(nz.namelist())=={'MoonCast/'+name for name in notices}|{'NOTICE-INVENTORY.json'},'notices exact inventory')
  for name in notices:require(sha(nz.read('MoonCast/'+name))==m['files'][name],'notice readback '+name)
assets=['MoonCast-0.9-arm64-v8a.apk','MoonCast-0.9-armeabi-v7a.apk','MoonCast-0.9-source.zip','MoonCast-0.9-notices.zip']
provenance={'version':'0.9','versionCode':66,'applicationId':'com.mooncast.community','sourceCommit':a.commit,'sourceTree':run('git','rev-parse','HEAD^{tree}').strip(),'buildId':a.commit,'workflowRun':os.environ.get('GITHUB_RUN_ID'),'nativeBuildCommit':NATIVE_COMMIT,'nativeReuseRun':39035789360,'nativeReuseArtifact':12102154262,'nativeReuseArtifactSha256':'d3817623086ad1d419ce996cf25d1143a239e9f6eef79634a781b97e69ace1cd','nativeReusePolicy':'Every native source/archive/patch/recipe/output matched; original native build provenance retained','identities':identities,'nativeLibraries':native,'sourceInventoryEntries':len(m['files']),'noticeEntries':len(notices),'assets':{n:file_info(d/n) for n in assets},'checks':['both ABI release build and lint','APK signature and fixed community certificate','static update identity against hash-pinned actual 0.8 APKs','11 native payload bytes per ABI against full source inventory','source ZIP CRC, unique safe entries and every file hash','Sunshine session-stop ABI symbol','exact source build ID embedded'],'runtimeEvidence':'See docs/release-0.9-validation.md for individually scoped successes, failures and untested physical hardware; build checks do not establish interoperability.','signingPurpose':'Existing intentionally public community test key, not publisher authenticity','physicalScreenOffEnabled':False}
(d/'RELEASE-PROVENANCE.json').write_text(json.dumps(provenance,indent=2)+'\n');assets.append('RELEASE-PROVENANCE.json')
(d/'SHA256SUMS').write_text(''.join(sha((d/n).read_bytes())+'  '+n+'\n' for n in assets))
require({x.name for x in d.iterdir()}==set(assets)|{'SHA256SUMS'},'explicit release allowlist only')
print(json.dumps({'verifiedAssets':{n:file_info(d/n) for n in assets+['SHA256SUMS']},'sourceCommit':a.commit,'scope':'static artifact/source/identity verification'},indent=2))
