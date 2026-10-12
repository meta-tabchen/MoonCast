/* SPDX-License-Identifier: GPL-3.0-only */
package android.view;

/** Synthetic ownership accounting, not an Android surface implementation. */
public final class Surface {
    private boolean valid = true;
    private int owners;
    public synchronized boolean isValid() { return valid; }
    public synchronized int codecOwners() { return owners; }
    public synchronized void acquireForCodec() {
        if (!valid) throw new AssertionError("Codec acquired a released surface");
        owners++;
    }
    public synchronized void releaseFromCodec() {
        if (owners <= 0) throw new AssertionError("Codec released an unowned surface");
        owners--;
    }
    public synchronized void release() {
        if (owners != 0) throw new AssertionError("Surface freed before confirmed codec release");
        valid = false;
    }
}
