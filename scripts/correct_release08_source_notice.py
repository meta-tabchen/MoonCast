#!/usr/bin/env python3
"""Correct only the obsolete source-upload banner after re-verifying that old asset.
No old tags, assets, version fields, validation limits or publication status change.
"""
import hashlib,io,json,os,subprocess,urllib.request,zipfile
REPO='meta-tabchen/MoonCast';ID=415627796;ASSET=649770855;SHA='b0bc3512d600c33b604bb1585dbd232dbbe28c601acf66cfc2570d0887f7c4bb'
def require(ok,why):
 if not ok:raise ValueError(why)
def api(path,method='GET',data=None):
 args=['gh','api','--method',method,'repos/'+REPO+'/'+path]
 if data is not None:args+=['--input','-']
 result=subprocess.check_output(args,input=None if data is None else json.dumps(data),text=True);return json.loads(result)
require(os.environ.get('GITHUB_REPOSITORY')==REPO and os.environ.get('GITHUB_REF')=='refs/heads/main','authorized repository/main only')
old=api('releases/'+str(ID));require(old['tag_name']=='v0.8','historical release identity');body=old['body'].replace('\r\n','\n')
if body.startswith('> 完整对应源码 ZIP（364 MB）正在后台上传，尚未完成公共下载核验；'):
 lines=body.split('\n');require(len(lines)>3 and lines[1].startswith('> The 364 MB complete-source ZIP is still uploading and has not passed public-download verification.'),'exact obsolete bilingual banner')
 assets={row['name']:row for row in old['assets']};source=assets['MoonCast-0.8-source.zip'];require(source['id']==ASSET and source['digest']=='sha256:'+SHA,'original source asset identity')
 with urllib.request.urlopen(source['browser_download_url'],timeout=180) as response:data=response.read()
 require(len(data)==source['size'] and hashlib.sha256(data).hexdigest()==SHA,'historical source public bytes')
 with zipfile.ZipFile(io.BytesIO(data)) as z:require(z.testzip() is None,'historical source CRC')
 correction='> 完整对应源码 ZIP 已完成公开下载、ZIP CRC 与 SHA-256 核验，下载附件现已齐全；原有实机与测试限制保持不变。\n> The complete-source ZIP is publicly available and verified by download, ZIP CRC and SHA-256. Original hardware/test limitations remain unchanged.\n'
 updated=correction+'\n'.join(lines[2:]);latest=api('releases/'+str(ID));require(latest['body']==old['body'],'historical notes changed concurrently; refusing overwrite')
 api('releases/'+str(ID),'PATCH',{'body':updated});after=api('releases/'+str(ID))
 require(after['body'].replace('\r\n','\n')==updated,'corrected banner readback')
 require([(x['id'],x['name'],x.get('digest'),x['size']) for x in after['assets']]==[(x['id'],x['name'],x.get('digest'),x['size']) for x in old['assets']],'historical assets unchanged')
 print('Corrected only v0.8 obsolete source-upload banner after fresh public ZIP/hash verification; assets preserved')
else:print('Historical upload banner is absent; no change made')
