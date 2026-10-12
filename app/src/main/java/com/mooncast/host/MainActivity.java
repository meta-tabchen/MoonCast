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
    private LinearLayout browserPanel;
    private RadioButton browserTab;
    private Button browserStart,browserStop,browserCopy;
    private TextView browserState,browserDetail,browserLink;
    private Spinner browserSource,browserAddress;
    private CompoundButton browserAudio;
    private String browserUrls="";
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
        if(!session.hasHost()){
            session.stop();pendingEndpoint=null;activeState().setText(R.string.ui_stopped);
            activeDetail().setText(browserMode()?R.string.browser_start_hint:airMode()?R.string.ui_airplay_start_hint:R.string.ui_starting_again_will_request_screen_sharing_permission);updateSessionUi();return;
        }
        if(browserMode())showBrowserUrls("");
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
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(22),dp(20),dp(22),dp(28));page.setBackgroundColor(PhoneVisuals.CANVAS);scroll.addView(page);setContentView(scroll);
        TextView title=text("MoonCast",28,INK);title.setGravity(Gravity.CENTER);title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));title.setPadding(0,dp(8),0,dp(10));page.addView(title);
        if((getPackageName().equals("com.mooncast.unified.preview") || getPackageName().equals("com.mooncast.community"))){
            Button deviceMode=smallButton(getString(R.string.phone_device_settings));
            deviceMode.setOnClickListener(v->new android.app.AlertDialog.Builder(this).setTitle(R.string.phone_settings_title)
                .setItems(new String[]{getString(R.string.phone_switch_device_mode),getString(R.string.phone_language),getString(R.string.phone_about_version)},(d,w)->{
                    if(w==0){PhoneRolePicker.show(this,choice->{startActivity(new Intent().setClassName(getPackageName(),"com.mooncast.unified.RoleSettingsActivity").putExtra("role_choice",choice).putExtra("require_cleanup",true));finish();});}
                    else if(w==1){com.mooncast.locale.LanguagePicker.show(this,choice->{startActivity(new Intent().setClassName(getPackageName(),"com.mooncast.unified.LanguageSettingsActivity").putExtra("require_cleanup",true).putExtra("language_choice",choice));finish();});}
                    else showAbout();
                }).show());page.addView(deviceMode);
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
        page.removeView(sound);page.addView(sound,4);
        page.removeView(pinBox);page.addView(pinBox,4);
        page.removeView(hero);page.addView(hero,4);
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
        hero.setBackgroundColor(Color.TRANSPARENT);hero.setPadding(0,dp(18),0,dp(10));
        ImageView icon=PhoneVisuals.icon(this,R.drawable.ic_cast,100,BLUE);LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(dp(100),dp(100));layout.gravity=Gravity.CENTER_HORIZONTAL;layout.bottomMargin=dp(14);hero.addView(icon,layout);
    }
    private void updateAdvancedLabel(){advancedToggle.setText(advancedExpanded?R.string.visual_advanced_close:R.string.visual_advanced);}

    private void buildProtocolTabs(LinearLayout page){
        protocolTabs=new ProtocolTabs(this,mode->{selectProtocol(mode);protocolTabs.select(session.selected());});
        airTab=protocolTabs.addTab(getString(R.string.ui_tab_airplay),"protocol_tab_airplay",CastSessionCoordinator.AIRPLAY);
        moonlightTab=protocolTabs.addTab(getString(R.string.phone_tab_gamestream),"protocol_tab_gamestream",CastSessionCoordinator.MOONLIGHT);
        browserTab=protocolTabs.addTab(getString(R.string.browser_tab),"protocol_tab_browser",CastSessionCoordinator.BROWSER);
        page.addView(protocolTabs,new LinearLayout.LayoutParams(-1,-2));
        protocolHint=text("",12,MUTED);protocolHint.setGravity(Gravity.CENTER);page.addView(protocolHint);
    }
    private void showAbout(){
        String message=com.mooncast.locale.AppBuildInfo.describe(this);
        if(getPackageName().equals("com.mooncast.community"))message+="\n\n"+getString(R.string.phone_community_signing_notice);
        new AlertDialog.Builder(this).setTitle(R.string.phone_about_version).setMessage(message).setPositiveButton(android.R.string.ok,null).show();
    }
    private void buildBrowserPanel(LinearLayout page){
        browserPanel=new LinearLayout(this);browserPanel.setOrientation(LinearLayout.VERTICAL);page.addView(browserPanel);
        LinearLayout hero=card(browserPanel);styleHero(hero);
        browserState=text(getString(R.string.browser_ready),26,INK);browserState.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));browserState.setGravity(Gravity.CENTER);hero.addView(browserState);
        browserDetail=text(getString(R.string.browser_start_hint),13,MUTED);browserDetail.setGravity(Gravity.CENTER);hero.addView(browserDetail);
        browserStart=button(getString(R.string.browser_start),BLUE);browserStart.setTag("browser_start");browserStart.setOnClickListener(v->begin());hero.addView(browserStart);
        browserStop=button(getString(R.string.ui_stop_casting),0xff8a97ad);browserStop.setOnClickListener(v->requestStop());hero.addView(browserStop);

        LinearLayout connect=card(browserPanel);section(connect,getString(R.string.browser_open_link),getString(R.string.browser_open_link_hint));
        browserAddress=spinner(new String[]{});browserAddress.setContentDescription(getString(R.string.browser_address));connect.addView(browserAddress);
        browserLink=text(getString(R.string.browser_link_pending),14,INK);browserLink.setTypeface(Typeface.MONOSPACE);browserLink.setTextIsSelectable(true);connect.addView(browserLink);
        browserAddress.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(AdapterView<?> parent){}
            public void onItemSelected(AdapterView<?> parent,View view,int position,long id){Object address=browserAddress.getSelectedItem();if(address!=null)browserLink.setText(address.toString());}
        });
        browserCopy=smallButton(getString(R.string.browser_copy_link));browserCopy.setOnClickListener(v->{
            Object address=browserAddress.getSelectedItem();if(address==null || browserUrls.isEmpty())return;
            getSystemService(android.content.ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("MoonCast",address.toString()));toast(getString(R.string.ui_file_link_copied));
        });connect.addView(browserCopy);showBrowserUrls("");

        LinearLayout capture=card(browserPanel);section(capture,getString(R.string.ui_capture_source),getString(R.string.browser_capture_hint));
        browserSource=spinner(new String[]{getString(R.string.ui_source_screen),getString(R.string.ui_source_app)});
        browserSource.setSelection(Math.max(0,Math.min(1,getPreferences(0).getInt("browserSource",0))));capture.addView(browserSource);
        browserSource.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> parent){}public void onItemSelected(AdapterView<?> parent,View view,int position,long id){if(uiReady && !session.busy())saveBrowser();}});
        browserAudio=toggle(capture,getString(R.string.ui_stream_playback_audio),getString(R.string.browser_audio_hint),getPreferences(0).getBoolean("browserAudio",false));
        browserAudio.setOnCheckedChangeListener((button,checked)->{if(uiReady && !session.busy())saveBrowser();});
        capture.addView(text(getString(R.string.browser_controls_hint),12,MUTED));
    }
    private void showBrowserUrls(String urls){
        String normalized=urls==null?"":urls.trim();
        if(!normalized.equals(browserUrls) || browserAddress.getAdapter().getCount()==0){
            String previous=browserAddress.getSelectedItem()==null?"":browserAddress.getSelectedItem().toString();
            browserUrls=normalized;
            String[] addresses=browserUrls.isEmpty()?new String[]{}:browserUrls.split("\\s*\\n\\s*");
            browserAddress.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,addresses));
            int selected=0;for(int i=0;i<addresses.length;i++)if(addresses[i].equals(previous))selected=i;
            if(addresses.length>0){browserAddress.setSelection(selected);browserLink.setText(addresses[selected]);}
        }
        if(browserUrls.isEmpty())browserLink.setText(R.string.browser_link_pending);
        browserAddress.setVisibility(browserAddress.getAdapter().getCount()>1?View.VISIBLE:View.GONE);
        browserCopy.setEnabled(!browserUrls.isEmpty() && session.hasHost() && session.phase()!=CastSessionCoordinator.STOPPING);
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
        capture.addView(text(getString(R.string.ui_airplay_quality),13,INK));airQuality=spinner(new String[]{getString(R.string.phone_quality_720),getString(R.string.phone_quality_1080),getString(R.string.phone_quality_1080_fast)});airQuality.setSelection(Math.max(0,Math.min(2,getPreferences(0).getInt("airplayQuality",1))));capture.addView(airQuality);
        airLegacy=toggle(capture,getString(R.string.ui_airplay_legacy),getString(R.string.ui_airplay_legacy_hint),getPreferences(0).getBoolean("airplayLegacy",false));airLegacy.setOnCheckedChangeListener((v,checked)->{if(uiReady){saveAirPlay();updateSessionUi();}});
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
        browserSource.setEnabled(!busy);browserAudio.setEnabled(!busy && Build.VERSION.SDK_INT>=29);browserCopy.setEnabled(!browserUrls.isEmpty() && hasHost && !stopping);
        if(browserMode() && session.phase()==CastSessionCoordinator.PERMISSION){browserState.setText(R.string.browser_permission_title);browserDetail.setText(R.string.browser_permission_hint);}
        start.setVisibility(busy?View.GONE:View.VISIBLE);start.setEnabled(!busy);stop.setVisibility(busy?View.VISIBLE:View.GONE);stop.setEnabled(!stopping);stop.setText(hasHost?R.string.ui_stop_casting:R.string.ui_cancel_start);
        airSource.setEnabled(!busy);airQuality.setEnabled(!busy && !airLegacy.isChecked());airLegacy.setEnabled(!busy);
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
    private void launchAirPlay(Intent grant){
        if(grant==null || pendingEndpoint==null){session.denied(pendingToken);updateSessionUi();return;}
        int quality=airQuality.getSelectedItemPosition();boolean legacy=airLegacy.isChecked();
        int width=legacy || quality==0?1280:1920,height=legacy || quality==0?720:1080,fps=!legacy && quality==2?60:30,bitrate=legacy || quality==0?5000000:quality==2?16000000:8000000;
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
        pendingToken=session.begin();if(pendingToken<0)return;pendingMode=session.selected();lastFailure="";
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
                .putExtra("source",effectiveSource()).putExtra("frameLimit",30).putExtra("scaleMode",CropGeometry.SCREEN));
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
    @Override protected void onResume(){super.onResume();resumed=true;refreshControlOptions();if(reopenAirPickerAfterStop && !session.busy() && airMode()){reopenAirPickerAfterStop=false;showAirPicker();}else if(airPickerOpen)showAirPicker();updateDiscovery();main.removeCallbacks(poll);main.post(poll);}
    private void showReceivers(Bundle b){
        long[] ids=b.getLongArray("receivers");String[] labels=b.getStringArray("receiverLabels");if(ids==null || labels==null || java.util.Arrays.equals(ids,shownReceivers))return;
        shownReceivers=ids.clone();receiverList.removeAllViews();for(int i=0;i<ids.length;i++){long id=ids[i];Button disconnect=smallButton(labels[i]+" · "+getString(R.string.ui_disconnect));disconnect.setOnClickListener(v->{Bundle data=new Bundle();data.putLong("session",id);send(HostService.DISCONNECT,data);});receiverList.addView(disconnect);}
    }
    private void queryFile(){if(fileRemote==null)return;try{Message m=Message.obtain(null,FileCinemaService.STATUS);m.replyTo=fileResponse;fileRemote.send(m);}catch(RemoteException ignored){}}
    @Override protected void onPause(){resumed=false;saveProfile();saveAirPlay();saveBrowser();main.removeCallbacks(poll);dismissAirPicker();super.onPause();}
    @Override protected void onSaveInstanceState(Bundle out){
        super.onSaveInstanceState(out);saveProfile();saveAirPlay();saveBrowser();
        out.putBoolean("advancedOpen",advancedExpanded);
        out.putInt("protocol",session.selected());out.putInt("activeProtocol",session.active());out.putInt("targetProtocol",session.target());
        out.putInt("sessionPhase",session.phase());out.putInt("sessionGeneration",session.generation());out.putInt("senderPid",senderPid);
        out.putInt("pendingToken",pendingToken);out.putInt("pendingMode",pendingMode);out.putInt("pendingAudioRequest",pendingAudioRequest);out.putInt("pendingProjectionRequest",pendingProjectionRequest);
        out.putBoolean("pendingAudioAllowed",pendingAudioAllowed);out.putString("pendingEndpoint",pendingEndpoint==null?"":pendingEndpoint.address());
        out.putBoolean("airPickerOpen",airPickerOpen);out.putBoolean("reopenAirPickerAfterStop",reopenAirPickerAfterStop);out.putBoolean("airConnected",airConnected);
        out.putInt("airSource",airSource.getSelectedItemPosition());out.putString("lastFailure",lastFailure);
    }
    @Override protected void onDestroy(){dismissAirPicker();if(airChangeDialog!=null)airChangeDialog.dismiss();detachHost();if(fileBound){unbindService(fileConnection);fileBound=false;fileRemote=null;}rikka.shizuku.Shizuku.removeRequestPermissionResultListener(shizukuPermission);main.removeCallbacksAndMessages(null);super.onDestroy();}
    private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
    private TextView text(String value,int sp,int color){TextView v=new TextView(this);v.setText(value);v.setTextSize(sp);v.setTextColor(color);v.setPadding(0,dp(5),0,dp(5));return v;}
    private LinearLayout card(LinearLayout parent){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);v.setPadding(dp(20),dp(16),dp(20),dp(16));GradientDrawable bg=new GradientDrawable();bg.setColor(Color.WHITE);bg.setCornerRadius(dp(24));bg.setStroke(dp(1),0xffecf0f5);v.setBackground(bg);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(14);parent.addView(v,p);return v;}
    private Button button(String label,int color){Button v=new Button(this);v.setText(label);PhoneVisuals.button(v,color==BLUE);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(56));p.topMargin=dp(14);v.setLayoutParams(p);return v;}

    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
