package com.mooncast.host;

import android.content.Context;
import android.app.AppOpsManager;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.os.SystemClock;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Minimum display brightness, not physical screen-off. The OS owns the override
 * through this window and removes it on window/process/overlay-permission loss.
 * Transparent pixels preserve captured content; never use FLAG_SECURE or a black veil. */
final class ScreenDimmer implements AutoCloseable {
 private final Context context;private final WindowManager windows;private final AppOpsManager appOps;
 private final Runnable changed;
 private final Handler main=new Handler(Looper.getMainLooper());private final ScreenDimState state=new ScreenDimState();
 private final BroadcastReceiver screenOff=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){finish();}};
 private FrameLayout guard;private WindowManager.LayoutParams params;
 private ViewTreeObserver.OnPreDrawListener noticePreDraw;private ViewTreeObserver.OnDrawListener noticeDraw;private Runnable noticeCommit,noticeTimeout;private boolean gesture,closed,watching,screenRegistered;
 private final AppOpsManager.OnOpChangedListener overlayChanged=(op,pkg)->{if(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW.equals(op))main.post(()->{if(!closed)finish();});};
 ScreenDimmer(Context context,Runnable changed){this.context=context;this.changed=changed;windows=context.getSystemService(WindowManager.class);appOps=context.getSystemService(AppOpsManager.class);try{appOps.startWatchingMode(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,context.getPackageName(),overlayChanged);watching=true;IntentFilter filter=new IntentFilter(Intent.ACTION_SCREEN_OFF);if(Build.VERSION.SDK_INT>=33)context.registerReceiver(screenOff,filter,Context.RECEIVER_NOT_EXPORTED);else context.registerReceiver(screenOff,filter);screenRegistered=true;}catch(RuntimeException unavailable){watching=false;}}
 boolean active(){return state.active();}
 boolean dimmed(){return state.phase==ScreenDimState.Phase.DIMMED;}
 boolean countdown(){return state.phase==ScreenDimState.Phase.COUNTDOWN;}
 long countdownStartedAt(){return state.countdownStartedAt;}
 long dimmedAt(){return state.dimmedAt;}
 void request(){
  if(closed)return;if(active()){restore();return;}
  if(!watching||!screenRegistered||!Settings.canDrawOverlays(context)){Toast.makeText(context,R.string.controls_overlay_denied,Toast.LENGTH_LONG).show();return;}
  int token=state.request(SystemClock.elapsedRealtime());guard=new FrameLayout(context);guard.setBackgroundColor(Color.TRANSPARENT);
  guard.setOnTouchListener((v,event)->{
   int action=event.getActionMasked();
   if(action==MotionEvent.ACTION_DOWN){gesture=true;restore();}
   if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){gesture=false;if(state.phase==ScreenDimState.Phase.WAKING)finish();}
   return true;
  });
  params=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
  params.alpha=1f;params.screenBrightness=WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
  TextView notice=new TextView(context);notice.setText(R.string.controls_dim_countdown);notice.setTextColor(Color.WHITE);notice.setBackgroundColor(0xe018354a);notice.setPadding(24,24,24,24);
  FrameLayout.LayoutParams text=new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER);guard.addView(notice,text);
  try{windows.addView(guard,params);}catch(RuntimeException denied){guard=null;state.bright();changed.run();return;}
  observeNotice(token,guard);changed.run();
 }
 private void observeNotice(int token,View expected){
  noticeTimeout=()->{if(!closed&&guard==expected&&state.noticeExpired(token,SystemClock.elapsedRealtime()))finish();};
  main.postDelayed(noticeTimeout,Math.max(0,state.noticeDeadline-SystemClock.elapsedRealtime()));
  noticePreDraw=()->{
   ViewTreeObserver observer=expected.getViewTreeObserver();
   if(observer.isAlive())observer.removeOnPreDrawListener(noticePreDraw);noticePreDraw=null;
   if(closed||guard!=expected||state.generation!=token)return true;
   if(Build.VERSION.SDK_INT>=29&&expected.isHardwareAccelerated()){
    // A submitted frame is stronger than layout readiness, not optical proof.
    noticeCommit=()->main.post(()->noticeReady(token,expected));observer.registerFrameCommitCallback(noticeCommit);
   }else{
    // Older/software rendering has no frame-commit API. Wait until its first
    // draw traversal returns; this is deliberately a weaker fallback.
    boolean[] posted={false};noticeDraw=()->{if(!posted[0]){posted[0]=true;main.post(()->noticeReady(token,expected));}};
    observer.addOnDrawListener(noticeDraw);
   }
   return true;
  };
  expected.getViewTreeObserver().addOnPreDrawListener(noticePreDraw);
 }
 private void clearNoticeCallbacks(View expected){
  if(noticeTimeout!=null)main.removeCallbacks(noticeTimeout);noticeTimeout=null;
  if(expected!=null){ViewTreeObserver observer=expected.getViewTreeObserver();if(observer.isAlive()){
   if(noticePreDraw!=null)observer.removeOnPreDrawListener(noticePreDraw);
   if(noticeDraw!=null)observer.removeOnDrawListener(noticeDraw);
   if(Build.VERSION.SDK_INT>=29&&noticeCommit!=null)observer.unregisterFrameCommitCallback(noticeCommit);
  }}
  noticePreDraw=null;noticeDraw=null;noticeCommit=null;
 }
 private void noticeReady(int token,View expected){
  if(closed||guard!=expected)return;
  long now=SystemClock.elapsedRealtime();if(state.noticeExpired(token,now)){finish();return;}
  if(!state.noticeReady(token,now))return;
  clearNoticeCallbacks(expected);changed.run();
  main.postDelayed(()->{
   if(closed||guard!=expected||!state.activate(token,SystemClock.elapsedRealtime()))return;
   if(guard==null||!Settings.canDrawOverlays(context)){finish();return;}
   guard.removeAllViews();params.screenBrightness=0f;
   try{windows.updateViewLayout(guard,params);}catch(RuntimeException failure){finish();return;}
   changed.run();
  },3000);
 }
 void restore(){
  if(!active())return;main.removeCallbacksAndMessages(null);clearNoticeCallbacks(guard);state.beginWake();
  if(guard!=null&&params!=null){params.screenBrightness=WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;try{windows.updateViewLayout(guard,params);}catch(RuntimeException ignored){}}
  // Keep the first complete waking gesture away from the foreground application.
  if(!gesture)finish();else changed.run();
 }
 private void finish(){
  main.removeCallbacksAndMessages(null);clearNoticeCallbacks(guard);state.bright();gesture=false;
  if(guard!=null){View old=guard;guard=null;try{windows.removeViewImmediate(old);}catch(RuntimeException ignored){}}
  params=null;changed.run();
 }
 @Override public void close(){if(closed)return;closed=true;try{appOps.stopWatchingMode(overlayChanged);}catch(RuntimeException ignored){}try{if(screenRegistered)context.unregisterReceiver(screenOff);}catch(IllegalArgumentException ignored){}finish();}
}
