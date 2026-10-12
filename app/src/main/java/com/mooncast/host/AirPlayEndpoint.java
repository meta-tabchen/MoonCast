package com.mooncast.host;

/** Numeric local-network endpoints only. Parsing never resolves an arbitrary hostname. */
final class AirPlayEndpoint {
    final String host;
    final int port;
    AirPlayEndpoint(String host, int port) { this.host = host; this.port = port; }
    static AirPlayEndpoint parse(String input) {
        if (input == null) return null;
        String value = input.trim(), host;
        int port = 7000;
        try {
            if (value.startsWith("[")) {
                int end = value.indexOf(']');
                if (end < 0) return null;
                host = value.substring(1, end);
                if (end + 1 < value.length()) {
                    if (value.charAt(end + 1) != ':') return null;
                    port = parsePort(value.substring(end + 2));
                }
            } else if (value.indexOf(':') == value.lastIndexOf(':') && value.contains(":")) {
                int colon = value.indexOf(':');
                host = value.substring(0, colon);
                port = parsePort(value.substring(colon + 1));
            } else host = value;
            if (port < 1 || port > 65535 || !localNumericHost(host)) return null;
            return new AirPlayEndpoint(host, port);
        } catch (IllegalArgumentException e) { return null; }
    }
    private static int parsePort(String port) {
        if (!port.matches("[0-9]{1,5}")) throw new IllegalArgumentException();
        return Integer.parseInt(port);
    }
    static boolean localNumericHost(String host) {
        if (host == null || host.isEmpty()) return false;
        if (!host.contains(":")) {
            String[] parts = host.split("\\.", -1);
            if (parts.length != 4) return false;
            int[] values = new int[4];
            for (int i = 0; i < 4; i++) {
                if (!parts[i].matches("[0-9]{1,3}")) return false;
                values[i] = Integer.parseInt(parts[i]);
                if (values[i] > 255 || (parts[i].length() > 1 && parts[i].startsWith("0"))) return false;
            }
            return values[0] == 10 || (values[0] == 172 && values[1] >= 16 && values[1] <= 31)
                || (values[0] == 192 && values[1] == 168) || (values[0] == 169 && values[1] == 254)
                || (values[0] == 100 && values[1] >= 64 && values[1] <= 127);
        }
        return false; // This sender runtime currently accepts numeric IPv4 only.
    }
    String address() { return (host.contains(":") ? "[" + host + "]" : host) + ":" + port; }
}
