#!/usr/bin/env python3
"""Static scope checks only; not runtime or Miracast interoperability tests."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parent
ns = '{http://schemas.android.com/apk/res/android}'
manifest = ET.parse(root / 'src/main/AndroidManifest.xml').getroot()
permissions = {x.attrib[ns + 'name']: x for x in manifest.findall('uses-permission')}
sources = '\n'.join(p.read_text() for p in (root / 'src/main/java').rglob('*.java'))
probe = (root / 'src/main/java/com/mooncast/miracastlab/CapabilityProbe.java').read_text()
activity = (root / 'src/main/java/com/mooncast/miracastlab/LabActivity.java').read_text()
checks = 0

def check(condition, message):
    global checks
    if not condition:
        raise AssertionError(message)
    checks += 1

check('android.permission.INTERNET' not in permissions, 'Diagnostic must not transmit network traffic')
check('android.permission.CONFIGURE_WIFI_DISPLAY' not in permissions, 'Do not request signature permission')
check(permissions['android.permission.NEARBY_WIFI_DEVICES'].attrib.get(ns + 'usesPermissionFlags') == 'neverForLocation', 'Nearby permission purpose')
check(permissions['android.permission.ACCESS_FINE_LOCATION'].attrib.get(ns + 'maxSdkVersion') == '32', 'Location permission limited to older OS')
for method in ('setWfdInfo', 'discoverPeers', 'discoverServices', 'startListening', 'connect', 'createGroup', 'removeGroup', 'setVendorElements', 'setWifiEnabled'):
    check(not re.search(r'\.' + method + r'\s*\(', sources), 'Forbidden state change: ' + method)
for field in ('getPassphrase', 'getHardwareAddress', 'getHostAddress', 'getSerial', 'getSSID', 'getBSSID', 'getDeclaredMethod', 'setAccessible'):
    check(not re.search(r'\.' + field + r'\s*\(', sources), 'Forbidden data/API access: ' + field)
check('if (!hasRuntimePermission())' in probe, 'Permission must gate query calls')
check('if (Build.VERSION.SDK_INT < 27)' in probe and 'Build.VERSION.SDK_INT >= 27' in probe, 'Channel lifecycle API guards')
check('token == generation' in probe and '++generation' in probe, 'Reject stale callbacks')
check('6000' in probe and 'R.string.timed_out' in probe, 'Bounded callback wait with inconclusive result')
check('FLAG_SYSTEM' in probe and '!info.exported' in probe, 'Settings must resolve to accessible system handler')
check('setPositiveButton(R.string.continue_label' in activity and 'requestPermissions(' in activity, 'Explicit permission flow')
check('Intent.ACTION_CREATE_DOCUMENT' in activity, 'User-selected export only')
check(not re.search(r'\b(signingConfig|storeFile|keyAlias)\s', (root / 'build.gradle').read_text()), 'No release signing identity')
en = ET.parse(root / 'src/main/res/values/strings.xml').getroot()
zh = ET.parse(root / 'src/main/res/values-zh-rCN/strings.xml').getroot()
check({s.attrib['name'] for s in en} == {s.attrib['name'] for s in zh}, 'English/Chinese resource keys match')
print(f'PASS: {checks} diagnostic source-boundary checks; no device or casting claim')
