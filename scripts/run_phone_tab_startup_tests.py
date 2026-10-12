"""Test measured-width layout and cross-process startup failure retention without Android/ADB."""
from pathlib import Path
import os, subprocess, tempfile
root=Path(__file__).resolve().parents[1]
java=Path(os.environ['JAVA_HOME'])/'bin'
with tempfile.TemporaryDirectory(prefix='mooncast-phone-policy-') as output:
    names=['ProtocolTabLayout','HostFailureStore','CastSessionCoordinator']
    source=[root/'app/src/main/java/com/mooncast/host'/f'{name}.java' for name in names]
    tests=[root/'tests'/f'{name}Test.java' for name in names]
    subprocess.run([java/'javac','--release','17','-d',output,*source,*tests],check=True)
    for name in names:subprocess.run([java/'java','-cp',output,'com.mooncast.host.'+name+'Test'],check=True)
