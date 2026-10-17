#!/usr/bin/env python3
"""Reuse exact CI-native outputs only when every recorded native input is unchanged.
This is artifact reuse with original provenance, never a claim of a new native build.
"""
import argparse,hashlib,json,pathlib,shutil,subprocess,sys,zipfile
p=argparse.ArgumentParser();p.add_argument('--bundle',type=pathlib.Path,required=True);p.add_argument('--verify-only',action='store_true');a=p.parse_args()
r=pathlib.Path(__file__).resolve().parents[1];b=a.bundle.resolve()
EXPECTED_SOURCE='aa447c143f4e7d452d880da6d117f78c1f2f85a866bb15ad24a85e23bdbaa733'
EXPECTED_COMMIT='ab57723605c8e26fc0575f848f022121d0cbcc1e'
def require(value,message):
 if not value:raise ValueError(message)
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
source=b/'receiver-candidate/MoonCast-0.9-source.zip';require(sha(source)==EXPECTED_SOURCE,'known source artifact checksum')
with zipfile.ZipFile(source) as z:
 require(z.testzip() is None,'source CRC')
 inventory=json.loads(z.read('MoonCast/CORRESPONDING-SOURCE.json'))
 require(inventory['sourceCommit']==EXPECTED_COMMIT,'source artifact commit')
 for path,digest in inventory['files'].items():require(hashlib.sha256(z.read('MoonCast/'+path)).hexdigest()==digest,'source inventory '+path)
 receiver=json.loads(z.read('MoonCast/native/airplay/receiver-candidate-build-record.json'))
 sunshine=json.loads(z.read('MoonCast/native/sunshine-candidate-build-record.json'))
 for kind,record in [('receiver',receiver),('sunshine',sunshine)]:
  require(json.loads((b/(kind+'-native/BUILD_RECORD.json')).read_text())==record,'artifact record agrees with verified source inventory '+kind)
  for path,digest in record['libraries'].items():require(sha(b/(kind+'-native/outputs')/path)==digest,'artifact native output '+kind+'/'+path)
 # A changed/added native source, patch, recipe or dependency rejects reuse.
 current_sources={str(f.relative_to(r)) for folder in ['sunshine','sessions'] for f in (r/'native'/folder).rglob('*') if f.is_file()}
 require(current_sources==set(sunshine['sources']),'Sunshine native input inventory changed; rebuild required')
 for path,digest in sunshine['sources'].items():require(sha(r/path)==digest,'Sunshine native source changed: '+path)
 for path,digest in sunshine['archives'].items():require(sha(r/'native/sender-build-inputs'/path)==digest,'Sunshine dependency changed: '+path)
 for recipe in ['scripts/build_sunshine_candidate.py','scripts/build_airplay_receiver_candidate.py']:
  require(sha(r/recipe)==inventory['files'][recipe],'native recipe changed: '+recipe)
 require(sunshine['sourceGitCommit']==EXPECTED_COMMIT,'original native build commit')
 for path,digest in receiver['sourceArchives'].items():require(sha(r/'native/airplay'/path)==digest,'receiver source archive changed: '+path)
 for folder,key in [('patches','mooncast'),('bridge-patches','bridge')]:
  current={key+'/'+f.name:sha(f) for f in (r/'native/airplay'/folder).glob('*.patch')}
  require(current=={path:digest for path,digest in receiver['patches'].items() if path.startswith(key+'/')},'receiver patches changed: '+folder)
 manifest_path=r/'native/prebuilt-manifest.json';manifest=json.loads(manifest_path.read_text())
 require(manifest['libraries']==sunshine['previousLibraries'],'Sunshine original manifest changed')
 for path,digest in manifest['libraries'].items():require(sha(r/'app/src/main/jniLibs'/path)==digest,'unexpected original Sunshine bytes '+path)
 if a.verify_only:
  print('PASS unchanged native sources/archives/patches/recipes, 21 outputs and full known source inventory; no files installed');sys.exit(0)
 # Existing installers retain the baseline manifest and original build record.
 subprocess.run([sys.executable,str(r/'scripts/install_receiver_candidate.py'),'--work',str(b/'receiver-native')],check=True)
 for path in sunshine['libraries']:shutil.copy2(b/'sunshine-native/outputs'/path,r/'app/src/main/jniLibs'/path)
 for path in ['native/prebuilt-manifest.json','native/sunshine-candidate-build-record.json']:
  (r/path).write_bytes(z.read('MoonCast/'+path))
print('Reused 21 exact native libraries from CI run39035789360 / '+EXPECTED_COMMIT+'; every native source/archive/patch/recipe unchanged; original provenance retained')
