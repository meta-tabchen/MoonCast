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
    private boolean watchParty,panelWanted;
    private DisplayPowerLease power;
    private long lastPanelRenew,ownerHeartbeat;
    private final Runnable panelWatch=new Runnable(){public void run(){
        try{long now=SystemClock.elapsedRealtime();if(power!=null)power.tick(now);if(panelWanted && now-ownerHeartbeat>=30000)restorePanel();
            if(panelWanted && pipeline!=null && !receivers.isEmpty() && pipeline.statistics().totalFrames()>0 && now-lastPanelRenew>=5000){
                if(power==null)power=new DisplayPowerLease(new PhysicalDisplayPower(context));power.renew(now,30000);lastPanelRenew=now;event("panel",PhoneMessages.message(R.string.phone_root_panel_off));
            }
        }catch(Exception e){panelWanted=false;event("panel",PhoneMessages.message(R.string.phone_panel_power_unavailable , e.getMessage()));restorePanel();}
        main.postDelayed(this,1000);
    }};
    private void restorePanel(){panelWanted=false;if(power!=null)try{power.restore();event("panel",PhoneMessages.message(R.string.phone_panel_restored));}catch(Exception e){event("panel",PhoneMessages.message(R.string.phone_panel_restore_retry , e.getMessage()));}}
    private int scaleMode=CropGeometry.VIDEO_REGION;
    private int frameLimit=120;
    private long session;
    private boolean audioPump;
    private RemoteInputController remoteInput;
    private CropGeometry.Mapping mapping;
    public static void main(String[] args) {
        RootDaemon daemon=new RootDaemon();
        try {
            if (args.length!=10 || android.os.Process.myUid()!=0) throw new IllegalArgumentException("Root daemon requires uid 0 and ten arguments");
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
            daemon.ownerHeartbeat=SystemClock.elapsedRealtime();daemon.panelWanted=false;daemon.main.post(daemon.panelWatch);daemon.watchParty=Boolean.parseBoolean(args[8]);daemon.control(!daemon.watchParty && Boolean.parseBoolean(args[6]));
            new Thread(() -> {
                try {
                    DataInputStream commands=new DataInputStream(daemon.socket.getInputStream());
                    for (;;) {
                        String command=commands.readUTF();
                        if (command.equals("STOP")) break;
                        if (command.matches("PIN [0-9]{4}")) SunshineServer.submitPin(command.substring(4));
                        if(command.matches("SCALE [0-4]"))daemon.main.post(()->{daemon.scaleMode=Integer.parseInt(command.substring(6));if(daemon.pipeline!=null)daemon.pipeline.setMode(daemon.scaleMode);});
                        if(command.matches("DISCONNECT -?[0-9]+"))daemon.main.post(()->{long id=Long.parseLong(command.substring(11));if(daemon.receivers.containsKey(id))new Thread(()->NativeSessions.disconnect(id),"RootDisconnect").start();});
                        if(command.matches("PANEL_SET [0-9]+ [01]"))daemon.main.post(()->{
                            String[] parts=command.split(" ");boolean off=parts[2].equals("1"),ok=false;
                            try{if(off){if(daemon.pipeline==null||daemon.receivers.isEmpty()||daemon.pipeline.statistics().totalFrames()==0)throw new IllegalStateException("No active capture");if(daemon.power==null)daemon.power=new DisplayPowerLease(new PhysicalDisplayPower(daemon.context));daemon.ownerHeartbeat=SystemClock.elapsedRealtime();daemon.power.renew(daemon.ownerHeartbeat,30000);daemon.lastPanelRenew=daemon.ownerHeartbeat;daemon.panelWanted=true;ok=true;}else{daemon.restorePanel();ok=daemon.power==null||!daemon.power.pending();}}
                            catch(Exception e){daemon.restorePanel();}
                            daemon.event("panelResult","{\"token\":"+parts[1]+",\"success\":"+ok+"}");
                        });
                        if(command.matches("PAUSE [01]"))daemon.main.post(()->{if(daemon.pipeline!=null)daemon.pipeline.setPaused(command.endsWith("1"));});
                        if(command.equals("PANEL_HEARTBEAT"))daemon.main.post(()->daemon.ownerHeartbeat=SystemClock.elapsedRealtime());
                        if(command.equals("RESTORE_DISPLAY"))daemon.main.post(daemon::restorePanel);
                        if(command.equals("RESCAN"))daemon.main.post(()->{if(daemon.pipeline!=null)daemon.pipeline.rescan();});
                        if(command.matches("CONTROL [01]"))daemon.main.post(()->daemon.control(command.endsWith("1")));
                    }
                } catch (IOException ignored) {} finally { daemon.main.post(daemon::shutdown); }
            },"OwnerWatchdog").start();
            new Thread(SunshineServer::start,"RootSunshine").start();
            daemon.event("log",PhoneMessages.message(R.string.phone_root_started));
            Looper.loop();
        } catch (Throwable e) { e.printStackTrace(); daemon.event("error",PhoneMessages.message(R.string.phone_root_init_failed , e.toString())); daemon.shutdown(); }
    }
    @Override public void pinRequested() { event("pin",""); }
    @Override public void uuid(String ignored) {}
    @Override public void streamStats(long s,int w,int h,int targetFps,int targetKbps,double fps,double mbps){try{event("encoderStats",new JSONObject().put("id",s).put("width",w).put("height",h).put("targetFps",targetFps).put("targetKbps",targetKbps).put("fps",fps).put("mbps",mbps).toString());}catch(org.json.JSONException ignored){}}
    @Override public void createDisplay(long s,int w,int h,int fps,int ms,Surface surface,boolean audio) {
        main.post(() -> {
            try {
                if(!NativeSessions.active(s))return;
                if(receivers.size()>=(watchParty?3:1) || (audioPacketMs!=0 && ms!=audioPacketMs)){event("log",PhoneMessages.message(R.string.phone_root_receiver_rejected));new Thread(()->NativeSessions.disconnect(s),"RootReject").start();return;}
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
                    if(vd==null)throw new IllegalStateException(PhoneMessages.message(R.string.phone_root_display_rejected));
                    return vd;
                });
                if (!audioPump) { audioPump=true; SunshineServer.startAudioRecording(new AudioFeed(),48*Math.max(1,ms)); }
                event("stream",PhoneMessages.message(R.string.phone_root_stream , w, h, fps));receiverState();
            } catch (Throwable e) { event("error",PhoneMessages.message(R.string.phone_root_mirror_failed , e.toString())); }
        });
    }
    private void receiverState(){event("receivers",new JSONArray(receivers.values()).toString());}
    @Override public void stopDisplay(long s) {
        VideoPipeline current=pipeline;if(current!=null){java.util.concurrent.CountDownLatch removed=new java.util.concurrent.CountDownLatch(1);current.removeOutput(s,removed::countDown);try{removed.await(2,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
        main.post(()->{if(receivers.remove(s)==null)return;if(receivers.isEmpty() && pipeline!=null){restorePanel();if(remoteInput!=null)remoteInput.begin(0);session=0;pipeline.close();pipeline=null;event("end","");}receiverState();});
    }
    @Override public void error(String error) { event("error",PhoneMessages.hasMessage(error)?error:PhoneMessages.message(R.string.phone_native_runtime_failed,error)); }
    private synchronized void event(String kind,String text) {
        try { if (events!=null) { events.writeUTF(new JSONObject().put("kind",kind).put("text",text).toString()); events.flush(); } }
        catch (Exception ignored) {}
    }
    private void control(boolean enabled){
        if(watchParty)enabled=false;
        try{
            if(remoteInput==null && enabled){
                remoteInput=new RemoteInputController(main,new RootInputInjector(),true,text->event(PhoneMessages.is(text,R.string.phone_input_disabled_error)?"controlOff":"log",text));
                remoteInput.begin(session);if(mapping!=null)remoteInput.mapping(mapping);InputBridge.receiver=remoteInput;
            }
            if(remoteInput!=null)remoteInput.enabled(enabled);
            event("control",enabled?PhoneMessages.message(R.string.phone_root_control_ready):PhoneMessages.message(R.string.phone_input_disabled));
        }catch(Exception e){event("controlOff",PhoneMessages.message(R.string.phone_root_input_unavailable , e.getMessage()));}
    }
    private void shutdown() {
        if(main!=null)main.removeCallbacks(panelWatch);if(power!=null)for(int i=0;i<3 && power.pending();i++)try{power.restore();}catch(Exception e){event("panel",PhoneMessages.message(R.string.phone_panel_restore_failed , e.toString()));SystemClock.sleep(100);}
        if(remoteInput!=null)try{remoteInput.close();}catch(RuntimeException ignored){}
        if(pipeline!=null)pipeline.close();
        if (display!=null) display.release();
        try { if (socket!=null) socket.close(); } catch (IOException ignored) {}
        android.os.Process.killProcess(android.os.Process.myPid());
    }
}
