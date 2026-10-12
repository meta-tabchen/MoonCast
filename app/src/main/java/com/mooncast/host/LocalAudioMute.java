package com.mooncast.host;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.AtomicFile;
import android.util.Log;
import java.io.*;
import java.util.function.BooleanSupplier;

/** Only adjusts STREAM_MUSIC, leaving capture samples and other stream settings untouched. */
final class LocalAudioMute {
    // AOSP's internal stream-volume broadcast includes each change's value. OEM delivery
    // is not a public SDK guarantee; restore also checks the current system volume.
    static final String VOLUME_CHANGED="android.media.VOLUME_CHANGED_ACTION";
    static final String STREAM_TYPE="android.media.EXTRA_VOLUME_STREAM_TYPE";
    static final String STREAM_VALUE="android.media.EXTRA_VOLUME_STREAM_VALUE";
    private final Context context;
    private final MediaVolumeSession session;
    private final BooleanSupplier volumeFixed;
    private final Handler monitor=new Handler(Looper.getMainLooper());
    private BroadcastReceiver volumeChanges;
    private final Runnable sampleVolume=new Runnable(){
        @Override public void run(){
            synchronized(LocalAudioMute.this){
                if(volumeChanges==null)return;
                try{session.observeCurrentVolume();}
                catch(RuntimeException failure){Log.w("MoonCast","Volume observation failed",failure);}
                if(session.ownsVolume())monitor.postDelayed(this,500);
                else stopObserving();
            }
        }
    };

    private void startObserving(){
        if(volumeChanges!=null)return;
        BroadcastReceiver receiver=new BroadcastReceiver(){
            @Override public void onReceive(Context ignored,Intent event){
                synchronized(LocalAudioMute.this){
                    // A queued event from a previous mute/restore cannot release new ownership.
                    if(volumeChanges!=this || !VOLUME_CHANGED.equals(event.getAction()) ||
                        event.getIntExtra(STREAM_TYPE,-1)!=AudioManager.STREAM_MUSIC)return;
                    try{session.volumeChanged(event.getIntExtra(STREAM_VALUE,-1));}
                    catch(RuntimeException failure){Log.w("MoonCast","Volume override recovery cleanup pending",failure);}
                    finally{if(!session.ownsVolume())stopObserving();}
                }
            }
        };
        IntentFilter filter=new IntentFilter(VOLUME_CHANGED);
        if(Build.VERSION.SDK_INT>=33)context.registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED);
        else context.registerReceiver(receiver,filter);
        volumeChanges=receiver;
    }

    LocalAudioMute(Context context){this(context,context.getSystemService(AudioManager.class));}
    private LocalAudioMute(Context context,AudioManager manager){
        this(context,createSession(context,manager),manager::isVolumeFixed);
    }
    /** Injectable volume/journal for generated lifecycle checks without changing system volume. */
    LocalAudioMute(Context context,MediaVolumeSession session,BooleanSupplier volumeFixed){
        Context app=context.getApplicationContext();
        this.context=app==null?context:app;this.session=session;this.volumeFixed=volumeFixed;
    }
    private static MediaVolumeSession createSession(Context context,AudioManager manager){
        AtomicFile file=new AtomicFile(new File(context.getFilesDir(),"local-media-volume"));
        return new MediaVolumeSession(new MediaVolumeSession.Device(){
            public int volume(){return manager.getStreamVolume(AudioManager.STREAM_MUSIC);}
            public void volume(int value){manager.setStreamVolume(AudioManager.STREAM_MUSIC,value,0);}
        },new MediaVolumeSession.Journal(){
            public Integer saved(){
                try(DataInputStream in=new DataInputStream(file.openRead())){
                    int value=in.readInt();
                    if(value<0 || value>manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC))throw new IOException("音量恢复记录无效");
                    return value;
                }catch(FileNotFoundException e){return null;}
                catch(IOException e){throw new IllegalStateException("读取音量恢复记录失败",e);}
            }
            public void save(int volume){
                FileOutputStream stream=null;
                try{
                    stream=file.startWrite();new DataOutputStream(stream).writeInt(volume);file.finishWrite(stream);stream=null;
                    Integer recorded=saved();
                    if(recorded==null || recorded!=volume)throw new IOException("音量恢复记录未保存");
                }
                catch(IOException e){if(stream!=null)file.failWrite(stream);throw new IllegalStateException("保存音量失败，未静音手机",e);}
            }
            public void clear(){
                file.delete();
                if(saved()!=null)throw new IllegalStateException("音量恢复记录未清除");
            }
        });
    }
    synchronized void silence(){
        if(volumeFixed.getAsBoolean())throw new IllegalStateException("设备使用固定媒体音量");
        try{
            startObserving();
            session.silence();
            if(session.ownsVolume()){
                monitor.removeCallbacks(sampleVolume);
                monitor.postDelayed(sampleVolume,500);
            }
        }finally{if(!session.ownsVolume())stopObserving();}
    }
    synchronized void restore(){
        // Drop the observer even when restoration fails; the journal owns later recovery.
        try{session.restore();}finally{stopObserving();}
    }
    private void stopObserving(){
        monitor.removeCallbacks(sampleVolume);
        BroadcastReceiver receiver=volumeChanges;volumeChanges=null;
        if(receiver==null)return;
        try{context.unregisterReceiver(receiver);}
        catch(IllegalArgumentException ignored){/* Already removed by context teardown. */}
    }
}
