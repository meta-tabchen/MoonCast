package com.mooncast.host;

/** Teardown fallback when the UI missed Binder death. Other :host components are not senders. */
final class SenderLiveness {
    static final long KNOWN_PID_GONE_GRACE_MS = 700;
    // Both sender services arm a 4-second process-exit watchdog before cleanup.
    static final long NO_PID_SERVICE_ABSENT_GRACE_MS = 5500;
    private SenderLiveness() {}
    /** -1 means keep waiting; otherwise require continuous absence for this duration. */
    static long absenceGrace(int senderPid, boolean processSnapshotKnown, boolean senderPidAlive,
            boolean serviceSnapshotKnown, boolean senderServicePresent) {
        if (!serviceSnapshotKnown || senderServicePresent) return -1;
        if (senderPid > 0)
            return processSnapshotKnown && !senderPidAlive ? KNOWN_PID_GONE_GRACE_MS : -1;
        return NO_PID_SERVICE_ABSENT_GRACE_MS;
    }
}
