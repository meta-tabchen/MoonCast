package com.mooncast.transfer;

import java.io.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/** Shared, dependency-free wire constants and bounded APK container checks. */
public final class ApkTransfer {
    public static final int PORT = 48152;
    public static final int PROTOCOL_VERSION = 2;
    public static final String SERVICE_TYPE = "_mooncast-apk._tcp.";
    public static final long MAX_BYTES = 1024L * 1024 * 1024;
    public static final long FREE_RESERVE_BYTES = 32L * 1024 * 1024;
    public static final long SESSION_MILLIS = 5L * 60 * 1000;
    // ZipFile loads the central directory before callers can inspect entries. Bound that
    // allocation first; the payload limit alone is much too large for a TV process.
    static final long MAX_DIRECTORY_BYTES = 16L * 1024 * 1024;
    static final int MAX_ZIP_ENTRIES = 50000;
    static final int MAX_ENTRY_NAME_BYTES = 4096;
    static final long MAX_RESOURCE_TABLE_BYTES = 64L * 1024 * 1024;
    private ApkTransfer() {}
    public static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(String.format(Locale.ROOT, "%02x", b & 255));
        return out.toString();
    }
    public static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    public static String hash(File file) throws IOException {
        MessageDigest digest = sha256();
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[65536]; int n;
            while ((n = in.read(buffer)) != -1) digest.update(buffer, 0, n);
        }
        return hex(digest.digest());
    }
    public static void checkName(String name) throws IOException {
        if (name == null || name.length() > 240 || !name.toLowerCase(Locale.ROOT).endsWith(".apk")
            || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.matches(".*[\\p{Cntrl}].*"))
            throw new IOException("Choose one ordinary .apk file; .apks and .xapk bundles are unsupported");
    }
    /** Necessary structural check only. Android PackageManager and installer remain authoritative. */
    public static void checkContainer(File file) throws IOException {
        checkDirectory(file);
        try (ZipFile zip = new ZipFile(file)) {
            ZipEntry manifest = null; int count = 0; Set<String> names = new HashSet<>();
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (++count > MAX_ZIP_ENTRIES || !names.add(entry.getName())) throw new IOException("Invalid APK ZIP entries");
                if ("AndroidManifest.xml".equals(entry.getName())) manifest = entry;
                // PackageManager/AssetManager may load this table to resolve app metadata.
                // A small compressed APK must not trigger an unbounded resource allocation.
                if ("resources.arsc".equals(entry.getName())) checkResourceTable(zip, entry);
            }
            if (manifest == null || manifest.isDirectory() || manifest.getSize() <= 0 || manifest.getSize() > 4L * 1024 * 1024)
                throw new IOException("File is not an ordinary APK: missing or invalid AndroidManifest.xml");
            // APK manifests use Android's binary XML format (RES_XML_TYPE / 8-byte header).
            try (InputStream in = zip.getInputStream(manifest)) {
                if (in.read() != 3 || in.read() != 0 || in.read() != 8 || in.read() != 0)
                    throw new IOException("APK manifest is not Android binary XML");
            }
        }
    }

    private static void checkResourceTable(ZipFile zip, ZipEntry entry) throws IOException {
        long declared = entry.getSize();
        if (entry.isDirectory() || declared <= 0 || declared > MAX_RESOURCE_TABLE_BYTES)
            throw new IOException("APK resource table exceeds limits");
        // Do not trust the central directory's declared inflated size. Bound the actual
        // decoder output too, before handing the archive to Android's resource loader.
        try (InputStream in = zip.getInputStream(entry)) {
            byte[] buffer = new byte[65536]; long total = 0; int n;
            while ((n = in.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("APK validation canceled");
                if (total > declared - n || total > MAX_RESOURCE_TABLE_BYTES - n)
                    throw new IOException("APK resource table size does not match");
                total += n;
            }
            if (total != declared) throw new IOException("APK resource table size does not match");
        }
    }

    /** Fixed-memory ZIP32 preflight. APK ZIP64/multi-disk containers are unsupported. */
    private static void checkDirectory(File file) throws IOException {
        long length = file.length();
        if (length < 22 || length > MAX_BYTES) throw new IOException("Invalid APK ZIP size");
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            int tailLength = (int)Math.min(length, 22L + 65535);
            byte[] tail = new byte[tailLength];
            input.seek(length - tailLength); input.readFully(tail);
            int end = -1;
            for (int offset = tail.length - 22; offset >= 0; offset--) {
                if (u32(tail, offset) == 0x06054b50L
                        && offset + 22 + u16(tail, offset + 20) == tail.length) {
                    end = offset; break;
                }
            }
            if (end < 0) throw new IOException("Missing APK ZIP end record");
            int entries = u16(tail, end + 10);
            long directorySize = u32(tail, end + 12), directoryOffset = u32(tail, end + 16);
            long endOffset = length - tailLength + end;
            if (u16(tail, end + 4) != 0 || u16(tail, end + 6) != 0
                    || u16(tail, end + 8) != entries)
                throw new IOException("Multi-disk APK ZIPs are unsupported");
            if (entries == 0xffff || directorySize == 0xffffffffL || directoryOffset == 0xffffffffL)
                throw new IOException("ZIP64 APKs are unsupported");
            if (entries < 1 || entries > MAX_ZIP_ENTRIES || directorySize > MAX_DIRECTORY_BYTES)
                throw new IOException("APK ZIP directory exceeds limits");
            if (directoryOffset + directorySize != endOffset || directorySize < entries * 46L)
                throw new IOException("Invalid APK ZIP directory bounds");

            input.seek(directoryOffset);
            byte[] header = new byte[46], extraHeader = new byte[4];
            for (int index = 0; index < entries; index++) {
                long entryOffset = input.getFilePointer();
                if (endOffset - entryOffset < header.length) throw new IOException("Truncated APK ZIP directory");
                input.readFully(header);
                if (u32(header, 0) != 0x02014b50L) throw new IOException("Invalid APK ZIP entry");
                int nameSize = u16(header, 28), extraSize = u16(header, 30), commentSize = u16(header, 32);
                long next = entryOffset + header.length + nameSize + extraSize + commentSize;
                if (nameSize < 1 || nameSize > MAX_ENTRY_NAME_BYTES || next > endOffset)
                    throw new IOException("APK ZIP entry exceeds bounds");
                if (u16(header, 34) != 0 || u32(header, 20) == 0xffffffffL
                        || u32(header, 24) == 0xffffffffL || u32(header, 42) == 0xffffffffL)
                    throw new IOException("ZIP64 or multi-disk APK entries are unsupported");
                if (u32(header, 42) >= directoryOffset)
                    throw new IOException("Invalid APK ZIP payload offset");
                long extraEnd = entryOffset + header.length + nameSize + extraSize;
                input.seek(entryOffset + header.length + nameSize);
                while (input.getFilePointer() < extraEnd) {
                    if (extraEnd - input.getFilePointer() < extraHeader.length)
                        throw new IOException("Invalid APK ZIP extra field");
                    input.readFully(extraHeader);
                    long extraNext = input.getFilePointer() + u16(extraHeader, 2);
                    if (u16(extraHeader, 0) == 1) throw new IOException("ZIP64 APK entries are unsupported");
                    if (extraNext > extraEnd) throw new IOException("Invalid APK ZIP extra field");
                    input.seek(extraNext);
                }
                input.seek(next);
            }
            if (input.getFilePointer() != endOffset) throw new IOException("APK ZIP entry count does not match directory");
        }
    }
    private static int u16(byte[] data, int offset) {
        return (data[offset] & 255) | ((data[offset + 1] & 255) << 8);
    }
    private static long u32(byte[] data, int offset) {
        return (u16(data, offset) & 65535L) | ((long)u16(data, offset + 2) << 16);
    }
}
