package com.mooncast.host;
import android.content.Context; import android.content.res.Resources; import java.util.*;
/** Tests production rendering with resource/JSON boundary adapters, not Android runtime behavior. */
public class PhoneMessagesTest {
 static int checks;
 static final String[] names=PhoneMessageResources.NAMES;
 static final String[][] strings=PhoneMessageResources.TEXT;
 static Context context(int lang){return new Context(){ public Resources getResources(){return new Resources(){public String getResourceEntryName(int id){if(id<1||id>=names.length)throw new NotFoundException();return names[id];}};}
 public String getString(int id,Object... args){return String.format(lang==0?Locale.ENGLISH:Locale.SIMPLIFIED_CHINESE,strings[lang][id],args);} };}
 static void check(boolean condition){checks++;if(!condition)throw new AssertionError("check "+checks);}
 static void equal(String a,String b){checks++;if(!a.equals(b))throw new AssertionError(a+" != "+b);}
 public static void main(String[] args){
  Context en=context(0),zh=context(1);
  String frame=PhoneMessages.message(R.string.phone_gpu_capture,1920,1080,2);
  equal(PhoneMessages.render(en,frame),"GPU capture 1920×1080 → 2 encoder output(s)");
  equal(PhoneMessages.render(zh,frame),"GPU 捕获 1920×1080 → 2 路编码输出");
  String nested=PhoneMessages.message(R.string.phone_root_input_unavailable,PhoneMessages.message(R.string.phone_unlock_phone));
  equal(PhoneMessages.render(en,nested),"Root input unavailable: Unlock and wake the phone first");
  equal(PhoneMessages.render(zh,"Exception: "+nested),"Exception: Root 远程操控不可用：请先解锁并唤醒手机");
  equal(PhoneMessages.render(en,PhoneMessages.message(R.string.phone_cinema_locked,100,56)),"Cinema region locked: 100% × 56%");
  equal(PhoneMessages.render(en,PhoneMessages.message(R.string.phone_input_disabled_error,(Object)null)),"Remote input disabled: ");
  check(PhoneMessages.is(PhoneMessages.message(R.string.phone_input_disabled_error,"failure"),R.string.phone_input_disabled_error));
  check(!PhoneMessages.is("Remote input disabled",R.string.phone_input_disabled_error));
  equal(PhoneMessages.render(en,"系统未允许修改媒体音量"),"The system did not allow media volume changes");
  equal(PhoneMessages.render(zh,"untouched external error"),"untouched external error");
  equal(PhoneMessages.render(en,"\u001e9999:[]"),"Diagnostic details unavailable");
  equal(PhoneMessages.render(en,PhoneMessages.message(99999)),"Diagnostic details unavailable");
  equal(PhoneMessages.render(en,PhoneMessages.message(R.string.phone_gpu_capture,"wrong")),"Diagnostic details unavailable");
  check(PhoneMessages.isApkParserDiagnostic("Invalid APK manifest"));
  check(!PhoneMessages.isApkParserDiagnostic("TV rejected transfer"));
  equal(PhoneMessages.render(en,frame+" | "+frame),"GPU capture 1920×1080 → 2 encoder output(s) | GPU capture 1920×1080 → 2 encoder output(s)");
  String escaped="TV \"name\" \\ \n 中文";
  equal(PhoneMessages.render(en,PhoneMessages.message(R.string.phone_input_disabled_error,escaped)),"Remote input disabled: "+escaped);
  equal(PhoneMessages.render(en,PhoneMessages.message(R.string.phone_airplay_metrics,12L,59.5,"video only")),"12 frames · 59.5 fps · H.264 / video only");
  equal(PhoneMessages.render(zh,PhoneMessages.message(R.string.phone_airplay_metrics,12L,59.5,"仅视频")),"12 帧 · 59.5 帧/秒 · H.264 / 仅视频");
  check(!PhoneMessages.is(PhoneMessages.message(R.string.phone_input_disabled_error,"failure")+"tail",R.string.phone_input_disabled_error));
  System.out.println("PASS: "+checks+" phone message codec/translation/format/routing/invalid-input checks (host Android/JSON adapters; no Android runtime claim)");
 }
}
