// SPDX-License-Identifier: GPL-3.0-or-later
package com.mooncast.tv.gamestream.render;
import com.limelight.nvstream.av.audio.AudioRenderer;
import com.mooncast.receiver.RendererRelease;
import com.mooncast.tv.gamestream.testing.Synthetic;
public final class GameStreamAudioRenderer implements AudioRenderer, RendererRelease {
    public interface Listener { void onError(String code); }
    public final Listener listener;
    public volatile int stopCalls, cleanupCalls;
    public GameStreamAudioRenderer(Listener listener) { this.listener = listener; Synthetic.audios.incrementAndGet(); }
    public void start() { }
    public void stop() { stopCalls++; }
    public void cleanup() { Synthetic.workerOnly("audio cleanup"); cleanupCalls++; }
    public boolean awaitReleased(long timeoutMillis) {
        Synthetic.workerOnly("audio release wait"); Synthetic.audioRelease.pass(); return Synthetic.audioReleaseResult;
    }
}
