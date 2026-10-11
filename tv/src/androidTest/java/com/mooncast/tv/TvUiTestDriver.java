package com.mooncast.tv;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import com.mooncast.tv.airplay.AirPlayEngine;
import com.mooncast.tv.net.DlnaReceiver;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONArray;
import org.json.JSONObject;

/** Real APK UI regression harness. Only packaged in the separate androidTest APK. */
final class TvUiTestDriver {
    private final Instrumentation runner;
    private Bundle arguments;
    private TvActivity activity;
    private File directory;
    private final JSONArray checks = new JSONArray();
    private final JSONObject report = new JSONObject();
    private String prefix;
    private boolean offline;
    TvUiTestDriver(Instrumentation runner,Bundle args){this.runner=runner;arguments=args;}
    void run() {
        Bundle result=new Bundle();
        try {
            prefix=arguments.getString("label","en"); offline="true".equals(arguments.getString("offline","false"));
            if(!prefix.matches("[a-zA-Z0-9_-]+"))throw new IllegalArgumentException("Invalid label");
            directory=new File(runner.getTargetContext().getExternalFilesDir(null),"visual-validation");
            if(!directory.isDirectory()&&!directory.mkdirs())throw new IllegalStateException("Cannot create screenshot directory");
            report.put("scope","Actual APK on Android; generated media fixture only; no physical-device or AirPlay interoperability claim");
            report.put("mode",offline?"offline UI with explicitly simulated session state":"LAN listener and optional decoded fixture");
            report.put("language",prefix); report.put("sdk",android.os.Build.VERSION.SDK_INT);
            report.put("model",android.os.Build.MODEL); report.put("checks",checks);
            launch();
            report.put("actualLocales",activity.getResources().getConfiguration().getLocales().toLanguageTags());
            check("Requested language is active",activity.getResources().getConfiguration().getLocales().get(0).getLanguage().equals(java.util.Locale.forLanguageTag(prefix).getLanguage()));
            await("Activity automatically requested receiver startup",()->bool("requested"),30000);
            if(!offline)await("DLNA listener started without clicking Start",()->((DlnaReceiver)field("dlna"))!=null&&((DlnaReceiver)field("dlna")).isRunning(),60000);
            else report.put("receiverListening","not tested: offline mode does not require a LAN");
            await("Actual Activity window has focus",()->activity.hasWindowFocus(),30000);
            check("AirPlay engine is configured with PIN disabled",!((AirPlayEngine)field("airplay")).getRuntimeStats().pinRequired);
            check("Fresh install defaults to automatic black-bar removal",framing("mirrorFraming").mode()==FramingSettings.CINEMA);
            check("Idle home starts focused on Framing",focus().contains(text(R.string.home_framing)));
            SystemClock.sleep(2000); capture("01-home");
            String first=focus(); key(KeyEvent.KEYCODE_DPAD_RIGHT);
            check("D-pad Right moves from Framing to Settings",!first.equals(focus())&&focus().contains(text(R.string.home_advanced)));
            key(KeyEvent.KEYCODE_DPAD_CENTER); awaitText(text(R.string.advanced_title)); capture("02-advanced");
            key(KeyEvent.KEYCODE_BACK); check("Back dismisses advanced settings without stopping receiver",bool("requested"));
            key(KeyEvent.KEYCODE_DPAD_LEFT); key(KeyEvent.KEYCODE_DPAD_CENTER); awaitText(text(R.string.manual)); capture("03-framing");
            clickText(text(R.string.manual)); await("Manual editor opened",()->editor().isEditing(),10000);
            int initialZoom=framing("mirrorFraming").settings().zoom();
            key(KeyEvent.KEYCODE_DPAD_RIGHT); key(KeyEvent.KEYCODE_DPAD_RIGHT); key(KeyEvent.KEYCODE_DPAD_RIGHT);
            check("D-pad changes live crop zoom",framing("mirrorFraming").settings().zoom()==initialZoom+3);
            capture("04-editor"); key(KeyEvent.KEYCODE_BACK);
            check("Back cancels crop preview",!editor().isEditing()&&framing("mirrorFraming").mode()==FramingSettings.CINEMA&&framing("mirrorFraming").settings().zoom()==initialZoom);
            openFraming(); clickText(text(R.string.fill));
            await("Framing choice is shared by both pipelines",()->framing("mirrorFraming").mode()==FramingSettings.FILL&&framing("mediaFraming").mode()==FramingSettings.FILL,10000);
            runner.runOnMainSync(()->activity.finish()); runner.waitForIdleSync(); launch();
            await("Receiver auto-starts again after reopening",()->bool("requested"),30000);
            check("App-wide framing choice survives Activity restart",framing("mirrorFraming").mode()==FramingSettings.FILL&&framing("mediaFraming").mode()==FramingSettings.FILL);
            capture("10-saved-framing-choice");
            openFraming(); clickText(text(R.string.cinema));
            String fixture=arguments.getString("fixtureUrl","");
            if(offline) simulatedPlaybackChecks();
            else if(!fixture.isEmpty()) playbackChecks(fixture);
            else report.put("liveMedia","not run: no fixtureUrl provided");
            report.put("passed",true); writeReport(); result.putString("stream",(offline?"TV_UI_OFFLINE_VALIDATION_PASSED ":"TV_UI_VALIDATION_PASSED ")+directory.getAbsolutePath()+"\n");
            runner.finish(Activity.RESULT_OK,result);
        } catch(Throwable failure) {
            try { report.put("passed",false);report.put("failure",android.util.Log.getStackTraceString(failure));if(directory!=null){capture("failure");writeReport();} }catch(Throwable ignored){}
            result.putString("stream","TV_UI_VALIDATION_FAILED\n"+android.util.Log.getStackTraceString(failure));runner.finish(Activity.RESULT_CANCELED,result);
        }
    }
    private void simulatedPlaybackChecks() throws Exception {
        report.put("liveMedia","NOT RUN: no decoding, AirPlay session or DLNA playback. Test-only reflection marks playingMirror=true solely to exercise navigation.");
        runner.runOnMainSync(()->setField("playingMirror",true));
        key(KeyEvent.KEYCODE_MENU);key(KeyEvent.KEYCODE_BACK);
        check("Simulated session: Back from home hides controls",panel().getVisibility()==View.GONE);
        key(KeyEvent.KEYCODE_BACK);
        check("Simulated session: Back opens home without stopping receiver",panel().getVisibility()==View.VISIBLE&&bool("requested"));
        check("Simulated session: Return to playback has default focus",focus().contains(text(R.string.home_return_playback)));
        capture("06-simulated-session-home");
        key(KeyEvent.KEYCODE_BACK);check("Simulated session: second Back returns without exit",panel().getVisibility()==View.GONE);
        key(KeyEvent.KEYCODE_BACK);key(KeyEvent.KEYCODE_DPAD_LEFT);key(KeyEvent.KEYCODE_DPAD_LEFT);key(KeyEvent.KEYCODE_DPAD_CENTER);
        awaitText(text(R.string.manual));clickText(text(R.string.manual));await("Simulated session crop editor opened",()->editor().isEditing(),10000);
        key(KeyEvent.KEYCODE_DPAD_RIGHT);capture("07-simulated-session-editor");
        key(KeyEvent.KEYCODE_BACK);check("Simulated session: crop Back cancels and hides controls",!editor().isEditing()&&panel().getVisibility()==View.GONE&&framing("mirrorFraming").mode()==FramingSettings.CINEMA);
        key(KeyEvent.KEYCODE_BACK);key(KeyEvent.KEYCODE_DPAD_LEFT);key(KeyEvent.KEYCODE_DPAD_CENTER);awaitText(text(R.string.advanced_title));
        key(KeyEvent.KEYCODE_BACK);check("Simulated session: nested Back preserves home",panel().getVisibility()==View.VISIBLE&&bool("requested"));
        key(KeyEvent.KEYCODE_BACK);check("Simulated session: home Back hides controls",panel().getVisibility()==View.GONE);
        runner.runOnMainSync(()->setField("playingMirror",false));key(KeyEvent.KEYCODE_MENU);key(KeyEvent.KEYCODE_BACK);
        awaitText(text(R.string.exit_title));capture("09-idle-exit-confirmation");key(KeyEvent.KEYCODE_BACK);
        check("Idle exit confirmation Back keeps Activity and receiver request",!activity.isFinishing()&&bool("requested"));
    }
    private void setField(String name,Object value){try{Field f=TvActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(activity,value);}catch(Exception e){throw new IllegalStateException(e);}}
    private void playbackChecks(String fixture) throws Exception {
        await("DLNA listener available for generated fixture",()->field("dlna")!=null&&((DlnaReceiver)field("dlna")).isRunning(),60000);
        soap("SetAVTransportURI","<CurrentURI>"+escape(fixture)+"</CurrentURI><CurrentURIMetaData></CurrentURIMetaData>");
        soap("Play","<Speed>1</Speed>");
        await("Actual Media3 decoder rendered generated fixture",()->bool("mediaHasFrame"),90000);
        check("First decoded playback hides home controls",panel().getVisibility()==View.GONE);
        SystemClock.sleep(1500); capture("05-playback");
        key(KeyEvent.KEYCODE_BACK);
        check("Back from playback opens home and keeps receiver active",panel().getVisibility()==View.VISIBLE&&bool("requested"));
        check("Return to playback has default focus",focus().contains(text(R.string.home_return_playback)));capture("06-playback-home");
        key(KeyEvent.KEYCODE_BACK);check("Back from playback home returns to video",panel().getVisibility()==View.GONE&&bool("mediaHasFrame"));
        key(KeyEvent.KEYCODE_BACK);key(KeyEvent.KEYCODE_DPAD_LEFT);key(KeyEvent.KEYCODE_DPAD_LEFT);key(KeyEvent.KEYCODE_DPAD_CENTER);
        awaitText(text(R.string.manual));clickText(text(R.string.manual));await("Live media crop editor opened",()->editor().isEditing(),10000);
        key(KeyEvent.KEYCODE_DPAD_RIGHT);key(KeyEvent.KEYCODE_DPAD_RIGHT);capture("07-live-editor");
        key(KeyEvent.KEYCODE_BACK);check("Back cancels live crop and returns to video",!editor().isEditing()&&panel().getVisibility()==View.GONE&&framing("mediaFraming").mode()==FramingSettings.CINEMA);
        key(KeyEvent.KEYCODE_BACK);key(KeyEvent.KEYCODE_DPAD_LEFT);key(KeyEvent.KEYCODE_DPAD_CENTER);awaitText(text(R.string.advanced_title));capture("08-live-advanced");
        key(KeyEvent.KEYCODE_BACK);check("Back dismisses nested settings while playback remains",panel().getVisibility()==View.VISIBLE&&bool("mediaHasFrame"));
        key(KeyEvent.KEYCODE_BACK);check("Second Back returns to playback without exit dialog",panel().getVisibility()==View.GONE&&bool("requested"));
        soap("Stop","");await("Generated playback stopped",()->!bool("mediaHasFrame"),10000);
        key(KeyEvent.KEYCODE_BACK);awaitText(text(R.string.exit_title));capture("09-idle-exit-confirmation");
        key(KeyEvent.KEYCODE_BACK);check("Back cancels idle exit confirmation",!activity.isFinishing()&&bool("requested"));
        report.put("liveMedia","Generated letterbox H.264 MP4 via actual local DLNA SetAVTransportURI/Play/Stop and Media3 decoder; no remote sender or discovery claim");
    }
    private void launch(){runner.setInTouchMode(false);Intent i=new Intent(runner.getTargetContext(),TvActivity.class);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);activity=(TvActivity)runner.startActivitySync(i);runner.waitForIdleSync();SystemClock.sleep(800);}
    private void openFraming()throws Exception{key(KeyEvent.KEYCODE_MENU);key(KeyEvent.KEYCODE_DPAD_CENTER);awaitText(text(R.string.manual));}
    private void key(int key){runner.sendKeyDownUpSync(key);runner.waitForIdleSync();SystemClock.sleep(250);}
    private String text(int id){return activity.getString(id);}
    private String focus(){String[] out={""};runner.runOnMainSync(()->{View v=activity.getCurrentFocus();out[0]=v==null?"none":String.valueOf(v.getContentDescription());});return out[0];}
    private View panel(){return (View)field("panel");}
    private CropEditorOverlay editor(){return (CropEditorOverlay)field("cropEditor");}
    private VideoFraming framing(String name){return (VideoFraming)field(name);}
    private boolean bool(String name){return Boolean.TRUE.equals(field(name));}
    private Object field(String name){try{Field f=TvActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}catch(Exception e){throw new IllegalStateException(e);}}
    private interface Condition{boolean get();}
    private void await(String description,Condition condition,long millis)throws Exception{long end=SystemClock.uptimeMillis()+millis;while(SystemClock.uptimeMillis()<end){AtomicBoolean ok=new AtomicBoolean();runner.runOnMainSync(()->ok.set(condition.get()));if(ok.get()){check(description,true);return;}SystemClock.sleep(250);}check(description,false);}
    private void check(String description,boolean passed)throws Exception{checks.put(new JSONObject().put("check",description).put("passed",passed));android.util.Log.i("MoonCastUiTest",(passed?"PASS ":"FAIL ")+description);if(!passed)throw new AssertionError(description);}
    private void awaitText(String value)throws Exception{long end=SystemClock.uptimeMillis()+20000;while(SystemClock.uptimeMillis()<end){if(hasText(value)){check("UI shows "+value,true);return;}SystemClock.sleep(250);}check("UI shows "+value,false);}
    private boolean hasText(String value){AccessibilityNodeInfo root=runner.getUiAutomation().getRootInActiveWindow();return root!=null&&!root.findAccessibilityNodeInfosByText(value).isEmpty();}
    private void clickText(String value)throws Exception{
        AccessibilityNodeInfo root=runner.getUiAutomation().getRootInActiveWindow();
        List<AccessibilityNodeInfo> found=root.findAccessibilityNodeInfosByText(value);
        for(AccessibilityNodeInfo node:found){AccessibilityNodeInfo current=node;while(current!=null){if(current.isClickable()&&current.performAction(AccessibilityNodeInfo.ACTION_CLICK)){runner.waitForIdleSync();SystemClock.sleep(300);return;}current=current.getParent();}}
        throw new AssertionError("Cannot select UI text: "+value);
    }
    private void capture(String name)throws Exception{SystemClock.sleep(500);Bitmap bitmap=runner.getUiAutomation().takeScreenshot();if(bitmap==null)throw new AssertionError("Screenshot unavailable");File path=new File(directory,prefix+"-"+name+".png");try(FileOutputStream stream=new FileOutputStream(path)){if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,stream))throw new AssertionError("Screenshot encoding failed");}bitmap.recycle();}
    private void writeReport()throws Exception{try(FileOutputStream stream=new FileOutputStream(new File(directory,prefix+"-report.json"))){stream.write(report.toString(2).getBytes(StandardCharsets.UTF_8));}}
    private void soap(String action,String arguments)throws Exception{
        String description=((DlnaReceiver)field("dlna")).getDescriptionUrl();URL base=new URL(description);
        HttpURLConnection connection=(HttpURLConnection)new URL(base,"/control/AVTransport").openConnection();
        connection.setConnectTimeout(15000);connection.setReadTimeout(30000);connection.setRequestMethod("POST");connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type","text/xml; charset=utf-8");connection.setRequestProperty("SOAPAction","\"urn:schemas-upnp-org:service:AVTransport:1#"+action+"\"");
        byte[] body=("<?xml version=\"1.0\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body><u:"+action+" xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\"><InstanceID>0</InstanceID>"+arguments+"</u:"+action+"></s:Body></s:Envelope>").getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(body.length);try(java.io.OutputStream out=connection.getOutputStream()){out.write(body);}int code=connection.getResponseCode();connection.disconnect();check("Local DLNA "+action+" returned HTTP 200",code==200);
    }
    private static String escape(String value){return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
}
