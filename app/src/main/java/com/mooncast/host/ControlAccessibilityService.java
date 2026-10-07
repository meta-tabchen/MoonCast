package com.mooncast.host;

import android.accessibilityservice.*;
import android.graphics.Path;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

/** Explicit opt-in service. No accessibility tree contents are collected or logged. */
public final class ControlAccessibilityService extends AccessibilityService implements RemoteInputController.Backend {
    private static volatile ControlAccessibilityService instance;
    private Path path;
    private int pointer=-1;
    private long started;
    @Override protected void onServiceConnected(){instance=this;}
    @Override public void onAccessibilityEvent(AccessibilityEvent event){}
    @Override public void onInterrupt(){cancel();}
    @Override public void onDestroy(){cancel();if(instance==this)instance=null;super.onDestroy();}
    @Override public boolean ready(){return instance==this;}
    @Override public void touch(int type,int id,float x,float y,float pressure){
        if(type==4){if(id==pointer)cancel();return;}
        if(type==1){if(pointer!=-1)return;pointer=id;path=new Path();path.moveTo(x,y);started=SystemClock.uptimeMillis();}
        else if(id==pointer && path!=null){
            path.lineTo(x,y);
            if(type==2){
                long duration=Math.max(30,Math.min(2000,SystemClock.uptimeMillis()-started));
                GestureDescription gesture=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,duration)).build();
                cancel();if(!dispatchGesture(gesture,null,null))throw new IllegalStateException("无障碍服务拒绝手势");
            }
        }
    }
    @Override public void key(int key,boolean release,int flags){
        if(!release)return;
        if(key==27)performGlobalAction(GLOBAL_ACTION_BACK);
        else if(key==36 || key==91 || key==92)performGlobalAction(GLOBAL_ACTION_HOME);
        else if(key==93)performGlobalAction(GLOBAL_ACTION_RECENTS);
    }
    @Override public void cancel(){path=null;pointer=-1;}
    static RemoteInputController.Backend backend(){return new RemoteInputController.Backend(){
        public boolean ready(){return instance!=null;}
        public void touch(int type,int id,float x,float y,float pressure){ControlAccessibilityService s=instance;if(s!=null)s.touch(type,id,x,y,pressure);}
        public void key(int key,boolean release,int flags){ControlAccessibilityService s=instance;if(s!=null)s.key(key,release,flags);}
        public void cancel(){ControlAccessibilityService s=instance;if(s!=null)s.cancel();}
    };}
}
