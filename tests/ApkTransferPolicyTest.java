package com.mooncast.host;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.net.InetAddress;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import com.mooncast.transfer.ApkTransfer;
import com.mooncast.transfer.ApkTransferClient;
import com.mooncast.transfer.ApkTransferServer;

/** Generated binary XML is policy-test input only, never an installable APK. */
public final class ApkTransferPolicyTest {
    private static int checks;
    private interface Checked { void run() throws Exception; }
    private static void yes(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    private static void rejected(Checked work, String label) throws Exception {
        try { work.run(); } catch (IOException expected) { checks++; return; }
        throw new AssertionError(label);
    }
    public static void main(String[] args) throws Exception {
        for (String address : new String[]{"10.0.0.1:1", "10.255.255.254:65535", "172.16.0.1:9999", "172.31.255.254:1", "192.168.1.2:12345"}) {
            ApkEndpoint value = ApkEndpoint.parse(address);
            yes(value != null && address.equals(value.display()) && value.same(ApkEndpoint.parse(address)), "private numeric endpoint");
        }
        for (String address : new String[]{null, "", "127.0.0.1:1", "169.254.1.2:1", "100.64.1.2:1", "8.8.8.8:53", "172.15.0.1:1", "172.32.0.1:1", "192.169.0.1:1", "192.168.1.2:0", "192.168.1.2:65536", "192.168.1.256:1", "192.168.01.2:1", "192.168.1:1", "192.168.1.2:1/path", "http://192.168.1.2:1", "tv.local:123", "[::1]:1", "192.168.1.2:-1", "192.168.1.2:1\n", "192.168.1.2: 1"})
            yes(ApkEndpoint.parse(address) == null, "invalid endpoint rejected: " + address);
        yes(!ApkEndpoint.parse("10.0.0.1:1").same(ApkEndpoint.parse("10.0.0.1:2")), "port identifies endpoint");
        ApkSingleManifest.check(document(null, null, null)); checks++;
        ApkSingleManifest.check(document(new Attr("isFeatureSplit", 0x12, 0), new Attr("isSplitRequired", 0x12, 0), null)); checks++;
        ApkSingleManifest.check(document(new Attr("split", ""), null, null)); checks++;
        for (String name : new String[]{"split", "splitTypes", "requiredSplitTypes"})
            rejected(() -> ApkSingleManifest.check(document(new Attr(name, "feature"), null, null)), name + " rejected");
        rejected(() -> ApkSingleManifest.check(document(new Attr("isFeatureSplit", 0x12, 1), null, null)), "feature flag");
        rejected(() -> ApkSingleManifest.check(document(new Attr("isFeatureSplit", 1, 0x7f010001), null, null)), "unresolved feature resource");
        rejected(() -> ApkSingleManifest.check(document(null, new Attr("isSplitRequired", 0x12, 1), null)), "required split flag");
        rejected(() -> ApkSingleManifest.check(document(null, new Attr("isSplitRequired", "true"), null)), "required string flag");
        rejected(() -> ApkSingleManifest.check(document(null, new Attr("isSplitRequired", 1, 0x7f010001), null)), "unresolved split resource");
        rejected(() -> ApkSingleManifest.check(document(null, null, "uses-split")), "uses split element");
        String required = "com.android.vending.splits.required";
        ApkSingleManifest.check(document(null, null, "com.android.vending.splits")); checks++;
        for (Attr value : new Attr[]{new Attr("value", 0x12, 0), new Attr("value", "false"), new Attr("value", "0")}) {
            ApkSingleManifest.check(document(null, null, required, value)); checks++;
        }
        for (Attr value : new Attr[]{new Attr("value", 0x12, 1), new Attr("value", "true"), new Attr("value", 1, 0x7f010001), new Attr("resource", 1, 0x7f010001)})
            rejected(() -> ApkSingleManifest.check(document(null, null, required, value)), "required or unresolved split metadata");
        rejected(() -> ApkSingleManifest.check(document(null, null, required)), "missing required metadata value");
        rejected(() -> ApkSingleManifest.check(new byte[8]), "bad root");
        byte[] valid = document(null, null, null);
        rejected(() -> ApkSingleManifest.check(Arrays.copyOf(valid, valid.length - 1)), "truncated XML");
        byte[] badIndex = valid.clone(); put32(badIndex, 8 + 28, 0x7fffffff);
        rejected(() -> ApkSingleManifest.check(badIndex), "string offset overflow");
        byte[] hugePool = valid.clone(); put32(hugePool, 8 + 8, 65537);
        rejected(() -> ApkSingleManifest.check(hugePool), "string count limit");
        rejected(() -> ApkSingleManifest.check(new byte[4 * 1024 * 1024 + 1]), "manifest allocation limit");
        for (String apk : args) { ApkSingleManifest.requireStandalone(new File(apk)); checks++; transferRealApk(new File(apk)); }
        System.out.println("APK sender endpoint/manifest policy: " + checks + " checks passed");
    }

    private static void transferRealApk(File apk) throws Exception {
        File directory = Files.createTempDirectory("mooncast-real-apk-loopback-").toFile();
        AtomicReference<File> received = new AtomicReference<>(); AtomicReference<String> failure = new AtomicReference<>();
        CountDownLatch complete = new CountDownLatch(1); InetAddress loop = InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
        String hash = ApkTransfer.hash(apk);
        try (ApkTransferServer receiver = new ApkTransferServer(directory, loop, InetAddress::isLoopbackAddress, "00000000-0000-4000-8000-000000000001", new ApkTransferServer.Listener() {
            public void onProgress(long bytes, long total) {}
            public void onReceived(File file, String sha256) { received.set(file); if (!hash.equals(sha256)) failure.set("hash"); complete.countDown(); }
            public void onError(String message) { failure.set("receiver error"); complete.countDown(); }
        })) {
            ApkTransferServer.Session session = receiver.start();
            try (ApkTransferClient client = new ApkTransferClient(loop, session.port)) {
                client.send(apk, "generated-test-copy.apk", hash, null);
            }
            yes(complete.await(10, TimeUnit.SECONDS) && failure.get() == null && received.get() != null, "actual APK local transfer completes");
            yes(received.get().length() == apk.length() && ApkTransfer.hash(received.get()).equals(hash), "actual APK transfer exact length and hash");
            ApkSingleManifest.requireStandalone(received.get()); checks++;
        } finally {
            File[] files = directory.listFiles(); if (files != null) for (File file : files) if (!file.delete()) throw new IOException("Test cleanup failed");
            if (!directory.delete()) throw new IOException("Test cleanup failed");
        }
    }

    private static final class Attr {
        final String name, text; final int type, value;
        Attr(String name, String text) { this.name = name; this.text = text; type = 3; value = 0; }
        Attr(String name, int type, int value) { this.name = name; this.type = type; this.value = value; text = null; }
    }
    private static byte[] document(Attr manifest, Attr application, String child) throws Exception {
        return document(manifest, application, child, null);
    }
    private static byte[] document(Attr manifest, Attr application, String child, Attr metadataValue) throws Exception {
        List<String> strings = new ArrayList<>(Arrays.asList("manifest", "application"));
        add(strings, manifest); add(strings, application); add(strings, metadataValue);
        if (child != null) {
            strings.add("uses-split".equals(child) ? child : "meta-data");
            if (!"uses-split".equals(child)) { strings.add("name"); strings.add(child); }
        }
        ByteArrayOutputStream body = new ByteArrayOutputStream(); body.write(pool(strings));
        body.write(start(strings, "manifest", manifest)); body.write(start(strings, "application", application));
        if (child != null) {
            String element = "uses-split".equals(child) ? child : "meta-data";
            body.write(start(strings, element, "uses-split".equals(child) ? null : new Attr("name", child), metadataValue));
            body.write(end(strings, element));
        }
        body.write(end(strings, "application")); body.write(end(strings, "manifest"));
        ByteArrayOutputStream xml = new ByteArrayOutputStream(); u16(xml, 3); u16(xml, 8); u32(xml, body.size() + 8); body.writeTo(xml); return xml.toByteArray();
    }
    private static void add(List<String> strings, Attr attr) { if (attr != null) { strings.add(attr.name); if (attr.text != null) strings.add(attr.text); } }
    private static byte[] pool(List<String> strings) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(), offsets = new ByteArrayOutputStream();
        for (String value : strings) { u32(offsets, bytes.size()); byte[] text = value.getBytes(StandardCharsets.UTF_8); bytes.write(text.length); bytes.write(text.length); bytes.write(text); bytes.write(0); }
        while ((bytes.size() & 3) != 0) bytes.write(0);
        ByteArrayOutputStream pool = new ByteArrayOutputStream(); u16(pool, 1); u16(pool, 28); u32(pool, 28 + offsets.size() + bytes.size());
        u32(pool, strings.size()); u32(pool, 0); u32(pool, 0x100); u32(pool, 28 + offsets.size()); u32(pool, 0); offsets.writeTo(pool); bytes.writeTo(pool); return pool.toByteArray();
    }
    private static byte[] start(List<String> strings, String element, Attr... attributes) {
        int count = 0; for (Attr attr : attributes) if (attr != null) count++;
        ByteArrayOutputStream out = new ByteArrayOutputStream(); u16(out, 0x102); u16(out, 16); u32(out, 36 + 20 * count);
        u32(out, 1); u32(out, -1); u32(out, -1); u32(out, strings.indexOf(element)); u16(out, 20); u16(out, 20); u16(out, count); u16(out, 0); u16(out, 0); u16(out, 0);
        for (Attr attr : attributes) if (attr != null) { u32(out, -1); u32(out, strings.indexOf(attr.name)); u32(out, -1); u16(out, 8); out.write(0); out.write(attr.type); u32(out, attr.text == null ? attr.value : strings.indexOf(attr.text)); }
        return out.toByteArray();
    }
    private static byte[] end(List<String> strings, String element) { ByteArrayOutputStream out = new ByteArrayOutputStream(); u16(out, 0x103); u16(out, 16); u32(out, 24); u32(out, 1); u32(out, -1); u32(out, -1); u32(out, strings.indexOf(element)); return out.toByteArray(); }
    private static void u16(ByteArrayOutputStream out, int n) { out.write(n); out.write(n >>> 8); }
    private static void u32(ByteArrayOutputStream out, int n) { u16(out, n); u16(out, n >>> 16); }
    private static void put32(byte[] data, int at, int value) { for (int i = 0; i < 4; i++) data[at + i] = (byte) (value >>> (8 * i)); }
}
