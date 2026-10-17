package com.mooncast.locale;
import android.app.Service;import android.content.Intent;import android.os.*;import android.util.AtomicFile;import java.io.*;import java.nio.charset.StandardCharsets;import java.util.concurrent.*;
/** Disposable two-process exercise of the real store and Android AtomicFile. */
public class DeviceNameFixtureService extends Service {
 private volatile boolean armed,writeDone,readStarted,readDone;private volatile String observed="",error="";private final CountDownLatch release=new CountDownLatch(1);private Thread worker;
 private final Messenger binder=new Messenger(new Handler(Looper.getMainLooper(),message->{Bundle out=new Bundle();out.putInt("nonce",message.getData().getInt("nonce"));try{
  if(message.what==1){DeviceName.save(this,"Generated old name");observed=DeviceName.get(this);}
  if(message.what==2){if(worker!=null)throw new IllegalStateException("duplicate worker");worker=new Thread(()->{try{DeviceName.withStorageLock(this,()->{AtomicFile target=DeviceName.file(this);FileOutputStream stream=target.startWrite();try{stream.write("Generated 新名字".getBytes(StandardCharsets.UTF_8));armed=true;try{if(!release.await(15,TimeUnit.SECONDS))throw new IOException("writer release timeout");}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IOException(interrupted);}target.finishWrite(stream);return null;}catch(IOException failure){target.failWrite(stream);throw failure;}});observed=DeviceName.get(this);writeDone=true;}catch(Throwable failure){error=failure.toString();}},"GeneratedNameWriter");worker.start();}
  if(message.what==3)release.countDown();
  if(message.what==4){if(worker!=null)throw new IllegalStateException("duplicate worker");worker=new Thread(()->{readStarted=true;try{observed=DeviceName.get(this);readDone=true;}catch(Throwable failure){error=failure.toString();}},"GeneratedNameReader");worker.start();}
  if(message.what==5){release.countDown();if(worker!=null){worker.join(5000);if(worker.isAlive())throw new IllegalStateException("name worker not retired");}stopSelf();}
  if(message.what==7){boolean failed=false;try{DeviceName.withStorageLock(this,()->{throw new IOException("generated lock failure");});}catch(IOException expected){failed=true;}if(!failed)throw new AssertionError("expected lock failure");DeviceName.save(this,"Generated final name");observed=DeviceName.get(this);}
  if(message.what==6)observed=DeviceName.get(this);
 }catch(Throwable failure){error=failure.toString();}
 out.putString("error",error);out.putString("name",observed);out.putInt("pid",android.os.Process.myPid());out.putBoolean("armed",armed);out.putBoolean("writeDone",writeDone);out.putBoolean("readStarted",readStarted);out.putBoolean("readDone",readDone);
 if(message.replyTo!=null)try{Message reply=Message.obtain();reply.setData(out);message.replyTo.send(reply);}catch(RemoteException ignored){}return true;}));
 @Override public IBinder onBind(Intent intent){return binder.getBinder();}
 @Override public void onDestroy(){release.countDown();super.onDestroy();}
}
