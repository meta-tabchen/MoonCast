#!/usr/bin/env python3
"""Orchestrator-only regression: a failed requested device stage can never report PASS.
No Android/native execution is performed by these tests.
"""
import contextlib,io,json,pathlib,runpy,subprocess,sys,tempfile
from unittest.mock import patch
SCRIPT=pathlib.Path(__file__).with_name('run_receiver_session_device.py')
def scenario(game,exception=None,airplay=True,native=True):
 with tempfile.TemporaryDirectory() as temp:
  out=pathlib.Path(temp)/'evidence'
  def command(args,**kwargs):
   text='Success\n';code=0
   if 'package' in args:text='package:/system/framework/framework-res.apk\n'
   elif 'logcat' in args:text='generated test log\n'
   elif 'instrument' in args:
    if 'gamestream-native' in args:
     if exception:raise exception
     text=('GAMESTREAM_NATIVE_SESSION_PASSED' if game else 'GAMESTREAM_NATIVE_SESSION_FAILED')+'\nINSTRUMENTATION_CODE: '+('-1' if game else '0')
    elif 'airplay-receiver' in args:text=('AIRPLAY_RECEIVER_SESSION_PASSED' if airplay else 'FAILED')+'\nINSTRUMENTATION_CODE: -1'
    else:text=('actual Android sender/receiver JNI loads' if native else 'FAILED')+'\nINSTRUMENTATION_CODE: -1'
   return subprocess.CompletedProcess(args,code,text,'')
  exit_ok=True
  with patch.object(sys,'argv',[str(SCRIPT),'--gamestream','--serial','emulator-5554','--debug','fixture.apk','--test','test.apk','--output',str(out)]),patch('subprocess.run',side_effect=command),patch('time.sleep'),contextlib.redirect_stdout(io.StringIO()):
   try:runpy.run_path(str(SCRIPT),run_name='__main__')
   except SystemExit:exit_ok=False
  status=json.loads((out/'result.json').read_text())
  expected=game and exception is None and airplay and native
  assert status['passed'] is expected,(status,expected)
  assert exit_ok is expected,(status,exit_ok)
  return status
scenario(True)
scenario(False)
scenario(True,subprocess.TimeoutExpired(['adb'],600,output='partial game output'))
scenario(True,RuntimeError('generated failure'))
scenario(True,airplay=False)
scenario(True,native=False)
print('Receiver runner aggregate failure policy: 6 mocked orchestrator cases passed; no device/native coverage')
