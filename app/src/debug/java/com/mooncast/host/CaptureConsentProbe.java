package com.mooncast.host;

import android.app.*;
import android.content.*;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;

/** Debug-only normal system consent for generated-content capture integration. */
public final class CaptureConsentProbe extends Activity {
    public static volatile Intent grant;
    @Override public void onCreate(Bundle saved){super.onCreate(saved);getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);grant=null;startActivityForResult(getSystemService(MediaProjectionManager.class).createScreenCaptureIntent(),1);}
    @Override public void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result==RESULT_OK)grant=data;finish();}
}
