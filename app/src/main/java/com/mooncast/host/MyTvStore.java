package com.mooncast.host;

import android.content.Context;
import android.content.SharedPreferences;

/** One shared hint for both workflows. Stored capabilities are never treated as live APK evidence. */
final class MyTvStore {
    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("my_tv", Context.MODE_PRIVATE); }
    static void remember(Context context, MyTvDevice device) {
        prefs(context).edit().clear().putString("host", device.host).putString("name", device.name)
            .putInt("airplay_port", device.airplayPort).putString("identity", device.identity)
            .putString("airplay_identity", device.airplayIdentity).apply();
    }
    static MyTvDevice last(Context context) {
        SharedPreferences p = prefs(context);
        String host = p.getString("host", "");
        if (!AirPlayEndpoint.localNumericHost(host)) return null;
        return new MyTvDevice(host, p.getString("name", host), p.getInt("airplay_port", 0), 0,
            p.getString("identity", ""), p.getString("airplay_identity", ""), true);
    }
}
