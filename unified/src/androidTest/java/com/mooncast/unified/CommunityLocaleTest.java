package com.mooncast.unified;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.app.LocaleManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ListView;
import android.widget.Spinner;
import com.mooncast.locale.AppLocale;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Actual Android resource/UI checks; controlled cleanup-failure state lives only in androidTest. */
final class CommunityLocaleTest {
    private static final String PHONE="com.mooncast.host.MainActivity", TV="com.mooncast.tv.TvActivity";
    private final Instrumentation runner;
    private final Bundle arguments;
    private final Context target;
    private final JSONArray checks=new JSONArray(), screenshots=new JSONArray();
    private final JSONObject report=new JSONObject();
    private final List<Activity> activities=new ArrayList<>();
    private File directory;
    private String phase,language;
    CommunityLocaleTest(Instrumentation runner,Bundle arguments){this.runner=runner;this.arguments=arguments;target=runner.getTargetContext();}
    void run()throws Exception {
        phase=arguments.getString("phase","resources");language=arguments.getString("language","en");
        String runId=arguments.getString("run_id","");
        if(!runId.matches("[a-f0-9]{32}")||!language.matches("system|en|zh"))throw new IllegalArgumentException("Invalid test parameters");
        directory=new File(target.getExternalFilesDir(null),"community-locale/"+runId);
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IllegalStateException("Cannot create evidence directory");
        report.put("scope","Actual debug APK UI on an Android emulator. No screen-capture permission, sent stream, decoded media, physical-TV or AirPlay interoperability is tested.")
            .put("phase",phase).put("language",language).put("sdk",Build.VERSION.SDK_INT).put("checks",checks).put("screenshots",screenshots);
        try {
            if("flows".equals(phase)||"phone".equals(phase)||"tv".equals(phase)){
                // The host harness may wake/dismiss the nonsecure disposable emulator;
                // this runner only observes readiness and never bypasses a device lock.
                android.os.PowerManager power=target.getSystemService(android.os.PowerManager.class);
                android.app.KeyguardManager keyguard=target.getSystemService(android.app.KeyguardManager.class);
                await("UI precondition: display is interactive",()->power!=null&&power.isInteractive(),5000);
                await("UI precondition: keyguard is dismissed",()->keyguard!=null&&!keyguard.isKeyguardLocked(),5000);
            }
            switch(phase){
                case "select" -> {AppLocale.select(target,language);verifyResources();}
                case "verify" -> verifyResources();
                case "resources" -> {for(String choice:new String[]{"en","zh","system"}){language=choice;AppLocale.select(target,choice);verifyResources();}}
                case "flows" -> languageFlows();
                case "phone" -> phoneScreens();
                case "tv" -> tvScreens();
                default -> throw new IllegalArgumentException("Unknown locale phase "+phase);
            }
            report.put("passed",true);
        }catch(Throwable failure){
            report.put("passed",false).put("failure",android.util.Log.getStackTraceString(failure));
            try{capture("failure");}catch(Throwable screenshotFailure){report.put("failureScreenshotError",screenshotFailure.toString());}
            throw failure;
        }finally{
            for(Activity activity:activities)runner.runOnMainSync(()->{if(!activity.isFinishing())activity.finish();});
            try(FileOutputStream stream=new FileOutputStream(new File(directory,phase+"-"+language+"-report.json"))){stream.write(report.toString(2).getBytes(StandardCharsets.UTF_8));}
        }
    }
    private Context fresh()throws Exception{return AppLocale.wrap(target.createPackageContext(target.getPackageName(),0));}
    private Context explicit(String tag)throws Exception {
        Context base=target.createPackageContext(target.getPackageName(),0);
        Configuration c=new Configuration(base.getResources().getConfiguration());c.setLocale(Locale.forLanguageTag(tag));
        return base.createConfigurationContext(c);
    }
    private String text(Context context,String name){int id=context.getResources().getIdentifier(name,"string",target.getPackageName());if(id==0)throw new AssertionError("Missing string "+name);return context.getString(id);}
    private String text(String name)throws Exception{return text(fresh(),name);}
    private String systemLanguage(){
        if(Build.VERSION.SDK_INT>=33){LocaleManager lm=target.getSystemService(LocaleManager.class);return lm.getSystemLocales().get(0).getLanguage();}
        return android.content.res.Resources.getSystem().getConfiguration().getLocales().get(0).getLanguage();
    }
    private void verifyResources()throws Exception {
        await("AppLocale persists "+language,()->language.equals(AppLocale.selected(target)),10000);
        String expected="system".equals(language)?("zh".equals(systemLanguage())?"zh":"en"):language;
        String[] keys={"unified_role_title","unified_role_phone","unified_role_tv","unified_language_system","ui_start_casting","phone_device_settings","frame","home_advanced","gs_connect_title","tv_airplay_ready"};
        for(String key:keys){
            String english=text(explicit("en"),key),chinese=text(explicit("zh"),key);
            check(key+" has distinct English/Chinese resources",!english.equals(chinese));
            String want="zh".equals(expected)?chinese:english;
            await(language+" resolves "+key,()->want.equals(text(fresh(),key)),10000);
        }
        report.put("actualLocales",fresh().getResources().getConfiguration().getLocales().toLanguageTags());
    }
    private SharedPreferences roles(){return target.getSharedPreferences("unified-role",Context.MODE_PRIVATE);}
    private void idlePhone(){roles().edit().putString("role","phone").remove("pendingRole").remove("pendingLanguage").putBoolean("pendingCleanup",false).commit();}
    private Activity launch(String cls){
        runner.setInTouchMode(false);
        Activity a=runner.startActivitySync(new Intent().setClassName(target.getPackageName(),cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        activities.add(a);runner.waitForIdleSync();return a;
    }
    private AlertDialog chooser(Activity activity){try{Field f=RoleActivity.class.getDeclaredField("chooser");f.setAccessible(true);return(AlertDialog)f.get(activity);}catch(Exception e){throw new IllegalStateException(e);}}
    private void openPhoneLanguage()throws Exception {
        clickText(text("phone_device_settings"));clickText(text("phone_language"));awaitText(text("locale_follow_system"));
    }
    private void verifyUnchanged(Activity phone,Object originalSession)throws Exception {
        check("Cancel/reselect keeps English","en".equals(AppLocale.selected(target)));
        check("Cancel/reselect keeps phone role","phone".equals(roles().getString("role","")));
        check("No language write or cleanup transaction remains",!roles().contains("pendingLanguage")&&!roles().getBoolean("pendingCleanup",false));
        awaitText(text("phone_device_settings"));
        runner.runOnMainSync(()->{
            if(phone.isFinishing()||!phone.hasWindowFocus())throw new AssertionError("Language cancellation replaced the current phone screen");
            try{Field f=phone.getClass().getDeclaredField("session");f.setAccessible(true);if(f.get(phone)!=originalSession)throw new AssertionError("Language cancellation replaced the idle session coordinator");}
            catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
        });
        check("Unchanged language leaves the same Activity/session coordinator",true);
    }
    private void languageFlows()throws Exception {
        language="en";AppLocale.select(target,language);idlePhone();verifyResources();
        Activity phone=launch(PHONE);awaitText(text("phone_device_settings"));
        Object[] session={null};runner.runOnMainSync(()->{try{Field f=phone.getClass().getDeclaredField("session");f.setAccessible(true);session[0]=f.get(phone);}catch(Exception e){throw new IllegalStateException(e);}});
        Instrumentation.ActivityMonitor unchangedMonitor=runner.addMonitor(PHONE,null,false);
        try{
            openPhoneLanguage();back();verifyUnchanged(phone,session[0]);
            openPhoneLanguage();clickText("简体中文");awaitText(text("locale_stop_notice"));back();verifyUnchanged(phone,session[0]);
            for(int i=0;i<2;i++){openPhoneLanguage();clickText("English");verifyUnchanged(phone,session[0]);}
            check("Cancel and repeated same-language selection launch no replacement Activity",unchangedMonitor.getHits()==0);
        }finally{runner.removeMonitor(unchangedMonitor);}
        Activity failed=launch("com.mooncast.unified.RoleSettingsActivity");
        // Controlled missed acknowledgments, no real casting session or production hook.
        // The role chooser is only a container for this failure-path test; actual language
        // cancellation tests above use the unchanged phone screen and normal menu.
        runner.runOnMainSync(()->{
            try{
                chooser(failed).dismiss();set(failed,"chooser",null);set(failed,"leaving",true);set(failed,"pending","tv");set(failed,"hostAck",false);set(failed,"fileAck",false);
                roles().edit().putBoolean("pendingCleanup",true).putString("pendingRole","tv").putString("pendingLanguage","zh").commit();
                Method m=RoleActivity.class.getDeclaredMethod("awaitStopped",long.class);m.setAccessible(true);m.invoke(failed,SystemClock.elapsedRealtime()-1);
            }catch(Exception e){throw new IllegalStateException(e);}
        });
        awaitText(text("unified_stop_failed"));
        check("Controlled cleanup failure preserves role","phone".equals(roles().getString("role","")));
        check("Controlled cleanup failure preserves language","en".equals(AppLocale.selected(target)));
        check("Controlled cleanup failure retains retry transaction",roles().getBoolean("pendingCleanup",false)&&"zh".equals(roles().getString("pendingLanguage","")));
        capture("cleanup-failure-controlled-state");
        runner.runOnMainSync(failed::finish);idlePhone();
        report.put("cleanupFailureMode","Only androidTest reflection supplies unacknowledged idle state and an expired deadline to the production failure path. No live stream was simulated or tested.");
        launch(PHONE);awaitText(text("phone_device_settings"));
        // Exercise the real home-menu confirmation -> acknowledged idle cleanup -> commit path.
        for(String choice:new String[]{"zh","en"}){
            openPhoneLanguage();clickText("zh".equals(choice)?"简体中文":"English");awaitText(text("locale_stop_notice"));
            clickText(fresh().getString(android.R.string.ok));
            await("Confirmed language is committed after idle cleanup",()->choice.equals(AppLocale.selected(target)),20000);
            language=choice;awaitText(text(explicit(choice),"phone_device_settings"));
            check("Successful language change retains phone role","phone".equals(roles().getString("role","")));
            check("Successful language change clears pending transaction",!roles().contains("pendingLanguage")&&!roles().getBoolean("pendingCleanup",false));
        }
    }
    private static void set(Activity activity,String name,Object value)throws Exception{Field f=RoleActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(activity,value);}
    private void phoneScreens()throws Exception {
        AppLocale.select(target,language);idlePhone();verifyResources();
        target.getSharedPreferences(PHONE,Context.MODE_PRIVATE).edit().putInt("selectedProtocol",0).putBoolean("hostExpected",false).commit();
        Activity phone=launch(PHONE);awaitText(text("phone_device_settings"));awaitText(text("ui_start_casting"));
        check("Phone Activity uses selected locale",text(phone,"ui_start_casting").equals(text("ui_start_casting")));
        capture("01-phone-home");
        clickText(text("phone_device_settings"));awaitText(text("phone_switch_device_mode"));awaitText(text("phone_language"));capture("02-phone-device-settings");back();
        // Use the real disclosure and spinner, scrolling them into view; no synthetic UI.
        runner.runOnMainSync(()->{
            View toggle=phone.getWindow().getDecorView().findViewWithTag("advanced_toggle");
            if(toggle==null)throw new AssertionError("Advanced settings disclosure missing");toggle.performClick();
        });runner.waitForIdleSync();
        Spinner[] scale={null};
        runner.runOnMainSync(()->{
            try{Field f=phone.getClass().getDeclaredField("scale");f.setAccessible(true);scale[0]=(Spinner)f.get(phone);scale[0].requestRectangleOnScreen(new android.graphics.Rect(0,0,scale[0].getWidth(),scale[0].getHeight()),true);}
            catch(Exception e){throw new IllegalStateException(e);}
        });runner.waitForIdleSync();SystemClock.sleep(400);
        runner.runOnMainSync(()->{if(!scale[0].isShown())throw new AssertionError("Picture spinner is hidden");scale[0].performClick();});
        awaitText(text("ui_whole_screen_fit"));awaitText(text("ui_auto_crop_fit"));awaitText(text("ui_cinema_mode"));capture("03-phone-crop-options");back();
        // A real role selector proves both role labels in each language.
        Activity role=launch("com.mooncast.unified.RoleSettingsActivity");awaitText(text("unified_role_phone"));awaitText(text("unified_role_tv"));capture("04-role-choices");runner.runOnMainSync(role::finish);
    }
    private void tvScreens()throws Exception {
        AppLocale.select(target,language);idlePhone();verifyResources();
        // Cross-process TV UI is observed through UiAutomation, never an in-process mock.
        target.startActivity(new Intent().setClassName(target.getPackageName(),TV).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        awaitText(text("home_framing"));awaitText(text("home_advanced"));awaitText(text("gs_connect_title"));capture("01-tv-home");
        clickText(text("home_framing"));awaitText(text("fit"));awaitText(text("cinema"));awaitText(text("manual"));capture("02-tv-crop-settings");back();
        clickText(text("home_advanced"));awaitText(text("tv_device_mode"));awaitText(text("tv_language"));capture("03-tv-advanced");
        // Use the real in-place TV language menu. Cancel/reselect must keep receiving.
        clickText(text("tv_language"));awaitText(text("locale_follow_system"));capture("04-tv-language-entry");back();awaitText(text("home_stop"));
        clickText(text("home_advanced"));clickText(text("tv_language"));clickText("zh".equals(language)?"English":"简体中文");
        awaitText(text("locale_stop_notice"));back();awaitText(text("home_stop"));
        for(int i=0;i<2;i++){
            clickText(text("home_advanced"));clickText(text("tv_language"));clickText("zh".equals(language)?"简体中文":"English");awaitText(text("home_stop"));
        }
        check("TV cancel/reselect preserves the selected language",language.equals(AppLocale.selected(target)));
        check("TV cancel/reselect does not enqueue cleanup",!roles().getBoolean("pendingCleanup",false));
        report.put("tvScope","Real receiver home/settings in the separate :receiver process. Receiving remains requested through in-place language Cancel/reselect. No media is sent and no decoder/streaming claim is made.");
    }
    private interface Condition{boolean get()throws Exception;}
    private void await(String label,Condition condition,long timeout)throws Exception{
        long deadline=SystemClock.uptimeMillis()+timeout;while(SystemClock.uptimeMillis()<deadline){if(condition.get()){check(label,true);return;}SystemClock.sleep(200);}check(label,false);
    }
    private void check(String label,boolean passed)throws Exception{checks.put(new JSONObject().put("check",label).put("passed",passed));if(!passed)throw new AssertionError(label);}
    private boolean hasText(String wanted){
        AccessibilityNodeInfo root=runner.getUiAutomation().getRootInActiveWindow();if(root==null)return false;
        for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(wanted))if(node.isVisibleToUser()&&(wanted.contentEquals(node.getText()==null?"":node.getText())||String.valueOf(node.getContentDescription()).contains(wanted)))return true;
        return false;
    }
    private void awaitText(String value)throws Exception{await("Visible UI: "+value,()->hasText(value),20000);}
    private void clickText(String wanted)throws Exception{
        awaitText(wanted);AccessibilityNodeInfo root=runner.getUiAutomation().getRootInActiveWindow();
        for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(wanted)){
            if(!node.isVisibleToUser())continue;
            AccessibilityNodeInfo current=node;while(current!=null){if(current.isClickable()&&current.performAction(AccessibilityNodeInfo.ACTION_CLICK)){runner.waitForIdleSync();SystemClock.sleep(350);return;}current=current.getParent();}
        }throw new AssertionError("Cannot click visible text "+wanted);
    }
    private void back(){runner.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);runner.waitForIdleSync();SystemClock.sleep(300);}
    private void capture(String name)throws Exception{
        runner.waitForIdleSync();SystemClock.sleep(600);Bitmap bitmap=runner.getUiAutomation().takeScreenshot();
        if(bitmap==null||bitmap.getWidth()<100||bitmap.getHeight()<100)throw new AssertionError("Screenshot unavailable: "+name);
        String filename=phase+"-"+language+"-"+name+"-debug-emulator.png";
        try(FileOutputStream out=new FileOutputStream(new File(directory,filename))){if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new AssertionError("Screenshot encoding failed");}finally{bitmap.recycle();}
        screenshots.put(new JSONObject().put("file",filename).put("source","UiAutomation.takeScreenshot; actual debug APK emulator display; unmodified pixels"));
    }
}
