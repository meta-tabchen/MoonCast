// SPDX-License-Identifier: GPL-3.0-or-later
package com.mooncast.tv.gamestream;

import android.graphics.SurfaceTexture;
import android.view.Surface;
import com.mooncast.tv.gamestream.render.GameStreamVideoRenderer;

/** Never frees a TextureView producer while a vendor codec may still hold its consumer. */
public final class GameStreamSurfaces {
    private GameStreamSurfaces() { }
    public static void retire(Surface surface, SurfaceTexture texture, GameStreamVideoRenderer renderer) {
        if (surface == null) { texture.release(); return; }
        if (renderer == null) { surface.release(); texture.release(); return; }
        renderer.setSurface(null);
        Thread release = new Thread(() -> {
            // A vendor hang deliberately retains both objects until process exit. There is no
            // timeout that can turn an unconfirmed detach into permission to release a Surface.
            for (;;) {
                try {
                    if (renderer.awaitSurfaceDetached(surface, 15000)) {
                        surface.release(); texture.release(); return;
                    }
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) { /* Retain ownership until confirmed. */ }
            }
        }, "MoonCast-GameStream-surface-release");
        release.setDaemon(true); release.start();
    }
}
