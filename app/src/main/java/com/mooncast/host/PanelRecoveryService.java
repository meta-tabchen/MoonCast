package com.mooncast.host;
import android.app.*;import android.content.*;import android.content.pm.ServiceInfo;import android.graphics.*;import android.os.*;import android.provider.Settings;import android.view.*;import android.widget.*;
import java.util.concurrent.*;
/** Dedicated :panel owner: no capture, transport, remote input or media libraries.
 * Production physical-off remains gated until backend/device fault tests pass. */
public final class PanelRecoveryService extends Service {
 private final Handler main=new Handler(Looper.getMainLooper());
 private final ExecutorService io=Executors.newSingleThreadExecutor(r->new Thread(r,"PanelRecoveryIO"));
 private final PanelRecoveryState state=new PanelRecoveryState();
 private PanelRecoveryJournal journal;
 private IBinder owner;private boolean rootMode,started,gesture,restorationAck,confirmationPending,terminalRequested;private volatile boolean closed;
 private volatile int operationGeneration;
 private ShizukuInputBackend shizuku;private RootPanelBackend root;
 private WindowManager windows;private LinearLayout guard;private Button recoveryConfirmation;private PowerManager.WakeLock wake;
 private final Runnable watch=new Runnable(){public void run(){
  if(closed)return;
  if(state.phase==PanelRecoveryState.Phase.OFF){
   if(!Settings.canDrawOverlays(PanelRecoveryService.this)||owner==null||!owner.isBinderAlive()){restore(false);return;}
   if(state.renewing())submitOff(operationGeneration);
  }
  main.postDelayed(this,5000);
 }};
 private final IPanelRecovery.Stub binder=new IPanelRecovery.Stub(){
  public void initialize(IBinder token,boolean root){main.post(()->initializeOwner(token,root));}
  public void requestOff(){main.post(PanelRecoveryService.this::requestOff);}
  public void restore(){main.post(()->PanelRecoveryService.this.restore(false));}
  public void disconnected(){main.post(()->{if(closed)return;state.invalidate();confirmationPending=false;PanelRecoveryService.this.restore(false);});}
  public void ownerStopped(){main.post(PanelRecoveryService.this::ownerStopped);}
 };
 @Override protected void attachBaseContext(Context c){super.attachBaseContext(com.mooncast.locale.AppLocale.wrap(c));}
 @Override public void onCreate(){super.onCreate();SharedPreferences prefs=getSharedPreferences("panel-recovery",0);journal=new PanelRecoveryJournal(new PanelRecoveryJournal.Storage(){public boolean read(String key){return prefs.getBoolean(key,false);}public boolean write(String key,boolean value){return prefs.edit().putBoolean(key,value).commit();}});if(journal.blocked())state.blockFurtherOff();windows=getSystemService(WindowManager.class);getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("panel-recovery",getString(R.string.controls_recovery_channel),NotificationManager.IMPORTANCE_LOW));}
 @Override public IBinder onBind(Intent i){return binder;}
 @Override public int onStartCommand(Intent i,int flags,int id){if(i!=null&&"restore".equals(i.getAction()))restore(false);return START_NOT_STICKY;}
 private void initializeOwner(IBinder token,boolean root){
  if(closed||token==null||owner!=null&&owner!=token)return;
  owner=token;rootMode=root;
  try{token.linkToDeath(()->main.post(this::ownerStopped),0);}catch(RemoteException failure){ownerStopped();}
 }
 private void requestOff(){
  if(closed)return;
  if(state.failedRecovery){toast(R.string.controls_recovery_disabled);return;}
  if(!BuildConfig.PHYSICAL_SCREEN_OFF_VALIDATED){toast(R.string.controls_panel_validation_pending);return;}
  if(owner==null||!owner.isBinderAlive()||!Settings.canDrawOverlays(this)){toast(R.string.controls_panel_unavailable);return;}
  if(state.phase!=PanelRecoveryState.Phase.BRIGHT){restore(false);return;}
  if(rootMode){if(root==null||root.ended()){if(root!=null)root.close();root=new RootPanelBackend(this);terminalRequested=false;root.start();}}else if((shizuku==null||terminalRequested)&&ShizukuInputBackend.authorized()){shizuku=new ShizukuInputBackend(this,s->{},"mooncast-panel");terminalRequested=false;shizuku.start();}
  if(!(rootMode?root!=null&&root.ready():shizuku!=null&&shizuku.ready())){toast(R.string.controls_panel_unavailable);return;}
  terminalRequested=false;
  if(!state.request())return;operationGeneration=state.generation;int token=operationGeneration;
  startService(new Intent(this,PanelRecoveryService.class));started=true;
  Notification notification=notification();if(Build.VERSION.SDK_INT>=29)startForeground(19,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);else startForeground(19,notification);
  wake=getSystemService(PowerManager.class).newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK,"MoonCast:PanelRecovery");wake.setReferenceCounted(false);wake.acquire(6*60*60*1000L);
  showGuard(false);addLabel(state.test?R.string.controls_test_instructions:R.string.controls_off_countdown);addButton(R.string.controls_cancel_off,()->restore(false));
  main.postDelayed(()->{
   if(closed||token!=operationGeneration)return;if(guard==null||!Settings.canDrawOverlays(this)){restore(false);return;}if(!journal.arm()){state.blockFurtherOff();toast(R.string.controls_recovery_disabled);restore(false);return;}if(!state.off(token))return;
   // The same guard remains through the entire wake gesture and every failed restoration.
   showGuard(true);main.removeCallbacks(watch);main.postDelayed(watch,5000);
   submitOff(token);
   if(state.test)main.postDelayed(()->{if(token==operationGeneration&&state.phase==PanelRecoveryState.Phase.OFF){toast(R.string.controls_test_timeout);restore(false);}},15000);
   notifyState();
  },3000);
 }
 private void submitOff(int token){
  state.offSubmitted(token);
  io.execute(()->{boolean ok=!closed&&token==operationGeneration&&off();main.post(()->{if(!closed&&token==operationGeneration&&!ok)restore(false);});});
 }
 private boolean off(){try{if(rootMode)return root!=null&&root.power(true);return shizuku!=null&&shizuku.displayPower(true);}catch(RuntimeException failure){return false;}}
 private boolean on(){try{if(rootMode)return root!=null&&root.power(false);return shizuku!=null&&shizuku.restoreOwnedPanel();}catch(RuntimeException failure){return false;}}
 private void restore(boolean touch){
  if(closed)return;
  if(state.phase==PanelRecoveryState.Phase.BRIGHT){if(!state.owner)finishBright();return;}
  if(state.phase==PanelRecoveryState.Phase.RESTORING||state.phase==PanelRecoveryState.Phase.RECOVERY_REQUIRED)return;
  confirmationPending=state.test&&touch;int token=state.restore(touch);operationGeneration=token;main.removeCallbacks(watch);restoreAttempt(token);notifyState();
 }
 private void restoreAttempt(int token){
  if(closed||token!=operationGeneration)return;
  if(!state.beginAttempt(token)){if(state.phase==PanelRecoveryState.Phase.RECOVERY_REQUIRED)showRecovery();return;}
  // No synchronous Binder/Root call runs on the touch/notification looper.
  final boolean[] completed={false};
  io.execute(()->{boolean ok=!closed&&token==operationGeneration&&on();main.post(()->{
   if(closed||token!=operationGeneration)return;
   // Ordered ON after prior OFF proves those commands can no longer darken later.
   if(ok){state.acknowledgeQuiescence(token);updateRecoveryConfirmation();}
   if(completed[0]||token!=operationGeneration||state.phase!=PanelRecoveryState.Phase.RESTORING)return;completed[0]=true;
   if(ok){restorationAck=true;if(!gesture)acceptRestore(token);}else main.postDelayed(()->restoreAttempt(token),500);
  });});
  main.postDelayed(()->{if(!closed&&!completed[0]&&token==operationGeneration&&state.phase==PanelRecoveryState.Phase.RESTORING){completed[0]=true;state.uncertain(token);showRecovery();}},3000);
 }
 private void acceptRestore(int token){
  if(closed)return;
  if(!state.restored(token))return;if(!state.failedRecovery&&!journal.restored())state.blockFurtherOff();restorationAck=false;removeGuard();finishBright();
  if(state.failedRecovery){confirmationPending=false;toast(R.string.controls_recovery_disabled);}
  if(!closed&&!state.failedRecovery&&confirmationPending&&state.owner&&Settings.canDrawOverlays(this)){
   confirmationPending=false;showGuard(false);addLabel(R.string.controls_test_confirm);
   addButton(R.string.controls_test_yes,()->{state.verified=true;removeGuard();toast(R.string.controls_verified);});
   addButton(android.R.string.cancel,()->{state.verified=false;removeGuard();});
  }
 }
 private void showRecovery(){
  if(closed)return;state.blockFurtherOff();journal.quarantine();confirmationPending=false;restorationAck=false;operationGeneration=state.generation;terminalize();showGuard(false);addLabel(R.string.controls_recovery_required);
  // Long hold + complete UP is deliberately different from the first blind wake tap.
  Button confirm=addButton(R.string.controls_recovery_visible,()->{});recoveryConfirmation=confirm;updateRecoveryConfirmation();long[] down={0};
  confirm.setOnTouchListener((v,event)->{if(event.getAction()==MotionEvent.ACTION_DOWN){down[0]=SystemClock.elapsedRealtime();return true;}if(event.getAction()==MotionEvent.ACTION_UP){if(state.canVisiblyConfirm()&&down[0]!=0&&SystemClock.elapsedRealtime()-down[0]>=2000&&state.visiblyConfirmed()){operationGeneration=state.generation;removeGuard();finishBright();}down[0]=0;return true;}if(event.getAction()==MotionEvent.ACTION_CANCEL)down[0]=0;return true;});
  notifyState();
 }
 private void ownerStopped(){if(closed||!state.owner)return;state.ownerLost();confirmationPending=false;if(state.phase==PanelRecoveryState.Phase.BRIGHT){removeGuard();finishBright();}else{restore(false);if(state.phase==PanelRecoveryState.Phase.RECOVERY_REQUIRED)terminalize();}}
 private void updateRecoveryConfirmation(){if(recoveryConfirmation!=null)recoveryConfirmation.setEnabled(!closed&&state.canVisiblyConfirm());}
 private void terminalize(){
  if(terminalRequested)return;terminalRequested=true;int token=operationGeneration;
  final RootPanelBackend ownedRoot=root;final ShizukuInputBackend ownedShizuku=shizuku;final boolean ownedRootMode=rootMode;
  // Separate worker: a hung OFF on io must not prevent the helper receiving its
  // terminal flag. A true helper reply waits for all prior OFF operations to finish.
  new Thread(()->{boolean ack=ownedRootMode?ownedRoot!=null&&ownedRoot.terminalizePanel():ownedShizuku!=null&&ownedShizuku.terminalizePanel();
   main.post(()->{if(closed||token!=operationGeneration)return;if(ack)state.acknowledgeQuiescence(token);updateRecoveryConfirmation();});
  },"PanelTerminalize").start();
 }
 private void showGuard(boolean transparent){
  if(closed||!Settings.canDrawOverlays(this))return;
  if(guard==null){guard=new LinearLayout(this);guard.setOrientation(LinearLayout.VERTICAL);guard.setGravity(Gravity.CENTER);guard.setPadding(24,24,24,24);
   guard.setOnTouchListener((v,event)->{if(event.getAction()==MotionEvent.ACTION_DOWN){gesture=true;if(state.phase==PanelRecoveryState.Phase.OFF)restore(true);}if(event.getAction()==MotionEvent.ACTION_UP||event.getAction()==MotionEvent.ACTION_CANCEL){gesture=false;if(restorationAck)acceptRestore(operationGeneration);}return true;});
   WindowManager.LayoutParams p=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
   try{windows.addView(guard,p);}catch(RuntimeException failure){guard=null;return;}
  }
  guard.removeAllViews();guard.setBackgroundColor(transparent?Color.TRANSPARENT:0xf018354a);
 }
 private void addLabel(int text){if(guard==null)return;TextView label=new TextView(this);label.setText(text);label.setTextColor(Color.WHITE);label.setTextSize(18);guard.addView(label);}
 private Button addButton(int text,Runnable action){Button b=new Button(this);b.setText(text);b.setOnClickListener(v->action.run());if(guard!=null)guard.addView(b);return b;}
 private void removeGuard(){if(guard!=null){try{windows.removeView(guard);}catch(RuntimeException ignored){}guard=null;}recoveryConfirmation=null;gesture=false;}
 private Notification notification(){PendingIntent restore=PendingIntent.getService(this,70,new Intent(this,PanelRecoveryService.class).setAction("restore"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);return new Notification.Builder(this,"panel-recovery").setSmallIcon(R.drawable.ic_launcher).setContentTitle(getString(R.string.controls_recovery_channel)).setContentText(getString(state.phase==PanelRecoveryState.Phase.RECOVERY_REQUIRED?R.string.controls_recovery_required:R.string.controls_touch_restore)).setOngoing(true).setContentIntent(restore).addAction(new Notification.Action.Builder(null,getString(R.string.ui_panel_restore),restore).build()).build();}
 private void notifyState(){if(started)getSystemService(NotificationManager.class).notify(19,notification());}
 private void toast(int id){Toast.makeText(this,id,Toast.LENGTH_LONG).show();}
 private void finishBright(){if(wake!=null&&wake.isHeld())wake.release();if(started){stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();started=false;}if(!state.owner){closed=true;++operationGeneration;main.removeCallbacksAndMessages(null);terminalize();io.shutdown();}}
 @Override public void onDestroy(){
  if(!closed){closed=true;++operationGeneration;state.ownerLost();state.invalidate();main.removeCallbacksAndMessages(null);terminalize();removeGuard();if(wake!=null&&wake.isHeld())wake.release();io.shutdown();}
  super.onDestroy();
 }
}
