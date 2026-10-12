package com.mooncast.receiver;

import com.limelight.nvstream.NvConnectionListener;
import com.limelight.nvstream.av.audio.AudioRenderer;
import com.limelight.nvstream.av.video.VideoDecoderRenderer;
import com.limelight.nvstream.jni.MoonBridge;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.*;

public class ReceiverSessionTest {
    static class Driver implements ReceiverSession.Driver {
        volatile int stops;
        NvConnectionListener callback;
        boolean startupComplete = true;
        public void start(AudioRenderer a, VideoDecoderRenderer v, NvConnectionListener l) { callback = l; }
        public void stop() { stops++; }
        public boolean awaitStartupComplete(long ms) { return startupComplete; }
    }
    static class Audio implements AudioRenderer, RendererRelease {
        volatile int stops, cleanups; boolean released = true;
        public int setup(MoonBridge.AudioConfiguration c,int rate,int samples) { return 0; }
        public void start() {}
        public void stop() { stops++; }
        public void playDecodedAudio(short[] data) {}
        public void cleanup() { cleanups++; }
        public boolean awaitReleased(long ms) { return released; }
    }
    static class Video extends VideoDecoderRenderer implements RendererRelease {
        volatile int stops, cleanups; boolean released = true, cleanupThrows;
        public int setup(int format,int w,int h,int rate) { return 0; }
        public void start() {}
        public void stop() { stops++; }
        public int submitDecodeUnit(byte[] b,int length,int type,int frame,int kind,char latency,long rx,long enqueue) { return 0; }
        public void cleanup() { cleanups++; if(cleanupThrows) throw new IllegalStateException("mock vendor failure"); }
        public int getCapabilities() { return 0; }
        public void setHdrMode(boolean enabled,byte[] metadata) {}
        public boolean awaitReleased(long ms) { return released; }
    }
    static class Listener implements NvConnectionListener {
        int starts, failed; boolean throwsOnStart;
        public void stageStarting(String stage) {}
        public void stageComplete(String stage) {}
        public void stageFailed(String stage,int ports,int code) { failed++; }
        public void connectionStarted() { starts++; if(throwsOnStart) throw new IllegalStateException("mock UI failure"); }
        public void connectionTerminated(int code) {}
        public void connectionStatusUpdate(int status) {}
        public void displayMessage(String msg) {}
        public void displayTransientMessage(String msg) {}
        public void rumble(short a,short b,short c) {}
        public void rumbleTriggers(short a,short b,short c) {}
        public void setHdrMode(boolean enabled,byte[] metadata) {}
        public void setMotionEventState(short n,byte kind,short rate) {}
        public void setControllerLED(short n,byte r,byte g,byte b) {}
    }
    @Test public void successfulStopIsExactlyOnce() throws Exception {
        Driver d=new Driver(); Audio a=new Audio(); Video v=new Video(); Listener l=new Listener();
        ReceiverSession s=new ReceiverSession(d,a,v,l); d.callback.connectionStarted();
        assertEquals(ReceiverSession.State.ACTIVE,s.getState());
        CompletableFuture<Void> first=s.stopAsync(); assertSame(first,s.stopAsync());
        first.get(3,TimeUnit.SECONDS);
        assertEquals(1,d.stops);assertEquals(1,a.cleanups);assertEquals(1,v.cleanups);
        assertEquals(ReceiverSession.State.STOPPED,s.getState());
    }
    @Test public void failureTriggersCleanup() throws Exception {
        Driver d=new Driver();Audio a=new Audio();Video v=new Video();Listener l=new Listener();
        ReceiverSession s=new ReceiverSession(d,a,v,l);d.callback.stageFailed("RTSP",0,-1);
        s.stopAsync().get(3,TimeUnit.SECONDS);assertEquals(1,l.failed);assertEquals(1,d.stops);
    }
    @Test public void cancellationSuppressesLateCallbacks() throws Exception {
        Driver d=new Driver();Listener l=new Listener();ReceiverSession s=new ReceiverSession(d,new Audio(),new Video(),l);
        s.stopAsync().get(3,TimeUnit.SECONDS);d.callback.connectionStarted();assertEquals(0,l.starts);
        assertEquals(ReceiverSession.State.STOPPED,s.getState());
    }
    @Test public void terminalStopCannotBeOverwrittenByLateFailure() throws Exception {
        Driver d=new Driver();Listener l=new Listener();ReceiverSession s=new ReceiverSession(d,new Audio(),new Video(),l);
        s.stopAsync().get(3,TimeUnit.SECONDS);d.callback.stageFailed("late",0,-1);
        assertEquals(ReceiverSession.State.STOPPED,s.getState());assertEquals(0,l.failed);
    }
    @Test public void racingStartCallbackCannotOverwriteCompletedStop() throws Exception {
        for(int n=0;n<100;n++) {
            Driver d=new Driver();ReceiverSession s=new ReceiverSession(d,new Audio(),new Video(),new Listener());
            Thread callback=new Thread(d.callback::connectionStarted);callback.start();
            s.stopAsync().get(3,TimeUnit.SECONDS);callback.join(3000);
            assertEquals(ReceiverSession.State.STOPPED,s.getState());
        }
    }
    @Test public void callbackExceptionDoesNotEscapeToJni() throws Exception {
        Driver d=new Driver();Listener l=new Listener();l.throwsOnStart=true;
        ReceiverSession s=new ReceiverSession(d,new Audio(),new Video(),l);
        d.callback.connectionStarted();s.stopAsync().get(3,TimeUnit.SECONDS);assertEquals(1,l.starts);
    }
    @Test public void unreleasedVideoCannotReportSuccess() throws Exception {
        Driver d=new Driver();Video v=new Video();v.released=false;
        ReceiverSession s=new ReceiverSession(d,new Audio(),v,new Listener());
        try {s.stopAsync().get(3,TimeUnit.SECONDS);fail();} catch(ExecutionException expected) {}
        assertEquals(ReceiverSession.State.FAILED,s.getState());
    }
    @Test public void uncertainNativeStartupDoesNotReleaseSurface() throws Exception {
        Driver d=new Driver();d.startupComplete=false;Video v=new Video();Audio a=new Audio();
        ReceiverSession s=new ReceiverSession(d,a,v,new Listener());
        try {s.stopAsync().get(3,TimeUnit.SECONDS);fail();} catch(ExecutionException expected) {}
        assertEquals(0,v.cleanups);assertEquals(0,a.cleanups);
    }
    @Test public void failedVideoCleanupStillAttemptsAudio() throws Exception {
        Driver d=new Driver();Video v=new Video();v.cleanupThrows=true;Audio a=new Audio();
        ReceiverSession s=new ReceiverSession(d,a,v,new Listener());
        try {s.stopAsync().get(3,TimeUnit.SECONDS);fail();} catch(ExecutionException expected) {}
        assertEquals(1,a.cleanups);assertEquals(1,v.cleanups);
    }
    @Test public void rejectsConcurrentRendererReuseUntilReleased() throws Exception {
        Driver first=new Driver();Audio a=new Audio();Video v=new Video();
        ReceiverSession s=new ReceiverSession(first,a,v,new Listener());
        try {new ReceiverSession(new Driver(),a,v,new Listener());fail();} catch(IllegalStateException expected) {}
        s.stopAsync().get(3,TimeUnit.SECONDS);
        ReceiverSession next=new ReceiverSession(new Driver(),a,v,new Listener());
        next.stopAsync().get(3,TimeUnit.SECONDS);
        assertEquals(2,a.cleanups);
    }
}
