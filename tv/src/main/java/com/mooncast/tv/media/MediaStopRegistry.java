// SPDX-License-Identifier: GPL-3.0-or-later
package com.mooncast.tv.media;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Process-scoped stop debt survives singleTask Activity destruction/recreation. */
public final class MediaStopRegistry {
    private CompletableFuture<Void> pending = CompletableFuture.completedFuture(null);
    public synchronized CompletableFuture<Void> snapshot() { return pending; }
    public synchronized CompletableFuture<Void> record(CompletableFuture<Void> stopped) {
        pending = CompletableFuture.allOf(pending, Objects.requireNonNull(stopped));
        return pending;
    }
}
