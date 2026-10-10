package com.mooncast.tv.net;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;

/** Deliberately IPv4-only: discovery and control belong to one explicitly selected LAN interface. */
public final class LanAccess {
    private final byte[] local;
    private final int prefix;
    public LanAccess(Inet4Address address, int prefixLength) {
        if (address.isAnyLocalAddress() || address.isMulticastAddress() || prefixLength < 1 || prefixLength > 32) {
            throw new IllegalArgumentException("A concrete IPv4 interface and valid prefix are required");
        }
        local = address.getAddress();
        prefix = prefixLength;
    }
    public boolean allows(InetAddress peer) {
        if (!(peer instanceof Inet4Address) || peer.isAnyLocalAddress() || peer.isMulticastAddress()) return false;
        byte[] remote = peer.getAddress();
        int remaining = prefix;
        for (int i = 0; i < 4 && remaining > 0; i++) {
            int mask = (0xff << (8 - Math.min(8, remaining))) & 0xff;
            if ((local[i] & mask) != (remote[i] & mask)) return false;
            remaining -= 8;
        }
        // Do not allow multicast/broadcast source addresses through a broad interface prefix.
        if ((remote[0] & 0xff) >= 224) return false;
        if (prefix < 31) {
            long hostMask = (1L << (32 - prefix)) - 1;
            long bits = ((remote[0] & 255L) << 24) | ((remote[1] & 255L) << 16)
                    | ((remote[2] & 255L) << 8) | (remote[3] & 255L);
            if ((bits & hostMask) == 0 || (bits & hostMask) == hostMask) return false;
        }
        return true;
    }
    /** Eventing cannot be used as a LAN scanner or to reflect traffic at another machine. */
    static URI callback(String value, InetAddress peer) {
        if (value == null || value.length() > 2048 || !value.startsWith("<") || !value.endsWith(">")) {
            throw new IllegalArgumentException("One callback URL is required");
        }
        URI uri = URI.create(value.substring(1, value.length() - 1));
        if (!"http".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null || uri.getFragment() != null
                || uri.getHost() == null || !uri.getHost().equals(peer.getHostAddress())
                || uri.getPort() == 0 || uri.getPort() > 65535) {
            throw new IllegalArgumentException("Callback must be HTTP on the subscribing peer's literal address");
        }
        return uri;
    }
    /** A receiver is allowed to fetch ordinary LAN/public HTTP media, never local file/content schemes. */
    public static String validateMediaUri(String value) {
        if (value == null || value.isEmpty() || value.length() > 8192) throw new IllegalArgumentException("Missing media URL");
        URI uri = URI.create(value);
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
                || uri.getPort() == 0 || uri.getPort() > 65535) throw new IllegalArgumentException("HTTP(S) media URL required");
        // Numeric loopback, unspecified and link-local infrastructure endpoints are never media sources.
        String host = uri.getHost().toLowerCase(java.util.Locale.ROOT);
        while (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        if (host.equals("100.100.100.200") || host.equals("metadata.google.internal") || host.equals("instance-data")
                || host.endsWith(".ec2.internal")) throw new IllegalArgumentException("Infrastructure URL refused");
        if (host.matches("(?i)(?:0x[0-9a-f]+|[0-9]+)(?:\\.(?:0x[0-9a-f]+|[0-9]+)){0,3}") && host.contains("x")) {
            throw new IllegalArgumentException("Noncanonical IPv4 address");
        }
        if (host.equals("[fd00:ec2::254]")) throw new IllegalArgumentException("Infrastructure URL refused");
        if (host.matches("[0-9.]+")) {
            String[] octets = host.split("\\.", -1);
            if (octets.length != 4) throw new IllegalArgumentException("Noncanonical IPv4 address");
            for (String octet : octets) {
                if (!octet.matches("0|[1-9][0-9]{0,2}") || Integer.parseInt(octet) > 255) {
                    throw new IllegalArgumentException("Noncanonical IPv4 address");
                }
            }
            if (Integer.parseInt(octets[0]) >= 224 || octets[0].equals("0")) throw new IllegalArgumentException("Not unicast media");
        }
        if (host.startsWith("[")) {
            try {
                InetAddress literal = InetAddress.getByName(host);
                if (literal.isAnyLocalAddress() || literal.isLoopbackAddress() || literal.isLinkLocalAddress()
                        || literal.isMulticastAddress()) throw new IllegalArgumentException("Local system URL refused");
            } catch (java.net.UnknownHostException e) { throw new IllegalArgumentException("Invalid IPv6 address", e); }
        }
        if (host.equals("localhost") || host.endsWith(".localhost") || host.equals("0.0.0.0")
                || host.startsWith("127.") || host.startsWith("169.254.") || host.equals("[::1]")
                || host.equals("[::]") || host.startsWith("[fe80:") || host.startsWith("[ff")) {
            throw new IllegalArgumentException("Local system URLs are not media sources");
        }
        return value;
    }
}
