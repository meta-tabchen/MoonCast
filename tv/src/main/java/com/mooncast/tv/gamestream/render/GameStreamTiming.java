/* SPDX-License-Identifier: GPL-3.0-only */
package com.mooncast.tv.gamestream.render;

/**
 * Honest timing contract for the current live GameStream path. Audio callbacks carry
 * decoded PCM but no shared source presentation timestamp. Codec submission timestamps
 * are receiver-monotonic scheduling values, not measured source-to-screen latency.
 *
 * Do not apply a file-player sample-trimming offset to this live path: advancing audio
 * requires holding video, and delaying audio requires a real bounded PCM buffer.
 */
public final class GameStreamTiming {
    public static final GameStreamTiming LOW_LATENCY = new GameStreamTiming();
    public final int relativeAudioOffsetMs = 0;
    public final boolean supportsSignedOffset = false;
    public final boolean measuredSourceClockSync = false;

    private GameStreamTiming() { }

    /** Fails explicitly instead of silently accepting an ineffective calibration setting. */
    public static GameStreamTiming withRelativeAudioOffsetMs(int offsetMs) {
        if (offsetMs != 0) {
            throw new IllegalArgumentException("Live GameStream A/V offset is not implemented");
        }
        return LOW_LATENCY;
    }
}
