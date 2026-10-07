package com.mooncast.host;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.*;
import java.lang.reflect.*;

/** Physical panel power from an explicitly owned shell/root helper. Never locks Android. */
@SuppressLint({"PrivateApi","SoonBlockedPrivateApi","BlockedPrivateApi"})
final class PhysicalDisplayPower implements DisplayPowerLease.Backend {
    private final Context context;
    private IBinder token;
    private Method setPower;
    PhysicalDisplayPower(Context context){this.context=context;}
    private void prepare() throws Exception {
        int uid=android.os.Process.myUid();if(uid!=0 && uid!=2000)throw new SecurityException("Shell/root required");
        if(token!=null)return;
        Class<?> surface=Class.forName("android.view.SurfaceControl");
        setPower=surface.getDeclaredMethod("setDisplayPowerMode",IBinder.class,int.class);setPower.setAccessible(true);
        if(Build.VERSION.SDK_INT<29){token=(IBinder)surface.getDeclaredMethod("getBuiltInDisplay",int.class).invoke(null,0);}
        else {
            try{token=(IBinder)surface.getDeclaredMethod("getInternalDisplayToken").invoke(null);}catch(NoSuchMethodException ignored){}
            if(token==null){
                Class<?> control=surface;
                if(Build.VERSION.SDK_INT>=34){
                    String path=android.system.Os.getenv("SYSTEMSERVERCLASSPATH");if(path==null || path.isEmpty())throw new IllegalStateException("System display API unavailable");
                    Method factory=Class.forName("com.android.internal.os.ClassLoaderFactory").getDeclaredMethod("createClassLoader",String.class,String.class,String.class,ClassLoader.class,int.class,boolean.class,String.class);
                    ClassLoader loader=(ClassLoader)factory.invoke(null,path,null,null,ClassLoader.getSystemClassLoader(),0,true,null);
                    control=loader.loadClass("com.android.server.display.DisplayControl");
                    Method load=Runtime.class.getDeclaredMethod("loadLibrary0",Class.class,String.class);load.setAccessible(true);load.invoke(Runtime.getRuntime(),control,"android_servers");
                }
                long[] ids=(long[])control.getDeclaredMethod("getPhysicalDisplayIds").invoke(null);
                if(ids==null || ids.length!=1)throw new IllegalStateException("Requires one unambiguous physical panel");
                token=(IBinder)control.getDeclaredMethod("getPhysicalDisplayToken",long.class).invoke(null,ids[0]);
            }
        }
        if(token==null)throw new IllegalStateException("Physical panel token unavailable");
    }
    @Override public void off() throws Exception {
        if(!context.getSystemService(PowerManager.class).isInteractive())throw new IllegalStateException("Unlock and wake the phone first");
        prepare();setPower.invoke(null,token,0);
    }
    @Override public void on() throws Exception {if(token!=null && setPower!=null)setPower.invoke(null,token,2);}
}
