// SPDX-License-Identifier: GPL-3.0-or-later
package com.mooncast.tv.gamestream.hosts;

import java.util.Objects;

/** Discovery is a hint, never proof of identity or pairing. */
public final class DiscoveredHost {
    public final HostEndpoint endpoint;
    public final String displayName;
    public DiscoveredHost(HostEndpoint endpoint, String displayName) {
        this.endpoint = Objects.requireNonNull(endpoint);
        this.displayName = HostRecord.safeDisplayName(displayName);
    }
}
