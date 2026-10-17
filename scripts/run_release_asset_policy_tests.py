#!/usr/bin/env python3
import json,pathlib
from release_asset_policy import build_id,legal_paths
S='a'*40
fixture='''E: manifest
  E: application
    E: meta-data
      A: android:name(0x1)="com.mooncast.BUILD_ID"
      A: android:value(0x2)="%s"
    E: meta-data
      A: android:name(0x1)="other"
      A: android:value(0x2)="%s"
'''
assert build_id(fixture%(S,'wrong'))==S
assert build_id(fixture%('b'*40,S))!=S
for bad in [fixture%('',S),fixture%('short',S),(fixture%(S,S)).replace('"other"','"com.mooncast.BUILD_ID"')]:
 try:build_id(bad)
 except ValueError:pass
 else:raise AssertionError('bad build-id accepted')
r=pathlib.Path(__file__).resolve().parents[1]
w=json.loads((r/'native/webrtc/SOURCE_LOCK.json').read_text());q=json.loads((r/'native/browser-qr/SOURCE_LOCK.json').read_text());required={row['path'] for row in w['licenses']}|{row['path'] for row in q['bundled'] if row['path'].startswith('app/src/main/assets/')}
names=required|{'LICENSE','THIRD_PARTY_NOTICES.md','moonlight-core/PATENTS-Opus.txt','src/main.java'}
selected=set(legal_paths(names,w,q));assert required<=selected and 'moonlight-core/PATENTS-Opus.txt' in selected and 'src/main.java' not in selected
try:legal_paths(names-{next(iter(required))},w,q)
except ValueError:pass
else:raise AssertionError('missing legal asset accepted')
print('PASS release asset policy: exact metadata binding, duplicates/missing rejected, all locked legal files and patent notices included; no APK/runtime claim')
