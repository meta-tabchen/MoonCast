#!/usr/bin/env python3
"""Package explicitly selected coexist previews; never production releases or key creation."""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import zipfile
from package_tv_release import (ROOT, committed_source_archive, require_committed_sources,
                                normalized_fingerprint, sha256)
from tv_release_inputs import generated_inputs
from verify_tv_apk import verify


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--armv7',type=Path,required=True)
    p.add_argument('--arm64',type=Path,required=True)
    p.add_argument('--expected-certificate',required=True)
    p.add_argument('--output',type=Path,required=True)
    p.add_argument('--sdk',type=Path,default=Path(os.environ.get('ANDROID_HOME','')))
    args=p.parse_args()
    expected=normalized_fingerprint(args.expected_certificate)
    require_committed_sources()
    revision=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
    version=re.search(r"versionName\s+'([^']+)'",(ROOT/'tv/build.gradle').read_text()).group(1)
    output=args.output.resolve()
    if output.exists() and any(output.iterdir()): raise ValueError('Output must be new/empty; historical files cannot be replaced')
    output.mkdir(parents=True,exist_ok=True)
    version_code=int(re.search(r"versionCode\s+(\d+)",(ROOT/'tv/build.gradle').read_text()).group(1))
    rows=[]
    for abi,path in [('armeabi-v7a',args.armv7),('arm64-v8a',args.arm64)]:
        checked=verify(path.resolve(),args.sdk.resolve(),True,abi)
        badging=subprocess.check_output([str(args.sdk/'build-tools/35.0.0/aapt'),'dump','badging',str(path)],text=True)
        if f"versionCode='{version_code}'" not in badging:raise ValueError('APK version code differs from committed source')
        report=subprocess.check_output([str(args.sdk/'build-tools/35.0.0/apksigner'),'verify','--verbose','--print-certs',str(path)],text=True)
        fingerprints=re.findall(r'^Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]+)$',report,re.M)
        if re.findall(r'^Number of signers: (\d+)$',report,re.M)!=['1'] or fingerprints!=[expected]:
            raise ValueError('Preview certificate differs from the explicitly pinned existing identity')
        subprocess.run([str(args.sdk/'build-tools/35.0.0/zipalign'),'-c','4',str(path)],check=True)
        target=output/f'MoonCast-TV-{version}-coexist-{abi}.apk'
        shutil.copyfile(path,target)
        rows.append({**checked,'file':target.name,'bytes':target.stat().st_size,'certificateSha256':expected})
    source=output/f'MoonCast-TV-{version}-coexist-source.zip'
    archive=committed_source_archive()
    extras=generated_inputs(ROOT)
    with zipfile.ZipFile(io.BytesIO(archive)) as original,zipfile.ZipFile(source,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        names=set(original.namelist())
        for name in sorted(names):
            data=original.read(name)
            if name in extras and hashlib.sha256(data).hexdigest()!=extras[name][1]:raise ValueError('Pinned committed input differs: '+name)
            info=original.getinfo(name);info.filename='MoonCast/'+name;z.writestr(info,data)
        for name,(local,checksum) in sorted(extras.items()):
            if name not in names:
                data=local.read_bytes()
                if hashlib.sha256(data).hexdigest()!=checksum:raise ValueError('Pinned input differs: '+name)
                z.writestr('MoonCast/'+name,data)
    expected_entries={"MoonCast/"+n for n in names|set(extras)}
    with zipfile.ZipFile(source) as z:
        if len(z.namelist())!=len(expected_entries) or set(z.namelist())!=expected_entries or z.testzip() is not None:
            raise ValueError('Source ZIP entry/CRC validation failed')
    build={'applicationId' :'com.mooncast.tv.preview','version':version,'versionCode':version_code,'sourceCommit':revision,
           'signing':'Existing debug/test certificate; coexist preview, not a production release',
           'apks':rows,'source':{'file':source.name,'bytes':source.stat().st_size,'sha256':sha256(source),'committedBlobCount':len(names),'generatedInputCount':len(extras),'archiveFileCount':len(expected_entries)},
           'limits':'Physical TV install-source permission, OEM installer, mDNS and phone-to-TV interoperability require user acceptance. Host/emulator tests are not those claims.'}
    (output/'BUILD_INFO.json').write_text(json.dumps(build,indent=2)+'\n')
    files=sorted(x for x in output.iterdir() if x.is_file())
    (output/'SHA256SUMS').write_text(''.join(sha256(x)+'  '+x.name+'\n' for x in files))
    print(json.dumps(build,indent=2))

if __name__=='__main__':main()
