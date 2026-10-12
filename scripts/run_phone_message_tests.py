#!/usr/bin/env python3
"""Exercise production PhoneMessages with resource/JSON boundary adapters on JDK 17.

Reads both production XML resource sets rather than keeping translated fixture copies.
This verifies presentation logic, not Android locale lifecycle or org.json compatibility.
No Android SDK, downloaded dependency, device or network is required.
"""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
java_home = os.environ.get('JAVA_HOME')
suffix = '.exe' if os.name == 'nt' else ''
def tool(name):
    return str(Path(java_home) / 'bin' / (name + suffix)) if java_home else name

translations = {}
for locale in ('values', 'values-zh'):
    translations[locale] = {
        item.attrib['name']: ''.join(item.itertext()).replace('\\n', '\n').replace("\\'", "'")
        for item in ET.parse(root / 'app/src/main/res' / locale / 'phone_localization.xml').getroot()
    }
names = sorted(translations['values'])
assert set(names) == set(translations['values-zh']), 'Phone resource key mismatch'
def string(value):
    return json.dumps(value, ensure_ascii=False)

flags = ['-source', '17', '-target', '17'] if os.environ.get('MOONCAST_TEST_JAVAC_SOURCE_ONLY') == '1' else ['--release', '17']
with tempfile.TemporaryDirectory(prefix='mooncast-phone-messages-') as directory:
    temp = Path(directory)
    generated_r = temp / 'R.java'
    generated_r.write_text('package com.mooncast.host; final class R {static final class string {\n'
        + ''.join('static final int ' + name + '=' + str(i + 1) + ';\n' for i, name in enumerate(names))
        + '}}\n', encoding='utf-8')
    generated_strings = temp / 'PhoneMessageResources.java'
    generated_strings.write_text('package com.mooncast.host; final class PhoneMessageResources {\n'
        + 'static final String[] NAMES={"",' + ','.join(map(string, names)) + '};\n'
        + 'static final String[][] TEXT={' + ','.join('{"",' + ','.join(
            string(translations[locale][name]) for name in names) + '}'
            for locale in ('values', 'values-zh')) + '};}\n', encoding='utf-8')
    sources = sorted((root / 'tests/phone_message_stubs').rglob('*.java'))
    sources += [generated_r, generated_strings, root / 'tests/PhoneMessagesTest.java',
                root / 'app/src/main/java/com/mooncast/host/PhoneMessages.java']
    subprocess.run([tool('javac'), *flags, '-encoding', 'UTF-8', '-d', directory, *map(str, sources)], check=True)
    subprocess.run([tool('java'), '-cp', directory, 'com.mooncast.host.PhoneMessagesTest'], check=True)
