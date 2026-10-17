#!/usr/bin/env python3
"""Host-only metadata fixtures for the device test harness; no Android pass implied."""
import copy
import re
import io
import tarfile
import tempfile
import importlib.util
from pathlib import Path
import unittest

p=Path(__file__).with_name('run_community_device_checks.py')
spec=importlib.util.spec_from_file_location('device_checks',p)
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)

class Metadata(unittest.TestCase):
 def fixture(self):
  cert='a'*64
  rows={n:{'applicationId':m.PACKAGE,'versionCode':v,'versionName':m.VERSION_NAME,'certificateSha256':cert,'debuggable':n=='debug'} for n,v in [('baseline',m.BASELINE_VERSION_CODE),('candidate',m.CANDIDATE_VERSION_CODE),('debug',m.CANDIDATE_VERSION_CODE)]}
  rows['test']={'applicationId':m.TEST_PACKAGE,'versionCode':None,'versionName':'','certificateSha256':cert,'debuggable':True}
  return rows,cert
 def test_test_apk_empty_version(self):
  b="package: name='com.mooncast.community.test' versionCode='' versionName='' compileSdkVersion='35'"
  # The previous numeric-only regex rejected this legitimate test APK.
  self.assertIsNone(re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']*)'",b,re.M))
  self.assertIsNone(m.parse_badging(b)['versionCode'])
  rows,cert=self.fixture();m.validate_fixtures(rows,cert)
 def test_test_apk_absent_version(self):
  self.assertEqual(m.parse_badging("package: name='com.mooncast.community.test'"),{'applicationId':m.TEST_PACKAGE,'versionCode':None,'versionName':''})
 def test_app_version_preserved(self):
  self.assertEqual(m.parse_badging("package: name='com.mooncast.community' versionCode='59' versionName='0.7.2'")['versionCode'],59)
 def test_badging_missing(self):self.assertIsNone(m.parse_badging('application: label=MoonCast'))
 def test_app_malformed_version_rejected(self):
  rows,cert=self.fixture();rows['candidate']['versionCode']=m.parse_badging("package: name='com.mooncast.community' versionCode='abc'")['versionCode']
  with self.assertRaises(RuntimeError):m.validate_fixtures(rows,cert)
 def test_each_required_identity(self):
  rows,cert=self.fixture()
  for name in rows:
   for field in ('applicationId','certificateSha256'):
    with self.subTest(name=name,field=field):
     bad=copy.deepcopy(rows);bad[name][field]='wrong'
     with self.assertRaises(RuntimeError):m.validate_fixtures(bad,cert)
 def test_app_version_guards(self):
  rows,cert=self.fixture()
  for name in ('baseline','candidate','debug'):
   for value in (None,0,50,51,52,53,54,55,56,60):
    with self.subTest(name=name,value=value):
     bad=copy.deepcopy(rows);bad[name]['versionCode']=value
     with self.assertRaises(RuntimeError):m.validate_fixtures(bad,cert)
 def test_exact_browser_release_contract(self):
  self.assertEqual((m.BASELINE_VERSION_CODE,m.CANDIDATE_VERSION_CODE,m.VERSION_NAME),(64,65,'0.8'))
  rows,cert=self.fixture()
  for name in ('baseline','candidate','debug'):
   for value in (None,'','0.5.1-community','0.7-browser-preview.2'):
    with self.subTest(name=name,value=value):
     bad=copy.deepcopy(rows);bad[name]['versionName']=value
     with self.assertRaises(RuntimeError):m.validate_fixtures(bad,cert)
 def test_baseline_and_candidate_are_not_interchangeable(self):
  rows,cert=self.fixture()
  for name,value in (('baseline',65),('candidate',64),('debug',64)):
   with self.subTest(name=name):
    bad=copy.deepcopy(rows);bad[name]['versionCode']=value
    with self.assertRaises(RuntimeError):m.validate_fixtures(bad,cert)
 def test_workflow_version_and_manual_device_contract(self):
  workflow=(p.parents[1]/'.github/workflows/unified.yml').read_text()
  for label in ('baseline65.apk','candidate66.apk','debug66.apk','MoonCast-0.9-arm64-v8a.apk','MoonCast-0.9-armeabi-v7a.apk','MoonCast-0.9-source.zip'):
   self.assertIn(label,workflow)
  for stale in ('baseline50.apk','candidate51.apk','debug51.apk','MoonCast-0.5.1-community-'):
   self.assertNotIn(stale,workflow)
  self.assertIn("if: github.event_name == 'workflow_dispatch'",workflow)
  for check in ('python3 scripts/run_app_build_info_tests.py','python3 scripts/run_tv_role_picker_tests.py','node --check app/src/main/assets/browser/receiver.js'):
   self.assertIn(check,workflow)
 def test_all_install_routes(self):
  # Baseline, release and debug share the same exact app target and flags.
  for name in ('baseline', 'candidate', 'debug'):
   with self.subTest(name=name):self.assertEqual(m.installation_flags(m.PACKAGE), ['-r'])
  self.assertEqual(m.installation_flags(m.TEST_PACKAGE), ['-r','-t'])
  with self.assertRaises(ValueError):m.installation_flags('unrelated.package')
 def test_stream_retry_does_not_bypass_install_rejection(self):
  self.assertTrue(m.retry_stream_error(1,'','cmd: Failure calling service package: Broken pipe (32)'))
  self.assertFalse(m.retry_stream_error(0,'Success','Broken pipe'))
  for message in ('INSTALL_FAILED_UPDATE_INCOMPATIBLE Broken pipe', 'INSTALL_FAILED_VERSION_DOWNGRADE', 'INSTALL_FAILED_INSUFFICIENT_STORAGE', 'different error'):
   with self.subTest(message=message):self.assertFalse(m.retry_stream_error(1,'',message))
 def archive(self,name='phase-en-report.json',kind=tarfile.REGTYPE):
  data=io.BytesIO()
  with tarfile.open(fileobj=data,mode='w') as archive:
   entry=tarfile.TarInfo(name);entry.type=kind
   if kind==tarfile.REGTYPE:entry.size=2;archive.addfile(entry,io.BytesIO(b'{}'))
   else:entry.linkname='/tmp/unrelated';archive.addfile(entry)
  return data.getvalue()
 def test_private_evidence_flat_extract(self):
  with tempfile.TemporaryDirectory() as d:
   m.extract_evidence(self.archive('./phase-en-report.json'),Path(d))
   self.assertEqual((Path(d)/'phase-en-report.json').read_bytes(),b'{}')
 def test_evidence_path_and_link_rejection(self):
  with tempfile.TemporaryDirectory() as d:
   for name in ('../outside.json','/outside.json','nested/out.json'):
    with self.subTest(name=name),self.assertRaises(RuntimeError):m.extract_evidence(self.archive(name),Path(d))
   with self.assertRaises(RuntimeError):m.extract_evidence(self.archive(kind=tarfile.SYMTYPE),Path(d))
 def test_debug_flags(self):
  rows,cert=self.fixture()
  for name in ('baseline','candidate','debug'):
   with self.subTest(name=name):
    bad=copy.deepcopy(rows);bad[name]['debuggable']=not bad[name]['debuggable']
    with self.assertRaises(RuntimeError):m.validate_fixtures(bad,cert)

if __name__=='__main__':unittest.main()
