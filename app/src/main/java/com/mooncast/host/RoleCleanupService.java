package com.mooncast.host;
import android.app.Service;import android.content.Intent;import android.os.*;
/** Private :host handshake. A capture lease remains held until its owning PID exits. */
public final class RoleCleanupService extends Service {
    @Override protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(com.mooncast.locale.AppLocale.wrap(base));
    }

 private final Handler main=new Handler(Looper.getMainLooper());
 @Override public IBinder onBind(Intent intent){return new Messenger(new Handler(Looper.getMainLooper(),m->{
  if(m.replyTo==null)return true;
  boolean owned=SenderOwnership.isHeld();
  stopService(new Intent(this,HostService.class));stopService(new Intent(this,AirPlayHostService.class));stopService(new Intent(this,BrowserHostService.class));
  Message result=Message.obtain();Bundle b=new Bundle();b.putInt("pid",android.os.Process.myPid());b.putBoolean("owned",owned);result.setData(b);
  try{m.replyTo.send(result);}catch(RemoteException ignored){}return true;
 })).getBinder();}
}
