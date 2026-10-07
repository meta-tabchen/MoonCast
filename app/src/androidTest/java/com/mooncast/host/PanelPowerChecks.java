package com.mooncast.host;

import android.app.*;
import android.content.*;
import android.os.*;

/** Exercises the actual owned Shizuku power RPC and watchdog, using only a test emulator. */
final class PanelPowerChecks {
    static String verify(Instrumentation test)throws Exception {
        Context c=test.getTargetContext();Activity app=test.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        ShizukuInputBackend backend=new ShizukuInputBackend(c,text->android.util.Log.i("MoonCastPowerTest",text));
        try{
            ShizukuSetupChecks.authorize(test);test.runOnMainSync(backend::start);
            long end=SystemClock.elapsedRealtime()+15000;while(!backend.ready() && SystemClock.elapsedRealtime()<end)Thread.sleep(100);
            if(!backend.ready())throw new AssertionError("Shizuku helper unavailable");
            try{if(!backend.displayPower(true))throw new AssertionError("No panel lease");}
            catch(RuntimeException unavailable){
                backend.displayPower(false);if(backend.displayPowerPending())throw new AssertionError("Rejected power request retained a lease");
                return "PASS: unsupported physical-panel API safely rejected, explicit restoration and helper cleanup; no panel-off claim. Reason: "+unavailable.getMessage();
            }
            if(!c.getSystemService(PowerManager.class).isInteractive())throw new AssertionError("Panel control locked Android");
            backend.displayPower(false);if(backend.displayPowerPending())throw new AssertionError("Manual restore retained lease");
            backend.displayPower(true);Thread.sleep(31500);
            if(backend.displayPowerPending())throw new AssertionError("Missing heartbeat did not expire panel lease");
            backend.displayPower(true);backend.close();
            return "PASS: privileged panel API, Android stays interactive, manual restore, 30-second watchdog expiry and helper cleanup; physical phone/power measurements still required";
        }finally{backend.close();test.runOnMainSync(app::finish);}
    }
}
