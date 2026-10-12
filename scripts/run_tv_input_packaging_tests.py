#!/usr/bin/env python3
"""Synthetic source-input archive guards. No keys, real credentials, or publication."""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
import zipfile
from tv_release_inputs import generated_inputs, pack, restore, safe_relative

def sha(data): return hashlib.sha256(data).hexdigest()

class InputPackaging(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix='mooncast-input-tests-')
        self.root = Path(self.tmp.name)
        def write(name, data):
            if isinstance(data, str): data = data.encode()
            p = self.root / name; p.parent.mkdir(parents=True, exist_ok=True); p.write_bytes(data)
            return sha(data)
        self.write = write
        airsha = write('native/airplay/source.tar.gz', b'public source fixture')
        libsha = write('tv/src/main/jniLibs/arm64-v8a/libairplay_native.so', b'fixture runtime')
        write('native/airplay/prebuilt-manifest.json', json.dumps({'sources':{'source.tar.gz':airsha},'libraries':{'arm64-v8a/libairplay_native.so':libsha}}))
        source_sha = write('moonlight-core/native/native-sources/openssl/LICENSE.txt','public license fixture')
        inventory = source_sha + '  native-sources/openssl/LICENSE.txt\n'
        invsha = write('moonlight-core/SOURCE-INPUTS-SHA256SUMS',inventory)
        jarsha = write('moonlight-core/native/java-sources/example-sources.jar', b'public Java source fixture')
        javasha = write('moonlight-core/JAVA-SOURCES.json',json.dumps([{'file':'example-sources.jar','sha256':jarsha}]))
        locksha = write('moonlight-core/SOURCE_LOCK.json',json.dumps({'source_input_manifest_sha256':invsha,'java_sources_manifest_sha256':javasha}))
        rows=[]
        for abi in ['arm64-v8a','armeabi-v7a','x86_64']:
            path='src/main/jniLibs/'+abi+'/libmoonlight-core.so'
            digest=write('moonlight-core/'+path,abi)
            rows.append({'abi':abi,'path':path,'sha256':digest})
        write('moonlight-core/native-build-manifest.json',json.dumps({'source_lock_sha256':locksha,'toolchain':{'ndk':'29.0.14206865'},'binaries':rows}))
        for target, source in [('BUILDING.md','SOURCE-BUILDING.md'),('dependency-rebuild/rebuild.sh','rebuild_moonlight_dependencies.sh'),('full-source-native/relink.sh','relink_moonlight_native.sh'),('full-source-native/exports.map','exports.map')]:
            write('moonlight-core/scripts/'+source,source)
            write('moonlight-core/native/'+target,source)
        write('moonlight-core/native/native-sources/SOURCE-FILES-SHA256SUMS',source_sha+'  openssl/LICENSE.txt\n')
    def tearDown(self): self.tmp.cleanup()
    def test_roundtrip_and_determinism(self):
        a=self.root/'a.zip';b=self.root/'b.zip'
        count=pack(a,self.root);pack(b,self.root)
        self.assertEqual(a.read_bytes(),b.read_bytes())
        self.assertEqual(restore(a,self.root),count)
    def test_changed_source_rejected(self):
        self.write('moonlight-core/native/native-sources/openssl/LICENSE.txt','changed')
        with self.assertRaises(ValueError): generated_inputs(self.root)
    def test_source_lock_mismatch(self):
        self.write('moonlight-core/SOURCE_LOCK.json','{}')
        with self.assertRaises(ValueError): generated_inputs(self.root)
    def test_native_path_escape(self):
        p=self.root/'moonlight-core/native-build-manifest.json';data=json.loads(p.read_text());data['binaries'][0]['path']='../secret';p.write_text(json.dumps(data))
        with self.assertRaises(ValueError): generated_inputs(self.root)
    def test_invalid_inventory_path(self):
        for path in ['../secret','/tmp/secret','a/../../secret','a\\secret','']:
            with self.assertRaises(ValueError):safe_relative(path)
    def test_archive_cannot_replace_committed_manifest(self):
        path=self.root/'evil.zip';original=(self.root/'native/airplay/prebuilt-manifest.json').read_bytes()
        with zipfile.ZipFile(path,'w') as z:z.writestr('native/airplay/prebuilt-manifest.json','{}')
        with self.assertRaises(ValueError):restore(path,self.root)
        self.assertEqual((self.root/'native/airplay/prebuilt-manifest.json').read_bytes(),original)
    def test_symlink_source_rejected(self):
        p=self.root/'moonlight-core/native/java-sources/example-sources.jar';data=p.read_bytes();p.unlink();target=self.root/'elsewhere';target.write_bytes(data);p.symlink_to(target)
        with self.assertRaises(ValueError):generated_inputs(self.root)

if __name__=='__main__':unittest.main()
