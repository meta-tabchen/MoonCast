package com.mooncast.host;

/** A bounded panel-off lease. Pending restoration survives backend failures. */
final class DisplayPowerLease {
    interface Backend { void off() throws Exception; void on() throws Exception; }
    private final Backend backend;
    private boolean pending;
    private long expires;
    DisplayPowerLease(Backend backend){this.backend=backend;}
    synchronized void renew(long now,long duration) throws Exception {
        if(duration<1 || duration>30000)throw new IllegalArgumentException("Lease must be <= 30 seconds");
        if(pending && now>=expires){restore();throw new IllegalStateException("Display lease expired");}
        if(!pending){pending=true;expires=now;try{backend.off();}catch(Exception e){try{restore();}catch(Exception restore){e.addSuppressed(restore);}throw e;}}
        expires=now+duration;
    }
    synchronized void tick(long now) throws Exception {if(pending && now>=expires)restore();}
    synchronized void restore() throws Exception {if(pending){backend.on();pending=false;expires=0;}}
    synchronized boolean pending(){return pending;}
}
