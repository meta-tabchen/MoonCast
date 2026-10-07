package com.mooncast.host;

import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.view.Surface;
import java.util.function.Consumer;
import rikka.shizuku.Shizuku;

final class ShizukuInputBackend implements RemoteInputController.Backend {
    private final Consumer<String> log;
    private final Shizuku.UserServiceArgs args;
    private final Binder owner=new Binder();
    private volatile IPrivilegedInput service;
    private boolean closed,binding;
    private int displayId;
    private final ServiceConnection connection=new ServiceConnection(){
        public void onServiceConnected(ComponentName name,IBinder binder){
            if(closed)return;
            try{IPrivilegedInput remote=IPrivilegedInput.Stub.asInterface(binder);remote.initialize(owner);remote.display(displayId);service=remote;log.accept("Shizuku input ready (UID "+remote.uid()+")");}
            catch(Exception e){log.accept("Shizuku input unavailable: "+e.getMessage());}
        }
        public void onServiceDisconnected(ComponentName name){service=null;log.accept("Shizuku service disconnected");}
    };
    ShizukuInputBackend(Context c,Consumer<String> log){this.log=log;args=new Shizuku.UserServiceArgs(new ComponentName(c.getPackageName(),PrivilegedInputService.class.getName())).daemon(false).tag("mooncast-host").processNameSuffix("privileged").debuggable(false).version(BuildConfig.VERSION_CODE);}
    static boolean authorized(){try{return Shizuku.pingBinder() && !Shizuku.isPreV11() && Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED;}catch(RuntimeException e){return false;}}
    void start(){if(closed || binding)return;if(!authorized()){log.accept("Authorize Shizuku before starting");return;}binding=true;try{Shizuku.bindUserService(args,connection);}catch(RuntimeException e){binding=false;log.accept("Shizuku bind failed: "+e.getMessage());}}
    @Override public boolean ready(){return !closed && authorized() && service!=null && service.asBinder().isBinderAlive();}
    private IPrivilegedInput remote(){if(!ready())throw new IllegalStateException("Shizuku service not ready");return service;}
    void display(int id){displayId=id;if(ready())try{remote().display(id);}catch(RemoteException e){throw new IllegalStateException(e);}}
    int createDisplay(Surface surface,int w,int h,int dpi){try{return remote().createDisplay(surface,w,h,dpi);}catch(RemoteException e){throw new IllegalStateException(e);}}
    void launch(String component,int id){try{remote().launch(component,id);}catch(RemoteException e){throw new IllegalStateException(e);}}
    @Override public void touch(int type,int id,float x,float y,float pressure){try{remote().touch(type,id,x,y,pressure);}catch(RemoteException e){throw new IllegalStateException(e);}}
    @Override public void key(int key,boolean release,int flags){try{remote().key(key,release,flags);}catch(RemoteException e){throw new IllegalStateException(e);}}
    @Override public void cancel(){if(service!=null)try{service.cancel();}catch(Exception ignored){}}
    @Override public void close(){if(closed)return;cancel();closed=true;try{if(service!=null)service.releaseDisplays();}catch(Exception ignored){}service=null;if(binding)try{Shizuku.unbindUserService(args,connection,true);}catch(RuntimeException ignored){}binding=false;}
}
