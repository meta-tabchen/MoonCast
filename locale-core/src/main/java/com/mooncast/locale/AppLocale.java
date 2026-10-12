package com.mooncast.locale;

import android.app.LocaleManager;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.LocaleList;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** OS app-language support on Android 13+, atomic process-independent fallback on 8–12. */
public final class AppLocale {
 private AppLocale() {}
 private static AtomicFile file(Context c) { return new AtomicFile(new File(c.getFilesDir(), "app-language")); }
 public static String selected(Context c) {
  if(Build.VERSION.SDK_INT>=33) {
   LocaleManager manager=c.getSystemService(LocaleManager.class);
   LocaleList locales=manager==null?LocaleList.getEmptyLocaleList():manager.getApplicationLocales();
   return locales.isEmpty()?"system":LocalePolicy.valid(locales.get(0).getLanguage());
  }
  try { byte[] bytes=file(c).readFully();return bytes.length<=16?LocalePolicy.valid(new String(bytes,StandardCharsets.US_ASCII)):"system"; }
  catch(IOException|RuntimeException ignored) {return "system";}
 }
 /** Caller must stop active capture/playback before changing language. Does not kill processes. */
 public static void select(Context c,String choice) throws IOException {
  String value=LocalePolicy.valid(choice);
  if(Build.VERSION.SDK_INT>=33) {
   LocaleManager manager=c.getSystemService(LocaleManager.class);
   if(manager==null)throw new IOException("Android app language service unavailable");
   manager.setApplicationLocales("system".equals(value)?LocaleList.getEmptyLocaleList():LocaleList.forLanguageTags(value));
  } else {
   AtomicFile target=file(c);FileOutputStream stream=null;
   try {stream=target.startWrite();stream.write(value.getBytes(StandardCharsets.US_ASCII));target.finishWrite(stream);}
   catch(IOException e){target.failWrite(stream);throw e;}
  }
 }
 public static Context wrap(Context base) {
  if(Build.VERSION.SDK_INT>=33)return base; // Follow the OS per-app setting, including changes in Settings.
  String value=selected(base);
  if("system".equals(value))return base;
  Configuration configuration=new Configuration(base.getResources().getConfiguration());
  configuration.setLocales(new LocaleList(Locale.forLanguageTag(value)));
  configuration.setLayoutDirection(Locale.forLanguageTag(value));
  return base.createConfigurationContext(configuration);
 }
}
