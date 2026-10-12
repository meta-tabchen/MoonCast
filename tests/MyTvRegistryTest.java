package com.mooncast.host;
import java.util.List;
public final class MyTvRegistryTest {
    static MyTvDevice device(String host, String name, int air, int apk, String id, String airId) {
        return new MyTvDevice(host, name, air, apk, id, airId, false);
    }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        MyTvRegistry r = new MyTvRegistry();
        MyTvDevice air = device("192.168.1.20", "Living room", 7000, 0, "", "aa:bb");
        MyTvDevice apk = device("192.168.1.20", "MoonCast", 0, 48152, "receiver-a", "");
        r.put("air", air); r.put("apk", apk);
        check(r.snapshot().size() == 1 && r.snapshot().get(0).supportsApk() && r.snapshot().get(0).supportsAirplay(), "same private host merges capabilities");
        r.remove("apk"); check(!r.snapshot().get(0).supportsApk(), "lost APK service removes capability");
        r.put("same-name-other-host", device("192.168.1.21", "Living room", 7000, 0, "", "cc:dd"));
        check(r.snapshot().size() == 2, "same names never merge different hosts");
        r.clear(); r.put("a", apk); r.put("b", device(apk.host, "MoonCast", 0, 48152, "receiver-b", ""));
        check(r.snapshot().size() == 2, "conflicting receiver IDs never merge");
        r.put("unknown", air); check(r.snapshot().size() == 3, "ambiguous host cannot bridge conflicting IDs");
        r.clear(); r.put("unknown", air); r.put("a", apk); r.put("b", device(apk.host, "Other", 0, 48152, "receiver-b", ""));
        check(r.snapshot().size() == 3, "conflict behavior does not depend on service arrival order");
        r.clear(); r.put("native", air); check(!r.snapshot().get(0).supportsApk(), "native AirPlay has no implied APK capability");
        check(device(air.host, "TV", 7000, 12345, "", "").apkEndpoint() == null, "APK port is fixed");
        boolean rejected = false; try { device("8.8.8.8", "bad", 7000, 0, "", ""); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "public hosts rejected");
        check(!device(air.host, "TV", 7000, 0, "", "aa").canMerge(device(air.host, "TV", 7000, 0, "", "bb")), "conflicting AirPlay stable identities rejected");
        MyTvDevice manual = new MyTvDevice(air.host, "manual", 7000, 0, "", "", true);
        MyTvDevice verified = device(air.host, "real", 7100, 0, "", "aa:bb");
        check(manual.merge(verified).airplayPort == 7100 && !manual.merge(verified).manualAirplay, "verified AirPlay port replaces manual fallback");
        check(verified.merge(manual).airplayPort == 7100 && !verified.merge(manual).manualAirplay, "manual fallback cannot replace verified port");
        System.out.println("MyTvRegistryTest passed: identity, capability, loss, ambiguity, private-host invariants");
    }
}
