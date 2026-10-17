package com.mooncast.unified;
import android.app.Activity;import android.os.Bundle;import android.graphics.*;import android.view.*;import java.util.concurrent.atomic.AtomicInteger;
/** Synthetic foreground app content, not Douyin or physical-phone acceptance. */
public final class DimFixtureActivity extends Activity {
 public static final AtomicInteger touches=new AtomicInteger();public static volatile boolean resumed;
 @Override public void onCreate(Bundle b){super.onCreate(b);touches.set(0);WindowManager.LayoutParams p=getWindow().getAttributes();p.screenBrightness=.7f;getWindow().setAttributes(p);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);setContentView(new View(this){@Override protected void onDraw(Canvas c){c.drawColor(Color.RED);Paint p=new Paint();p.setColor(Color.BLUE);c.drawRect(getWidth()/2f,0,getWidth(),getHeight(),p);}@Override public boolean onTouchEvent(MotionEvent e){touches.incrementAndGet();return true;}});}
 @Override protected void onResume(){super.onResume();resumed=true;}@Override protected void onPause(){resumed=false;super.onPause();}
}
