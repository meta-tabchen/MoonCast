package com.mooncast.host;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.*;
import android.view.View;

/** Deterministic visual test content; has no network, storage, or external app dependencies. */
public final class TestPatternActivity extends Activity {
    @Override public void onCreate(Bundle b){super.onCreate(b);setContentView(new Pattern());}
    private final class Pattern extends View {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        final long start=android.os.SystemClock.elapsedRealtime();
        Pattern(){super(TestPatternActivity.this);setKeepScreenOn(true);}
        @Override protected void onDraw(Canvas c){
            int w=getWidth(),h=getHeight();c.drawColor(Color.rgb(18,30,53));
            p.setColor(Color.WHITE);p.setTextSize(w/18f);c.drawText("MOONCAST / LIVE",w*.07f,h*.14f,p);
            long ms=android.os.SystemClock.elapsedRealtime()-start;p.setTypeface(Typeface.MONOSPACE);p.setTextSize(w/10f);c.drawText(String.format(java.util.Locale.ROOT,"%06.2f s",ms/1000.0),w*.07f,h*.25f,p);
            p.setTextSize(w/32f);c.drawText(getString(R.string.ui_aa_text_clarity_0123456789),w*.07f,h*.32f,p);
            int[] colors={Color.RED,Color.GREEN,Color.BLUE,Color.CYAN,Color.MAGENTA,Color.YELLOW};
            for(int i=0;i<6;i++){p.setColor(colors[i]);c.drawRect(w*.07f+i*w*.86f/6,h*.38f,w*.07f+(i+1)*w*.86f/6,h*.48f,p);}
            for(int i=0;i<256;i++){p.setColor(Color.rgb(i,i,i));c.drawRect(w*.07f+i*w*.86f/256,h*.50f,w*.07f+(i+1)*w*.86f/256+1,h*.60f,p);}
            p.setColor(Color.WHITE);for(int i=0;i<80;i++)c.drawRect(w*.07f+i*4,h*.63f,w*.07f+i*4+1,h*.73f,p);
            p.setColor(Color.rgb(72,159,255));float x=w*.07f+(ms%3000)/3000f*w*.86f;c.drawCircle(x,h*.84f,w*.025f,p);
            p.setColor(Color.WHITE);p.setTextSize(w/40f);c.drawText(getString(R.string.ui_compare_timers_to_estimate_latency_back_to_exit),w*.07f,h*.94f,p);postInvalidateOnAnimation();
        }
    }
}
