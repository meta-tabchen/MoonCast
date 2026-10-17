#!/usr/bin/env python3
"""Run real generated-media AirPlay loopback against a disposable emulator only."""
import argparse,pathlib,subprocess,json,time
p=argparse.ArgumentParser();p.add_argument('--serial',required=True);p.add_argument('--debug',type=pathlib.Path,required=True);p.add_argument('--test',type=pathlib.Path,required=True);p.add_argument('--output',type=pathlib.Path,required=True);a=p.parse_args()
if not a.serial.startswith('emulator-'):raise SystemExit('This suite is restricted to a disposable emulator, never a personal device')
a.output.mkdir(parents=True,exist_ok=True)
def adb(*args,**kwargs):return subprocess.run(['adb','-s',a.serial,*map(str,args)],text=True,capture_output=True,**kwargs)
for file in [a.debug,a.test]:
 result=adb('install','-r','-t',file,timeout=120)
 if result.returncode or 'Success' not in result.stdout:raise SystemExit(result.stdout+result.stderr)
result=adb('shell','am','instrument','-w','-r','-e','suite','airplay-receiver','com.mooncast.community.test/com.mooncast.unified.UnifiedInstrumentation',timeout=240)
text=result.stdout+result.stderr;(a.output/'instrumentation.txt').write_text(text)
logs=adb('logcat','-d','-t','1500','-v','threadtime',timeout=20)
# This device contains only generated fixtures. Keep the tail for native crash/recovery diagnosis.
(a.output/'logcat.txt').write_text(logs.stdout)
passed=result.returncode==0 and 'AIRPLAY_RECEIVER_SESSION_PASSED' in text and 'INSTRUMENTATION_CODE: -1' in text and 'INSTRUMENTATION_FAILED' not in text
(a.output/'result.json').write_text(json.dumps({'passed':passed,'scope':'disposable emulator loopback, native transport/JNI/MediaCodec/GL pixels/reconnect; no physical TV, LAN discovery, screen capture or audio acceptance','returnCode':result.returncode},indent=2)+'\n')
print(text)
if not passed:raise SystemExit('AirPlay receiver session test failed or process exited; see captured evidence')
