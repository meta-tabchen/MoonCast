package com.mooncast.host;

public final class AirPlayEndpointTest {
    public static void main(String[] args) {
        for (String input : new String[]{"192.168.1.9", "10.0.0.2:7100", "172.16.0.1:1", "172.31.255.254:65535", "169.254.1.2", "100.64.1.2", " 192.168.0.2:7000 "})
            if (AirPlayEndpoint.parse(input) == null) throw new AssertionError("valid " + input);
        for (String input : new String[]{"", "example.com", "http://192.168.1.2", "8.8.8.8", "127.0.0.1", "172.32.1.2", "100.128.0.1", "192.168.256.1", "192.168.1.1:0", "192.168.1.1:65536", "192.168.1.1:", "192.168.01.1", "192.168.1.1:+7000", "[fd00::1]:7000", "fd00::1"})
            if (AirPlayEndpoint.parse(input) != null) throw new AssertionError("invalid " + input);
        AirPlayEndpoint plain = AirPlayEndpoint.parse("192.168.1.9");
        if (plain.port != 7000 || !plain.address().equals("192.168.1.9:7000")) throw new AssertionError("default port");
        System.out.println("PASS: AirPlay LAN-only numeric receiver and port validation");
    }
}
