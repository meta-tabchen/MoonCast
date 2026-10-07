package com.mooncast.host;

public final class DisplayPowerLeaseTest {
    private static final class Panel implements DisplayPowerLease.Backend {
        int off,on;boolean failOff,failOn;
        public void off() throws Exception {off++;if(failOff)throw new Exception("off denied");}
        public void on() throws Exception {on++;if(failOn)throw new Exception("on denied");}
    }
    public static void main(String[] args)throws Exception {
        Panel panel=new Panel();DisplayPowerLease lease=new DisplayPowerLease(panel);
        lease.renew(100,30000);lease.renew(1000,30000);check(panel.off==1,"renew must not repeat power-off");lease.tick(30099);check(lease.pending(),"heartbeat extends lease");lease.tick(31000);check(!lease.pending() && panel.on==1,"expiry restores");lease.restore();check(panel.on==1,"cleanup idempotent");
        lease.renew(40000,1000);try{lease.renew(41000,1000);throw new AssertionError("expired lease reactivated");}catch(IllegalStateException expected){}check(!lease.pending(),"late heartbeat restores before refusal");
        panel.failOff=true;try{lease.renew(50000,1000);throw new AssertionError();}catch(Exception expected){}check(!lease.pending() && panel.on==3,"failed off attempts recovery");
        panel.failOff=false;lease.renew(60000,1000);panel.failOn=true;try{lease.tick(61000);throw new AssertionError();}catch(Exception expected){}check(lease.pending(),"failed restore remains pending");panel.failOn=false;lease.tick(62000);check(!lease.pending(),"watchdog retries restore");
        for(long duration:new long[]{0,30001})try{lease.renew(0,duration);throw new AssertionError();}catch(IllegalArgumentException expected){}
        System.out.println("PASS: bounded display lease, renew, expiry, idempotent stop and failed restore retries");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
