// SPDX-License-Identifier: GPL-3.0-or-later
package com.mooncast.receiver;

/** Actual vendor-resource release, beyond merely posting a cleanup message. */
public interface RendererRelease {
    boolean awaitReleased(long timeoutMillis);
}
