package com.mooncast.host;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

/** Session-owned manual controls. No physical-off action occurs at start/reconnect.
 * Overlay is deliberately not FLAG_SECURE: that produces a captured black rectangle.
 * Android does not guarantee excluding application overlays from whole-screen capture. */
final class CastControls implements AutoCloseable {
    static final String ACTION_PAUSE="mooncast.pause", ACTION_PANEL="mooncast.panel", ACTION_RESTORE="mooncast.restore", ACTION_DIM="mooncast.dim";
    interface Session {
        boolean connected();
        boolean pause(boolean value);
        void stop();
    }
    private final Service service;
    private final Session session;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final WindowManager windows;
    private final String channel;
    private final int notificationId;
    private final PanelRecoveryClient recovery;
    private final ScreenDimmer dimmer;
    private PowerManager.WakeLock bright;
    private LinearLayout overlay;
    private WindowManager.LayoutParams position;
    private boolean expanded,closed,resourcesReleased;
    private final ManualControlState state=new ManualControlState();
    private final Runnable monitor=new Runnable(){public void run(){
        if(closed)return;
        if(!session.connected()){if(recovery!=null)recovery.disconnected();dimmer.restore();}
        if(!Settings.canDrawOverlays(service)){removeOverlay();restore(false);}
        main.postDelayed(this,5000);
    }};
    CastControls(Service service,String channel,int notificationId,Session session,boolean root){
        this.service=service;this.channel=channel;this.notificationId=notificationId;this.session=session;
        windows=service.getSystemService(WindowManager.class);
        dimmer=new ScreenDimmer(service,()->{draw();updateNotification();});
        recovery=BuildConfig.PHYSICAL_SCREEN_OFF_VALIDATED?new PanelRecoveryClient(service,root):null;
        bright=service.getSystemService(PowerManager.class).newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK,"MoonCast:ManualBright");
        bright.setReferenceCounted(false);bright.acquire(6*60*60*1000L);
        if(service.getSharedPreferences("cast-controls",0).getBoolean("overlay",false)&&Settings.canDrawOverlays(service))showOverlay();
        updateNotification();main.post(monitor);
    }
    static boolean isAction(String action){return ACTION_DIM.equals(action)||ACTION_PAUSE.equals(action)||ACTION_PANEL.equals(action)||ACTION_RESTORE.equals(action);}
    boolean handle(String action){
        if(ACTION_DIM.equals(action)){requestDim();return true;}
        if(ACTION_PAUSE.equals(action)){togglePause();return true;}
        if(ACTION_PANEL.equals(action)){requestOff();return true;}
        if(ACTION_RESTORE.equals(action)){restore(false);return true;}
        return false;
    }
    void disconnected(){session.pause(false);state.disconnect();if(recovery!=null)recovery.disconnected();restore(false);draw();updateNotification();}
    private void togglePause(){
        if(!session.connected()){toast(R.string.controls_not_connected);return;}
        boolean wanted=!state.paused;
        if(session.pause(wanted)){state.pauseApplied(wanted);restore(false);draw();updateNotification();}
        else toast(R.string.controls_not_connected);
    }
    private void requestOff(){
        if(closed)return;
        if(!BuildConfig.PHYSICAL_SCREEN_OFF_VALIDATED){toast(R.string.controls_panel_validation_pending);return;}
        if(!session.connected()||state.paused){toast(R.string.controls_not_connected);return;}
        if(!Settings.canDrawOverlays(service)){toast(R.string.controls_panel_unavailable);return;}
        expanded=false;draw();if(recovery!=null)recovery.requestOff();
    }
    private void requestDim(){if(closed)return;if(!session.connected()){toast(R.string.controls_not_connected);return;}expanded=false;draw();dimmer.request();}
    private void restore(boolean ignored){dimmer.restore();if(recovery!=null)recovery.restore();}
    private void showOverlay(){
        if(closed||overlay!=null||!Settings.canDrawOverlays(service))return;
        overlay=new LinearLayout(service);overlay.setOrientation(LinearLayout.VERTICAL);overlay.setPadding(dp(6),dp(6),dp(6),dp(6));
        GradientDrawable bg=new GradientDrawable();bg.setColor(0xed18354a);bg.setCornerRadius(dp(20));overlay.setBackground(bg);
        if(position==null){position=new WindowManager.LayoutParams(-2,-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);position.gravity=Gravity.TOP|Gravity.LEFT;position.x=0;position.y=dp(180);}
        try{windows.addView(overlay,position);draw();}catch(RuntimeException denied){overlay=null;toast(R.string.controls_overlay_denied);}
    }
    private void draw(){
        if(overlay==null||closed)return;overlay.removeAllViews();
        Button bubble=button(state.pending()?R.string.controls_cancel_off:expanded?R.string.controls_collapse:R.string.controls_bubble,()->{
            if(state.pending()){restore(false);return;}expanded=!expanded;draw();});
        bubble.setOnTouchListener(new View.OnTouchListener(){
            float x,y;int startX,startY;boolean moved;
            public boolean onTouch(View v,MotionEvent e){
                if(e.getAction()==MotionEvent.ACTION_DOWN){x=e.getRawX();y=e.getRawY();startX=position.x;startY=position.y;moved=false;return false;}
                if(e.getAction()==MotionEvent.ACTION_MOVE){float dx=e.getRawX()-x,dy=e.getRawY()-y;if(Math.abs(dx)+Math.abs(dy)>dp(8))moved=true;if(moved){position.x=Math.max(0,startX+(int)dx);position.y=Math.max(0,startY+(int)dy);try{windows.updateViewLayout(overlay,position);}catch(RuntimeException ignored){}}return moved;}
                if(e.getAction()==MotionEvent.ACTION_UP&&moved){int width=service.getResources().getDisplayMetrics().widthPixels;int height=service.getResources().getDisplayMetrics().heightPixels;position.x=position.x<width/2?0:Math.max(0,width-overlay.getWidth());position.y=Math.min(position.y,Math.max(0,height-overlay.getHeight()));try{windows.updateViewLayout(overlay,position);}catch(RuntimeException ignored){}return true;}return false;
            }
        });
        if(expanded){button(R.string.controls_dim_phone,this::requestDim);button(state.paused?R.string.controls_resume:R.string.controls_pause,this::togglePause);button(R.string.controls_screen_off,this::requestOff);button(R.string.ui_panel_restore,()->restore(false));button(R.string.ui_stop,session::stop);label(R.string.controls_dim_notice);label(R.string.controls_capture_notice);}
    }
    private Button button(int text,Runnable action){Button b=new Button(service);b.setText(text);b.setTextSize(13);b.setAllCaps(false);b.setMinHeight(dp(48));b.setMaxWidth(dp(240));b.setOnClickListener(v->action.run());overlay.addView(b);return b;}
    private void label(int text){TextView v=new TextView(service);v.setText(text);v.setTextColor(Color.WHITE);v.setTextSize(12);v.setMaxWidth(dp(230));v.setPadding(dp(6),dp(6),dp(6),dp(6));overlay.addView(v);}
    private int dp(int value){return Math.round(value*service.getResources().getDisplayMetrics().density);}
    private void toast(int id){Toast.makeText(service,id,Toast.LENGTH_LONG).show();}
    private PendingIntent action(String action,int id){return PendingIntent.getService(service,id,new Intent(service,service.getClass()).setAction(action),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);}
    private void updateNotification(){
        if(closed)return;
        Notification.Builder builder=new Notification.Builder(service,channel).setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(service.getString(state.paused?R.string.controls_paused:R.string.ui_mooncast_is_running))
            .setContentText(service.getString(dimmer.countdown()?R.string.controls_dim_countdown:dimmer.active()?R.string.controls_dim_active:R.string.controls_notification))
            .setContentIntent(PendingIntent.getActivity(service,40,new Intent(service,MainActivity.class),PendingIntent.FLAG_IMMUTABLE)).setOngoing(true)
            .addAction(new Notification.Action.Builder(null,service.getString(state.paused?R.string.controls_resume:R.string.controls_pause),action(ACTION_PAUSE,41)).build())
            .addAction(new Notification.Action.Builder(null,service.getString(dimmer.active()?R.string.ui_panel_restore:R.string.controls_dim_phone),action(dimmer.active()?ACTION_RESTORE:ACTION_DIM,42)).build())
            .addAction(new Notification.Action.Builder(null,service.getString(R.string.ui_stop),action("stop",43)).build());
        service.getSystemService(NotificationManager.class).notify(notificationId,builder.build());
    }
    private void removeOverlay(){if(overlay!=null){try{windows.removeView(overlay);}catch(RuntimeException ignored){}overlay=null;}}
    @Override public void close(){if(closed)return;closed=true;main.removeCallbacksAndMessages(null);removeOverlay();dimmer.close();if(recovery!=null)recovery.close();releaseResources();}
    private void releaseResources(){if(resourcesReleased)return;resourcesReleased=true;if(bright!=null&&bright.isHeld())bright.release();}
}
