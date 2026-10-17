#!/usr/bin/env python3
"""Install a verified recipe's rebuilt receiver outputs and record their provenance.
No signing material, sender binaries or source archives are changed.
"""
from pathlib import Path
import argparse,hashlib,json,shutil
p=argparse.ArgumentParser();p.add_argument('--work',type=Path,required=True);a=p.parse_args()
r=Path(__file__).resolve().parents[1];w=a.work.resolve();record=json.loads((w/'BUILD_RECORD.json').read_text())
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
manifest_path=r/'native/airplay/prebuilt-manifest.json';manifest=json.loads(manifest_path.read_text())
assert record['toolVersions']['ndk']=='27.0.12077973'
assert record['toolVersions']['cmake'].startswith('cmake version 3.22.1')
assert record['sourceArchives']==manifest['sources']
assert len(record['libraries'])==12 and set(record['libraries'])==set(manifest['libraries'])
for name,digest in record['libraries'].items():
 source=w/'outputs'/name;target=r/'tv/src/main/jniLibs'/name
 assert sha(source)==digest,(name,'candidate checksum')
 assert sha(target)==manifest['libraries'][name],(name,'unexpected original')
for name in record['libraries']:
 shutil.copy2(w/'outputs'/name,r/'tv/src/main/jniLibs'/name)
record_path=r/'native/airplay/receiver-candidate-build-record.json';record_path.write_text(json.dumps(record,indent=2)+'\n')
manifest['libraries']=record['libraries']
manifest['localBuild']={'recipe':'scripts/build_airplay_receiver_candidate.py','recipeSha256':sha(r/'scripts/build_airplay_receiver_candidate.py'),'record':str(record_path.relative_to(r)),'recordSha256':sha(record_path),'patches':{str(path.relative_to(r)):sha(path) for folder in ['patches','bridge-patches'] for path in sorted((r/'native/airplay'/folder).glob('*.patch'))}}
manifest_path.write_text(json.dumps(manifest,indent=2)+'\n')
print('Installed all 12 rebuilt receiver inputs with archive, recipe, patch and toolchain provenance')
