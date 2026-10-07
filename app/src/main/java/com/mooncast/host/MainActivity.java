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
    private static final int PROJECTION=50, AUDIO=51;
    private final Handler main=new Handler(Looper.getMainLooper());
    private Messenger remote;
    private boolean bound, resumed;
    private TextView state, detail, logs, ip;
    private Button start, stop, submit;
    private CompoundButton hevc, audio, root, muteLocal, control;
    private Spinner controlBackend;
    private TextView controlInfo, modeInfo;
    private Button accessibility, rescan;
    private boolean updatingControl;
    private EditText pin, name;
    private LinearLayout pinBox;
    private Spinner scale;
    private boolean updatingScale;
    private final Messenger response=new Messenger(new Handler(Looper.getMainLooper(), msg -> {
        if (msg.what==HostService.STATUS) {
            Bundle b=msg.getData(); state.setText(b.getString("state")); detail.setText(b.getString("detail"));
            logs.setText(b.getString("logs")); pinBox.setVisibility(b.getBoolean("pinPending")?View.VISIBLE:View.GONE);
            updatingControl=true;control.setChecked(b.getBoolean("control",false));updatingControl=false;
            controlInfo.setText(b.getString("controlStatus",getString(R.string.ui_off)));running(true);
            int mode=b.getInt("scaleMode",CropGeometry.VIDEO_REGION);
            if(scale.getSelectedItemPosition()!=mode){updatingScale=true;scale.setSelection(mode);updatingScale=false;}
        }
        return true;
    }));
    private final ServiceConnection connection=new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName n,IBinder b) { remote=new Messenger(b); bound=true; query(); }
        @Override public void onServiceDisconnected(ComponentName n) {
            remote=null; if(bound) { unbindService(this); bound=false; }
            running(false); state.setText(getString(R.string.ui_stopped)); detail.setText(getString(R.string.ui_starting_again_will_request_screen_sharing_permission));
        }
    };
    private final Runnable poll=new Runnable() {
        @Override public void run() { if (!resumed) return; if (remote==null) bindExisting(); else query(); ip.setText(HostService.addresses(MainActivity.this)); main.postDelayed(this,1500); }
    };
    private static final int INK=0xff172338, MUTED=0xff778396, BLUE=0xff3868ed;
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(0xfff5f7fb);getWindow().setNavigationBarColor(0xfff5f7fb);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(22),dp(20),dp(22),dp(28));page.setBackgroundColor(0xfff5f7fb);scroll.addView(page);setContentView(scroll);
        LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark=text("↗",30,Color.WHITE);mark.setGravity(Gravity.CENTER);mark.setBackground(background(BLUE,16));heading.addView(mark,new LinearLayout.LayoutParams(dp(52),dp(52)));
        LinearLayout wordmark=new LinearLayout(this);wordmark.setOrientation(LinearLayout.VERTICAL);wordmark.setPadding(dp(13),0,0,0);
        TextView title=text("MoonCast",27,INK);title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));wordmark.addView(title);wordmark.addView(text(getString(R.string.ui_phone_moonlight_lan),12,MUTED));heading.addView(wordmark,new LinearLayout.LayoutParams(0,-2,1));
        TextView version=text(BuildConfig.VERSION_NAME,11,MUTED);heading.addView(version);page.addView(heading);

        LinearLayout hero=card(page);hero.setBackground(background(0xffeaf0ff,24));hero.addView(text(getString(R.string.ui_connection),12,BLUE));
        state=text(getString(R.string.ui_ready_to_cast),26,INK);state.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));hero.addView(state);
        detail=text(getString(R.string.ui_start_casting_then_connect_from_moonlight_on_your_ipad_or_tv),13,MUTED);hero.addView(detail);
        LinearLayout address=new LinearLayout(this);address.setGravity(Gravity.CENTER_VERTICAL);address.setPadding(0,dp(8),0,0);
        LinearLayout ipLabel=new LinearLayout(this);ipLabel.setOrientation(LinearLayout.VERTICAL);ipLabel.addView(text(getString(R.string.ui_phone_lan_ip),11,MUTED));
        ip=text(HostService.addresses(MainActivity.this),18,INK);ip.setTypeface(Typeface.MONOSPACE);ip.setTextIsSelectable(true);ipLabel.addView(ip);address.addView(ipLabel,new LinearLayout.LayoutParams(0,-2,1));
        Button copy=smallButton(getString(R.string.ui_copy_ip));copy.setOnClickListener(v->{android.content.ClipboardManager clipboard=getSystemService(android.content.ClipboardManager.class);clipboard.setPrimaryClip(ClipData.newPlainText("MoonCast IP",HostService.addresses(MainActivity.this)));toast(getString(R.string.ui_ip_copied));});address.addView(copy);hero.addView(address);

        LinearLayout picture=card(page);section(picture,getString(R.string.ui_picture),getString(R.string.ui_choose_how_the_picture_fits_your_screen));
        scale=spinner(new String[]{getString(R.string.ui_whole_screen_fit),getString(R.string.ui_auto_crop_fit),getString(R.string.ui_fill_screen_crop_edges),getString(R.string.ui_video_region_centered_16_9),getString(R.string.ui_cinema_mode)});
        if(!getPreferences(0).getBoolean("videoRegionIntroduced",false))getPreferences(0).edit().putInt("scaleMode",3).putBoolean("videoRegionIntroduced",true).apply();
        scale.setSelection(getPreferences(0).getInt("scaleMode",3));picture.addView(scale);modeInfo=text("",12,MUTED);picture.addView(modeInfo);
        rescan=smallButton(getString(R.string.ui_rescan_video));rescan.setEnabled(false);rescan.setOnClickListener(v->{send(HostService.CROP_RESET,null);toast(getString(R.string.ui_rescan_video_hint));});picture.addView(rescan);describeMode(scale.getSelectedItemPosition());
        scale.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(AdapterView<?> parent){}
            public void onItemSelected(AdapterView<?> parent,View view,int position,long id){describeMode(position);if(updatingScale)return;getPreferences(0).edit().putInt("scaleMode",position).apply();if(remote!=null)try{Message m=Message.obtain(null,HostService.SCALE);m.arg1=position;remote.send(m);}catch(RemoteException ignored){}}
        });

        LinearLayout sound=card(page);section(sound,getString(R.string.ui_sound),getString(R.string.ui_choose_where_playback_audio_is_heard));
        audio=toggle(sound,getString(R.string.ui_stream_playback_audio),getString(R.string.ui_android_10_source_app_must_allow_capture),getPreferences(0).getBoolean("audio",false));
        muteLocal=toggle(sound,getString(R.string.ui_mute_phone_locally),getString(R.string.ui_only_while_streaming_audio_restore_on_stop),getPreferences(0).getBoolean("muteLocal",false));muteLocal.setEnabled(audio.isChecked());

        LinearLayout input=card(page);section(input,getString(R.string.ui_remote_input),getString(R.string.ui_let_a_paired_receiver_control_this_phone));
        control=toggle(input,getString(R.string.ui_enable_remote_input),getString(R.string.ui_turn_off_for_viewing_only),getPreferences(0).getBoolean("control",false));
        controlBackend=spinner(new String[]{getString(R.string.ui_no_root_accessibility_taps_and_swipes),getString(R.string.ui_root_continuous_touch_and_keyboard)});controlBackend.setSelection(getPreferences(0).getBoolean("controlRoot",false)?1:0);input.addView(controlBackend);
        controlInfo=text(getString(R.string.ui_off),12,MUTED);input.addView(controlInfo);
        accessibility=smallButton(getString(R.string.ui_enable_mooncast_accessibility));accessibility.setOnClickListener(v->startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)));input.addView(accessibility);

        LinearLayout settings=card(page);TextView expand=text(getString(R.string.ui_connection_and_device_settings_collapsed),15,INK);expand.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));settings.addView(expand);
        LinearLayout advanced=new LinearLayout(this);advanced.setOrientation(LinearLayout.VERTICAL);advanced.setVisibility(View.GONE);settings.addView(advanced);
        expand.setOnClickListener(v->{boolean open=advanced.getVisibility()!=View.VISIBLE;advanced.setVisibility(open?View.VISIBLE:View.GONE);expand.setText(open?getString(R.string.ui_connection_and_device_settings_expanded):getString(R.string.ui_connection_and_device_settings_collapsed));});
        advanced.addView(text(getString(R.string.ui_host_name),12,MUTED));name=new EditText(this);name.setSingleLine(true);name.setTextSize(15);name.setText(getPreferences(0).getString("name","MoonCast · "+Build.MODEL));advanced.addView(name);
        hevc=toggle(advanced,getString(R.string.ui_prefer_hevc_h_265),getString(R.string.ui_set_resolution_frame_rate_and_bitrate_in_moonlight),getPreferences(0).getBoolean("hevc",true));
        root=toggle(advanced,getString(R.string.ui_root_screen_capture),getString(R.string.ui_experimental_no_capture_dialog_video_only),false);
        root.setOnCheckedChangeListener((button,checked)->{audio.setEnabled(!checked && remote==null);if(checked){audio.setChecked(false);controlBackend.setSelection(1);}muteLocal.setEnabled(!checked && audio.isChecked() && remote==null);refreshControlOptions();});
        audio.setOnCheckedChangeListener((button,checked)->muteLocal.setEnabled(checked && remote==null && !root.isChecked()));
        control.setOnCheckedChangeListener((button,checked)->{
            if(updatingControl)return;getPreferences(0).edit().putBoolean("control",checked).apply();refreshControlOptions();
            if(remote!=null)try{Message m=Message.obtain(null,HostService.CONTROL);m.arg1=checked?1:0;remote.send(m);}catch(RemoteException ignored){}
        });
        controlBackend.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int position,long id){getPreferences(0).edit().putBoolean("controlRoot",position==1).apply();refreshControlOptions();}});
        advanced.addView(text(getString(R.string.ui_device_encoders),12,MUTED));TextView codec=text(EncoderSupport.describe(),11,MUTED);codec.setTextIsSelectable(true);advanced.addView(codec);
        Button pattern=smallButton(getString(R.string.ui_open_visual_test_pattern));pattern.setOnClickListener(v->startActivity(new Intent(this,TestPatternActivity.class)));advanced.addView(pattern);
        advanced.addView(text(getString(R.string.ui_diagnostics),12,MUTED));logs=text(getString(R.string.ui_waiting_to_start_pending),11,MUTED);logs.setTextIsSelectable(true);advanced.addView(logs);

        start=button(getString(R.string.ui_start_casting),BLUE);hero.addView(start);start.setOnClickListener(v->begin());
        stop=button(getString(R.string.ui_stop_casting),0xff8a97ad);hero.addView(stop);stop.setOnClickListener(v->send(HostService.STOP,null));stop.setEnabled(false);stop.setVisibility(View.GONE);
        pinBox=card(page);pinBox.setVisibility(View.GONE);section(pinBox,getString(R.string.ui_pair_receiver),getString(R.string.ui_enter_the_4_digit_pin_shown_in_moonlight));
        pin=new EditText(this);pin.setHint("0000");pin.setTextSize(26);pin.setTypeface(Typeface.MONOSPACE);pin.setInputType(InputType.TYPE_CLASS_NUMBER);pin.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(4)});pinBox.addView(pin);
        submit=button(getString(R.string.ui_pair),BLUE);pinBox.addView(submit);submit.setOnClickListener(v->{String code=pin.getText().toString();if(!code.matches("[0-9]{4}")){toast(getString(R.string.ui_enter_a_4_digit_pin));return;}Bundle data=new Bundle();data.putString("pin",code);send(HostService.PIN,data);pin.setText("");});
        page.addView(text(getString(R.string.ui_use_the_same_lan_and_official_moonlight_nshare_the_entire_phone_screen),12,MUTED));
        TextView foot=text("MoonCast Preview · GPLv3",11,0xffa4adbc);foot.setGravity(Gravity.CENTER);foot.setPadding(0,dp(18),0,0);page.addView(foot);refreshControlOptions();
    }
    private void describeMode(int mode){modeInfo.setText(switch(mode){case 0->getString(R.string.ui_show_the_entire_phone_screen_at_its_original_aspect_ratio);case 1->getString(R.string.ui_detect_landscape_borders_including_paused_frames_letterboxing_needed_f);case 2->getString(R.string.ui_fill_the_receiver_screen_some_picture_edges_will_be_cropped);case 4->getString(R.string.ui_cinema_mode_hint);default->getString(R.string.ui_cast_the_centered_landscape_16_9_region_no_selection_or_border_detecti);});if(rescan!=null)rescan.setVisibility(mode==CropGeometry.CINEMA?View.VISIBLE:View.GONE);}
    private boolean accessibilityEnabled(){
        android.view.accessibility.AccessibilityManager manager=getSystemService(android.view.accessibility.AccessibilityManager.class);
        for(android.accessibilityservice.AccessibilityServiceInfo service:manager.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK))
            if(service.getResolveInfo().serviceInfo.packageName.equals(getPackageName()) && service.getResolveInfo().serviceInfo.name.equals(ControlAccessibilityService.class.getName()))return true;
        return false;
    }
    private void refreshControlOptions(){
        boolean enabled=control.isChecked(),privileged=controlBackend.getSelectedItemPosition()==1;
        controlBackend.setEnabled(enabled && remote==null && !root.isChecked());
        accessibility.setVisibility(enabled && !privileged?View.VISIBLE:View.GONE);
        if(remote==null)controlInfo.setText(!enabled?getString(R.string.ui_off):privileged?getString(R.string.ui_root_continuous_touch_multi_touch_and_keys_permission_requested_on_sta):accessibilityEnabled()?getString(R.string.ui_accessibility_enabled_taps_single_finger_swipes_back_and_home):getString(R.string.ui_enable_accessibility_to_control_other_apps));
    }
    private GradientDrawable background(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private void section(LinearLayout parent,String title,String subtitle){TextView heading=text(title,17,INK);heading.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));parent.addView(heading);parent.addView(text(subtitle,12,MUTED));}
    private CompoundButton toggle(LinearLayout parent,String title,String subtitle,boolean checked){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(9),0,dp(9));
        LinearLayout label=new LinearLayout(this);label.setOrientation(LinearLayout.VERTICAL);label.addView(text(title,14,INK));label.addView(text(subtitle,11,MUTED));row.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        Switch toggle=new Switch(this);toggle.setChecked(checked);toggle.setContentDescription(title);row.addView(toggle);parent.addView(row);label.setOnClickListener(v->{if(toggle.isEnabled())toggle.toggle();});return toggle;
    }
    private Spinner spinner(String[] labels){
        Spinner v=new Spinner(this);v.setBackground(background(0xfff2f5fa,13));v.setPadding(dp(8),dp(5),dp(8),dp(5));
        v.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,labels){
            @Override public View getView(int position,View convert,ViewGroup parent){TextView t=(TextView)super.getView(position,convert,parent);t.setTextColor(INK);t.setTextSize(14);t.setSingleLine(false);t.setMaxLines(3);t.setPadding(dp(10),dp(12),dp(6),dp(12));return t;}
        });return v;
    }
    private Button smallButton(String label){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(12);b.setTextColor(BLUE);b.setBackground(background(0xffeaf0ff,12));b.setMinHeight(dp(42));b.setMinimumHeight(dp(42));b.setPadding(dp(14),0,dp(14),0);return b;}
    private void begin() {
        if(control.isChecked() && !root.isChecked() && controlBackend.getSelectedItemPosition()==0 && !accessibilityEnabled()){
            toast(getString(R.string.ui_enable_mooncast_accessibility_first_or_turn_off_remote_input));startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));return;
        }
        java.util.ArrayList<String> permissions=new java.util.ArrayList<>();
        if (Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        if (!root.isChecked() && audio.isChecked() && Build.VERSION.SDK_INT>=29 && checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) permissions.add(Manifest.permission.RECORD_AUDIO);
        if (!permissions.isEmpty()) { requestPermissions(permissions.toArray(new String[0]),AUDIO); return; }
        if (root.isChecked()) launch(null); else requestProjection();
    }
    private void requestProjection() {
        MediaProjectionManager manager=getSystemService(MediaProjectionManager.class);
        Intent request=Build.VERSION.SDK_INT>=34 ? manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()) : manager.createScreenCaptureIntent();
        startActivityForResult(request,PROJECTION);
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants) {
        super.onRequestPermissionsResult(request,permissions,grants);
        if(request==AUDIO) {
            if(audio.isChecked() && checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){audio.setChecked(false);toast(getString(R.string.ui_audio_permission_denied_continuing_with_video_only));}
            if(root.isChecked()) launch(null); else requestProjection();
        }
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==PROJECTION) { if(result==RESULT_OK && data!=null) launch(data); else toast(getString(R.string.ui_screen_sharing_was_not_authorized)); }
    }
    private void launch(Intent grant) {
        String host=name.getText().toString().trim();
        if(host.isEmpty()) host="MoonCast";
        getPreferences(0).edit().putString("name",host).putBoolean("hevc",hevc.isChecked()).putBoolean("audio",audio.isChecked()).putBoolean("muteLocal",muteLocal.isChecked()).putBoolean("control",control.isChecked()).putBoolean("controlRoot",controlBackend.getSelectedItemPosition()==1).apply();
        Intent i=new Intent(this,HostService.class).putExtra("name",host).putExtra("hevc",hevc.isChecked()).putExtra("audio",audio.isChecked()).putExtra("muteLocal",audio.isChecked() && muteLocal.isChecked()).putExtra("root",root.isChecked()).putExtra("scaleMode",scale.getSelectedItemPosition()).putExtra("control",control.isChecked()).putExtra("controlRoot",controlBackend.getSelectedItemPosition()==1);
        if(grant!=null) i.putExtra("grant",grant);
        startForegroundService(i); state.setText(getString(R.string.ui_starting_pending)); main.postDelayed(this::bindExisting,300);
    }
    private void bindExisting() { if(!bound) { bound=bindService(new Intent(this,HostService.class),connection,0);if(!bound)try{new LocalAudioMute(this).restore();}catch(RuntimeException ignored){} } }
    private void query() { send(HostService.STATUS,null); }
    private void send(int what,Bundle data) {
        if(remote==null) return;
        try { Message m=Message.obtain(null,what); m.replyTo=response; if(data!=null)m.setData(data); remote.send(m); }
        catch(RemoteException e){remote=null; running(false);}
    }
    private void running(boolean yes) { rescan.setEnabled(yes); start.setEnabled(!yes); stop.setEnabled(yes);start.setVisibility(yes?View.GONE:View.VISIBLE);stop.setVisibility(yes?View.VISIBLE:View.GONE); name.setEnabled(!yes); hevc.setEnabled(!yes); audio.setEnabled(!yes && !root.isChecked());muteLocal.setEnabled(!yes && audio.isChecked() && !root.isChecked()); root.setEnabled(!yes);refreshControlOptions(); if(!yes)pinBox.setVisibility(View.GONE); }
    @Override protected void onResume(){super.onResume();resumed=true;refreshControlOptions();main.post(poll);}
    @Override protected void onPause(){resumed=false;main.removeCallbacks(poll);if(bound){unbindService(connection);bound=false;remote=null;}super.onPause();}
    private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
    private TextView text(String value,int sp,int color){TextView v=new TextView(this);v.setText(value);v.setTextSize(sp);v.setTextColor(color);v.setPadding(0,dp(5),0,dp(5));return v;}
    private LinearLayout card(LinearLayout parent){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);v.setPadding(dp(20),dp(16),dp(20),dp(16));GradientDrawable bg=new GradientDrawable();bg.setColor(Color.WHITE);bg.setCornerRadius(dp(24));bg.setStroke(dp(1),0xffecf0f5);v.setBackground(bg);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(14);parent.addView(v,p);return v;}
    private Button button(String label,int color){Button v=new Button(this);v.setText(label);v.setTextSize(15);v.setTextColor(Color.WHITE);v.setAllCaps(false);GradientDrawable bg=new GradientDrawable();bg.setColor(color);bg.setCornerRadius(dp(16));v.setBackground(bg);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(54));p.topMargin=dp(12);v.setLayoutParams(p);return v;}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
