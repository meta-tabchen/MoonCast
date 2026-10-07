package com.mooncast.host;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.hardware.display.*;
import android.media.*;
import android.media.projection.*;
import android.net.nsd.*;
import android.os.*;
import android.view.Surface;
import io.github.jqssun.displaymirror.sunshine.SunshineServer;
import io.github.jqssun.displaymirror.sunshine.NativeSessions;
import java.net.*;
import java.io.*;
import java.util.*;

/** Dedicated process owns all native resources. Stopping terminates that process, not the UI. */
public final class HostService extends Service implements SunshineServer.Listener {
    static final int STATUS=1, PIN=2, STOP=3, SCALE=4, CONTROL=5, CROP_RESET=6, LAUNCH_APP=7, DISCONNECT=8, RESTORE_DISPLAY=9;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Messenger binder = new Messenger(new Handler(Looper.getMainLooper(), msg -> {
        if (msg.what == STATUS) {
            if (msg.replyTo != null) try { Message response = Message.obtain(null, STATUS); response.setData(snapshot()); msg.replyTo.send(response); } catch (RemoteException ignored) {}
        } else if (msg.what == PIN) {
            String pin = msg.getData().getString("pin", "");
            if (pin.matches("[0-9]{4}")) {
                pinPending=false;
                if (HostService.this.root != null) HostService.this.root.pin(pin); else new Thread(() -> SunshineServer.submitPin(pin), "Pairing").start();
                log(getString(R.string.ui_pin_submitted_check_the_receiver_for_the_pairing_result));
            }
        } else if (msg.what == STOP) stopHost();
        else if(msg.what==SCALE) updateScale(msg.arg1);
        else if(msg.what==CONTROL) updateControl(msg.arg1!=0);
        else if(msg.what==CROP_RESET) rescanCrop();
        else if(msg.what==LAUNCH_APP) launchApp(msg.getData().getString("component",""));
        else if(msg.what==DISCONNECT)disconnectReceiver(msg.getData().getLong("session"));
        else if(msg.what==RESTORE_DISPLAY)restorePanel();
        return true;
    }));
    private final ArrayList<String> logs = new ArrayList<>();
    private MediaProjection projection;
    private VirtualDisplay display;
    private Surface encoderSurface;
    private AudioRecord audioRecord;
    private AudioFeed audioFeed;
    private LocalAudioMute localAudioMute;
    private RemoteInputController remoteInput;
    private RootInputBackend rootInput;
    private ShizukuInputBackend shizukuInput;
    private boolean controlEnabled,controlRoot,rootInputStarted;
    private String rootControlStatus="";
    private NsdManager nsd;
    private NsdManager.RegistrationListener registration;
    private PowerManager.WakeLock wakeLock;
    private RootBackend root;
    private volatile VideoPipeline pipeline;
    private int scaleMode=CropGeometry.VIDEO_REGION;
    private String state="", detail="", error="", hostName="MoonCast";
    private boolean started, stopping, pinPending, sendAudio, audioPump, muteLocal;
    private long session;
    private record Receiver(int ordinal,int width,int height,int fps){}
    private final LinkedHashMap<Long,Receiver> receivers=new LinkedHashMap<>();
    private int receiverOrdinal,receiverLimit=1,audioPacketMs;
    private boolean watchParty,panelWanted,panelOff;
    private volatile boolean panelRenewing;
    private String panelState="";
    private final Runnable panelHeartbeat=new Runnable(){public void run(){
        if(stopping || !panelWanted)return;
        if(root!=null){root.panelHeartbeat();main.postDelayed(this,5000);return;}
        if(!receivers.isEmpty() && pipeline!=null && pipeline.statistics().totalFrames()>0){
            ShizukuInputBackend backend=shizukuInput;
            if(!panelRenewing && backend!=null && backend.ready()){panelRenewing=true;new Thread(()->{try{boolean off=backend.displayPower(true);main.post(()->{if(!panelWanted || stopping){try{backend.displayPower(false);}catch(RuntimeException ignored){}return;}panelOff=off;panelState=getString(R.string.ui_panel_off);});}catch(RuntimeException e){main.post(()->{panelWanted=false;panelState=getString(R.string.ui_panel_unavailable);log(panelState+" "+e.getMessage());restorePanel();});}finally{panelRenewing=false;}},"PanelPower").start();}
        }
        if(panelWanted)main.postDelayed(this,5000);
    }};
    private void restorePanel(){panelWanted=false;main.removeCallbacks(panelHeartbeat);if(root!=null)root.restoreDisplay();if(shizukuInput!=null)try{shizukuInput.displayPower(false);panelOff=false;panelState=getString(R.string.ui_panel_restored);}catch(RuntimeException e){panelState=getString(R.string.ui_panel_unavailable);log(panelState+" "+e.getMessage());}}
    private int frameLimit=120,captureSource,independentDisplayId=-1;
    private int capturedW,capturedH;
    private boolean rootCapture;
    private final TrafficRate trafficRate=new TrafficRate();

    private void rescanCrop(){if(pipeline!=null)pipeline.rescan();if(root!=null)root.rescan();}
    private void disconnectReceiver(long id){if(root!=null){if(receivers.containsKey(id))root.disconnect(id);}else if(receivers.containsKey(id))new Thread(()->NativeSessions.disconnect(id),"DisconnectReceiver").start();}
    private void launchApp(String component){if(independentDisplayId<0 || shizukuInput==null)return;ShizukuInputBackend backend=shizukuInput;int id=independentDisplayId;new Thread(()->{try{backend.launch(component,id);}catch(RuntimeException e){log(getString(R.string.ui_independent_app_rejected)+" "+e.getMessage());}},"LaunchIndependentApp").start();}

    private void updateScale(int value) {
        scaleMode=Math.max(0,Math.min(CropGeometry.CINEMA,value));
        if(pipeline!=null)pipeline.setMode(scaleMode);
        if(root!=null)root.scale(scaleMode);
        log(getString(R.string.ui_picture_mode_updated)+scaleMode);
    }
    private void updateControl(boolean enabled){
        if(captureSource==1 || watchParty)enabled=false;
        controlEnabled=enabled;
        if(remoteInput!=null)remoteInput.enabled(enabled);
        if(root!=null)root.control(enabled);
        if(enabled && rootInput!=null && !rootInputStarted){rootInputStarted=true;rootInput.start();}
        log(enabled?getString(R.string.ui_remote_input_enabled):getString(R.string.ui_remote_input_disabled_releasing_touch_and_keys));
    }

    @Override public IBinder onBind(Intent intent) { return binder.getBinder(); }
    @Override public void onCreate(){
        super.onCreate();state=getString(R.string.ui_starting);rootControlStatus=getString(R.string.ui_waiting_for_root_connection);localAudioMute=new LocalAudioMute(this);
        restoreLocalAudio();
    }
    @Override public int onStartCommand(Intent intent, int flags, int id) {
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }
        if ("stop".equals(intent.getAction())) { stopHost(); return START_NOT_STICKY; }
        if (started) return START_NOT_STICKY;
        started=true;
        captureSource=intent.getIntExtra("source",0);
        boolean isRoot=captureSource==0 && intent.getBooleanExtra("root", false);rootCapture=isRoot;
        frameLimit=intent.getIntExtra("frameLimit",120);watchParty=intent.getBooleanExtra("watchParty",false);receiverLimit=watchParty?3:1;panelWanted=intent.getBooleanExtra("panelOff",false);panelState=getString(panelWanted?R.string.ui_panel_waiting:R.string.ui_off);
        controlEnabled=captureSource!=1 && !watchParty && intent.getBooleanExtra("control",false);controlRoot=isRoot || intent.getBooleanExtra("controlRoot",false);
        if(!isRoot){
            int inputBackend=intent.getIntExtra("controlBackend",controlRoot?1:0);
            if(inputBackend==2 || captureSource==2 || panelWanted){shizukuInput=new ShizukuInputBackend(this,this::log);shizukuInput.start();}
            RemoteInputController.Backend backend;
            if(inputBackend==2)backend=shizukuInput;
            else if(controlRoot){rootInput=new RootInputBackend(this,this::log);backend=rootInput;}
            else backend=ControlAccessibilityService.backend();
            if(captureSource==2 && inputBackend==0){controlEnabled=false;log(getString(R.string.ui_independent_control_requires_privilege));}
            remoteInput=new RemoteInputController(main,backend,controlEnabled,this::log);InputBridge.receiver=remoteInput;
            if(controlEnabled && rootInput!=null){rootInputStarted=true;rootInput.start();}
        }
        hostName=intent.getStringExtra("name"); if (hostName==null || hostName.trim().isEmpty()) hostName="MoonCast";
        sendAudio=intent.getBooleanExtra("audio",false);
        muteLocal=!isRoot && sendAudio && intent.getBooleanExtra("muteLocal",false);
        scaleMode=intent.getIntExtra("scaleMode",CropGeometry.VIDEO_REGION);
        makeNotification(isRoot || captureSource==2 ? ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE : ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        wakeLock=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"MoonCast:host");
        wakeLock.acquire(6*60*60*1000L);
        log(isRoot ? getString(R.string.ui_starting_root_host) : getString(R.string.ui_starting_screen_capture_host));
        try {
            if (!isRoot && captureSource!=2) {
                Intent grant=intent.getParcelableExtra("grant");
                if (grant==null) throw new IllegalArgumentException(getString(R.string.ui_missing_screen_capture_permission_start_again));
                projection=getSystemService(MediaProjectionManager.class).getMediaProjection(Activity.RESULT_OK, grant);
                projection.registerCallback(new MediaProjection.Callback() {
                    @Override public void onStop() { if (!stopping && error!=null && error.isEmpty()) { log(getString(R.string.ui_system_ended_screen_sharing)); stopHost(); } }
                    @Override public void onCapturedContentResize(int width,int height){capturedW=width;capturedH=height;if(pipeline!=null)pipeline.resize(width,height);}
                }, main);
            }
            final boolean hevc=intent.getBooleanExtra("hevc",true) && !EncoderSupport.hardware("video/hevc").isEmpty();
            if (EncoderSupport.hardware("video/avc").isEmpty()) throw new IllegalStateException(getString(R.string.ui_no_h_264_hardware_encoder_with_surface_input_found));
            new Thread(() -> {
                try {
                    HostIdentity.ensure(getFilesDir());
                    if(shizukuInput!=null){long deadline=SystemClock.elapsedRealtime()+15000;while(!stopping && !shizukuInput.ready() && SystemClock.elapsedRealtime()<deadline)Thread.sleep(100);if(!stopping && !shizukuInput.ready())throw new IllegalStateException(getString(R.string.ui_shizuku_hint));}
                    if (stopping) return;
                    if (isRoot) {
                        root=new RootBackend(this, this::rootEvent);
                        root.start(hostName,hevc,scaleMode,controlEnabled,frameLimit,watchParty,panelWanted);
                    } else {
                        SunshineServer.listener=this;
                        SunshineServer.setSunshineName(hostName);
                        SunshineServer.setCertPath(new File(getFilesDir(),"host-cert.pem").getPath());
                        SunshineServer.setPkeyPath(new File(getFilesDir(),"host-key.pem").getPath());
                        SunshineServer.setFileStatePath(new File(getFilesDir(),"paired-clients.json").getPath());
                        SunshineServer.setHevcSupported(hevc);
                        if(watchParty && !NativeSessions.available())throw new IllegalStateException(getString(R.string.ui_session_bridge_unavailable));
                        new Thread(SunshineServer::start,"Sunshine").start();
                    }
                    waitForServer();if(panelWanted)main.post(panelHeartbeat);
                } catch (Exception | LinkageError e) { error(getString(R.string.ui_start_failed)+e.getMessage()); }
            },"HostInit").start();
        } catch (Exception e) { error(e.getMessage()); }
        return START_NOT_STICKY;
    }
    private void waitForServer() throws Exception {
        for (int i=0;i<100 && !stopping;i++) {
            try {
                HttpURLConnection c=(HttpURLConnection)new URL("http://127.0.0.1:47989/serverinfo?uniqueid=mooncast-health").openConnection();
                c.setConnectTimeout(250); c.setReadTimeout(250);
                String xml;
                try (InputStream in=c.getInputStream(); ByteArrayOutputStream data=new ByteArrayOutputStream()) {
                    byte[] buffer=new byte[4096]; int count;
                    while ((count=in.read(buffer))!=-1) { data.write(buffer,0,count); if(data.size()>65536) throw new IOException(getString(R.string.ui_health_check_response_too_large)); }
                    xml=data.toString("UTF-8");
                }
                finally { c.disconnect(); }
                if (xml.contains(hostName.replace("&","&amp;"))) {
                    main.post(() -> { if (!stopping) { state=getString(R.string.ui_waiting_for_receiver); log(getString(R.string.ui_host_ready_add_the_phone_ip_in_moonlight_if_needed)); advertise(); } });
                    return;
                }
            } catch (IOException ignored) {}
            Thread.sleep(200);
        }
        if (!stopping) throw new IOException(getString(R.string.ui_host_port_unavailable_check_whether_another_sunshine_host_uses_port_47));
    }
    private void advertise() {
        nsd=getSystemService(NsdManager.class);
        NsdServiceInfo service=new NsdServiceInfo(); service.setServiceName(hostName); service.setServiceType("_nvstream._tcp."); service.setPort(47989);
        registration=new NsdManager.RegistrationListener() {
            @Override public void onServiceRegistered(NsdServiceInfo i) { log(getString(R.string.ui_lan_discovery)+i.getServiceName()); }
            @Override public void onRegistrationFailed(NsdServiceInfo i,int code) { log(getString(R.string.ui_discovery_failed)+code+getString(R.string.ui_add_the_ip_manually)); }
            @Override public void onServiceUnregistered(NsdServiceInfo i) {}
            @Override public void onUnregistrationFailed(NsdServiceInfo i,int code) {}
        };
        try { nsd.registerService(service,NsdManager.PROTOCOL_DNS_SD,registration); } catch (RuntimeException e) { log(getString(R.string.ui_discovery_unavailable_add_the_ip_manually)); }
    }
    private void makeNotification(int type) {
        NotificationManager manager=getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("host",getString(R.string.ui_casting_host),NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,HostService.class).setAction("stop"),PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(this,"host").setSmallIcon(R.drawable.ic_launcher).setContentTitle(getString(R.string.ui_mooncast_is_running))
            .setContentText(getString(R.string.ui_lan_casting_tap_to_open_stop_at_any_time)).setContentIntent(open).setOngoing(true)
            .addAction(new Notification.Action.Builder(null,getString(R.string.ui_stop),stop).build()).build();
        if (Build.VERSION.SDK_INT>=29) startForeground(7,n,type); else startForeground(7,n);
    }
    @Override public void pinRequested() { main.post(() -> { pinPending=true; state=getString(R.string.ui_enter_the_receiver_pin); log(getString(R.string.ui_receiver_requested_pairing_enter_its_4_digit_pin_on_the_phone)); }); }
    @Override public void uuid(String uuid) { log(getString(R.string.ui_host_identity_loaded)); }
    @Override public void createDisplay(long s,int w,int h,int fps,int packetMs,Surface surface,boolean captureAudio) {
        main.post(() -> {
            if (stopping || !NativeSessions.active(s)) return;
            if(receivers.size()>=receiverLimit || (pipeline!=null && packetMs!=audioPacketMs)){
                log(getString(R.string.ui_receiver_rejected));new Thread(()->NativeSessions.disconnect(s),"RejectReceiver").start();return;
            }
            try {
                receivers.put(s,new Receiver(++receiverOrdinal,w,h,fps));
                if(pipeline!=null){pipeline.addOutput(s,surface,w,h);startAudio(packetMs,captureAudio);updateReceivers();return;}
                session=s;audioPacketMs=packetMs;encoderSurface=surface;
                if(remoteInput!=null)remoteInput.begin(s);
                if (projection==null && captureSource!=2) throw new IllegalStateException(getString(R.string.ui_screen_capture_permission_expired_start_again));
                MediaProjection capture=projection;
                pipeline=new VideoPipeline(this,surface,w,h,scaleMode,new VideoPipeline.Events(){
                    public void message(String text){log(text);}
                    public void error(String text){HostService.this.error(text);}
                    public void geometry(long id,CropGeometry.Mapping mapping){if(id==session && remoteInput!=null)remoteInput.mapping(mapping);}
                },s);
                pipeline.setFrameLimit(frameLimit);
                android.util.DisplayMetrics size=VideoPipeline.metrics(getSystemService(DisplayManager.class));
                int width=captureSource==2?w:capturedW>0?capturedW:size.widthPixels,height=captureSource==2?h:capturedH>0?capturedH:size.heightPixels;
                pipeline.start(width,height,captureSource==0,(input,captureW,captureH)->{
                    if(stopping)return null;
                    if(captureSource==2){
                        independentDisplayId=shizukuInput.createDisplay(input,captureW,captureH,getResources().getDisplayMetrics().densityDpi);
                        shizukuInput.display(independentDisplayId);if(rootInput!=null)rootInput.display(independentDisplayId);return null;
                    }
                    return capture.createVirtualDisplay("MoonCast",captureW,captureH,getResources().getDisplayMetrics().densityDpi,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,input,null,main);
                });
                startAudio(packetMs, captureAudio);
                updateReceivers();
                log(getString(R.string.ui_video_surface_connected)+w+" × "+h+" · "+fps+" FPS");
            } catch (Exception e) { error(getString(R.string.ui_capture_creation_failed)+e.getMessage()); }
        });
    }
    private void startAudio(int packetMs,boolean requested) {
        if(audioFeed==null)audioFeed=new AudioFeed(()->main.post(()->{
            if(stopping || audioRecord==null)return;
            restoreLocalAudio();audioFeed.set(null);
            try{audioRecord.stop();}catch(Exception ignored){}
            audioRecord.release();audioRecord=null;
            log(getString(R.string.ui_audio_capture_interrupted_phone_volume_restored_video_continues));
        }));
        if (audioRecord==null && projection!=null && requested && sendAudio && Build.VERSION.SDK_INT>=29 && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)==android.content.pm.PackageManager.PERMISSION_GRANTED) {
            try {
                AudioPlaybackCaptureConfiguration capture=new AudioPlaybackCaptureConfiguration.Builder(projection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA).addMatchingUsage(AudioAttributes.USAGE_GAME).addMatchingUsage(AudioAttributes.USAGE_UNKNOWN).build();
                int bytes=Math.max(19200,AudioRecord.getMinBufferSize(48000,AudioFormat.CHANNEL_IN_STEREO,AudioFormat.ENCODING_PCM_FLOAT));
                audioRecord=new AudioRecord.Builder().setAudioFormat(new AudioFormat.Builder().setSampleRate(48000).setChannelMask(AudioFormat.CHANNEL_IN_STEREO).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).build())
                    .setBufferSizeInBytes(bytes).setAudioPlaybackCaptureConfig(capture).build();
                audioRecord.startRecording(); audioFeed.set(audioRecord);
                if(audioRecord.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING)throw new IllegalStateException(getString(R.string.ui_audio_capture_did_not_start));
                if(muteLocal)try{localAudioMute.silence();log(getString(R.string.ui_phone_media_audio_muted_during_casting_restored_on_stop));}
                catch(RuntimeException e){log(getString(R.string.ui_phone_muting_unavailable)+e.getMessage());}
                log(getString(R.string.ui_playback_audio_opus_source_apps_must_allow_capture));
            } catch (Exception e) {
                audioFeed.set(null);
                if(audioRecord!=null){try{audioRecord.stop();}catch(Exception ignored){}audioRecord.release();audioRecord=null;}
                restoreLocalAudio();log(getString(R.string.ui_audio_capture_unavailable_video_continues)+e.getMessage());
            }
        } else log(getString(R.string.ui_video_only_sound_stays_on_the_phone));
        if (!audioPump) { audioPump=true; SunshineServer.startAudioRecording(audioFeed,48*Math.max(1,packetMs)); }
    }
    private void updateReceivers(){state=getString(R.string.ui_casting);detail=getString(R.string.ui_receivers_connected,receivers.size());}
    @Override public void stopDisplay(long s) {
        // Detach EGL while the native encoder still owns its Surface, before this callback returns.
        VideoPipeline current=pipeline;
        if(current!=null){java.util.concurrent.CountDownLatch removed=new java.util.concurrent.CountDownLatch(1);current.removeOutput(s,removed::countDown);try{removed.await(2,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
        main.post(()->{if(stopping || receivers.remove(s)==null)return;if(receivers.isEmpty()){log(getString(R.string.ui_receiver_disconnected_next_session_needs_screen_capture_permission));stopHost();}else updateReceivers();});
    }
    @Override public void error(String text) { main.post(() -> { if (!stopping) { error=text; state=getString(R.string.ui_error); log(text); cleanupCapture(); } }); }
    private void rootEvent(String kind,String text) {
        main.post(() -> {
            switch (kind) {
                case "panel" -> {panelState=text;log(text);}
                case "pin" -> pinRequested();
                case "receivers" -> {try{var items=new org.json.JSONArray(text);receivers.clear();for(int i=0;i<items.length();i++){var r=items.getJSONObject(i);receivers.put(r.getLong("id"),new Receiver(r.getInt("ordinal"),r.getInt("width"),r.getInt("height"),r.getInt("fps")));}if(!receivers.isEmpty())updateReceivers();}catch(org.json.JSONException e){log("Invalid receiver inventory");}}
                case "stream" -> { state=getString(R.string.ui_casting_root); detail=text; log(text); }
                case "end" -> { state=getString(R.string.ui_waiting_for_receiver_root); detail=""; log(getString(R.string.ui_root_session_ended_waiting_for_reconnection)); }
                case "error" -> error(text);
                case "control" -> {rootControlStatus=text;log(text);}
                case "controlOff" -> {controlEnabled=false;rootControlStatus=text;log(text);}
                default -> log(text);
            }
        });
    }
    private void cleanupCapture() {
        restorePanel();
        if(remoteInput!=null){remoteInput.close();remoteInput=null;rootInput=null;}
        if(shizukuInput!=null){shizukuInput.close();shizukuInput=null;}
        restoreLocalAudio();
        if(pipeline!=null){pipeline.close();pipeline=null;}independentDisplayId=-1;
        if (display!=null) { display.release(); display=null; }
        if (audioFeed!=null) audioFeed.set(null);
        if (audioRecord!=null) { try { audioRecord.stop(); } catch (Exception ignored) {} audioRecord.release(); audioRecord=null; }
        // The native encoder owns the Surface. Do not release it from Java while it drains.
        encoderSurface=null;receivers.clear();
        if (projection!=null) { MediaProjection old=projection; projection=null; old.stop(); }
    }
    private void restoreLocalAudio(){
        if(localAudioMute!=null)try{localAudioMute.restore();}
        catch(RuntimeException e){log(getString(R.string.ui_volume_restoration_failed_retrying_on_next_launch)+e.getMessage());}
    }
    private void stopHost() {
        if (stopping) return;
        stopping=true; state=getString(R.string.ui_stopped); cleanupCapture();
        if (root!=null) root.stop();
        if (nsd!=null && registration!=null) try { nsd.unregisterService(registration); } catch (Exception ignored) {}
        if (wakeLock!=null && wakeLock.isHeld()) wakeLock.release();
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
        main.postDelayed(() -> android.os.Process.killProcess(android.os.Process.myPid()),250);
    }
    @Override public void onDestroy() { stopHost(); super.onDestroy(); }
    private void log(String text) {
        if (Looper.myLooper()!=Looper.getMainLooper()) { main.post(() -> log(text)); return; }
        android.util.Log.i("MoonCast",text);
        logs.add(java.time.LocalTime.now().withNano(0)+"  "+text);
        if (logs.size()>80) logs.remove(0);
    }
    private Bundle snapshot() {
        Bundle b=new Bundle(); b.putString("state",state); b.putString("detail",detail); b.putString("error",error);
        long[] ids=new long[receivers.size()];String[] labels=new String[receivers.size()];int index=0;for(var entry:receivers.entrySet()){ids[index]=entry.getKey();Receiver r=entry.getValue();labels[index++]=getString(R.string.ui_receiver_label,r.ordinal())+" · "+r.width()+"×"+r.height()+" · "+r.fps()+" FPS";}b.putLongArray("receivers",ids);b.putStringArray("receiverLabels",labels);
        b.putString("panelState",panelState);b.putBoolean("panelOff",panelOff);b.putInt("displayId",independentDisplayId);b.putInt("scaleMode",scaleMode); b.putBoolean("pinPending",pinPending); b.putString("logs",String.join("\n",logs));
        b.putBoolean("control",remoteInput!=null?remoteInput.enabled():controlEnabled);
        b.putString("metrics",metrics());
        b.putString("controlStatus",remoteInput!=null?remoteInput.status(this):!controlEnabled?getString(R.string.ui_off):rootControlStatus);return b;
    }
    private String metrics(){
        if(rootCapture)return getString(R.string.ui_dashboard_root_unavailable);
        double mbps=trafficRate.sample(android.net.TrafficStats.getUidTxBytes(android.os.Process.myUid()),SystemClock.elapsedRealtime());
        FrameStatistics.Sample stats=pipeline==null?new FrameStatistics.Sample(0,0,0):pipeline.statistics();
        Intent battery=registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int temp=battery==null?0:battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,0);
        String rate=mbps<0?"—":String.format(java.util.Locale.getDefault(),"%.1f",mbps);
        String temperature=temp<=0?"—":String.format(java.util.Locale.getDefault(),"%.1f",temp/10d);
        int thermal=Build.VERSION.SDK_INT>=29?getSystemService(PowerManager.class).getCurrentThermalStatus():-1;
        return getString(R.string.ui_dashboard_values,rate,stats.fps(),stats.submissionMs(),temperature,thermal<0?"—":Integer.toString(thermal));
    }
    static String addresses(Context context) {
        ArrayList<String> result=new ArrayList<>();
        try { for (NetworkInterface ni: Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!ni.isUp() || ni.isLoopback()) continue;
            for (InetAddress a:Collections.list(ni.getInetAddresses())) if (a instanceof Inet4Address && a.isSiteLocalAddress()) result.add(a.getHostAddress());
        }} catch (SocketException ignored) {}
        return result.isEmpty()?context.getString(R.string.ui_no_lan_connection):String.join("  /  ",result);
    }
}
