// SPDX-License-Identifier: GPL-3.0-or-later
package android.os;
public final class Handler {
    private final Looper looper;
    public Handler(Looper looper) { this.looper = looper; }
    public boolean post(Runnable action) { looper.post(action); return true; }
}
