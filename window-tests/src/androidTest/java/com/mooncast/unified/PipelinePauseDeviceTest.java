package com.mooncast.unified;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.hardware.display.*;
import android.os.*;
import android.view.*;
import com.mooncast.host.CropGeometry;
import com.mooncast.host.VideoPipeline;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Production SurfaceTexture/GL pause path, with an isolated generated VirtualDisplay.
 * No MediaProjection, privileged screen capture, codec, transport or audio claim. */
public final class PipelinePauseDeviceTest {
 private final Instrumentation runner;
 private PipelineFixtureActivity activity;
 private VideoPipeline pipeline;
 private Surface output;
 private Presentation presentation;
 private Pattern source;
 private final AtomicReference<String> failure=new AtomicReference<>();
 private final CountDownLatch sourceReady=new CountDownLatch(1);
 private final StringBuilder evidence=new StringBuilder();
 private static final int WIDTH=160,HEIGHT=96;
 public PipelinePauseDeviceTest(Instrumentation runner){this.runner=runner;}
 private static final class Pattern extends View {
  int phase;final AtomicInteger draws=new AtomicInteger();
  Pattern(Context context){super(context);}
  @Override protected void onDraw(Canvas canvas){
   canvas.drawColor(phase==0?Color.RED:Color.BLUE);Paint paint=new Paint();paint.setColor(phase==0?Color.BLUE:Color.RED);
   canvas.drawRect(getWidth()/2f,0,getWidth(),getHeight(),paint);draws.incrementAndGet();
  }
 }
 public void run(){
  Bundle result=new Bundle();int code=Activity.RESULT_CANCELED;
  try{
   require(Build.FINGERPRINT.contains("generic")||Build.MODEL.contains("sdk"),"disposable emulator only");
   activity=(PipelineFixtureActivity)runner.startActivitySync(new Intent(runner.getTargetContext(),PipelineFixtureActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
   long end=SystemClock.elapsedRealtime()+15000;boolean[] ready={false};
   while(SystemClock.elapsedRealtime()<end){onMain(()->ready[0]=activity.texture.isAvailable());if(ready[0])break;SystemClock.sleep(100);}
   require(ready[0],"real TextureView ready");
   onMain(()->{activity.texture.getSurfaceTexture().setDefaultBufferSize(WIDTH,HEIGHT);output=new Surface(activity.texture.getSurfaceTexture());});
   pipeline=new VideoPipeline(runner.getTargetContext(),output,WIDTH,HEIGHT,CropGeometry.SCREEN,new VideoPipeline.Events(){
    public void message(String text){}public void error(String text){failure.compareAndSet(null,text);}
   });
   pipeline.start(WIDTH,HEIGHT,false,(input,width,height)->{
    VirtualDisplay display=runner.getTargetContext().getSystemService(DisplayManager.class).createVirtualDisplay("MoonCast-generated-only",width,height,160,input,DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY|DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION);
    if(display==null)throw new IllegalStateException("generated display not created");
    new Handler(Looper.getMainLooper()).post(()->{try{
     presentation=new Presentation(activity,display.getDisplay());source=new Pattern(presentation.getContext());presentation.setContentView(source);
     presentation.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);presentation.show();presentation.getWindow().setLayout(-1,-1);
    }catch(Throwable error){failure.compareAndSet(null,android.util.Log.getStackTraceString(error));}finally{sourceReady.countDown();}});
    return display;
   });
   require(sourceReady.await(15,TimeUnit.SECONDS),"generated presentation ready");waitPixels(0);
   pipeline.setPaused(true);barrier();int beforeFrames=activity.frames.get();int beforeDraws=source.draws.get();
   onMain(()->{source.phase=1;source.invalidate();});
   end=SystemClock.elapsedRealtime()+5000;while(source.draws.get()<=beforeDraws&&SystemClock.elapsedRealtime()<end){healthy();SystemClock.sleep(50);}
   require(source.draws.get()>beforeDraws,"new source pixels drawn while paused");
   end=SystemClock.elapsedRealtime()+2200;while(SystemClock.elapsedRealtime()<end){healthy();require(pixels(0),"pause holds actual old output pixels");SystemClock.sleep(100);}
   // Establish a late baseline after the original queued output has been consumed.
   barrier();beforeFrames=activity.frames.get();end=SystemClock.elapsedRealtime()+2200;
   while(SystemClock.elapsedRealtime()<end){healthy();require(pixels(0),"late paused output remains frozen");SystemClock.sleep(100);}
   require(activity.frames.get()>=beforeFrames+3,"periodic held-frame keepalive advances after warmup");barrier();
   evidence.append("pause: generated source changed, output held original pixels, late consumer frames advanced ").append(activity.frames.get()-beforeFrames).append(" over 2.2s (no exact FPS claim)\n");
   int staticDraws=source.draws.get();pipeline.setPaused(false);barrier();waitPixels(1);
   require(source.draws.get()==staticDraws,"resume acquired queued latest frame without another source redraw");
   evidence.append("resume: distinct current pixels with unchanged source draw count\n");
   pipeline.setPaused(true);barrier();pipeline.setPaused(false);barrier();waitPixels(1);
   evidence.append("repeated pause/resume: current pixels preserved\n");
   code=Activity.RESULT_OK;
  }catch(Throwable error){evidence.append(android.util.Log.getStackTraceString(error));}
  finally{
   try{if(presentation!=null)onMain(presentation::dismiss);}catch(Throwable error){code=Activity.RESULT_CANCELED;evidence.append("Presentation cleanup: ").append(error).append('\n');}
   boolean released=pipeline==null;
   try{if(pipeline!=null){pipeline.close();java.lang.reflect.Field field=VideoPipeline.class.getDeclaredField("thread");field.setAccessible(true);Thread gl=(Thread)field.get(pipeline);gl.join(15000);require(!gl.isAlive(),"GL worker terminated before consumer release");released=true;}}catch(Throwable error){code=Activity.RESULT_CANCELED;evidence.append("GL cleanup: ").append(error).append('\n');}
   if(released){if(output!=null)output.release();if(activity!=null)try{onMain(activity::finish);}catch(Throwable error){code=Activity.RESULT_CANCELED;evidence.append("Activity cleanup: ").append(error).append('\n');}}
   if(failure.get()!=null){code=Activity.RESULT_CANCELED;evidence.append("Pipeline error: ").append(failure.get()).append('\n');}
  }
  result.putString("stream",(code==Activity.RESULT_OK?"PIPELINE_PAUSE_DEVICE_PASSED\n":"PIPELINE_PAUSE_DEVICE_FAILED\n")+evidence+"Scope: generated-only VirtualDisplay -> production VideoPipeline -> TextureView pixels; no codec/network/capture/audio/device acceptance\n");runner.finish(code,result);
 }
 private void barrier()throws Exception{java.lang.reflect.Field field=VideoPipeline.class.getDeclaredField("handler");field.setAccessible(true);Handler handler=(Handler)field.get(pipeline);CountDownLatch complete=new CountDownLatch(1);require(handler!=null&&handler.post(complete::countDown)&&complete.await(10,TimeUnit.SECONDS),"GL command barrier");healthy();}
 private void waitPixels(int phase)throws Exception{long end=SystemClock.elapsedRealtime()+15000;while(SystemClock.elapsedRealtime()<end){healthy();if(pixels(phase))return;SystemClock.sleep(100);}throw new AssertionError("actual output missing expected generated phase="+phase+" frames="+activity.frames.get());}
 private boolean pixels(int phase)throws Exception{Bitmap[] out={null};onMain(()->out[0]=activity.texture.getBitmap(WIDTH,HEIGHT));Bitmap bitmap=out[0];if(bitmap==null)return false;int left=bitmap.getPixel(WIDTH/4,HEIGHT/2),right=bitmap.getPixel(3*WIDTH/4,HEIGHT/2);bitmap.recycle();int red=phase==0?left:right,blue=phase==0?right:left;return Color.red(red)>200&&Color.green(red)<50&&Color.blue(red)<50&&Color.blue(blue)>200&&Color.green(blue)<50&&Color.red(blue)<50;}
 private void healthy(){require(failure.get()==null,"production pipeline error: "+failure.get());}
 private void onMain(Runnable action)throws Exception{Throwable[] error={null};runner.runOnMainSync(()->{try{action.run();}catch(Throwable failure){error[0]=failure;}});if(error[0]!=null)throw new AssertionError("main-thread fixture action",error[0]);}
 private static void require(boolean value,String why){if(!value)throw new AssertionError(why);}
}
