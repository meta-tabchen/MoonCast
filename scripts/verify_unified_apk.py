#!/usr/bin/env python3
# Verification uses explicit exceptions; PYTHONOPTIMIZE must not disable safety checks.
"""Verify every unified native payload byte, JNI graph and standalone APK identity."""
from pathlib import Path
import argparse, hashlib, json, os, re, subprocess, tempfile, zipfile
from elf_dependencies import dynamic_strings, elf_exports
R = Path(__file__).resolve().parents[1]

def sha(b):
    return hashlib.sha256(b).hexdigest()
p = argparse.ArgumentParser()
p.add_argument('apk', type=Path)
p.add_argument('--abi', required=True, choices=['arm64-v8a', 'armeabi-v7a', 'x86_64'])
p.add_argument('--debug', action='store_true')
a = p.parse_args()
expected = {}
for path in (R / 'app/src/main/jniLibs' / a.abi).glob('*.so'):
    expected[path.name] = sha(path.read_bytes())
receiver = json.loads((R / 'native/unified-receiver-manifest.json').read_text())
for name, row in receiver['libraries'].items():
    if name.startswith(a.abi + '/'):
        filename = name.split('/')[1]
        if not filename not in expected:
            raise ValueError('duplicate native name')
        expected[filename] = row['sha256']
moon = json.loads((R / 'moonlight-core/native-build-manifest.json').read_text())
for row in moon['binaries']:
    if row['abi'] == a.abi:
        expected['libmoonlight-core.so'] = row['sha256']
with zipfile.ZipFile(R / 'app/libs/airplaylib.aar') as z:
    expected['libgojni.so'] = sha(z.read('jni/' + a.abi + '/libgojni.so'))
webrtc = json.loads((R / 'native/webrtc/SOURCE_LOCK.json').read_text())
expected['libjingle_peerconnection_so.so'] = webrtc['libraries'][a.abi + '/libjingle_peerconnection_so.so']
with zipfile.ZipFile(a.apk) as z, tempfile.TemporaryDirectory() as d:
    names = [n for n in z.namelist() if n.startswith('lib/') and n.endswith('.so')]
    if not len(names) == len(expected) == 11:
        raise ValueError((len(names), len(expected)))
    if not set(names) == {'lib/' + a.abi + '/' + n for n in expected}:
        raise ValueError('Verification failed')
    for name, h in expected.items():
        data = z.read('lib/' + a.abi + '/' + name)
        if not sha(data) == h:
            raise ValueError(name)
        dyn = dynamic_strings(data)
        for dep in dyn['needed']:
            if not (dep in expected or dep in {'libandroid.so', 'libmediandk.so', 'liblog.so', 'libm.so', 'libdl.so', 'libc.so', 'libz.so', 'libOpenSLES.so', 'libEGL.so'}):
                raise ValueError((name, dep))
        if name == 'libairplay_native.so':
            if not ('libtvcryp.so' in dyn['needed'] and 'libcrypto.so' not in dyn['needed']):
                raise ValueError('Verification failed')
        if name == 'libtvcryp.so':
            if not dyn['soname'] == 'libtvcryp.so':
                raise ValueError('Verification failed')
    sunshine_exports=elf_exports(z.read('lib/'+a.abi+'/libsunshine.so'))
    if '_ZN6stream7session4stopERNS_9session_tE' not in sunshine_exports:
        raise ValueError('Sunshine session stop export missing')
    bridge_exports=elf_exports(z.read('lib/'+a.abi+'/libmooncast_sessions.so'))
    for method in ('availableNative','stopNative'):
        if 'Java_io_github_jqssun_displaymirror_sunshine_NativeSessions_'+method not in bridge_exports:
            raise ValueError('Session bridge JNI export missing: '+method)
    dex = b''.join((z.read(n) for n in z.namelist() if re.fullmatch('classes\\d*\\.dex', n)))
    for asset in ('index.html', 'receiver.js', 'receiver.css'):
        name = 'assets/browser/' + asset
        if z.read(name) != (R / 'app/src/main/assets/browser' / asset).read_bytes():
            raise ValueError('Browser asset mismatch: ' + asset)
    for notice in webrtc['licenses']:
        if sha(z.read('assets/' + Path(notice['path']).name)) != notice['sha256']:
            raise ValueError('WebRTC license notice mismatch')
    qr = json.loads((R / 'native/browser-qr/SOURCE_LOCK.json').read_text())
    for notice in qr['bundled']:
        if notice['path'].startswith('app/src/main/assets/'):
            if sha(z.read('assets/' + Path(notice['path']).name)) != notice['sha256']:
                raise ValueError('QR license notice mismatch')
    for cls in ['com/mooncast/host/MainActivity', 'com/mooncast/tv/TvActivity', 'com/mooncast/unified/RoleActivity', 'com/mooncast/transfer/ApkTransfer', 'com/mooncast/host/BrowserHostService', 'org/webrtc/PeerConnection', 'org/jni_zero/JniInit']:
        if not ('L' + cls + ';').encode() in dex:
            raise ValueError(cls)
    if not a.debug:
        if not b'Lcom/mooncast/unified/SenderNativeProbe;' not in dex:
            raise ValueError('Debug probe leaked')
    sdk = Path(os.environ['ANDROID_HOME'])
    aapt = sdk / 'build-tools/35.0.0/aapt'
    badge = subprocess.check_output([aapt, 'dump', 'badging', a.apk], text=True)
    if not "package: name='com.mooncast.community' versionCode='66' versionName='0.9'" in badge:
        raise ValueError('Verification failed')
    if not ("sdkVersion:'26'" in badge and "targetSdkVersion:'35'" in badge):
        raise ValueError('Verification failed')
    if not ("application-label:'MoonCast Community'" in badge and 'leanback-launchable-activity:' in badge):
        raise ValueError('Verification failed')
    if not a.debug:
        if not 'application-debuggable' not in badge:
            raise ValueError('Verification failed')
print(json.dumps({'status': 'PASS static native byte/ELF/dependency/APK identity checks', 'apk': a.apk.name, 'abi': a.abi, 'sha256': sha(a.apk.read_bytes()), 'nativeLibraries': len(expected), 'notEstablished': 'Physical protocol interoperability or real capture/playback'}, indent=2))
