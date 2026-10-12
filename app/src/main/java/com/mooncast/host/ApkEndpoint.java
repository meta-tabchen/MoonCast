package com.mooncast.host;

import java.net.InetAddress;
import java.net.UnknownHostException;

/** Numeric RFC1918 IPv4 only. Parsing never performs DNS or accepts URLs. */
final class ApkEndpoint {
    final InetAddress address;
    final int port;
    private ApkEndpoint(InetAddress address, int port) { this.address = address; this.port = port; }

    static ApkEndpoint parse(String text) {
        if (text == null || text.length() > 22 || !text.matches("[0-9.]+:[0-9]{1,5}")) return null;
        int colon = text.indexOf(':');
        String[] octets = text.substring(0, colon).split("\\.", -1);
        if (octets.length != 4) return null;
        byte[] bytes = new byte[4];
        try {
            for (int i = 0; i < 4; i++) {
                if (octets[i].isEmpty() || octets[i].length() > 3
                    || octets[i].length() > 1 && octets[i].charAt(0) == '0') return null;
                int value = Integer.parseInt(octets[i]);
                if (value > 255) return null;
                bytes[i] = (byte) value;
            }
            int port = Integer.parseInt(text.substring(colon + 1));
            return from(InetAddress.getByAddress(bytes), port);
        } catch (NumberFormatException | UnknownHostException ignored) { return null; }
    }

    static ApkEndpoint from(InetAddress address, int port) {
        if (address == null || port < 1 || port > 65535) return null;
        byte[] bytes = address.getAddress();
        if (bytes.length != 4) return null;
        int first = bytes[0] & 255, second = bytes[1] & 255;
        if (!(first == 10 || first == 172 && second >= 16 && second <= 31 || first == 192 && second == 168)) return null;
        return new ApkEndpoint(address, port);
    }

    String display() { return address.getHostAddress() + ":" + port; }
    boolean same(ApkEndpoint other) { return other != null && port == other.port && address.equals(other.address); }
}
