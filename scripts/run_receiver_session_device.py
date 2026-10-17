#!/usr/bin/env python3
"""Run real generated-media AirPlay loopback against a disposable emulator only."""
import argparse,pathlib,subprocess,json,time
p=argparse.ArgumentParser();p.add_argument('--gamestream',action='store_true');p.add_argument('--surface',choices=['reuse','fresh'],default='reuse');p.add_argument('--probe-texture',action='store_true');p.add_argument('--serial',required=True);p.add_argument('--debug',type=pathlib.Path,required=True);p.add_argument('--test',type=pathlib.Path,required=True);p.add_argument('--output',type=pathlib.Path,required=True);a=p.parse_args()
if not a.serial.startswith('emulator-'):raise SystemExit('This suite is restricted to a disposable emulator, never a personal device')
a.output.mkdir(parents=True,exist_ok=True)
def adb(*args,**kwargs):return subprocess.run(['adb','-s',a.serial,*map(str,args)],text=True,capture_output=True,**kwargs)
passed=False;status={'passed':False,'scope':'disposable emulator AirPlay native transport/JNI/MediaCodec/GL pixels/reconnect; optional generated-Surface Sunshine encode/transport/MediaCodec outputs and native sessions; no physical TV, LAN discovery, screen capture or audio acceptance','gameStreamRequested':a.gamestream,'surfaceMode':a.surface,'acquireWithoutSignal':a.probe_texture};evidence=[]
try:
 # boot_completed can briefly precede PackageManager readiness or a system-server restart.
 end=time.monotonic()+180;ready=0
 while time.monotonic()<end:
  try:
   probe=adb('shell','cmd','package','path','android',timeout=15)
   ready=ready+1 if probe.returncode==0 and 'package:' in probe.stdout else 0
  except subprocess.TimeoutExpired:
   ready=0
  if ready>=3:break
  time.sleep(3)
 if ready<3:raise RuntimeError('Emulator package service did not become stable; no app test executed')
 for file in [a.debug,a.test]:
  result=adb('install','-r','-t',file,timeout=180);evidence.append(result.stdout+result.stderr)
  if result.returncode or 'Success' not in result.stdout:raise RuntimeError('Fixture installation failed')
 result=adb('shell','am','instrument','-w','-r','com.mooncast.community.test/com.mooncast.unified.UnifiedInstrumentation',timeout=180)
 load_text=result.stdout+result.stderr;evidence.append(load_text)
 status['nativeLoadPassed']=result.returncode==0 and 'actual Android sender/receiver JNI loads' in load_text and 'INSTRUMENTATION_CODE: -1' in load_text and 'INSTRUMENTATION_FAILED' not in load_text
 if not status['nativeLoadPassed']:raise RuntimeError('Actual sender/receiver library load or isolated process test failed')
 result=adb('shell','am','instrument','-w','-r','-e','suite','airplay-receiver','-e','surface',a.surface,'-e','acquireWithoutSignal',str(a.probe_texture).lower(),'com.mooncast.community.test/com.mooncast.unified.UnifiedInstrumentation',timeout=300)
 text=result.stdout+result.stderr;evidence.append(text);status['returnCode']=result.returncode
 passed=result.returncode==0 and 'AIRPLAY_RECEIVER_SESSION_PASSED' in text and 'INSTRUMENTATION_CODE: -1' in text and 'INSTRUMENTATION_FAILED' not in text
 status['airplayReceiverPassed']=passed
 if a.gamestream:
  game=adb('shell','am','instrument','-w','-r','-e','suite','gamestream-native','com.mooncast.community.test/com.mooncast.unified.UnifiedInstrumentation',timeout=600)
  game_text=game.stdout+game.stderr;evidence.append(game_text)
  status['gameStreamPassed']=game.returncode==0 and 'GAMESTREAM_NATIVE_SESSION_PASSED' in game_text and 'INSTRUMENTATION_CODE: -1' in game_text and 'INSTRUMENTATION_FAILED' not in game_text
  passed=passed and status['gameStreamPassed']
except subprocess.TimeoutExpired as failure:
 passed=False
 def decoded(value):return value.decode(errors='replace') if isinstance(value,bytes) else value or ''
 evidence.append(decoded(failure.stdout)+decoded(failure.stderr));status['failure']='command-timeout'
except Exception as failure:
 passed=False
 status['failure']=str(failure)
finally:
 status['passed']=passed;(a.output/'instrumentation.txt').write_text('\n'.join(evidence))
 try:
  logs=adb('logcat','-d','-t','2000','-v','threadtime',timeout=30)
  # This disposable device contains only generated fixtures, never user media.
  (a.output/'logcat.txt').write_text(logs.stdout)
  if not passed:
   print('Disposable emulator diagnostic tail:')
   print('\n'.join(logs.stdout.splitlines()[-180:]))
 except Exception as failure:status['logCaptureFailure']=type(failure).__name__
 (a.output/'result.json').write_text(json.dumps(status,indent=2)+'\n');print('\n'.join(evidence));print(json.dumps(status,indent=2))
if not passed:raise SystemExit('AirPlay receiver session test failed/blocked/process exited; see captured evidence')
