package com.mooncast.host;

import android.os.Handler;
import java.util.*;
import java.util.function.Consumer;

/** Session gate and shared encoder-to-phone coordinate mapping for both input backends. */
final class RemoteInputController implements InputBridge.Receiver {
    interface Backend {
        boolean ready();void touch(int type,int id,float x,float y,float pressure);void key(int key,boolean release,int flags);void cancel();
        default void close(){cancel();}
    }
    private final Handler handler;
    private final Backend backend;
    private final Consumer<String> log;
    private final Map<Integer,CropGeometry.Point> pointers=new HashMap<>();
    private volatile boolean enabled;
    private long session;
    private CropGeometry.Mapping mapping;
    private float mouseX=.5f,mouseY=.5f;
    private boolean announced;
    RemoteInputController(Handler handler,Backend backend,boolean enabled,Consumer<String> log){this.handler=handler;this.backend=backend;this.enabled=enabled;this.log=log;}
    void begin(long value){cancel();session=value;announced=false;}
    void mapping(CropGeometry.Mapping value){handler.post(()->{
        if(mapping!=null && !mapping.equals(value))cancel();mapping=value;
    });}
    void enabled(boolean value){enabled=value;handler.post(()->{if(!value)cancel();announced=false;});}
    boolean enabled(){return enabled;}
    String status(android.content.Context context){return !enabled?context.getString(R.string.ui_off):backend.ready()?context.getString(R.string.ui_ready):context.getString(R.string.ui_waiting_for_permission_root_connection);}
    private boolean accepts(long value){return enabled && session!=0 && value==session && mapping!=null && backend.ready();}
    private void run(Runnable task){handler.post(()->{try{task.run();}catch(RuntimeException e){enabled=false;try{cancel();}catch(RuntimeException ignored){}log.accept("反控已关闭: "+e.getMessage());}});}
    @Override public void touch(long value,int type,int id,float x,float y,float pressure){
        if(!enabled)return;
        run(()->processTouch(value,type,id,x,y,pressure));
    }
    private void processTouch(long value,int type,int id,float x,float y,float pressure){
            if(!accepts(value))return;
            if(type==7){cancel();return;}
            if(type==4){backend.touch(4,id,0,0,0);pointers.remove(id);return;}
            if(type!=1 && type!=2 && type!=3)return;
            if(id<0 || !Float.isFinite(pressure))return;
            CropGeometry.Point point=mapping.map(x,y);
            if(type==1){if(point==null || pointers.size()>=10)return;pointers.put(id,point);}
            else{if(!pointers.containsKey(id))return;if(point==null)point=pointers.get(id);else pointers.put(id,point);}
            if(!announced){announced=true;log.accept("Moonlight 输入已到达；按当前视频区域映射");}
            backend.touch(type,id,point.x(),point.y(),Math.max(.01f,Math.min(1,pressure)));
            if(type==2)pointers.remove(id);
    }
    @Override public void mouse(long value,float x,float y,float width,float height){
        if(!enabled || !Float.isFinite(width) || !Float.isFinite(height) || width<=0 || height<=0)return;
        run(()->{if(!accepts(value) || !Float.isFinite(x) || !Float.isFinite(y))return;mouseX=Math.max(0,Math.min(1,x/width));mouseY=Math.max(0,Math.min(1,y/height));if(pointers.containsKey(0))processTouch(value,3,0,mouseX,mouseY,1);});
    }
    @Override public void relative(long value,int x,int y){if(!enabled)return;run(()->{
        if(!accepts(value))return;mouseX=Math.max(0,Math.min(1,mouseX+x/(float)mapping.outW()));mouseY=Math.max(0,Math.min(1,mouseY+y/(float)mapping.outH()));
        if(pointers.containsKey(0))processTouch(value,3,0,mouseX,mouseY,1);
    });}
    @Override public void button(long value,boolean release){if(!enabled)return;run(()->{if(accepts(value))processTouch(value,release?2:1,0,mouseX,mouseY,1);});}
    @Override public void key(int key,boolean release,int flags){if(!enabled)return;run(()->{if(accepts(session))backend.key(key&255,release,flags);});}
    private void cancel(){backend.cancel();pointers.clear();}
    void close(){enabled=false;session=0;mapping=null;try{cancel();}finally{backend.close();if(InputBridge.receiver==this)InputBridge.receiver=null;}}
}
