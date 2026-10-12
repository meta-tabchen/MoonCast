/* SPDX-License-Identifier: GPL-3.0-only */
package android.media;

import android.view.Surface;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Failure injection only. Does not implement decoding or Android vendor behavior. */
public final class MediaCodec {
    public static final int INFO_OUTPUT_FORMAT_CHANGED = -2, BUFFER_FLAG_CODEC_CONFIG = 2;
    private static final Map<String, Plan> PLANS = new LinkedHashMap<>();
    private static final List<MediaCodec> CREATED = new ArrayList<>();
    public static final class Plan {
        public boolean failConfigure, failStart, failStop;
        /** -1 fails until explicitly changed; positive values fail that many attempts. */
        public int releaseFailures;
    }
    public static final class BufferInfo {
        public int flags;
        public long presentationTimeUs;
    }
    public final String name;
    public int configureCalls, startCalls, stopCalls, releaseCalls, setOutputSurfaceCalls, queuedInputs;
    public boolean released;
    private final Plan plan;
    private final ByteBuffer input = ByteBuffer.allocate(1024);
    private Surface surface;
    private MediaFormat format;
    private boolean started;

    private MediaCodec(String name, Plan plan) { this.name = name; this.plan = plan; }
    public static synchronized void resetForTest() {
        for (MediaCodec codec : CREATED)
            if (!codec.released) throw new AssertionError("Test leaked codec " + codec.name);
        PLANS.clear(); CREATED.clear();
    }
    public static synchronized Plan addDecoder(String name) {
        Plan plan = new Plan(); PLANS.put(name, plan); return plan;
    }
    static synchronized MediaCodecInfo[] testCodecInfos() {
        return PLANS.keySet().stream().map(MediaCodecInfo::new).toArray(MediaCodecInfo[]::new);
    }
    public static synchronized int createdCount() { return CREATED.size(); }
    public static synchronized MediaCodec created(int index) { return CREATED.get(index); }
    public static synchronized MediaCodec createByCodecName(String name) {
        // A swallowed release failure must fail the test even if the flag is lost.
        for (MediaCodec codec : CREATED)
            if (!codec.released)
                throw new AssertionError("New codec created before release was confirmed: " + codec.name);
        Plan plan = PLANS.get(name);
        if (plan == null) throw new AssertionError("Unknown synthetic decoder " + name);
        MediaCodec codec = new MediaCodec(name, plan); CREATED.add(codec); return codec;
    }
    private void requireLive() {
        if (released) throw new AssertionError("Codec used after successful release: " + name);
    }
    private void requireStarted() {
        requireLive();
        if (!started) throw new IllegalStateException("Synthetic codec is not started");
    }
    public synchronized Surface testSurface() { return surface; }
    public synchronized void configure(MediaFormat value, Surface target, Object crypto, int flags) {
        requireLive(); configureCalls++;
        // A configure failure may already have taken ownership of the output producer.
        surface = target; surface.acquireForCodec(); format = value;
        if (plan.failConfigure) throw new IllegalStateException("Synthetic configure failure");
    }
    public synchronized void start() {
        requireLive(); startCalls++;
        if (plan.failStart) throw new IllegalStateException("Synthetic start failure");
        started = true;
    }
    public synchronized void stop() {
        requireLive(); stopCalls++;
        if (plan.failStop || !started) throw new IllegalStateException("Synthetic stop failure");
        started = false;
    }
    public synchronized void release() {
        requireLive(); releaseCalls++;
        if (plan.releaseFailures != 0) {
            if (plan.releaseFailures > 0) plan.releaseFailures--;
            throw new IllegalStateException("Synthetic release failure");
        }
        released = true; started = false;
        if (surface != null) surface.releaseFromCodec();
    }
    public synchronized void setOutputSurface(Surface value) {
        requireLive(); setOutputSurfaceCalls++; requireStarted();
        value.acquireForCodec(); surface.releaseFromCodec(); surface = value;
    }
    public synchronized int dequeueInputBuffer(long timeoutUs) { requireStarted(); return 0; }
    public synchronized ByteBuffer getInputBuffer(int index) { requireStarted(); return input; }
    public synchronized void queueInputBuffer(int index, int offset, int size, long ptsUs, int flags) {
        requireStarted(); queuedInputs++;
    }
    public synchronized int dequeueOutputBuffer(BufferInfo info, long timeoutUs) {
        requireStarted(); return -1;
    }
    public synchronized MediaFormat getOutputFormat() { requireStarted(); return format; }
    public synchronized void releaseOutputBuffer(int index, boolean render) { requireStarted(); }
    public synchronized void releaseOutputBuffer(int index, long timestampNs) { requireStarted(); }
}
