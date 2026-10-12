/* SPDX-License-Identifier: GPL-3.0-only */
package android.view;
public final class Surface {
    private volatile boolean valid = true;
    public boolean isValid() { return valid; }
    public void release() { valid = false; }
}
