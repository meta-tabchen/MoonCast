package com.mooncast.host;

import android.content.Context;
import android.hardware.display.*;
import android.net.*;
import android.os.*;
import android.view.Surface;
import io.github.jqssun.displaymirror.sunshine.SunshineServer;
import io.github.jqssun.displaymirror.sunshine.NativeSessions;
import java.util.*;
import org.json.JSONArray;
import java.io.*;
import java.lang.reflect.Method;
import org.json.JSONObject;

/** Experimental privileged capture backend; no system settings or input devices are modified. */
public final class RootDaemon implements SunshineServer.Listener {
    private Context context;
    private Handler main;
    private LocalSocket socket;
    private DataOutputStream events;
    private VirtualDisplay display;
    private volatile VideoPipeline pipeline;
    private final LinkedHashMap<Long,JSONObject> receivers=new LinkedHashMap<>();
    private int receiverOrdinal,audioPacketMs;
    private boolean watchParty;
    private int scaleMode=CropGeometry.VIDEO_REGION;
    private int frameLimit=120;
    private long session;
    private boolean audioPump;
    private RemoteInputController remoteInput;
    private CropGeometry.Mapping mapping;
    public static void main(String[] args) {
        RootDaemon daemon=new RootDaemon();
        try {
            if (args.length!=9 || android.os.Process.myUid()!=0) throw new IllegalArgumentException("Root daemon requires uid 0 and nine arguments");
            // app_process runs outside an APK process; it needs a system context and a main looper.
            if (Looper.myLooper()==null) Looper.prepareMainLooper();
            daemon.main=new Handler(Looper.getMainLooper());
            Class<?> thread=Class.forName("android.app.ActivityThread");
            Method systemMain=thread.getDeclaredMethod("systemMain"); systemMain.setAccessible(true);
            Object activityThread=systemMain.invoke(null);
            Method getContext=thread.getDeclaredMethod("getSystemContext"); getContext.setAccessible(true);
            daemon.context=(Context)getContext.invoke(activityThread);
            daemon.socket=new LocalSocket();
            daemon.socket.connect(new LocalSocketAddress(args[0]));
            daemon.events=new DataOutputStream(daemon.socket.getOutputStream());
            System.setProperty("mooncast.nativeDir",args[2]);
            SunshineServer.listener=daemon;
            SunshineServer.setSunshineName(args[3]);
            SunshineServer.setCertPath(args[1]+"/host-cert.pem");
            SunshineServer.setPkeyPath(args[1]+"/host-key.pem");
            SunshineServer.setFileStatePath(args[1]+"/paired-clients.json");
            SunshineServer.setHevcSupported(Boolean.parseBoolean(args[4]));
            daemon.scaleMode=Integer.parseInt(args[5]);
            daemon.frameLimit=Integer.parseInt(args[7]);
            daemon.watchParty=Boolean.parseBoolean(args[8]);daemon.control(!daemon.watchParty && Boolean.parseBoolean(args[6]));
            new Thread(() -> {
                try {
                    DataInputStream commands=new DataInputStream(daemon.socket.getInputStream());
                    for (;;) {
                        String command=commands.readUTF();
                        if (command.equals("STOP")) break;
                        if (command.matches("PIN [0-9]{4}")) SunshineServer.submitPin(command.substring(4));
                        if(command.matches("SCALE [0-4]"))daemon.main.post(()->{daemon.scaleMode=Integer.parseInt(command.substring(6));if(daemon.pipeline!=null)daemon.pipeline.setMode(daemon.scaleMode);});
                        if(command.matches("DISCONNECT -?[0-9]+"))daemon.main.post(()->{long id=Long.parseLong(command.substring(11));if(daemon.receivers.containsKey(id))new Thread(()->NativeSessions.disconnect(id),"RootDisconnect").start();});
                        if(command.equals("RESCAN"))daemon.main.post(()->{if(daemon.pipeline!=null)daemon.pipeline.rescan();});
                        if(command.matches("CONTROL [01]"))daemon.main.post(()->daemon.control(command.endsWith("1")));
                    }
                } catch (IOException ignored) {} finally { daemon.main.post(daemon::shutdown); }
            },"OwnerWatchdog").start();
            new Thread(SunshineServer::start,"RootSunshine").start();
            daemon.event("log","Root daemon 已启动；免 MediaProjection 授权（实验功能）");
            Looper.loop();
        } catch (Throwable e) { e.printStackTrace(); daemon.event("error","Root 初始化失败: "+e); daemon.shutdown(); }
    }
    @Override public void pinRequested() { event("pin",""); }
    @Override public void uuid(String ignored) {}
    @Override public void createDisplay(long s,int w,int h,int fps,int ms,Surface surface,boolean audio) {
        main.post(() -> {
            try {
                if(!NativeSessions.active(s))return;
                if(receivers.size()>=(watchParty?3:1) || (audioPacketMs!=0 && ms!=audioPacketMs)){event("log","Receiver rejected: capacity or audio packet duration");new Thread(()->NativeSessions.disconnect(s),"RootReject").start();return;}
                receivers.put(s,new JSONObject().put("id",s).put("ordinal",++receiverOrdinal).put("width",w).put("height",h).put("fps",fps));
                if(pipeline!=null){pipeline.addOutput(s,surface,w,h);receiverState();return;}
                session=s;audioPacketMs=ms;
                if(remoteInput!=null)remoteInput.begin(s);
                pipeline=new VideoPipeline(context,surface,w,h,scaleMode,new VideoPipeline.Events(){
                    public void message(String message){event("log",message);}
                    public void error(String error){event("error",error);}
                    public void geometry(long id,CropGeometry.Mapping value){if(id==session){mapping=value;if(remoteInput!=null)remoteInput.mapping(value);}}
                },s);
                pipeline.setFrameLimit(frameLimit);
                android.util.DisplayMetrics size=VideoPipeline.metrics(context.getSystemService(DisplayManager.class));
                pipeline.start(size.widthPixels,size.heightPixels,true,(input,width,height)->{
                    VirtualDisplay vd=context.getSystemService(DisplayManager.class).createVirtualDisplay("MoonCastRoot",width,height,context.getResources().getDisplayMetrics().densityDpi,
                        input,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR|DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC);
                    if(vd==null)throw new IllegalStateException("ROM 拒绝创建镜像显示");
                    return vd;
                });
                if (!audioPump) { audioPump=true; SunshineServer.startAudioRecording(new AudioFeed(),48*Math.max(1,ms)); }
                event("stream",w+" × "+h+" · "+fps+" FPS · Root 镜像");receiverState();
            } catch (Throwable e) { event("error","Root 镜像失败: "+e+"；请切换非 Root 模式"); }
        });
    }
    private void receiverState(){event("receivers",new JSONArray(receivers.values()).toString());}
    @Override public void stopDisplay(long s) {
        VideoPipeline current=pipeline;if(current!=null){java.util.concurrent.CountDownLatch removed=new java.util.concurrent.CountDownLatch(1);current.removeOutput(s,removed::countDown);try{removed.await(2,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
        main.post(()->{if(receivers.remove(s)==null)return;if(receivers.isEmpty() && pipeline!=null){if(remoteInput!=null)remoteInput.begin(0);session=0;pipeline.close();pipeline=null;event("end","");}receiverState();});
    }
    @Override public void error(String error) { event("error",error); }
    private synchronized void event(String kind,String text) {
        try { if (events!=null) { events.writeUTF(new JSONObject().put("kind",kind).put("text",text).toString()); events.flush(); } }
        catch (Exception ignored) {}
    }
    private void control(boolean enabled){
        if(watchParty)enabled=false;
        try{
            if(remoteInput==null && enabled){
                remoteInput=new RemoteInputController(main,new RootInputInjector(),true,text->event(text.startsWith("反控已关闭")?"controlOff":"log",text));
                remoteInput.begin(session);if(mapping!=null)remoteInput.mapping(mapping);InputBridge.receiver=remoteInput;
            }
            if(remoteInput!=null)remoteInput.enabled(enabled);
            event("control",enabled?"Root 反控已就绪":"远程操控已关闭");
        }catch(Exception e){event("controlOff","Root 反控不可用: "+e.getMessage());}
    }
    private void shutdown() {
        if(remoteInput!=null)try{remoteInput.close();}catch(RuntimeException ignored){}
        if(pipeline!=null)pipeline.close();
        if (display!=null) display.release();
        try { if (socket!=null) socket.close(); } catch (IOException ignored) {}
        android.os.Process.killProcess(android.os.Process.myPid());
    }
}
