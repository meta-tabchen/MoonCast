#!/usr/bin/env python3
"""Package receiver OpenSSL under a unique SONAME, retaining separate processes.
No code/symbol relocation: replace only exact dynamic-string entries with equal-size
names in checksum-pinned receiver inputs. Re-run after build_airplay_patched.py.
Original inputs stay untouched; outputs are deterministic and independently pinned.
"""
from pathlib import Path
import hashlib,json,struct
ROOT=Path(__file__).resolve().parents[1]
OLD=b'libcrypto.so';NEW=b'libtvcryp.so'
def sha(data):return hashlib.sha256(data).hexdigest()
def rename_dynamic(data):
    b=bytearray(data)
    if b[:4]!=b'\x7fELF' or b[5]!=1:raise ValueError('Expected little-endian ELF')
    is64=b[4]==2
    off=struct.unpack_from('<Q' if is64 else '<I',b,40 if is64 else 32)[0]
    size,count=struct.unpack_from('<HH',b,58 if is64 else 46)
    sections=[struct.unpack_from('<IIQQQQIIQQ' if is64 else '<IIIIIIIIII',b,off+i*size) for i in range(count)]
    changed=0
    for section in sections:
        if section[1]!=6:continue # SHT_DYNAMIC
        strings=sections[section[6]]
        for pos in range(section[4],section[4]+section[5],section[9]):
            tag,index=struct.unpack_from('<QQ' if is64 else '<II',b,pos)
            if tag not in (1,14):continue # DT_NEEDED or DT_SONAME
            at=strings[4]+index;end=b.index(0,at)
            if bytes(b[at:end])==OLD:
                assert len(OLD)==len(NEW)
                b[at:end]=NEW;changed+=1
    if changed!=1:raise ValueError('Expected exactly one crypto dynamic reference, got '+str(changed))
    return bytes(b)
def main():
    manifest=json.loads((ROOT/'native/airplay/prebuilt-manifest.json').read_text())
    output=ROOT/'unified/native-receiver';record={'transformationRecipe':'scripts/prepare_unified_native.py','transformationRecipeSha256':sha(Path(__file__).read_bytes()),'strategy':'receiver process + distinct OpenSSL filename/DT_NEEDED/SONAME','libraries':{},'sourceManifest':'native/airplay/prebuilt-manifest.json'}
    for name,h in manifest['libraries'].items():
        data=(ROOT/'tv/src/main/jniLibs'/name).read_bytes()
        if sha(data)!=h:raise ValueError('Source hash mismatch: '+name)
        abi,filename=name.split('/')
        if filename in ('libcrypto.so','libairplay_native.so'):data=rename_dynamic(data)
        target_name=abi+'/'+('libtvcryp.so' if filename=='libcrypto.so' else filename)
        dest=output/target_name;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(data)
        record['libraries'][target_name]={'sha256':sha(data),'source':name,'sourceSha256':h}
    (ROOT/'native/unified-receiver-manifest.json').write_text(json.dumps(record,indent=2)+'\n')
    print('Prepared 12 isolated receiver libraries; no originals changed')
if __name__=='__main__':main()
