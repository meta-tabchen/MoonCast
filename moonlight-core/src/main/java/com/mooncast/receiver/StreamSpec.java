// SPDX-License-Identifier: GPL-3.0-or-later
package com.mooncast.receiver;

/** Conservative SDR AVC/stereo prototype. Bitrate is in kbit/s, not bit/s. */
public final class StreamSpec {
    public final int width, height, framesPerSecond, bitrateKbps;
    public StreamSpec(int width, int height, int framesPerSecond, int bitrateKbps) {
        if (width < 128 || width > 3840 || height < 128 || height > 2160 ||
                (width & 1) != 0 || (height & 1) != 0) {
            throw new IllegalArgumentException("Prototype requires even AVC dimensions up to 3840x2160");
        }
        if (framesPerSecond < 1 || framesPerSecond > 60) {
            throw new IllegalArgumentException("Prototype frame rate must be 1–60");
        }
        if (bitrateKbps < 1000 || bitrateKbps > 100000) {
            throw new IllegalArgumentException("Bitrate must be 1000–100000 kbit/s");
        }
        this.width = width;
        this.height = height;
        this.framesPerSecond = framesPerSecond;
        this.bitrateKbps = bitrateKbps;
    }
    public static StreamSpec conservative1080p() { return new StreamSpec(1920, 1080, 60, 30000); }
}
