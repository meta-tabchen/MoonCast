#!/usr/bin/env python3
from pathlib import Path
import os,subprocess,tempfile
r=Path(__file__).resolve().parents[1];j=Path(os.environ['JAVA_HOME'])/'bin'
with tempfile.TemporaryDirectory() as d:
 p=Path(d)/'RoleTest.java';p.write_text('''import com.mooncast.unified.RolePolicy; import com.mooncast.unified.RoleGatePolicy; public class RoleTest {public static void main(String[] a){int checks=0;for(boolean tv:new boolean[]{false,true})for(String saved:new String[]{null,"","corrupt","auto","phone","tv"}){String want="phone".equals(saved)?"phone":"tv".equals(saved)?"tv":tv?"tv":"phone";if(!want.equals(RolePolicy.selected(saved,tv)))throw new AssertionError();checks++;}String pkg="com.mooncast.unified.preview";
boolean oldGateBlocked=(pkg+":host").equals(pkg+":receiver")||(pkg+":host").equals(pkg+":stream");
if(oldGateBlocked||!RoleGatePolicy.blocks(42,42,pkg+":host",pkg))throw new AssertionError("old-fail/new-pass host teardown");
if(RoleGatePolicy.blocks(42,43,pkg+":host",pkg))throw new AssertionError("idle/restarted accessibility host must not block");
if(!RoleGatePolicy.blocks(-1,43,pkg+":receiver",pkg)||!RoleGatePolicy.blocks(-1,44,pkg+":stream",pkg))throw new AssertionError("native process still alive");
if(RoleGatePolicy.ready(false,true,true,false)||RoleGatePolicy.ready(true,false,true,false)||RoleGatePolicy.ready(true,true,false,false)||RoleGatePolicy.ready(true,true,true,true)||!RoleGatePolicy.ready(true,true,true,false))throw new AssertionError("ack gate");
System.out.println("PASS "+checks+" role policy cases plus old-fail/new-pass owner-PID teardown, idle accessibility, receiver/stream and ACK failures");}}''')
 subprocess.run([j/'javac','-d',d,r/'unified/src/main/java/com/mooncast/unified/RolePolicy.java',r/'unified/src/main/java/com/mooncast/unified/RoleGatePolicy.java',p],check=True)
 subprocess.run([j/'java','-cp',d,'RoleTest'],check=True)
