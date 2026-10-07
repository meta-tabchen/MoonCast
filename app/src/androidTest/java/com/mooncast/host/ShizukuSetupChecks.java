package com.mooncast.host;

import android.app.Instrumentation;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import rikka.shizuku.Shizuku;

/** Test-emulator provisioning only; never included in the delivered APK. */
final class ShizukuSetupChecks {
    static void authorize(Instrumentation test) throws Exception {
        long end=SystemClock.elapsedRealtime()+10000;
        while(!Shizuku.pingBinder() && SystemClock.elapsedRealtime()<end)Thread.sleep(100);
        if(!Shizuku.pingBinder())throw new AssertionError("Start Shizuku Manager on the test emulator first");
        if(ShizukuInputBackend.authorized())return;
        test.runOnMainSync(()->Shizuku.requestPermission(6060));
        end=SystemClock.elapsedRealtime()+10000;
        while(!ShizukuInputBackend.authorized() && SystemClock.elapsedRealtime()<end){
            AccessibilityNodeInfo root=test.getUiAutomation().getRootInActiveWindow();
            if(root!=null && "moe.shizuku.privileged.api".contentEquals(root.getPackageName()))clickAllow(root);
            Thread.sleep(100);
        }
        if(!ShizukuInputBackend.authorized())throw new AssertionError("Shizuku permission dialog did not grant test permission");
    }
    static boolean clickAllow(AccessibilityNodeInfo node){
        String text=node.getText()==null?"":node.getText().toString();
        if(text.equals("Allow all the time") || text.equals("始终允许") || text.equals("Allow") || text.equals("允许")){
            AccessibilityNodeInfo target=node;while(target!=null && !target.isClickable())target=target.getParent();
            if(target!=null)return target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo child=node.getChild(i);if(child!=null && clickAllow(child))return true;}
        return false;
    }
}
