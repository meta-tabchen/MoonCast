/* SPDX-License-Identifier: GPL-3.0-only */
package com.mooncast.tv.airplay;

/** Bounded PTS-relative scheduling. Native PTS uses Unix realtime, not System.nanoTime().
 * Matching initial audio/video cushions is approximate: JNI exports no audio playback clock. */
final class VideoTiming {
    private long ptsAnchorUs = Long.MIN_VALUE, wallAnchorNs;
    private boolean movie = true;
    private int cushionMs = 150;
    synchronized void configure(boolean movie, int cushionMs) {
        if (cushionMs < 0 || cushionMs > 500) throw new IllegalArgumentException("Invalid cushion");
        if (this.movie != movie || this.cushionMs != cushionMs) reset();
        this.movie = movie; this.cushionMs = cushionMs;
    }
    synchronized void reset() { ptsAnchorUs = Long.MIN_VALUE; }
    synchronized long target(long ptsUs, long nowNs) {
        if (!movie) return nowNs;
        long cushionNs = cushionMs * 1_000_000L;
        if (ptsAnchorUs == Long.MIN_VALUE) { ptsAnchorUs = ptsUs; wallAnchorNs = nowNs; }
        long deltaUs = ptsUs - ptsAnchorUs;
        if (deltaUs < -1_000_000L || deltaUs > 86_400_000_000L) {
            ptsAnchorUs = ptsUs; wallAnchorNs = nowNs; deltaUs = 0;
        }
        long target = wallAnchorNs + deltaUs * 1000L + cushionNs;
        if (target < nowNs - 250_000_000L || target > nowNs + cushionNs + 250_000_000L) {
            ptsAnchorUs = ptsUs; wallAnchorNs = nowNs; target = nowNs + cushionNs;
        }
        return Math.max(nowNs, target);
    }
}
