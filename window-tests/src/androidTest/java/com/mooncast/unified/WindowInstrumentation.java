package com.mooncast.unified;
import android.app.Instrumentation;import android.os.Bundle;
public final class WindowInstrumentation extends Instrumentation {private Bundle args;@Override public void onCreate(Bundle b){super.onCreate(b);args=b;start();}@Override public void onStart(){new ScreenDimDeviceTest(this,args).run();}}
