#!/usr/bin/env python3
"""Publish only exact already-verified 0.9 assets, without replacing history or credentials.
Runs with the job's existing GITHUB_TOKEN. Uncertain writes are reconciled on a rerun;
existing tags/assets are never moved, deleted or overwritten.
"""
import argparse,hashlib,json,os,pathlib,subprocess,tempfile,time,urllib.request
R=pathlib.Path(__file__).resolve().parents[1];REPO='meta-tabchen/MoonCast';TAG='v0.9'
p=argparse.ArgumentParser();p.add_argument('--directory',type=pathlib.Path,required=True);p.add_argument('--commit',required=True);a=p.parse_args();d=a.directory
NAMES={'MoonCast-0.9-arm64-v8a.apk','MoonCast-0.9-armeabi-v7a.apk','MoonCast-0.9-source.zip','MoonCast-0.9-notices.zip','RELEASE-PROVENANCE.json','SHA256SUMS'}
OLD_TAG='0db24011fe59abd145f7839d5a00bb6d503954e7'
OLD_ASSETS={'MoonCast-0.8-arm64-v8a.apk':(649762653,'1f8f33aad642c4afea44ee0c02aad4edeb2b121f7f5fd8380980208f5801b4f1'),'MoonCast-0.8-armeabi-v7a.apk':(649762465,'bb6863d5e4d71fb9eeccdad903efb7ead77294c6474358a5e29a3274e44fd20a'),'MoonCast-0.8-source.zip':(649770855,'b0bc3512d600c33b604bb1585dbd232dbbe28c601acf66cfc2570d0887f7c4bb'),'MoonCast-0.8-notices.zip':(649762388,'33444d419ff97315adc1518cd3cc743681cb31cdbc723bdb0defdaa89187b83e'),'RELEASE-PROVENANCE.json':(649762436,'799795b61b999e8542e3fa893393a0aeb23db5a2fea4e3c719ca8d246b29899f'),'SHA256SUMS':(649762307,'7c1d6cfa68a76807125565e0e5b43820323b8ae2136534cb67d3828d73cf3c47')}
def require(ok,why):
 if not ok:raise ValueError(why)
def sha(data):return hashlib.sha256(data).hexdigest()
def command(*args,**kw):return subprocess.run(list(map(str,args)),check=True,text=True,capture_output=True,**kw).stdout
def api(path,method='GET',data=None,optional=False):
 args=['gh','api','--method',method,'repos/'+REPO+'/'+path]
 if data is not None:args+=['--input','-']
 result=subprocess.run(args,input=None if data is None else json.dumps(data),text=True,capture_output=True)
 if result.returncode:
  if optional and '(HTTP 404)' in result.stderr:return None
  raise RuntimeError('GitHub API failed: '+method+' '+path+' '+result.stderr[-1000:])
 return json.loads(result.stdout) if result.stdout.strip() else None
def old_snapshot():
 require(api('git/ref/tags/v0.8')['object']['sha']==OLD_TAG,'historical tag unchanged')
 old=api('releases/tags/v0.8');got={x['name']:(x['id'],x.get('digest','').removeprefix('sha256:')) for x in old['assets']}
 require(got==OLD_ASSETS,'historical assets unchanged');return old
require(os.environ.get('GITHUB_REPOSITORY')==REPO and os.environ.get('GITHUB_REF')=='refs/heads/main' and os.environ.get('GITHUB_EVENT_NAME')=='push','bounded main push only')
require(a.commit==os.environ.get('GITHUB_SHA'),'exact workflow source')
require(set(x.name for x in d.iterdir())==NAMES,'explicit six-file allowlist')
prov=json.loads((d/'RELEASE-PROVENANCE.json').read_text());require(prov['sourceCommit']==a.commit and prov['version']=='0.9' and prov['versionCode']==66,'verified artifact source/version')
expected={n:sha((d/n).read_bytes()) for n in NAMES}
require(set(prov['assets'])==NAMES-{'RELEASE-PROVENANCE.json','SHA256SUMS'},'exact provenance asset inventory')
for name,row in prov['assets'].items():require(row['sha256']==expected[name] and row['bytes']==(d/name).stat().st_size,'provenance inventory')
checks={line.split('  ',1)[1]:line.split('  ',1)[0] for line in (d/'SHA256SUMS').read_text().splitlines()};require(checks=={n:h for n,h in expected.items() if n!='SHA256SUMS'},'complete checksums')
require(api('git/ref/heads/main')['object']['sha']==a.commit,'remote main did not advance')
old_snapshot();tag=api('git/ref/tags/'+TAG,optional=True)
if tag is None:api('git/refs','POST',{'ref':'refs/tags/'+TAG,'sha':a.commit})
require(api('git/ref/tags/'+TAG)['object']=={'sha':a.commit,'type':'commit','url':'https://api.github.com/repos/'+REPO+'/git/commits/'+a.commit},'new tag resolves to exact source')
notes=(R/'docs/release-0.9-notes.md').read_text();matches=[]
# The tag endpoint intentionally excludes drafts. List authenticated releases to
# reconcile an uncertain create/upload without creating a second draft.
for page in range(1,101):
 rows=api('releases?per_page=100&page='+str(page));matches.extend(row for row in rows if row['tag_name']==TAG)
 if len(rows)<100:break
else:raise ValueError('Release listing was incomplete; refusing uncertain creation')
require(len(matches)<=1,'multiple matching drafts/releases require investigation')
release=api('releases/'+str(matches[0]['id'])) if matches else None
if release is None:release=api('releases','POST',{'tag_name':TAG,'target_commitish':a.commit,'name':'MoonCast 0.9','body':notes,'draft':True,'prerelease':False,'make_latest':'false'})
require(release['tag_name']==TAG and release['name']=='MoonCast 0.9' and release['body']==notes and not release['prerelease'],'existing release belongs to exact operation')
assets=release['assets'];require(len(assets)==len({x['name'] for x in assets}) and {x['name'] for x in assets}<=NAMES,'no unexpected release assets')
for row in assets:require(row.get('digest')=='sha256:'+expected[row['name']] and row['size']==(d/row['name']).stat().st_size,'existing asset matches; never overwrite')
missing=NAMES-{x['name'] for x in assets}
require(not missing or release['draft'],'published release may not be partially replaced')
for name in sorted(missing):command('gh','release','upload',TAG,d/name,'--repo',REPO)
with tempfile.TemporaryDirectory(prefix='mooncast-draft-check-') as temp:
 command('gh','release','download',TAG,'--repo',REPO,'--dir',temp)
 require({x.name for x in pathlib.Path(temp).iterdir()}==NAMES,'downloaded draft allowlist')
 for name in NAMES:require(sha((pathlib.Path(temp)/name).read_bytes())==expected[name],'draft download bytes '+name)
require(api('git/ref/heads/main')['object']['sha']==a.commit,'remote main stable before publication')
require(api('git/ref/tags/'+TAG)['object']['sha']==a.commit,'tag stable before publication');old_snapshot()
if release['draft']:api('releases/'+str(release['id']),'PATCH',{'draft':False,'prerelease':False,'make_latest':'true'})
public=api('releases/tags/'+TAG);require(not public['draft'] and not public['prerelease'],'public release state')
require(api('releases/latest')['id']==public['id'],'Latest classification')
public_proof={}
for row in public['assets']:
 name=row['name'];require(name in NAMES,'public asset allowlist');url=row['browser_download_url'];require(url=='https://github.com/'+REPO+'/releases/download/'+TAG+'/'+name,'verified public asset URL')
 data=None
 for attempt in range(6):
  try:
   with urllib.request.urlopen(url,timeout=180) as response:data=response.read()
   if len(data)==(d/name).stat().st_size and sha(data)==expected[name]:break
  except Exception:
   if attempt==5:raise
  time.sleep(5*(attempt+1))
 require(data is not None and sha(data)==expected[name] and len(data)==(d/name).stat().st_size,'unauthenticated public download '+name)
 public_proof[name]={'url':url,'sha256':expected[name],'bytes':len(data)}
require(set(public_proof)==NAMES,'all public downloads verified');require(api('git/ref/tags/'+TAG)['object']['sha']==a.commit,'tag stable after public verification');old_snapshot()
print(json.dumps({'release':public['html_url'],'tagCommit':a.commit,'latest':True,'publicDownloads':public_proof,'oldReleaseAssetsAndTagUnchanged':True},indent=2))
