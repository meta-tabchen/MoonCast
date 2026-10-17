package com.mooncast.tv.airplay;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Private local diagnostics only. Native process exits are read on the next launch. */
public final class ReceiverDiagnostics {
    private static final ReceiverTimeline timeline=new ReceiverTimeline();
    private static AtomicFile file;
    private static String previous="",exits="",anchor="";
    private static boolean initialized;
    private ReceiverDiagnostics(){}
    public static synchronized void initialize(Context context){
        if(initialized)return;initialized=true;anchor="pid="+android.os.Process.myPid()+" wallMs="+System.currentTimeMillis()+" elapsedMs="+SystemClock.elapsedRealtime()+"\n";file=new AtomicFile(new File(context.getFilesDir(),"receiver-event-timeline.txt"));
        try{byte[] bytes=file.readFully();if(bytes.length<=16384)previous=new String(bytes,StandardCharsets.UTF_8);}catch(Exception ignored){}
        if(Build.VERSION.SDK_INT>=30)try{
            StringBuilder result=new StringBuilder();
            for(android.app.ApplicationExitInfo exit:context.getSystemService(ActivityManager.class).getHistoricalProcessExitReasons(context.getPackageName(),0,8)){
                if(exit.getProcessName()!=null&&exit.getProcessName().endsWith(":receiver"))result.append("reason=").append(exit.getReason()).append(" status=").append(exit.getStatus()).append(" wallMs=").append(exit.getTimestamp()).append('\n');
            }exits=result.toString();
        }catch(RuntimeException ignored){}
        Thread.UncaughtExceptionHandler old=Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread,error)->{event("uncaught-java",error.getClass().getName().hashCode());if(old!=null)old.uncaughtException(thread,error);});
        event("process-start",Build.VERSION.SDK_INT);
    }
    public static synchronized void event(String code,long value){
        timeline.add(SystemClock.elapsedRealtime(),code,value);
        if(file==null)return;FileOutputStream out=null;
        try{out=file.startWrite();out.write((anchor+timeline.text()).getBytes(StandardCharsets.UTF_8));file.finishWrite(out);}
        catch(Exception ignored){if(out!=null)file.failWrite(out);}
    }
    public static synchronized String report(){return "receiver-events v2 (elapsed ms / event / numeric value; exception/codec values are class/name hashes)\n"+anchor+timeline.text()+"\nprevious-process:\n"+previous+"\nAndroid exit reasons (if available):\n"+exits;}
}
