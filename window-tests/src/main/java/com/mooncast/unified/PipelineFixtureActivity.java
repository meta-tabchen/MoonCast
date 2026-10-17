package com.mooncast.unified;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.view.TextureView;
import android.view.WindowManager;
import java.util.concurrent.atomic.AtomicInteger;

/** Generated-media consumer only. This Activity never captures another application. */
public final class PipelineFixtureActivity extends Activity {
 public TextureView texture;
 public final AtomicInteger frames=new AtomicInteger();
 @Override public void onCreate(Bundle state){
  super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  texture=new TextureView(this);texture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener(){
   public void onSurfaceTextureAvailable(SurfaceTexture t,int w,int h){}
   public void onSurfaceTextureSizeChanged(SurfaceTexture t,int w,int h){}
   public boolean onSurfaceTextureDestroyed(SurfaceTexture t){return true;}
   public void onSurfaceTextureUpdated(SurfaceTexture t){frames.incrementAndGet();}
  });setContentView(texture);
 }
}
