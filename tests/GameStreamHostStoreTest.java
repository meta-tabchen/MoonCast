// SPDX-License-Identifier: GPL-3.0-or-later
package com.mooncast.tv.gamestream.hosts;

import com.mooncast.tv.net.LanAccess;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Public CA certificates only. No generated/stored private identity and no network tests. */
public final class GameStreamHostStoreTest {
    // Public ISRG trust roots: https://letsencrypt.org/certificates/
    private static final String ROOT_X1 = """
-----BEGIN CERTIFICATE-----
MIIFazCCA1OgAwIBAgIRAIIQz7DSQONZRGPgu2OCiwAwDQYJKoZIhvcNAQELBQAw
TzELMAkGA1UEBhMCVVMxKTAnBgNVBAoTIEludGVybmV0IFNlY3VyaXR5IFJlc2Vh
cmNoIEdyb3VwMRUwEwYDVQQDEwxJU1JHIFJvb3QgWDEwHhcNMTUwNjA0MTEwNDM4
WhcNMzUwNjA0MTEwNDM4WjBPMQswCQYDVQQGEwJVUzEpMCcGA1UEChMgSW50ZXJu
ZXQgU2VjdXJpdHkgUmVzZWFyY2ggR3JvdXAxFTATBgNVBAMTDElTUkcgUm9vdCBY
MTCCAiIwDQYJKoZIhvcNAQEBBQADggIPADCCAgoCggIBAK3oJHP0FDfzm54rVygc
h77ct984kIxuPOZXoHj3dcKi/vVqbvYATyjb3miGbESTtrFj/RQSa78f0uoxmyF+
0TM8ukj13Xnfs7j/EvEhmkvBioZxaUpmZmyPfjxwv60pIgbz5MDmgK7iS4+3mX6U
A5/TR5d8mUgjU+g4rk8Kb4Mu0UlXjIB0ttov0DiNewNwIRt18jA8+o+u3dpjq+sW
T8KOEUt+zwvo/7V3LvSye0rgTBIlDHCNAymg4VMk7BPZ7hm/ELNKjD+Jo2FR3qyH
B5T0Y3HsLuJvW5iB4YlcNHlsdu87kGJ55tukmi8mxdAQ4Q7e2RCOFvu396j3x+UC
B5iPNgiV5+I3lg02dZ77DnKxHZu8A/lJBdiB3QW0KtZB6awBdpUKD9jf1b0SHzUv
KBds0pjBqAlkd25HN7rOrFleaJ1/ctaJxQZBKT5ZPt0m9STJEadao0xAH0ahmbWn
OlFuhjuefXKnEgV4We0+UXgVCwOPjdAvBbI+e0ocS3MFEvzG6uBQE3xDk3SzynTn
jh8BCNAw1FtxNrQHusEwMFxIt4I7mKZ9YIqioymCzLq9gwQbooMDQaHWBfEbwrbw
qHyGO0aoSCqI3Haadr8faqU9GY/rOPNk3sgrDQoo//fb4hVC1CLQJ13hef4Y53CI
rU7m2Ys6xt0nUW7/vGT1M0NPAgMBAAGjQjBAMA4GA1UdDwEB/wQEAwIBBjAPBgNV
HRMBAf8EBTADAQH/MB0GA1UdDgQWBBR5tFnme7bl5AFzgAiIyBpY9umbbjANBgkq
hkiG9w0BAQsFAAOCAgEAVR9YqbyyqFDQDLHYGmkgJykIrGF1XIpu+ILlaS/V9lZL
ubhzEFnTIZd+50xx+7LSYK05qAvqFyFWhfFQDlnrzuBZ6brJFe+GnY+EgPbk6ZGQ
3BebYhtF8GaV0nxvwuo77x/Py9auJ/GpsMiu/X1+mvoiBOv/2X/qkSsisRcOj/KK
NFtY2PwByVS5uCbMiogziUwthDyC3+6WVwW6LLv3xLfHTjuCvjHIInNzktHCgKQ5
ORAzI4JMPJ+GslWYHb4phowim57iaztXOoJwTdwJx4nLCgdNbOhdjsnvzqvHu7Ur
TkXWStAmzOVyyghqpZXjFaH3pO3JLF+l+/+sKAIuvtd7u+Nxe5AW0wdeRlN8NwdC
jNPElpzVmbUq4JUagEiuTDkHzsxHpFKVK7q4+63SM1N95R1NbdWhscdCb+ZAJzVc
oyi3B43njTOQ5yOf+1CceWxG1bQVs5ZufpsMljq4Ui0/1lvh+wjChP4kqKOJ2qxq
4RgqsahDYVvTH9w7jXbyLeiNdd8XM2w9U/t7y0Ff/9yi0GE44Za4rF2LN9d11TPA
mRGunUHBcnWEvgJBQl9nJEiU0Zsnvgc/ubhPgXRR4Xq37Z0j4r7g1SgEEzwxA57d
emyPxgcYxn/eR44/KJ4EBs+lVDR3veyJm+kXQ99b21/+jh5Xos1AnX5iItreGCc=
-----END CERTIFICATE-----
""";
    private static final String ROOT_X2 = """
-----BEGIN CERTIFICATE-----
MIICGzCCAaGgAwIBAgIQQdKd0XLq7qeAwSxs6S+HUjAKBggqhkjOPQQDAzBPMQsw
CQYDVQQGEwJVUzEpMCcGA1UEChMgSW50ZXJuZXQgU2VjdXJpdHkgUmVzZWFyY2gg
R3JvdXAxFTATBgNVBAMTDElTUkcgUm9vdCBYMjAeFw0yMDA5MDQwMDAwMDBaFw00
MDA5MTcxNjAwMDBaME8xCzAJBgNVBAYTAlVTMSkwJwYDVQQKEyBJbnRlcm5ldCBT
ZWN1cml0eSBSZXNlYXJjaCBHcm91cDEVMBMGA1UEAxMMSVNSRyBSb290IFgyMHYw
EAYHKoZIzj0CAQYFK4EEACIDYgAEzZvVn4CDCuwJSvMWSj5cz3es3mcFDR0HttwW
+1qLFNvicWDEukWVEYmO6gbf9yoWHKS5xcUy4APgHoIYOIvXRdgKam7mAHf7AlF9
ItgKbppbd9/w+kHsOdx1ymgHDB/qo0IwQDAOBgNVHQ8BAf8EBAMCAQYwDwYDVR0T
AQH/BAUwAwEB/zAdBgNVHQ4EFgQUfEKWrt5LSDv6kviejM9ti6lyN5UwCgYIKoZI
zj0EAwMDaAAwZQIwe3lORlCEwkSHRhtFcP9Ymd70/aTSVaYgLXTWNLxBo1BfASdW
tL4ndQavEi51mI38AjEAi/V3bNTIZargCyzuFJ0nN6T5U6VR5CmD1/iQMVtCnwr1
/q4AaOeMSQ+2b1tbFfLn
-----END CERTIFICATE-----
""";
    private static int assertions;
    public static void main(String[] args) throws Exception {
        LanAccess lan = new LanAccess((Inet4Address)InetAddress.getByAddress(new byte[] {(byte)192, (byte)168, 4, 2}), 24);
        HostEndpoint endpoint = HostEndpoint.parse(" 192.168.4.11:47989 ").requireOnLan(lan);
        check(endpoint.equals(HostEndpoint.of("192.168.4.11", 47989)), "Endpoint canonical equality");
        check(HostEndpoint.parse("192.168.4.11").httpPort == 47989, "Default HTTP port");
        check(HostEndpoint.parse("10.1.2.3:65535").httpPort == 65535, "Upper port bound");
        for (String invalid : new String[] {"localhost", "host.local", "https://192.168.4.11", "192.168.4.11/path", "192.168.4.11?x", "192.168.4.11#x", "192.168.4.11@host", "127.0.0.1", "0.0.0.0", "169.254.1.2", "8.8.8.8", "224.1.2.3", "255.255.255.255", "172.32.1.2", "192.168.004.11", "192.168.4", "192.168.4.256", "192.168.4.-1", "192.168.4.11:0", "192.168.4.11:65536", "192.168.4.11:+1", "192.168.4.11:00080", "192.168.4.11: 80", "192.168.4.11:", "[::1]", "192.168.4.11:80:81", "192.168.4.11\u0000"}) {
            illegal(() -> HostEndpoint.parse(invalid));
        }
        illegal(() -> HostEndpoint.parse("192.168.5.11").requireOnLan(lan));
        illegal(() -> HostEndpoint.parse("192.168.4.0").requireOnLan(lan));
        illegal(() -> HostEndpoint.parse("192.168.4.255").requireOnLan(lan));
        illegal(() -> HostEndpoint.of("192.168.4.11", -1));
        X509Certificate first = cert(ROOT_X1), changed = cert(ROOT_X2);
        HostRecord original = new HostRecord(endpoint, "Living room", 47984, "host-123", first);
        HostRecord reloaded = HostStoreCodec.decode(HostStoreCodec.encode(List.of(original))).get(0);
        check(reloaded.endpoint.equals(endpoint) && reloaded.hasSamePin(original), "Certificate pin and endpoint round trip");
        check(reloaded.displayName.equals("Living room") && reloaded.serverUuid.equals("host-123") && reloaded.httpsPort == 47984, "Metadata round trip");
        byte[] escaped = original.encodedCertificate();
        escaped[0] ^= 1;
        check(original.hasSamePin(reloaded), "Certificate DER defensive copy");
        HostRecord newMetadata = new HostRecord(endpoint, "Renamed", 47984, "host-123", first);
        check(HostStoreCodec.upsert(List.of(original), newMetadata).get(0).displayName.equals("Renamed"), "Same certificate update");
        HostEndpoint moved = HostEndpoint.parse("192.168.4.21");
        HostRecord movedRecord = new HostRecord(moved, "Moved", 47984, "host-123", first);
        List<HostRecord> migrated = HostStoreCodec.upsert(List.of(original), movedRecord);
        check(migrated.size() == 1 && migrated.get(0).endpoint.equals(moved), "Authenticated same-pin address move");
        fails(() -> HostStoreCodec.upsert(List.of(original), new HostRecord(endpoint, "Changed", 47984, "host-123", changed)));
        fails(() -> HostStoreCodec.upsert(List.of(original), new HostRecord(moved, "Changed", 47984, "host-123", changed)));
        fails(() -> HostStoreCodec.upsert(List.of(original), new HostRecord(endpoint, "Forget pin", 0, "", null)));
        check(original.hasSamePin(reloaded), "Failed replacements preserve original trust");
        HostRecord unpaired = new HostRecord(endpoint, "Manual", 0, "", null);
        check(!HostStoreCodec.decode(HostStoreCodec.encode(List.of(unpaired))).get(0).isPaired(), "Unpaired metadata round trip");
        check(HostStoreCodec.upsert(List.of(unpaired), original).get(0).isPaired(), "Pairing fills first pin");
        List<HostRecord> full = new ArrayList<>();
        for (int i = 1; i <= HostStoreCodec.MAX_HOSTS; i++) full.add(new HostRecord(HostEndpoint.of("10.0.0." + i, 47989), "host", 0, "id-" + i, null));
        check(HostStoreCodec.decode(HostStoreCodec.encode(full)).size() == HostStoreCodec.MAX_HOSTS, "Maximum host count round trip");
        fails(() -> HostStoreCodec.upsert(full, original));
        fails(() -> HostStoreCodec.encode(List.of(original, original)));
        fails(() -> HostStoreCodec.encode(List.of(original, movedRecord)));
        byte[] good = HostStoreCodec.encode(List.of(original));
        fails(() -> HostStoreCodec.decode(Arrays.copyOf(good, good.length - 1)));
        fails(() -> HostStoreCodec.decode(Arrays.copyOf(good, good.length + 1)));
        byte[] badHeader = good.clone(); badHeader[0] = 0;
        fails(() -> HostStoreCodec.decode(badHeader));
        byte[] badVersion = good.clone(); badVersion[7] = 2;
        fails(() -> HostStoreCodec.decode(badVersion));
        byte[] tooMany = HostStoreCodec.encode(List.of()); tooMany[11] = 127;
        fails(() -> HostStoreCodec.decode(tooMany));
        fails(() -> HostStoreCodec.decode(new byte[HostStoreCodec.MAX_FILE_BYTES + 1]));
        fails(() -> HostStoreCodec.decode(new byte[0]));
        illegal(() -> new HostRecord(endpoint, "a", 65536, "", null));
        illegal(() -> new HostRecord(endpoint, "a", 0, "url://host", null));
        check(new HostRecord(endpoint, "name\n\u202Ebad", 0, "", null).displayName.equals("namebad"), "Untrusted name removes controls/bidi formats");
        check(new HostRecord(endpoint, "x".repeat(500), 0, "", null).displayName.length() == 96, "Name length bound");
        System.out.println("GameStream host validation/store: " + assertions + " assertions passed (public certificates, no private identity or network).");
    }
    private static X509Certificate cert(String pem) throws Exception { return (X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII))); }
    private static void check(boolean ok, String label) { assertions++; if (!ok) throw new AssertionError(label); }
    private interface Action { void run() throws Exception; }
    private static void illegal(Action action) throws Exception { assertions++; try { action.run(); } catch (IllegalArgumentException expected) { return; } throw new AssertionError("Expected invalid-input rejection"); }
    private static void fails(Action action) throws Exception { assertions++; try { action.run(); } catch (IOException expected) { return; } throw new AssertionError("Expected store rejection"); }
}
