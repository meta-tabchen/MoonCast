package com.mooncast.host;

import android.content.Intent;
import android.os.*;

/** Debug-only fault injection in the real HostService process; no encoder or user capture. */
public final class HostLifecycleProbe extends HostService {
    @Override public IBinder onBind(Intent intent){
        IBinder binder=super.onBind(intent);
        String fault=intent.getStringExtra("fault");
        Handler main=new Handler(Looper.getMainLooper());
        if("main".equals(fault))main.postDelayed(()->SystemClock.sleep(30000),500);
        if("cleanup".equals(fault)){
            try{
                var field=HostService.class.getDeclaredField("remoteInput");field.setAccessible(true);
                field.set(this,new RemoteInputController(main,new RemoteInputController.Backend(){
                    public boolean ready(){return true;}
                    public void touch(int t,int i,float x,float y,float p){}
                    public void key(int k,boolean release,int flags){}
                    public void cancel(){SystemClock.sleep(30000);}
                },false,text->{}));
            }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
        }
        return binder;
    }
}
