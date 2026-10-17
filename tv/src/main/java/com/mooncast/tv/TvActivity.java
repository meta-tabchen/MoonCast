package com.mooncast.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import com.mooncast.tv.media.CalibratedRenderersFactory;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import com.mooncast.tv.net.ReceiverMediaSource;
import com.mooncast.tv.airplay.AirPlayEngine;
import com.mooncast.tv.net.DlnaReceiver;
import com.mooncast.tv.net.LanAccess;
import com.mooncast.tv.net.PlaybackTarget;
import java.net.Inet4Address;
import com.mooncast.tv.gamestream.GameStreamController;
import com.mooncast.tv.gamestream.GameStreamHosts;
import com.mooncast.tv.gamestream.GameStreamSurfaces;
import com.mooncast.tv.gamestream.hosts.HostRecord;
import com.mooncast.tv.gamestream.ui.GameStreamDialog;
import com.mooncast.receiver.StreamSpec;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Foreground-only TV receiver: leaving the Activity revokes discovery, sessions and audio. */
@androidx.media3.common.util.UnstableApi
public final class TvActivity extends Activity implements PlaybackTarget {
    private static final Handler CLEANUP_MAIN=new Handler(Looper.getMainLooper());
    private static final com.mooncast.tv.media.MediaStopRegistry MEDIA_STOPS=new com.mooncast.tv.media.MediaStopRegistry();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService networkWorker=Executors.newSingleThreadExecutor();
    private final ExecutorService statsWorker=Executors.newSingleThreadExecutor();
    private final java.util.concurrent.atomic.AtomicBoolean statsPending=new java.util.concurrent.atomic.AtomicBoolean();
    private volatile AirPlayEngine.RuntimeStats lastAirStats;
    private FrameLayout root;
    private com.mooncast.tv.apk.ApkReceiveController apkReceiver;
    private View blackout;
    private CropEditorOverlay cropEditor;
    private SharedPreferences preferences;
    private boolean mirrorHasFrame,mediaHasFrame,identityResetting,autoStartAfterReset;
    private volatile boolean dlnaOwnsAudio;
    private CalibratedRenderersFactory mediaRenderers;
    private String mediaDecoder="";
    private Player.Listener mediaListener;
    private TvHomeView panel;
    private boolean receiverReady,airplayProblem,dlnaProblem;
    private TextView status,details,runtimeView;
    private TextureView mirrorTexture,mediaTexture;
    private VideoFraming mirrorFraming,mediaFraming;
    private volatile Surface mirrorSurface;
    private TextureView gameTexture;
    private VideoFraming gameFraming;
    private Surface gameSurface;
    private GameStreamController game;
    private GameStreamHosts gameHosts;
    private volatile boolean gameOwned;
    private boolean gameHasFrame,gameConnecting;
    private int gameEpoch;
    private Runnable gameSurfaceReady;
    private volatile CompletableFuture<Void> gameStopped=GameStreamController.globalReleaseBarrier();
    private volatile CompletableFuture<Void> mediaStopped=MEDIA_STOPS.snapshot();
    private ExoPlayer player;
    private AirPlayEngine airplay;
    private DlnaReceiver dlna;
    private ConnectivityManager connectivity;
    private WifiManager.MulticastLock multicast;
    private volatile Snapshot snapshot=Snapshot.empty();
    private volatile boolean requested,foreground;
    private boolean playingMirror,mediaError;
    private volatile boolean destroyed;
    private volatile int generation;
    private int mediaEpoch,volume=100,quality;
    private boolean preparingMedia,wantPlay,mediaFromAirplay;
    private volatile boolean airplaySuspended;
    private boolean muted;
    private long pendingSeek;
    private volatile int allowedMediaProxyPort=-1;
    private String uri="",metadata="",receiverName,uuid;
    private String gameRequested="";
    private String activeNetwork="";
    private final Runnable tick=new Runnable(){@Override public void run(){
        if(destroyed)return;
        refreshSnapshot();
        if(airplayOwns()&&statsPending.compareAndSet(false,true))statsWorker.execute(()->{try{lastAirStats=airplay.getRuntimeStats();}finally{statsPending.set(false);}});
        updateRuntime();
        if(cropEditor!=null)cropEditor.refresh();
        if(gameOwned&&gameHasFrame)gameFraming.sample();else if(playingMirror&&mirrorHasFrame)mirrorFraming.sample();else if(mediaHasFrame&&player!=null&&player.getPlaybackState()==Player.STATE_READY)mediaFraming.sample();
        if(airplay!=null&&player!=null&&airplayOwns()&&mediaFromAirplay&&!uri.isEmpty())airplay.updatePlaybackInfo(player.getCurrentPosition()/1000f,Math.max(0,player.getDuration())/1000f,player.isPlaying()?1:0,player.getPlaybackState()==Player.STATE_READY);
        main.postDelayed(this,500);
    }};
    private final ConnectivityManager.NetworkCallback networkCallback=new ConnectivityManager.NetworkCallback(){
        @Override public void onAvailable(Network network){networkChanged();}
        @Override public void onLost(Network network){networkChanged();}
        @Override public void onLinkPropertiesChanged(Network n,LinkProperties p){networkChanged();}
    };
    @Override protected void attachBaseContext(Context base){super.attachBaseContext(com.mooncast.locale.AppLocale.wrap(base));}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);com.mooncast.tv.airplay.ReceiverDiagnostics.initialize(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        preferences=getSharedPreferences("receiver-settings",MODE_PRIVATE);
        preferences.edit().remove("airplay.pin").apply();
        quality=preferences.getInt("media.quality",0);
        String saved=getPreferences(MODE_PRIVATE).getString("receiver_id",null);
        if(saved==null){saved=UUID.randomUUID().toString();getPreferences(MODE_PRIVATE).edit().putString("receiver_id",saved).apply();}
        uuid=saved;receiverName=com.mooncast.locale.DeviceName.get(this);
        apkReceiver=new com.mooncast.tv.apk.ApkReceiveController(this);
        apkReceiver.restoreState(state);
        createUi();
        createMediaPlayer();
        airplay=new AirPlayEngine(this,new AirPlayEngine.Listener(){
            @Override public void onStatus(AirPlayEngine.Status state,String value){if(airplayOwns()){details.setText(value);if(state==AirPlayEngine.Status.READY){showProtocolStatus();}if(state==AirPlayEngine.Status.PROBLEM){airplayProblem=true;showProtocolStatus();showPanel();}}}
            @Override public void onVideoSize(int w,int h){mirrorFraming.videoSize(w,h);}
            @Override public void onMirrorStarted(){if(!airplayOwns())return;mediaPresentationEnabled=false;allowedMediaProxyPort=-1;dlnaOwnsAudio=false;cancelCropEdit();++mediaEpoch;preparingMedia=false;wantPlay=false;uri="";metadata="";player.stop();player.clearMediaItems();playingMirror=true;mirrorHasFrame=false;blackout.setVisibility(View.VISIBLE);mediaTexture.setVisibility(View.INVISIBLE);mirrorTexture.setVisibility(View.VISIBLE);mirrorFraming.sourceProfile("airplay_mirror");details.setText(R.string.source_mirror);hidePanel();}
            @Override public void onMirrorStopped(){if(!airplayOwns()||mediaPresentationEnabled&&mediaFromAirplay&&!uri.isEmpty())return;cancelCropEdit();playingMirror=false;mirrorHasFrame=false;blackout.setVisibility(View.VISIBLE);if(uri.isEmpty()){showPanel();details.setText(R.string.instructions);}}

            @Override public void onVideoPlay(String url,float position){if(!airplayOwns())return;try{loadMedia(url,"",true);seekTo((long)(position*1000));play();}catch(IllegalArgumentException e){showStatus(getString(R.string.media_error,getString(R.string.tv_unsupported_media_url)));showPanel();}}
            @Override public void onAudioStarted(){if(!airplayOwns())return;cancelCropEdit();mediaHasFrame=false;mediaPresentationEnabled=false;allowedMediaProxyPort=-1;++mediaEpoch;preparingMedia=false;wantPlay=false;player.stop();player.clearMediaItems();uri="";metadata="";playingMirror=false;mirrorHasFrame=false;blackout.setVisibility(View.VISIBLE);details.setText(R.string.audio_session);showPanel();}
            @Override public void onVideoScrub(float position){if(!airplayOwns())return;seekTo((long)(position*1000));}
            @Override public void onVideoRate(float rate){if(!airplayOwns())return;if(rate==0)pause();else play();}
            @Override public void onVideoStop(){if(airplayOwns())stop();}
            @Override public void onSessionCleared(String reason){if(destroyed||gameOwned||dlnaOwnsAudio||mediaPresentationEnabled&&mediaFromAirplay&&!uri.isEmpty())return;mirrorHasFrame=false;playingMirror=false;blackout.setVisibility(View.VISIBLE);cancelCropEdit();}
            @Override public void onRecoveryRequired(String reason){if(!airplayOwns()||identityResetting)return;stopMediaOnly();showStatus(getString(R.string.recovery_ready));details.setText(reason);restartAirplay();}

        });
        mirrorTexture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener(){
            @Override public void onSurfaceTextureAvailable(SurfaceTexture t,int w,int h){Surface created=new Surface(t);mirrorSurface=created;com.mooncast.tv.airplay.AirPlaySurfaces.attach(airplay,created,()->mirrorSurface==created&&!destroyed);mirrorFraming.apply();}
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture t,int w,int h){mirrorFraming.apply();}
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture t){Surface old=mirrorSurface;mirrorSurface=null;com.mooncast.tv.airplay.AirPlaySurfaces.retire(airplay,old,t);return false;}
            @Override public void onSurfaceTextureUpdated(SurfaceTexture t){if(playingMirror&&requested&&!gameOwned&&!dlnaOwnsAudio){if(!mirrorHasFrame)ReceiverDiagnostics.event("texture-first-frame",0);mirrorHasFrame=true;blackout.setVisibility(View.GONE);}}
        });
        configureAirplay();
        createGameStream();
        connectivity=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        connectivity.registerDefaultNetworkCallback(networkCallback);
        main.post(tick);
    }
    private void createUi(){
        root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);setContentView(root);
        mirrorTexture=new TextureView(this);mediaTexture=new TextureView(this);gameTexture=new TextureView(this);
        root.addView(mirrorTexture,new FrameLayout.LayoutParams(-1,-1));root.addView(mediaTexture,new FrameLayout.LayoutParams(-1,-1));
        mediaTexture.setVisibility(View.INVISIBLE);root.addView(gameTexture,new FrameLayout.LayoutParams(-1,-1));gameTexture.setVisibility(View.INVISIBLE);gameFraming=new VideoFraming(gameTexture,preferences,"gamestream");
        mirrorFraming=new VideoFraming(mirrorTexture,preferences,"airplay_mirror");mediaFraming=new VideoFraming(mediaTexture,preferences,"dlna_media");
        blackout=new View(this);blackout.setBackgroundColor(Color.BLACK);root.addView(blackout,new FrameLayout.LayoutParams(-1,-1));
        root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{mirrorFraming.apply();mediaFraming.apply();gameFraming.apply();});
        status=new TextView(this);details=new TextView(this);runtimeView=new TextView(this);
        details.setText(R.string.instructions);
        panel=new TvHomeView(this,new TvHomeView.Listener(){
            public void onFraming(){framingDialog();}
            public void onAdvanced(){advancedDialog();}
            public void onReceiveApk(){Lan lan=findLan();apkReceiver.show(lan==null?null:lan.address(),lan==null?0:lan.prefix(),lan==null?"":lan.key()+":"+lan.prefix());}
            public void onEndCast(){stop();}
            public void onStopStart(){if(requested)stopReceiver();else startReceiver();}
            public void onHide(){if(hasPlayback())hidePanel();else gameHosts.show();}
        });
        panel.setReceiverName(receiverName);root.addView(panel,new FrameLayout.LayoutParams(-1,-1));
        cropEditor=new CropEditorOverlay(this,applied->{if(requested&&hasPlayback())hidePanel();else showPanel();});root.addView(cropEditor,new FrameLayout.LayoutParams(-1,-1));
        panel.requestPrimaryFocus();
    }
    private void createGameStream(){
        game=new GameStreamController(this,new GameStreamController.Listener(){
            public void onVideoSize(int w,int h){if(gameOwned)gameFraming.videoSize(w,h);}
            public void onState(GameStreamController.State state,String code){
                if(destroyed||!gameOwned)return;
                if(state==GameStreamController.State.ACTIVE){gameConnecting=false;details.setText(R.string.gs_session);hidePanel();}
                else if(state==GameStreamController.State.CONNECTING){details.setText(R.string.gs_connecting);}
                else if(state==GameStreamController.State.FAILED){
                    stopMediaOnly();details.setText(gameStreamFailure(code));showPanel();
                    if(!"RELEASE_PENDING".equals(code)&&requested&&foreground)restartAirplay();
                    else showStatus(getString(R.string.gs_release_pending));
                }
            }
        });
        gameHosts=new GameStreamHosts(this,preferences,()->{Lan lan=findLan();return lan==null?null:new LanAccess(lan.address(),lan.prefix());},this::connectGameStream);
        gameTexture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener(){
            public void onSurfaceTextureAvailable(SurfaceTexture t,int w,int h){gameSurface=new Surface(t);gameFraming.apply();Runnable pending=gameSurfaceReady;gameSurfaceReady=null;if(pending!=null)pending.run();else game.setSurface(gameSurface);}
            public void onSurfaceTextureSizeChanged(SurfaceTexture t,int w,int h){gameFraming.apply();}
            public boolean onSurfaceTextureDestroyed(SurfaceTexture t){Surface old=gameSurface;gameSurface=null;GameStreamSurfaces.retire(old,t,game.video());return false;}
            public void onSurfaceTextureUpdated(SurfaceTexture t){if(gameOwned&&requested&&foreground){gameHasFrame=true;blackout.setVisibility(View.GONE);}}
        });
    }
    private void connectGameStream(HostRecord host,GameStreamDialog.Quality quality){
        if(!requested||!foreground||destroyed){notice(R.string.gs_start_receiver);return;}
        Lan lan=findLan();if(lan==null){notice(R.string.gs_network_required);return;}
        LanAccess access=new LanAccess(lan.address(),lan.prefix());
        try{host.endpoint.requireOnLan(access);}catch(IllegalArgumentException e){notice(R.string.gs_network_required);return;}
        gameRequested=getString(R.string.gs_requested_details,quality.width,quality.height,quality.fps,quality.bitrateKbps);stopMediaOnly();gameOwned=true;gameConnecting=true;airplaySuspended=true;gameHasFrame=false;int token=++gameEpoch;
        gameFraming.sourceProfile("gamestream");mediaTexture.setVisibility(View.INVISIBLE);gameTexture.setVisibility(View.VISIBLE);
        details.setText(R.string.gs_connecting);showPanel();
        networkWorker.execute(()->{airplay.stop();if(!airplayStoppedSafely()||!awaitGameStopped())return;main.post(()->{
            if(destroyed||!requested||!foreground||!gameOwned||token!=gameEpoch)return;
            mirrorTexture.setVisibility(View.INVISIBLE);
            Runnable begin=()->{
                if(destroyed||!requested||!foreground||!gameOwned||token!=gameEpoch)return;
                Lan now=findLan();if(now==null||!now.key().equals(lan.key())){stop();return;}
                try{host.endpoint.requireOnLan(new LanAccess(now.address(),now.prefix()));game.connect(host,access,new StreamSpec(quality.width,quality.height,quality.fps,quality.bitrateKbps),gameSurface);}
                catch(RuntimeException e){stopMediaOnly();details.setText(R.string.gs_connection_failed);restartAirplay();showPanel();}
            };
            if(gameSurface!=null&&gameSurface.isValid())begin.run();else{gameSurfaceReady=begin;main.postDelayed(()->{if(token==gameEpoch&&gameSurfaceReady!=null){gameSurfaceReady=null;stop();details.setText(R.string.gs_surface_failed);}},15000);}
        });});
    }
    private void stopGameStream(){
        ++gameEpoch;gameOwned=false;gameConnecting=false;gameHasFrame=false;gameSurfaceReady=null;
        if(gameTexture!=null)gameTexture.setVisibility(View.INVISIBLE);
        if(game!=null)gameStopped=game.stopAsync();
    }
    /** Network worker only. A failed/hung native release must block replacement audio. */
    private boolean airplayStoppedSafely(){if(!airplay.hasVideoReleaseFailure())return true;main.post(()->{if(!destroyed){receiverReady=false;showStatus(getString(R.string.gs_release_pending));showPanel();}});return false;}
    private boolean awaitGameStopped(){
        try{gameStopped.get(75,TimeUnit.SECONDS);mediaStopped.get(15,TimeUnit.SECONDS);return true;}
        catch(Exception e){main.post(()->{if(!destroyed){receiverReady=false;showStatus(getString(R.string.gs_release_pending));showPanel();}});return false;}
    }
    private boolean isUnifiedApp(){return getPackageName().equals("com.mooncast.unified.preview")||getPackageName().equals("com.mooncast.community");}
    private boolean switchingUnifiedRole;
    private void switchUnifiedRole(){
        TvRolePicker.show(this,choice->{
            switchingUnifiedRole=true;
            stopReceiver();
            startActivity(new android.content.Intent().setClassName(getPackageName(),"com.mooncast.unified.RoleSettingsActivity").putExtra("role_choice",choice).putExtra("require_cleanup",true));
            finish();
        });
    }
    private void switchUnifiedLanguage(){
        com.mooncast.locale.LanguagePicker.show(this,choice->{
            switchingUnifiedRole=true;
            stopReceiver();
            startActivity(new android.content.Intent().setClassName(getPackageName(),"com.mooncast.unified.LanguageSettingsActivity").putExtra("require_cleanup",true).putExtra("language_choice",choice));
            finish();
        });
    }
    private void advancedDialog(){
        boolean unified=isUnifiedApp();
        java.util.ArrayList<String> labels=new java.util.ArrayList<>();
        labels.add(getString(com.mooncast.locale.R.string.device_name_title));labels.add(getString(R.string.theme_title));if(unified){labels.add(getString(R.string.tv_device_mode));labels.add(getString(R.string.tv_language));}
        for(int id:new int[]{R.string.playback_controls,R.string.quality,R.string.recovery_settings,R.string.diagnostics_title,R.string.cast_guide_title,R.string.about,R.string.gs_connect_title})labels.add(getString(id));
        new AlertDialog.Builder(this).setTitle(R.string.advanced_title).setItems(labels.toArray(new String[0]),(d,w)->{
            if(w==0){com.mooncast.locale.DeviceName.show(this,()->{});return;}
            w--;
            if(w==0){TvThemeDialog.show(this,preferences,panel);return;}
            if(unified&&w==1){switchUnifiedRole();return;}
            if(unified&&w==2){switchUnifiedLanguage();return;}
            int choice=w-(unified?3:1);
            if(choice==0)playbackDialog();else if(choice==1)pictureSoundDialog();else if(choice==2)recoveryDialog();else if(choice==3)diagnosticsDialog();else if(choice==4)notice(R.string.cast_guide);else if(choice==5)about();else gameHosts.show();
        }).setNegativeButton(R.string.close,null).show();
    }
    private void playbackDialog(){boolean media=mediaPresentationEnabled&&!uri.isEmpty();String[] choices=media?new String[]{getString(player.isPlaying()?R.string.pause_action:R.string.resume_action),getString(R.string.stop_media)}:new String[]{getString(R.string.stop_media)};new AlertDialog.Builder(this).setTitle(R.string.playback_controls).setItems(choices,(d,w)->{if(media&&w==0){if(player.isPlaying())pause();else play();}else stop();}).setNegativeButton(R.string.close,null).show();}
    private void diagnosticsDialog(){updateRuntime();new AlertDialog.Builder(this).setTitle(R.string.diagnostics_title).setMessage(status.getText()+"\n\n"+details.getText()+"\n\n"+runtimeView.getText()+"\n\n"+com.mooncast.tv.airplay.ReceiverDiagnostics.report()+"\n\n"+getString(R.string.trusted)).setPositiveButton(R.string.close,null).show();}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private void showProtocolStatus(){receiverReady=!airplayProblem&&!dlnaProblem;showStatus(getString(receiverReady?R.string.ready_simple:airplayProblem&&dlnaProblem?R.string.receiver_partial:dlnaProblem?R.string.dlna_partial:R.string.airplay_partial));panel.setHint(receiverReady?"":getString(dlnaProblem?R.string.dlna_partial_hint:R.string.airplay_partial_hint));}
    private void showStatus(String value){status.setText(value);panel.setStatus(value,receiverReady);panel.setReceiving(requested);}
    private void showPanel(){if(cropEditor!=null&&cropEditor.isEditing())return;panel.setVisibility(View.VISIBLE);panel.setPlaybackActive(hasPlayback());panel.requestPrimaryFocus();}
    private boolean hasPlayback(){return gameOwned||playingMirror||(mediaPresentationEnabled&&!uri.isEmpty());}
    private void hidePanel(){if(!requested||!hasPlayback())return;panel.setVisibility(View.GONE);root.setFocusableInTouchMode(true);root.requestFocus();}
    private void about(){
        View returnFocus=getCurrentFocus();
        String message=com.mooncast.locale.AppBuildInfo.describe(this)+"\n\n"+getString(R.string.about_body);
        if(getPackageName().equals("com.mooncast.community"))message+="\n\n"+getString(R.string.tv_community_signing_notice);
        new AlertDialog.Builder(this).setTitle(R.string.about).setMessage(message).setPositiveButton(R.string.close,null)
            .setOnDismissListener(dialog->{if(!destroyed){if(returnFocus!=null&&returnFocus.isAttachedToWindow()&&returnFocus.isShown())returnFocus.requestFocus();else panel.requestPrimaryFocus();}}).show();
    }
    private VideoFraming activeFraming(){return gameOwned?gameFraming:playingMirror||uri.isEmpty()?mirrorFraming:mediaFraming;}
    private void cancelCropEdit(){if(cropEditor!=null&&cropEditor.isEditing())cropEditor.finish(false);}
    private void framingDialog(){
        String[] choices={getString(R.string.fit),getString(R.string.fill),getString(R.string.cinema),getString(R.string.manual),getString(R.string.rescan),getString(R.string.frame_reset)};
        new AlertDialog.Builder(this).setTitle(R.string.frame).setItems(choices,(d,which)->{VideoFraming f=activeFraming();if(which==3){panel.setVisibility(View.GONE);cropEditor.begin(f);return;}if(which==4)f.reset();else if(which==5){f.resetProfile();f.applyEdits();}else f.mode(which);hidePanel();}).setNeutralButton(R.string.about,(d,w)->notice(R.string.framing_profile)).setNegativeButton(R.string.cancel,null).show();
    }
    private void pictureSoundDialog(){new AlertDialog.Builder(this).setTitle(R.string.quality).setItems(new String[]{getString(R.string.picture_settings),getString(R.string.mirror_settings),getString(R.string.media_sound)},(d,w)->{if(w==0)qualityDialog();else if(w==1)mirrorSettingsDialog();else mediaSoundDialog();}).show();}
    private void qualityDialog(){new AlertDialog.Builder(this).setTitle(R.string.picture_settings).setSingleChoiceItems(new String[]{getString(R.string.quality_auto),getString(R.string.quality_1080),getString(R.string.quality_720)},quality,(d,which)->{quality=which;preferences.edit().putInt("media.quality",which).apply();applyMediaQuality();d.dismiss();notice(R.string.quality_note);}).setNegativeButton(R.string.cancel,null).show();}
    private void applyMediaQuality(){player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setMaxVideoSize(quality==0?Integer.MAX_VALUE:quality==1?1920:1280,quality==0?Integer.MAX_VALUE:quality==1?1080:720).build());}
    private void notice(int message){new AlertDialog.Builder(this).setMessage(message).setPositiveButton(R.string.close,null).show();}
    private void mirrorSettingsDialog(){
        new AlertDialog.Builder(this).setTitle(R.string.mirror_settings).setItems(new String[]{getString(R.string.mirror_resolution),getString(R.string.mode_movie)+" / "+getString(R.string.mode_low_latency),getString(R.string.mirror_buffer)},(d,w)->{
            if(w==0)new AlertDialog.Builder(this).setTitle(R.string.mirror_resolution).setSingleChoiceItems(new String[]{getString(R.string.mirror_1080_60),getString(R.string.mirror_1080_30),getString(R.string.mirror_720_60)},preferences.getInt("mirror.quality",0),(a,v)->{a.dismiss();applyReceiverChange(()->preferences.edit().putInt("mirror.quality",v).apply());}).setNegativeButton(R.string.cancel,null).show();
            else if(w==1)new AlertDialog.Builder(this).setTitle(R.string.mirror_settings).setSingleChoiceItems(new String[]{getString(R.string.mode_movie),getString(R.string.mode_low_latency)},preferences.getBoolean("mirror.low_latency",false)?1:0,(a,v)->{a.dismiss();applyReceiverChange(()->preferences.edit().putBoolean("mirror.low_latency",v==1).apply());}).setNegativeButton(R.string.cancel,null).show();
            else {String[] values=new String[11];values[0]=getString(R.string.mirror_buffer_auto);for(int i=1;i<values.length;i++)values[i]=getString(R.string.ms_value,i*50);new AlertDialog.Builder(this).setTitle(R.string.mirror_buffer).setSingleChoiceItems(values,preferences.getInt("mirror.buffer",preferences.getBoolean("mirror.low_latency",false)?0:150)/50,(a,v)->{a.dismiss();applyReceiverChange(()->preferences.edit().putInt("mirror.buffer",v*50).apply());}).setNeutralButton(R.string.about,(a,v)->notice(R.string.mirror_buffer_note)).setNegativeButton(R.string.cancel,null).show();}
        }).show();
    }
    private void applyReceiverChange(Runnable save){Runnable apply=()->{save.run();if(requested)startReceiver();else networkWorker.execute(this::configureAirplay);};if(requested)new AlertDialog.Builder(this).setTitle(R.string.apply_reconnect).setMessage(R.string.apply_reconnect_note).setPositiveButton(R.string.apply,(d,w)->apply.run()).setNegativeButton(R.string.cancel,null).show();else apply.run();}
    private String mediaSettingsKey(){return mediaFromAirplay?"airplay_media":"dlna_media";}
    private void mediaSoundDialog(){
        String key=mediaSettingsKey();new AlertDialog.Builder(this).setTitle(R.string.media_sound).setItems(new String[]{getString(R.string.media_offset),getString(R.string.mode_movie)+" / "+getString(R.string.mode_low_latency)},(d,w)->{
            if(w==0){String[] offsets=new String[21];for(int i=0;i<21;i++)offsets[i]=getString(R.string.ms_value,(i-10)*50);new AlertDialog.Builder(this).setTitle(R.string.media_offset).setSingleChoiceItems(offsets,preferences.getInt(key+".offset",0)/50+10,(a,v)->{preferences.edit().putInt(key+".offset",(v-10)*50).apply();a.dismiss();restartMediaForSettings();}).setNeutralButton(R.string.about,(a,v)->notice(R.string.media_offset_note)).setNegativeButton(R.string.cancel,null).show();}
            else new AlertDialog.Builder(this).setTitle(R.string.media_sound).setSingleChoiceItems(new String[]{getString(R.string.mode_movie),getString(R.string.mode_low_latency)},preferences.getBoolean(key+".low_latency",false)?1:0,(a,v)->{preferences.edit().putBoolean(key+".low_latency",v==1).apply();a.dismiss();restartMediaForSettings();}).setNeutralButton(R.string.about,(a,v)->notice(R.string.buffer_mode_note)).setNegativeButton(R.string.cancel,null).show();
        }).show();
    }
    private void recoveryDialog(){
        new AlertDialog.Builder(this).setTitle(R.string.recovery_settings).setItems(new String[]{getString(R.string.watchdog),getString(R.string.recover_now),getString(R.string.reset_pairing),getString(R.string.peer_limit)},(d,w)->{
            if(w==0){int[] secs={0,15,30,60};String[] labels={getString(R.string.off),getString(R.string.seconds_value,15),getString(R.string.seconds_value,30),getString(R.string.seconds_value,60)};int selected=1;for(int i=0;i<secs.length;i++)if(secs[i]==preferences.getInt("airplay.watchdog",15))selected=i;new AlertDialog.Builder(this).setTitle(R.string.watchdog).setSingleChoiceItems(labels,selected,(a,v)->{preferences.edit().putInt("airplay.watchdog",secs[v]).apply();airplay.setWatchdogTimeoutSeconds(secs[v]);a.dismiss();}).setNeutralButton(R.string.about,(a,v)->notice(R.string.watchdog_note)).setNegativeButton(R.string.cancel,null).show();}
            else if(w==1){stop();}
            else if(w==2)new AlertDialog.Builder(this).setTitle(R.string.reset_pairing).setMessage(R.string.reset_pairing_warning).setPositiveButton(R.string.reset_confirm,(a,v)->resetReceiverIdentity()).setNegativeButton(R.string.cancel,null).show();else notice(R.string.peer_limit);
        }).show();
    }
    private void resetReceiverIdentity(){
        if(identityResetting)return;boolean resume=requested;stopReceiver();identityResetting=true;panel.setControlsEnabled(false);showStatus(getString(R.string.resetting));int epoch=generation;
        networkWorker.execute(()->{boolean success=true;try{airplay.stop();airplay.resetIdentityAfterConfirmation(true);}catch(Exception e){success=false;}boolean done=success;main.post(()->{if(destroyed)return;identityResetting=false;panel.setControlsEnabled(true);if(!done)showStatus(getString(R.string.restore_identity_failed));if(epoch!=generation){if(done&&foreground&&autoStartAfterReset){autoStartAfterReset=false;startReceiver();}return;}showStatus(getString(done?R.string.reset_done:R.string.restore_identity_failed));if(done&&resume&&foreground)startReceiver();});});
    }
    private int mirrorWidth(){return preferences.getInt("mirror.quality",0)==2?1280:1920;}
    private int mirrorHeight(){return preferences.getInt("mirror.quality",0)==2?720:1080;}
    private int mirrorFps(){return preferences.getInt("mirror.quality",0)==1?30:60;}
    private void configureAirplay(){if(airplay==null)return;airplay.requestPinMode(false);airplay.setWatchdogTimeoutSeconds(preferences.getInt("airplay.watchdog",15));airplay.setPlaybackMode(preferences.getBoolean("mirror.low_latency",false)?AirPlayEngine.PlaybackMode.LOW_LATENCY:AirPlayEngine.PlaybackMode.MOVIE);airplay.setAudioBufferMs(preferences.getInt("mirror.buffer",preferences.getBoolean("mirror.low_latency",false)?0:150));}
    private boolean mediaPresentationEnabled;
    private void createMediaPlayer(){
        mediaPresentationEnabled=false;
        if(player!=null){
            ExoPlayer previous=player;if(mediaListener!=null)previous.removeListener(mediaListener);
            CompletableFuture<Void> stopped=mediaRenderers.playbackBarrier().stopAsync(previous);
            mediaStopped=MEDIA_STOPS.record(stopped);
            stopped.whenComplete((ignored,error)->CLEANUP_MAIN.post(previous::release));
        }
        String key=mediaSettingsKey();mediaRenderers=new CalibratedRenderersFactory(this,preferences.getBoolean(key+".low_latency",false),preferences.getInt(key+".offset",0));
        ExoPlayer created=new ExoPlayer.Builder(this,mediaRenderers).setLoadControl(mediaRenderers.createLoadControl()).setMediaSourceFactory(new DefaultMediaSourceFactory(ReceiverMediaSource.factory(()->allowedMediaProxyPort))).build();player=created;
        created.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true);created.setHandleAudioBecomingNoisy(true);created.setVideoTextureView(mediaTexture);created.setVolume(muted?0:volume/100f);applyMediaQuality();mediaDecoder="";
        mediaListener=new Player.Listener(){
            private boolean active(){return !destroyed&&player==created;}
            @Override public void onVideoSizeChanged(VideoSize size){if(active())mediaFraming.videoSize(Math.round(size.width*size.pixelWidthHeightRatio),size.height);}
            @Override public void onRenderedFirstFrame(){if(active()&&requested&&foreground&&!gameOwned&&mediaPresentationEnabled&&!playingMirror&&!uri.isEmpty()){mediaHasFrame=true;blackout.setVisibility(View.GONE);}}
            @Override public void onPlaybackStateChanged(int state){if(!active())return;refreshSnapshot();if(state==Player.STATE_ENDED&&!mediaFromAirplay&&requested)stopTransport();}
            @Override public void onIsPlayingChanged(boolean value){if(active())refreshSnapshot();}
            @Override public void onPlayerError(PlaybackException error){if(!active()||gameOwned||(!mediaPresentationEnabled&&!preparingMedia))return;mediaError=true;showStatus(getString(R.string.tv_diagnostic_code,getString(R.string.tv_media_playback_failed),error.getErrorCodeName()));if(requested)stopTransport();showPanel();refreshSnapshot();}
        };created.addListener(mediaListener);created.addAnalyticsListener(new AnalyticsListener(){@Override public void onVideoDecoderInitialized(EventTime eventTime,String decoderName,long initializedTimestampMs,long initializationDurationMs){if(player==created&&!destroyed)mediaDecoder=decoderName;}});
    }
    private void restartMediaForSettings(){if(uri.isEmpty()||playingMirror){createMediaPlayer();return;}String value=uri,meta=metadata;boolean nativeMedia=mediaFromAirplay,wasPlaying=wantPlay||player.getPlayWhenReady();long position=preparingMedia?pendingSeek:player.getCurrentPosition();loadMedia(value,meta,nativeMedia);seekTo(position);if(wasPlaying)play();}
    private String gameStreamFailure(String code){
        int message=switch(code){
            case "AUDIO_FOCUS_LOST"->R.string.tv_gamestream_audio_focus_lost;
            case "VIDEO_OUTPUT_FAILED"->R.string.tv_gamestream_video_failed;
            case "AUDIO_OUTPUT_FAILED"->R.string.tv_gamestream_audio_failed;
            case "HDR_UNSUPPORTED"->R.string.tv_gamestream_hdr_unsupported;
            case "RELEASE_PENDING"->R.string.gs_release_pending;
            default->code.startsWith("SESSION_ENDED_")?R.string.tv_gamestream_session_ended:R.string.gs_connection_failed;
        };
        return getString(R.string.tv_diagnostic_code,getString(message),code);
    }
    private void updateRuntime(){
        if(airplay==null||player==null)return;AirPlayEngine.RuntimeStats a=lastAirStats;String state=gameOwned?getString(R.string.gs_session):playingMirror?getString(R.string.source_mirror):!uri.isEmpty()?getString(mediaFromAirplay?R.string.airplay_media_session:R.string.dlna_session):a!=null&&a.state==AirPlayEngine.SessionState.AUDIO_ONLY?getString(R.string.audio_session):getString(R.string.idle_session);
        StringBuilder text=new StringBuilder(getString(R.string.current_session,state));
        android.view.Display physical=getWindowManager().getDefaultDisplay();android.view.Display.Mode displayMode=physical.getMode();
        text.append("\n").append(getString(R.string.tv_display_vs_stream,displayMode.getPhysicalWidth(),displayMode.getPhysicalHeight()));
        if(gameOwned)text.append("\n").append(gameRequested);
        if(gameOwned&&game.video()!=null){var g=game.video().getStats();text.append("\n").append(getString(R.string.tv_video_runtime,g.decodedWidth,g.decodedHeight,"H.264 SDR",g.decoderName,g.observedFps,g.droppedFrames)).append("\n").append(getString(R.string.gs_timing_limit));}
        else if(playingMirror&&a!=null)text.append("\n").append(getString(R.string.tv_video_runtime,a.decodedWidth,a.decodedHeight,a.codec,a.decoderName,a.observedFps,a.droppedFrames));
        else if(!uri.isEmpty()){androidx.media3.common.Format f=player.getVideoFormat();text.append("\n").append(getString(R.string.media_runtime,f==null?getString(R.string.unknown_spec):String.valueOf(f.sampleMimeType),f==null?0:f.width,f==null?0:f.height,mediaDecoder.isEmpty()?getString(R.string.unknown_spec):mediaDecoder,getString(player.isPlaying()?R.string.tv_playback_playing:player.getPlaybackState()==Player.STATE_BUFFERING?R.string.tv_playback_buffering:R.string.tv_playback_paused_stopped)));}
        if(a!=null)text.append("\n").append(getString(R.string.mirror_requested,a.requestedWidth,a.requestedHeight,a.requestedFps,a.configuredAudioBufferMs));
        text.append("\n").append(getString(airplay.isRunning()?R.string.airplay_active:airplaySuspended?R.string.airplay_paused:R.string.airplay_off));VideoFraming frame=activeFraming();if(frame.mode()==VideoFraming.CINEMA)text.append(" · ").append(frame.safetyExpanded()?getString(R.string.auto_safety_expanded):frame.locked()?getString(R.string.auto_locked_detail):getString(R.string.auto_progress,frame.stableSamples(),frame.requiredSamples()));runtimeView.setText(text);panel.setPlaybackActive(hasPlayback());panel.setModeLabel(getString(switch(frame.mode()){case VideoFraming.FILL->R.string.fill;case VideoFraming.MANUAL->R.string.manual;case VideoFraming.CINEMA->R.string.cinema;default->R.string.fit;}));
    }
    private record Lan(Inet4Address address,int prefix,String key){}
    private Lan findLan(){Network network=connectivity.getActiveNetwork();if(network==null)return null;NetworkCapabilities caps=connectivity.getNetworkCapabilities(network);if(caps==null||caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)||(!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)&&!caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)))return null;LinkProperties props=connectivity.getLinkProperties(network);if(props==null)return null;for(LinkAddress a:props.getLinkAddresses())if(a.getAddress() instanceof Inet4Address ip&&!ip.isLoopbackAddress()&&!ip.isLinkLocalAddress())return new Lan(ip,a.getPrefixLength(),network+":"+ip.getHostAddress());return null;}
    private void networkChanged(){ReceiverDiagnostics.event("network-changed",0);main.postDelayed(()->{if(apkReceiver!=null){Lan current=findLan();apkReceiver.onNetworkChanged(current==null?"":current.key()+":"+current.prefix());}if(!requested||!foreground)return;Lan lan=findLan();String key=lan==null?"":lan.key();if(!key.equals(activeNetwork)){showStatus(getString(R.string.network_change));startReceiver();}},750);}
    private void startReceiver(){
        com.mooncast.tv.airplay.ReceiverDiagnostics.event("receiver-start",generation);
        if(identityResetting)return;receiverName=com.mooncast.locale.DeviceName.get(this);panel.setReceiverName(receiverName);receiverReady=false;airplayProblem=false;dlnaProblem=false;panel.setHint("");stopMediaOnly();airplaySuspended=true;
        requested=true;int epoch=++generation;Lan lan=findLan();activeNetwork=lan==null?"":lan.key();showStatus(getString(R.string.starting));panel.setReceiving(true);if(panel.getVisibility()==View.VISIBLE)panel.requestPrimaryFocus();
        networkWorker.execute(()->{
            closeServers();if(!airplayStoppedSafely()||!awaitGameStopped()||epoch!=generation||!requested)return;
            if(lan==null){main.post(()->{if(epoch==generation)showStatus(getString(R.string.network_missing));});return;}
            try{
                WifiManager wifi=(WifiManager)getApplicationContext().getSystemService(Context.WIFI_SERVICE);if(wifi!=null){multicast=wifi.createMulticastLock("mooncast-tv-discovery");multicast.setReferenceCounted(false);multicast.acquire();}
                dlna=new DlnaReceiver(sessionTarget(epoch),receiverName,uuid,new DlnaReceiver.Listener(){public void onStatus(String value){}public void onError(DlnaReceiver.Failure failure,String detail){main.post(()->{if(epoch==generation){details.setText(getString(failure==DlnaReceiver.Failure.DISCOVERY_STOPPED?R.string.tv_dlna_discovery_stopped:R.string.tv_dlna_listener_stopped,detail));dlnaProblem=true;showProtocolStatus();}});}});
                dlna.start(lan.address(),lan.prefix());
                String airplayStatus="";try{if(!dlnaOwnsAudio&&!gameOwned){configureAirplay();airplaySuspended=false;airplay.start(receiverName,mirrorWidth(),mirrorHeight(),mirrorFps(),false);}}catch(Exception error){airplayStatus=getString(R.string.tv_airplay_unavailable,error.getClass().getSimpleName());}
                if(epoch!=generation||!requested){closeServers();return;}
                String result=airplayStatus;main.post(()->{if(epoch!=generation)return;airplayProblem|=!result.isEmpty();showProtocolStatus();if(!result.isEmpty())details.setText(result+(dlnaProblem?"":"\n"+getString(R.string.tv_dlna_ready)));else if(!airplayProblem&&!dlnaProblem)details.setText(R.string.instructions);});
            }catch(Exception error){closeServers();main.post(()->{if(epoch==generation){requested=false;receiverReady=false;panel.setReceiving(false);showStatus(getString(R.string.tv_receiver_unavailable,error.getClass().getSimpleName()));}});}
        });
    }
    private PlaybackTarget sessionTarget(int epoch){
        return new PlaybackTarget(){
            private boolean active(){return !destroyed&&requested&&epoch==generation;}
            public void setMedia(String u,String m){if(active())TvActivity.this.setMedia(u,m);}
            public void play(){if(active())TvActivity.this.play();}
            public void pause(){if(active())TvActivity.this.pause();}
            public void stop(){if(active())TvActivity.this.stopTransport();}
            public void seekTo(long p){if(active())TvActivity.this.seekTo(p);}
            public void setVolume(int v){if(active())TvActivity.this.setVolume(v);}
            public void setMuted(boolean v){if(active())TvActivity.this.setMuted(v);}
            public Snapshot snapshot(){return active()?TvActivity.this.snapshot():Snapshot.empty();}
        };
    }
    private void stopReceiver(){
        com.mooncast.tv.airplay.ReceiverDiagnostics.event("receiver-stop",generation);requested=false;receiverReady=false;++generation;activeNetwork="";stop();panel.setReceiving(false);showStatus(getString(R.string.waiting));networkWorker.execute(this::closeServers);showPanel();}
    private void closeServers(){if(dlna!=null){dlna.stop();dlna=null;}if(airplay!=null)airplay.stop();if(multicast!=null){try{if(multicast.isHeld())multicast.release();}catch(RuntimeException ignored){}multicast=null;}}
    @Override protected void onNewIntent(android.content.Intent intent){ReceiverDiagnostics.event("activity-new-intent",0);super.onNewIntent(intent);setIntent(intent);if(foreground&&!requested&&!identityResetting)startReceiver();}
    @Override protected void onActivityResult(int request,int result,android.content.Intent data){super.onActivityResult(request,result,data);apkReceiver.onActivityResult(request,result);}
    @Override protected void onResume(){super.onResume();apkReceiver.onResume();}
    @Override protected void onPause(){apkReceiver.onPause();super.onPause();}
    @Override protected void onSaveInstanceState(Bundle state){apkReceiver.saveState(state);super.onSaveInstanceState(state);}
    @Override protected void onStart(){super.onStart();com.mooncast.tv.airplay.ReceiverDiagnostics.event("activity-start",0);foreground=true;if(identityResetting)autoStartAfterReset=true;else if(!requested)startReceiver();}
    @Override protected void onStop(){com.mooncast.tv.airplay.ReceiverDiagnostics.event("activity-stop",isChangingConfigurations()?1:0);apkReceiver.onStop();foreground=false;autoStartAfterReset=false;stopReceiver();super.onStop();}
    @Override protected void onDestroy(){com.mooncast.tv.airplay.ReceiverDiagnostics.event("activity-destroy",isFinishing()?1:0);apkReceiver.close();destroyed=true;main.removeCallbacksAndMessages(null);connectivity.unregisterNetworkCallback(networkCallback);networkWorker.execute(this::closeServers);networkWorker.shutdown();statsWorker.shutdown();ExoPlayer closingPlayer=player;MEDIA_STOPS.record(mediaRenderers.playbackBarrier().stopAsync(closingPlayer)).whenComplete((ignored,error)->CLEANUP_MAIN.post(closingPlayer::release));mirrorFraming.release();mediaFraming.release();gameFraming.release();gameHosts.close();game.close();
        if(switchingUnifiedRole&&isUnifiedApp())new Thread(()->{
            try{networkWorker.awaitTermination(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
            // This Activity is packaged only in :receiver by unified. Ending that private
            // process also releases a stuck native decoder before the next role starts.
            android.app.ActivityManager am=(android.app.ActivityManager)getSystemService(ACTIVITY_SERVICE);
            if(am!=null&&am.getRunningAppProcesses()!=null)for(android.app.ActivityManager.RunningAppProcessInfo p:am.getRunningAppProcesses())
                if(p.pid==android.os.Process.myPid()&&p.uid==android.os.Process.myUid()&&p.processName.equals(getPackageName()+":receiver"))android.os.Process.killProcess(p.pid);
        },"ReceiverRoleExit").start();super.onDestroy();}
    @Override public void onBackPressed(){if(gameConnecting){stop();return;}if(cropEditor.isEditing()){cropEditor.finish(false);return;}if(panel.getVisibility()!=View.VISIBLE){showPanel();return;}if(hasPlayback()){hidePanel();return;}new AlertDialog.Builder(this).setTitle(R.string.exit_title).setPositiveButton(R.string.exit_yes,(d,w)->finish()).setNegativeButton(R.string.cancel,null).show();}
    @Override public boolean onKeyDown(int key,KeyEvent event){if(cropEditor.isEditing())return cropEditor.onKeyDown(key,event);if(key==KeyEvent.KEYCODE_MENU||(key==KeyEvent.KEYCODE_DPAD_CENTER&&panel.getVisibility()!=View.VISIBLE)){showPanel();return true;}if(key==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE){if(player.isPlaying())pause();else play();return true;}if(key==KeyEvent.KEYCODE_MEDIA_PLAY){play();return true;}if(key==KeyEvent.KEYCODE_MEDIA_PAUSE){pause();return true;}if(key==KeyEvent.KEYCODE_MEDIA_STOP){stop();return true;}if(key==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD){seekTo(player.getCurrentPosition()+10000);return true;}if(key==KeyEvent.KEYCODE_MEDIA_REWIND){seekTo(player.getCurrentPosition()-10000);return true;}return super.onKeyDown(key,event);}
    private boolean airplayOwns(){return requested&&foreground&&!destroyed&&!identityResetting&&!gameOwned&&!dlnaOwnsAudio&&!airplaySuspended;}
    private void onPlayer(Runnable action){
        int expected=generation;
        if(Looper.myLooper()==Looper.getMainLooper()){if(!destroyed&&requested){action.run();refreshSnapshot();}return;}
        java.util.concurrent.CountDownLatch completed=new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean pending=new java.util.concurrent.atomic.AtomicBoolean(true);
        main.post(()->{try{if(pending.getAndSet(false)&&!destroyed&&requested&&expected==generation){action.run();refreshSnapshot();}}finally{completed.countDown();}});
        try{if(!completed.await(2,java.util.concurrent.TimeUnit.SECONDS)){pending.set(false);throw new IllegalArgumentException("Player unavailable");}}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalArgumentException("Player interrupted");}
    }
    @Override public void setMedia(String value,String meta){loadMedia(value,meta,false);}
    private void loadMedia(String value,String meta,boolean fromAirplay){
        if(value.isEmpty()){onPlayer(this::stop);return;}
        boolean trustedProxy=false;
        if(fromAirplay){try{java.net.URI address=java.net.URI.create(value);trustedProxy="http".equals(address.getScheme())&&("localhost".equals(address.getHost())||"127.0.0.1".equals(address.getHost()))&&address.getPort()==airplay.getPort()&&"/master.m3u8".equals(address.getPath())&&address.getRawUserInfo()==null;}catch(RuntimeException ignored){}}
        if(!trustedProxy)LanAccess.validateMediaUri(value);
        final int proxyPort=trustedProxy?airplay.getPort():-1;
        onPlayer(()->{
            stopGameStream();cancelCropEdit();allowedMediaProxyPort=proxyPort;dlnaOwnsAudio=!fromAirplay;mediaFromAirplay=fromAirplay;createMediaPlayer();mediaHasFrame=false;blackout.setVisibility(View.VISIBLE);mediaFraming.sourceProfile(fromAirplay?"airplay_media":"dlna_media");
            int epoch=++mediaEpoch;mediaFromAirplay=fromAirplay;uri=value;metadata=meta;mediaError=false;playingMirror=false;preparingMedia=true;wantPlay=false;pendingSeek=0;player.stop();
            mediaTexture.setVisibility(View.VISIBLE);mediaFraming.reset();
            Runnable prepare=()->{if(destroyed||!requested||epoch!=mediaEpoch)return;preparingMedia=false;mirrorTexture.setVisibility(View.INVISIBLE);mediaPresentationEnabled=true;player.setMediaItem(MediaItem.fromUri(value));player.seekTo(pendingSeek);player.prepare();if(wantPlay){player.play();hidePanel();}details.setText(R.string.source_media);refreshSnapshot();};
            if(fromAirplay){networkWorker.execute(()->{if(awaitGameStopped())main.post(prepare);});}else{airplaySuspended=true;networkWorker.execute(()->{airplay.stop();if(airplayStoppedSafely()&&awaitGameStopped())main.post(prepare);});}
        });
    }
    @Override public void play(){onPlayer(()->{if(!uri.isEmpty()){if((!mediaPresentationEnabled&&!preparingMedia)||(!mediaFromAirplay&&!airplaySuspended))loadMedia(uri,metadata,mediaFromAirplay);wantPlay=true;if(!preparingMedia){if(player.getPlaybackState()==Player.STATE_IDLE)player.prepare();player.play();}hidePanel();}});}
    @Override public void pause(){onPlayer(()->{wantPlay=false;player.pause();});}
    private void restartAirplay(){
        if(!requested||!foreground||identityResetting)return;Lan current=findLan();if(current==null||!current.key().equals(activeNetwork)){startReceiver();return;}int epoch=generation;airplaySuspended=true;airplayProblem=false;receiverReady=false;showStatus(getString(R.string.starting));
        networkWorker.execute(()->{airplay.stop();if(!airplayStoppedSafely()||!awaitGameStopped()||epoch!=generation||!requested||dlnaOwnsAudio||gameOwned)return;try{configureAirplay();airplaySuspended=false;airplay.start(receiverName,mirrorWidth(),mirrorHeight(),mirrorFps(),false);}catch(Exception e){main.post(()->{if(epoch==generation){details.setText(R.string.tv_airplay_restart_failed);airplayProblem=true;showProtocolStatus();}});}});
    }
    private void stopTransport(){onPlayer(()->{if(gameOwned)return;cancelCropEdit();dlnaOwnsAudio=false;mediaHasFrame=false;mediaPresentationEnabled=false;blackout.setVisibility(View.VISIBLE);++mediaEpoch;preparingMedia=false;wantPlay=false;mediaStopped=MEDIA_STOPS.record(mediaRenderers.playbackBarrier().stopAsync(player));playingMirror=false;showPanel();if(airplaySuspended){airplaySuspended=false;restartAirplay();}});}
    private void stopMediaOnly(){
        if(player==null)return;stopGameStream();if(gameHosts!=null)gameHosts.dismiss();cancelCropEdit();dlnaOwnsAudio=false;mirrorHasFrame=false;mediaHasFrame=false;mediaPresentationEnabled=false;blackout.setVisibility(View.VISIBLE);allowedMediaProxyPort=-1;++mediaEpoch;preparingMedia=false;wantPlay=false;mediaStopped=MEDIA_STOPS.record(mediaRenderers.playbackBarrier().stopAsync(player));player.clearMediaItems();uri="";metadata="";mediaError=false;playingMirror=false;mediaTexture.setVisibility(View.INVISIBLE);mirrorTexture.setVisibility(View.VISIBLE);mediaFraming.reset();mirrorFraming.reset();showPanel();refreshSnapshot();
    }
    @Override public void stop(){
        if(Looper.myLooper()!=Looper.getMainLooper()){int expected=generation;main.post(()->{if(!destroyed&&expected==generation)stop();});return;}
        if(destroyed)return;stopMediaOnly();airplaySuspended=false;
        restartAirplay();
    }
    @Override public void seekTo(long value){onPlayer(()->{pendingSeek=Math.max(0,value);if(!preparingMedia)player.seekTo(pendingSeek);});}
    @Override public void setVolume(int value){onPlayer(()->{volume=Math.max(0,Math.min(100,value));player.setVolume(muted?0:volume/100f);});}
    @Override public void setMuted(boolean value){onPlayer(()->{muted=value;player.setVolume(muted?0:volume/100f);});}
    @Override public Snapshot snapshot(){return snapshot;}
    private void refreshSnapshot(){if(player==null)return;String state=uri.isEmpty()?"NO_MEDIA_PRESENT":preparingMedia||player.getPlaybackState()==Player.STATE_BUFFERING?"TRANSITIONING":player.isPlaying()?"PLAYING":player.getPlaybackState()==Player.STATE_READY?"PAUSED_PLAYBACK":"STOPPED";snapshot=new Snapshot(state,uri,metadata,Math.max(0,player.getDuration()),Math.max(0,player.getCurrentPosition()),volume,muted,mediaError);}
}
