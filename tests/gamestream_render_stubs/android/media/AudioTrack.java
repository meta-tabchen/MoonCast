/* SPDX-License-Identifier: GPL-3.0-only */
package android.media;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
/** Synthetic partial writes and release barriers; never outputs sound. */
public final class AudioTrack {
    public static final int WRITE_NON_BLOCKING=1, ERROR_DEAD_OBJECT=-6, MODE_STREAM=1, PERFORMANCE_MODE_LOW_LATENCY=1, STATE_INITIALIZED=1;
    public static volatile CountDownLatch releaseGate;
    public static volatile boolean blockWrite, deadOnce, uninitialized;
    public static volatile int partialWriteShorts=Integer.MAX_VALUE;
    public static final AtomicInteger created=new AtomicInteger(),released=new AtomicInteger(),writtenShorts=new AtomicInteger(),flushes=new AtomicInteger();
    private final Thread owner=Thread.currentThread();private boolean freed;
    private void check(){if(owner!=Thread.currentThread())throw new AssertionError("AudioTrack owner changed");if(freed)throw new AssertionError("Track used after release");}
    public static void reset(){releaseGate=null;blockWrite=false;deadOnce=false;uninitialized=false;partialWriteShorts=Integer.MAX_VALUE;created.set(0);released.set(0);writtenShorts.set(0);flushes.set(0);}
    public static int getMinBufferSize(int rate,int mask,int encoding){return 1920;}
    public int getState(){check();return uninitialized?0:STATE_INITIALIZED;}
    public int getBufferSizeInFrames(){check();return 960;}
    public int getUnderrunCount(){check();return 0;}
    public void play(){check();}public void pause(){check();}public void flush(){check();flushes.incrementAndGet();}
    public int write(short[] samples,int offset,int length,int mode){check();if(mode!=WRITE_NON_BLOCKING)throw new AssertionError("Blocking PCM write");if(deadOnce){deadOnce=false;return ERROR_DEAD_OBJECT;}if(blockWrite)return 0;int count=Math.min(length,partialWriteShorts);writtenShorts.addAndGet(count);return count;}
    public void release(){check();if(releaseGate!=null)try{releaseGate.await();}catch(InterruptedException e){throw new AssertionError(e);}freed=true;released.incrementAndGet();}
    public static final class Builder {
        public Builder setAudioAttributes(AudioAttributes v){return this;}public Builder setAudioFormat(AudioFormat v){return this;}
        public Builder setTransferMode(int v){return this;}public Builder setBufferSizeInBytes(int v){return this;}public Builder setPerformanceMode(int v){return this;}
        public AudioTrack build(){created.incrementAndGet();return new AudioTrack();}
    }
}
