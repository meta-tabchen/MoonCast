package com.mooncast.unified;
import android.app.Service;import android.content.Intent;import android.os.*;import java.nio.file.*;
/** Debug-only, private native-load probe. Does not start capture/network services. */
public abstract class NativeProbe extends Service {
 protected abstract void load();
 @Override public IBinder onBind(Intent intent){return new Messenger(new Handler(getMainLooper(),m->{
  Message reply=Message.obtain();Bundle result=new Bundle();
  try{load();String maps=new String(Files.readAllBytes(Paths.get("/proc/self/maps")),java.nio.charset.StandardCharsets.UTF_8);result.putString("maps",maps);result.putBoolean("ok",true);}catch(Throwable e){result.putString("error",android.util.Log.getStackTraceString(e));}
  reply.setData(result);try{m.replyTo.send(reply);}catch(RemoteException ignored){}return true;
 })).getBinder();}
}
