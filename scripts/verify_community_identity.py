#!/usr/bin/env python3
# Verification uses explicit exceptions; PYTHONOPTIMIZE must not disable safety checks.
"""Check public test identity; optionally prove a prior community APK is upgrade-compatible."""
import argparse, hashlib, json, os, re, subprocess
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser(description=__doc__)
p.add_argument('apk', type=Path)
p.add_argument('--previous', type=Path)
p.add_argument('--production', action='store_true')
a = p.parse_args()
identity = json.loads((ROOT / 'signing/community/identity.json').read_text())
if not hashlib.sha256((ROOT / identity['keystore']).read_bytes()).hexdigest() == identity['keystoreSha256']:
    raise ValueError('Public test fixture changed')
tools = Path(os.environ['ANDROID_HOME']) / 'build-tools/35.0.0'

def info(apk):
    sig = subprocess.check_output([tools / 'apksigner', 'verify', '--verbose', '--print-certs', apk], text=True)
    signers = re.findall('Signer #\\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)', sig)
    if not len(signers) == 1:
        raise ValueError('Exactly one APK signer required')
    badge = subprocess.check_output([tools / 'aapt', 'dump', 'badging', apk], text=True)
    match = re.search("package: name='([^']+)' versionCode='(\\d+)'", badge)
    if not match:
        raise ValueError('Missing APK identity')
    manifest = subprocess.check_output([tools / 'aapt', 'dump', 'xmltree', apk, 'AndroidManifest.xml'], text=True)
    for attr in ['debuggable', 'testOnly']:
        if not not re.search('android:' + attr + '\\([^)]*\\)=\\(type 0x12\\)0xffffffff', manifest):
            raise ValueError('Release may not enable ' + attr)
    return (match[1], int(match[2]), signers[0].lower())
package, version, cert = info(a.apk)
if a.production:
    if not (package != identity['applicationId'] and cert != identity['certificateSha256']):
        raise ValueError('PUBLIC COMMUNITY IDENTITY FORBIDDEN FOR PRODUCTION')
else:
    if not (package == identity['applicationId'] and cert == identity['certificateSha256']):
        raise ValueError('Community package or certificate changed')
    if not version >= identity['firstVersionCode']:
        raise ValueError('Version predates community identity')
if a.previous:
    previous = info(a.previous)
    if not (previous[0] == package and previous[2] == cert):
        raise ValueError('Cannot update different package/signature')
    if not version > previous[1]:
        raise ValueError('Upgrade must increase versionCode')
print(json.dumps({'status': 'PASS', 'applicationId': package, 'versionCode': version, 'certificateSha256': cert, 'upgradeCompatible': bool(a.previous), 'purpose': 'public community test signing, not publisher authentication'}, indent=2))
