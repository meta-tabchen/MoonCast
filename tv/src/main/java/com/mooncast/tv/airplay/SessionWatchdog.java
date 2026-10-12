/* SPDX-License-Identifier: GPL-3.0-only */
package com.mooncast.tv.airplay;

/** Conservative session liveness: silence alone cannot distinguish a disconnected sender
 * from a paused/static screen. Pinned JNI does not forward pause/feedback callbacks. */
final class SessionWatchdog {
    static final String DECODER_STALLED = "Video decoder stopped producing frames";
    static final String SENDER_DISCONNECTED = "Sender disconnected";
    private long timeoutMs = 15_000;
    private int connections;
    private boolean mirror, emitted;
    private long disconnectedAt = -1, lastPacketAt = -1, pendingVideoAt = -1;
    synchronized void setTimeoutSeconds(int seconds) {
        if (seconds != 0 && (seconds < 5 || seconds > 120))
            throw new IllegalArgumentException("Watchdog must be off or 5–120 seconds");
        timeoutMs = seconds * 1000L;
    }
    synchronized int timeoutSeconds() { return (int) (timeoutMs / 1000); }
    synchronized void reset() {
        connections = 0; mirror = false; emitted = false;
        disconnectedAt = lastPacketAt = pendingVideoAt = -1;
    }
    synchronized void connectionOpened() { connections++; disconnectedAt = -1; }
    synchronized void connectionClosed(long now) {
        if (connections > 0) connections--;
        if (connections == 0 && mirror && disconnectedAt < 0) disconnectedAt = now;
    }
    synchronized void beginMirror(long now) {
        if (mirror) return;
        mirror = true; emitted = false; lastPacketAt = now; pendingVideoAt = -1;
        disconnectedAt = -1; // No init callback is not evidence of a closed connection.
    }
    synchronized void endMirror() { mirror = false; pendingVideoAt = -1; disconnectedAt = -1; }
    synchronized void videoPacket(long now, boolean presentable) {
        if (!mirror) beginMirror(now);
        lastPacketAt = now;
        if (presentable && pendingVideoAt < 0) pendingVideoAt = now;
    }
    synchronized void videoOutput() { pendingVideoAt = -1; }
    synchronized int connections() { return connections; }
    synchronized String check(long now) {
        if (!mirror || emitted || timeoutMs == 0) return null;
        if (pendingVideoAt >= 0 && elapsed(now, pendingVideoAt) >= timeoutMs) {
            emitted = true; return DECODER_STALLED;
        }
        if (connections == 0 && disconnectedAt >= 0 && elapsed(now, disconnectedAt) >= timeoutMs
                && lastPacketAt >= 0 && elapsed(now, lastPacketAt) >= timeoutMs) {
            emitted = true; return SENDER_DISCONNECTED;
        }
        return null;
    }
    private static long elapsed(long now, long since) { return Math.max(0, now - since); }
}
