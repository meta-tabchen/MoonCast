package com.mooncast.host;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.provider.MediaStore;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

final class FileCinemaChecks {
    static void verify(Instrumentation test) throws Exception {
        Context c=test.getTargetContext();Activity activity=test.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        ContentValues values=new ContentValues();values.put(MediaStore.Images.Media.DISPLAY_NAME,"mooncast-generated-cinema-test.png");values.put(MediaStore.Images.Media.MIME_TYPE,"image/png");if(Build.VERSION.SDK_INT>=29)values.put(MediaStore.Images.Media.IS_PENDING,1);
        var uri=c.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);if(uri==null)throw new AssertionError("no media test document");
        Bitmap bitmap=Bitmap.createBitmap(80,60,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.BLUE);ByteArrayOutputStream bytes=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.PNG,100,bytes);bitmap.recycle();byte[] original=bytes.toByteArray();
        AtomicReference<Messenger> remote=new AtomicReference<>();AtomicReference<Bundle> snapshot=new AtomicReference<>();
        Messenger reply=new Messenger(new Handler(Looper.getMainLooper(),m->{snapshot.set(m.getData());return true;}));
        ServiceConnection connection=new ServiceConnection(){public void onServiceConnected(ComponentName n,IBinder b){remote.set(new Messenger(b));}public void onServiceDisconnected(ComponentName n){remote.set(null);}};
        boolean bound=false;int port=0;String path="";
        try{
            try(OutputStream out=c.getContentResolver().openOutputStream(uri)){out.write(original);}
            if(Build.VERSION.SDK_INT>=29){ContentValues published=new ContentValues();published.put(MediaStore.Images.Media.IS_PENDING,0);c.getContentResolver().update(uri,published,null,null);}
            c.startForegroundService(new Intent(c,FileCinemaService.class).setData(uri));
            bound=c.bindService(new Intent(c,FileCinemaService.class),connection,Context.BIND_AUTO_CREATE);
            long deadline=SystemClock.elapsedRealtime()+10000;
            while(SystemClock.elapsedRealtime()<deadline){if(remote.get()!=null){Message m=Message.obtain(null,FileCinemaService.STATUS);m.replyTo=reply;remote.get().send(m);}Bundle s=snapshot.get();if(s!=null && !s.getString("error","").isEmpty())throw new AssertionError(s.getString("error"));if(s!=null && !s.getString("urls","").isEmpty())break;Thread.sleep(100);}
            Bundle status=snapshot.get();if(status==null || status.getString("urls","").isEmpty())throw new AssertionError("no sharing link");
            URL url=new URL(status.getString("urls").split("\n")[0]);port=url.getPort();path=url.getPath();
            HttpURLConnection full=(HttpURLConnection)new URL("http://127.0.0.1:"+port+path+"media").openConnection();full.setReadTimeout(3000);
            byte[] received;try(InputStream in=full.getInputStream()){received=read(in);}finally{full.disconnect();}
            if(!Arrays.equals(original,received))throw new AssertionError("SAF original bytes changed");
            HttpURLConnection part=(HttpURLConnection)new URL("http://127.0.0.1:"+port+path+"media").openConnection();part.setRequestProperty("Range","bytes=8-31");
            try(InputStream in=part.getInputStream()){if(part.getResponseCode()!=206 || !Arrays.equals(Arrays.copyOfRange(original,8,32),read(in)))throw new AssertionError("SAF range");}finally{part.disconnect();}
            remote.get().send(Message.obtain(null,FileCinemaService.STOP));Thread.sleep(300);
            try(Socket ignored=new Socket("127.0.0.1",port)){throw new AssertionError("stop did not revoke link");}catch(ConnectException expected){}
        }finally{
            if(remote.get()!=null)try{remote.get().send(Message.obtain(null,FileCinemaService.STOP));}catch(RemoteException ignored){}
            if(bound)c.unbindService(connection);c.getContentResolver().delete(uri,null,null);test.runOnMainSync(activity::finish);
        }
    }
    private static byte[] read(InputStream in) throws IOException {ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);return out.toByteArray();}
}
