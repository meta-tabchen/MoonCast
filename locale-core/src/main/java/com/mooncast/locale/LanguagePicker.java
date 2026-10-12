package com.mooncast.locale;

import android.app.Activity;
import android.app.AlertDialog;
import java.util.function.Consumer;

/** In-place choice: cancel and selecting the current language never stop the session. */
public final class LanguagePicker {
 private LanguagePicker() {}
 public static void show(Activity activity, Consumer<String> accepted) {
  show(activity,accepted,()->{});
 }
 public static void show(Activity activity, Consumer<String> accepted, Runnable unchanged) {
  String selected=AppLocale.selected(activity);
  String[] labels={activity.getString(R.string.locale_follow_system),"English","简体中文"};
  new AlertDialog.Builder(activity).setTitle(R.string.locale_picker_title)
   .setSingleChoiceItems(labels,"en".equals(selected)?1:"zh".equals(selected)?2:0,(dialog,index)->{
    dialog.dismiss();String choice=index==1?"en":index==2?"zh":"system";
    if(choice.equals(selected)){unchanged.run();return;}
    new AlertDialog.Builder(activity).setTitle(R.string.locale_picker_title)
     .setMessage(R.string.locale_stop_notice)
     .setPositiveButton(android.R.string.ok,(confirm,which)->accepted.accept(choice))
     .setNegativeButton(android.R.string.cancel,(cancel,which)->unchanged.run()).setOnCancelListener(cancel->unchanged.run()).show();
   }).setNegativeButton(android.R.string.cancel,(cancel,which)->unchanged.run()).setOnCancelListener(cancel->unchanged.run()).show();
 }
}
