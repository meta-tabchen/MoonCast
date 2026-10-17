package com.mooncast.host;

import android.content.Context;
import android.hardware.display.*;
import android.os.*;
import android.view.Surface;
import java.util.*;

/** Shizuku-only root/shell service. Owner death releases pointers, keys and virtual displays. */
public final class PrivilegedInputService extends IPrivilegedInput.Stub {
    private Context context;
    private RootInputInjector input;
    private IBinder owner;
    private int ownerUid=-1;
    private final java.util.concurrent.atomic.AtomicBoolean stopping=new java.util.concurrent.atomic.AtomicBoolean();
    private DisplayPowerLease power;
    private final Handler watchdog=new Handler(Looper.getMainLooper());
    private final Runnable powerWatch=new Runnable(){public void run(){synchronized(PrivilegedInputService.this){try{if(power!=null)power.tick(SystemClock.elapsedRealtime());}catch(Exception e){android.util.Log.e("MoonCastPrivileged","Panel restoration will retry",e);}if(owner!=null&&!stopping.get())watchdog.postDelayed(this,1000);}}};
    private final Map<Integer,VirtualDisplay> displays=new HashMap<>();
    public PrivilegedInputService(){}
    public PrivilegedInputService(Context context){this.context=context;}
    @Override public synchronized void initialize(IBinder token){
        if(stopping.get())throw new IllegalStateException("Service is restoring and closing");
        int calling=Binder.getCallingUid();
        if(owner!=null && (ownerUid!=calling || owner!=token))throw new SecurityException(PhoneMessages.message(R.string.phone_privileged_owned));
        if(token==null)throw new IllegalArgumentException("owner");
        try{
            int uid=android.os.Process.myUid();if(uid!=0 && uid!=2000)throw new SecurityException(PhoneMessages.message(R.string.phone_panel_privilege_required));
            // Shell has the display permissions and an attribution package on stock Android.
            if(uid==0)android.system.Os.setuid(2000);
            if(input==null)input=new RootInputInjector(true);
            if(owner==null){owner=token;ownerUid=calling;token.linkToDeath(this::cleanup,0);watchdog.post(powerWatch);}
        }catch(Exception e){throw new IllegalStateException(PhoneMessages.message(R.string.phone_privileged_init_failed),e);}
    }
    private void checkOwner(){if(stopping.get() || owner==null || !owner.isBinderAlive() || Binder.getCallingUid()!=ownerUid)throw new SecurityException(PhoneMessages.message(R.string.phone_privileged_owner_rejected));}
    @Override public synchronized int uid(){checkOwner();return android.os.Process.myUid();}
    @Override public synchronized void touch(int type,int id,float x,float y,float pressure){checkOwner();input.touch(type,id,x,y,pressure);}
    @Override public synchronized void key(int code,boolean release,int flags){checkOwner();input.key(code,release,flags);}
    @Override public synchronized void cancel(){checkOwner();input.cancel();}
    @Override public synchronized void display(int id){checkOwner();input.display(id);}
    @Override public synchronized boolean displayPower(boolean off){
        checkOwner();long identity=Binder.clearCallingIdentity();
        try{
            if(power==null){if(context==null){Class<?> t=Class.forName("android.app.ActivityThread");Object thread=t.getDeclaredMethod("systemMain").invoke(null);context=(Context)t.getDeclaredMethod("getSystemContext").invoke(thread);}power=new DisplayPowerLease(new PhysicalDisplayPower(context));}
            if(off)power.renew(SystemClock.elapsedRealtime(),30000);else power.restore();return power.pending();
        }catch(Exception e){throw new IllegalStateException(PhoneMessages.message(R.string.phone_panel_power_unavailable, e.getMessage()),e);}
        finally{Binder.restoreCallingIdentity(identity);}
    }
    @Override public synchronized boolean displayPowerPending(){checkOwner();return power!=null && power.pending();}
    // Hidden display flags are intentionally used only by the checked shell/root service.
    @android.annotation.SuppressLint("WrongConstant")
    @Override public synchronized int createDisplay(Surface surface,int width,int height,int dpi){
        checkOwner();if(surface==null || width<1 || height<1 || width>8192 || height>8192)throw new IllegalArgumentException("display size/surface");
        long identity=Binder.clearCallingIdentity();
        try{
            if(context==null){Class<?> t=Class.forName("android.app.ActivityThread");Object thread=t.getDeclaredMethod("systemMain").invoke(null);context=(Context)t.getDeclaredMethod("getSystemContext").invoke(thread);}
            Context shell=context.createPackageContext("com.android.shell",Context.CONTEXT_IGNORE_SECURITY);
            int flags=DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC|DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY|(1<<6)|(1<<8);
            if(Build.VERSION.SDK_INT>=33)flags|=(1<<10)|(1<<11); // TRUSTED and OWN_DISPLAY_GROUP
            VirtualDisplay vd;
            try{vd=shell.getSystemService(DisplayManager.class).createVirtualDisplay("MoonCast app display",width,height,dpi,surface,flags);}
            catch(SecurityException denied){
                android.util.Log.i("MoonCastPrivileged","Trusted flags unavailable; using a shell-owned display and privileged app launch");
                vd=shell.getSystemService(DisplayManager.class).createVirtualDisplay("MoonCast app display",width,height,dpi,surface,DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC|DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY|(1<<6)|(1<<8));
            }
            if(vd==null)throw new IllegalStateException(PhoneMessages.message(R.string.phone_display_rejected));
            int id=vd.getDisplay().getDisplayId();displays.put(id,vd);return id;
        }catch(Exception e){android.util.Log.e("MoonCastPrivileged","Trusted display unavailable",e);throw new IllegalStateException(PhoneMessages.message(R.string.phone_independent_display_failed, e.getMessage()),e);}
        finally{Binder.restoreCallingIdentity(identity);}
    }
    @Override public synchronized void releaseDisplays(){checkOwner();for(VirtualDisplay d:displays.values())d.release();displays.clear();}
    @Override public synchronized void launch(String component,int displayId){
        checkOwner();if(!displays.containsKey(displayId))throw new IllegalArgumentException(PhoneMessages.message(R.string.phone_display_unknown));
        android.content.ComponentName name=android.content.ComponentName.unflattenFromString(component);if(name==null)throw new IllegalArgumentException("component");
        long identity=Binder.clearCallingIdentity();
        try{
            // ActivityThread's calling package in a user service may still be the host APK.
            // am supplies shell attribution; arguments are passed separately, never as shell code.
            java.lang.Process process=new ProcessBuilder("/system/bin/am","start","--user",Integer.toString(ownerUid/100000),"--display",Integer.toString(displayId),"-f","0x18000000","-n",name.flattenToString()).redirectErrorStream(true).start();
            if(!process.waitFor(8,java.util.concurrent.TimeUnit.SECONDS)){process.destroy();throw new IllegalStateException(PhoneMessages.message(R.string.phone_app_launch_timeout));}
            java.io.ByteArrayOutputStream result=new java.io.ByteArrayOutputStream();byte[] bytes=new byte[1024];int count;
            while((count=process.getInputStream().read(bytes))!=-1){result.write(bytes,0,count);if(result.size()>16384)throw new IllegalStateException(PhoneMessages.message(R.string.phone_app_launch_response_large));}
            String output=result.toString("UTF-8");
            if(process.exitValue()!=0 || output.contains("Error:") || output.contains("Exception"))throw new IllegalStateException(output.trim());
        }catch(Exception e){throw new IllegalStateException(PhoneMessages.message(R.string.phone_app_launch_failed, e.getMessage()),e);}
        finally{Binder.restoreCallingIdentity(identity);}
    }
    private void cleanup(){
        if(!stopping.compareAndSet(false,true))return;
        // Terminal first: no queued Binder call can off/renew/input/launch again.
        watchdog.removeCallbacks(powerWatch);
        new Thread(()->{
            synchronized(PrivilegedInputService.this){
                if(input!=null)try{input.cancel();}catch(RuntimeException ignored){}
                for(VirtualDisplay d:displays.values())try{d.release();}catch(RuntimeException ignored){}
                displays.clear();owner=null;
            }
            long deadline=SystemClock.elapsedRealtime()+60000;
            while(power!=null&&power.pending()&&SystemClock.elapsedRealtime()<deadline){
                try{power.restore();}catch(Exception failure){android.util.Log.e("MoonCastPrivileged","Restore-only retry failed",failure);}
                if(power.pending())SystemClock.sleep(1000);
            }
            // A persistent driver failure is a validation failure, never claimed as restored.
            System.exit(power!=null&&power.pending()?2:0);
        },"PanelRestoreOnly").start();
    }
    @Override public void destroy(){cleanup();}
}
