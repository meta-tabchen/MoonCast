package com.mooncast.host;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.projection.*;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;

public final class MainActivity extends Activity {
    @Override protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(com.mooncast.locale.AppLocale.wrap(base));
    }

    private static final int DOCUMENT=52;
    private CastSessionCoordinator session;
    private final java.util.ArrayList<View> moonlightOnly=new java.util.ArrayList<>();
    private final java.util.ArrayList<View> nativeSenderOnly=new java.util.ArrayList<>();
    private LinearLayout airPanel, advancedContent, airAdvanced;
    private Button advancedToggle;
    private boolean advancedExpanded;
    private LinearLayout browserPanel,browserConnect;
    private RadioButton browserTab;
    private Button browserStart,browserStop,browserCopy;
    private TextView browserState,browserDetail,browserLink,browserQualitySummary,browserQrStatus;
    private Spinner browserSource,browserAddress;
    private CompoundButton browserAudio;
    private String browserUrls="",browserSelectedUrl="";
    private ImageView browserQrImage;
    private android.graphics.Bitmap browserQrBitmap;
    private String browserQrUrl="";
    private boolean browserQrFailed;
    private BrowserQuality pendingBrowserQuality=BrowserQuality.DEFAULT;
    private BrowserQuality browserClearQuality;
    private MyTvPicker airPicker;
    private AlertDialog airChangeDialog;
    private Button airChange;
    private String selectedAirAddress="",selectedAirName="";
    private boolean airPickerOpen,reopenAirPickerAfterStop,airConnected;
    private ProtocolTabs protocolTabs;
    private RadioButton airTab,moonlightTab;
    private Button airStart,airStop,airSubmit;
    private TextView airState,airDetail,airMetrics,airLogs,airSelection,protocolHint,airPinHint;
    private EditText airPin;
    private Spinner airSource,airQuality;
    private Button airQualityAdvanced;
    private TextView airQualitySummary;
    private AirQuality pendingAirQuality=new AirQuality(1920,1080,30,8_000_000,false);
    private CompoundButton airLegacy;
    private LinearLayout airPinBox;
    private int pendingToken=-1,pendingMode=-1,pendingAudioRequest=-1,pendingProjectionRequest=-1;
    private boolean pendingAudioAllowed,updatingAirSource,uiReady;
    private AirPlayEndpoint pendingEndpoint;
    private String lastFailure="";
    private long startingSince,missingHostSince;
    private int senderPid=-1;
    private boolean airMode(){return session.selected()==CastSessionCoordinator.AIRPLAY;}
    private boolean browserMode(){return session.selected()==CastSessionCoordinator.BROWSER;}
    private Class<?> hostClass(){return switch(session.active()){case CastSessionCoordinator.AIRPLAY->AirPlayHostService.class;case CastSessionCoordinator.BROWSER->BrowserHostService.class;default->HostService.class;};}
    private TextView modeState(int mode){return mode==CastSessionCoordinator.BROWSER?browserState:mode==CastSessionCoordinator.AIRPLAY?airState:state;}
    private TextView modeDetail(int mode){return mode==CastSessionCoordinator.BROWSER?browserDetail:mode==CastSessionCoordinator.AIRPLAY?airDetail:detail;}
    private TextView activeState(){return modeState(session.selected());}
    private TextView activeDetail(){return modeDetail(session.selected());}
    private Messenger fileRemote;
    private boolean fileBound;
    private TextView fileInfo;
    private Button fileStop,fileCopy;
    private String fileUrls="";
    private final Messenger fileResponse=new Messenger(new Handler(Looper.getMainLooper(),msg->{
        if(msg.what==FileCinemaService.STATUS){Bundle b=msg.getData();if(b.getBoolean("stopped")){if(fileBound){unbindService(this.fileConnection);fileBound=false;fileRemote=null;}fileUrls="";fileInfo.setText(R.string.ui_file_idle);fileCopy.setEnabled(false);fileStop.setEnabled(false);return true;}fileUrls=b.getString("urls","");String failure=b.getString("error","");fileInfo.setText(!failure.isEmpty()?failure:b.getString("name","")+"\n"+(fileUrls.isEmpty()?getString(R.string.ui_file_preparing):fileUrls));fileCopy.setEnabled(!fileUrls.isEmpty());fileStop.setEnabled(true);}return true;
    }));
    private final ServiceConnection fileConnection=new ServiceConnection(){
        public void onServiceConnected(ComponentName n,IBinder b){fileRemote=new Messenger(b);queryFile();}
        public void onServiceDisconnected(ComponentName n){fileRemote=null;if(fileBound){unbindService(this);fileBound=false;}fileUrls="";fileInfo.setText(R.string.ui_file_idle);fileCopy.setEnabled(false);fileStop.setEnabled(false);}
    };
    private final Handler main=new Handler(Looper.getMainLooper());
    private Messenger remote;
    private boolean bound, resumed, stopPending;
    private long bindingSince,lastStatus;
    private TextView state, detail, logs, ip, metrics, scenarioHint;
    private Button start, stop, submit;
    private CompoundButton hevc, audio, root, muteLocal, control,watchParty,panelOff;
    private LinearLayout receiverList;
    private long[] shownReceivers=new long[0];
    private Spinner controlBackend;
    private TextView controlInfo, modeInfo;
    private Button accessibility, rescan, shizuku;
    private boolean pendingShizukuStart;
    private final rikka.shizuku.Shizuku.OnRequestPermissionResultListener shizukuPermission=(code,grant)->{if(code!=60)return;boolean resume=pendingShizukuStart;pendingShizukuStart=false;if(grant==PackageManager.PERMISSION_GRANTED && resume)main.postDelayed(this::begin,350);else if(grant!=PackageManager.PERMISSION_GRANTED)toast(getString(R.string.ui_shizuku_denied));refreshControlOptions();};
    private boolean updatingControl;
    private EditText pin, name;
    private LinearLayout pinBox;
    private Spinner scale, profile, scenario, source;
    private Button openApp,restoreDisplay;
    private TextView panelState;
    private int independentDisplay=-1;
    private ReceiverProfiles profiles;
    private int currentProfile,currentScenario;
    private boolean loadingProfile;
    private boolean updatingScale;
    private static final class AirQuality {
        final int width,height,fps,bitrate;final boolean legacy;
        AirQuality(int width,int height,int fps,int bitrate,boolean legacy){
            boolean valid=(width==1280&&height==720 || width==1920&&height==1080 || width==2560&&height==1440 || width==3840&&height==2160)
                && (fps==15||fps==24||fps==30||fps==60) && bitrate>=1_000_000 && bitrate<=40_000_000;
            this.legacy=legacy;this.width=legacy?1280:valid?width:1920;this.height=legacy?720:valid?height:1080;
            this.fps=legacy?30:valid?fps:30;this.bitrate=legacy?4_000_000:valid?bitrate:8_000_000;
        }
    }
    private HostConnection connection;
    private final class HostConnection implements ServiceConnection {
        final Messenger response=new Messenger(new Handler(Looper.getMainLooper(),msg->{
            if(connection==this && session.hasHost() && msg.what==HostService.STATUS)handleStatus(msg.getData());
            return true;
        }));
        @Override public void onServiceConnected(ComponentName n,IBinder binder) {
            if(connection!=this)return;
            remote=new Messenger(binder);bound=true;lastStatus=SystemClock.elapsedRealtime();
            if(stopPending)send(HostService.STOP,null);else query();
        }
        @Override public void onServiceDisconnected(ComponentName n) { if(connection==this)hostGone(); }
        @Override public void onBindingDied(ComponentName n) { if(connection==this)hostGone(); }
        @Override public void onNullBinding(ComponentName n) { if(connection==this){detachHost();verifyHostGone();} }
    }
    private void handleStatus(Bundle b){
        lastStatus=SystemClock.elapsedRealtime();
        int observedPid=b.getInt("pid",-1);
        if(observedPid>0 && observedPid!=senderPid){senderPid=observedPid;getPreferences(0).edit().putInt("activeHostPid",senderPid).apply();}
        String failure=b.getString("error","");
        if(!failure.isEmpty()){lastFailure=failure;activeDetail().setText(failure);}
        if(stopPending || b.getBoolean("stopping")){
            if(browserMode())showBrowserUrls("");
            stopPending=true;session.stop();activeState().setText(R.string.ui_stopping);updateSessionUi();return;
        }
        session.connected();
        activeState().setText(b.getString("state",getString(R.string.ui_ready_to_cast)));
        activeDetail().setText(failure.isEmpty()?b.getString("detail",""):failure);
        if(browserMode()){
            showBrowserUrls(b.getString("urls",b.getString("url","")));
        }else if(airMode()){
            airConnected=b.getBoolean("connected",false);refreshAirSelection();
            airLogs.setText(b.getString("logs",""));airMetrics.setText(b.getString("metrics",""));
            airPinBox.setVisibility(b.getBoolean("pinPending")?View.VISIBLE:View.GONE);
            airSubmit.setEnabled(b.getBoolean("pinPending"));
        }else{
            panelState.setText(b.getString("panelState",getString(R.string.ui_off)));showReceivers(b);
            logs.setText(b.getString("logs"));metrics.setText(b.getString("metrics"));
            independentDisplay=b.getInt("displayId",-1);openApp.setEnabled(independentDisplay>=0);
            pinBox.setVisibility(b.getBoolean("pinPending")?View.VISIBLE:View.GONE);
            updatingControl=true;control.setChecked(b.getBoolean("control",false));updatingControl=false;
            controlInfo.setText(b.getString("controlStatus",getString(R.string.ui_off)));
        }
        running(true);
        if(!browserMode()){
            int mode=b.getInt("scaleMode",scale.getSelectedItemPosition());
            if(scale.getSelectedItemPosition()!=mode){updatingScale=true;scale.setSelection(mode);updatingScale=false;}
        }
    }
    private void detachHost(){
        HostConnection old=connection;connection=null;remote=null;
        if(bound && old!=null)try{unbindService(old);}catch(IllegalArgumentException ignored){}
        bound=false;
    }
    private void hostGone(){
        int oldMode=session.active();
        if(oldMode==CastSessionCoordinator.BROWSER && lastFailure.isEmpty()
                && !stopPending && session.phase()!=CastSessionCoordinator.STOPPING)
            lastFailure=getString(R.string.browser_service_unexpected_exit);
        detachHost();stopPending=false;senderPid=-1;session.hostGone();
        getPreferences(0).edit().putBoolean("hostExpected",false).remove("activeHostPid").putInt("selectedProtocol",session.selected()).apply();
        running(false);applyProtocol();
        TextView previousState=modeState(oldMode);
        TextView previousDetail=modeDetail(oldMode);
        previousState.setText(lastFailure.isEmpty()?R.string.ui_stopped:R.string.ui_cast_failed);
        previousDetail.setText(lastFailure.isEmpty()?getString(R.string.ui_starting_again_will_request_screen_sharing_permission):lastFailure);
        airPin.setText("");pendingEndpoint=null;missingHostSince=0;airConnected=false;refreshAirSelection();showBrowserUrls("");
        if(reopenAirPickerAfterStop && airMode() && resumed){reopenAirPickerAfterStop=false;showAirPicker();}
        try{new LocalAudioMute(this).restore();}catch(RuntimeException ignored){}
    }
    @SuppressWarnings("deprecation")
    private long senderAbsenceGrace(){
        ActivityManager manager=getSystemService(ActivityManager.class);
        java.util.List<ActivityManager.RunningAppProcessInfo> processes=manager.getRunningAppProcesses();
        boolean expectedPidAlive=false;
        if(processes!=null)for(ActivityManager.RunningAppProcessInfo process:processes)
            if(process.uid==android.os.Process.myUid() && process.pid==senderPid)expectedPidAlive=true;
        // Since Android O this API returns the caller's own services. Inspect the sender
        // component, not the shared process name: Accessibility can restart :host on its own.
        java.util.List<ActivityManager.RunningServiceInfo> services=manager.getRunningServices(Integer.MAX_VALUE);
        boolean senderPresent=false;
        String senderClass=hostClass().getName();
        if(services!=null)for(ActivityManager.RunningServiceInfo service:services)
            if(service.uid==android.os.Process.myUid() && service.service.getClassName().equals(senderClass))senderPresent=true;
        return SenderLiveness.absenceGrace(senderPid,processes!=null,expectedPidAlive,services!=null,senderPresent);
    }
    private void verifyHostGone(){
        if(!session.hasHost()){running(false);return;}
        long grace=senderAbsenceGrace();
        if(grace<0){missingHostSince=0;return;}
        long now=SystemClock.elapsedRealtime();
        if(missingHostSince==0)missingHostSince=now;
        if(now-missingHostSince>=grace && now-startingSince>=grace)hostGone();
    }
    private void requestStop(){
        pendingShizukuStart=false;pendingToken=-1;pendingAudioRequest=pendingProjectionRequest=-1;
        if(browserMode())showBrowserUrls("");
        if(!session.hasHost()){
            session.stop();pendingEndpoint=null;activeState().setText(R.string.ui_stopped);
            activeDetail().setText(browserMode()?R.string.browser_start_hint:airMode()?R.string.ui_airplay_start_hint:R.string.ui_starting_again_will_request_screen_sharing_permission);updateSessionUi();return;
        }
        session.stop();stopPending=true;activeState().setText(R.string.ui_stopping);
        if(lastFailure.isEmpty())activeDetail().setText("");updateSessionUi();
        if(remote!=null)send(HostService.STOP,null);
        else{stopService(new Intent(this,hostClass()));verifyHostGone();}
    }
    private final Runnable poll=new Runnable() {
        @Override public void run() {
            if(!resumed)return;
            if(bound && remote==null && SystemClock.elapsedRealtime()-bindingSince>3000){detachHost();verifyHostGone();}
            if(remote==null)bindExisting();else{query();if(!stopPending && SystemClock.elapsedRealtime()-lastStatus>6000)activeState().setText(R.string.ui_host_unresponsive);}
            if(session.hasHost() && remote==null){verifyHostGone();if(!stopPending && SystemClock.elapsedRealtime()-startingSince>12000){lastFailure=getString(R.string.ui_host_unresponsive);requestStop();}}
            if(!fileBound)fileBound=bindService(new Intent(MainActivity.this,FileCinemaService.class),fileConnection,0);else queryFile();
            ip.setText(HostService.addresses(MainActivity.this));main.postDelayed(this,1500);
        }
    };
    private static final int INK=PhoneVisuals.INK, MUTED=PhoneVisuals.MUTED, BLUE=PhoneVisuals.BLUE;
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        if(b!=null){
            session=new CastSessionCoordinator(b.getInt("protocol"),b.getInt("activeProtocol",-1),b.getInt("targetProtocol",-1),b.getInt("sessionPhase"),b.getInt("sessionGeneration"));
            pendingToken=b.getInt("pendingToken",-1);pendingMode=b.getInt("pendingMode",-1);
            pendingAudioRequest=b.getInt("pendingAudioRequest",-1);pendingProjectionRequest=b.getInt("pendingProjectionRequest",-1);
            pendingAudioAllowed=b.getBoolean("pendingAudioAllowed");lastFailure=b.getString("lastFailure","");
            pendingBrowserQuality=validBrowserQuality(b.getInt("pendingBrowserLongEdge",BrowserQuality.DEFAULT.longEdge),b.getInt("pendingBrowserFps",BrowserQuality.DEFAULT.fps),b.getInt("pendingBrowserBitrate",BrowserQuality.DEFAULT.maxBitrateBps));
            pendingAirQuality=new AirQuality(b.getInt("pendingAirWidth",1920),b.getInt("pendingAirHeight",1080),b.getInt("pendingAirFps",30),b.getInt("pendingAirBitrate",8_000_000),b.getBoolean("pendingAirLegacy"));
            pendingEndpoint=AirPlayEndpoint.parse(b.getString("pendingEndpoint",""));
            stopPending=session.phase()==CastSessionCoordinator.STOPPING;senderPid=b.getInt("senderPid",-1);
        }else{
            session=new CastSessionCoordinator(getPreferences(0).getInt("selectedProtocol",CastSessionCoordinator.MOONLIGHT));
            if(getPreferences(0).getBoolean("hostExpected",false)){session.restoreHost(getPreferences(0).getInt("activeProtocol",0));senderPid=getPreferences(0).getInt("activeHostPid",-1);}
        }
        selectedAirAddress=getPreferences(0).getString("airplayAddress","");
        selectedAirName=getPreferences(0).getString("airplayReceiverName","");
        if(b!=null){airPickerOpen=b.getBoolean("airPickerOpen");
            reopenAirPickerAfterStop=b.getBoolean("reopenAirPickerAfterStop");airConnected=b.getBoolean("airConnected");}
        startingSince=SystemClock.elapsedRealtime();
        rikka.shizuku.Shizuku.addRequestPermissionResultListener(shizukuPermission);
        profiles=new ReceiverProfiles(this,getPreferences(0));currentProfile=profiles.selected();
        getWindow().setStatusBarColor(PhoneVisuals.CANVAS);getWindow().setNavigationBarColor(PhoneVisuals.CANVAS);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(true);scroll.setBackgroundColor(PhoneVisuals.CANVAS);
        scroll.setOnApplyWindowInsetsListener((view,insets)->{
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());view.setPadding(bars.left,bars.top,bars.right,bars.bottom);}
            else view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        FrameLayout content=new FrameLayout(this);scroll.addView(content,new ScrollView.LayoutParams(-1,-2));
        LinearLayout page=PhoneVisuals.contentColumn(this);page.setPadding(dp(22),dp(12),dp(22),dp(28));page.setBackgroundColor(PhoneVisuals.CANVAS);content.addView(page,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL));setContentView(scroll);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(0,0,0,dp(16));page.addView(header,new LinearLayout.LayoutParams(-1,-2));
        TextView title=text("MoonCast",26,INK);title.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));header.addView(title,new LinearLayout.LayoutParams(0,dp(48),1));
        if((getPackageName().equals("com.mooncast.unified.preview") || getPackageName().equals("com.mooncast.community"))){
            ImageButton deviceMode=PhoneVisuals.settingsButton(this,getString(R.string.phone_settings_title));deviceMode.setTag("open_settings");
            deviceMode.setOnClickListener(v->new android.app.AlertDialog.Builder(this).setTitle(R.string.phone_settings_title)
                .setItems(new String[]{getString(R.string.phone_switch_device_mode),getString(R.string.phone_language),getString(R.string.phone_about_version)},(d,w)->{
                    if(w==0){PhoneRolePicker.show(this,choice->{startActivity(new Intent().setClassName(getPackageName(),"com.mooncast.unified.RoleSettingsActivity").putExtra("role_choice",choice).putExtra("require_cleanup",true));finish();});}
                    else if(w==1){com.mooncast.locale.LanguagePicker.show(this,choice->{startActivity(new Intent().setClassName(getPackageName(),"com.mooncast.unified.LanguageSettingsActivity").putExtra("require_cleanup",true).putExtra("language_choice",choice));finish();});}
                    else showAbout();
                }).show());header.addView(deviceMode,new LinearLayout.LayoutParams(dp(48),dp(48)));
        }
        buildProtocolTabs(page);
        buildAirPlayPanel(page);
        buildBrowserPanel(page);

        LinearLayout hero=card(page);moonlightOnly.add(hero);styleHero(hero);
        state=text(getString(R.string.ui_ready_to_cast),26,INK);state.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));hero.addView(state);state.setGravity(Gravity.CENTER);
        detail=text(getString(R.string.ui_start_casting_then_connect_from_moonlight_on_your_ipad_or_tv),13,MUTED);hero.addView(detail);detail.setGravity(Gravity.CENTER);
        LinearLayout address=new LinearLayout(this);address.setGravity(Gravity.CENTER_VERTICAL);address.setPadding(0,dp(8),0,0);
        LinearLayout ipLabel=new LinearLayout(this);ipLabel.setOrientation(LinearLayout.VERTICAL);ipLabel.addView(text(getString(R.string.ui_phone_lan_ip),11,MUTED));
        ip=text(HostService.addresses(MainActivity.this),18,INK);ip.setTypeface(Typeface.MONOSPACE);ip.setTextIsSelectable(true);ipLabel.addView(ip);address.addView(ipLabel,new LinearLayout.LayoutParams(0,-2,1));
        Button copy=smallButton(getString(R.string.ui_copy_ip));copy.setOnClickListener(v->{android.content.ClipboardManager clipboard=getSystemService(android.content.ClipboardManager.class);clipboard.setPrimaryClip(ClipData.newPlainText("MoonCast IP",HostService.addresses(MainActivity.this)));toast(getString(R.string.ui_ip_copied));});address.addView(copy);hero.addView(address);

        LinearLayout cinema=card(page);moonlightOnly.add(cinema);section(cinema,getString(R.string.ui_original_cinema),getString(R.string.ui_original_cinema_hint));
        fileInfo=text(getString(R.string.ui_file_idle),12,MUTED);fileInfo.setTextIsSelectable(true);cinema.addView(fileInfo);
        Button choose=smallButton(getString(R.string.ui_choose_media));choose.setOnClickListener(v->{Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"video/*","audio/*","image/*"});startActivityForResult(pick,DOCUMENT);});cinema.addView(choose);
        fileCopy=smallButton(getString(R.string.ui_copy_file_link));fileCopy.setEnabled(false);fileCopy.setOnClickListener(v->{getSystemService(android.content.ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("MoonCast",fileUrls.split("\n")[0]));toast(getString(R.string.ui_file_link_copied));});
        fileStop=smallButton(getString(R.string.ui_stop_file_sharing));fileStop.setEnabled(false);fileStop.setOnClickListener(v->{if(fileRemote!=null)try{fileRemote.send(Message.obtain(null,FileCinemaService.STOP));}catch(RemoteException ignored){}});
        LinearLayout fileActions=new LinearLayout(this);fileActions.setPadding(0,dp(8),0,0);
        fileActions.addView(fileCopy,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams fileStopLayout=new LinearLayout.LayoutParams(0,-2,1);fileStopLayout.setMarginStart(dp(8));fileActions.addView(fileStop,fileStopLayout);cinema.addView(fileActions);

        LinearLayout devices=card(page);moonlightOnly.add(devices);section(devices,getString(R.string.ui_receiver_profile),getString(R.string.ui_receiver_profile_hint));
        profile=spinner(new String[]{getString(R.string.ui_profile_default),"iPad",getString(R.string.ui_profile_tv),getString(R.string.ui_profile_computer)});
        profile.setSelection(currentProfile);devices.addView(profile);
        profile.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(AdapterView<?> parent){}
            public void onItemSelected(AdapterView<?> parent,View view,int position,long id){
                if(position==currentProfile)return;
                if(session.busy()){profile.setSelection(currentProfile);return;}
                saveProfile();profiles.select(position);currentProfile=position;loadProfile();
            }
        });

        watchParty=toggle(devices,getString(R.string.ui_watch_party),getString(R.string.ui_watch_party_hint),settings().getBoolean("watchParty",false));
        receiverList=new LinearLayout(this);receiverList.setOrientation(LinearLayout.VERTICAL);devices.addView(receiverList);
        watchParty.setOnCheckedChangeListener((button,checked)->{if(loadingProfile)return;settings().edit().putBoolean("watchParty",checked).apply();refreshControlOptions();});

        LinearLayout dashboard=card(page);moonlightOnly.add(dashboard);section(dashboard,getString(R.string.ui_dashboard),getString(R.string.ui_dashboard_hint));
        metrics=text(getString(R.string.ui_dashboard_idle),14,INK);dashboard.addView(metrics);
        scenario=spinner(new String[]{getString(R.string.ui_scenario_custom),getString(R.string.ui_scenario_movie),getString(R.string.ui_scenario_game),getString(R.string.ui_scenario_eco)});
        currentScenario=settings().getInt("scenario",0);scenario.setSelection(currentScenario);dashboard.addView(scenario);scenarioHint=text("",12,MUTED);dashboard.addView(scenarioHint);
        scenario.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(AdapterView<?> p){}
            public void onItemSelected(AdapterView<?> p,View v,int position,long id){describeScenario(position);if(loadingProfile || session.busy() || position==currentScenario)return;currentScenario=position;settings().edit().putInt("source",source.getSelectedItemPosition()).putInt("scenario",position).apply();if(scale==null || hevc==null)return;if(position==1){scale.setSelection(CropGeometry.CINEMA);hevc.setChecked(true);}else if(position==2 || position==3)scale.setSelection(CropGeometry.SCREEN);}
        });describeScenario(scenario.getSelectedItemPosition());

        LinearLayout capture=card(page);moonlightOnly.add(capture);section(capture,getString(R.string.ui_capture_source),getString(R.string.ui_capture_source_hint));
        source=spinner(new String[]{getString(R.string.ui_source_screen),getString(R.string.ui_source_app),getString(R.string.ui_source_independent)});
        source.setSelection(settings().getInt("source",0));capture.addView(source);
        source.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int position,long id){if(loadingProfile)return;settings().edit().putInt("source",position).apply();if(airSource!=null && position<2){updatingAirSource=true;airSource.setSelection(position);updatingAirSource=false;}if(root!=null && position!=0)root.setChecked(false);if(control!=null)refreshControlOptions();if(openApp!=null)openApp.setVisibility(position==2?View.VISIBLE:View.GONE);}});
        openApp=smallButton(getString(R.string.ui_launch_independent_app));openApp.setEnabled(false);openApp.setOnClickListener(v->chooseIndependentApp());capture.addView(openApp);openApp.setVisibility(source.getSelectedItemPosition()==2?View.VISIBLE:View.GONE);

        panelOff=toggle(capture,getString(R.string.ui_panel_option),getString(R.string.ui_panel_hint),false);
        panelOff.setOnCheckedChangeListener((button,checked)->refreshControlOptions());
        panelState=text(getString(R.string.ui_off),12,MUTED);capture.addView(panelState);
        restoreDisplay=smallButton(getString(R.string.ui_panel_restore));restoreDisplay.setEnabled(false);restoreDisplay.setOnClickListener(v->{send(HostService.RESTORE_DISPLAY,null);panelOff.setChecked(false);});capture.addView(restoreDisplay);

        LinearLayout picture=card(page);nativeSenderOnly.add(picture);section(picture,getString(R.string.ui_picture),getString(R.string.ui_choose_how_the_picture_fits_your_screen));
        scale=spinner(new String[]{getString(R.string.ui_whole_screen_fit),getString(R.string.ui_auto_crop_fit),getString(R.string.ui_fill_screen_crop_edges),getString(R.string.ui_video_region_centered_16_9),getString(R.string.ui_cinema_mode)});
        if(!settings().getBoolean("videoRegionIntroduced",false))settings().edit().putInt("scaleMode",3).putBoolean("videoRegionIntroduced",true).apply();
        scale.setSelection(settings().getInt("scaleMode",3));picture.addView(scale);modeInfo=text("",12,MUTED);picture.addView(modeInfo);
        rescan=smallButton(getString(R.string.ui_rescan_video));rescan.setEnabled(false);rescan.setOnClickListener(v->{send(HostService.CROP_RESET,null);toast(getString(R.string.ui_rescan_video_hint));});picture.addView(rescan);describeMode(scale.getSelectedItemPosition());
        scale.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(AdapterView<?> parent){}
            public void onItemSelected(AdapterView<?> parent,View view,int position,long id){describeMode(position);if(updatingScale || loadingProfile || browserMode())return;settings().edit().putInt("scaleMode",position).apply();if(remote!=null)try{Message m=Message.obtain(null,HostService.SCALE);m.arg1=position;remote.send(m);}catch(RemoteException ignored){}}
        });

        LinearLayout sound=card(page);nativeSenderOnly.add(sound);sound.setTag("quick_audio");
        audio=toggle(sound,getString(R.string.ui_stream_playback_audio),getString(R.string.ui_android_10_source_app_must_allow_capture),settings().getBoolean("audio",false));
        muteLocal=toggle(sound,getString(R.string.ui_mute_phone_locally),getString(R.string.ui_only_while_streaming_audio_restore_on_stop),settings().getBoolean("muteLocal",false));muteLocal.setEnabled(audio.isChecked());

        LinearLayout input=card(page);moonlightOnly.add(input);section(input,getString(R.string.ui_remote_input),getString(R.string.ui_let_a_paired_receiver_control_this_phone));
        control=toggle(input,getString(R.string.ui_enable_remote_input),getString(R.string.ui_turn_off_for_viewing_only),settings().getBoolean("control",false));
        controlBackend=spinner(new String[]{getString(R.string.ui_no_root_accessibility_taps_and_swipes),getString(R.string.ui_root_continuous_touch_and_keyboard),getString(R.string.ui_shizuku_backend)});controlBackend.setSelection(settings().getInt("controlBackend",settings().getBoolean("controlRoot",false)?1:0));input.addView(controlBackend);
        controlInfo=text(getString(R.string.ui_off),12,MUTED);input.addView(controlInfo);
        accessibility=smallButton(getString(R.string.ui_enable_mooncast_accessibility));accessibility.setOnClickListener(v->startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)));input.addView(accessibility);
        shizuku=smallButton(getString(R.string.ui_connect_shizuku));shizuku.setOnClickListener(v->requestShizuku(false));input.addView(shizuku);

        LinearLayout settings=card(page);moonlightOnly.add(settings);TextView expand=text(getString(R.string.ui_connection_and_device_settings_collapsed),15,INK);expand.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));settings.addView(expand);
        LinearLayout advanced=new LinearLayout(this);advanced.setOrientation(LinearLayout.VERTICAL);advanced.setVisibility(View.GONE);settings.addView(advanced);
        expand.setOnClickListener(v->{boolean open=advanced.getVisibility()!=View.VISIBLE;advanced.setVisibility(open?View.VISIBLE:View.GONE);expand.setText(open?getString(R.string.ui_connection_and_device_settings_expanded):getString(R.string.ui_connection_and_device_settings_collapsed));});
        advanced.addView(text(getString(R.string.ui_host_name),12,MUTED));name=new EditText(this);name.setSingleLine(true);name.setTextSize(15);name.setText(settings().getString("name","MoonCast · "+Build.MODEL));advanced.addView(name);
        hevc=toggle(advanced,getString(R.string.ui_prefer_hevc_h_265),getString(R.string.ui_set_resolution_frame_rate_and_bitrate_in_moonlight),settings().getBoolean("hevc",true));
        root=toggle(advanced,getString(R.string.ui_root_screen_capture),getString(R.string.ui_experimental_no_capture_dialog_video_only),false);
        root.setOnCheckedChangeListener((button,checked)->{if(checked && source.getSelectedItemPosition()!=0)source.setSelection(0);audio.setEnabled(!checked && remote==null);if(checked)controlBackend.setSelection(1);muteLocal.setEnabled(!checked && audio.isChecked() && remote==null);refreshControlOptions();});
        audio.setOnCheckedChangeListener((button,checked)->refreshControlOptions());
        audio.setOnClickListener(view->{if(!loadingProfile)ReceiverProfiles.recordAudioChoice(settings(),"audio",audio.isChecked());});
        muteLocal.setOnClickListener(view->{if(!loadingProfile)ReceiverProfiles.recordAudioChoice(settings(),"muteLocal",muteLocal.isChecked());});
        control.setOnCheckedChangeListener((button,checked)->{
            if(updatingControl || loadingProfile)return;settings().edit().putBoolean("control",checked).apply();refreshControlOptions();
            if(remote!=null && !browserMode())try{Message m=Message.obtain(null,HostService.CONTROL);m.arg1=checked?1:0;remote.send(m);}catch(RemoteException ignored){}
        });
        controlBackend.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int position,long id){if(loadingProfile)return;settings().edit().putInt("controlBackend",position).putBoolean("controlRoot",position==1).apply();refreshControlOptions();}});
        advanced.addView(text(getString(R.string.ui_device_encoders),12,MUTED));TextView codec=text(EncoderSupport.describe(),11,MUTED);codec.setTextIsSelectable(true);advanced.addView(codec);
        Button pattern=smallButton(getString(R.string.ui_open_visual_test_pattern));pattern.setOnClickListener(v->startActivity(new Intent(this,TestPatternActivity.class)));advanced.addView(pattern);
        advanced.addView(text(getString(R.string.ui_diagnostics),12,MUTED));logs=text(getString(R.string.ui_waiting_to_start_pending),11,MUTED);logs.setTextIsSelectable(true);advanced.addView(logs);

        start=button(getString(R.string.ui_start_casting),BLUE);hero.addView(start);start.setOnClickListener(v->begin());
        stop=button(getString(R.string.ui_stop_casting),0xff8a97ad);hero.addView(stop);stop.setOnClickListener(v->requestStop());stop.setEnabled(false);stop.setVisibility(View.GONE);
        pinBox=card(page);moonlightOnly.add(pinBox);pinBox.setVisibility(View.GONE);section(pinBox,getString(R.string.ui_pair_receiver),getString(R.string.ui_enter_the_4_digit_pin_shown_in_moonlight));
        pin=new EditText(this);pin.setHint("0000");pin.setTextSize(26);pin.setTypeface(Typeface.MONOSPACE);pin.setInputType(InputType.TYPE_CLASS_NUMBER);pin.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(4)});pinBox.addView(pin);
        submit=button(getString(R.string.ui_pair),BLUE);pinBox.addView(submit);submit.setOnClickListener(v->{String code=pin.getText().toString();if(!code.matches("[0-9]{4}")){toast(getString(R.string.ui_enter_a_4_digit_pin));return;}Bundle data=new Bundle();data.putString("pin",code);send(HostService.PIN,data);pin.setText("");});
        TextView moonlightHelp=text(getString(R.string.ui_use_the_same_lan_and_official_moonlight_nshare_the_entire_phone_screen),12,MUTED);moonlightOnly.add(moonlightHelp);page.addView(moonlightHelp);
        // Reorder presentation only. All original controls/listeners remain alive and retain state.
        page.removeView(pinBox);page.addView(pinBox,page.indexOfChild(hero)+1);
        page.removeView(sound);page.addView(sound,page.indexOfChild(pinBox)+1);
        advancedContent=new LinearLayout(this);advancedContent.setOrientation(LinearLayout.VERTICAL);advancedContent.setTag("advanced_content");
        for(View item:new View[]{cinema,devices,dashboard,capture,picture,input,settings,moonlightHelp}){page.removeView(item);advancedContent.addView(item);}
        airPanel.removeView(airAdvanced);advancedContent.addView(airAdvanced,0);
        buildApkTransferCard(page);
        advancedToggle=smallButton(getString(R.string.visual_advanced));advancedToggle.setTag("advanced_toggle");
        LinearLayout.LayoutParams disclosure=new LinearLayout.LayoutParams(-1,dp(52));disclosure.topMargin=dp(12);page.addView(advancedToggle,disclosure);page.addView(advancedContent);
        advancedExpanded=b!=null && b.getBoolean("advancedOpen");
        advancedContent.setVisibility(advancedExpanded?View.VISIBLE:View.GONE);
        advancedToggle.setOnClickListener(v->{advancedExpanded=!advancedExpanded;advancedContent.setVisibility(advancedExpanded?View.VISIBLE:View.GONE);updateAdvancedLabel();});updateAdvancedLabel();
        TextView foot=text(getString(getPackageName().equals("com.mooncast.community")?R.string.phone_community_footer:R.string.phone_preview_footer),11,0xffa4adbc);foot.setGravity(Gravity.CENTER);foot.setPadding(0,dp(18),0,0);page.addView(foot);
        uiReady=true;
        if(b!=null){airSource.setSelection(b.getInt("airSource",airSource.getSelectedItemPosition()));}
        applyProtocol();running(session.hasHost());updateSessionUi();
    }
    private void buildApkTransferCard(LinearLayout page){
        LinearLayout transfer=card(page);transfer.setPadding(dp(16),dp(8),dp(16),dp(8));
        Button open=smallButton(getString(R.string.apk_title));open.setTag("open_apk_sender");open.setBackgroundColor(Color.TRANSPARENT);open.setTextColor(INK);open.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);
        android.graphics.drawable.Drawable icon=getDrawable(R.drawable.ic_package);icon.setBounds(0,0,dp(26),dp(26));
        android.graphics.drawable.Drawable arrow=getDrawable(R.drawable.ic_chevron);arrow.setBounds(0,0,dp(18),dp(18));open.setCompoundDrawablesRelative(icon,null,arrow,null);open.setCompoundDrawablePadding(dp(16));
        open.setOnClickListener(v->startActivity(new Intent(this,ApkSendActivity.class)));transfer.addView(open,new LinearLayout.LayoutParams(-1,dp(52)));
    }
    private void styleHero(LinearLayout hero){
        styleHero(hero,R.drawable.ic_cast);
    }
    private void styleHero(LinearLayout hero,int iconResource){
        hero.setBackgroundColor(Color.TRANSPARENT);hero.setPadding(0,dp(18),0,dp(10));
        ImageView icon=PhoneVisuals.icon(this,iconResource,48,BLUE);LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(dp(48),dp(48));layout.gravity=Gravity.CENTER_HORIZONTAL;layout.bottomMargin=dp(10);hero.addView(icon,layout);
    }
    private void updateAdvancedLabel(){advancedToggle.setText(advancedExpanded?R.string.visual_advanced_close:R.string.visual_advanced);}

    private void buildProtocolTabs(LinearLayout page){
        protocolTabs=new ProtocolTabs(this,mode->{selectProtocol(mode);protocolTabs.select(session.selected());});
        airTab=protocolTabs.addTab(getString(R.string.ui_tab_airplay),"protocol_tab_airplay",CastSessionCoordinator.AIRPLAY);
        moonlightTab=protocolTabs.addTab(getString(R.string.phone_tab_gamestream),"protocol_tab_gamestream",CastSessionCoordinator.MOONLIGHT);
        browserTab=protocolTabs.addTab(getString(R.string.browser_tab),"protocol_tab_browser",CastSessionCoordinator.BROWSER);
        page.addView(protocolTabs,new LinearLayout.LayoutParams(-1,-2));
        protocolHint=text("",12,MUTED);protocolHint.setGravity(Gravity.CENTER);protocolHint.setPadding(dp(8),dp(10),dp(8),0);page.addView(protocolHint);
    }
    private void showAbout(){
        String message=com.mooncast.locale.AppBuildInfo.describe(this);
        if(getPackageName().equals("com.mooncast.community"))message+="\n\n"+getString(R.string.phone_community_signing_notice);
        new AlertDialog.Builder(this).setTitle(R.string.phone_about_version).setMessage(message).setPositiveButton(android.R.string.ok,null).show();
    }
    private void buildBrowserPanel(LinearLayout page){
        browserPanel=new LinearLayout(this);browserPanel.setOrientation(LinearLayout.VERTICAL);page.addView(browserPanel);
        LinearLayout hero=card(browserPanel);styleHero(hero,R.drawable.ic_tv);
        browserState=text(getString(R.string.browser_ready),24,INK);browserState.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));browserState.setGravity(Gravity.CENTER);browserState.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);hero.addView(browserState);
        browserDetail=text(getString(R.string.browser_start_hint),13,MUTED);browserDetail.setGravity(Gravity.CENTER);hero.addView(browserDetail);
        browserStart=button(getString(R.string.browser_start),BLUE);browserStart.setTag("browser_start");browserStart.setOnClickListener(v->begin());hero.addView(browserStart);
        browserStop=button(getString(R.string.ui_stop_casting),0xff8a97ad);browserStop.setOnClickListener(v->requestStop());hero.addView(browserStop);

        LinearLayout connect=card(browserPanel);browserConnect=connect;connect.setTag("browser_connection");section(connect,getString(R.string.browser_open_link),getString(R.string.browser_open_link_hint));
        browserAddress=spinner(new String[]{});browserAddress.setContentDescription(getString(R.string.browser_address));connect.addView(browserAddress);
        browserQrImage=new ImageView(this);browserQrImage.setTag("browser_qr");browserQrImage.setSaveEnabled(false);browserQrImage.setScaleType(ImageView.ScaleType.FIT_CENTER);browserQrImage.setContentDescription(getString(R.string.browser_qr_description));browserQrImage.setVisibility(View.GONE);
        LinearLayout.LayoutParams qrLayout=new LinearLayout.LayoutParams(-1,dp(200));qrLayout.gravity=Gravity.CENTER_HORIZONTAL;qrLayout.topMargin=dp(8);qrLayout.bottomMargin=dp(8);connect.addView(browserQrImage,qrLayout);
        browserQrStatus=text(getString(R.string.browser_qr_unavailable),12,MUTED);browserQrStatus.setVisibility(View.GONE);connect.addView(browserQrStatus);
        LinearLayout linkRow=new LinearLayout(this);linkRow.setOrientation(LinearLayout.VERTICAL);connect.addView(linkRow);
        browserLink=text(getString(R.string.browser_link_pending),14,INK);browserLink.setTypeface(Typeface.MONOSPACE);browserLink.setTextIsSelectable(true);browserLink.setSaveEnabled(false);linkRow.addView(browserLink,new LinearLayout.LayoutParams(-1,-2));
        browserAddress.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(AdapterView<?> parent){}
            public void onItemSelected(AdapterView<?> parent,View view,int position,long id){Object address=browserAddress.getSelectedItem();selectBrowserUrl(address==null?"":address.toString());}
        });
        LinearLayout linkActions=new LinearLayout(this);linkActions.setGravity(Gravity.END);linkActions.setPadding(0,dp(8),0,0);linkRow.addView(linkActions);
        browserCopy=smallButton(getString(R.string.browser_copy_link));browserCopy.setOnClickListener(v->copyBrowserLink());
        linkActions.addView(browserCopy,new LinearLayout.LayoutParams(-2,dp(48)));showBrowserUrls("");

        LinearLayout capture=card(browserPanel);capture.addView(text(getString(R.string.ui_capture_source),14,INK));
        browserSource=spinner(new String[]{getString(R.string.ui_source_screen),getString(R.string.ui_source_app)});
        browserSource.setSelection(Math.max(0,Math.min(1,getPreferences(0).getInt("browserSource",0))));capture.addView(browserSource);
        browserSource.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> parent){}public void onItemSelected(AdapterView<?> parent,View view,int position,long id){if(uiReady && !session.busy())saveBrowser();}});
        browserAudio=toggle(capture,getString(R.string.ui_stream_playback_audio),getString(R.string.browser_audio_option_hint),getPreferences(0).getBoolean("browserAudio",false));
        browserAudio.setOnCheckedChangeListener((button,checked)->{if(uiReady && !session.busy())saveBrowser();});
        LinearLayout qualityRow=new LinearLayout(this);qualityRow.setGravity(Gravity.CENTER_VERTICAL);qualityRow.setPadding(0,dp(8),0,0);capture.addView(qualityRow);
        LinearLayout qualityText=new LinearLayout(this);qualityText.setOrientation(LinearLayout.VERTICAL);qualityText.addView(text(getString(R.string.browser_quality_title),14,INK));
        browserQualitySummary=text("",12,MUTED);qualityText.addView(browserQualitySummary);qualityRow.addView(qualityText,new LinearLayout.LayoutParams(0,-2,1));
        Button quality=smallButton(getString(R.string.browser_quality_change));quality.setContentDescription(getString(R.string.browser_quality_title));quality.setOnClickListener(v->showBrowserQuality());qualityRow.addView(quality,new LinearLayout.LayoutParams(-2,dp(48)));updateBrowserQualitySummary();
        Button connectionHelp=smallButton(getString(R.string.browser_connection_details));connectionHelp.setBackgroundColor(Color.TRANSPARENT);connectionHelp.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        connectionHelp.setOnClickListener(v->new AlertDialog.Builder(this).setTitle(R.string.browser_connection_details).setMessage(getString(R.string.browser_protocol_hint)+"\n\n"+getString(R.string.browser_capture_hint)+"\n\n"+getString(R.string.browser_audio_hint)+"\n\n"+getString(R.string.browser_controls_hint)).setPositiveButton(android.R.string.ok,null).show());capture.addView(connectionHelp,new LinearLayout.LayoutParams(-1,-2));
    }
    private void showBrowserUrls(String urls){
        String normalized=urls==null?"":urls.trim();
        if(!normalized.equals(browserUrls) || browserAddress.getAdapter().getCount()==0){
            String previous=browserAddress.getSelectedItem()==null?"":browserAddress.getSelectedItem().toString();
            browserUrls=normalized;
            String[] addresses=browserUrls.isEmpty()?new String[]{}:browserUrls.split("\\s*\\n\\s*");
            browserAddress.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,addresses));
            int selected=0;for(int i=0;i<addresses.length;i++)if(addresses[i].equals(previous))selected=i;
            if(addresses.length>0)browserAddress.setSelection(selected);
        }
        Object address=browserAddress.getSelectedItem();selectBrowserUrl(address==null?"":address.toString());
        browserAddress.setVisibility(browserAddress.getAdapter().getCount()>1?View.VISIBLE:View.GONE);
    }
    private boolean validBrowserUrl(String url){
        if(url.isEmpty() || !browserMode() || !session.hasHost() || stopPending || session.phase()==CastSessionCoordinator.STOPPING)return false;
        boolean advertised=false;for(String address:browserUrls.split("\\s*\\n\\s*"))if(address.equals(url)){advertised=true;break;}
        if(!advertised)return false;
        try{java.net.URI address=java.net.URI.create(url);return ("http".equals(address.getScheme())||"https".equals(address.getScheme())) && address.getHost()!=null && address.getRawUserInfo()==null;}
        catch(IllegalArgumentException invalid){return false;}
    }
    private void selectBrowserUrl(String url){
        String selected=validBrowserUrl(url)?url:"";
        if(!selected.equals(browserSelectedUrl)){clearBrowserQr();browserSelectedUrl=selected;}
        if(selected.isEmpty())browserLink.setText(R.string.browser_link_pending);else browserLink.setText(selected);
        browserCopy.setEnabled(!selected.isEmpty());updateBrowserQr();
        browserConnect.setVisibility(selected.isEmpty()?View.GONE:View.VISIBLE);
    }
    private void copyBrowserLink(){
        if(!validBrowserUrl(browserSelectedUrl))return;
        getSystemService(android.content.ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("MoonCast",browserSelectedUrl));toast(getString(R.string.ui_file_link_copied));
    }
    private void updateBrowserQr(){
        if(!resumed || isFinishing() || isDestroyed() || !validBrowserUrl(browserSelectedUrl)){clearBrowserQr();return;}
        if(browserSelectedUrl.equals(browserQrUrl) && (browserQrBitmap!=null || browserQrFailed))return;
        clearBrowserQr();browserQrUrl=browserSelectedUrl;
        try{
            browserQrBitmap=BrowserQrCode.encode(browserQrUrl,Math.max(128,Math.min(1024,dp(200))));
            browserQrImage.setImageBitmap(browserQrBitmap);browserQrImage.setVisibility(View.VISIBLE);
        }catch(IllegalArgumentException unavailable){browserQrFailed=true;browserQrStatus.setVisibility(View.VISIBLE);}
    }
    private void clearBrowserQr(){
        if(browserQrImage!=null){browserQrImage.setImageDrawable(null);browserQrImage.setVisibility(View.GONE);}
        if(browserQrStatus!=null)browserQrStatus.setVisibility(View.GONE);
        browserQrBitmap=null;browserQrUrl="";browserQrFailed=false;
    }
    private BrowserQuality readBrowserQuality(){
        return validBrowserQuality(getPreferences(0).getInt("browserLongEdge",BrowserQuality.DEFAULT.longEdge),getPreferences(0).getInt("browserFps",BrowserQuality.DEFAULT.fps),getPreferences(0).getInt("browserBitrate",BrowserQuality.DEFAULT.maxBitrateBps));
    }
    private static BrowserQuality validBrowserQuality(int edge,int fps,int bitrate){try{return BrowserQuality.of(edge,fps,bitrate);}catch(IllegalArgumentException invalid){return BrowserQuality.DEFAULT;}}
    private BrowserQuality browserQualityPreset(int preset){
        if(preset!=2)return BrowserQuality.fromPreset(preset);
        if(browserClearQuality==null)browserClearQuality=BrowserEncoderCapabilities.clearProfile(this);
        return browserClearQuality;
    }
    private String describeBrowserQuality(BrowserQuality quality){
        int label=R.string.browser_quality_custom;
        int[] labels={R.string.browser_quality_smooth,R.string.browser_quality_balanced,R.string.browser_quality_clear};
        for(int i=0;i<labels.length;i++){BrowserQuality preset=browserQualityPreset(i);if(preset.longEdge==quality.longEdge && preset.fps==quality.fps && preset.maxBitrateBps==quality.maxBitrateBps){label=labels[i];break;}}
        return describeBrowserQuality(quality,label);
    }
    private String describeBrowserQuality(BrowserQuality quality,int label){return getString(R.string.browser_quality_summary,getString(label),quality.longEdge,quality.fps,quality.maxBitrateBps/1_000_000);}
    private void updateBrowserQualitySummary(){browserQualitySummary.setText(getString(R.string.browser_quality_next,describeBrowserQuality(readBrowserQuality())));}
    private boolean saveBrowserQuality(BrowserQuality quality){
        if(!BrowserEncoderCapabilities.supports(this,quality)){toast(getString(R.string.browser_quality_unsupported));return false;}
        getPreferences(0).edit().putInt("browserLongEdge",quality.longEdge).putInt("browserFps",quality.fps).putInt("browserBitrate",quality.maxBitrateBps).apply();
        updateBrowserQualitySummary();toast(getString(session.busy()?R.string.browser_quality_saved_active:R.string.browser_quality_saved));return true;
    }
    private void showBrowserQuality(){
        BrowserQuality[] selected={readBrowserQuality()};
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(20),dp(8),dp(20),dp(8));
        body.addView(text(getString(R.string.browser_quality_next,describeBrowserQuality(selected[0])),13,INK));body.addView(text(getString(R.string.browser_quality_next_hint),12,MUTED));
        RadioGroup presets=new RadioGroup(this);body.addView(presets);
        int[] labels={R.string.browser_quality_smooth,R.string.browser_quality_balanced,R.string.browser_quality_clear};
        for(int i=0;i<3;i++){
            BrowserQuality preset=browserQualityPreset(i);RadioButton choice=new RadioButton(this);choice.setId(View.generateViewId());choice.setText(describeBrowserQuality(preset,labels[i]));choice.setTextSize(14);choice.setMinHeight(dp(48));choice.setPadding(0,dp(8),0,dp(8));presets.addView(choice);
            if(preset.longEdge==selected[0].longEdge && preset.fps==selected[0].fps && preset.maxBitrateBps==selected[0].maxBitrateBps)presets.check(choice.getId());
            choice.setOnCheckedChangeListener((button,checked)->{if(checked)selected[0]=preset;});
        }
        ScrollView scroll=new ScrollView(this);scroll.addView(body);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(R.string.browser_quality_title).setView(scroll).setNegativeButton(android.R.string.cancel,null)
            .setNeutralButton(R.string.browser_quality_advanced,(d,which)->showBrowserAdvancedQuality(selected[0]))
            .setPositiveButton(R.string.browser_quality_save,null).create();
        dialog.show();dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{if(saveBrowserQuality(selected[0]))dialog.dismiss();});
    }
    private void showBrowserAdvancedQuality(BrowserQuality initial){
        int[] edges={640,720,960,1280,1920,2560,3200,3840},frames={15,24,30,60},bitrates={1,2,4,6,8,12,16,24,32,40};
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(20),dp(8),dp(20),dp(8));body.addView(text(getString(R.string.browser_quality_next_hint),12,MUTED));
        Spinner edge=browserQualitySpinner(body,R.string.browser_quality_long_edge,edges,initial.longEdge,R.string.browser_quality_pixels);
        Spinner fps=browserQualitySpinner(body,R.string.browser_quality_fps,frames,initial.fps,R.string.browser_quality_frames);
        Spinner bitrate=browserQualitySpinner(body,R.string.browser_quality_bitrate,bitrates,initial.maxBitrateBps/1_000_000,R.string.browser_quality_mbps);
        ScrollView scroll=new ScrollView(this);scroll.addView(body);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(R.string.browser_quality_advanced).setView(scroll).setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.browser_quality_save,null).create();
        dialog.show();dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{if(saveBrowserQuality(BrowserQuality.of(edges[edge.getSelectedItemPosition()],frames[fps.getSelectedItemPosition()],bitrates[bitrate.getSelectedItemPosition()]*1_000_000)))dialog.dismiss();});
    }
    private Spinner browserQualitySpinner(LinearLayout parent,int title,int[] values,int current,int format){
        parent.addView(text(getString(title),14,INK));String[] labels=new String[values.length];int selected=0;
        for(int i=0;i<values.length;i++){labels[i]=getString(format,values[i]);if(values[i]==current)selected=i;}
        Spinner choice=spinner(labels);choice.setContentDescription(getString(title));choice.setSelection(selected);parent.addView(choice);return choice;
    }
    private void saveBrowser(){
        if(browserSource==null || browserAudio==null)return;
        getPreferences(0).edit().putInt("browserSource",browserSource.getSelectedItemPosition()).putBoolean("browserAudio",browserAudio.isChecked()).apply();
    }
    private void buildAirPlayPanel(LinearLayout page){
        airPanel=new LinearLayout(this);airPanel.setOrientation(LinearLayout.VERTICAL);page.addView(airPanel);
        LinearLayout hero=card(airPanel);styleHero(hero);
        TextView eyebrow=text(getString(R.string.ui_tab_airplay),12,BLUE);eyebrow.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));eyebrow.setVisibility(View.GONE);hero.addView(eyebrow);
        airState=text(getString(R.string.ui_airplay_ready),26,INK);airState.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));hero.addView(airState);airState.setGravity(Gravity.CENTER);
        airDetail=text(getString(R.string.ui_airplay_start_hint),13,MUTED);hero.addView(airDetail);airDetail.setGravity(Gravity.CENTER);
        airSelection=text("",13,BLUE);hero.addView(airSelection);airSelection.setGravity(Gravity.CENTER);refreshAirSelection();
        airStart=button(getString(R.string.ui_airplay_start),BLUE);airStart.setTag("airplay_open_picker");airStart.setOnClickListener(v->showAirPicker());hero.addView(airStart);
        airStop=button(getString(R.string.ui_stop_casting),0xff8a97ad);airStop.setOnClickListener(v->{reopenAirPickerAfterStop=false;requestStop();});hero.addView(airStop);
        airChange=smallButton(getString(R.string.ui_airplay_change_device));airChange.setOnClickListener(v->changeAirReceiver());
        LinearLayout.LayoutParams changeLayout=new LinearLayout.LayoutParams(-1,dp(46));changeLayout.topMargin=dp(8);hero.addView(airChange,changeLayout);
        buildAirPicker();
        airPinBox=new LinearLayout(this);airPinBox.setOrientation(LinearLayout.VERTICAL);section(airPinBox,getString(R.string.ui_pair_receiver),getString(R.string.ui_airplay_pin_hint));airPinHint=(TextView)airPinBox.getChildAt(1);
        airPin=new EditText(this);airPin.setSaveEnabled(false);airPin.setSingleLine(true);airPin.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);airPin.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(8)});airPinBox.addView(airPin);
        airSubmit=button(getString(R.string.ui_pair),BLUE);airSubmit.setOnClickListener(v->{String code=airPin.getText().toString();if(airLegacy.isChecked()?code.isEmpty() || code.length()>128:!code.matches("[0-9]{4,8}")){toast(getString(airLegacy.isChecked()?R.string.ui_airplay_password_invalid:R.string.ui_airplay_pin_invalid));return;}Bundle data=new Bundle();data.putString("pin",code);send(HostService.PIN,data);airPin.setText("");airSubmit.setEnabled(false);});airPinBox.addView(airSubmit);hero.addView(airPinBox);airPinBox.setVisibility(View.GONE);
        airAdvanced=new LinearLayout(this);airAdvanced.setOrientation(LinearLayout.VERTICAL);airPanel.addView(airAdvanced);
        LinearLayout capture=card(airAdvanced);section(capture,getString(R.string.ui_capture_source),getString(R.string.ui_airplay_capture_hint));
        airSource=spinner(new String[]{getString(R.string.ui_source_screen),getString(R.string.ui_source_app)});int initial=settings().getInt("source",0);if(initial>1)initial=0;airSource.setSelection(initial);airSource.setTag(initial);capture.addView(airSource);
        airSource.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int position,long id){boolean changed=!Integer.valueOf(position).equals(airSource.getTag());airSource.setTag(position);if(uiReady && changed && !updatingAirSource && airMode() && !session.busy()){settings().edit().putInt("source",position).apply();source.setSelection(position);}}});
        capture.addView(text(getString(R.string.airplay_quality_next_title),13,INK));
        LinearLayout qualityRow=new LinearLayout(this);qualityRow.setGravity(Gravity.CENTER_VERTICAL);capture.addView(qualityRow);
        airQuality=spinner(new String[]{getString(R.string.phone_quality_720),getString(R.string.phone_quality_1080),getString(R.string.phone_quality_1080_fast),getString(R.string.browser_quality_custom)});airQuality.setSelection(Math.max(0,Math.min(3,getPreferences(0).getInt("airplayQuality",1))));qualityRow.addView(airQuality,new LinearLayout.LayoutParams(0,-2,1));
        airQualityAdvanced=smallButton(getString(R.string.airplay_quality_advanced));airQualityAdvanced.setOnClickListener(v->showAirPlayQuality());qualityRow.addView(airQualityAdvanced,new LinearLayout.LayoutParams(-2,dp(48)));
        airQualitySummary=text("",12,MUTED);capture.addView(airQualitySummary);
        airQuality.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> parent){}public void onItemSelected(AdapterView<?> parent,View view,int position,long id){if(uiReady){saveAirPlay();updateAirQualitySummary();}}});
        airLegacy=toggle(capture,getString(R.string.ui_airplay_legacy),getString(R.string.ui_airplay_legacy_hint),getPreferences(0).getBoolean("airplayLegacy",false));airLegacy.setOnCheckedChangeListener((v,checked)->{if(uiReady){saveAirPlay();updateSessionUi();}});
        updateAirQualitySummary();
        LinearLayout dashboard=card(airAdvanced);section(dashboard,getString(R.string.ui_dashboard),getString(R.string.ui_airplay_diagnostics_hint));airMetrics=text(getString(R.string.ui_dashboard_idle),13,INK);dashboard.addView(airMetrics);airLogs=text("",11,MUTED);airLogs.setTextIsSelectable(true);dashboard.addView(airLogs);
    }
    /** Casting and APK transfer use exactly the same My TV picker and discovery registry. */
    private void buildAirPicker(){
        airPicker=new MyTvPicker(this,false,device->{
            if(session.busy() || !airMode() || device.airplayPort<=0)return;
            selectedAirAddress=new AirPlayEndpoint(device.host,device.airplayPort).address();
            selectedAirName=device.name;saveAirPlay();refreshAirSelection();dismissAirPicker();begin();
        });
        airPicker.setOnDismissListener(dialog->airPickerOpen=false);
    }
    private void showAirPicker(){
        if(!uiReady || !airMode() || session.busy() || isFinishing() || isDestroyed() || airPicker.isShowing())return;
        airPickerOpen=true;airPicker.show();
    }
    private void dismissAirPicker(){airPickerOpen=false;if(airPicker!=null)airPicker.dismiss();}
    private void refreshAirSelection(){
        if(airSelection==null)return;
        airSelection.setVisibility(selectedAirAddress.isEmpty()?View.GONE:View.VISIBLE);
        String label=selectedAirName.isEmpty()?selectedAirAddress:selectedAirName;
        airSelection.setText(getString(airConnected?R.string.ui_airplay_connected:R.string.ui_airplay_selected)+" "+label);
    }
    private void changeAirReceiver(){
        if(!airMode() || session.phase()==CastSessionCoordinator.STOPPING)return;
        if(!session.hasHost()){showAirPicker();return;}
        if(airChangeDialog!=null && airChangeDialog.isShowing())return;
        airChangeDialog=new AlertDialog.Builder(this).setTitle(R.string.ui_airplay_change_device).setMessage(R.string.ui_airplay_change_hint)
            .setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.ui_airplay_stop_choose,(dialog,which)->{if(!session.hasHost()){showAirPicker();return;}reopenAirPickerAfterStop=true;requestStop();}).create();airChangeDialog.show();
    }
    private void selectProtocol(int protocol){
        if(session.phase()==CastSessionCoordinator.STOPPING || session.selected()==protocol)return;
        dismissAirPicker();reopenAirPickerAfterStop=false;if(airChangeDialog!=null)airChangeDialog.dismiss();
        saveProfile();saveAirPlay();saveBrowser();pendingShizukuStart=false;pendingToken=-1;pendingAudioRequest=pendingProjectionRequest=-1;pendingEndpoint=null;airPin.setText("");pin.setText("");
        if(session.select(protocol)){requestStop();protocolHint.setText(R.string.ui_protocol_switch_wait);}
        else{lastFailure="";applyProtocol();running(false);}
    }
    private void applyProtocol(){
        if(!uiReady)return;
        boolean air=airMode(),browser=browserMode(),moonlight=!air && !browser;
        airAdvanced.setVisibility(air?View.VISIBLE:View.GONE);
        for(View view:moonlightOnly)if(view!=pinBox)view.setVisibility(moonlight?View.VISIBLE:View.GONE);
        for(View view:nativeSenderOnly)view.setVisibility(browser?View.GONE:View.VISIBLE);
        pinBox.setVisibility(View.GONE);airPanel.setVisibility(air?View.VISIBLE:View.GONE);browserPanel.setVisibility(browser?View.VISIBLE:View.GONE);
        advancedToggle.setVisibility(browser?View.GONE:View.VISIBLE);advancedContent.setVisibility(!browser && advancedExpanded?View.VISIBLE:View.GONE);
        protocolTabs.select(session.selected());
        protocolHint.setVisibility(browser?View.GONE:View.VISIBLE);
        protocolHint.setText(browser?R.string.browser_protocol_hint:air?R.string.ui_airplay_protocol_hint:R.string.ui_moonlight_protocol_hint);
        getPreferences(0).edit().putInt("selectedProtocol",session.selected()).apply();
        refreshControlOptions();updateSessionUi();updateDiscovery();
    }
    private void updateSessionUi(){
        if(!uiReady)return;
        boolean busy=session.busy(),hasHost=session.hasHost(),stopping=session.phase()==CastSessionCoordinator.STOPPING;
        int pinInput=airLegacy.isChecked()?InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD;
        if(airPin.getInputType()!=pinInput){airPin.setText("");airPin.setInputType(pinInput);airPin.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(airLegacy.isChecked()?128:8)});}
        airPinHint.setText(airLegacy.isChecked()?R.string.ui_airplay_password_hint:R.string.ui_airplay_pin_hint);
        airTab.setEnabled(!stopping);moonlightTab.setEnabled(!stopping);browserTab.setEnabled(!stopping);
        if(busy && airPicker.isShowing())dismissAirPicker();
        airStart.setVisibility(busy?View.GONE:View.VISIBLE);airStart.setEnabled(!busy);airStart.setText(lastFailure.isEmpty()?R.string.ui_airplay_start:R.string.ui_airplay_retry_cast);
        airChange.setVisibility(hasHost?View.VISIBLE:View.GONE);airChange.setEnabled(!stopping);
        if(airMode() && session.phase()==CastSessionCoordinator.PERMISSION){airState.setText(R.string.ui_airplay_permission_title);airDetail.setText(R.string.ui_airplay_permission_hint);}
        refreshAirSelection();
        airStop.setVisibility(busy?View.VISIBLE:View.GONE);airStop.setEnabled(!stopping);airStop.setText(hasHost?R.string.ui_stop_casting:R.string.ui_cancel_start);
        browserStart.setVisibility(busy?View.GONE:View.VISIBLE);browserStart.setEnabled(!busy);
        browserStop.setVisibility(busy?View.VISIBLE:View.GONE);browserStop.setEnabled(!stopping);browserStop.setText(hasHost?R.string.ui_stop_casting:R.string.ui_cancel_start);
        browserSource.setEnabled(!busy);browserAudio.setEnabled(!busy && Build.VERSION.SDK_INT>=29);browserCopy.setEnabled(validBrowserUrl(browserSelectedUrl));
        if(browserMode() && session.phase()==CastSessionCoordinator.PERMISSION){browserState.setText(R.string.browser_permission_title);browserDetail.setText(R.string.browser_permission_hint);}
        start.setVisibility(busy?View.GONE:View.VISIBLE);start.setEnabled(!busy);stop.setVisibility(busy?View.VISIBLE:View.GONE);stop.setEnabled(!stopping);stop.setText(hasHost?R.string.ui_stop_casting:R.string.ui_cancel_start);
        airSource.setEnabled(!busy);airQuality.setEnabled(!airLegacy.isChecked());airQualityAdvanced.setEnabled(!airLegacy.isChecked());airLegacy.setEnabled(!busy);updateAirQualitySummary();
        profile.setEnabled(!busy);source.setEnabled(!busy);scenario.setEnabled(!busy);root.setEnabled(!busy);hevc.setEnabled(!busy);name.setEnabled(!busy);watchParty.setEnabled(!busy);panelOff.setEnabled(!busy);
        scale.setEnabled(!stopping && session.phase()!=CastSessionCoordinator.PERMISSION);
        if(busy && !hasHost){control.setEnabled(false);controlBackend.setEnabled(false);audio.setEnabled(false);muteLocal.setEnabled(false);}
        else refreshControlOptions();
        if(!hasHost){airPinBox.setVisibility(View.GONE);airMetrics.setText(R.string.ui_dashboard_idle);}
        if(stopping && session.target()>=0)protocolHint.setText(R.string.ui_protocol_switch_wait);
        updateDiscovery();
    }
    private void updateDiscovery(){
        if(airPicker!=null && airPicker.isShowing() && (!resumed || !airMode() || session.busy()))dismissAirPicker();
    }
    private void saveAirPlay(){if(airQuality==null)return;getPreferences(0).edit().putString("airplayAddress",selectedAirAddress).putString("airplayReceiverName",selectedAirName).putInt("airplayQuality",airQuality.getSelectedItemPosition()).putBoolean("airplayLegacy",airLegacy.isChecked()).apply();}
    private AirQuality readAirQuality(){
        if(airLegacy.isChecked())return new AirQuality(1280,720,30,4_000_000,true);
        int selected=airQuality.getSelectedItemPosition();
        if(selected==3)return new AirQuality(getPreferences(0).getInt("airplayCustomWidth",1920),getPreferences(0).getInt("airplayCustomHeight",1080),getPreferences(0).getInt("airplayCustomFps",30),getPreferences(0).getInt("airplayCustomBitrate",8_000_000),false);
        return new AirQuality(selected==0?1280:1920,selected==0?720:1080,selected==2?60:30,selected==0?5_000_000:selected==2?16_000_000:8_000_000,false);
    }
    private void updateAirQualitySummary(){
        if(airQualitySummary==null || airLegacy==null)return;
        AirQuality quality=readAirQuality();
        airQualitySummary.setText(quality.legacy?getString(R.string.airplay_quality_legacy_limit):getString(R.string.airplay_quality_next_summary,quality.width,quality.height,quality.fps,quality.bitrate/1_000_000));
    }
    private void showAirPlayQuality(){
        if(airLegacy.isChecked()){toast(getString(R.string.airplay_quality_legacy_limit));return;}
        AirQuality initial=readAirQuality();int[] widths={1280,1920,2560,3840},heights={720,1080,1440,2160},frames={15,24,30,60};
        // Preserve the original 720p preset's 5 Mbps when opening its advanced editor.
        int[] bitrates=initial.bitrate==5_000_000?new int[]{1,2,4,5,6,8,12,16,24,32,40}:new int[]{1,2,4,6,8,12,16,24,32,40};
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(20),dp(8),dp(20),dp(8));body.addView(text(getString(R.string.airplay_quality_hint),12,MUTED));
        body.addView(text(getString(R.string.airplay_quality_output_box),14,INK));String[] boxes=new String[widths.length];int selected=0;
        for(int i=0;i<widths.length;i++){boxes[i]=getString(R.string.airplay_quality_box,widths[i],heights[i]);if(widths[i]==initial.width)selected=i;}
        Spinner box=spinner(boxes);box.setContentDescription(getString(R.string.airplay_quality_output_box));box.setSelection(selected);body.addView(box);
        Spinner fps=browserQualitySpinner(body,R.string.browser_quality_fps,frames,initial.fps,R.string.browser_quality_frames);
        Spinner bitrate=browserQualitySpinner(body,R.string.browser_quality_bitrate,bitrates,initial.bitrate/1_000_000,R.string.browser_quality_mbps);
        ScrollView scroll=new ScrollView(this);scroll.addView(body);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(R.string.airplay_quality_advanced).setView(scroll).setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.browser_quality_save,null).create();
        dialog.show();dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            int index=box.getSelectedItemPosition();AirQuality quality=new AirQuality(widths[index],heights[index],frames[fps.getSelectedItemPosition()],bitrates[bitrate.getSelectedItemPosition()]*1_000_000,false);
            if(!BrowserEncoderCapabilities.supportsAirPlay(quality.width,quality.height,quality.fps,quality.bitrate)){toast(getString(R.string.browser_quality_unsupported));return;}
            getPreferences(0).edit().putInt("airplayQuality",3).putInt("airplayCustomWidth",quality.width).putInt("airplayCustomHeight",quality.height).putInt("airplayCustomFps",quality.fps).putInt("airplayCustomBitrate",quality.bitrate).apply();
            airQuality.setSelection(3);updateAirQualitySummary();toast(getString(session.busy()?R.string.browser_quality_saved_active:R.string.browser_quality_saved));dialog.dismiss();
        });
    }
    private void launchAirPlay(Intent grant){
        if(grant==null || pendingEndpoint==null){session.denied(pendingToken);updateSessionUi();return;}
        AirQuality quality=pendingAirQuality;boolean legacy=quality.legacy;
        int width=quality.width,height=quality.height,fps=quality.fps,bitrate=quality.bitrate;
        Intent intent=new Intent(this,AirPlayHostService.class).putExtra("grant",grant).putExtra("airplayHost",pendingEndpoint.host).putExtra("airplayPort",pendingEndpoint.port).putExtra("airplayPin","").putExtra("airplayLegacy",legacy).putExtra("airplayWidth",width).putExtra("airplayHeight",height).putExtra("airplayFps",fps).putExtra("airplayBitrate",bitrate).putExtra("source",effectiveSource()).putExtra("frameLimit",fps).putExtra("scaleMode",scale.getSelectedItemPosition()).putExtra("audio",pendingAudioAllowed && !legacy).putExtra("muteLocal",pendingAudioAllowed && !legacy && muteLocal.isChecked());
        saveAirPlay();startHost(intent);
    }
    private SharedPreferences settings(){return profiles.settings();}
    private void saveProfile(){
        if(name==null || loadingProfile)return;
        settings().edit().putBoolean("watchParty",watchParty.isChecked()).putInt("source",source.getSelectedItemPosition()).putInt("scenario",scenario.getSelectedItemPosition()).putInt("scaleMode",scale.getSelectedItemPosition()).putBoolean("audio",audio.isChecked())
            .putBoolean("muteLocal",muteLocal.isChecked()).putBoolean("hevc",hevc.isChecked()).putBoolean("control",control.isChecked())
            .putInt("controlBackend",controlBackend.getSelectedItemPosition()).putBoolean("controlRoot",controlBackend.getSelectedItemPosition()==1).putString("name",name.getText().toString()).apply();
    }
    private void loadProfile(){
        loadingProfile=true;updatingScale=true;updatingControl=true;
        root.setChecked(false);watchParty.setChecked(settings().getBoolean("watchParty",false));source.setSelection(settings().getInt("source",0));openApp.setVisibility(source.getSelectedItemPosition()==2?View.VISIBLE:View.GONE);currentScenario=settings().getInt("scenario",0);scenario.setSelection(currentScenario);describeScenario(currentScenario);scale.setSelection(settings().getInt("scaleMode",CropGeometry.VIDEO_REGION));describeMode(scale.getSelectedItemPosition());
        audio.setChecked(settings().getBoolean("audio",false));muteLocal.setChecked(settings().getBoolean("muteLocal",false));
        hevc.setChecked(settings().getBoolean("hevc",true));control.setChecked(settings().getBoolean("control",false));
        controlBackend.setSelection(settings().getInt("controlBackend",settings().getBoolean("controlRoot",false)?1:0));name.setText(settings().getString("name","MoonCast · "+Build.MODEL));
        updatingScale=false;updatingControl=false;loadingProfile=false;refreshControlOptions();
    }
    private void chooseIndependentApp(){
        if(independentDisplay<0)return;
        Intent launcher=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        var apps=getPackageManager().queryIntentActivities(launcher,0);
        apps.sort((a,b)->String.CASE_INSENSITIVE_ORDER.compare(a.loadLabel(getPackageManager()).toString(),b.loadLabel(getPackageManager()).toString()));
        String[] labels=apps.stream().map(a->a.loadLabel(getPackageManager()).toString()).toArray(String[]::new);
        new AlertDialog.Builder(this).setTitle(R.string.ui_launch_independent_app).setItems(labels,(dialog,which)->{
            if(independentDisplay<0)return;var app=apps.get(which);
            Bundle data=new Bundle();data.putString("component",new ComponentName(app.activityInfo.packageName,app.activityInfo.name).flattenToString());send(HostService.LAUNCH_APP,data);
        }).setNegativeButton(android.R.string.cancel,null).show();
    }
    private void describeScenario(int value){scenarioHint.setText(getString(switch(value){case 1->R.string.ui_scenario_movie_hint;case 2->R.string.ui_scenario_game_hint;case 3->R.string.ui_scenario_eco_hint;default->R.string.ui_scenario_custom_hint;}));}
    private int frameLimit(){return scenario.getSelectedItemPosition()==3?30:scenario.getSelectedItemPosition()==1?60:120;}
    private void describeMode(int mode){modeInfo.setText(switch(mode){case 0->getString(R.string.ui_show_the_entire_phone_screen_at_its_original_aspect_ratio);case 1->getString(R.string.ui_detect_landscape_borders_including_paused_frames_letterboxing_needed_f);case 2->getString(R.string.ui_fill_the_receiver_screen_some_picture_edges_will_be_cropped);case 4->getString(R.string.ui_cinema_mode_hint);default->getString(R.string.ui_cast_the_centered_landscape_16_9_region_no_selection_or_border_detecti);});if(rescan!=null)rescan.setVisibility(mode==CropGeometry.CINEMA?View.VISIBLE:View.GONE);}
    private boolean accessibilityEnabled(){
        android.view.accessibility.AccessibilityManager manager=getSystemService(android.view.accessibility.AccessibilityManager.class);
        for(android.accessibilityservice.AccessibilityServiceInfo service:manager.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK))
            if(service.getResolveInfo().serviceInfo.packageName.equals(getPackageName()) && service.getResolveInfo().serviceInfo.name.equals(ControlAccessibilityService.class.getName()))return true;
        return false;
    }
    private void refreshControlOptions(){
        if(browserMode()){
            if(browserAudio!=null)browserAudio.setEnabled(Build.VERSION.SDK_INT>=29 && !session.busy());
            if(audio!=null)audio.setEnabled(false);
            if(muteLocal!=null)muteLocal.setEnabled(false);
            if(control!=null)control.setEnabled(false);
            if(controlBackend!=null)controlBackend.setEnabled(false);
            return;
        }
        if(airMode()){
            if(audio!=null){boolean allowed=Build.VERSION.SDK_INT>=29 && airLegacy!=null && !airLegacy.isChecked();audio.setEnabled(allowed && !session.busy());muteLocal.setEnabled(allowed && audio.isChecked() && !session.busy());}
            return;
        }
        if(source.getSelectedItemPosition()==2 && controlBackend.getSelectedItemPosition()==0)controlBackend.setSelection(2);
        if(audio!=null && root!=null){boolean allowed=source.getSelectedItemPosition()!=2 && !root.isChecked();audio.setEnabled(allowed && !session.busy());muteLocal.setEnabled(allowed && audio.isChecked() && !session.busy());}
        boolean appOnly=source.getSelectedItemPosition()==1 || (watchParty!=null && watchParty.isChecked());control.setEnabled(!appOnly);if(appOnly && control.isChecked()){updatingControl=true;control.setChecked(false);updatingControl=false;}
        boolean enabled=control.isChecked(),privileged=controlBackend.getSelectedItemPosition()!=0;
        if(shizuku!=null)shizuku.setVisibility((enabled && controlBackend.getSelectedItemPosition()==2) || source.getSelectedItemPosition()==2 || (panelOff!=null && panelOff.isChecked() && root!=null && !root.isChecked())?View.VISIBLE:View.GONE);
        controlBackend.setEnabled(enabled && remote==null && !root.isChecked());
        accessibility.setVisibility(enabled && !privileged?View.VISIBLE:View.GONE);
        if(remote==null && enabled && controlBackend.getSelectedItemPosition()==2){controlInfo.setText(getString(ShizukuInputBackend.authorized()?R.string.ui_shizuku_authorized:R.string.ui_shizuku_hint));return;}
        if(remote==null)controlInfo.setText(!enabled?getString(R.string.ui_off):privileged?getString(R.string.ui_root_continuous_touch_multi_touch_and_keys_permission_requested_on_sta):accessibilityEnabled()?getString(R.string.ui_accessibility_enabled_taps_single_finger_swipes_back_and_home):getString(R.string.ui_enable_accessibility_to_control_other_apps));
    }
    private GradientDrawable background(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private void section(LinearLayout parent,String title,String subtitle){TextView heading=text(title,17,INK);heading.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));parent.addView(heading);parent.addView(text(subtitle,12,MUTED));}
    private CompoundButton toggle(LinearLayout parent,String title,String subtitle,boolean checked){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(9),0,dp(9));
        if(title.equals(getString(R.string.ui_stream_playback_audio)) || title.equals(getString(R.string.ui_mute_phone_locally))){ImageView icon=PhoneVisuals.icon(this,title.equals(getString(R.string.ui_stream_playback_audio))?R.drawable.ic_sound:R.drawable.ic_mute,24,MUTED);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(dp(24),dp(24));params.setMarginEnd(dp(14));row.addView(icon,params);}
        LinearLayout label=new LinearLayout(this);label.setOrientation(LinearLayout.VERTICAL);label.addView(text(title,16,INK));label.addView(text(subtitle,11,MUTED));row.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        Switch toggle=new Switch(this);toggle.setChecked(checked);toggle.setContentDescription(title);toggle.setMinimumHeight(dp(48));row.addView(toggle);parent.addView(row);label.setOnClickListener(v->{if(toggle.isEnabled())toggle.performClick();});return toggle;
    }
    private Spinner spinner(String[] labels){
        Spinner v=new Spinner(this);v.setBackground(background(0xfff2f5fa,13));v.setPadding(dp(8),dp(5),dp(8),dp(5));
        v.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,labels){
            @Override public View getView(int position,View convert,ViewGroup parent){TextView t=(TextView)super.getView(position,convert,parent);t.setTextColor(INK);t.setTextSize(14);t.setSingleLine(false);t.setMaxLines(3);t.setPadding(dp(10),dp(12),dp(6),dp(12));return t;}
        });return v;
    }
    private Button smallButton(String label){Button b=new Button(this);b.setText(label);PhoneVisuals.button(b,false);b.setTextSize(14);return b;}

    private void requestShizuku(boolean thenStart){
        if(ShizukuInputBackend.authorized()){if(thenStart)begin();return;}
        if(!rikka.shizuku.Shizuku.pingBinder()){toast(getString(R.string.ui_shizuku_hint));Intent manager=getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");if(manager!=null)startActivity(manager);return;}
        pendingShizukuStart=thenStart;try{rikka.shizuku.Shizuku.requestPermission(60);}catch(RuntimeException e){pendingShizukuStart=false;toast(getString(R.string.ui_shizuku_hint));}
    }
    private void begin() {
        if(session.busy() || pendingShizukuStart)return;
        if(airMode()){
            pendingEndpoint=AirPlayEndpoint.parse(selectedAirAddress);
            if(pendingEndpoint==null){showAirPicker();return;}
        }else if(!browserMode()){
            if(watchParty.isChecked() && !io.github.jqssun.displaymirror.sunshine.NativeSessions.packaged()){toast(getString(R.string.ui_session_bridge_unavailable));return;}
            if(((!root.isChecked() && panelOff.isChecked()) || source.getSelectedItemPosition()==2 || (control.isChecked() && controlBackend.getSelectedItemPosition()==2)) && !ShizukuInputBackend.authorized()){requestShizuku(true);return;}
            if(control.isChecked() && !root.isChecked() && controlBackend.getSelectedItemPosition()==0 && !accessibilityEnabled()){
                toast(getString(R.string.ui_enable_mooncast_accessibility_first_or_turn_off_remote_input));startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));return;
            }
        }
        if(effectiveSource()==1 && Build.VERSION.SDK_INT<34){toast(getString(R.string.ui_app_capture_requires_14));return;}
        BrowserQuality browserQuality=browserMode()?readBrowserQuality():null;
        if(browserQuality!=null && !BrowserEncoderCapabilities.supports(this,browserQuality)){toast(getString(R.string.browser_quality_unsupported));return;}
        AirQuality airQuality=airMode()?readAirQuality():null;
        if(airQuality!=null && !BrowserEncoderCapabilities.supportsAirPlay(airQuality.width,airQuality.height,airQuality.fps,airQuality.bitrate)){toast(getString(R.string.browser_quality_unsupported));return;}
        pendingToken=session.begin();if(pendingToken<0)return;pendingMode=session.selected();lastFailure="";
        if(browserQuality!=null)pendingBrowserQuality=browserQuality;
        if(airQuality!=null)pendingAirQuality=airQuality;
        pendingAudioAllowed=Build.VERSION.SDK_INT>=29 && (browserMode()?browserAudio.isChecked():audio.isChecked() && (airMode()?!airLegacy.isChecked():!root.isChecked() && effectiveSource()!=2));
        saveProfile();saveBrowser();updateSessionUi();
        java.util.ArrayList<String> permissions=new java.util.ArrayList<>();
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        if(pendingAudioAllowed && checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)permissions.add(Manifest.permission.RECORD_AUDIO);
        if(!permissions.isEmpty()){pendingAudioRequest=1000+pendingToken*2;requestPermissions(permissions.toArray(new String[0]),pendingAudioRequest);return;}
        continueStart();
    }
    private int effectiveSource(){return browserMode()?browserSource.getSelectedItemPosition():airMode()?airSource.getSelectedItemPosition():source.getSelectedItemPosition();}
    private boolean acceptsPending(){return pendingToken>=0 && session.accepts(pendingToken,pendingMode);}
    private void continueStart(){
        if(!acceptsPending())return;
        if(!airMode() && !browserMode() && (root.isChecked() || effectiveSource()==2))launch(null);else requestProjection();
    }
    private void requestProjection() {
        if(!acceptsPending())return;
        MediaProjectionManager manager=getSystemService(MediaProjectionManager.class);
        Intent request=Build.VERSION.SDK_INT>=34 && effectiveSource()!=1 ? manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()) : manager.createScreenCaptureIntent();
        pendingProjectionRequest=1001+pendingToken*2;
        try{startActivityForResult(request,pendingProjectionRequest);}catch(RuntimeException e){projectionDenied();}
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants) {
        super.onRequestPermissionsResult(request,permissions,grants);
        if(request!=pendingAudioRequest || !acceptsPending())return;
        pendingAudioRequest=-1;
        if(pendingAudioAllowed && checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){pendingAudioAllowed=false;toast(getString(R.string.ui_audio_permission_denied_continuing_with_video_only));}
        continueStart();
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==DOCUMENT && result==RESULT_OK && data!=null && data.getData()!=null){startForegroundService(new Intent(this,FileCinemaService.class).setData(data.getData()).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));main.postDelayed(()->{if(resumed && !fileBound)fileBound=bindService(new Intent(this,FileCinemaService.class),fileConnection,0);},300);}
        if(request!=pendingProjectionRequest || !acceptsPending())return;
        pendingProjectionRequest=-1;
        if(result==RESULT_OK && data!=null)launch(data);
        else projectionDenied();
    }
    private void projectionDenied(){
        session.denied(pendingToken);pendingToken=-1;pendingAudioRequest=pendingProjectionRequest=-1;pendingEndpoint=null;
        if(airMode()){lastFailure=getString(R.string.ui_screen_sharing_was_not_authorized);airState.setText(R.string.ui_airplay_permission_denied);airDetail.setText(R.string.ui_airplay_permission_retry);}
        if(browserMode()){lastFailure=getString(R.string.ui_screen_sharing_was_not_authorized);browserState.setText(R.string.browser_permission_denied);browserDetail.setText(R.string.browser_start_hint);}
        updateSessionUi();toast(getString(R.string.ui_screen_sharing_was_not_authorized));
    }
    private void launch(Intent grant) {
        if(!acceptsPending())return;
        if(airMode()){launchAirPlay(grant);return;}
        if(browserMode()){
            if(grant==null){projectionDenied();return;}
            showBrowserUrls("");
            startHost(new Intent(this,BrowserHostService.class).putExtra("grant",grant).putExtra("audio",pendingAudioAllowed)
                .putExtra("source",effectiveSource()).putExtra("frameLimit",pendingBrowserQuality.fps).putExtra("scaleMode",CropGeometry.SCREEN)
                .putExtra("browserLongEdge",pendingBrowserQuality.longEdge).putExtra("browserFps",pendingBrowserQuality.fps).putExtra("browserBitrate",pendingBrowserQuality.maxBitrateBps));
            return;
        }
        String host=name.getText().toString().trim();
        if(host.isEmpty()) host="MoonCast";
        settings().edit().putString("name",host).putBoolean("hevc",hevc.isChecked()).putBoolean("audio",audio.isChecked()).putBoolean("muteLocal",muteLocal.isChecked()).putBoolean("control",control.isChecked()).putInt("controlBackend",controlBackend.getSelectedItemPosition()).putBoolean("controlRoot",controlBackend.getSelectedItemPosition()==1).apply();
        Intent i=new Intent(this,HostService.class).putExtra("panelOff",panelOff.isChecked()).putExtra("watchParty",watchParty.isChecked()).putExtra("source",source.getSelectedItemPosition()).putExtra("frameLimit",frameLimit()).putExtra("name",host).putExtra("hevc",hevc.isChecked()).putExtra("audio",pendingAudioAllowed).putExtra("muteLocal",pendingAudioAllowed && muteLocal.isChecked()).putExtra("root",root.isChecked()).putExtra("scaleMode",scale.getSelectedItemPosition()).putExtra("control",control.isChecked()).putExtra("controlBackend",controlBackend.getSelectedItemPosition()).putExtra("controlRoot",controlBackend.getSelectedItemPosition()==1);
        if(grant!=null) i.putExtra("grant",grant);
        startHost(i);
    }
    private void startHost(Intent intent){
        if(!session.start(pendingToken,pendingMode))return;
        pendingToken=-1;senderPid=-1;startingSince=SystemClock.elapsedRealtime();missingHostSince=0;
        getPreferences(0).edit().putBoolean("hostExpected",true).remove("activeHostPid").putInt("activeProtocol",session.active()).apply();
        dismissAirPicker();
        connection=new HostConnection();
        intent.putExtra("statusReply",connection.response);
        try{startForegroundService(intent);activeState().setText(R.string.ui_starting_pending);running(true);bindExisting();}
        catch(RuntimeException e){lastFailure=getString(R.string.ui_cast_start_failed);hostGone();}
    }
    private void bindExisting() {
        if(bound)return;
        if(!session.hasHost()){
            if(session.phase()==CastSessionCoordinator.IDLE)running(false);
            return;
        }
        bindingSince=SystemClock.elapsedRealtime();if(connection==null)connection=new HostConnection();
        bound=bindService(new Intent(this,hostClass()),connection,0);
        if(!bound)verifyHostGone();
    }
    private void query() { send(HostService.STATUS,null); }
    private void send(int what,Bundle data) {
        if(remote==null || connection==null)return;
        try{Message message=Message.obtain(null,what);message.replyTo=connection.response;if(data!=null)message.setData(data);remote.send(message);}
        catch(RemoteException e){if(!remote.getBinder().isBinderAlive())hostGone();else{lastFailure=getString(R.string.ui_host_unresponsive);requestStop();}}
    }
    private void running(boolean yes) { panelOff.setEnabled(!yes);restoreDisplay.setEnabled(yes);if(!yes)panelState.setText(R.string.ui_off);profile.setEnabled(!yes);watchParty.setEnabled(!yes);if(!yes){receiverList.removeAllViews();shownReceivers=new long[0];}scenario.setEnabled(!yes);source.setEnabled(!yes);if(!yes){independentDisplay=-1;openApp.setEnabled(false);} rescan.setEnabled(yes);if(!yes)metrics.setText(getString(R.string.ui_dashboard_idle)); start.setEnabled(!yes); stop.setEnabled(yes);start.setVisibility(yes?View.GONE:View.VISIBLE);stop.setVisibility(yes?View.VISIBLE:View.GONE); name.setEnabled(!yes); hevc.setEnabled(!yes); audio.setEnabled(!yes && !root.isChecked());muteLocal.setEnabled(!yes && audio.isChecked() && !root.isChecked()); root.setEnabled(!yes);refreshControlOptions(); if(!yes)pinBox.setVisibility(View.GONE);updateSessionUi(); }
    @Override protected void onResume(){super.onResume();resumed=true;updateBrowserQr();refreshControlOptions();if(reopenAirPickerAfterStop && !session.busy() && airMode()){reopenAirPickerAfterStop=false;showAirPicker();}else if(airPickerOpen)showAirPicker();updateDiscovery();main.removeCallbacks(poll);main.post(poll);}
    private void showReceivers(Bundle b){
        long[] ids=b.getLongArray("receivers");String[] labels=b.getStringArray("receiverLabels");if(ids==null || labels==null || java.util.Arrays.equals(ids,shownReceivers))return;
        shownReceivers=ids.clone();receiverList.removeAllViews();for(int i=0;i<ids.length;i++){long id=ids[i];Button disconnect=smallButton(labels[i]+" · "+getString(R.string.ui_disconnect));disconnect.setOnClickListener(v->{Bundle data=new Bundle();data.putLong("session",id);send(HostService.DISCONNECT,data);});receiverList.addView(disconnect);}
    }
    private void queryFile(){if(fileRemote==null)return;try{Message m=Message.obtain(null,FileCinemaService.STATUS);m.replyTo=fileResponse;fileRemote.send(m);}catch(RemoteException ignored){}}
    @Override protected void onPause(){resumed=false;saveProfile();saveAirPlay();saveBrowser();main.removeCallbacks(poll);dismissAirPicker();super.onPause();}
    @Override protected void onStop(){clearBrowserQr();super.onStop();}
    @Override protected void onSaveInstanceState(Bundle out){
        super.onSaveInstanceState(out);saveProfile();saveAirPlay();saveBrowser();
        out.putBoolean("advancedOpen",advancedExpanded);
        out.putInt("protocol",session.selected());out.putInt("activeProtocol",session.active());out.putInt("targetProtocol",session.target());
        out.putInt("sessionPhase",session.phase());out.putInt("sessionGeneration",session.generation());out.putInt("senderPid",senderPid);
        out.putInt("pendingToken",pendingToken);out.putInt("pendingMode",pendingMode);out.putInt("pendingAudioRequest",pendingAudioRequest);out.putInt("pendingProjectionRequest",pendingProjectionRequest);
        out.putBoolean("pendingAudioAllowed",pendingAudioAllowed);out.putString("pendingEndpoint",pendingEndpoint==null?"":pendingEndpoint.address());
        out.putInt("pendingBrowserLongEdge",pendingBrowserQuality.longEdge);out.putInt("pendingBrowserFps",pendingBrowserQuality.fps);out.putInt("pendingBrowserBitrate",pendingBrowserQuality.maxBitrateBps);
        out.putInt("pendingAirWidth",pendingAirQuality.width);out.putInt("pendingAirHeight",pendingAirQuality.height);out.putInt("pendingAirFps",pendingAirQuality.fps);out.putInt("pendingAirBitrate",pendingAirQuality.bitrate);out.putBoolean("pendingAirLegacy",pendingAirQuality.legacy);
        out.putBoolean("airPickerOpen",airPickerOpen);out.putBoolean("reopenAirPickerAfterStop",reopenAirPickerAfterStop);out.putBoolean("airConnected",airConnected);
        out.putInt("airSource",airSource.getSelectedItemPosition());out.putString("lastFailure",lastFailure);
    }
    @Override protected void onDestroy(){showBrowserUrls("");dismissAirPicker();if(airChangeDialog!=null)airChangeDialog.dismiss();detachHost();if(fileBound){unbindService(fileConnection);fileBound=false;fileRemote=null;}rikka.shizuku.Shizuku.removeRequestPermissionResultListener(shizukuPermission);main.removeCallbacksAndMessages(null);super.onDestroy();}
    private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
    private TextView text(String value,int sp,int color){TextView v=new TextView(this);v.setText(value);v.setTextSize(sp);v.setTextColor(color);v.setPadding(0,dp(5),0,dp(5));return v;}
    private LinearLayout card(LinearLayout parent){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);v.setPadding(dp(20),dp(16),dp(20),dp(16));GradientDrawable bg=new GradientDrawable();bg.setColor(Color.WHITE);bg.setCornerRadius(dp(24));bg.setStroke(dp(1),0xffecf0f5);v.setBackground(bg);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(14);parent.addView(v,p);return v;}
    private Button button(String label,int color){Button v=new Button(this);v.setText(label);PhoneVisuals.button(v,color==BLUE);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(56));p.topMargin=dp(14);v.setLayoutParams(p);return v;}

    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
