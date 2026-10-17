/* SPDX-License-Identifier: GPL-3.0-only */
package com.mooncast.tv.airplay;

import android.media.MediaCodec;
import android.view.Surface;
import java.lang.reflect.Field;

/** Synthetic host regression tests against the production MirrorDecoder class. */
public final class AirPlayDecoderReleaseTest {
    private static final byte[] AVC_IDR = {0, 0, 0, 1, 0x65, 1};
    private static final byte[] HEVC_IDR = {0, 0, 0, 1, 0x26, 1, 1};
    private static final Field CODEC = field("codec"), SURFACE = field("surface");
    private static int checks, scenarios;

    private static Field field(String name) {
        try {
            Field result = MirrorDecoder.class.getDeclaredField(name);
            result.setAccessible(true); return result;
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static Object value(Field field, MirrorDecoder decoder) {
        synchronized (decoder) {
            try { return field.get(decoder); }
            catch (IllegalAccessException failure) { throw new AssertionError(failure); }
        }
    }
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static final class Events implements MirrorDecoder.Events {
        int failures;
        final java.util.List<String> diagnostics=new java.util.ArrayList<>();
        public void diagnostic(String code,long value){check(code.matches("[a-z0-9_-]{1,40}"),"bounded diagnostic code only");diagnostics.add(code);}
        public void size(int width, int height) { }
        public void failure() { failures++; }
        public void output() { throw new AssertionError("Synthetic codecs must not produce frames"); }
    }
    private static final class Fixture {
        final Events events = new Events();
        final MirrorDecoder decoder = new MirrorDecoder(events);
        final Surface original = new Surface(), replacement = new Surface();
        final MediaCodec.Plan first;
        final boolean hevc;
        Fixture(boolean hevc) {
            MediaCodec.resetForTest();
            this.hevc = hevc;
            first = MediaCodec.addDecoder("synthetic.primary");
            MediaCodec.addDecoder("synthetic.fallback");
            decoder.setSurface(original);
            check(!decoder.releaseFailed(), "New decoder must start without release failure");
        }
        void feed() { decoder.feed(hevc ? HEVC_IDR : AVC_IDR, 1_000_000L, hevc); }
        void retained(MediaCodec codec, String context) {
            check(decoder.releaseFailed(), context + ": release failure must remain sticky");
            check(decoder.active(), context + ": uncertain codec must remain owned");
            check(value(CODEC, decoder) == codec, context + ": lost original codec reference");
            check(value(SURFACE, decoder) == original, context + ": accepted a new pending surface");
            check(!codec.released, context + ": failure is not positive release evidence");
            check(codec.testSurface() == original, context + ": codec acquired another surface");
            check(original.isValid() && original.codecOwners() == 1,
                    context + ": original producer must remain alive and owned");
            check(replacement.codecOwners() == 0, context + ": replacement producer acquired too early");
            check(MediaCodec.createdCount() == 1, context + ": unsafe fallback/restart");
            check(codec.setOutputSurfaceCalls == 0, context + ": touched output surface after failed release");
        }
        void blockedWhileUnreleased(MediaCodec codec) {
            int queued = codec.queuedInputs, failures = events.failures;
            feed();
            decoder.feed(hevc ? AVC_IDR : HEVC_IDR, 2_000_000L, !hevc);
            retained(codec, "Further packets and codec switch");
            check(codec.queuedInputs == queued, "Blocked session must not queue another frame");
            check(events.failures == failures, "Blocked packets must not repeat failure callbacks");

            for (Surface proposed : new Surface[]{replacement, null, original, replacement}) {
                int releases = codec.releaseCalls;
                decoder.setSurface(proposed);
                check(codec.releaseCalls > releases, "Surface operation must retry retained codec release");
                retained(codec, "Blocked surface change");
            }
            int releases = codec.releaseCalls;
            decoder.stopSession();
            check(codec.releaseCalls > releases, "Session stop must retry release");
            retained(codec, "Repeated session stop");
            decoder.setResolution(1280, 720, 30);
            retained(codec, "Resolution reset");
            decoder.configureTiming(false, 0);
            decoder.resetStatistics();
            retained(codec, "Timing/statistics reset");
            feed();
            check(events.failures == failures, "Stop/reset must not resume an unreleased decoder");
            check(codec.queuedInputs == queued, "Stop/reset must preserve the input block");
        }
        void recoverOnSurfaceChange(MediaCodec retained) {
            first.releaseFailures = 0;
            first.failConfigure = first.failStart = first.failStop = false;
            decoder.setSurface(replacement);
            check(retained.released, "Surface change requires successful old codec release");
            check(!decoder.releaseFailed(), "Positive release must clear sticky failure");
            check(!decoder.active() && value(CODEC, decoder) == null, "Released codec reference must be cleared");
            check(value(SURFACE, decoder) == replacement, "Replacement may be accepted after confirmed release");
            check(original.codecOwners() == 0, "Successful release must relinquish original producer");
            check(replacement.codecOwners() == 0, "Replacement needs no codec before another frame");
            original.release();
            feed();
            check(MediaCodec.createdCount() == 2, "Fresh decode may restart after positive release");
            MediaCodec restarted = MediaCodec.created(1);
            check(decoder.active() && !decoder.releaseFailed(), "Fresh decoder must be active and safe");
            check(restarted.testSurface() == replacement && replacement.codecOwners() == 1,
                    "Fresh decoder must own only the replacement producer");
            decoder.stopSession();
            check(!decoder.active() && !decoder.releaseFailed(), "Recovered decoder must stop normally");
            check(restarted.released && replacement.codecOwners() == 0, "Recovered codec must release producer");
            replacement.release();
        }
        void finish() {
            decoder.stopSession();
            check(!decoder.active() && !decoder.releaseFailed(), "Successful cleanup must fully stop codec");
            check(original.codecOwners() == 0 && replacement.codecOwners() == 0,
                    "Successful cleanup must leave no producer owners");
            check(events.diagnostics.contains("decoder-create")&&events.diagnostics.contains("decoder-released"),"decoder creation and confirmed release recorded");
            original.release(); replacement.release();
        }
    }

    private static void failedCandidateRetainsOwnership(boolean failStart, boolean hevc) {
        Fixture fixture = new Fixture(hevc);
        fixture.first.failConfigure = !failStart;
        fixture.first.failStart = failStart;
        fixture.first.releaseFailures = -1;
        fixture.feed();
        check(MediaCodec.createdCount() == 1, "Failed candidate must not fall back before release");
        MediaCodec candidate = MediaCodec.created(0);
        check(candidate.configureCalls == 1 && candidate.startCalls == (failStart ? 1 : 0),
                "Failure must originate at the intended configure/start operation");
        check(candidate.releaseCalls >= 2, "Both failed-start cleanup and outer session cleanup must run");
        check(candidate.stopCalls >= 1, "Outer cleanup must attempt stop even on an unstarted candidate");
        check(fixture.events.failures == 1, "Failed candidate must report one decode failure");
        fixture.retained(candidate, failStart ? "Start failure" : "Configure failure");
        fixture.blockedWhileUnreleased(candidate);
        fixture.recoverOnSurfaceChange(candidate);
        scenarios++;
    }

    private static void safeFallbackAfterConfirmedRelease(boolean failStart, boolean hevc) {
        Fixture fixture = new Fixture(hevc);
        fixture.first.failConfigure = !failStart;
        fixture.first.failStart = failStart;
        fixture.feed();
        check(MediaCodec.createdCount() == 2, "Released rejected candidate should permit fallback");
        MediaCodec rejected = MediaCodec.created(0), fallback = MediaCodec.created(1);
        check(rejected.released && rejected.releaseCalls == 1, "Rejected candidate release must succeed first");
        check("synthetic.fallback".equals(fallback.name) && fallback.queuedInputs == 1,
                "Usable fallback must receive the access unit");
        check(fixture.decoder.active() && !fixture.decoder.releaseFailed(), "Clean fallback must be usable");
        check(fixture.original.codecOwners() == 1, "Only fallback may own the surface");
        check(fixture.events.failures == 0, "Successful fallback must not report a terminal failure");
        fixture.finish();
        scenarios++;
    }

    private static void transientCandidateReleaseFailure(boolean failStart, boolean hevc) {
        Fixture fixture = new Fixture(hevc);
        fixture.first.failConfigure = !failStart;
        fixture.first.failStart = failStart;
        fixture.first.releaseFailures = 1;
        fixture.feed();
        check(MediaCodec.createdCount() == 1, "First release failure must abort same-attempt fallback");
        MediaCodec candidate = MediaCodec.created(0);
        check(candidate.releaseCalls == 2 && candidate.released, "Outer cleanup may positively release on retry");
        check(!fixture.decoder.releaseFailed() && !fixture.decoder.active(),
                "Sticky state may clear only after the successful retry");
        check(fixture.events.failures == 1, "Aborted first attempt must report a failure");
        fixture.first.failConfigure = fixture.first.failStart = false;
        fixture.feed();
        check(MediaCodec.createdCount() == 2 && fixture.decoder.active(), "Next frame may retry after release");
        check(fixture.events.failures == 1, "Successful restart must not report another failure");
        fixture.finish();
        scenarios++;
    }

    private static void normalStopReleaseFailure(boolean failStop, boolean hevc) {
        Fixture fixture = new Fixture(hevc);
        fixture.feed();
        MediaCodec running = MediaCodec.created(0);
        check(running.startCalls == 1 && running.queuedInputs == 1, "Normal stop test must start a codec");
        fixture.first.failStop = failStop;
        fixture.first.releaseFailures = -1;
        fixture.decoder.stopSession();
        check(running.stopCalls == 1 && running.releaseCalls == 1,
                "Stop failure must never skip release attempt");
        fixture.retained(running, "Normal session stop release failure");
        fixture.blockedWhileUnreleased(running);
        fixture.recoverOnSurfaceChange(running);
        scenarios++;
    }

    private static void stopFailureWithSuccessfulRelease(boolean hevc) {
        Fixture fixture = new Fixture(hevc);
        fixture.feed();
        MediaCodec running = MediaCodec.created(0);
        fixture.first.failStop = true;
        fixture.decoder.stopSession();
        check(running.stopCalls == 1 && running.releaseCalls == 1 && running.released,
                "A stop exception must still permit a successful release");
        check(!fixture.decoder.releaseFailed() && !fixture.decoder.active(),
                "A stop exception alone must not poison future sessions");
        fixture.finish();
        scenarios++;
    }

    public static void main(String[] args) {
        for (boolean hevc : new boolean[]{false, true}) {
            for (boolean failStart : new boolean[]{false, true}) {
                failedCandidateRetainsOwnership(failStart, hevc);
                safeFallbackAfterConfirmedRelease(failStart, hevc);
                transientCandidateReleaseFailure(failStart, hevc);
            }
            normalStopReleaseFailure(false, hevc);
            normalStopReleaseFailure(true, hevc);
            stopFailureWithSuccessfulRelease(hevc);
        }
        MediaCodec.resetForTest();
        System.out.println(scenarios + " AirPlay decoder lifecycle scenarios / " + checks
                + " checks passed (synthetic host tests, not device casting validation)");
    }
}
