package com.mooncast.host;

import android.app.*;
import android.content.*;
import android.graphics.PixelFormat;
import android.hardware.display.*;
import android.media.ImageReader;
import android.os.SystemClock;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

final class AppDisplayChecks {
    static void verify(Instrumentation test) throws Exception {
        Context c=test.getTargetContext();
        ImageReader reader=ImageReader.newInstance(800,600,PixelFormat.RGBA_8888,3);
        AtomicInteger id=new AtomicInteger(-1);AtomicReference<String> failure=new AtomicReference<>();CountDownLatch ready=new CountDownLatch(1);
        VideoPipeline pipeline=new VideoPipeline(c,reader.getSurface(),800,600,CropGeometry.SCREEN,new VideoPipeline.Events(){
            public void message(String text){}
            public void error(String text){failure.set(text);ready.countDown();}
        });
        Activity app=null,primary=null;
        ShizukuInputBackend privileged=new ShizukuInputBackend(c,text->android.util.Log.i("MoonCastPrivilegedTest",text));
        try{
            primary=test.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK));
            ShizukuSetupChecks.authorize(test);
            long bindEnd=SystemClock.elapsedRealtime()+15000;
            while(!ShizukuInputBackend.authorized() && SystemClock.elapsedRealtime()<bindEnd)Thread.sleep(100);
            if(!ShizukuInputBackend.authorized())throw new AssertionError("Authorize MoonCast in the test emulator's Shizuku before running this suite");
            test.runOnMainSync(privileged::start);
            while(!privileged.ready() && SystemClock.elapsedRealtime()<bindEnd)Thread.sleep(100);
            if(!privileged.ready())throw new AssertionError("Shizuku user service did not become ready");
            pipeline.start(800,600,false,(surface,w,h)->{
                id.set(privileged.createDisplay(surface,w,h,160));privileged.display(id.get());ready.countDown();return null;
            });
            if(!ready.await(5,TimeUnit.SECONDS) || id.get()<0)throw new AssertionError("separate display: "+failure.get());
            TestPatternActivity.lastDisplayId=-1;
            privileged.launch(new ComponentName(c,TestPatternActivity.class).flattenToString(),id.get());
            long launchEnd=SystemClock.elapsedRealtime()+5000;
            while(TestPatternActivity.lastDisplayId<0 && SystemClock.elapsedRealtime()<launchEnd)Thread.sleep(50);
            if(TestPatternActivity.lastDisplayId!=id.get())throw new AssertionError("app appeared on primary screen / did not launch");
            boolean rendered=false;long end=SystemClock.elapsedRealtime()+2500;
            while(SystemClock.elapsedRealtime()<end){
                try(var image=reader.acquireLatestImage()){
                    if(image!=null){var plane=image.getPlanes()[0];var pixels=plane.getBuffer();
                        int colored=0;for(int y=50;y<550;y+=50)for(int x=50;x<750;x+=50){int at=y*plane.getRowStride()+x*plane.getPixelStride();if((pixels.get(at)&255)>100 || (pixels.get(at+1)&255)>100 || (pixels.get(at+2)&255)>100)colored++;}
                        if(colored>=10)rendered=true;
                    }
                }Thread.sleep(40);
            }
            if(!rendered || failure.get()!=null)throw new AssertionError("separate app produced no generated pixels: "+failure.get());
            int touches=TestPatternActivity.touches,keys=TestPatternActivity.keys,cancels=TestPatternActivity.cancels;
            privileged.touch(1,1,400,300,1);Thread.sleep(100);privileged.touch(3,1,430,320,1);Thread.sleep(100);privileged.touch(2,1,430,320,1);
            privileged.key(65,false,0);privileged.key(65,true,0);Thread.sleep(200);
            if(TestPatternActivity.touches<=touches || TestPatternActivity.lastTouchDisplay!=id.get())throw new AssertionError("Shizuku touch was not routed to app display");
            if(TestPatternActivity.keys<=keys)throw new AssertionError("Shizuku keyboard was not routed to app display");
            privileged.touch(1,2,400,300,1);Thread.sleep(100);privileged.cancel();Thread.sleep(100);
            if(TestPatternActivity.cancels<=cancels)throw new AssertionError("Held touch did not receive cancel");
        }finally{
            Activity current=app;if(current!=null)test.runOnMainSync(current::finish);pipeline.close();privileged.close();reader.close();
            Activity original=primary;if(original!=null)test.runOnMainSync(original::finish);
        }
        Thread.sleep(200);if(c.getSystemService(DisplayManager.class).getDisplay(id.get())!=null)throw new AssertionError("separate display leaked after stop");
    }
}
