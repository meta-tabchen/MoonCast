package com.mooncast.tv.apk;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Bounded binary-XML policy check, not an APK signature verifier or malware scan. */
public final class ApkSingleManifest {
    private static final int MAX_MANIFEST = 4 * 1024 * 1024;
    private ApkSingleManifest() {}

    public static void requireStandalone(File apk) throws IOException {
        com.mooncast.transfer.ApkTransfer.checkContainer(apk);
        try (ZipFile zip = new ZipFile(apk)) {
            ZipEntry entry = zip.getEntry("AndroidManifest.xml");
            if (entry == null || entry.getSize() < 8 || entry.getSize() > MAX_MANIFEST) throw invalid();
            byte[] data;
            try (InputStream in = zip.getInputStream(entry); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int n;
                while ((n = in.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted() || out.size() > MAX_MANIFEST - n) throw invalid();
                    out.write(buffer, 0, n);
                }
                data = out.toByteArray();
            }
            check(data);
        }
    }

    static void check(byte[] data) throws IOException {
        if (data.length < 8 || data.length > MAX_MANIFEST || u16(data, 0) != 3
            || u16(data, 2) != 8 || u32(data, 4) != data.length) throw invalid();
        Strings strings = null;
        int offset = 8, depth = 0, nodes = 0; boolean manifest = false, application = false;
        while (offset < data.length) {
            if (++nodes > 100000 || Thread.currentThread().isInterrupted()) throw invalid();
            if (data.length - offset < 8) throw invalid();
            int type = u16(data, offset), header = u16(data, offset + 2), size = u32(data, offset + 4);
            if (header < 8 || size < header || size > data.length - offset) throw invalid();
            if (type == 1) {
                if (strings != null || manifest) throw invalid();
                strings = new Strings(data, offset, header, size);
            } else if (type == 0x102) {
                if (strings == null || header != 16 || size < 36) throw invalid();
                String element = strings.get(u32(data, offset + 20));
                if (depth == 0) {
                    if (manifest || !"manifest".equals(element)) throw invalid();
                    manifest = true;
                }
                if ("uses-split".equals(element)) throw unsupported();
                if ("application".equals(element)) application = true;
                int start = u16(data, offset + 24), width = u16(data, offset + 26), count = u16(data, offset + 28);
                if (start < 20 || width < 20 || count > 10000
                    || 16L + start + (long) width * count > size) throw invalid();
                String metadataName = null; boolean metadataFalse = false; int metadataValues = 0;
                for (int i = 0; i < count; i++) {
                    if (Thread.currentThread().isInterrupted()) throw invalid();
                    int attr = offset + 16 + start + width * i;
                    String name = strings.get(u32(data, attr + 4));
                    int raw = signed32(data, attr + 8), valueType = data[attr + 15] & 255, value = signed32(data, attr + 16);
                    if (u16(data, attr + 12) != 8) throw invalid();
                    String text = raw == -1 ? valueType == 3 ? strings.get(value) : "" : strings.get(raw);
                    boolean truthy = valueType == 0x12 ? value != 0 : "true".equalsIgnoreCase(text) || "1".equals(text);
                    boolean declared = !text.isEmpty() || valueType != 3 && value != 0;
                    if ("manifest".equals(element) && (("split".equals(name) || "splitTypes".equals(name)
                        || "requiredSplitTypes".equals(name)) && declared || "isFeatureSplit".equals(name)
                        && (truthy || valueType != 3 && valueType != 0x12 && value != 0))) throw unsupported();
                    if ("application".equals(element) && "isSplitRequired".equals(name)
                        && (truthy || valueType != 3 && valueType != 0x12 && value != 0)) throw unsupported();
                    if ("meta-data".equals(element) && "name".equals(name)) metadataName = text;
                    if ("meta-data".equals(element) && ("value".equals(name) || "resource".equals(name))) {
                        metadataValues++;
                        metadataFalse = "value".equals(name) && (valueType == 0x12 && value == 0
                            || valueType == 3 && ("false".equalsIgnoreCase(text) || "0".equals(text)));
                    }
                }
                // A splits inventory is not itself a requirement. Only an explicitly false
                // required flag is safe to resolve here; resource references stay fail-closed.
                if ("com.android.vending.splits.required".equals(metadataName)
                    && (metadataValues != 1 || !metadataFalse)) throw unsupported();
                if (++depth > 128) throw invalid();
            } else if (type == 0x103) {
                if (strings == null || header != 16 || size < 24 || --depth < 0) throw invalid();
                strings.get(u32(data, offset + 20));
            } else if (type != 0x100 && type != 0x101 && type != 0x180 && type != 0x104) {
                throw invalid();
            }
            offset += size;
        }
        if (offset != data.length || depth != 0 || !manifest || !application) throw invalid();
    }

    private static final class Strings {
        final byte[] data; final int base, limit, count, offsets, start; final boolean utf8;
        final String[] cache; private int decodedBytes;
        Strings(byte[] data, int base, int header, int size) throws IOException {
            if (header < 28) throw invalid();
            this.data = data; this.base = base; limit = base + size;
            count = u32(data, base + 8); int styles = u32(data, base + 12);
            if (count > 65536 || styles > 65536 || (long) header + 4L * (count + styles) > size) throw invalid();
            cache = new String[count];
            offsets = base + header; start = base + u32(data, base + 20);
            if (start < offsets + 4L * (count + styles) || start >= limit) throw invalid();
            utf8 = (u32(data, base + 16) & 0x100) != 0;
        }
        String get(int index) throws IOException {
            if (Thread.currentThread().isInterrupted()) throw invalid();
            if (index < 0 || index >= count) throw invalid();
            if (cache[index] != null) return cache[index];
            long location = (long) start + u32(data, offsets + index * 4);
            if (location >= limit) throw invalid();
            int[] p = {(int) location}; int length;
            if (utf8) { length8(p); length = length8(p); }
            else {
                int first = next16(p);
                length = (first & 0x8000) == 0 ? first : ((first & 0x7fff) << 16) | next16(p);
                if (length > MAX_MANIFEST / 2) throw invalid();
                length *= 2;
            }
            int ending = utf8 ? 1 : 2;
            if (length < 0 || length > 65536 || decodedBytes > 8 * 1024 * 1024 - length
                || (long) p[0] + length + ending > limit) throw invalid();
            for (int i = 0; i < ending; i++) if (data[p[0] + length + i] != 0) throw invalid();
            try {
                String value = (utf8 ? StandardCharsets.UTF_8 : StandardCharsets.UTF_16LE).newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data, p[0], length)).toString();
                decodedBytes += length; cache[index] = value; return value;
            } catch (CharacterCodingException ignored) { throw invalid(); }
        }
        int length8(int[] p) throws IOException {
            if (p[0] >= limit) throw invalid();
            int first = data[p[0]++] & 255;
            if ((first & 128) == 0) return first;
            if (p[0] >= limit) throw invalid();
            return (first & 127) << 8 | data[p[0]++] & 255;
        }
        int next16(int[] p) throws IOException { if (p[0] + 2 > limit) throw invalid(); int n = u16(data, p[0]); p[0] += 2; return n; }
    }
    private static int u16(byte[] data, int p) throws IOException {
        if (p < 0 || p > data.length - 2) throw invalid();
        return (data[p] & 255) | (data[p + 1] & 255) << 8;
    }
    private static int signed32(byte[] data, int p) throws IOException {
        return u16(data, p) | u16(data, p + 2) << 16;
    }
    private static int u32(byte[] data, int p) throws IOException { int n = signed32(data, p); if (n < 0) throw invalid(); return n; }
    private static IOException invalid() { return new IOException("Invalid APK manifest"); }
    private static IOException unsupported() { return new IOException("Split APKs and split-required packages are unsupported"); }
}
