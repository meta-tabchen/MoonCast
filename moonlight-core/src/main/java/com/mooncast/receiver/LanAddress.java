// SPDX-License-Identifier: GPL-3.0-or-later
package com.mooncast.receiver;

import com.limelight.nvstream.http.ComputerDetails;
import java.net.InetAddress;
import java.net.UnknownHostException;

/** Literal private-LAN targets only. Avoids hostname rebinding and Internet probes. */
public final class LanAddress {
    private LanAddress() {}

    public static ComputerDetails.AddressTuple require(String value, int port) {
        if (value == null || port < 1 || port > 65535) throw new IllegalArgumentException("Invalid host or port");
        String host = value.trim();
        if (host.startsWith("[") && host.endsWith("]")) host = host.substring(1, host.length() - 1);
        try {
            InetAddress address;
            if (host.contains(":")) {
                // A colon forces Java's numeric IPv6 parser; it cannot perform a DNS lookup.
                address = InetAddress.getByName(host);
            } else {
                String[] parts = host.split("\\.", -1);
                if (parts.length != 4) throw new IllegalArgumentException("Enter a literal LAN IP address");
                byte[] bytes = new byte[4];
                for (int i = 0; i < 4; i++) {
                    if (!parts[i].matches("0|[1-9][0-9]{0,2}")) throw new IllegalArgumentException("Invalid IPv4 address");
                    int part = Integer.parseInt(parts[i]);
                    if (part > 255) throw new IllegalArgumentException("Invalid IPv4 address");
                    bytes[i] = (byte)part;
                }
                address = InetAddress.getByAddress(bytes);
            }
            byte[] bytes = address.getAddress();
            boolean privateAddress;
            if (bytes.length == 4) {
                int a = bytes[0] & 255, b = bytes[1] & 255;
                privateAddress = a == 10 || (a == 172 && b >= 16 && b <= 31) ||
                        (a == 192 && b == 168) || (a == 169 && b == 254);
            } else {
                privateAddress = (bytes[0] & 0xfe) == 0xfc || address.isLinkLocalAddress();
            }
            if (!privateAddress || address.isAnyLocalAddress() || address.isLoopbackAddress() ||
                    address.isMulticastAddress()) throw new IllegalArgumentException("Only private LAN host addresses are supported");
            return new ComputerDetails.AddressTuple(address.getHostAddress(), port);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid literal LAN IP address", e);
        }
    }

    public static ComputerDetails.AddressTuple require(ComputerDetails.AddressTuple address) {
        if (address == null) throw new IllegalArgumentException("Host is required");
        return require(address.address, address.port);
    }
}
