package com.mooncast.host;

/** Process-local capture exclusion. Both protocol services live in :host. A lease is held
 * through teardown until process exit, so delayed cleanup cannot overlap a new sender. */
final class SenderOwnership {
    private static Object owner;
    private SenderOwnership() {}
    static synchronized boolean isHeld() { return owner != null; }
    static synchronized boolean acquire(Object candidate) {
        if (owner != null && owner != candidate) return false;
        owner = candidate;
        return true;
    }
}
