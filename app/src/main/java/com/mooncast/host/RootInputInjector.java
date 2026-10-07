package com.mooncast.host;

import android.os.SystemClock;
import android.view.*;
import java.lang.reflect.Method;
import java.util.*;

/** Runs only in uid-0 app_process; handles continuous touches and balanced key events. */
final class RootInputInjector implements RemoteInputController.Backend {
    private record Finger(int androidId,float x,float y,float pressure){}
    private final LinkedHashMap<Integer,Finger> fingers=new LinkedHashMap<>();
    private final Map<Integer,Long> keys=new HashMap<>();
    private final Object manager;
    private final Method inject;
    private long downTime;
    private int meta,displayId;
    void display(int id){cancel();displayId=id;}
    RootInputInjector() throws Exception {this(false);}
    RootInputInjector(boolean allowShell) throws Exception {
        int uid=android.os.Process.myUid();
        if(uid!=0 && !(allowShell && uid==2000))throw new SecurityException("Privileged input requires root or an authorized shell service");
        try{Class<?> runtime=Class.forName("dalvik.system.VMRuntime");Object vm=runtime.getDeclaredMethod("getRuntime").invoke(null);runtime.getDeclaredMethod("setHiddenApiExemptions",String[].class).invoke(vm,(Object)new String[]{"L"});}catch(Exception ignored){}
        Class<?> type;
        try{type=Class.forName("android.hardware.input.InputManagerGlobal");}catch(ClassNotFoundException e){type=Class.forName("android.hardware.input.InputManager");}
        manager=type.getDeclaredMethod("getInstance").invoke(null);
        inject=type.getDeclaredMethod("injectInputEvent",InputEvent.class,int.class);inject.setAccessible(true);
    }
    public boolean ready(){return true;}
    // This class executes only inside a checked root/shell process, with hidden-API exemptions.
    @android.annotation.SuppressLint("BlockedPrivateApi")
    private void inject(InputEvent event){try{
        if(displayId!=0)InputEvent.class.getDeclaredMethod("setDisplayId",int.class).invoke(event,displayId);
        Object accepted=inject.invoke(manager,event,0);
        if(Boolean.FALSE.equals(accepted))throw new IllegalStateException("系统拒绝输入事件");
    }catch(Exception e){throw new IllegalStateException("Root 输入失败: "+e.getClass().getSimpleName(),e);}}
    public void touch(int type,int id,float x,float y,float pressure){
        if(type==4 || type==7){cancel();return;}
        if(type!=1 && type!=2 && type!=3)return;
        Finger old=fingers.get(id);int action,index;
        if(type==1){
            if(old!=null || fingers.size()>=10)return;
            boolean[] used=new boolean[10];for(Finger f:fingers.values())used[f.androidId()]=true;
            int slot=0;while(used[slot])slot++;
            if(fingers.isEmpty())downTime=SystemClock.uptimeMillis();
            fingers.put(id,new Finger(slot,x,y,pressure));index=fingers.size()-1;
            action=index==0?MotionEvent.ACTION_DOWN:MotionEvent.ACTION_POINTER_DOWN|(index<<MotionEvent.ACTION_POINTER_INDEX_SHIFT);
        }else{
            if(old==null)return;fingers.put(id,new Finger(old.androidId(),x,y,pressure));
            index=new ArrayList<>(fingers.keySet()).indexOf(id);
            action=type==3?MotionEvent.ACTION_MOVE:fingers.size()==1?MotionEvent.ACTION_UP:MotionEvent.ACTION_POINTER_UP|(index<<MotionEvent.ACTION_POINTER_INDEX_SHIFT);
        }
        motion(action);if(type==2)fingers.remove(id);
    }
    private void motion(int action){
        int count=fingers.size();if(count==0)return;
        MotionEvent.PointerProperties[] properties=new MotionEvent.PointerProperties[count];MotionEvent.PointerCoords[] coords=new MotionEvent.PointerCoords[count];
        int i=0;for(Finger f:fingers.values()){
            properties[i]=new MotionEvent.PointerProperties();properties[i].id=f.androidId();properties[i].toolType=MotionEvent.TOOL_TYPE_FINGER;
            coords[i]=new MotionEvent.PointerCoords();coords[i].x=f.x();coords[i].y=f.y();coords[i].pressure=f.pressure();coords[i].size=.1f;i++;
        }
        MotionEvent event=MotionEvent.obtain(downTime,SystemClock.uptimeMillis(),action,count,properties,coords,meta,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);
        try{inject(event);}finally{event.recycle();}
    }
    public void key(int virtualKey,boolean release,int flags){
        int code=androidKey(virtualKey);if(code==KeyEvent.KEYCODE_UNKNOWN)return;
        long now=SystemClock.uptimeMillis();int mask=metaMask(code);
        if(!release){keys.putIfAbsent(code,now);meta|=mask;}
        else if(!keys.containsKey(code))return;
        KeyEvent event=new KeyEvent(keys.get(code),now,release?KeyEvent.ACTION_UP:KeyEvent.ACTION_DOWN,code,0,meta,KeyCharacterMap.VIRTUAL_KEYBOARD,0,0,InputDevice.SOURCE_KEYBOARD);
        inject(event);if(release){keys.remove(code);meta&=~mask;}
    }
    public void cancel(){
        RuntimeException failure=null;
        try{if(!fingers.isEmpty())motion(MotionEvent.ACTION_CANCEL);}catch(RuntimeException e){failure=e;}finally{fingers.clear();}
        for(int code:new ArrayList<>(keys.keySet()))try{
            long now=SystemClock.uptimeMillis();inject(new KeyEvent(keys.get(code),now,KeyEvent.ACTION_UP,code,0,meta,KeyCharacterMap.VIRTUAL_KEYBOARD,0,0,InputDevice.SOURCE_KEYBOARD));
        }catch(RuntimeException e){failure=e;}
        keys.clear();meta=0;if(failure!=null)throw failure;
    }
    private static int metaMask(int code){return switch(code){case KeyEvent.KEYCODE_SHIFT_LEFT,KeyEvent.KEYCODE_SHIFT_RIGHT->KeyEvent.META_SHIFT_ON;case KeyEvent.KEYCODE_CTRL_LEFT,KeyEvent.KEYCODE_CTRL_RIGHT->KeyEvent.META_CTRL_ON;case KeyEvent.KEYCODE_ALT_LEFT,KeyEvent.KEYCODE_ALT_RIGHT->KeyEvent.META_ALT_ON;case KeyEvent.KEYCODE_META_LEFT,KeyEvent.KEYCODE_META_RIGHT->KeyEvent.META_META_ON;default->0;};}
    static int androidKey(int key){
        if(key>=48 && key<=57)return KeyEvent.KEYCODE_0+key-48;
        if(key>=65 && key<=90)return KeyEvent.KEYCODE_A+key-65;
        if(key>=96 && key<=105)return KeyEvent.KEYCODE_NUMPAD_0+key-96;
        if(key>=112 && key<=123)return KeyEvent.KEYCODE_F1+key-112;
        return switch(key){
            case 8->KeyEvent.KEYCODE_DEL;case 9->KeyEvent.KEYCODE_TAB;case 13->KeyEvent.KEYCODE_ENTER;
            case 16,160->KeyEvent.KEYCODE_SHIFT_LEFT;case 161->KeyEvent.KEYCODE_SHIFT_RIGHT;
            case 17,162->KeyEvent.KEYCODE_CTRL_LEFT;case 163->KeyEvent.KEYCODE_CTRL_RIGHT;
            case 18,164->KeyEvent.KEYCODE_ALT_LEFT;case 165->KeyEvent.KEYCODE_ALT_RIGHT;
            case 20->KeyEvent.KEYCODE_CAPS_LOCK;case 27->KeyEvent.KEYCODE_BACK;case 32->KeyEvent.KEYCODE_SPACE;
            case 33->KeyEvent.KEYCODE_PAGE_UP;case 34->KeyEvent.KEYCODE_PAGE_DOWN;case 35->KeyEvent.KEYCODE_MOVE_END;case 36->KeyEvent.KEYCODE_MOVE_HOME;
            case 37->KeyEvent.KEYCODE_DPAD_LEFT;case 38->KeyEvent.KEYCODE_DPAD_UP;case 39->KeyEvent.KEYCODE_DPAD_RIGHT;case 40->KeyEvent.KEYCODE_DPAD_DOWN;
            case 46->KeyEvent.KEYCODE_FORWARD_DEL;case 91,92->KeyEvent.KEYCODE_HOME;case 93->KeyEvent.KEYCODE_APP_SWITCH;
            case 186->KeyEvent.KEYCODE_SEMICOLON;case 187->KeyEvent.KEYCODE_EQUALS;case 188->KeyEvent.KEYCODE_COMMA;case 189->KeyEvent.KEYCODE_MINUS;
            case 190->KeyEvent.KEYCODE_PERIOD;case 191->KeyEvent.KEYCODE_SLASH;case 192->KeyEvent.KEYCODE_GRAVE;
            case 219->KeyEvent.KEYCODE_LEFT_BRACKET;case 220->KeyEvent.KEYCODE_BACKSLASH;case 221->KeyEvent.KEYCODE_RIGHT_BRACKET;case 222->KeyEvent.KEYCODE_APOSTROPHE;
            default->KeyEvent.KEYCODE_UNKNOWN;
        };
    }
}
