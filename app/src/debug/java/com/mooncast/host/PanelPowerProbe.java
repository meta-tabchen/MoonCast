package com.mooncast.host;

import android.content.Context;
import android.os.*;

/** Debug-only bounded root API probe; never a public component or release entry point. */
public final class PanelPowerProbe {
    public static void main(String[] args)throws Exception {
        if(android.os.Process.myUid()!=0)throw new SecurityException("Root test only");
        if(Looper.myLooper()==null)Looper.prepareMainLooper();
        Class<?> t=Class.forName("android.app.ActivityThread");Object thread=t.getDeclaredMethod("systemMain").invoke(null);
        Context c=(Context)t.getDeclaredMethod("getSystemContext").invoke(thread);
        DisplayPowerLease lease=new DisplayPowerLease(new PhysicalDisplayPower(c));
        try{lease.renew(SystemClock.elapsedRealtime(),1000);SystemClock.sleep(1100);lease.tick(SystemClock.elapsedRealtime());if(lease.pending())throw new AssertionError("Lease did not expire");System.out.println("ROOT_PANEL_PROBE: API invocation and expiry passed; physical panel effect unmeasured");}
        catch(Exception e){System.out.println("ROOT_PANEL_PROBE: unsupported API, restoration attempted: "+e.getClass().getSimpleName());}
        finally{lease.restore();}
        System.exit(0);
    }
}
