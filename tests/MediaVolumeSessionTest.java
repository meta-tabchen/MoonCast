package com.mooncast.host;

public final class MediaVolumeSessionTest {
    static final class Fake implements MediaVolumeSession.Device,MediaVolumeSession.Journal {
        int volume=7,changes;Integer saved;boolean rejectSave,rejectMute;
        public int volume(){return volume;}
        public void volume(int value){changes++;if(value==0 && rejectMute)throw new IllegalStateException("denied");volume=value;}
        public Integer saved(){return saved;}
        public void save(int value){if(rejectSave)throw new IllegalStateException("disk full");saved=value;}
        public void clear(){saved=null;}
        MediaVolumeSession session(){return new MediaVolumeSession(this,this);}
    }
    public static void main(String[] args){
        Fake f=new Fake();var s=f.session();s.silence();s.silence();check(f.volume==0 && f.saved==7 && f.changes==1,"mute once, remember original");
        s.restore();s.restore();check(f.volume==7 && f.saved==null && f.changes==2,"stop/error restores once");
        f=new Fake();s=f.session();s.silence();f.session().restore();check(f.volume==7 && f.saved==null,"new process recovers journal");
        f=new Fake();s=f.session();s.silence();f.volume=3;s.restore();check(f.volume==3 && f.saved==null,"respect user volume change");
        f=new Fake();f.volume=0;f.session().silence();check(f.saved==null && f.changes==0,"already silent remains silent");
        f=new Fake();f.rejectSave=true;try{f.session().silence();throw new AssertionError("save should fail");}catch(IllegalStateException expected){}check(f.volume==7 && f.saved==null && f.changes==0,"do not mute without recovery record");
        f=new Fake();f.rejectMute=true;try{f.session().silence();throw new AssertionError("mute should fail");}catch(IllegalStateException expected){}check(f.volume==7 && f.saved==null,"failed mute leaves original volume");
        System.out.println("PASS: mute/restore, repeated cleanup, crash recovery, user override, already-muted and failure cases");
    }
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
