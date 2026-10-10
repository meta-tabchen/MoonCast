package com.mooncast.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import com.mooncast.tv.net.ReceiverMediaSource;
import com.mooncast.tv.airplay.AirPlayEngine;
import com.mooncast.tv.net.DlnaReceiver;
import com.mooncast.tv.net.LanAccess;
import com.mooncast.tv.net.PlaybackTarget;
import java.net.Inet4Address;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Foreground-only TV receiver: leaving the Activity revokes discovery, sessions and audio. */
@androidx.media3.common.util.UnstableApi
public final class TvActivity extends Activity implements PlaybackTarget {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService networkWorker=Executors.newSingleThreadExecutor();
    private FrameLayout root;
    private ScrollView panel;
    private TextView status,details,pinView,runtimeView;
    private Button startButton;
    private TextureView mirrorTexture,mediaTexture;
    private VideoFraming mirrorFraming,mediaFraming;
    private Surface mirrorSurface;
    private ExoPlayer player;
    private AirPlayEngine airplay;
    private DlnaReceiver dlna;
    private ConnectivityManager connectivity;
    private WifiManager.MulticastLock multicast;
    private volatile Snapshot snapshot=Snapshot.empty();
    private volatile boolean requested,foreground;
    private boolean playingMirror,mediaError,destroyed;
    private volatile int generation;
    private int mediaEpoch,volume=100,quality;
    private boolean preparingMedia,wantPlay,airplaySuspended,mediaFromAirplay;
    private boolean muted;
    private long pendingSeek;
    private volatile int allowedMediaProxyPort=-1;
    private String uri="",metadata="",receiverName,uuid;
    private String activeNetwork="";
    private final Runnable tick=new Runnable(){@Override public void run(){
        if(destroyed)return;
        refreshSnapshot();
        if(airplay!=null){String transport=airplay.isRunning()?getString(R.string.airplay_active):airplaySuspended?getString(R.string.airplay_paused):getString(R.string.airplay_off);runtimeView.setText(getString(R.string.runtime_stats,transport,airplay.getDecodedFrames(),airplay.getDroppedFrames()));}
        if(playingMirror)mirrorFraming.sample();else if(player!=null&&player.getPlaybackState()==Player.STATE_READY)mediaFraming.sample();
        if(airplay!=null&&player!=null)airplay.updatePlaybackInfo(player.getCurrentPosition()/1000f,Math.max(0,player.getDuration())/1000f,player.isPlaying()?1:0,player.getPlaybackState()==Player.STATE_READY);
        main.postDelayed(this,500);
    }};
    private final ConnectivityManager.NetworkCallback networkCallback=new ConnectivityManager.NetworkCallback(){
        @Override public void onAvailable(Network network){networkChanged();}
        @Override public void onLost(Network network){networkChanged();}
        @Override public void onLinkPropertiesChanged(Network n,LinkProperties p){networkChanged();}
    };
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        String saved=getPreferences(MODE_PRIVATE).getString("receiver_id",null);
        if(saved==null){saved=UUID.randomUUID().toString();getPreferences(MODE_PRIVATE).edit().putString("receiver_id",saved).apply();}
        uuid=saved;receiverName="MoonCast TV "+uuid.substring(0,4).toUpperCase(java.util.Locale.ROOT);
        createUi();
        player=new ExoPlayer.Builder(this).setMediaSourceFactory(new DefaultMediaSourceFactory(ReceiverMediaSource.factory(()->allowedMediaProxyPort))).build();
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true);
        player.setHandleAudioBecomingNoisy(true);player.setVideoTextureView(mediaTexture);
        player.addListener(new Player.Listener(){
            @Override public void onVideoSizeChanged(VideoSize size){mediaFraming.videoSize(Math.round(size.width*size.pixelWidthHeightRatio),size.height);}
            @Override public void onPlaybackStateChanged(int state){refreshSnapshot();if(state==Player.STATE_ENDED&&!mediaFromAirplay&&requested)stopTransport();}
            @Override public void onIsPlayingChanged(boolean p){refreshSnapshot();}
            @Override public void onPlayerError(PlaybackException error){mediaError=true;showStatus(getString(R.string.media_error,error.getErrorCodeName()));showPanel();refreshSnapshot();}
        });
        airplay=new AirPlayEngine(this,new AirPlayEngine.Listener(){
            @Override public void onStatus(String value){if(requested){details.setText(value);if(value.contains("error")||value.contains("failed")||value.contains("unavailable"))showPanel();}}
            @Override public void onVideoSize(int w,int h){mirrorFraming.videoSize(w,h);}
            @Override public void onMirrorStarted(){if(!airplayOwns())return;allowedMediaProxyPort=-1;++mediaEpoch;preparingMedia=false;wantPlay=false;uri="";metadata="";player.stop();player.clearMediaItems();playingMirror=true;mediaTexture.setVisibility(View.INVISIBLE);mirrorTexture.setVisibility(View.VISIBLE);mirrorFraming.reset();details.setText(R.string.source_mirror);hidePanel();}
            @Override public void onMirrorStopped(){if(!airplayOwns())return;playingMirror=false;if(uri.isEmpty()){showPanel();details.setText(R.string.instructions);}}
            @Override public void onPin(String pin){pinView.setText("AirPlay PIN: "+pin);pinView.setVisibility(View.VISIBLE);showPanel();}
            @Override public void onVideoPlay(String url,float position){if(!airplayOwns())return;try{loadMedia(url,"",true);seekTo((long)(position*1000));play();}catch(IllegalArgumentException e){showStatus(getString(R.string.media_error,"Unsupported media URL"));showPanel();}}
            @Override public void onAudioStarted(){if(!airplayOwns())return;allowedMediaProxyPort=-1;++mediaEpoch;preparingMedia=false;wantPlay=false;player.stop();player.clearMediaItems();uri="";metadata="";playingMirror=false;details.setText("AirPlay audio");showPanel();}
            @Override public void onVideoScrub(float position){if(!airplayOwns())return;seekTo((long)(position*1000));}
            @Override public void onVideoRate(float rate){if(!airplayOwns())return;if(rate==0)pause();else play();}
            @Override public void onVideoStop(){if(airplayOwns())stop();}
        });
        mirrorTexture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener(){
            @Override public void onSurfaceTextureAvailable(SurfaceTexture t,int w,int h){mirrorSurface=new Surface(t);airplay.setSurface(mirrorSurface);mirrorFraming.apply();}
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture t,int w,int h){mirrorFraming.apply();}
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture t){airplay.clearSurface();if(mirrorSurface!=null){mirrorSurface.release();mirrorSurface=null;}return true;}
            @Override public void onSurfaceTextureUpdated(SurfaceTexture t){}
        });
        connectivity=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        connectivity.registerDefaultNetworkCallback(networkCallback);
        main.post(tick);
    }
    private void createUi(){
        root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);setContentView(root);
        mirrorTexture=new TextureView(this);mediaTexture=new TextureView(this);
        root.addView(mirrorTexture,new FrameLayout.LayoutParams(-1,-1));root.addView(mediaTexture,new FrameLayout.LayoutParams(-1,-1));
        mediaTexture.setVisibility(View.INVISIBLE);
        mirrorFraming=new VideoFraming(mirrorTexture);mediaFraming=new VideoFraming(mediaTexture);
        root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{mirrorFraming.apply();mediaFraming.apply();});
        panel=new ScrollView(this);panel.setFillViewport(true);panel.setBackgroundColor(Color.argb(244,11,18,32));root.addView(panel,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(44),dp(24),dp(44),dp(24));panel.addView(content);
        TextView eyebrow=text(getString(R.string.preview),13,0xff7ee2c6);content.addView(eyebrow);
        TextView heading=text(getString(R.string.title),34,Color.WHITE);heading.setTypeface(null,Typeface.BOLD);content.addView(heading);
        content.addView(text(getString(R.string.subtitle),18,0xffbdc9dd));
        status=text(getString(R.string.waiting),22,0xff7ee2c6);status.setPadding(0,dp(16),0,dp(4));content.addView(status);
        content.addView(text(getString(R.string.name,receiverName),16,Color.WHITE));
        content.addView(text(getString(R.string.protocols),16,0xffbdc9dd));
        pinView=text("",26,0xffffd98c);pinView.setVisibility(View.GONE);content.addView(pinView);
        details=text(getString(R.string.instructions),16,Color.WHITE);details.setPadding(0,dp(12),0,dp(12));content.addView(details);
        runtimeView=text("",13,0xffa0afc8);content.addView(runtimeView);
        LinearLayout row=row(content);startButton=button(row,R.string.start,()->{if(requested)stopReceiver();else startReceiver();});
        button(row,R.string.frame,this::framingDialog);button(row,R.string.quality,this::qualityDialog);button(row,R.string.about,this::about);
        LinearLayout controls=row(content);button(controls,R.string.pause,()->{if(player.isPlaying())pause();else play();});button(controls,R.string.stop_media,this::stop);button(controls,R.string.hide,this::hidePanel);
        content.addView(text(getString(R.string.controls_hint),13,0xffa0afc8));
        TextView limits=text(getString(R.string.limits),13,0xffa0afc8);limits.setPadding(0,dp(10),0,dp(8));content.addView(limits);
        content.addView(text(getString(R.string.trusted),12,0xffe6bb7e));
        startButton.requestFocus();
    }
    private TextView text(String value,int sp,int color){TextView view=new TextView(this);view.setText(value);view.setTextSize(sp);view.setTextColor(color);return view;}
    private LinearLayout row(LinearLayout parent){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setPadding(0,dp(6),0,dp(6));parent.addView(row);return row;}
    private Button button(LinearLayout row,int label,Runnable action){Button b=new Button(this);b.setText(label);b.setTextSize(14);b.setTextColor(Color.WHITE);b.setAllCaps(false);b.setMinHeight(dp(48));b.setPadding(dp(14),dp(8),dp(14),dp(8));b.setFocusable(true);b.setOnClickListener(v->action.run());b.setOnFocusChangeListener((v,focus)->{GradientDrawable bg=new GradientDrawable();bg.setCornerRadius(dp(9));bg.setColor(focus?0xff1e6a60:0xff202c42);bg.setStroke(dp(2),focus?0xff7ee2c6:0xff34425b);v.setBackground(bg);});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(52));p.setMargins(0,0,dp(12),0);row.addView(b,p);return b;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private void showStatus(String value){status.setText(value);}
    private void showPanel(){panel.setVisibility(View.VISIBLE);startButton.requestFocus();}
    private void hidePanel(){if(!requested)return;panel.setVisibility(View.GONE);root.setFocusableInTouchMode(true);root.requestFocus();}
    private void about(){new AlertDialog.Builder(this).setTitle(R.string.about).setMessage(R.string.about_body).setPositiveButton(R.string.close,null).show();}
    private void framingDialog(){String[] choices={getString(R.string.fit),getString(R.string.fill),getString(R.string.cinema),getString(R.string.manual),getString(R.string.rescan)};new AlertDialog.Builder(this).setTitle(R.string.frame).setItems(choices,(d,which)->{if(which==4){mirrorFraming.reset();mediaFraming.reset();return;}mirrorFraming.mode(which);mediaFraming.mode(which);if(which==3)manualDialog();else hidePanel();}).show();}
    private void manualDialog(){LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(24),dp(10),dp(24),dp(10));TextView x=text("",18,Color.WHITE),y=text("",18,Color.WHITE);content.addView(x);LinearLayout xr=row(content);Runnable update=()->{x.setText(getString(R.string.crop_horizontal,mirrorFraming.horizontal()));y.setText(getString(R.string.crop_vertical,mirrorFraming.vertical()));};manualButton(xr,"−",()->adjustCrop(-1,0,update));manualButton(xr,"+",()->adjustCrop(1,0,update));content.addView(y);LinearLayout yr=row(content);manualButton(yr,"−",()->adjustCrop(0,-1,update));manualButton(yr,"+",()->adjustCrop(0,1,update));update.run();new AlertDialog.Builder(this).setTitle(R.string.manual).setView(content).setPositiveButton(R.string.close,(d,w)->hidePanel()).show();}
    private void manualButton(LinearLayout row,String title,Runnable action){Button b=button(row,R.string.close,action);b.setText(title);}
    private void adjustCrop(int dx,int dy,Runnable update){int x=mirrorFraming.horizontal()+dx,y=mirrorFraming.vertical()+dy;mirrorFraming.manual(x,y);mediaFraming.manual(x,y);update.run();}
    private void qualityDialog(){new AlertDialog.Builder(this).setTitle(R.string.quality).setSingleChoiceItems(new String[]{getString(R.string.quality_auto),getString(R.string.quality_1080),getString(R.string.quality_720)},quality,(d,which)->{quality=which;player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setMaxVideoSize(which==0?Integer.MAX_VALUE:which==1?1920:1280,which==0?Integer.MAX_VALUE:which==1?1080:720).build());d.dismiss();new AlertDialog.Builder(this).setMessage(R.string.quality_note).setPositiveButton(R.string.close,null).show();}).setNegativeButton(R.string.cancel,null).show();}
    private record Lan(Inet4Address address,int prefix,String key){}
    private Lan findLan(){Network network=connectivity.getActiveNetwork();if(network==null)return null;NetworkCapabilities caps=connectivity.getNetworkCapabilities(network);if(caps==null||caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)||(!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)&&!caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)))return null;LinkProperties props=connectivity.getLinkProperties(network);if(props==null)return null;for(LinkAddress a:props.getLinkAddresses())if(a.getAddress() instanceof Inet4Address ip&&!ip.isLoopbackAddress()&&!ip.isLinkLocalAddress())return new Lan(ip,a.getPrefixLength(),network+":"+ip.getHostAddress());return null;}
    private void networkChanged(){main.postDelayed(()->{if(!requested||!foreground)return;Lan lan=findLan();String key=lan==null?"":lan.key();if(!key.equals(activeNetwork)){showStatus(getString(R.string.network_change));startReceiver();}},750);}
    private void startReceiver(){
        stopMediaOnly();airplaySuspended=false;
        requested=true;int epoch=++generation;Lan lan=findLan();activeNetwork=lan==null?"":lan.key();showStatus(getString(R.string.starting));startButton.setText(R.string.stop);pinView.setVisibility(View.GONE);
        networkWorker.execute(()->{
            closeServers();if(epoch!=generation||!requested)return;
            if(lan==null){main.post(()->{if(epoch==generation)showStatus(getString(R.string.network_missing));});return;}
            try{
                WifiManager wifi=(WifiManager)getApplicationContext().getSystemService(Context.WIFI_SERVICE);if(wifi!=null){multicast=wifi.createMulticastLock("mooncast-tv-discovery");multicast.setReferenceCounted(false);multicast.acquire();}
                dlna=new DlnaReceiver(sessionTarget(epoch),receiverName,uuid,new DlnaReceiver.Listener(){public void onStatus(String value){}public void onError(String value){main.post(()->{if(epoch==generation)details.setText(value);});}});
                dlna.start(lan.address(),lan.prefix());
                String airplayStatus="";try{airplay.start(receiverName,1920,1080,60);}catch(Exception error){airplayStatus="AirPlay unavailable: "+error.getClass().getSimpleName();}
                if(epoch!=generation||!requested){closeServers();return;}
                String result=airplayStatus;main.post(()->{if(epoch!=generation)return;showStatus(getString(R.string.ready,lan.address().getHostAddress()));details.setText(result.isEmpty()?getString(R.string.instructions):result+"\nDLNA ready");});
            }catch(Exception error){closeServers();main.post(()->{if(epoch==generation){requested=false;startButton.setText(R.string.start);showStatus(error.getClass().getSimpleName()+": receiver unavailable");}});}
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
    private void stopReceiver(){requested=false;++generation;activeNetwork="";stop();pinView.setVisibility(View.GONE);startButton.setText(R.string.start);showStatus(getString(R.string.waiting));networkWorker.execute(this::closeServers);showPanel();}
    private void closeServers(){if(dlna!=null){dlna.stop();dlna=null;}if(airplay!=null)airplay.stop();if(multicast!=null){try{if(multicast.isHeld())multicast.release();}catch(RuntimeException ignored){}multicast=null;}}
    @Override protected void onStart(){super.onStart();foreground=true;}
    @Override protected void onStop(){foreground=false;stopReceiver();super.onStop();}
    @Override protected void onDestroy(){destroyed=true;main.removeCallbacksAndMessages(null);connectivity.unregisterNetworkCallback(networkCallback);networkWorker.execute(this::closeServers);networkWorker.shutdown();player.release();mirrorFraming.release();mediaFraming.release();super.onDestroy();}
    @Override public void onBackPressed(){if(panel.getVisibility()!=View.VISIBLE){showPanel();return;}new AlertDialog.Builder(this).setTitle(R.string.exit_title).setPositiveButton(R.string.exit_yes,(d,w)->finish()).setNegativeButton(R.string.cancel,null).show();}
    @Override public boolean onKeyDown(int key,KeyEvent event){if(key==KeyEvent.KEYCODE_MENU||(key==KeyEvent.KEYCODE_DPAD_CENTER&&panel.getVisibility()!=View.VISIBLE)){showPanel();return true;}if(key==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE){if(player.isPlaying())pause();else play();return true;}if(key==KeyEvent.KEYCODE_MEDIA_PLAY){play();return true;}if(key==KeyEvent.KEYCODE_MEDIA_PAUSE){pause();return true;}if(key==KeyEvent.KEYCODE_MEDIA_STOP){stop();return true;}if(key==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD){seekTo(player.getCurrentPosition()+10000);return true;}if(key==KeyEvent.KEYCODE_MEDIA_REWIND){seekTo(player.getCurrentPosition()-10000);return true;}return super.onKeyDown(key,event);}
    private boolean airplayOwns(){return requested&&foreground&&!destroyed&&!airplaySuspended;}
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
            allowedMediaProxyPort=proxyPort;
            int epoch=++mediaEpoch;mediaFromAirplay=fromAirplay;uri=value;metadata=meta;mediaError=false;playingMirror=false;preparingMedia=true;wantPlay=false;pendingSeek=0;player.stop();
            mirrorTexture.setVisibility(View.INVISIBLE);mediaTexture.setVisibility(View.VISIBLE);mediaFraming.reset();
            Runnable prepare=()->{if(destroyed||!requested||epoch!=mediaEpoch)return;preparingMedia=false;player.setMediaItem(MediaItem.fromUri(value));player.seekTo(pendingSeek);player.prepare();if(wantPlay)player.play();details.setText(R.string.source_media);refreshSnapshot();};
            if(fromAirplay){prepare.run();}else{airplaySuspended=true;networkWorker.execute(()->{airplay.stop();main.post(prepare);});}
        });
    }
    @Override public void play(){onPlayer(()->{if(!uri.isEmpty()){if(!mediaFromAirplay&&!airplaySuspended)loadMedia(uri,metadata,false);wantPlay=true;if(!preparingMedia){if(player.getPlaybackState()==Player.STATE_IDLE)player.prepare();player.play();}hidePanel();}});}
    @Override public void pause(){onPlayer(()->{wantPlay=false;player.pause();});}
    private void restartAirplay(){
        if(!requested||!foreground)return;int epoch=generation;
        networkWorker.execute(()->{airplay.stop();if(epoch!=generation||!requested)return;try{airplay.start(receiverName,1920,1080,60);}catch(Exception e){main.post(()->{if(epoch==generation)details.setText("AirPlay restart failed");});}});
    }
    private void stopTransport(){onPlayer(()->{++mediaEpoch;preparingMedia=false;wantPlay=false;player.stop();playingMirror=false;showPanel();if(airplaySuspended){airplaySuspended=false;restartAirplay();}});}
    private void stopMediaOnly(){
        if(player==null)return;allowedMediaProxyPort=-1;++mediaEpoch;preparingMedia=false;wantPlay=false;player.stop();player.clearMediaItems();uri="";metadata="";mediaError=false;playingMirror=false;mediaTexture.setVisibility(View.INVISIBLE);mirrorTexture.setVisibility(View.VISIBLE);mediaFraming.reset();mirrorFraming.reset();showPanel();refreshSnapshot();
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
