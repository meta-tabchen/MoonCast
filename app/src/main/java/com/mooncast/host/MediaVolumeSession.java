package com.mooncast.host;

/** Volume ownership and crash recovery; independent of Android for lifecycle tests. */
final class MediaVolumeSession {
    interface Device { int volume(); void volume(int value); }
    interface Journal { Integer saved(); void save(int volume); void clear(); }
    private final Device device;
    private final Journal journal;
    private boolean ownsVolume;
    private boolean relinquished;

    MediaVolumeSession(Device device,Journal journal){this.device=device;this.journal=journal;}

    synchronized void silence(){
        // A user override lasts for this casting session, including audio reconnects.
        if(relinquished || journal.saved()!=null)return;
        int original=device.volume();
        if(original==0)return;
        // Persist before changing system volume so a later launch can recover after a crash.
        journal.save(original);
        ownsVolume=true;
        try{
            device.volume(0);
            if(device.volume()!=0)throw new IllegalStateException("系统未允许修改媒体音量");
        }catch(RuntimeException failure){
            try{restore();}catch(RuntimeException recovery){failure.addSuppressed(recovery);}
            throw failure;
        }
    }

    /** Use the event's value, not a later read: a user can raise then lower to zero quickly. */
    synchronized void volumeChanged(int value){
        if(!ownsVolume || value<=0)return;
        relinquished=true;
        ownsVolume=false;
        forgetRecovery();
    }

    synchronized boolean ownsVolume(){return ownsVolume;}
    synchronized void observeCurrentVolume(){if(ownsVolume)volumeChanged(device.volume());}

    synchronized void restore(){
        ownsVolume=false;
        if(relinquished){forgetRecovery();return;}
        Integer saved=journal.saved();
        if(saved==null)return;
        // Zero is a durable cancellation marker, never a value to restore.
        if(saved==0){journal.clear();return;}
        if(device.volume()!=0){
            relinquished=true;
            forgetRecovery();
            return;
        }
        device.volume(saved);
        // Android may silently refuse a volume change. Keep the record for a later retry.
        if(device.volume()!=saved)throw new IllegalStateException("系统未恢复媒体音量，将在下次开始投屏时重试");
        forgetRecovery();
    }

    private void forgetRecovery(){
        if(journal.saved()==null)return;
        // If deletion fails, a new process must still respect the user's volume choice.
        try{journal.save(0);}
        catch(RuntimeException failure){
            try{journal.clear();}catch(RuntimeException cleanup){failure.addSuppressed(cleanup);throw failure;}
            return;
        }
        journal.clear();
    }
}
