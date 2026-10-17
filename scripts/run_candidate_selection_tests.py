#!/usr/bin/env python3
"""Local generated-fixture selection checks. No GitHub writes or real APK claims."""
import base64,contextlib,copy,io,json,os,pathlib,subprocess,tempfile,zipfile
from unittest.mock import patch
import select_reviewed_release_candidate as m
S='a'*40;T='b'*40;P='c'*40;B='d'*40;count=0
run={'id':11,'workflow_id':381662606,'path':m.WORKFLOW,'head_sha':S,'head_branch':m.BRANCH,'event':'push','repository':{'full_name':m.REPO},'head_repository':{'full_name':m.REPO},'status':'completed','conclusion':'success','run_attempt':1}
data={n:('generated '+n).encode() for n in m.NAMES-{'RELEASE-PROVENANCE.json','SHA256SUMS'}}
prov={'sourceCommit':S,'sourceTree':T,'buildId':S,'workflowRun':'11','version':'0.9','versionCode':66,'applicationId':'com.mooncast.community','physicalScreenOffEnabled':False,'identities':{abi:{'status':'PASS','certificateSha256':m.CERT,'applicationId':'com.mooncast.community','versionCode':66,'upgradeCompatible':True} for abi in ('arm64-v8a','armeabi-v7a')},'assets':{n:{'bytes':len(b),'sha256':m.sha(b)} for n,b in data.items()}}
data['RELEASE-PROVENANCE.json']=json.dumps(prov).encode();data['SHA256SUMS']=''.join(m.sha(b)+'  '+n+'\n' for n,b in sorted(data.items())).encode()
def archive(files=data,symlink=False):
 out=io.BytesIO()
 with zipfile.ZipFile(out,'w') as z:
  for n,b in files.items():
   row=zipfile.ZipInfo(n);row.external_attr=(0o120777 if symlink else 0o100644)<<16;z.writestr(row,b)
 return out.getvalue()
raw=archive();artifact={'id':22,'name':'community-release-candidates','expired':False,'size_in_bytes':len(raw),'digest':'sha256:'+m.sha(raw),'workflow_run':{'id':11,'head_sha':S,'head_branch':m.BRANCH}}
pin={'sourceCommit':S,'sourceTree':T,'candidateRun':11,'candidateRunAttempt':1,'candidateArtifact':22,'candidateArtifactSha256':m.sha(raw),'candidateArtifactBytes':len(raw),'verifiedAssets':{n:{'bytes':len(b),'sha256':m.sha(b)} for n,b in data.items()}}
def reject(call):
 global count
 try:call()
 except (ValueError,TypeError,KeyError):count+=1;return
 raise AssertionError('unsafe selection accepted')
assert m.select_run([run],S)==run;assert m.select_artifact([artifact],run)==artifact;assert m.verify_bundle(raw,artifact,S,T,11)==data;m.validate_receipt(pin,S,T);count+=4
for key,value in [('workflow_id',1),('path','other.yml'),('head_sha','f'*40),('head_branch','main'),('event','pull_request'),('status','in_progress'),('conclusion','failure'),('run_attempt',2),('head_repository',{'full_name':'other/repo'})]:
 bad=copy.deepcopy(run);bad[key]=value;reject(lambda:m.select_run([bad],S))
reject(lambda:m.select_run([run,run],S));reject(lambda:m.select_artifact([artifact,artifact],run))
for key,value in [('expired',True),('digest','sha256:bad'),('size_in_bytes',0),('workflow_run',{'id':12,'head_sha':S,'head_branch':m.BRANCH})]:
 bad=copy.deepcopy(artifact);bad[key]=value;reject(lambda:m.select_artifact([bad],run))
reject(lambda:m.verify_bundle(raw+b'x',artifact,S,T,11));reject(lambda:m.verify_bundle(raw,artifact,S,'e'*40,11))
for files,link in [(dict(data,extra=b'x'),False),(data,True),(dict(data,SHA256SUMS=data['SHA256SUMS']+data['SHA256SUMS'].splitlines()[0]+b'\n'),False)]:
 badraw=archive(files,link);bad=dict(artifact,digest='sha256:'+m.sha(badraw),size_in_bytes=len(badraw));reject(lambda:m.verify_bundle(badraw,bad,S,T,11))
for key,value in [('sourceCommit','f'*40),('candidateRunAttempt',True),('candidateArtifact',0),('candidateArtifactSha256','bad'),('verifiedAssets',{})]:
 bad=copy.deepcopy(pin);bad[key]=value;reject(lambda:m.validate_receipt(bad,S,T))
# Exercise the actual selection CLI orchestration with mocked GET-only GitHub data.
def scenario(kind):
 global count
 with tempfile.TemporaryDirectory() as root:
  dest=pathlib.Path(root)/'six';calls=[];refreads=0;artifactreads=0;receipt_bytes=json.dumps(pin).encode()
  def fetch(path):
   nonlocal refreads,artifactreads
   calls.append(path)
   if path=='git/ref/heads/main':return {'object':{'sha':S}}
   if path=='git/ref/heads/'+m.BRANCH:
    refreads+=1;return {'object':{'sha':'f'*40 if kind=='receipt-race' and refreads>1 else P}}
   if path=='git/commits/'+P:return {'parents':[{'sha':'f'*40 if kind=='wrong-parent' else S}],'tree':{'sha':'e'*40}}
   if path=='compare/'+S+'...'+P:return {'total_commits':1,'files':[{'filename':m.RECEIPT,'status':'added'}]+([{'filename':'app.java','status':'modified'}] if kind=='source-edit' else [])}
   if path.startswith('contents/'):return {'type':'file','encoding':'base64','size':len(receipt_bytes),'sha':B,'content':base64.b64encode(receipt_bytes).decode()}
   if path.startswith('git/trees/'):return {'truncated':False,'tree':[{'path':m.RECEIPT,'mode':'120000' if kind=='symlink-receipt' else '100644','type':'blob','sha':B}]}
   if path.startswith('actions/runs?'):return {'workflow_runs':[run]}
   if path.startswith('actions/runs/11/artifacts?'):
    artifactreads+=1;return {'artifacts':[dict(artifact,digest='sha256:'+'f'*64) if kind=='artifact-race' and artifactreads>1 else artifact]}
   if path=='actions/runs/11':return dict(run,run_attempt=2) if kind=='rerun-race' else run
   raise AssertionError(path)
  def command(args,**kw):
   if args==['git','rev-parse','HEAD^{tree}']:return T+'\n'
   if args==['git','rev-parse','HEAD']:return S+'\n'
   assert args==['gh','api','--method','GET','repos/'+m.REPO+'/actions/artifacts/22/zip'];return raw
  env={'GITHUB_REPOSITORY':m.REPO,'GITHUB_REF':'refs/heads/main','GITHUB_EVENT_NAME':'push','GITHUB_SHA':S}
  with patch.dict(os.environ,env),patch('sys.argv',['selector','--directory',str(dest),'--commit',S]),patch.object(m,'api',side_effect=fetch),patch('subprocess.check_output',side_effect=command),contextlib.redirect_stdout(io.StringIO()) as output:
   if kind=='success':m.main();assert {p.name:p.read_bytes() for p in dest.iterdir()}==data;assert json.loads(output.getvalue())['reviewReceiptCommit']==P;count+=1
   else:reject(m.main);assert not dest.exists(),'failure wrote selected bytes'
for kind in ('success','wrong-parent','source-edit','symlink-receipt','receipt-race','rerun-race','artifact-race'):scenario(kind)
print(f'PASS {count} generated candidate selection checks: pinned source/run/artifact/receipt, strict ZIP/checksums and race rejection; no real publication or APK validation')
