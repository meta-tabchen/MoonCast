"""Optional x86_64 loopback fixture, never used in release APKs or checked into Git."""
from pathlib import Path
import argparse,hashlib,urllib.request,zipfile,time

root=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser();parser.add_argument('apk',type=Path,nargs='?');args=parser.parse_args()
url='https://github.com/moonlight-stream/moonlight-android/releases/download/v12.2/app-nonRoot-release.apk'
expected='a871f6190365386ef3ac5722a016d4da015ee1555631ec257f6e5dc3f4a45fe0'
if args.apk:data=args.apk.read_bytes()
else:
    payload=bytearray();size=11137885
    while len(payload)<size:
        offset=len(payload);end=min(size-1,offset+1024*1024-1)
        for attempt in range(4):
            try:
                request=urllib.request.Request(url+f'?mooncast_chunk={offset}',headers={'Range':f'bytes={offset}-{end}','User-Agent':'MoonCast-test-fixture'})
                with urllib.request.urlopen(request,timeout=35) as response:block=response.read();actual=response.headers.get('Content-Range','');status=response.status
                if status==200 and len(block)==size:payload=bytearray(block);break
                if status==206 and actual.startswith(f'bytes {offset}-') and len(block)==end-offset+1:payload.extend(block);break
                raise RuntimeError('Unexpected ranged response')
            except Exception:
                if attempt==3:raise
                time.sleep(1)
        print('Test dependency bytes:',len(payload),'/',size)
    data=bytes(payload)
assert hashlib.sha256(data).hexdigest()==expected,'Unexpected official test APK hash'
import io
with zipfile.ZipFile(io.BytesIO(data)) as archive:library=archive.read('lib/x86_64/libmoonlight-core.so')
assert hashlib.sha256(library).hexdigest()=='c37d20986ac20c34ba26de1048a567e643ce0a68f20dd6927c302f51c4317bc2'
folder=root/'app/src/debug/jniLibs/x86_64';folder.mkdir(parents=True,exist_ok=True);(folder/'libmoonlight-core.so').write_bytes(library)
print('Prepared official Moonlight Android 12.2 core for debug-only x86_64 tests.')
