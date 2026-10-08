package com.mooncast.host;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.*;
import android.media.*;
import android.os.*;
import android.view.Surface;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Hardware GL integration check using generated video bars, never the user's screen. */
public final class RenderingInstrumentation extends Instrumentation {
    private String suite;
    @Override public void onCreate(Bundle b){super.onCreate(b);suite=b==null?"all":b.getString("suite","all");start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            if(suite.equals("host-lifecycle")){HostLifecycleChecks.verify(this);result.putString("result","PASS: stale UI binding recovery, normal Stop, blocked-main Stop, blocked-cleanup Stop and fresh process restart");finish(-1,result);return;}
            if(suite.equals("profiles")){PreferencesInstrumentation.verify(getTargetContext());result.putString("result","PASS: legacy migration, independent receiver settings, selected-profile and restart persistence");finish(-1,result);return;}
            if(suite.equals("app-display")){AppDisplayChecks.verify(this);result.putString("result","PASS: independent VirtualDisplay, generated app launch/pixels, Shizuku touch/key/cancel routing, display release");finish(-1,result);return;}
            if(suite.equals("root-input")){RootInputChecks.verify(this);result.putString("result","PASS: root input broker, real touch/key/cancel events and cleanup");finish(-1,result);return;}
            if(suite.equals("file-cinema")){FileCinemaChecks.verify(this);result.putString("result","PASS: SAF generated media, background service, exact HTTP bytes/range and stop revocation");finish(-1,result);return;}
            if(suite.equals("multi-output")){MultiOutputChecks.verify(this);result.putString("result","PASS: single capture, simultaneous GPU outputs, independent detach/add, survivor frames, native session bridge and inactive-handle gate");finish(-1,result);return;}
            if(suite.equals("panel-power")){result.putString("result",PanelPowerChecks.verify(this));finish(-1,result);return;}
            if(suite.equals("native-stream") || suite.equals("native-phone")){NativeStreamChecks.verify(this,suite.equals("native-phone"));result.putString("result","PASS: two real Moonlight-core sessions, MediaCodec encode/transport/client decode and individual native disconnect with survivor frames, last-disconnect/reconnect and explicit Stop");finish(-1,result);return;}
            checkCase(CropGeometry.SCREEN,"screen",false);
            checkCase(CropGeometry.VIDEO_FIT,"video-fit",false);
            checkCase(CropGeometry.VIDEO_FILL,"video-fill",false);
            checkCase(CropGeometry.VIDEO_REGION,"video-region",false);
            checkCase(CropGeometry.VIDEO_FIT,"paused",false);
            checkCase(CropGeometry.VIDEO_FIT,"portrait",true);
            checkCase(CropGeometry.CINEMA,"cinema-controls",false);
            checkCase(CropGeometry.SCREEN,"frame-limit",false);
            result.putString("result","PASS: actual SurfaceTexture/EGL output, auto crop, paused single-frame crop, fixed 16:9, fit/fill, orientation, portrait resize, cinema lock through opaque controls");
            finish(android.app.Activity.RESULT_OK,result);
        }catch(Throwable e){result.putString("error",e.toString());e.printStackTrace();finish(android.app.Activity.RESULT_CANCELED,result);}
    }
    private void checkCase(int mode,String label,boolean portrait) throws Exception {
        Context context=getTargetContext();ImageReader reader=ImageReader.newInstance(800,600,PixelFormat.RGBA_8888,3);
        CountDownLatch ready=new CountDownLatch(1);AtomicReference<Surface> source=new AtomicReference<>();AtomicReference<String> failure=new AtomicReference<>();
        VideoPipeline pipeline=new VideoPipeline(context,reader.getSurface(),800,600,mode,new VideoPipeline.Events(){
            public void message(String message){android.util.Log.i("MoonCastGpuTest",label+": "+message);}
            public void error(String message){failure.set(message);ready.countDown();}
        });
        if(label.equals("frame-limit"))pipeline.setFrameLimit(15);
        pipeline.start(2400,1080,false,(surface,w,h)->{source.set(surface);ready.countDown();return null;});
        try{
            if(!ready.await(5,TimeUnit.SECONDS) || source.get()==null)throw new AssertionError("GL setup: "+failure.get());
            Image last=null;
            // Three stable probes are required by the crop filter. Repeated source frames let it settle.
            long end=SystemClock.elapsedRealtime()+2200;
            if(label.equals("paused"))paint(source.get(),false);
            while(SystemClock.elapsedRealtime()<end){
                if(!label.equals("paused"))paint(source.get(),false);
                Thread.sleep(55);
                Image next=reader.acquireLatestImage();if(next!=null){if(last!=null)last.close();last=next;}
                if(failure.get()!=null)throw new AssertionError(failure.get());
            }
            if(portrait){
                pipeline.resize(1080,2400);Thread.sleep(120);
                for(int i=0;i<10;i++){paint(source.get(),true);Thread.sleep(55);Image next=reader.acquireLatestImage();if(next!=null){if(last!=null)last.close();last=next;}}
            }
            if(mode==CropGeometry.CINEMA){
                // Opaque player controls cover the original pillar bars for longer than the old expansion filter.
                long controlsEnd=SystemClock.elapsedRealtime()+1900;
                while(SystemClock.elapsedRealtime()<controlsEnd){
                    Canvas c=source.get().lockCanvas(null);c.drawColor(Color.BLUE);source.get().unlockCanvasAndPost(c);
                    Thread.sleep(55);Image next=reader.acquireLatestImage();if(next!=null){if(last!=null)last.close();last=next;}
                }
            }
            if(label.equals("frame-limit")){var stats=pipeline.statistics();if(stats.fps()<=0 || stats.fps()>16)throw new AssertionError("capture submission limit: "+stats);}
            if(last==null)throw new AssertionError("No GPU output");
            try{
                boolean leftBlack=black(last,50,300),topBlack=black(last,400,20),centerBlack=black(last,400,300);
                if(centerBlack)throw new AssertionError(label+" blank center");
                if(mode==CropGeometry.SCREEN && !leftBlack)throw new AssertionError("screen mode should retain player pillar bars");
                if((mode==CropGeometry.VIDEO_FIT || mode==CropGeometry.VIDEO_REGION || mode==CropGeometry.CINEMA) && !portrait && (leftBlack || !topBlack))throw new AssertionError("fit must remove pillar bars while retaining aspect bars");
                if(mode==CropGeometry.VIDEO_FILL && (leftBlack || topBlack))throw new AssertionError("fill should cover output");
                if(portrait && !leftBlack)throw new AssertionError("portrait should preserve full height and pillarbox");
                // Pattern has a red top half and green bottom half. Verify SurfaceTexture transform is used.
                if(!portrait && mode!=CropGeometry.CINEMA){int[] upper=color(last,400,200),lower=color(last,400,400);if(upper[0]<150 || lower[1]<150)throw new AssertionError("texture upside down / colors lost");}
                Bitmap bitmap=Bitmap.createBitmap(800,600,Bitmap.Config.ARGB_8888);int[] argb=new int[800*600];
                for(int y=0;y<600;y++)for(int x=0;x<800;x++){int[] c=color(last,x,y);argb[y*800+x]=Color.rgb(c[0],c[1],c[2]);}
                bitmap.setPixels(argb,0,800,0,0,800,600);
                try(java.io.FileOutputStream out=context.openFileOutput("gpu-test-"+label+".png",Context.MODE_PRIVATE)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
            }finally{last.close();}
        }finally{pipeline.close();reader.close();}
    }
    private void paint(Surface surface,boolean portrait){
        Canvas canvas=surface.lockCanvas(null);Paint paint=new Paint();canvas.drawColor(Color.BLACK);
        float l=portrait?0:canvas.getWidth()*.10f,r=portrait?canvas.getWidth():canvas.getWidth()*.90f;
        paint.setColor(Color.RED);canvas.drawRect(l,0,r,canvas.getHeight()/2f,paint);
        paint.setColor(Color.GREEN);canvas.drawRect(l,canvas.getHeight()/2f,r,canvas.getHeight(),paint);
        surface.unlockCanvasAndPost(canvas);
    }
    private static int[] color(Image image,int x,int y){Image.Plane p=image.getPlanes()[0];ByteBuffer buffer=p.getBuffer();int offset=y*p.getRowStride()+x*p.getPixelStride();return new int[]{buffer.get(offset)&255,buffer.get(offset+1)&255,buffer.get(offset+2)&255};}
    private static boolean black(Image image,int x,int y){int[] c=color(image,x,y);return c[0]<20 && c[1]<20 && c[2]<20;}
}
