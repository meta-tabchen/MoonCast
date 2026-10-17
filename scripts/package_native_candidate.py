#!/usr/bin/env python3
"""Candidate-only complete source: exact committed baseline + verified generated runtime overrides.
Never sweeps the workspace or alters baseline release manifests/assets. No signing keys.
"""
import argparse,hashlib,json,zipfile,subprocess,os
from pathlib import Path
from restore_release_inputs import safe_target
p=argparse.ArgumentParser();p.add_argument('--base',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--expected-commit');a=p.parse_args();r=Path(__file__).resolve().parents[1]
def sha(data):return hashlib.sha256(data).hexdigest()
def require(value,message):
 if not value:raise ValueError(message)
extra={}
def add(name,expected=None):
 path=safe_target(r,name);require(path.is_file() and not path.is_symlink(),'missing regular generated input '+name);data=path.read_bytes()
 require(expected is None or sha(data)==expected,'generated input checksum '+name);extra[name]=data
receiver=json.loads((r/'native/airplay/prebuilt-manifest.json').read_text())
require(len(receiver['libraries'])==12,'receiver ABI inventory')
for name,digest in receiver['libraries'].items():add('tv/src/main/jniLibs/'+name,digest)
for name,row in json.loads((r/'native/unified-receiver-manifest.json').read_text())['libraries'].items():
 require(row['sourceSha256']==receiver['libraries'][row['source']],'isolated receiver mapping');add('unified/native-receiver/'+name,row['sha256'])
for name in ['native/airplay/prebuilt-manifest.json','native/unified-receiver-manifest.json','native/airplay/receiver-candidate-build-record.json']:add(name)
# Optional independently rebuilt Sunshine candidate. Its exact inputs/outputs are recorded too.
sunshine=r/'native/sunshine-candidate-build-record.json'
require(sunshine.is_file(),'required rebuilt Sunshine provenance')
if sunshine.is_file():
 record=json.loads(sunshine.read_text());require(len(record['libraries'])==9,'Sunshine ABI inventory')
 for name,digest in record['libraries'].items():add('app/src/main/jniLibs/'+name,digest)
 add('native/prebuilt-manifest.json');add('native/sunshine-candidate-build-record.json')
with zipfile.ZipFile(a.base) as base:
 require(base.testzip() is None,'baseline CRC');original=json.loads(base.read('MoonCast/CORRESPONDING-SOURCE.json'))
 expected=a.expected_commit or os.environ.get('GITHUB_SHA') or subprocess.check_output(['git','rev-parse','HEAD'],cwd=r,text=True).strip()
 require(original['sourceCommit']==expected,'base source commit must match exact current candidate')
 for name,digest in original['files'].items():require(sha(base.read('MoonCast/'+name))==digest,'baseline inventory '+name)
 # Old bootstrap archive still supplies all unchanged dependencies. New native outputs
 # are already included here, and must never be overwritten with its older payload.
 bootstrap=json.loads(base.read('MoonCast/scripts/release-inputs.json'));count=0
 for shard in bootstrap['shards']:
  name=shard['path'];old=base.read('MoonCast/'+name);require(sha(old)==shard['sha256'],'bootstrap shard')
  rows=[row for row in json.loads(old) if row[0] not in extra];count+=len(rows)
  if len(rows)!=shard['entries']:
   data=(json.dumps(rows,indent=2)+'\n').encode();extra[name]=data;shard.update(sha256=sha(data),bytes=len(data),entries=len(rows))
 bootstrap['inputCount']=count;extra['scripts/release-inputs.json']=(json.dumps(bootstrap,indent=2)+'\n').encode()
 names=set(original['files'])|set(extra);inventory={}
 require(not a.output.exists(),'candidate source output exists')
 a.output.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(a.output,'x',zipfile.ZIP_DEFLATED,compresslevel=6) as out:
  for name in sorted(names):
   data=extra[name] if name in extra else base.read('MoonCast/'+name);inventory[name]=sha(data)
   info=zipfile.ZipInfo('MoonCast/'+name,(1980,1,1,0,0,0));info.external_attr=base.getinfo('MoonCast/'+name).external_attr if name in original['files'] else 0o100644<<16;info.compress_type=zipfile.ZIP_DEFLATED;out.writestr(info,data)
  original.update(version='0.9',files=inventory,candidateGeneratedOverrides={name:sha(data) for name,data in extra.items()},baseSourceSha256=sha(a.base.read_bytes()),scope='Exact committed application source plus hash-verified CI native outputs; original native build provenance retained; publication status is recorded by the associated Release')
  info=zipfile.ZipInfo('MoonCast/CORRESPONDING-SOURCE.json',(1980,1,1,0,0,0));info.external_attr=0o100644<<16;info.compress_type=zipfile.ZIP_DEFLATED
  out.writestr(info,json.dumps(original,indent=2)+'\n')
with zipfile.ZipFile(a.output) as archive:
 require(archive.testzip() is None,'candidate CRC')
 for name,digest in inventory.items():require(sha(archive.read('MoonCast/'+name))==digest,'candidate inventory '+name)
print(json.dumps({'sourceCommit':original['sourceCommit'],'generatedOverrides':len(extra),'files':len(inventory),'sha256':sha(a.output.read_bytes()),'bytes':a.output.stat().st_size},indent=2))
