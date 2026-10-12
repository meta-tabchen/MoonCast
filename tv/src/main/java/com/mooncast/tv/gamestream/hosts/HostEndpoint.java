// SPDX-License-Identifier: GPL-3.0-or-later
package com.mooncast.tv.gamestream.hosts;

import com.mooncast.tv.net.LanAccess;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

/** A canonical private IPv4 literal. Parsing never performs a DNS lookup. */
public final class HostEndpoint {
    public static final int DEFAULT_HTTP_PORT = 47989;
    public final String address;
    public final int httpPort;
    private final Inet4Address literal;

    private HostEndpoint(Inet4Address literal, int port) {
        this.literal = literal;
        address = literal.getHostAddress();
        httpPort = port;
    }

    public static HostEndpoint parse(String value) {
        if (value == null || value.length() > 64) throw new IllegalArgumentException("Enter a literal IPv4 address and optional port");
        for (int i = 0; i < value.length(); i++) if (Character.isISOControl(value.charAt(i))) throw new IllegalArgumentException("Invalid host address");
        String input = value.trim();
        int separator = input.indexOf(':');
        if (separator < 0) return of(input, DEFAULT_HTTP_PORT);
        if (separator != input.lastIndexOf(':')) throw new IllegalArgumentException("Only IPv4 addresses are supported");
        String port = input.substring(separator + 1);
        if (!port.matches("[1-9][0-9]{0,4}")) throw new IllegalArgumentException("Invalid host port");
        return of(input.substring(0, separator), Integer.parseInt(port));
    }

    public static HostEndpoint of(String address, int port) {
        if (address == null || address.length() > 15 || port < 1 || port > 65535) throw new IllegalArgumentException("Invalid IPv4 host or port");
        String[] octets = address.split("\\.", -1);
        if (octets.length != 4) throw new IllegalArgumentException("Enter a literal IPv4 address");
        byte[] bytes = new byte[4];
        for (int i = 0; i < 4; i++) {
            if (!octets[i].matches("0|[1-9][0-9]{0,2}")) throw new IllegalArgumentException("Enter a canonical IPv4 address");
            int number = Integer.parseInt(octets[i]);
            if (number > 255) throw new IllegalArgumentException("Invalid IPv4 address");
            bytes[i] = (byte)number;
        }
        int a = bytes[0] & 255, b = bytes[1] & 255;
        if (!(a == 10 || a == 172 && b >= 16 && b <= 31 || a == 192 && b == 168)) {
            throw new IllegalArgumentException("Only private LAN IPv4 addresses are supported");
        }
        try { return new HostEndpoint((Inet4Address)InetAddress.getByAddress(bytes), port); }
        catch (UnknownHostException impossible) { throw new AssertionError(impossible); }
    }

    /** Call again immediately before every inspect, pairing, or connect operation. */
    public HostEndpoint requireOnLan(LanAccess lan) {
        if (!Objects.requireNonNull(lan).allows(literal)) throw new IllegalArgumentException("Host must be on the selected IPv4 subnet");
        return this;
    }

    public Inet4Address inetAddress() { return literal; }
    @Override public boolean equals(Object other) {
        return other instanceof HostEndpoint && httpPort == ((HostEndpoint)other).httpPort && address.equals(((HostEndpoint)other).address);
    }
    @Override public int hashCode() { return 31 * address.hashCode() + httpPort; }
    @Override public String toString() { return address + ":" + httpPort; }
}
