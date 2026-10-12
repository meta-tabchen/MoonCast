// SPDX-License-Identifier: GPL-3.0-or-later
package com.limelight.nvstream;
import com.limelight.nvstream.av.audio.AudioRenderer;
import com.limelight.nvstream.av.video.VideoDecoderRenderer;
import com.mooncast.tv.gamestream.testing.Synthetic;
/** Deliberate scheduling injection, not an emulation of native startup or its latency. */
public final class NvConnection {
    public volatile NvConnectionListener listener;
    public NvConnection() { Synthetic.connection = this; }
    public void start(AudioRenderer audio, VideoDecoderRenderer video, NvConnectionListener listener) {
        Synthetic.workerOnly("connection.start"); this.listener = listener;
        Synthetic.starts.incrementAndGet(); Synthetic.start.pass();
    }
    public void stop() {
        Synthetic.workerOnly("connection.stop"); Synthetic.stops.incrementAndGet(); Synthetic.nativeStop.pass();
    }
    public boolean awaitStartupComplete(long timeoutMillis) { return true; }
}
