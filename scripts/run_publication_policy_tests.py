#!/usr/bin/env python3
"""Mocked publication state-machine regressions. No network or release mutation."""
import ast,contextlib,copy,hashlib,io,json,os,pathlib,runpy,subprocess,sys,tempfile,urllib.request
from unittest.mock import patch
R=pathlib.Path(__file__).resolve().parents[1];script=R/'scripts/publish_community_release.py';constants={}
for node in ast.parse(script.read_text()).body:
 if isinstance(node,ast.Assign):
  for target in node.targets:
   if isinstance(target,ast.Name) and target.id in ('NAMES','OLD_TAG','OLD_ASSETS'):constants[target.id]=ast.literal_eval(node.value)
NAMES=constants['NAMES'];S='a'*40;notes=(R/'docs/release-0.9-notes.md').read_text()
def sha(data):return hashlib.sha256(data).hexdigest()
def scenario(kind):
 with tempfile.TemporaryDirectory() as temp:
  d=pathlib.Path(temp)/'assets';d.mkdir();files={n:('generated fixture '+n).encode() for n in NAMES-{'RELEASE-PROVENANCE.json','SHA256SUMS'}}
  prov={'sourceCommit':S,'version':'0.9','versionCode':66,'assets':{n:{'sha256':sha(b),'bytes':len(b)} for n,b in files.items()}}
  files['RELEASE-PROVENANCE.json']=(json.dumps(prov)+'\n').encode();files['SHA256SUMS']=''.join(sha(b)+'  '+n+'\n' for n,b in sorted(files.items())).encode()
  for n,b in files.items():(d/n).write_bytes(b)
  proof=pathlib.Path(temp)/'selection.json';proof.write_text(json.dumps({'sourceCommit':S,'reviewReceiptCommit':'c'*40,'assets':{n:{'sha256':sha(b),'bytes':len(b)} for n,b in files.items()}}))
  assets=lambda names:[{'name':n,'id':100+i,'digest':'sha256:'+sha(files[n]),'size':len(files[n]),'browser_download_url':'https://github.com/meta-tabchen/MoonCast/releases/download/v0.9/'+n} for i,n in enumerate(sorted(names))]
  release={'id':99,'tag_name':'v0.9','name':'MoonCast 0.9','body':notes,'draft':True,'prerelease':False,'assets':[],'html_url':'https://github.com/meta-tabchen/MoonCast/releases/tag/v0.9'}
  state={'tag':kind in ('resume','published','tag-conflict','multiple-drafts','body-conflict','digest-conflict'),'release':copy.deepcopy(release) if kind in ('resume','published','multiple-drafts','body-conflict','digest-conflict') else None,'writes':[],'uploads':[],'tagReads':0}
  if kind=='resume':state['release']['assets']=assets(sorted(NAMES)[:2])
  if kind=='published':state['release']['draft']=False;state['release']['assets']=assets(NAMES)
  if kind=='body-conflict':state['release']['body']='someone else edited these notes'
  if kind=='digest-conflict':state['release']['assets']=assets([sorted(NAMES)[0]]);state['release']['assets'][0]['digest']='sha256:'+'0'*64
  old={'tag_name':'v0.8','id':88,'assets':[{'name':n,'id':row[0],'digest':'sha256:'+row[1]} for n,row in constants['OLD_ASSETS'].items()]}
  if kind=='old-mutated':old['assets'][0]['id']+=1
  def proc(args,input=None,**kw):
   result=None;rc=0;err=''
   if args[:2]==['gh','api']:
    method=args[args.index('--method')+1];path=args[args.index('--method')+2].removeprefix('repos/meta-tabchen/MoonCast/');body=json.loads(input) if input else None
    if method!='GET':state['writes'].append((method,path))
    if path=='git/ref/heads/main':result={'object':{'sha':'b'*40 if kind=='main-advanced' else S}}
    elif path=='git/ref/heads/codex/mooncast-0.9':result={'object':{'sha':'d'*40 if kind=='receipt-advanced' else 'c'*40}}
    elif path=='git/ref/tags/v0.8':result={'object':{'sha':constants['OLD_TAG']}}
    elif path=='releases/tags/v0.8':result=old
    elif path=='git/ref/tags/v0.9':
     state['tagReads']+=1
     if not state['tag']:rc=1;err='gh: Not Found (HTTP 404)'
     else:result={'object':{'sha':'b'*40 if kind=='tag-conflict' or (kind=='tag-raced' and state['tagReads']>=3) else S,'type':'commit','url':'https://api.github.com/repos/meta-tabchen/MoonCast/git/commits/'+S}}
    elif path=='git/refs':state['tag']=True;result={}
    elif path.startswith('releases?'):
     result=[] if state['release'] is None else [state['release']]
     if kind=='multiple-drafts':result+=copy.deepcopy(result)
    elif path=='releases' and method=='POST':state['release']=copy.deepcopy(release);result=state['release']
    elif path=='releases/99' and method=='GET':result=state['release']
    elif path=='releases/99' and method=='PATCH':state['release']['draft']=False;result=state['release']
    elif path in ('releases/tags/v0.9','releases/latest'):result=state['release']
    else:raise AssertionError('unexpected API '+method+' '+path)
   elif args[:3]==['gh','release','upload']:
    n=pathlib.Path(args[4]).name
    if kind=='upload-failed':rc=1;err='simulated upload failure'
    else:state['uploads'].append(n);state['release']['assets']+=assets([n])
   elif args[:3]==['gh','release','download']:
    out=pathlib.Path(args[args.index('--dir')+1])
    for row in state['release']['assets']:(out/row['name']).write_bytes(files[row['name']])
   else:raise AssertionError('unexpected command '+str(args))
   if rc and kw.get('check'):raise subprocess.CalledProcessError(rc,args,stderr=err)
   return subprocess.CompletedProcess(args,rc,stdout=json.dumps(result) if result is not None else '',stderr=err)
  def download(url,**kw):
   n=url.rsplit('/',1)[1];return io.BytesIO(b'corrupt' if kind=='public-corrupt' else files[n])
  failure=None
  with patch.object(sys,'argv',[str(script),'--directory',str(d),'--commit',S,'--selection-proof',str(proof)]),patch.dict(os.environ,{'GITHUB_REPOSITORY':'meta-tabchen/MoonCast','GITHUB_REF':'refs/heads/main','GITHUB_EVENT_NAME':'push','GITHUB_SHA':S}),patch('subprocess.run',side_effect=proc),patch('urllib.request.urlopen',side_effect=download),patch('time.sleep'),contextlib.redirect_stdout(io.StringIO()):
   try:runpy.run_path(str(script),run_name='__main__')
   except Exception as error:failure=error
  if kind in ('fresh','resume','published'):
   assert failure is None,(kind,failure);assert state['release']['draft'] is False
   assert len(state['uploads'])==(6 if kind=='fresh' else 4 if kind=='resume' else 0)
   assert sum(path=='releases' for _,path in state['writes'])==(1 if kind=='fresh' else 0)
  else:assert failure is not None,kind+' falsely passed'
  if kind in ('main-advanced','old-mutated','tag-conflict','body-conflict','digest-conflict','published','receipt-advanced'):assert not state['writes'],state
  if kind=='tag-raced':assert ('PATCH','releases/99') not in state['writes']
for case in ('fresh','resume','main-advanced','old-mutated','tag-conflict','multiple-drafts','tag-raced','upload-failed','public-corrupt','published','body-conflict','digest-conflict','receipt-advanced'):scenario(case)
print('PASS 13 mocked publication cases: exact fresh publish, partial draft reconciliation, concurrent refs/history, upload/public corruption fail closed; no real release mutation')
