package com.mooncast.host;

import android.app.*;
import android.content.*;
import android.os.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

final class HostLifecycleChecks {
    static void verify(Instrumentation test)throws Exception{
        Context c=test.getTargetContext();
        Activity activity=test.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        test.runOnMainSync(()->activity.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
        try{
            // A true bindService return with no onServiceConnected must not retain the old UI.
            test.runOnMainSync(()->{
                try{
                    var state=MainActivity.class.getDeclaredField("state");state.setAccessible(true);
                    ((android.widget.TextView)state.get(activity)).setText(R.string.ui_casting);
                    var running=MainActivity.class.getDeclaredMethod("running",boolean.class);running.setAccessible(true);running.invoke(activity,true);
                }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
            });
            Thread.sleep(5000);
            test.runOnMainSync(()->{
                try{var start=MainActivity.class.getDeclaredField("start");start.setAccessible(true);if(((android.widget.Button)start.get(activity)).getVisibility()!=android.view.View.VISIBLE)throw new AssertionError("idle/stale binding left casting UI");}
                catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
            });
            for(String fault:new String[]{"none","main","cleanup","none"})stopProcess(c,fault);
        }finally{test.runOnMainSync(activity::finish);}
    }
    private static void stopProcess(Context c,String fault)throws Exception{
        CountDownLatch connected=new CountDownLatch(1),dead=new CountDownLatch(1);
        AtomicReference<Messenger> remote=new AtomicReference<>();
        ServiceConnection connection=new ServiceConnection(){
            public void onServiceConnected(ComponentName name,IBinder binder){
                try{binder.linkToDeath(dead::countDown,0);}catch(RemoteException e){dead.countDown();}
                remote.set(new Messenger(binder));connected.countDown();
            }
            public void onServiceDisconnected(ComponentName name){}
        };
        Intent intent=new Intent(c,HostLifecycleProbe.class).putExtra("fault",fault);
        if(!c.bindService(intent,connection,Context.BIND_AUTO_CREATE))throw new AssertionError("probe binding");
        try{
            if(!connected.await(5,TimeUnit.SECONDS))throw new AssertionError("probe connection "+fault);
            Thread.sleep(800); // main-stall case is now blocked before Stop is sent.
            long begin=SystemClock.elapsedRealtime();
            remote.get().send(Message.obtain(null,HostService.STOP));
            if(!dead.await(5500,TimeUnit.MILLISECONDS))throw new AssertionError("Stop failed with "+fault);
            long elapsed=SystemClock.elapsedRealtime()-begin;
            if(fault.equals("none") && elapsed>2000)throw new AssertionError("normal stop too slow "+elapsed);
            android.util.Log.i("MoonCastLifecycleTest",fault+" stop process exit in "+elapsed+"ms");
        }finally{c.unbindService(connection);}
        Thread.sleep(300);
    }
}
