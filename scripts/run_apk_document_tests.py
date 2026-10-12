"""Real APK bytes with provider-name variants; never installs or executes APKs."""
from pathlib import Path
import os,sys,subprocess,tempfile
root=Path(__file__).resolve().parents[1]
bin=Path(os.environ.get('JAVA_HOME',''))/'bin'
flags=['-source','17','-target','17'] if os.getenv('MOONCAST_TEST_JAVAC_SOURCE_ONLY')=='1' else ['--release','17']
sources=[root/'app/src/main/java/com/mooncast/host'/f'{n}.java' for n in ('ApkDocumentName','ApkSingleManifest')]
sources += [root/'app/src/main/java/com/mooncast/transfer/ApkTransfer.java',root/'tests/ApkDocumentNameTest.java']
with tempfile.TemporaryDirectory() as tmp:
 subprocess.run([str(bin/'javac'),*flags,'-d',tmp,*map(str,sources)],check=True)
 subprocess.run([str(bin/'java'),'-cp',tmp,'com.mooncast.host.ApkDocumentNameTest',*sys.argv[1:]],check=True)
