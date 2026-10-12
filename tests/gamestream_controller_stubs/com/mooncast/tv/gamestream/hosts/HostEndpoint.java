// SPDX-License-Identifier: GPL-3.0-or-later
package com.mooncast.tv.gamestream.hosts;
import com.mooncast.tv.net.LanAccess;
public final class HostEndpoint {
    public final String address = "192.168.77.2";
    public final int httpPort = 47989;
    public HostEndpoint requireOnLan(LanAccess lan) {
        if (lan == null || !lan.allowed) throw new IllegalArgumentException("Synthetic LAN rejected"); return this;
    }
}
