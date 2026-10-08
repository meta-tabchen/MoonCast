package com.mooncast.host;

import android.content.*;
import android.os.*;
import android.view.Surface;
import io.github.jqssun.displaymirror.sunshine.SunshineServer;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Owns one native-process generation; old callbacks cannot attach to a new generation. */
final class NativeHostConnection implements AutoCloseable {
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final SunshineServer.Listener listener;
    private final String name;
    private final boolean hevc;
    private final java.util.function.IntFunction<float[]> audio;
    private final Runnable lost;
    private final Runnable ready;
    private final Set<Long> active=ConcurrentHashMap.newKeySet();
    private final java.util.Map<Long,Surface> surfaces=new ConcurrentHashMap<>();
    private volatile INativeHost remote;
    private volatile int generation;
    private boolean bound,closed,restarting;
    NativeHostConnection(Context c,String name,boolean hevc,SunshineServer.Listener listener,java.util.function.IntFunction<float[]> audio,Runnable lost,Runnable ready){this.context=c;this.name=name;this.hevc=hevc;this.listener=listener;this.audio=audio;this.lost=lost;this.ready=ready;}
    private final ServiceConnection connection=new ServiceConnection(){
        public void onServiceConnected(ComponentName n,IBinder binder){
            if(closed)return;remote=INativeHost.Stub.asInterface(binder);restarting=false;
            int current=++generation;
            try{remote.start(name,hevc,callbacks(current));new Thread(()->awaitReady(current),"NativeHostReady").start();}catch(RemoteException e){listener.error("Native host binding failed");}
        }
        public void onServiceDisconnected(ComponentName n){remote=null;if(closed)return;++generation;active.clear();lost.run();releaseSurfaces();rebind();}
        public void onBindingDied(ComponentName n){onServiceDisconnected(n);}
        public void onNullBinding(ComponentName n){listener.error("Native host unavailable");}
    };
    void start(){main.post(this::bind);}
    private void awaitReady(int expected){
        long end=SystemClock.elapsedRealtime()+15000;
        while(!closed && generation==expected && SystemClock.elapsedRealtime()<end){
            java.net.HttpURLConnection request=null;
            try{
                request=(java.net.HttpURLConnection)new java.net.URL("http://127.0.0.1:47989/serverinfo?uniqueid=mooncast-ready").openConnection();
                request.setConnectTimeout(250);request.setReadTimeout(250);
                if(request.getResponseCode()==200){main.post(()->{if(!closed && generation==expected)ready.run();});return;}
            }catch(java.io.IOException ignored){}finally{if(request!=null)request.disconnect();}
            SystemClock.sleep(100);
        }
        if(!closed && generation==expected)listener.error("Native transport restart timed out");
    }
    private void bind(){if(closed || bound)return;bound=context.bindService(new Intent(context,NativeHostService.class),connection,Context.BIND_AUTO_CREATE);if(!bound)listener.error("Cannot bind native host");}
    private void rebind(){if(bound){context.unbindService(connection);bound=false;}main.postDelayed(this::bind,250);}
    boolean active(long session){return active.contains(session);}
    void pin(String pin){INativeHost r=remote;if(r!=null)try{r.pin(pin);}catch(RemoteException ignored){}}
    void disconnect(long session){INativeHost r=remote;if(r!=null)try{r.disconnect(session);}catch(RemoteException ignored){}}
    void audio(int frames){INativeHost r=remote;if(r!=null)try{r.audio(frames);}catch(RemoteException e){listener.error("Native audio process unavailable");}}
    void restart(){if(closed || restarting)return;restarting=true;++generation;active.clear();INativeHost r=remote;if(r!=null)try{r.shutdown();}catch(RemoteException ignored){}else rebind();}
    private INativeHostCallbacks callbacks(int expected){return new INativeHostCallbacks.Stub(){
        private boolean valid(){return !closed && generation==expected;}
        public void createDisplay(long s,int w,int h,int f,int p,Surface surface,boolean captureAudio){
            if(!valid()){surface.release();return;}surfaces.put(s,surface);active.add(s);listener.createDisplay(s,w,h,f,p,surface,captureAudio);
        }
        public void stopDisplay(long s){if(!valid())return;active.remove(s);try{listener.stopDisplay(s);}finally{Surface surface=surfaces.remove(s);if(surface!=null)surface.release();}}
        public void pinRequested(){if(valid())listener.pinRequested();}
        public void error(String text){if(valid())listener.error(text);}
        public void uuid(String uuid){if(valid())listener.uuid(uuid);}
        public float[] readAudio(int count){if(!valid() || count<1 || count>96000)return null;return audio.apply(count);}
        private InputBridge.Receiver input(){return valid()?InputBridge.receiver:null;}
        public void touch(long s,int t,int p,float x,float y,float pressure){InputBridge.Receiver r=input();if(r!=null)r.touch(s,t,p,x,y,pressure);}
        public void mouse(long s,float x,float y,float w,float h){InputBridge.Receiver r=input();if(r!=null)r.mouse(s,x,y,w,h);}
        public void relative(long s,int x,int y){InputBridge.Receiver r=input();if(r!=null)r.relative(s,x,y);}
        public void button(long s,boolean release){InputBridge.Receiver r=input();if(r!=null)r.button(s,release);}
        public void key(int key,boolean release,int flags){InputBridge.Receiver r=input();if(r!=null)r.key(key,release,flags);}
    };}
    private void releaseSurfaces(){for(Surface surface:surfaces.values())surface.release();surfaces.clear();}
    @Override public void close(){closed=true;++generation;active.clear();INativeHost r=remote;remote=null;if(r!=null)try{r.shutdown();}catch(RemoteException ignored){}if(bound){context.unbindService(connection);bound=false;}releaseSurfaces();}
}
