/* SPDX-License-Identifier: GPL-3.0-only */
package com.mooncast.tv.gamestream.render;

import android.media.AudioTrack;
import android.media.MediaCodec;
import android.view.Surface;
import com.limelight.nvstream.jni.MoonBridge;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Runs production adapter logic against explicit synthetic Android fakes, never a device. */
public final class GameStreamRenderLifecycleTest {
    private static final byte[] SPS={0,0,0,1,0x67,66,0,31,(byte)0x80};
    private static final byte[] PPS={0,0,1,0x68,(byte)0x80};
    private static final byte[] IDR={0,0,0,1,0x65,(byte)0x80};
    private static final byte[] P={0,0,1,0x41,(byte)0x80};
    private static final MoonBridge.AudioConfiguration STEREO=new MoonBridge.AudioConfiguration(2,3);
    private static final AtomicReference<Throwable> asynchronousFailure=new AtomicReference<>();
    private static int assertions;
    public static void main(String[] args) throws Exception {
        Thread.setDefaultUncaughtExceptionHandler((t,e)->asynchronousFailure.compareAndSet(null,e));
        normalVideoAndSurfaceReplacement(); absentInitialSurface(); configureDetachRace(); delayedVideoRelease();
        videoStallAndIdrRecovery(); videoFailureAndUnsupportedFormats(); normalAudioAndPartialWrites();
        audioDeadObjectAndInvalidFrame(); delayedAudioRelease(); concurrentStopAndCallbacks(); failedConstructionRetainsOwnership();
        if(asynchronousFailure.get()!=null)throw new AssertionError("Worker failure",asynchronousFailure.get());
        System.out.println("GameStream synthetic lifecycle tests passed ("+assertions+" assertions). Android/vendor playback remains untested.");
    }
    private static void normalVideoAndSurfaceReplacement() throws Exception {
        MediaCodec.reset(); Surface a=new Surface(),b=new Surface();AtomicInteger sizes=new AtomicInteger(),detaches=new AtomicInteger();
        GameStreamVideoRenderer r=new GameStreamVideoRenderer(a,new GameStreamVideoRenderer.Listener(){
            @Override public void onVideoSizeChanged(int w,int h){if(w==1920&&h==1080)sizes.incrementAndGet();}
            @Override public void onSurfaceDetached(Surface s){detaches.incrementAndGet();}
            @Override public void onStats(GameStreamVideoRenderer.Stats s){throw new IllegalStateException("Generated listener failure");}
        });
        eq(0,r.getCapabilities());eq(0,r.setup(1,1920,1080,60));r.start();idr(r,1);
        waitFor(()->r.getStats().frames==1,"First video frame");eq(1920,r.getStats().decodedWidth);eq(1080,r.getStats().decodedHeight);
        eq("host-contract-fake.avc",r.getStats().decoderName);eq("hardware",r.getStats().implementation);check(sizes.get()>0);
        r.setSurface(null);check(r.awaitSurfaceDetached(a,1000));check(a.isValid());a.release();eq(1,detaches.get());
        eq(0,send(r,SPS,1,2,1));eq(0,send(r,PPS,2,2,1));eq(-1,send(r,IDR,0,2,1));eq(0,r.getStats().queuedFrames);
        r.setSurface(b);waitFor(()->MediaCodec.created.get()==2&&sizes.get()==2,"Replacement decoder configured");
        eq(0,send(r,IDR,0,3,1));waitFor(()->r.getStats().frames==2,"Replacement surface frame");
        r.cleanup();check(r.isReleased());check(b.isValid());b.release();eq(2,MediaCodec.released.get());
    }
    private static void absentInitialSurface() throws Exception {
        MediaCodec.reset();GameStreamVideoRenderer r=new GameStreamVideoRenderer((GameStreamVideoRenderer.Listener)null);
        eq(0,r.setup(1,1280,720,60));r.start();eq(0,send(r,SPS,1,1,1));eq(0,send(r,PPS,2,1,1));eq(-1,send(r,IDR,0,1,1));
        eq(0,MediaCodec.created.get());Surface s=new Surface();r.setSurface(s);
        waitFor(()->r.getStats().decodedWidth==1280,"Deferred decoder");eq(0,send(r,IDR,0,2,1));
        waitFor(()->r.getStats().frames==1,"Deferred first frame");r.cleanup();check(r.isReleased());s.release();
    }
    private static void configureDetachRace() throws Exception {
        MediaCodec.reset();MediaCodec.configureGate=new CountDownLatch(1);Surface s=new Surface();
        GameStreamVideoRenderer r=new GameStreamVideoRenderer(s,null);AtomicInteger setup=new AtomicInteger(99);
        Thread caller=new Thread(()->setup.set(r.setup(1,1920,1080,60)));caller.start();
        waitFor(()->MediaCodec.created.get()==1,"Configure entered");r.setSurface(null);check(!r.awaitSurfaceDetached(s,10));
        check(s.isValid());MediaCodec.configureGate.countDown();caller.join(2000);check(!caller.isAlive());eq(0,setup.get());
        check(r.awaitSurfaceDetached(s,1000));r.cleanup();check(r.isReleased());s.release();
    }
    private static void delayedVideoRelease() throws Exception {
        MediaCodec.reset();Surface s=new Surface();GameStreamVideoRenderer r=new GameStreamVideoRenderer(s,null);
        eq(0,r.setup(1,1920,1080,60));MediaCodec.releaseGate=new CountDownLatch(1);r.setSurface(null);r.cleanup();
        check(!r.isReleased());check(!r.awaitSurfaceDetached(s,10));check(s.isValid());
        MediaCodec.releaseGate.countDown();check(r.awaitReleased(1000));check(r.awaitSurfaceDetached(s,1000));s.release();
    }
    private static void videoStallAndIdrRecovery() throws Exception {
        MediaCodec.reset();MediaCodec.blockInput=true;Surface s=new Surface();GameStreamVideoRenderer r=new GameStreamVideoRenderer(s,null);
        eq(0,r.setup(1,1920,1080,60));r.start();idr(r,1);
        waitFor(()->r.getStats().droppedFrames>=1&&r.getStats().awaitingIdr,"Stalled picture dropped");
        eq(-1,send(r,P,0,2,0));MediaCodec.blockInput=false;eq(0,send(r,IDR,0,3,1));
        waitFor(()->r.getStats().frames==1,"IDR recovery output");check(r.getStats().decoderResets>=1);
        r.cleanup();check(r.isReleased());s.release();
    }
    private static void videoFailureAndUnsupportedFormats() throws Exception {
        MediaCodec.reset();Surface s=new Surface();AtomicInteger errors=new AtomicInteger();
        GameStreamVideoRenderer r=new GameStreamVideoRenderer(s,new GameStreamVideoRenderer.Listener(){@Override public void onError(String e){errors.incrementAndGet();}});
        eq(-1,r.setup(0x100,1920,1080,60));check(r.isReleased());eq(0,MediaCodec.created.get());
        eq(0,r.setup(1,1920,1080,60));r.start();MediaCodec.failDecode=true;idr(r,1);
        waitFor(r::isReleased,"Faulted decoder cleanup");check(errors.get()>=2);eq(-1,send(r,P,0,2,0));s.release();
    }
    private static void normalAudioAndPartialWrites() throws Exception {
        AudioTrack.reset();AudioTrack.partialWriteShorts=48;GameStreamAudioRenderer r=new GameStreamAudioRenderer();
        eq(0,r.setup(STEREO,48000,240));r.start();r.playDecodedAudio(new short[480]);
        waitFor(()->r.getStats().writtenFrames==240,"Partial PCM writes");eq(480,AudioTrack.writtenShorts.get());
        eq(240L,r.getStats().receivedFrames);eq(0L,r.getStats().droppedFrames);check(r.getStats().lastWriteNs>0);
        eq(20f,r.getStats().outputBufferMs());r.cleanup();check(r.isReleased());eq(1,AudioTrack.released.get());
    }
    private static void audioDeadObjectAndInvalidFrame() throws Exception {
        AudioTrack.reset();GameStreamAudioRenderer r=new GameStreamAudioRenderer();
        eq(-1,r.setup(new MoonBridge.AudioConfiguration(6,63),48000,240));eq(-1,r.setup(STEREO,44100,240));
        eq(0,r.setup(STEREO,48000,240));r.start();AudioTrack.deadOnce=true;r.playDecodedAudio(new short[480]);
        waitFor(()->r.getStats().outputResets==1,"Dead AudioTrack recovery");eq(2,AudioTrack.created.get());eq(240L,r.getStats().droppedFrames);
        r.playDecodedAudio(new short[480]);waitFor(()->r.getStats().writtenFrames==240,"Replacement PCM output");
        r.playDecodedAudio(new short[2]);waitFor(r::isReleased,"Malformed PCM terminates safely");check(r.getLastError()!=null);
    }
    private static void delayedAudioRelease() throws Exception {
        AudioTrack.reset();GameStreamAudioRenderer r=new GameStreamAudioRenderer();eq(0,r.setup(STEREO,48000,240));
        AudioTrack.releaseGate=new CountDownLatch(1);r.cleanup();check(!r.isReleased());
        AudioTrack.releaseGate.countDown();check(r.awaitReleased(1000));eq(1,AudioTrack.released.get());
    }
    private static void concurrentStopAndCallbacks() throws Exception {
        MediaCodec.reset();AudioTrack.reset();Surface s=new Surface();GameStreamVideoRenderer v=new GameStreamVideoRenderer(s,null);GameStreamAudioRenderer a=new GameStreamAudioRenderer();
        eq(0,v.setup(1,1280,720,60));eq(0,a.setup(STEREO,48000,240));v.start();a.start();idr(v,1);
        Thread input=new Thread(()->{for(int i=2;i<2000;i++){send(v,P,0,i,0);a.playDecodedAudio(new short[480]);}});input.start();
        v.stop();a.stop();input.join(2000);check(!input.isAlive());check(v.awaitReleased(2000));check(a.awaitReleased(2000));
        v.cleanup();a.cleanup();check(s.isValid());s.release();eq(MediaCodec.created.get(),MediaCodec.released.get());eq(AudioTrack.created.get(),AudioTrack.released.get());
    }
    private static void failedConstructionRetainsOwnership() throws Exception {
        MediaCodec.reset();MediaCodec.failConfigure=true;MediaCodec.failRelease=true;Surface surface=new Surface();AtomicInteger errors=new AtomicInteger();
        GameStreamVideoRenderer video=new GameStreamVideoRenderer(surface,new GameStreamVideoRenderer.Listener(){@Override public void onError(String message){errors.incrementAndGet();}});
        eq(-1,video.setup(1,1920,1080,60));waitFor(()->errors.get()>=2,"Configure plus release exception reported");
        check(!video.isReleased());video.setSurface(null);check(!video.awaitSurfaceDetached(surface,10));eq(0,MediaCodec.released.get());check(surface.isValid());
        // A throwing vendor release is deliberately not a release confirmation. Retain surface.
        AudioTrack.reset();AudioTrack.uninitialized=true;AudioTrack.releaseGate=new CountDownLatch(1);GameStreamAudioRenderer audio=new GameStreamAudioRenderer();
        eq(-1,audio.setup(STEREO,48000,240));check(!audio.isReleased());AudioTrack.releaseGate.countDown();check(audio.awaitReleased(1000));eq(1,AudioTrack.released.get());
    }
    private static void idr(GameStreamVideoRenderer r,int frame){eq(0,send(r,SPS,1,frame,1));eq(0,send(r,PPS,2,frame,1));eq(0,send(r,IDR,0,frame,1));}
    private static int send(GameStreamVideoRenderer r,byte[] data,int type,int frame,int kind){return r.submitDecodeUnit(data,data.length,type,frame,kind,(char)0,0,0);}
    private static void waitFor(BooleanSupplier condition,String message) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
        while(!condition.getAsBoolean()&&System.nanoTime()<deadline){if(asynchronousFailure.get()!=null)throw new AssertionError(message,asynchronousFailure.get());Thread.sleep(2);}
        if(!condition.getAsBoolean())throw new AssertionError("Timed out: "+message);assertions++;
    }
    private static void eq(Object a,Object b){if(!a.equals(b))throw new AssertionError("Expected "+a+", got "+b);assertions++;}
    private static void check(boolean value){assertions++;if(!value)throw new AssertionError("Assertion "+assertions+" failed");}
}
