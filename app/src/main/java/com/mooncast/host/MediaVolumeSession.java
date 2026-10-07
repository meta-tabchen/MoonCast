package com.mooncast.host;

/** Volume ownership and crash recovery; independent of Android for lifecycle tests. */
final class MediaVolumeSession {
    interface Device { int volume(); void volume(int value); }
    interface Journal { Integer saved(); void save(int volume); void clear(); }
    private final Device device;
    private final Journal journal;
    MediaVolumeSession(Device device,Journal journal){this.device=device;this.journal=journal;}
    void silence(){
        if(journal.saved()!=null)return;
        int original=device.volume();
        if(original==0)return;
        // Persist before changing system volume so a later launch can recover after a crash.
        journal.save(original);
        try{
            device.volume(0);
            if(device.volume()!=0)throw new IllegalStateException("系统未允许修改媒体音量");
        }catch(RuntimeException e){restore();throw e;}
    }
    void restore(){
        Integer saved=journal.saved();
        if(saved==null)return;
        // Respect a volume change made by the user during casting.
        if(device.volume()==0)device.volume(saved);
        journal.clear();
    }
}
