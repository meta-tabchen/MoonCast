package com.mooncast.host;

public final class MoonCastApplication extends android.app.Application {
    @Override public void onCreate(){super.onCreate();rikka.shizuku.ShizukuProvider.enableMultiProcessSupport(true);}
}
