"""Verify mandatory BC types, reporting the guarded optional OkHttp JSSE integration separately."""
import argparse, json, re, struct, zipfile
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__);p.add_argument('apk',type=Path);p.add_argument('--expect-missing',action='store_true');a=p.parse_args()
defined=set();referenced=set()
with zipfile.ZipFile(a.apk) as z:
 for name in z.namelist():
  if not re.fullmatch(r'classes\d*\.dex',name):continue
  data=z.read(name);count,offset=struct.unpack_from('<II',data,56);strings=[]
  for i in range(count):
   pos=struct.unpack_from('<I',data,offset+4*i)[0]
   while data[pos]&128:pos+=1
   pos+=1;end=data.index(0,pos);strings.append(data[pos:end].decode('utf-8',errors='replace'))
  count,offset=struct.unpack_from('<II',data,64)
  types=[strings[struct.unpack_from('<I',data,offset+4*i)[0]] for i in range(count)]
  referenced.update(x.lstrip('[') for x in types if x.lstrip('[').startswith('Lorg/bouncycastle/'))
  count,offset=struct.unpack_from('<II',data,96)
  defined.update(types[struct.unpack_from('<I',data,offset+32*i)[0]] for i in range(count))
missing=sorted(referenced-defined)
# OkHttp 4.12.0 BouncyCastlePlatform first probes the JSSE provider with Class.forName
# and catches ClassNotFoundException; buildIfSupported returns null when absent.
# This application does not install the optional bctls/JSSE provider. These three
# absent types also occur in code56 and are unrelated to HostIdentity's PKIX path.
# Source: square/okhttp parent-4.12.0, BouncyCastlePlatform.kt:76-86.
optional={
 'Lorg/bouncycastle/jsse/BCSSLParameters;',
 'Lorg/bouncycastle/jsse/BCSSLSocket;',
 'Lorg/bouncycastle/jsse/provider/BouncyCastleJsseProvider;',
}
mandatory=[x for x in missing if x not in optional]
print(json.dumps({'apk':a.apk.name,'bcReferencedTypes':len(referenced),'bcDefinedTypes':len([x for x in defined if x.startswith('Lorg/bouncycastle/')]),'missingMandatory':mandatory,'guardedOptionalJsseMissing':[x for x in missing if x in optional],'passed':bool(mandatory) if a.expect_missing else not mandatory},indent=2))
if bool(mandatory)!=a.expect_missing:raise SystemExit(1)
