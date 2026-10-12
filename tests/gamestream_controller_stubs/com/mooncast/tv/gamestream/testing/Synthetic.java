// SPDX-License-Identifier: GPL-3.0-or-later
package com.mooncast.tv.gamestream.testing;
import android.os.Looper;
import com.limelight.nvstream.NvConnection;
import com.mooncast.receiver.MoonlightHostClient;
import com.mooncast.tv.gamestream.ui.GameStreamDialog;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
/** Test scheduling and observations only. The production ownership algorithms are not copied. */
public final class Synthetic {
    public static final Gate identity = new Gate(), prepare = new Gate(), start = new Gate(), nativeStop = new Gate(),
        audioRelease = new Gate(), videoRelease = new Gate(), pair = new Gate(), save = new Gate(), storeLoad = new Gate();
    public static final AtomicInteger identityLoads = new AtomicInteger(), identityCreates = new AtomicInteger(),
        clients = new AtomicInteger(), prepares = new AtomicInteger(), starts = new AtomicInteger(), stops = new AtomicInteger(),
        closes = new AtomicInteger(), audios = new AtomicInteger(), videos = new AtomicInteger(),
        inspects = new AtomicInteger(), pairs = new AtomicInteger(), saves = new AtomicInteger(), loads = new AtomicInteger(), forgets = new AtomicInteger();
    public static volatile NvConnection connection;
    public static volatile MoonlightHostClient client;
    public static volatile GameStreamDialog dialog;
    public static volatile RuntimeException storeLoadFailure;
    public static volatile java.util.List<com.mooncast.tv.gamestream.hosts.HostRecord> savedHosts = java.util.List.of();
    public static volatile boolean audioReleaseResult = true, videoReleaseResult = true;
    public static void workerOnly(String action) {
        if (Looper.myLooper() == Looper.getMainLooper()) throw new AssertionError(action + " ran on main");
    }
    public static final class Gate {
        public final CountDownLatch entered = new CountDownLatch(1);
        private volatile CountDownLatch released = new CountDownLatch(0);
        public void hold() { released = new CountDownLatch(1); }
        public void release() { released.countDown(); }
        public void pass() {
            entered.countDown();
            try { if (!released.await(8, TimeUnit.SECONDS)) throw new AssertionError("Synthetic gate was not released"); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
        }
        public void awaitEntered() throws InterruptedException {
            if (!entered.await(5, TimeUnit.SECONDS)) throw new AssertionError("Synthetic operation did not enter");
        }
    }
    public static void releaseAll() {
        for (Gate gate : new Gate[] {identity, prepare, start, nativeStop, audioRelease, videoRelease, pair, save, storeLoad}) gate.release();
    }
}
