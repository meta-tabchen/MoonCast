package com.mooncast.host;

import android.app.Service;
import android.content.Intent;
import android.os.*;
import android.view.Surface;
import io.github.jqssun.displaymirror.sunshine.NativeSessions;
import io.github.jqssun.displaymirror.sunshine.SunshineServer;
import java.io.File;

/** Process-local pinned native runtime. Capture permission stays in :host across restarts. */
public final class NativeHostService extends Service implements SunshineServer.Listener, InputBridge.Receiver {
    private volatile INativeHostCallbacks owner;
    private boolean started, audioStarted;
    private final INativeHost.Stub binder=new INativeHost.Stub(){
        public synchronized void start(String name,boolean hevc,INativeHostCallbacks callbacks)throws RemoteException{
            if(started)return;started=true;owner=callbacks;
            callbacks.asBinder().linkToDeath(NativeHostService.this::exit,0);
            new Thread(()->{
                try{
                    HostIdentity.ensure(getFilesDir());SunshineServer.listener=NativeHostService.this;
                    InputBridge.receiver=NativeHostService.this;
                    SunshineServer.setSunshineName(name);
                    SunshineServer.setCertPath(new File(getFilesDir(),"host-cert.pem").getPath());
                    SunshineServer.setPkeyPath(new File(getFilesDir(),"host-key.pem").getPath());
                    SunshineServer.setFileStatePath(new File(getFilesDir(),"paired-clients.json").getPath());
                    SunshineServer.setHevcSupported(hevc);
                    if(!NativeSessions.available())throw new IllegalStateException("Native session bridge unavailable");
                    SunshineServer.start();
                }catch(Exception|LinkageError e){error("Native host start failed: "+e.getMessage());}
            },"NativeHost").start();
        }
        public void pin(String pin){if(pin.matches("[0-9]{4}"))new Thread(()->SunshineServer.submitPin(pin),"Pairing").start();}
        public boolean disconnect(long session){return NativeSessions.disconnect(session);}
        public synchronized void audio(int frames){if(audioStarted)return;audioStarted=true;SunshineServer.startAudioRecording(new RemoteAudio(),frames);}
        public void shutdown(){exit();}
    };
    private interface Call {void run(INativeHostCallbacks callback)throws RemoteException;}
    private void call(Call action){INativeHostCallbacks callback=owner;if(callback!=null)try{action.run(callback);}catch(RemoteException e){exit();}}
    @Override public IBinder onBind(Intent intent){return binder;}
    private void exit(){new Thread(()->{SystemClock.sleep(100);android.os.Process.killProcess(android.os.Process.myPid());},"NativeHostExit").start();}
    @Override public void onDestroy(){exit();super.onDestroy();}
    public void createDisplay(long session,int w,int h,int fps,int packetMs,Surface surface,boolean audio){call(c->c.createDisplay(session,w,h,fps,packetMs,surface,audio));}
    public void stopDisplay(long session){call(c->c.stopDisplay(session));}
    public void pinRequested(){call(INativeHostCallbacks::pinRequested);}
    public void error(String text){call(c->c.error(text));}
    public void uuid(String uuid){call(c->c.uuid(uuid));}
    public void touch(long s,int t,int p,float x,float y,float pressure){call(c->c.touch(s,t,p,x,y,pressure));}
    public void mouse(long s,float x,float y,float w,float h){call(c->c.mouse(s,x,y,w,h));}
    public void relative(long s,int x,int y){call(c->c.relative(s,x,y));}
    public void button(long s,boolean release){call(c->c.button(s,release));}
    public void key(int key,boolean release,int flags){call(c->c.key(key,release,flags));}
    /** Same read signature as AudioRecord/AudioFeed, consumed by the upstream JNI audio pump. */
    public final class RemoteAudio {
        public int read(float[] buffer,int offset,int count,int mode){
            INativeHostCallbacks callback=owner;
            try{if(callback!=null){float[] data=callback.readAudio(count);if(data!=null && data.length==count){System.arraycopy(data,0,buffer,offset,count);return count;}}}
            catch(RemoteException e){exit();}
            java.util.Arrays.fill(buffer,offset,offset+count,0f);SystemClock.sleep(Math.max(1,count*1000L/96000));return count;
        }
    }
}
