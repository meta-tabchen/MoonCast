package com.mooncast.host;

public final class MediaVolumeSessionTest {
    static final class Fake implements MediaVolumeSession.Device,MediaVolumeSession.Journal {
        int volume=7,changes;Integer saved;
        boolean rejectSave,rejectMute,rejectRestore,ignoreRestore,rejectClear;
        public int volume(){return volume;}
        public void volume(int value){
            changes++;
            if(value==0 && rejectMute || value>0 && rejectRestore)throw new IllegalStateException("denied");
            if(value>0 && ignoreRestore)return;
            volume=value;
        }
        public Integer saved(){return saved;}
        public void save(int value){if(rejectSave)throw new IllegalStateException("disk full");saved=value;}
        public void clear(){if(rejectClear)throw new IllegalStateException("delete denied");saved=null;}
        MediaVolumeSession session(){return new MediaVolumeSession(this,this);}
    }
    public static void main(String[] args){
        Fake f=new Fake();var s=f.session();s.silence();s.silence();
        check(f.volume==0 && f.saved==7 && f.changes==1 && s.ownsVolume(),"mute once, remember original");
        s.volumeChanged(0);check(s.ownsVolume(),"own mute notification keeps ownership");
        s.restore();s.restore();check(f.volume==7 && f.saved==null && f.changes==2 && !s.ownsVolume(),"stop/error restores once");
        s.volumeChanged(7);s.silence();s.restore();check(f.changes==4,"later ordinary audio reconnect may mute again");

        f=new Fake();s=f.session();s.silence();f.session().restore();check(f.volume==7 && f.saved==null,"new process recovers journal");
        f=new Fake();s=f.session();s.silence();f.volume=3;s.restore();check(f.volume==3 && f.saved==null,"respect user volume change without notification");
        f.volume=0;s.silence();check(f.volume==0 && f.saved==null,"fallback override also prevents reacquisition");

        f=new Fake();s=f.session();s.silence();f.volume=0;s.volumeChanged(3);s.volumeChanged(0);s.restore();
        check(f.volume==0 && f.saved==null && f.changes==1,"queued raise then zero notification preserves user zero");
        f.volume=4;s.silence();check(f.volume==4 && f.saved==null && f.changes==1,"manual override cannot be reacquired within same session");
        f=new Fake();s=f.session();s.silence();s.volumeChanged(2);f.volume=0;f.session().restore();
        check(f.volume==0 && f.saved==null,"manual override survives process restart");
        f=new Fake();s=f.session();s.silence();f.volume=2;s.observeCurrentVolume();f.volume=0;s.restore();
        check(f.volume==0 && f.saved==null,"sampled nonzero fallback relinquishes before user returns to zero");

        f=new Fake();f.volume=0;s=f.session();s.silence();s.restore();check(f.saved==null && f.changes==0,"already silent remains silent");
        f=new Fake();f.rejectSave=true;Fake saveFailure=f;fails(()->saveFailure.session().silence());
        check(f.volume==7 && f.saved==null && f.changes==0,"do not mute without recovery record");
        f=new Fake();f.rejectMute=true;Fake muteFailure=f;fails(()->muteFailure.session().silence());
        check(f.volume==7 && f.saved==null,"failed mute leaves original volume");

        f=new Fake();s=f.session();s.silence();f.ignoreRestore=true;MediaVolumeSession refused=s;fails(refused::restore);
        check(f.volume==0 && f.saved==7 && !s.ownsVolume(),"silent failed restore keeps crash recovery record");
        f.ignoreRestore=false;f.session().restore();check(f.volume==7 && f.saved==null,"later process retries refused restore");
        f=new Fake();s=f.session();s.silence();f.rejectRestore=true;MediaVolumeSession denied=s;fails(denied::restore);
        check(f.volume==0 && f.saved==7,"throwing restore retains journal");f.rejectRestore=false;s.restore();
        check(f.volume==7 && f.saved==null,"repeat cleanup retries failed restore");

        f=new Fake();s=f.session();s.silence();f.rejectClear=true;MediaVolumeSession restoredButUncleared=s;fails(restoredButUncleared::restore);
        check(f.volume==7 && f.saved==0,"verified restore leaves cancellation marker when delete fails");
        f.volume=0;f.rejectClear=false;f.session().restore();check(f.volume==0 && f.saved==null,"post-restoration crash cannot apply original volume twice");
        f=new Fake();s=f.session();s.silence();f.rejectClear=true;MediaVolumeSession uncleared=s;fails(()->uncleared.volumeChanged(3));
        check(f.saved==0 && !s.ownsVolume(),"durable tombstone protects user override after deletion failure");
        f.volume=0;f.rejectClear=false;f.session().restore();check(f.volume==0 && f.saved==null,"crash recovery never restores canceled ownership");
        f=new Fake();s=f.session();s.silence();f.rejectSave=true;s.volumeChanged(3);
        check(f.saved==null,"delete fallback cancels recovery if tombstone write fails");
        f=new Fake();s=f.session();s.silence();f.rejectSave=true;f.rejectClear=true;MediaVolumeSession unavailable=s;
        fails(()->unavailable.volumeChanged(3));f.volume=0;fails(unavailable::restore);
        check(f.volume==0 && !s.ownsVolume(),"journal failure must not overwrite manual zero in current process");
        f.rejectSave=false;f.rejectClear=false;s.restore();check(f.volume==0 && f.saved==null,"later cleanup retries override cancellation");
        System.out.println("PASS: mute/restore, crash retry, manual raise/zero ownership, durable cancellation and failure cleanup");
    }
    static void fails(Runnable action){try{action.run();throw new AssertionError("expected failure");}catch(IllegalStateException expected){}}
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
