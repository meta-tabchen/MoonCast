package com.mooncast.host;

/** Capability facts for one numeric private-network host; names are never identity. */
final class MyTvDevice {
    static final int APK_PORT = 48152;
    final String host, name, identity, airplayIdentity;
    final int airplayPort, apkPort;
    final boolean manualAirplay;
    MyTvDevice(String host, String name, int airplayPort, int apkPort, String identity,
               String airplayIdentity, boolean manualAirplay) {
        if (!AirPlayEndpoint.localNumericHost(host)) throw new IllegalArgumentException("Local IPv4 required");
        this.host = host;
        this.name = clean(name == null || name.isEmpty() ? host : name);
        this.airplayPort = airplayPort > 0 && airplayPort <= 65535 ? airplayPort : 0;
        this.apkPort = apkPort == APK_PORT && ApkEndpoint.parse(host + ":" + APK_PORT) != null ? apkPort : 0;
        this.identity = cleanId(identity); this.airplayIdentity = cleanId(airplayIdentity);
        this.manualAirplay = manualAirplay;
    }
    boolean supportsApk() { return apkPort == APK_PORT; }
    boolean supportsAirplay() { return airplayPort > 0; }
    AirPlayEndpoint airplayEndpoint() { return supportsAirplay() ? new AirPlayEndpoint(host, airplayPort) : null; }
    ApkEndpoint apkEndpoint() { return supportsApk() ? ApkEndpoint.parse(host + ":" + apkPort) : null; }
    boolean canMerge(MyTvDevice other) {
        return host.equals(other.host) && compatible(identity, other.identity)
            && compatible(airplayIdentity, other.airplayIdentity);
    }
    private static boolean compatible(String a, String b) { return a.isEmpty() || b.isEmpty() || a.equals(b); }
    MyTvDevice merge(MyTvDevice other) {
        if (!canMerge(other)) throw new IllegalArgumentException("Conflicting identity");
        int selectedAirplay = airplayPort > 0 && (!manualAirplay || other.airplayPort == 0 || other.manualAirplay)
            ? airplayPort : other.airplayPort;
        return new MyTvDevice(host, name, selectedAirplay,
            apkPort > 0 ? apkPort : other.apkPort, identity.isEmpty() ? other.identity : identity,
            airplayIdentity.isEmpty() ? other.airplayIdentity : airplayIdentity,
            (airplayPort == 0 || manualAirplay) && (other.airplayPort == 0 || other.manualAirplay));
    }
    static String clean(String value) {
        String result = value.replaceAll("[\\p{Cntrl}\\p{Cf}]", "").trim();
        return result.substring(0, Math.min(80, result.length()));
    }
    private static String cleanId(String value) {
        return value == null || value.length() > 128 ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
