package com.mooncast.host;

public final class MoonCastApplication extends android.app.Application {
    // The provider marks its own process in attachInfo; other processes start false.
    static { rikka.shizuku.ShizukuProvider.enableMultiProcessSupport(false); }
    @Override public void onCreate(){super.onCreate();rikka.shizuku.ShizukuProvider.requestBinderForNonProviderProcess(this);}
}
