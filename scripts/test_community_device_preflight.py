#!/usr/bin/env python3
"""Host-only metadata fixtures for the device test harness; no Android pass implied."""
import copy
import re
import importlib.util
from pathlib import Path
import unittest

p=Path(__file__).with_name('run_community_device_checks.py')
spec=importlib.util.spec_from_file_location('device_checks',p)
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)

class Metadata(unittest.TestCase):
 def fixture(self):
  cert='a'*64
  rows={n:{'applicationId':m.PACKAGE,'versionCode':v,'certificateSha256':cert,'debuggable':n=='debug'} for n,v in [('baseline',50),('candidate',51),('debug',51)]}
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
  self.assertEqual(m.parse_badging("package: name='com.mooncast.community' versionCode='51' versionName='0.5.1-community'")['versionCode'],51)
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
   for value in (None,0,49,52):
    with self.subTest(name=name,value=value):
     bad=copy.deepcopy(rows);bad[name]['versionCode']=value
     with self.assertRaises(RuntimeError):m.validate_fixtures(bad,cert)
 def test_debug_flags(self):
  rows,cert=self.fixture()
  for name in ('baseline','candidate','debug'):
   with self.subTest(name=name):
    bad=copy.deepcopy(rows);bad[name]['debuggable']=not bad[name]['debuggable']
    with self.assertRaises(RuntimeError):m.validate_fixtures(bad,cert)

if __name__=='__main__':unittest.main()
