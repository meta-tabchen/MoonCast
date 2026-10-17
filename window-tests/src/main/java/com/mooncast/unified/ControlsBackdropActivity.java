package com.mooncast.unified;
/** Synthetic foreground content. No real third-party application is driven. */
public final class ControlsBackdropActivity extends android.app.Activity {
 @Override public void onCreate(android.os.Bundle state){super.onCreate(state);getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);android.view.View content=new android.view.View(this);content.setBackgroundColor(android.graphics.Color.rgb(30,70,110));setContentView(content);}
}
