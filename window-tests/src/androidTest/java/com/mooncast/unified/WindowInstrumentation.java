package com.mooncast.unified;
import android.app.Instrumentation;import android.os.Bundle;
public final class WindowInstrumentation extends Instrumentation {private Bundle args;@Override public void onCreate(Bundle b){super.onCreate(b);args=b;start();}@Override public void onStart(){if("pipeline".equals(args.getString("suite")))new PipelinePauseDeviceTest(this).run();else if("controls".equals(args.getString("suite")))new ControlsLayoutDeviceTest(this).run();else new ScreenDimDeviceTest(this,args).run();}}
