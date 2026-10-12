package com.mooncast.host;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.View;
import android.widget.*;
import java.lang.reflect.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Real Activity/rotation/background/binder checks; permission results are injected, not user capture. */
final class ProtocolTabsChecks {
    interface Checked { void run() throws Exception; }
    interface Condition { boolean ready() throws Exception; }
    private static volatile String stage="initialization";
    private static void checkpoint(Instrumentation test,String value){
        stage=value;android.util.Log.i("MoonCastProtocolTabs",value);
        Bundle progress=new Bundle();progress.putString("stream","protocol-tabs: "+value+"\n");test.sendStatus(0,progress);
    }
    private static void onMain(Instrumentation test, Checked action) throws Exception {
        // Global queue-idle waits can be held indefinitely by live rendering/animations.
        // Dispatch only this operation and bound it independently of the render queue.
        AtomicReference<Throwable> failed = new AtomicReference<>();CountDownLatch completed=new CountDownLatch(1);
        Handler handler=new Handler(Looper.getMainLooper());Runnable task=()->{
            try{action.run();}catch(Throwable failure){failed.set(failure);}finally{completed.countDown();}
        };
        handler.post(task);
        if(!completed.await(8,TimeUnit.SECONDS)){handler.removeCallbacks(task);throw new AssertionError("Main callback timed out during "+stage);}
        if(failed.get()!=null)throw new AssertionError("During "+stage,failed.get());
    }
    private static void awaitState(Instrumentation test,String description,Condition condition)throws Exception{
        long deadline=SystemClock.elapsedRealtime()+8000;
        do{
            AtomicBoolean ready=new AtomicBoolean();onMain(test,()->ready.set(condition.ready()));
            if(ready.get())return;Thread.sleep(40);
        }while(SystemClock.elapsedRealtime()<deadline);
        throw new AssertionError("Timed out waiting for "+description+" during "+stage);
    }
    private static Object field(Activity activity, String name) throws Exception {
        Field field = MainActivity.class.getDeclaredField(name); field.setAccessible(true); return field.get(activity);
    }
    private static void set(Activity activity, String name, Object value) throws Exception {
        Field field = MainActivity.class.getDeclaredField(name); field.setAccessible(true); field.set(activity, value);
    }
    private static void call(Activity activity, String name) throws Exception {
        Method method = MainActivity.class.getDeclaredMethod(name); method.setAccessible(true); method.invoke(activity);
    }
    private static Object invoke(Activity activity,String name,Class<?>[] types,Object... args) throws Exception {
        Method method=MainActivity.class.getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(activity,args);
    }
    private static Dialog picker(Activity activity)throws Exception{return (Dialog)field(activity,"airPicker");}
    private static void recreate(Instrumentation test,AtomicReference<Activity> current)throws Exception{
        Activity before=current.get();CountDownLatch recreated=new CountDownLatch(1);Application app=before.getApplication();
        Application.ActivityLifecycleCallbacks callbacks=new Application.ActivityLifecycleCallbacks(){
            public void onActivityCreated(Activity activity,Bundle state){if(activity instanceof MainActivity && activity!=before)current.set(activity);}
            public void onActivityResumed(Activity activity){if(activity==current.get() && activity!=before)recreated.countDown();}
            public void onActivityStarted(Activity a){}public void onActivityPaused(Activity a){}public void onActivityStopped(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}public void onActivityDestroyed(Activity a){}
        };
        onMain(test,()->{app.registerActivityLifecycleCallbacks(callbacks);before.recreate();});
        try{check(recreated.await(8,TimeUnit.SECONDS),"Activity recreation completed");}
        finally{onMain(test,()->app.unregisterActivityLifecycleCallbacks(callbacks));}
        awaitState(test,"recreated Activity window visible",()->(Boolean)field(current.get(),"resumed") && current.get().getWindow().getDecorView().isShown());
    }
    private static CastSessionCoordinator flow(Activity activity) throws Exception { return (CastSessionCoordinator)field(activity,"session"); }
    private static Button button(Activity activity, String name) throws Exception { return (Button)field(activity,name); }
    private static void check(boolean pass, String message) { if (!pass) throw new AssertionError(message); }
    private static int fakeConsent(Activity activity) throws Exception {
        CastSessionCoordinator flow = flow(activity); int token = flow.begin();
        set(activity,"pendingToken",token); set(activity,"pendingMode",flow.selected());
        set(activity,"pendingProjectionRequest",1001 + token * 2); call(activity,"updateSessionUi");
        return 1001 + token * 2;
    }
    private static void result(Activity activity,int request,int code) throws Exception {
        Method callback = MainActivity.class.getDeclaredMethod("onActivityResult",int.class,int.class,Intent.class);
        callback.setAccessible(true); callback.invoke(activity,request,code,code==Activity.RESULT_OK?new Intent():null);
    }
    static void verify(Instrumentation test) throws Exception {
        checkpoint(test,"launch Activity");
        Context context = test.getTargetContext();
        AtomicReference<Activity> current = new AtomicReference<>(test.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        try {
            checkpoint(test,"Activity launched; initialize tabs");
            awaitState(test,"Activity visible and resumed",()->(Boolean)field(current.get(),"resumed") && current.get().getWindow().getDecorView().isShown());
            onMain(test, () -> {
                Activity activity = current.get();
                activity.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                View advanced=activity.getWindow().getDecorView().findViewWithTag("advanced_content");
                View disclosure=activity.getWindow().getDecorView().findViewWithTag("advanced_toggle");
                check(advanced!=null && advanced.getVisibility()==View.GONE,"advanced dashboard is collapsed on fresh launch");
                int generation=flow(activity).generation();
                boolean audioBefore=((CompoundButton)field(activity,"audio")).isChecked();
                disclosure.performClick();check(advanced.getVisibility()==View.VISIBLE,"settings deliberately expand");
                disclosure.performClick();check(advanced.getVisibility()==View.GONE,"settings collapse repeatedly");
                check(flow(activity).generation()==generation && ((CompoundButton)field(activity,"audio")).isChecked()==audioBefore,"presentation disclosure never changes capture or audio session");
                check(((View)field(activity,"pinBox")).getParent()!=advanced,"pairing stays outside advanced disclosure");
                button(activity,"moonlightTab").performClick();
                check(button(activity,"airTab").getLayoutParams().height >= 64 * context.getResources().getDisplayMetrics().density,"large top AirPlay tab");
                ((Spinner)field(activity,"source")).setSelection(0);
                ((CompoundButton)field(activity,"root")).setChecked(false);
            });
            checkpoint(test,"explicit audio choices");audioChoiceChecks(test,current.get());
            checkpoint(test,"picker visibility, selection and discovery states");pickerChecks(test,current);
            checkpoint(test,"legacy preferences and consent guards");
            onMain(test, () -> {
                Activity activity=current.get();
                ((CompoundButton)field(activity,"audio")).setChecked(true);
                ((CompoundButton)field(activity,"muteLocal")).setChecked(true);
                ((Spinner)field(activity,"scale")).setSelection(CropGeometry.CINEMA);
                button(activity,"airTab").performClick();
                check(flow(activity).selected()==CastSessionCoordinator.AIRPLAY,"AirPlay tab selected");
                check(((View)field(activity,"airPanel")).getVisibility()==View.VISIBLE,"AirPlay panel visible");
                ((CompoundButton)field(activity,"airLegacy")).setChecked(true);
                check(((CompoundButton)field(activity,"audio")).isChecked(),"legacy mode must not erase audio preference");
                check(!((View)field(activity,"audio")).isEnabled(),"legacy audio disabled");
                EditText legacyPassword=(EditText)field(activity,"airPin");legacyPassword.setText("TV-pass_123!");
                check(legacyPassword.getText().toString().equals("TV-pass_123!"),"legacy receiver password accepts letters and symbols");
                check(!legacyPassword.isSaveEnabled(),"receiver password is not saved into Activity view state");
                check(((CompoundButton)field(activity,"muteLocal")).isChecked(),"legacy must preserve local mute preference");
                ((CompoundButton)field(activity,"airLegacy")).setChecked(false);
                check(((EditText)field(activity,"airPin")).length()==0,"changing protocol variant clears password");
                set(activity,"selectedAirAddress","192.168.1.30:7100");
                call(activity,"saveAirPlay");
                ((Spinner)field(activity,"airQuality")).setSelection(2);
            });
            onMain(test, () -> {
                Activity activity=current.get();
                check(((Spinner)field(activity,"scale")).getSelectedItemPosition()==CropGeometry.CINEMA,"shared framing retained");
                int request=fakeConsent(activity), generation=flow(activity).generation();
                call(activity,"begin"); call(activity,"begin");
                check(flow(activity).generation()==generation,"repeated Start does not request another grant");
                check(!button(activity,"airStart").isEnabled(),"Start disabled while awaiting grant");
                button(activity,"moonlightTab").performClick();
                result(activity,request,Activity.RESULT_OK);
                check(!flow(activity).hasHost() && flow(activity).selected()==CastSessionCoordinator.MOONLIGHT,"stale AirPlay grant discarded after tab switch");
                button(activity,"airTab").performClick();
                request=fakeConsent(activity); result(activity,request,Activity.RESULT_CANCELED);
                check(!flow(activity).busy() && button(activity,"airStart").isEnabled(),"denied projection returns Start");
                request=fakeConsent(activity); button(activity,"airStop").performClick(); result(activity,request,Activity.RESULT_OK);
                check(!flow(activity).hasHost(),"cancelled start ignores late success");
            });
            checkpoint(test,"pending-consent Activity recreation");
            // Real recreation while an OS consent result is outstanding.
            final int[] pending = new int[1];
            onMain(test, () -> pending[0]=fakeConsent(current.get()));
            onMain(test,()->current.get().getWindow().getDecorView().findViewWithTag("advanced_toggle").performClick());
            recreate(test,current);
            onMain(test,()->{View advanced=current.get().getWindow().getDecorView().findViewWithTag("advanced_content");check(advanced.getVisibility()==View.VISIBLE,"disclosure survives rotation during consent");current.get().getWindow().getDecorView().findViewWithTag("advanced_toggle").performClick();});
            onMain(test, ()->{
                Activity activity=current.get();
                check(flow(activity).selected()==CastSessionCoordinator.AIRPLAY && flow(activity).phase()==CastSessionCoordinator.PERMISSION,"pending mode/consent restored across recreation");
                check(field(activity,"selectedAirAddress").equals("192.168.1.30:7100"),"manual endpoint restored");
                check(((Spinner)field(activity,"airQuality")).getSelectedItemPosition()==2,"quality persisted");
                check(((CompoundButton)field(activity,"audio")).isChecked(),"shared audio persisted");
                result(activity,pending[0],Activity.RESULT_CANCELED);check(!flow(activity).busy(),"result after recreation consumed correctly");
            });
            checkpoint(test,"background and resume");
            Activity overlay=test.startActivitySync(new Intent(context,TestPatternActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            onMain(test,overlay::finish);awaitState(test,"Activity resumed after overlay",()->(Boolean)field(current.get(),"resumed"));
            onMain(test,()->check(flow(current.get()).selected()==CastSessionCoordinator.AIRPLAY,"background/foreground keeps tab"));
            checkpoint(test,"change-device confirmation and teardown");changeDeviceChecks(test,current.get());
            checkpoint(test,"early service failure and stale callback");fastFailureBeforeBinding(test,current.get());
            checkpoint(test,"actual host binder-death barrier");binderDeathBarrier(test,current.get());
            checkpoint(test,"stale host recovery");staleHostRecovery(test,current.get());
            checkpoint(test,"all assertions passed");
        } finally { onMain(test,()->current.get().finish()); }
    }
    /** Locate the actual label action, ignoring decorative icons and the switch itself. */
    private static View clickableToggleLabel(CompoundButton toggle){
        android.view.ViewGroup row=(android.view.ViewGroup)toggle.getParent();
        View match=null;
        for(int i=0;i<row.getChildCount();i++){
            View child=row.getChildAt(i);
            if(child!=toggle && child instanceof android.view.ViewGroup && child.isClickable()){
                check(match==null,"toggle row has one unambiguous clickable label");match=child;
            }
        }
        check(match!=null,"toggle row exposes a clickable label independently of icon order");
        return match;
    }
    private static void audioChoiceChecks(Instrumentation test,Activity activity)throws Exception{
        onMain(test,()->{
            button(activity,"moonlightTab").performClick();CompoundButton audio=(CompoundButton)field(activity,"audio"),mute=(CompoundButton)field(activity,"muteLocal");
            SharedPreferences preferences=(SharedPreferences)invoke(activity,"settings",new Class<?>[]{});
            audio.setChecked(true);clickableToggleLabel(audio).performClick();
            check(!audio.isChecked() && !preferences.getBoolean("audio",true) && preferences.getBoolean("audioUserChosen",false),"audio label click persists explicit false choice");
            ((CompoundButton)field(activity,"root")).setChecked(true);((CompoundButton)field(activity,"root")).setChecked(false);call(activity,"loadProfile");
            check(!audio.isChecked() && !preferences.getBoolean("audio",true),"explicit audio false survives disabled source and profile reload");
            audio.performClick();mute.setChecked(true);mute.performClick();
            check(!mute.isChecked() && !preferences.getBoolean("muteLocal",true) && preferences.getBoolean("muteLocalUserChosen",false),"mute switch click persists explicit false choice");
            call(activity,"loadProfile");check(!mute.isChecked(),"explicit mute false survives profile reload");
            int request=fakeConsent(activity);call(activity,"refreshControlOptions");
            check(field(activity,"remote")==null && !audio.isEnabled() && !mute.isEnabled(),"Moonlight permission wait disables audio choices even before binding");
            result(activity,request,Activity.RESULT_CANCELED);
            int token=flow(activity).begin();check(flow(activity).start(token,CastSessionCoordinator.MOONLIGHT),"generated Moonlight starting state");call(activity,"refreshControlOptions");
            check(!audio.isEnabled() && !mute.isEnabled(),"Moonlight STARTING disables audio choices before binding");
            call(activity,"hostGone");
        });
    }
    private static void pickerChecks(Instrumentation test,AtomicReference<Activity> current)throws Exception{
        onMain(test,()->{
            Activity activity=current.get();button(activity,"airTab").performClick();
            check(!picker(activity).isShowing(),"homepage has no receiver dialog");
            int generation=flow(activity).generation();button(activity,"airStart").performClick();
            check(picker(activity) instanceof MyTvPicker && picker(activity).isShowing(),"AirPlay opens shared My TV picker");
            check(!flow(activity).busy() && flow(activity).generation()==generation,"picker never requests capture without selection");
            Dialog first=picker(activity);button(activity,"airStart").performClick();
            check(first==picker(activity),"repeated Cast does not stack dialogs");
            picker(activity).onBackPressed();check(!picker(activity).isShowing(),"Back closes shared picker");
            button(activity,"airStart").performClick();button(activity,"moonlightTab").performClick();
            check(!picker(activity).isShowing() && !flow(activity).busy(),"protocol switch closes picker without capture");
            button(activity,"airTab").performClick();
        });
    }
    private static void changeDeviceChecks(Instrumentation test,Activity activity)throws Exception{
        onMain(test,()->{
            set(activity,"selectedAirAddress","192.168.1.71:7000");set(activity,"selectedAirName","Generated living room");
            int token=flow(activity).begin();check(flow(activity).start(token,CastSessionCoordinator.AIRPLAY),"generated active AirPlay state");
            Bundle status=new Bundle();status.putBoolean("connected",true);status.putString("state","Generated connected state");invoke(activity,"handleStatus",new Class<?>[]{Bundle.class},status);
            check(((TextView)field(activity,"airSelection")).getText().toString().contains(activity.getString(R.string.ui_airplay_connected)),"connected receiver is identified on homepage");
            check(button(activity,"airStop").isShown() && button(activity,"airChange").isShown(),"connected page exposes Stop and Change device");
            button(activity,"airChange").performClick();AlertDialog confirm=(AlertDialog)field(activity,"airChangeDialog");check(confirm.isShowing(),"Change device explains current cast will stop");
            button(activity,"airChange").performClick();check(field(activity,"airChangeDialog")==confirm,"repeated change does not stack confirmations");
            confirm.getButton(DialogInterface.BUTTON_NEGATIVE).performClick();
        });
        awaitState(test,"change confirmation dismissed",()->!((AlertDialog)field(activity,"airChangeDialog")).isShowing());
        onMain(test,()->{
            check(flow(activity).hasHost() && !picker(activity).isShowing(),"cancelled device change preserves current cast");
            button(activity,"airChange").performClick();((AlertDialog)field(activity,"airChangeDialog")).getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        });
        awaitState(test,"change-device stop requested",()->flow(activity).phase()==CastSessionCoordinator.STOPPING);
        onMain(test,()->{
            check(flow(activity).phase()==CastSessionCoordinator.STOPPING && !picker(activity).isShowing(),"device picker waits for old host teardown");
            call(activity,"hostGone");check(picker(activity).isShowing() && !flow(activity).hasHost() && !flow(activity).busy(),"confirmed teardown opens picker without auto-starting another cast");
            picker(activity).dismiss();check(!flow(activity).hasHost(),"cancelling change picker leaves cast stopped");
        });
    }
    private static void fastFailureBeforeBinding(Instrumentation test,Activity activity)throws Exception{
        AtomicReference<Messenger> initialReply=new AtomicReference<>();
        onMain(test,()->{
            button(activity,"airTab").performClick();int token=flow(activity).begin();flow(activity).start(token,CastSessionCoordinator.AIRPLAY);
            set(activity,"startingSince",SystemClock.elapsedRealtime());set(activity,"lastFailure","");set(activity,"remote",null);
            Class<?> inner=Class.forName("com.mooncast.host.MainActivity$HostConnection");
            Constructor<?> constructor=inner.getDeclaredConstructor(MainActivity.class);constructor.setAccessible(true);
            Object connection=constructor.newInstance(activity);set(activity,"connection",connection);
            Field reply=inner.getDeclaredField("response");reply.setAccessible(true);initialReply.set((Messenger)reply.get(connection));
            Bundle status=new Bundle();status.putString("error","Generated immediate startup failure");status.putBoolean("stopping",true);
            Message message=Message.obtain(null,HostService.STATUS);message.setData(status);initialReply.get().send(message);
        });
        onMain(test,()->{
            check(field(activity,"remote")==null,"failure arrived before service connection");
            check(field(activity,"lastFailure").equals("Generated immediate startup failure"),"initial status Messenger retains early error");
            call(activity,"hostGone");
            check(((TextView)field(activity,"airDetail")).getText().toString().equals("Generated immediate startup failure"),"early error survives host death");
            set(activity,"lastFailure","");
            Bundle status=new Bundle();status.putString("error","Stale old connection");Message message=Message.obtain(null,HostService.STATUS);message.setData(status);initialReply.get().send(message);
        });
        onMain(test,()->check(field(activity,"lastFailure").equals(""),"discarded connection cannot overwrite later state"));
    }
    private static void staleHostRecovery(Instrumentation test,Activity activity)throws Exception{
        // This self-binding keeps the shared :host process alive without enabling Accessibility
        // in system settings, collecting events, or granting any input permissions.
        CountDownLatch connected=new CountDownLatch(1);
        ServiceConnection unrelated=new ServiceConnection(){
            public void onServiceConnected(ComponentName name,IBinder binder){connected.countDown();}
            public void onServiceDisconnected(ComponentName name){}
        };
        boolean bound=activity.bindService(new Intent(activity,ControlAccessibilityService.class),unrelated,Context.BIND_AUTO_CREATE);
        check(bound,"unrelated same-process component bound");
        try{
            check(connected.await(5,TimeUnit.SECONDS),"unrelated shared-process component connected");
            onMain(test,()->{
                boolean hostAlive=false;
                for(ActivityManager.RunningAppProcessInfo process:activity.getSystemService(ActivityManager.class).getRunningAppProcesses())
                    if(process.uid==android.os.Process.myUid() && process.processName.equals(activity.getPackageName()+":host"))hostAlive=true;
                check(hostAlive,"shared host process really exists");
                long now=SystemClock.elapsedRealtime();
                flow(activity).restoreHost(CastSessionCoordinator.MOONLIGHT);flow(activity).select(CastSessionCoordinator.AIRPLAY);
                set(activity,"senderPid",Integer.MAX_VALUE);set(activity,"stopPending",true);
                set(activity,"startingSince",now-10000);set(activity,"missingHostSince",now-800);
                call(activity,"verifyHostGone");
                check(!flow(activity).busy() && flow(activity).selected()==CastSessionCoordinator.AIRPLAY,"dead sender PID recovers despite live Accessibility process");
                flow(activity).restoreHost(CastSessionCoordinator.MOONLIGHT);flow(activity).select(CastSessionCoordinator.AIRPLAY);
                set(activity,"senderPid",-1);set(activity,"stopPending",true);set(activity,"startingSince",now-10000);set(activity,"missingHostSince",now-1000);
                call(activity,"verifyHostGone");
                check(flow(activity).phase()==CastSessionCoordinator.STOPPING,"no-PID recovery waits through cleanup deadline");
                set(activity,"missingHostSince",now-6000);call(activity,"verifyHostGone");
                check(!flow(activity).busy(),"actual sender-service absence recovers after conservative grace");
            });
        }finally{activity.unbindService(unrelated);}
    }
    private static void binderDeathBarrier(Instrumentation test,Activity activity)throws Exception{
        // Bind the Activity's actual ServiceConnection to the existing no-capture lifecycle probe.
        // The probe's main looper stalls; only the host's independent shutdown deadline kills it.
        onMain(test,()->{
            button(activity,"moonlightTab").performClick();flow(activity).restoreHost(CastSessionCoordinator.MOONLIGHT);
            set(activity,"startingSince",SystemClock.elapsedRealtime());
            Class<?> inner=Class.forName("com.mooncast.host.MainActivity$HostConnection");
            Constructor<?> constructor=inner.getDeclaredConstructor(MainActivity.class);constructor.setAccessible(true);
            ServiceConnection connection=(ServiceConnection)constructor.newInstance(activity);set(activity,"connection",connection);
            boolean bound=activity.bindService(new Intent(activity,HostLifecycleProbe.class).putExtra("fault","main"),connection,Context.BIND_AUTO_CREATE);
            set(activity,"bound",bound);set(activity,"bindingSince",SystemClock.elapsedRealtime());check(bound,"probe binding accepted");
        });
        long deadline=SystemClock.elapsedRealtime()+5000;
        while(SystemClock.elapsedRealtime()<deadline){AtomicBoolean ready=new AtomicBoolean();onMain(test,()->ready.set(field(activity,"remote")!=null));if(ready.get())break;Thread.sleep(50);}
        Thread.sleep(700);
        onMain(test,()->{check(field(activity,"remote")!=null,"probe connected");button(activity,"airTab").performClick();});
        Thread.sleep(600);
        onMain(test,()->{
            check(flow(activity).phase()==CastSessionCoordinator.STOPPING,"Stop waits for real host process death");
            check(flow(activity).selected()==CastSessionCoordinator.MOONLIGHT,"old tab retained before death");
            check(!button(activity,"airTab").isEnabled() && !button(activity,"start").isEnabled(),"no repeated switch/start while old host still alive");
        });
        deadline=SystemClock.elapsedRealtime()+6000;
        while(SystemClock.elapsedRealtime()<deadline){AtomicBoolean done=new AtomicBoolean();onMain(test,()->done.set(!flow(activity).busy()));if(done.get())break;Thread.sleep(100);}
        onMain(test,()->{
            check(!flow(activity).busy() && flow(activity).selected()==CastSessionCoordinator.AIRPLAY,"binder death completes deferred tab switch");
            check(!flow(activity).hasHost() && button(activity,"airStart").isEnabled(),"new tab needs explicit fresh Start");
        });
    }
}
