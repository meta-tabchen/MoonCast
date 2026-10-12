package com.mooncast.host;

import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.SystemClock;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Isolated preference/event checks plus bounded STREAM_MUSIC changes on an authorized test device. */
final class AudioPreferencesChecks {
    static String verify(Instrumentation test){
        preferences(test.getTargetContext());
        observerLifecycle(test.getTargetContext());
        return "PASS: missing-only audio/mute defaults, conservative migration/profile choices, generated observer/restore failures; "+systemVolume(test);
    }

    private static void preferences(Context target){
        String prefix="test-audio-defaults-"+System.nanoTime()+"-";
        List<SharedPreferences> opened=new ArrayList<>();
        Context isolated=new ContextWrapper(target){
            @Override public SharedPreferences getSharedPreferences(String name,int mode){
                SharedPreferences preferences=super.getSharedPreferences(prefix+name,mode);
                if(!opened.contains(preferences))opened.add(preferences);
                return preferences;
            }
        };
        try{
            SharedPreferences legacy=isolated.getSharedPreferences("legacy",0);
            ReceiverProfiles profiles=new ReceiverProfiles(isolated,legacy);
            for(int profile=0;profile<4;profile++){
                profiles.select(profile);
                check(profiles.settings().getBoolean("audio",false),"missing audio defaults enabled in each profile");
                check(profiles.settings().getBoolean("muteLocal",false),"missing mute defaults enabled in each profile");
                check(!profiles.settings().contains("audioUserChosen") && !profiles.settings().contains("muteLocalUserChosen"),"initialization is not a user choice");
            }
            profiles.select(1);
            ReceiverProfiles.recordAudioChoice(profiles.settings(),"audio",false);
            ReceiverProfiles.recordAudioChoice(profiles.settings(),"muteLocal",false);
            check(profiles.settings().getBoolean("audioUserChosen",false) && profiles.settings().getBoolean("muteLocalUserChosen",false),"real choices have independent markers");
            profiles.select(2);check(profiles.settings().getBoolean("audio",false),"second profile remains enabled");
            profiles=new ReceiverProfiles(isolated,legacy);profiles.select(1);
            check(!profiles.settings().getBoolean("audio",true) && !profiles.settings().getBoolean("muteLocal",true),"explicit false survives profile changes/recreation");

            // Old releases cannot tell automatic false writes from deliberate choices.
            SharedPreferences old=profiles.settings(3);
            old.edit().putBoolean("audio",false).putBoolean("muteLocal",false).remove("audioUserChosen").remove("muteLocalUserChosen").commit();
            profiles.select(3);profiles=new ReceiverProfiles(isolated,legacy);
            check(!profiles.settings().getBoolean("audio",true) && !profiles.settings().getBoolean("muteLocal",true),"unmarked existing false remains conservative");
            old.edit().remove("muteLocal").commit();
            check(!profiles.settings().getBoolean("audio",true) && profiles.settings().getBoolean("muteLocal",false),"only missing key receives new default");

            isolated.getSharedPreferences("receiver-profiles",0).edit().clear().commit();
            isolated.getSharedPreferences("profile-default",0).edit().clear().commit();
            legacy.edit().putBoolean("audio",false).putBoolean("muteLocal",false).commit();
            profiles=new ReceiverProfiles(isolated,legacy);
            check(!profiles.settings().getBoolean("audio",true) && !profiles.settings().getBoolean("muteLocal",true),"legacy false migration overrides freshly initialized defaults");
            check(!profiles.settings().contains("audioUserChosen") && !profiles.settings().contains("muteLocalUserChosen"),"legacy migration invents no user intent");
            legacy.edit().putBoolean("audio",true).commit();
            profiles=new ReceiverProfiles(isolated,legacy);
            check(!profiles.settings().getBoolean("audio",true),"migration is not repeated");
        }finally{for(SharedPreferences preferences:opened)preferences.edit().clear().commit();}
    }

    private static final class FakeVolume implements MediaVolumeSession.Device,MediaVolumeSession.Journal {
        int volume=7,writes;Integer saved;boolean rejectSave,ignoreRestore;
        public int volume(){return volume;}
        public void volume(int value){writes++;if(value>0 && ignoreRestore)return;volume=value;}
        public Integer saved(){return saved;}
        public void save(int value){if(rejectSave)throw new IllegalStateException("generated storage failure");saved=value;}
        public void clear(){saved=null;}
        MediaVolumeSession session(){return new MediaVolumeSession(this,this);}
    }
    private static final class ObserverContext extends ContextWrapper {
        BroadcastReceiver active;int registrations,removals;boolean rejectRegister;
        ObserverContext(Context target){super(target);}
        @Override public Context getApplicationContext(){return this;}
        @Override public Intent registerReceiver(BroadcastReceiver receiver,IntentFilter filter){return register(receiver);}
        @Override public Intent registerReceiver(BroadcastReceiver receiver,IntentFilter filter,int flags){return register(receiver);}
        private Intent register(BroadcastReceiver receiver){
            if(rejectRegister)throw new SecurityException("generated registration failure");
            check(active==null,"at most one volume observer");active=receiver;registrations++;return null;
        }
        @Override public void unregisterReceiver(BroadcastReceiver receiver){
            check(active==receiver,"only owned receiver is unregistered");active=null;removals++;
        }
        void event(int stream,int value){if(active!=null)active.onReceive(this,volumeEvent(stream,value));}
    }
    private static Intent volumeEvent(int stream,int value){
        return new Intent(LocalAudioMute.VOLUME_CHANGED).putExtra(LocalAudioMute.STREAM_TYPE,stream).putExtra(LocalAudioMute.STREAM_VALUE,value);
    }
    private static void observerLifecycle(Context target){
        ObserverContext context=new ObserverContext(target);FakeVolume volume=new FakeVolume();
        LocalAudioMute mute=new LocalAudioMute(context,volume.session(),()->false);
        try{
            mute.silence();mute.silence();check(context.registrations==1 && volume.volume==0,"single observer while audio is muted");
            context.event(AudioManager.STREAM_RING,3);context.event(AudioManager.STREAM_MUSIC,0);
            check(context.active!=null && volume.saved==7,"unrelated streams and own zero event keep recovery");
            BroadcastReceiver stale=context.active;
            mute.restore();check(context.active==null && volume.volume==7,"normal stop removes observer and restores volume");
            mute.silence();stale.onReceive(context,volumeEvent(AudioManager.STREAM_MUSIC,7));
            check(volume.saved==7 && context.active!=null,"stale prior observer cannot release new mute ownership");
            // Actual system volume is already zero when queued raise/zero messages arrive.
            volume.volume=0;context.event(AudioManager.STREAM_MUSIC,3);context.event(AudioManager.STREAM_MUSIC,0);
            mute.restore();mute.silence();
            check(volume.volume==0 && volume.saved==null && context.active==null,"queued manual raise/zero releases mute forever in this session");
            check(context.registrations==context.removals,"manual override/repeated cleanup leaks no observer");
        }finally{mute.restore();}

        context=new ObserverContext(target);volume=new FakeVolume();volume.rejectSave=true;
        mute=new LocalAudioMute(context,volume.session(),()->false);
        try{fails(mute::silence);check(volume.volume==7 && context.active==null,"save failure never mutes or retains observer");}
        finally{mute.restore();}

        context=new ObserverContext(target);volume=new FakeVolume();context.rejectRegister=true;
        mute=new LocalAudioMute(context,volume.session(),()->false);
        try{fails(mute::silence);check(volume.volume==7 && volume.saved==null && context.active==null,"observer setup failure does not mute");}
        finally{mute.restore();}

        context=new ObserverContext(target);volume=new FakeVolume();
        mute=new LocalAudioMute(context,volume.session(),()->false);
        try{
            mute.silence();volume.ignoreRestore=true;fails(mute::restore);
            check(context.active==null && volume.saved==7 && volume.volume==0,"failed stop removes observer but retains recovery");
            volume.ignoreRestore=false;volume.session().restore();
            check(volume.volume==7 && volume.saved==null,"new session recovers pending volume without observer");
        }finally{volume.ignoreRestore=false;mute.restore();}

        context=new ObserverContext(target);volume=new FakeVolume();
        mute=new LocalAudioMute(context,volume.session(),()->true);
        try{fails(mute::silence);check(context.registrations==0 && volume.volume==7,"fixed volume never registers or mutes");}
        finally{mute.restore();}
    }
    private static String systemVolume(Instrumentation test){
        Context target=test.getTargetContext();AudioManager manager=target.getSystemService(AudioManager.class);
        if(manager.isVolumeFixed())return "SKIP: real volume observation/restoration on fixed-volume device";
        int maximum=manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        if(maximum<1)return "SKIP: real volume observation/restoration without an adjustable media level";
        int original=manager.getStreamVolume(AudioManager.STREAM_MUSIC),baseline=Math.min(3,maximum);
        File directory=new File(target.getCacheDir(),"audio-recovery-test-"+System.nanoTime());
        check(directory.mkdir(),"isolated generated journal directory created");
        Context isolated=new ContextWrapper(target){@Override public File getFilesDir(){return directory;}};
        LocalAudioMute normal=new LocalAudioMute(isolated),manual=new LocalAudioMute(isolated);
        File recovery=new File(directory,"local-media-volume");
        try{
            manager.setStreamVolume(AudioManager.STREAM_MUSIC,baseline,0);
            check(manager.getStreamVolume(AudioManager.STREAM_MUSIC)==baseline,"nonzero real-volume baseline accepted");
            // Drain setup/restore notifications before starting the next observation epoch.
            SystemClock.sleep(100);test.waitForIdleSync();
            normal.silence();check(manager.getStreamVolume(AudioManager.STREAM_MUSIC)==0,"real local media mute applied");
            check(recovery.isFile(),"real mute has a durable recovery record");
            normal.restore();check(manager.getStreamVolume(AudioManager.STREAM_MUSIC)==baseline,"normal stop restores real original volume");
            check(!recovery.exists(),"successful real stop clears recovery record");
            SystemClock.sleep(100);test.waitForIdleSync();
            manual.silence();check(recovery.isFile() && manager.getStreamVolume(AudioManager.STREAM_MUSIC)==0,"second real observation epoch owns mute");
            manager.setStreamVolume(AudioManager.STREAM_MUSIC,baseline,0);
            long deadline=SystemClock.elapsedRealtime()+1000;
            while(recovery.exists() && SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(20);
            check(!recovery.exists(),"real broadcast/poll observes manual volume within one second");
            manager.setStreamVolume(AudioManager.STREAM_MUSIC,0,0);
            manual.restore();
            check(manager.getStreamVolume(AudioManager.STREAM_MUSIC)==0,"real manual raise then zero is preserved on stop");
            manager.setStreamVolume(AudioManager.STREAM_MUSIC,baseline,0);manual.silence();
            check(manager.getStreamVolume(AudioManager.STREAM_MUSIC)==baseline && !recovery.exists(),"same-session reconnect never reacquires overridden volume");
            return "PASS: actual AudioManager mute/restore and real observed raise-to-zero override; original test volume restored";
        }finally{
            try{normal.restore();}finally{
                try{manual.restore();}finally{
                    manager.setStreamVolume(AudioManager.STREAM_MUSIC,original,0);
                    File[] files=directory.listFiles();if(files!=null)for(File file:files)file.delete();directory.delete();
                    check(manager.getStreamVolume(AudioManager.STREAM_MUSIC)==original,"pre-test system volume restored");
                }
            }
        }
    }
    private static void fails(Runnable action){try{action.run();throw new AssertionError("expected failure");}catch(RuntimeException expected){}}
    private static void check(boolean pass,String message){if(!pass)throw new AssertionError(message);}
}
