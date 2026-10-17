// SPDX-License-Identifier: GPL-3.0-or-later
package com.mooncast.tv.airplay;

import android.graphics.SurfaceTexture;
import android.view.Surface;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;

/** Serial surface attachment/retirement never makes the TV UI wait on a vendor codec. */
public final class AirPlaySurfaces {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "MoonCast-AirPlay-surfaces"); thread.setDaemon(true); return thread;
    });
    private AirPlaySurfaces() { }
    public static void attach(AirPlayEngine engine, Surface surface, BooleanSupplier stillCurrent) {
        WORKER.execute(() -> { if (stillCurrent.getAsBoolean()){ReceiverDiagnostics.event("surface-attach",0);engine.setSurface(surface);} });
    }
    public static void retire(AirPlayEngine engine, Surface surface, SurfaceTexture texture) {
        WORKER.execute(() -> {
            ReceiverDiagnostics.event("surface-retire",0);
            for (;;) {
                engine.clearSurface();
                if (!engine.hasVideoReleaseFailure()) {
                    if (surface != null) surface.release(); texture.release(); ReceiverDiagnostics.event("surface-retired",0); return;
                }
                // Failed release is not permission to free the producer or attach a new one.
                try { Thread.sleep(15000); } catch (InterruptedException ignored) { }
            }
        });
    }
}
