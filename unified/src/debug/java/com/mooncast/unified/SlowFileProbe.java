package com.mooncast.unified;
import android.app.Service;import android.content.Intent;import android.os.*;import java.io.*;import java.net.*;
/** Debug-only synthetic blocked document open, then delayed loopback listener creation. */
public final class SlowFileProbe extends Service {
 @Override public IBinder onBind(Intent intent){return new Messenger(new Handler(getMainLooper(),m->{
  File marker=new File(getCacheDir(),"role-slow-file-"+System.nanoTime());
  Message reply=Message.obtain();Bundle b=new Bundle();b.putString("marker",marker.getAbsolutePath());reply.setData(b);
  try{m.replyTo.send(reply);}catch(RemoteException ignored){}
  new Thread(()->{SystemClock.sleep(2000);try(ServerSocket socket=new ServerSocket(0,1,InetAddress.getLoopbackAddress())){try(FileOutputStream out=new FileOutputStream(marker)){out.write(1);}}catch(IOException ignored){}},"SlowDocumentFixture").start();return true;
 })).getBinder();}
}
