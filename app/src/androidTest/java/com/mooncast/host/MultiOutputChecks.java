package com.mooncast.host;

import android.app.Instrumentation;
import android.graphics.*;
import android.media.*;
import android.view.Surface;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import io.github.jqssun.displaymirror.sunshine.*;

final class MultiOutputChecks {
    static void verify(Instrumentation test) throws Exception {
        SunshineServer.setSunshineName("MoonCast generated test");
        if(!NativeSessions.available() || NativeSessions.disconnect(1234))throw new AssertionError("session bridge availability/inactive handle gate");
        ImageReader first=ImageReader.newInstance(800,600,PixelFormat.RGBA_8888,3),second=ImageReader.newInstance(1280,720,PixelFormat.RGBA_8888,3),third=ImageReader.newInstance(640,480,PixelFormat.RGBA_8888,3);
        AtomicReference<Surface> input=new AtomicReference<>();AtomicReference<String> failure=new AtomicReference<>();CountDownLatch ready=new CountDownLatch(1);AtomicInteger captures=new AtomicInteger();
        VideoPipeline pipeline=new VideoPipeline(test.getTargetContext(),first.getSurface(),800,600,CropGeometry.SCREEN,new VideoPipeline.Events(){public void message(String t){}public void error(String t){failure.set(t);ready.countDown();}},1);
        try{
            pipeline.start(1280,720,false,(surface,w,h)->{input.set(surface);captures.incrementAndGet();ready.countDown();return null;});
            if(!ready.await(3,TimeUnit.SECONDS) || failure.get()!=null)throw new AssertionError("capture setup "+failure.get());
            pipeline.addOutput(2,second.getSurface(),1280,720);
            for(int i=0;i<10;i++){paint(input.get(),false,Color.RED);Thread.sleep(60);}
            try(Image a=first.acquireLatestImage();Image b=second.acquireLatestImage()){
                if(a==null || b==null)throw new AssertionError("two outputs missing");
                requireColor(a,400,200,Color.RED);requireColor(a,400,400,Color.GREEN);requireColor(a,400,20,Color.BLACK);
                requireColor(b,640,100,Color.RED);requireColor(b,640,600,Color.GREEN);
            }
            CountDownLatch removed=new CountDownLatch(1);pipeline.removeOutput(1,removed::countDown);if(!removed.await(2,TimeUnit.SECONDS))throw new AssertionError("first detach timeout");
            first.close();
            for(int i=0;i<8;i++){paint(input.get(),true,Color.BLUE);Thread.sleep(60);}
            try(Image b=second.acquireLatestImage()){if(b==null)throw new AssertionError("remaining output stopped");requireColor(b,640,360,Color.BLUE);}
            pipeline.addOutput(3,third.getSurface(),640,480);
            removed=new CountDownLatch(1);pipeline.removeOutput(2,removed::countDown);if(!removed.await(2,TimeUnit.SECONDS))throw new AssertionError("second detach timeout");
            second.close();
            removed=new CountDownLatch(1);pipeline.removeOutput(3,removed::countDown);if(!removed.await(2,TimeUnit.SECONDS))throw new AssertionError("last detach timeout");
            // Idle has zero outputs; reconnect must use the same consent-owned input.
            for(int i=0;i<4;i++){paint(input.get(),true,Color.BLUE);Thread.sleep(60);}
            pipeline.addOutput(4,third.getSurface(),640,480);
            for(int i=0;i<8;i++){paint(input.get(),true,Color.WHITE);Thread.sleep(60);}
            try(Image c=third.acquireLatestImage()){if(c==null)throw new AssertionError("third output missing");requireColor(c,320,240,Color.WHITE);}
            if(captures.get()!=1 || failure.get()!=null)throw new AssertionError("single capture ownership: "+captures+" "+failure.get());
        }finally{pipeline.close();first.close();second.close();third.close();}
    }
    private static void paint(Surface source,boolean solid,int color){Canvas c=source.lockCanvas(null);c.drawColor(color);if(!solid){Paint p=new Paint();p.setColor(Color.GREEN);c.drawRect(0,c.getHeight()/2f,c.getWidth(),c.getHeight(),p);}source.unlockCanvasAndPost(c);}
    private static void requireColor(Image image,int x,int y,int expected){var plane=image.getPlanes()[0];var bytes=plane.getBuffer();int offset=y*plane.getRowStride()+x*plane.getPixelStride();for(int i=0;i<3;i++){int value=bytes.get(offset+i)&255,want=i==0?Color.red(expected):i==1?Color.green(expected):Color.blue(expected);if(Math.abs(value-want)>20)throw new AssertionError("output pixel mismatch at "+x+","+y);}}
}
