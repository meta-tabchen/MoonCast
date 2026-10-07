package com.mooncast.host;

import android.app.*;
import android.content.Intent;
import android.os.SystemClock;

final class RootInputChecks {
    static void verify(Instrumentation test) throws Exception {
        var c=test.getTargetContext();Activity app=test.startActivitySync(new Intent(c,TestPatternActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK));
        RootInputBackend backend=new RootInputBackend(c,text->android.util.Log.i("MoonCastRootInputTest",text));
        try{
            backend.start();long end=SystemClock.elapsedRealtime()+20000;
            while(!backend.ready() && SystemClock.elapsedRealtime()<end){
                var root=test.getUiAutomation().getRootInActiveWindow();
                if(root!=null && "com.android.settings".contentEquals(root.getPackageName()) && !root.findAccessibilityNodeInfosByText("MoonCast").isEmpty())ShizukuSetupChecks.clickAllow(root);
                Thread.sleep(100);
            }
            if(!backend.ready())throw new AssertionError("Root broker not ready: enable root on this test emulator");
            int touches=TestPatternActivity.touches,keys=TestPatternActivity.keys,cancels=TestPatternActivity.cancels;
            backend.touch(1,1,300,300,1);Thread.sleep(100);backend.touch(3,1,350,300,1);Thread.sleep(100);backend.touch(2,1,350,300,1);
            backend.key(65,false,0);backend.key(65,true,0);Thread.sleep(200);
            if(TestPatternActivity.touches<=touches || TestPatternActivity.keys<=keys)throw new AssertionError("root touch/key did not arrive");
            backend.touch(1,2,300,300,1);Thread.sleep(100);backend.cancel();Thread.sleep(100);
            if(TestPatternActivity.cancels<=cancels)throw new AssertionError("root cancel did not arrive");
        }finally{backend.close();test.runOnMainSync(app::finish);}
        if(backend.ready())throw new AssertionError("closed broker still ready");
    }
}
