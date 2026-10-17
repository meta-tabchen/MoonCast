package com.mooncast.host;
import android.app.Service;import android.content.Intent;import android.os.*;
/** Debug-only window-owner process. Never controls physical panel power or saved brightness. */
public final class DimFixtureService extends Service {
 private PowerManager.WakeLock bright;private ScreenDimmer dimmer;private final Handler main=new Handler(Looper.getMainLooper());
 private final Messenger binder=new Messenger(new Handler(Looper.getMainLooper(),m->{
  if(dimmer==null)dimmer=new ScreenDimmer(this,()->{});
  if(m.what==1)dimmer.request();else if(m.what==2)dimmer.restore();else if(m.what==3){dimmer.close();dimmer=null;stopSelf();}
  Bundle b=new Bundle();b.putInt("nonce",m.getData().getInt("nonce"));b.putInt("pid",android.os.Process.myPid());b.putBoolean("brightWakeLock",bright!=null&&bright.isHeld());b.putBoolean("active",dimmer!=null&&dimmer.active());b.putBoolean("dimmed",dimmer!=null&&dimmer.dimmed());
  if(m.replyTo!=null)try{Message reply=Message.obtain();reply.setData(b);m.replyTo.send(reply);}catch(RemoteException ignored){}
  if(m.what==4)main.postDelayed(()->android.os.Process.killProcess(android.os.Process.myPid()),100);
  return true;
 }));
 @Override public void onCreate(){super.onCreate();bright=getSystemService(PowerManager.class).newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK,"MoonCast:GeneratedManualBright");bright.setReferenceCounted(false);bright.acquire(10*60*1000L);}
 @Override public IBinder onBind(Intent i){return binder.getBinder();}
 @Override public int onStartCommand(Intent i,int f,int s){return START_NOT_STICKY;}
 @Override public void onDestroy(){if(dimmer!=null)dimmer.close();main.removeCallbacksAndMessages(null);if(bright!=null&&bright.isHeld())bright.release();super.onDestroy();}
}
