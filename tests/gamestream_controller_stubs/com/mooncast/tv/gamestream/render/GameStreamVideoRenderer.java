// SPDX-License-Identifier: GPL-3.0-or-later
package com.mooncast.tv.gamestream.render;
import android.view.Surface;
import com.limelight.nvstream.av.video.VideoDecoderRenderer;
import com.mooncast.receiver.RendererRelease;
import com.mooncast.tv.gamestream.testing.Synthetic;
public final class GameStreamVideoRenderer extends VideoDecoderRenderer implements RendererRelease {
    public interface Listener { void onVideoSizeChanged(int width, int height); void onError(String code); }
    public final Listener listener;
    public int stopCalls, cleanupCalls;
    public GameStreamVideoRenderer(Surface surface, Listener listener) { this.listener = listener; Synthetic.videos.incrementAndGet(); }
    public void setSurface(Surface surface) { }
    public void start() { }
    public void stop() { stopCalls++; }
    public void cleanup() { Synthetic.workerOnly("video cleanup"); cleanupCalls++; }
    public boolean awaitReleased(long timeoutMillis) {
        Synthetic.workerOnly("video release wait"); Synthetic.videoRelease.pass(); return Synthetic.videoReleaseResult;
    }
}
