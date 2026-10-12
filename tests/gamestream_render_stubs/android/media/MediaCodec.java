/* SPDX-License-Identifier: GPL-3.0-only */
package android.media;
import android.view.Surface;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
/** Intentionally synthetic; asserts all codec calls have one owner thread. */
public final class MediaCodec {
    public static final int BUFFER_FLAG_CODEC_CONFIG=2, BUFFER_FLAG_KEY_FRAME=1, BUFFER_FLAG_END_OF_STREAM=4;
    public static final int INFO_OUTPUT_FORMAT_CHANGED=-2, INFO_OUTPUT_BUFFERS_CHANGED=-3;
    public static volatile CountDownLatch configureGate, releaseGate;
    public static volatile boolean blockInput, failRelease, failDecode, failConfigure;
    public static final AtomicInteger created=new AtomicInteger(), released=new AtomicInteger(), rendered=new AtomicInteger(), flushed=new AtomicInteger();
    private final Thread owner=Thread.currentThread();
    private final ByteBuffer input=ByteBuffer.allocate(8*1024*1024);
    private final ArrayDeque<Long> outputs=new ArrayDeque<>();
    private MediaFormat format;
    private Surface surface;
    private boolean freed, changed;
    public static final class BufferInfo { public int flags,size; public long presentationTimeUs; }
    public static MediaCodec createByCodecName(String name){created.incrementAndGet();return new MediaCodec();}
    public static void reset(){configureGate=null;releaseGate=null;blockInput=false;failRelease=false;failDecode=false;failConfigure=false;created.set(0);released.set(0);rendered.set(0);flushed.set(0);}
    private void check(){if(owner!=Thread.currentThread())throw new AssertionError("Codec owner changed");if(freed)throw new AssertionError("Codec used after release");}
    private static void gate(CountDownLatch latch){if(latch!=null)try{latch.await();}catch(InterruptedException e){throw new AssertionError(e);}}
    public void configure(MediaFormat f,Surface s,Object crypto,int flags){check();surface=s;gate(configureGate);if(failConfigure)throw new IllegalStateException("Generated configure failure");if(!s.isValid())throw new AssertionError("Surface released while configuring");format=f;}
    public void start(){check();changed=true;}
    public void release(){check();gate(releaseGate);if(failRelease)throw new IllegalStateException("Generated release failure");if(surface!=null&&!surface.isValid())throw new AssertionError("Surface released before decoder");freed=true;released.incrementAndGet();}
    public void flush(){check();outputs.clear();flushed.incrementAndGet();}
    public int dequeueInputBuffer(long wait){check();if(failDecode)throw new IllegalStateException("Generated codec error");return blockInput?-1:0;}
    public ByteBuffer getInputBuffer(int index){check();return input;}
    public void queueInputBuffer(int index,int offset,int size,long pts,int flags){check();if(size>0&&(flags&BUFFER_FLAG_CODEC_CONFIG)==0)outputs.add(pts);}
    public int dequeueOutputBuffer(BufferInfo info,long wait){check();if(changed){changed=false;return INFO_OUTPUT_FORMAT_CHANGED;}Long pts=outputs.poll();if(pts==null)return -1;info.presentationTimeUs=pts;info.flags=0;info.size=1;return 0;}
    public MediaFormat getOutputFormat(){check();return format;}
    public void releaseOutputBuffer(int index,boolean render){check();if(render){if(!surface.isValid())throw new AssertionError("Rendered to released Surface");rendered.incrementAndGet();}}
}
