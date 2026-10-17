#!/usr/bin/env python3
"""Read-only GitHub selection of the exact candidate bytes before any publication.
A main rebuild remains an independent check, but its newly generated provenance
must not silently replace the previously inspected candidate artifact.
"""
import argparse, base64, hashlib, io, json, os, pathlib, re, stat, subprocess, zipfile
REPO='meta-tabchen/MoonCast';BRANCH='codex/mooncast-0.9';WORKFLOW='.github/workflows/unified.yml'
NAMES={'MoonCast-0.9-arm64-v8a.apk','MoonCast-0.9-armeabi-v7a.apk','MoonCast-0.9-source.zip','MoonCast-0.9-notices.zip','RELEASE-PROVENANCE.json','SHA256SUMS'}
RECEIPT='release/reviewed-0.9.json'
CERT='290e4372fff33c00848758b12275936900b2eccad053dc63c18622e1a1222bb2'
def require(ok,why):
 if not ok:raise ValueError(why)
def sha(data):return hashlib.sha256(data).hexdigest()
def select_run(rows,commit):
 matches=[r for r in rows if r.get('workflow_id')==381662606 and r.get('path')==WORKFLOW and r.get('head_sha')==commit and r.get('head_branch')==BRANCH and r.get('event')=='push' and r.get('repository',{}).get('full_name')==REPO and r.get('head_repository',{}).get('full_name')==REPO]
 require(len(matches)==1,'exactly one source-matched candidate run required')
 run=matches[0];require(run.get('status')=='completed' and run.get('conclusion')=='success' and run.get('run_attempt')==1,'candidate must have one unambiguous successful attempt');return run

def select_artifact(rows,run):
 matches=[a for a in rows if a.get('name')=='community-release-candidates'];require(len(matches)==1,'one complete candidate artifact required');a=matches[0];w=a.get('workflow_run',{})
 require(not a.get('expired',True) and w.get('id')==run['id'] and w.get('head_sha')==run['head_sha'] and w.get('head_branch')==BRANCH,'artifact source/run binding')
 require(re.fullmatch('sha256:[0-9a-f]{64}',a.get('digest','')) is not None and 0<a.get('size_in_bytes',0)<700*1024*1024,'bounded artifact digest/size');return a

def verify_bundle(raw,artifact,commit,tree,run_id):
 require(len(raw)==artifact['size_in_bytes'] and 'sha256:'+sha(raw)==artifact['digest'],'exact immutable candidate ZIP digest')
 with zipfile.ZipFile(io.BytesIO(raw)) as z:
  require(len(z.infolist())==len(NAMES) and set(z.namelist())==NAMES,'strict six-file candidate ZIP')
  for row in z.infolist():require(0<row.file_size<600*1024*1024 and stat.S_IFMT(row.external_attr>>16) in (0,stat.S_IFREG) and not row.is_dir(),'bounded regular candidate entry')
  require(sum(r.file_size for r in z.infolist())<800*1024*1024 and z.testzip() is None,'candidate CRC and aggregate size')
  data={n:z.read(n) for n in NAMES}
 p=json.loads(data['RELEASE-PROVENANCE.json']);require(p.get('sourceCommit')==commit and p.get('sourceTree')==tree and p.get('buildId')==commit and str(p.get('workflowRun'))==str(run_id),'candidate exact source/build/run')
 require(p.get('version')=='0.9' and p.get('versionCode')==66 and p.get('applicationId')=='com.mooncast.community' and p.get('physicalScreenOffEnabled') is False,'candidate release scope')
 require(set(p.get('identities',{}))=={'arm64-v8a','armeabi-v7a'},'both candidate identities')
 for row in p['identities'].values():require(row.get('status')=='PASS' and row.get('certificateSha256')==CERT and row.get('applicationId')=='com.mooncast.community' and row.get('versionCode')==66 and row.get('upgradeCompatible') is True,'verified fixed candidate identity')
 require(set(p.get('assets',{}))==NAMES-{'RELEASE-PROVENANCE.json','SHA256SUMS'},'candidate provenance inventory')
 for n,row in p['assets'].items():require(row=={'bytes':len(data[n]),'sha256':sha(data[n])},'candidate asset digest '+n)
 rows=[line.split('  ',1) for line in data['SHA256SUMS'].decode().splitlines()];require(all(len(r)==2 for r in rows) and len(rows)==5,'five unique checksum entries')
 sums={n:h for h,n in rows};require(len(sums)==5 and sums=={n:sha(b) for n,b in data.items() if n!='SHA256SUMS'},'complete candidate checksums');return data

def validate_receipt(pin,commit,tree):
 require(set(pin)=={'sourceCommit','sourceTree','candidateRun','candidateRunAttempt','candidateArtifact','candidateArtifactSha256','candidateArtifactBytes','verifiedAssets'},'exact review receipt schema')
 require(pin['sourceCommit']==commit and pin['sourceTree']==tree and type(pin['candidateRunAttempt']) is int and pin['candidateRunAttempt']==1,'reviewed source/tree/attempt')
 for key in ('candidateRun','candidateArtifact','candidateArtifactBytes'):require(type(pin[key]) is int and pin[key]>0,'positive receipt '+key)
 require(re.fullmatch('[0-9a-f]{64}',pin['candidateArtifactSha256']) is not None,'receipt ZIP digest')
 require(set(pin['verifiedAssets'])==NAMES,'receipt six assets')
 for row in pin['verifiedAssets'].values():require(set(row)=={'sha256','bytes'} and type(row['bytes']) is int and row['bytes']>0 and re.fullmatch('[0-9a-f]{64}',row['sha256']) is not None,'receipt asset digest/size')

def api(path):return json.loads(subprocess.check_output(['gh','api','--method','GET','repos/'+REPO+'/'+path],text=True))
def pages(path,key):
 rows=[]
 for page in range(1,101):
  batch=api(path+('&' if '?' in path else '?')+'per_page=100&page='+str(page))[key];rows.extend(batch)
  if len(batch)<100:return rows
 raise ValueError('Incomplete paginated candidate listing')
def main():
 p=argparse.ArgumentParser();p.add_argument('--directory',type=pathlib.Path,required=True);p.add_argument('--commit',required=True);a=p.parse_args()
 require(os.environ.get('GITHUB_REPOSITORY')==REPO and os.environ.get('GITHUB_REF')=='refs/heads/main' and os.environ.get('GITHUB_EVENT_NAME')=='push' and os.environ.get('GITHUB_SHA')==a.commit,'authorized exact main push only')
 require(re.fullmatch('[0-9a-f]{40}',a.commit) is not None and api('git/ref/heads/main')['object']['sha']==a.commit,'stable main source')
 tree=subprocess.check_output(['git','rev-parse','HEAD^{tree}'],text=True).strip();require(subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip()==a.commit,'exact checkout')
 # The receipt is written only AFTER this exact source was built and audited.
 # Its one-file child commit is not promoted to main or represented as app source.
 receipt_commit=api('git/ref/heads/'+BRANCH)['object']['sha'];receipt_git=api('git/commits/'+receipt_commit)
 require([p['sha'] for p in receipt_git['parents']]==[a.commit],'receipt must be the direct child of the reviewed source')
 comparison=api('compare/'+a.commit+'...'+receipt_commit)
 require(comparison['total_commits']==1 and [(f['filename'],f['status']) for f in comparison['files']]==[(RECEIPT,'added')],'receipt child may only add the audit pin')
 receipt=api('contents/'+RECEIPT+'?ref='+receipt_commit);require(receipt['type']=='file' and receipt['encoding']=='base64' and 0<receipt['size']<16384,'bounded review receipt')
 entries=api('git/trees/'+receipt_git['tree']['sha']+'?recursive=1');require(not entries.get('truncated',True),'complete receipt tree')
 receipt_rows=[r for r in entries['tree'] if r['path']==RECEIPT];require(len(receipt_rows)==1 and receipt_rows[0]['mode']=='100644' and receipt_rows[0]['type']=='blob' and receipt_rows[0]['sha']==receipt['sha'],'regular receipt blob')
 pin=json.loads(base64.b64decode(receipt['content'],validate=False));validate_receipt(pin,a.commit,tree)
 run=select_run(pages('actions/runs?branch='+BRANCH+'&event=push&head_sha='+a.commit,'workflow_runs'),a.commit)
 require(run['id']==pin['candidateRun'] and run['run_attempt']==pin['candidateRunAttempt'],'audited run tuple')
 artifact=select_artifact(pages('actions/runs/'+str(run['id'])+'/artifacts','artifacts'),run)
 require(artifact['id']==pin['candidateArtifact'] and artifact['digest']=='sha256:'+pin['candidateArtifactSha256'] and artifact['size_in_bytes']==pin['candidateArtifactBytes'],'audited artifact tuple')
 raw=subprocess.check_output(['gh','api','--method','GET','repos/'+REPO+'/actions/artifacts/'+str(artifact['id'])+'/zip'])
 data=verify_bundle(raw,artifact,a.commit,tree,run['id'])
 require({n:{'sha256':sha(b),'bytes':len(b)} for n,b in data.items()}==pin['verifiedAssets'],'exact independently inspected six asset bytes')
 require(select_run([api('actions/runs/'+str(run['id']))],a.commit)['run_attempt']==pin['candidateRunAttempt'],'candidate run changed during selection')
 require(select_artifact(pages('actions/runs/'+str(run['id'])+'/artifacts','artifacts'),run)==artifact,'candidate artifact changed during selection')
 require(api('git/ref/heads/'+BRANCH)['object']['sha']==receipt_commit,'review receipt changed during selection')
 require(api('git/ref/heads/main')['object']['sha']==a.commit,'main stable after download')
 a.directory.mkdir(parents=True,exist_ok=False)
 for name,content in data.items():(a.directory/name).write_bytes(content)
 print(json.dumps({'reviewReceiptCommit':receipt_commit,'reviewReceiptBlob':receipt['sha'],'candidateRun':run['id'],'candidateRunAttempt':run['run_attempt'],'candidateArtifact':artifact['id'],'candidateArtifactSha256':sha(raw),'sourceCommit':a.commit,'sourceTree':tree,'assets':{n:{'sha256':sha(b),'bytes':len(b)} for n,b in sorted(data.items())}},indent=2))
if __name__=='__main__':main()
