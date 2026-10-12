package com.mooncast.host;
import android.app.*;import android.content.Intent;import android.os.*;
/** Private :files process boundary: a blocked document-provider open cannot outlive a role switch. */
public final class FileRoleCleanupService extends Service {
 @Override public IBinder onBind(Intent intent){return new Messenger(new Handler(Looper.getMainLooper(),m->{
  if(m.replyTo==null)return true;
  stopService(new Intent(this,FileCinemaService.class));
  Message reply=Message.obtain();Bundle b=new Bundle();b.putInt("pid",android.os.Process.myPid());reply.setData(b);
  try{m.replyTo.send(reply);}catch(RemoteException ignored){}
  new Thread(()->{
   SystemClock.sleep(300);
   ActivityManager am=(ActivityManager)getSystemService(ACTIVITY_SERVICE);
   if(am!=null&&am.getRunningAppProcesses()!=null)for(ActivityManager.RunningAppProcessInfo p:am.getRunningAppProcesses())
    if(p.pid==android.os.Process.myPid()&&p.uid==android.os.Process.myUid()&&p.processName.equals(getPackageName()+":files"))android.os.Process.killProcess(p.pid);
  },"FilesRoleExit").start();return true;
 })).getBinder();}
}
