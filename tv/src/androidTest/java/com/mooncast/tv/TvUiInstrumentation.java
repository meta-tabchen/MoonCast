package com.mooncast.tv;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;

/** Keep startup minimal; resolve the complete driver only on the worker thread. */
public final class TvUiInstrumentation extends Instrumentation {
    private Bundle arguments;
    @Override public void onCreate(Bundle args) { super.onCreate(args); arguments=args; start(); }
    @Override public void onStart() {
        try {
            String suite = arguments == null ? "" : arguments.getString("suite", "");
            Class<?> type=Class.forName("apk-receive".equals(suite) ? "com.mooncast.tv.ApkReceiveDeviceTest" : "gamestream-renderer".equals(suite)
                    ? "com.mooncast.tv.GameStreamRendererDeviceTest" : "com.mooncast.tv.TvUiTestDriver");
            java.lang.reflect.Constructor<?> constructor=type.getDeclaredConstructor(Instrumentation.class,Bundle.class);
            constructor.setAccessible(true);Object driver=constructor.newInstance(this,arguments);
            java.lang.reflect.Method run=type.getDeclaredMethod("run");run.setAccessible(true);run.invoke(driver);
        } catch(Throwable failure) {
            Bundle result=new Bundle();result.putString("stream","TV_UI_VALIDATION_FAILED during driver startup\n"+android.util.Log.getStackTraceString(failure));
            finish(Activity.RESULT_CANCELED,result);
        }
    }
}
