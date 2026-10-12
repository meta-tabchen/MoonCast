package com.mooncast.host;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.net.nsd.NsdServiceInfo;
import android.view.View;
import android.widget.EditText;
import java.net.InetAddress;

/** Generated-device tests only. Never initiates a cast, upload or installation. */
final class MyTvChecks {
    static void verify(Instrumentation test) throws Exception {
        Context context = test.getTargetContext();
        NsdServiceInfo air = new NsdServiceInfo(); air.setServiceName("Generated native TV");
        air.setServiceType("_airplay._tcp."); air.setHost(InetAddress.getByName("192.168.1.71")); air.setPort(7100);
        MyTvDevice nativeTv = MyTvDiscovery.fromService(air);
        check(nativeTv.supportsAirplay() && !nativeTv.supportsApk(), "native AirPlay does not gain APK capability");
        NsdServiceInfo apk = new NsdServiceInfo(); apk.setServiceName("Generated receiver");
        apk.setServiceType("_mooncast-apk._tcp."); apk.setHost(air.getHost()); apk.setPort(48152);
        apk.setAttribute("v", "1"); apk.setAttribute("apk", "1"); apk.setAttribute("pairing", "none");
        check(MyTvDiscovery.fromService(apk) == null, "old APK protocol excluded");
        apk.setAttribute("v", "2"); apk.setAttribute("id", "00000000-0000-4000-8000-000000000001");
        MyTvDevice receiver = MyTvDiscovery.fromService(apk);
        check(receiver.supportsApk(), "v2 APK capability admitted");
        android.content.SharedPreferences prefs = context.getSharedPreferences("my_tv", Context.MODE_PRIVATE);
        java.util.Map<String, ?> previous = new java.util.HashMap<>(prefs.getAll());
        Activity activity = test.startActivitySync(new Intent(context, ApkSendActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            MyTvStore.remember(context, nativeTv.merge(receiver));
            MyTvDevice remembered = MyTvStore.last(context);
            check(remembered.host.equals(receiver.host) && remembered.airplayPort == 7100, "one shared last TV remembers cast address");
            check(!remembered.supportsApk(), "remembered APK permission requires fresh discovery/probe");
            test.runOnMainSync(() -> {
                MyTvPicker picker = new MyTvPicker(activity, true, device -> { throw new AssertionError("No selection expected"); });
                picker.show();
                check(picker.isShowing(), "shared APK picker visible");
                check((picker.getWindow().getAttributes().gravity & android.view.Gravity.BOTTOM)==android.view.Gravity.BOTTOM,"shared selector is a bottom sheet");
                View content = picker.getWindow().getDecorView();
                content.findViewWithTag("my_tv_manual").performClick();
                EditText ip = content.findViewWithTag("my_tv_ip"); ip.setText("8.8.8.8");
                content.findViewWithTag("my_tv_check").performClick();
                check(ip.getError() != null, "public IP rejected before probe");
                picker.dismiss(); picker.show(); check(picker.isShowing(), "dismiss/reopen succeeds"); picker.dismiss();
            });
        } finally {
            test.runOnMainSync(activity::finish);
            android.content.SharedPreferences.Editor edit = prefs.edit().clear();
            for (java.util.Map.Entry<String, ?> e : previous.entrySet()) {
                Object v=e.getValue(); if(v instanceof String)edit.putString(e.getKey(),(String)v);
                else if(v instanceof Integer)edit.putInt(e.getKey(),(Integer)v);
                else if(v instanceof Boolean)edit.putBoolean(e.getKey(),(Boolean)v);
            }
            edit.commit();
        }
    }
    private static void check(boolean pass, String message) { if (!pass) throw new AssertionError(message); }
}
