package com.mooncast.unified;
import android.app.*;import android.content.*;import android.os.*;import java.util.concurrent.*;
/** Actual Android process/ELF-load checks; no projection permission or network identity. */
public final class UnifiedInstrumentation extends Instrumentation {
 private Bundle arguments;
 @Override public void onCreate(Bundle b){super.onCreate(b);arguments=b;start();}
 @Override public void onStart(){Bundle result=new Bundle();try{
  if(arguments!=null&&"gamestream-native".equals(arguments.getString("suite"))){new GameStreamNativeDeviceTest(this,arguments).run();return;}
  if(arguments!=null&&"airplay-receiver".equals(arguments.getString("suite"))){new AirPlayReceiverDeviceTest(this,arguments).run();return;}
  if(arguments!=null&&("tv".equals(arguments.getString("suite"))||"apk".equals(arguments.getString("suite")))){
   Class<?> type=Class.forName("apk".equals(arguments.getString("suite"))?"com.mooncast.tv.ApkReceiveDeviceTest":"com.mooncast.tv.TvUiTestDriver");java.lang.reflect.Constructor<?> constructor=type.getDeclaredConstructor(Instrumentation.class,Bundle.class);constructor.setAccessible(true);Object driver=constructor.newInstance(this,arguments);java.lang.reflect.Method run=type.getDeclaredMethod("run");run.setAccessible(true);run.invoke(driver);return;
  }

  if(!RolePolicy.TV.equals(RolePolicy.selected("auto",true))||!RolePolicy.PHONE.equals(RolePolicy.selected("broken",false))||!RolePolicy.PHONE.equals(RolePolicy.selected("phone",true)))throw new AssertionError("role policy");
  String sender=probe("SenderNativeProbe"),receiver=probe("ReceiverNativeProbe");
  StringBuilder appReceiver=new StringBuilder();for(String line:receiver.split("\n"))if(line.contains(getTargetContext().getApplicationInfo().nativeLibraryDir))appReceiver.append(line).append("\n");
  String receiverApp=appReceiver.toString();
  result.putString("receiverAppMaps",receiverApp);
  require(sender.contains("libsunshine.so")&&sender.contains("libcrypto.so")&&sender.contains("libgojni.so"),"sender loaded correct libraries");
  require(!sender.contains("libtvcryp.so")&&!sender.contains("libairplay_native.so"),"sender isolation");
  require(receiver.contains("libairplay_native.so")&&receiver.contains("libtvcryp.so")&&receiver.contains("libmoonlight-core.so"),"receiver loaded correct libraries");
  require(!receiverApp.contains("/libcrypto.so")&&!receiverApp.contains("libsunshine.so"),"receiver isolation");
  fileCleanupCheck();
  result.putString("stream","PASS slow-file old-fail/new-pass process cleanup; actual Android sender/receiver JNI loads, isolated OpenSSL maps and role policy. No capture/encoded playback/physical interoperability claim.\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}}
 private void fileCleanupCheck()throws Exception {
  Bundle baseline=message("com.mooncast.unified.SlowFileProbe");java.io.File old=new java.io.File(baseline.getString("marker"));
  SystemClock.sleep(2500);require(old.isFile(),"Baseline should demonstrate delayed listener after early flag acknowledgment");old.delete();
  Bundle candidate=message("com.mooncast.unified.SlowFileProbe");java.io.File prevented=new java.io.File(candidate.getString("marker"));
  Bundle reply=message("com.mooncast.host.FileRoleCleanupService");int pid=reply.getInt("pid",-1);require(pid>0,"cleanup PID acknowledgment");
  SystemClock.sleep(2500);require(!prevented.exists(),"Delayed listener survived file-role cleanup");
  ActivityManager am=(ActivityManager)getTargetContext().getSystemService(Context.ACTIVITY_SERVICE);
  for(ActivityManager.RunningAppProcessInfo proc:am.getRunningAppProcesses())require(proc.pid!=pid,"Old files process survived cleanup");
 }
 private Bundle message(String cls)throws Exception {
  Context c=getTargetContext();CountDownLatch done=new CountDownLatch(1);Bundle[] answer={null};
  Messenger reply=new Messenger(new Handler(Looper.getMainLooper(),m->{answer[0]=m.getData();done.countDown();return true;}));
  ServiceConnection connection=new ServiceConnection(){public void onServiceConnected(ComponentName n,IBinder b){try{Message m=Message.obtain();m.replyTo=reply;new Messenger(b).send(m);}catch(RemoteException e){done.countDown();}}public void onServiceDisconnected(ComponentName n){}};
  require(c.bindService(new Intent().setClassName(c.getPackageName(),cls),connection,Context.BIND_AUTO_CREATE),"fixture bind");
  try{require(done.await(20,TimeUnit.SECONDS)&&answer[0]!=null,"fixture response");return answer[0];}finally{c.unbindService(connection);}
 }
 private static void require(boolean yes,String what){if(!yes)throw new AssertionError(what);}
 private String probe(String cls)throws Exception{
  CountDownLatch done=new CountDownLatch(1);Bundle[] out={null};Context c=getTargetContext();
  Messenger answer=new Messenger(new Handler(Looper.getMainLooper(),m->{out[0]=m.getData();done.countDown();return true;}));
  ServiceConnection connection=new ServiceConnection(){public void onServiceConnected(ComponentName n,IBinder b){try{Message m=Message.obtain();m.replyTo=answer;new Messenger(b).send(m);}catch(RemoteException e){done.countDown();}}public void onServiceDisconnected(ComponentName n){}};
  require(c.bindService(new Intent().setClassName(c.getPackageName(),"com.mooncast.unified."+cls),connection,Context.BIND_AUTO_CREATE),"bind "+cls);
  try{require(done.await(60,TimeUnit.SECONDS),"probe timeout "+cls);require(out[0]!=null&&out[0].getBoolean("ok"),out[0]==null?"missing result":out[0].getString("error"));return out[0].getString("maps");}finally{c.unbindService(connection);}
 }
}
