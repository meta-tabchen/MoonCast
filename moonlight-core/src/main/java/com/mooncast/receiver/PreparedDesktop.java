// SPDX-License-Identifier: GPL-3.0-or-later
package com.mooncast.receiver;

import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.NvConnectionListener;
import com.limelight.nvstream.av.audio.AudioRenderer;
import com.limelight.nvstream.av.video.VideoDecoderRenderer;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** One-shot, view-only Desktop session. No raw connection or input API is exposed. */
public final class PreparedDesktop {
    public final StreamSpec spec;
    public final int desktopAppId;
    private final NvConnection connection;
    private final Consumer<Runnable> startGate;
    private final AtomicBoolean used = new AtomicBoolean();

    PreparedDesktop(NvConnection connection, StreamSpec spec, int desktopAppId,
            Consumer<Runnable> startGate) {
        this.connection = connection;
        this.spec = spec;
        this.desktopAppId = desktopAppId;
        this.startGate = startGate;
    }

    public ReceiverSession start(AudioRenderer audio, VideoDecoderRenderer video,
            NvConnectionListener listener) {
        Objects.requireNonNull(audio); Objects.requireNonNull(video); Objects.requireNonNull(listener);
        if (!(audio instanceof RendererRelease) || !(video instanceof RendererRelease)) {
            throw new IllegalArgumentException("Render adapters must implement RendererRelease");
        }
        ReceiverSession[] result = new ReceiverSession[1];
        startGate.accept(() -> {
            if (!used.compareAndSet(false, true)) throw new IllegalStateException("PreparedDesktop is one-shot");
            result[0] = new ReceiverSession(connection, audio, video, listener);
        });
        return result[0];
    }
}
