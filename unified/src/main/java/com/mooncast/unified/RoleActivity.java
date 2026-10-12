package com.mooncast.unified;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.*;
import android.widget.TextView;
import java.util.ArrayList;

/** Single launcher; switches are transactions, committed only after cleanup acknowledgment. */
public class RoleActivity extends Activity {
 private final Handler main=new Handler();
 private final ArrayList<ServiceConnection> bindings=new ArrayList<>();
 private AlertDialog chooser;
 private boolean leaving,hostAck,fileAck;
 private int oldHostPid=-1,oldFilePid=-1;
 private String pending;
 private android.content.SharedPreferences prefs(){return getSharedPreferences("unified-role",MODE_PRIVATE);}
 private String read(String key,String fallback){try{return prefs().getString(key,fallback);}catch(ClassCastException e){return fallback;}}
 private boolean needsCleanup(){try{return prefs().getBoolean("pendingCleanup",false);}catch(ClassCastException e){return true;}}
 private boolean chinese(){return getResources().getConfiguration().getLocales().get(0).getLanguage().equals("zh");}
 private boolean television(){UiModeManager u=(UiModeManager)getSystemService(UI_MODE_SERVICE);return (u!=null&&u.getCurrentModeType()==Configuration.UI_MODE_TYPE_TELEVISION)||getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK);}
 @Override public void onCreate(Bundle b){super.onCreate(b);
  if(needsCleanup()&&(b==null||!b.getBoolean("choosing",false))){begin(read("pendingRole",read("role",RolePolicy.AUTO)),true);return;}
  route(getIntent());
 }
 @Override protected void onSaveInstanceState(Bundle b){b.putBoolean("choosing",chooser!=null);super.onSaveInstanceState(b);}
 @Override protected void onNewIntent(Intent i){super.onNewIntent(i);if(leaving||chooser!=null)return;setIntent(i);route(i);}
 private void route(Intent i){
  if(leaving||chooser!=null)return;
  String current=read("role",RolePolicy.AUTO);
  boolean settings=getClass()==RoleSettingsActivity.class;
  if(settings&&i.getBooleanExtra("require_cleanup",false)&&!needsCleanup())prefs().edit().putBoolean("pendingCleanup",true).putString("pendingRole",current).commit();
  if(needsCleanup()&&!settings){begin(read("pendingRole",current),true);return;}
  if(!settings){launch(RolePolicy.selected(current,television()));return;}
  String[] names=chinese()?new String[]{"自动选择（按设备）","手机 · 发送投屏","电视 · 接收投屏"}:new String[]{"Automatic (device type)","Phone · Send","TV · Receive"};
  int selected=RolePolicy.PHONE.equals(current)?1:RolePolicy.TV.equals(current)?2:0;
  chooser=new AlertDialog.Builder(this).setTitle(chinese()?"设备模式":"Device mode").setSingleChoiceItems(names,selected,(d,w)->{
   chooser=null;d.dismiss();begin(w==1?RolePolicy.PHONE:w==2?RolePolicy.TV:RolePolicy.AUTO,true);
  }).setNegativeButton(android.R.string.cancel,(d,w)->{chooser=null;begin(current,needsCleanup());}).setOnCancelListener(d->{chooser=null;begin(current,needsCleanup());}).create();chooser.show();
 }
 private void begin(String choice,boolean cleanup){
  if(leaving)return;leaving=true;pending=RolePolicy.valid(choice)?choice:RolePolicy.AUTO;
  if(!cleanup){launch(RolePolicy.selected(pending,television()));return;}
  // Separate pending keys survive relaunch; saved role changes only on successful commit.
  prefs().edit().putBoolean("pendingCleanup",true).putString("pendingRole",pending).commit();
  TextView text=new TextView(this);text.setText(chinese()?"正在停止旧模式…":"Stopping previous mode…");text.setTextSize(22);text.setPadding(32,64,32,32);setContentView(text);
  hostAck=false;fileAck=false;oldHostPid=-1;oldFilePid=-1;
  bindCleanup("RoleCleanupService",true);bindCleanup("FileRoleCleanupService",false);
  stopService(new Intent().setClassName(this,"com.mooncast.host.NativeHostService"));
  awaitStopped(SystemClock.elapsedRealtime()+12000);
 }
 private void bindCleanup(String name,boolean host){
  ServiceConnection[] holder=new ServiceConnection[1];
  Messenger reply=new Messenger(new Handler(getMainLooper(),m->{
   if(host){hostAck=true;if(m.getData().getBoolean("owned"))oldHostPid=m.getData().getInt("pid",-1);}else{fileAck=true;oldFilePid=m.getData().getInt("pid",-1);}
   releaseOne(holder[0]);return true;
  }));
  ServiceConnection connection=new ServiceConnection(){
   public void onServiceConnected(ComponentName c,IBinder binder){try{Message m=Message.obtain();m.replyTo=reply;new Messenger(binder).send(m);}catch(RemoteException ignored){}}
   public void onServiceDisconnected(ComponentName c){if(host)hostAck=true;else fileAck=true;releaseOne(holder[0]);}
  };
  holder[0]=connection;
  if(bindService(new Intent().setClassName(this,"com.mooncast.host."+name),connection,BIND_AUTO_CREATE))bindings.add(connection);
 }
 private void awaitStopped(long deadline){
  ActivityManager am=(ActivityManager)getSystemService(ACTIVITY_SERVICE);
  java.util.List<ActivityManager.RunningAppProcessInfo> processes=am==null?null:am.getRunningAppProcesses();
  boolean blocked=false;
  if(processes!=null)for(ActivityManager.RunningAppProcessInfo p:processes)if(p.uid==android.os.Process.myUid()){
   if(p.pid==oldFilePid||RoleGatePolicy.blocks(oldHostPid,p.pid,p.processName,getPackageName()))blocked=true;
  }
  boolean busy=!RoleGatePolicy.ready(hostAck,fileAck,processes!=null,blocked);
  if(busy){
   if(SystemClock.elapsedRealtime()<deadline){main.postDelayed(()->awaitStopped(deadline),100);return;}
   releaseBindings();leaving=true;
   new AlertDialog.Builder(this).setMessage(chinese()?"旧模式仍在停止，未切换。请稍后重试。":"Previous mode is still stopping. Mode was not changed; retry shortly.").setPositiveButton(android.R.string.ok,(d,w)->finish()).show();return;
  }
  releaseBindings();prefs().edit().putString("role",pending).remove("pendingRole").putBoolean("pendingCleanup",false).commit();
  launch(RolePolicy.selected(pending,television()));
 }
 private void releaseOne(ServiceConnection c){if(c!=null&&bindings.remove(c))try{unbindService(c);}catch(IllegalArgumentException ignored){}}
 private void releaseBindings(){for(ServiceConnection c:bindings)try{unbindService(c);}catch(IllegalArgumentException ignored){}bindings.clear();}
 private void launch(String role){String cls=RolePolicy.TV.equals(role)?"com.mooncast.tv.TvActivity":"com.mooncast.host.MainActivity";startActivity(new Intent().setClassName(this,cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));finish();}
 @Override protected void onDestroy(){main.removeCallbacksAndMessages(null);releaseBindings();if(chooser!=null){chooser.setOnCancelListener(null);chooser.dismiss();chooser=null;}super.onDestroy();}
}
