"""Execute production controller lifecycle methods in a generated JVM fixture.

The Android/validation collaborators are small deterministic fakes; no APK is installed.
An optional git revision exercises the exact older method bodies for regression evidence.
"""
from pathlib import Path
import os, subprocess, tempfile, sys
root = Path(__file__).resolve().parents[1]
relative = 'tv/src/main/java/com/mooncast/tv/apk/ApkReceiveController.java'
source = subprocess.check_output(['git','show',sys.argv[1]+':'+relative],cwd=root,text=True) if len(sys.argv)>1 else (root/relative).read_text()
def method(signature, fallback):
    at=source.find(signature)
    if at<0:return fallback
    brace=source.index('{',at); depth=1; end=brace+1
    while depth:
        depth += (source[end]=='{')-(source[end]=='}');end+=1
    return source[at:end]
methods='\n'.join(method(signature,fallback) for signature,fallback in [
 ('public boolean onActivityResult(',''),('public void onStop(',''),
 ('public void onPause(', 'public void onPause(){}'),('public void onResume(', 'public void onResume(){}'),
 ('public void saveState(', 'public void saveState(android.os.Bundle state){}'),('public void restoreState(', 'public void restoreState(android.os.Bundle state){}')])
fixture='''import java.io.File;
import com.mooncast.tv.apk.ApkSettingsReturn;
class ApkMetadataValidator {static void requirePrivateFile(File dir,File file)throws java.io.IOException {if(!file.isFile()||!file.getCanonicalFile().getParentFile().equals(dir.getCanonicalFile()))throw new java.io.IOException();}}
class Activity {static final int RESULT_OK=-1;}
class R {static class string {static int apk_permission_allowed=1,apk_permission_denied=2,apk_install_return_ok=3,apk_install_return_cancel=4,apk_returning_settings=5;}}
public class LifecycleFixture {
 static final int REQUEST_SOURCES=7431,REQUEST_INSTALL=7432;
 boolean external,installerActive,validating,enabled,exists=true,installLaunched;
 Object dialog=new Object(); File pending=new File("unused.apk"),directory=null,root;
 String expectedHash="0000000000000000000000000000000000000000000000000000000000000000";
 boolean expiredMessage; Runnable expiry=()->{}; Scheduler main=new Scheduler();
 static class Scheduler {void postDelayed(Runnable r,long delay){}}
 void showDialog(){dialog=new Object();}void showExpiredReview(){expiredMessage=true;showDialog();}
 long pendingUntil=10000; int reviewNotice,validations; FakeActivity activity=new FakeActivity();
 ApkSettingsReturn settingsReturn=new ApkSettingsReturn(); Label message=new Label(); Button install=new Button();
 static class Label {void setText(int id){}}
 static class Button {void setEnabled(boolean value){}}
 static class FakeActivity {boolean allowed=true;FakeActivity getPackageManager(){return this;}boolean canRequestPackageInstalls(){return allowed;}}
 void stopReceiving(){} void revoke(){} void discardFile(){pending=null;exists=false;}
 void cancel(){discardFile();dialog=null;enabled=false;validating=false;}
 void validate(boolean launch){validations++;validating=true;enabled=false;if(launch)installLaunched=true;}
 void finishValidation(){if(pending!=null){validating=false;enabled=true;}}
 void setInstallEnabled(boolean value){enabled=value;}
 void beginSettings(){external=true;enabled=false;settingsReturn.begin();}
 METHODS
 static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
 public static void main(String[] args)throws Exception{
  LifecycleFixture early=new LifecycleFixture();early.beginSettings();
  early.onActivityResult(REQUEST_SOURCES,0);early.onPause();early.onStop();
  check(early.exists&&early.pending!=null,"early Settings result followed by onStop must retain APK");
  early.onResume();check(early.validating,"return must revalidate");early.finishValidation();
  check(early.enabled&&!early.installLaunched,"permission return enables explicit action, never installs");
  LifecycleFixture missing=new LifecycleFixture();missing.beginSettings();missing.onPause();missing.onStop();missing.onResume();
  check(missing.validating&&missing.exists,"missing OEM result still revalidates onResume");missing.finishValidation();
  check(missing.enabled&&!missing.installLaunched,"missing result reaches review only");
  missing.onActivityResult(REQUEST_SOURCES,0);check(missing.enabled,"late result does not disable ready review");
  int count=missing.validations;missing.onResume();check(missing.validations==count,"duplicate resume does not restart validation");
  LifecycleFixture denied=new LifecycleFixture();denied.activity.allowed=false;denied.beginSettings();denied.onPause();denied.onStop();denied.onResume();denied.finishValidation();
  check(denied.enabled&&denied.reviewNotice==R.string.apk_permission_denied&&!denied.installLaunched,"denial returns actionable review without install");
  LifecycleFixture expired=new LifecycleFixture();expired.beginSettings();expired.onPause();expired.pendingUntil=-1;expired.onResume();
  check(!expired.exists&&!expired.enabled,"expired review discarded");
  LifecycleFixture home=new LifecycleFixture();home.onPause();home.onStop();check(!home.exists,"ordinary background still cancels review");
  File root=java.nio.file.Files.createTempDirectory("review-state-").toFile();
  File dir=new File(root,"12345678-1234-1234-1234-123456789abc");dir.mkdir();File apk=new File(dir,"mooncast-apk-12345.apk");apk.createNewFile();
  try {
   LifecycleFixture saved=new LifecycleFixture();saved.root=root;saved.directory=dir;saved.pending=apk;saved.beginSettings();saved.onPause();
   android.os.Bundle bundle=new android.os.Bundle();saved.saveState(bundle);
   LifecycleFixture restored=new LifecycleFixture();restored.root=root;restored.pending=null;restored.restoreState(bundle);
   check(restored.pending!=null&&restored.pending.equals(apk)&&restored.external,"recreation restores only pending review");
   restored.onResume();restored.finishValidation();check(restored.enabled&&!restored.installLaunched,"restored return revalidates without auto-install");
   apk.delete();LifecycleFixture missingFile=new LifecycleFixture();missingFile.root=root;missingFile.pending=null;missingFile.restoreState(bundle);
   check(missingFile.expiredMessage&&missingFile.pending==null,"evicted review shows actionable reason");
   bundle.getBundle("apk-source-review").putString("file","../outside.apk");LifecycleFixture escape=new LifecycleFixture();escape.root=root;escape.pending=null;escape.restoreState(bundle);
   check(escape.expiredMessage&&escape.pending==null,"restoration rejects path traversal");
  }finally{apk.delete();dir.delete();root.delete();}
  System.out.println("PASS production lifecycle: early/missing/late Settings result, explicit review, expiry, ordinary background, saved review restoration and invalid-file feedback");
 }
}'''.replace('METHODS',methods)
java=Path(os.environ['JAVA_HOME'])/'bin' if os.environ.get('JAVA_HOME') else Path('/usr/bin')
with tempfile.TemporaryDirectory(prefix='apk-settings-lifecycle-') as temp:
    out=Path(temp);(out/'LifecycleFixture.java').write_text(fixture)
    bundle=out/'android/os/Bundle.java';bundle.parent.mkdir(parents=True);bundle.write_text('package android.os;public class Bundle extends java.util.HashMap<String,Object>{public void putString(String k,String v){put(k,v);}public String getString(String k,String d){Object v=get(k);return v instanceof String?(String)v:d;}public void putLong(String k,long v){put(k,v);}public long getLong(String k){Object v=get(k);return v instanceof Long?(Long)v:0;}public void putBundle(String k,Bundle v){put(k,v);}public Bundle getBundle(String k){return (Bundle)get(k);}}')
    clock=out/'android/os/SystemClock.java';clock.parent.mkdir(parents=True,exist_ok=True);clock.write_text('package android.os;public class SystemClock {public static long elapsedRealtime(){return 100;}}')
    subprocess.run([str(java/'javac'),'-source','8','-target','8','-d',temp,str(out/'LifecycleFixture.java'),str(clock),str(bundle),str(root/'tv/src/main/java/com/mooncast/tv/apk/ApkSettingsReturn.java')],check=True)
    subprocess.run([str(java/'java'),'-cp',temp,'LifecycleFixture'],check=True)
