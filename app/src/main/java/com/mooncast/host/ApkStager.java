package com.mooncast.host;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.SystemClock;
import android.provider.OpenableColumns;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;
import com.mooncast.transfer.ApkTransfer;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Copies only a selected content URI. Every expensive step runs on the controller worker. */
final class ApkStager {
    interface Progress { void copied(long bytes); }
    static final class Selection {
        final File file;
        final String name, label, packageName, version, sha256, signers;
        final long bytes;
        Selection(File file, String name, String label, String packageName, String version,
                  String sha256, String signers) {
            this.file = file; this.name = name; this.label = label; this.packageName = packageName;
            this.version = version; this.sha256 = sha256; this.signers = signers; bytes = file.length();
        }
    }
    static final class Work {
        final CancellationSignal signal = new CancellationSignal();
        final long deadline = SystemClock.elapsedRealtime() + ApkTransfer.SESSION_MILLIS;
        private final List<Closeable> resources = new ArrayList<>();
        volatile boolean canceled;
        private File file;
        void check() throws IOException {
            if (canceled || Thread.currentThread().isInterrupted() || SystemClock.elapsedRealtime() >= deadline)
                throw new InterruptedIOException("APK preparation canceled");
        }
        synchronized <T extends Closeable> T own(T resource) throws IOException {
            if (canceled) { closeQuietly(resource); throw new InterruptedIOException("APK preparation canceled"); }
            resources.add(resource); return resource;
        }
        void setFile(File value) {
            boolean discard;
            synchronized (this) { file = value; discard = canceled; }
            if (discard) remove(value);
        }
        // Called on a cleanup worker, never while holding the controller/UI lock.
        void cancelAndDelete() {
            canceled = true;
            closeResources();
            File old;
            synchronized (this) { old = file; file = null; }
            if (old != null) remove(old);
            // Delete before contacting the provider: its cancellation callback must not
            // be able to keep an already-owned private spool alive.
            try { signal.cancel(); } catch (RuntimeException ignored) {}
        }
        synchronized void closeResources() {
            for (int i = resources.size() - 1; i >= 0; i--) closeQuietly(resources.get(i));
            resources.clear();
        }
    }
    private static final HashSet<String> ACTIVE_FILES = new HashSet<>();
    private ApkStager() {}

    static File directory(Context context) { return new File(context.getCacheDir(), "apk-outgoing"); }
    static void clearStale(Context context) {
        synchronized (ACTIVE_FILES) {
            File[] files = directory(context).listFiles();
            if (files != null) for (File file : files)
                if (file.isFile() && !ACTIVE_FILES.contains(file.getAbsolutePath())) file.delete();
        }
    }
    private static void remove(File file) {
        synchronized (ACTIVE_FILES) { file.delete(); ACTIVE_FILES.remove(file.getAbsolutePath()); }
    }
    static Selection prepare(Context context, Uri uri, Work work, Progress progress) throws IOException {
        if (uri == null || !"content".equals(uri.getScheme()) || uri.getAuthority() == null) throw new IOException(context.getString(R.string.phone_apk_choose_document));
        work.check(); clearStale(context);
        String name = null; long hint = -1;
        try (Cursor cursor = context.getContentResolver().query(uri,
            new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}, null, null, null, work.signal)) {
            work.check();
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME), sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex);
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) hint = cursor.getLong(sizeIndex);
            }
        } catch (SecurityException denied) { throw denied; }
        catch (RuntimeException optionalMetadataUnavailable) { work.check(); }
        // Some SAF providers omit DISPLAY_NAME or expose an extensionless label.
        // This name is display-only; actual APK structure, metadata and signer are checked below.
        name = ApkDocumentName.wireName(name);
        if (hint > ApkTransfer.MAX_BYTES) throw new IOException(context.getString(R.string.phone_apk_too_large));
        File dir = directory(context);
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException(context.getString(R.string.phone_apk_staging_unavailable));
        reserve(context, dir, 1);
        File partial;
        synchronized (ACTIVE_FILES) {
            partial = File.createTempFile("send-", ".part", dir);
            ACTIVE_FILES.add(partial.getAbsolutePath());
        }
        work.setFile(partial);
        try {
            work.check();
            AssetFileDescriptor descriptor = context.getContentResolver().openAssetFileDescriptor(uri, "r", work.signal);
            if (descriptor == null) throw new IOException(context.getString(R.string.phone_apk_open_failed));
            work.own(descriptor);
            if (descriptor.getLength() > ApkTransfer.MAX_BYTES) throw new IOException(context.getString(R.string.phone_apk_too_large));
            InputStream in = work.own(descriptor.createInputStream());
            FileOutputStream out = work.own(new FileOutputStream(partial));
            StructPollfd poll = new StructPollfd();
            poll.fd = descriptor.getParcelFileDescriptor().getFileDescriptor();
            poll.events = (short) (OsConstants.POLLIN | OsConstants.POLLHUP | OsConstants.POLLERR);
            byte[] buffer = new byte[65536]; long bytes = 0, reported = 0;
            while (true) {
                work.check();
                try { if (Os.poll(new StructPollfd[]{poll}, 200) == 0) continue; }
                catch (ErrnoException error) { throw new IOException(context.getString(R.string.phone_apk_read_failed)); }
                work.check();
                int n = in.read(buffer);
                if (n == -1) break;
                if (n == 0) continue;
                if (bytes > ApkTransfer.MAX_BYTES - n) throw new IOException(context.getString(R.string.phone_apk_too_large));
                reserve(context, dir, n); out.write(buffer, 0, n); bytes += n;
                if (bytes - reported >= 256 * 1024) { progress.copied(bytes); reported = bytes; }
            }
            if (bytes == 0) throw new IOException(context.getString(R.string.phone_apk_empty));
            out.getFD().sync(); work.closeResources(); work.check();
            File apk = new File(dir, partial.getName().replace(".part", ".apk"));
            synchronized (ACTIVE_FILES) {
                if (!partial.renameTo(apk)) throw new IOException(context.getString(R.string.phone_apk_staging_failed));
                ACTIVE_FILES.remove(partial.getAbsolutePath()); ACTIVE_FILES.add(apk.getAbsolutePath());
            }
            work.setFile(apk);
            String sha256 = hash(apk, work);
            work.check(); ApkTransfer.checkContainer(apk); ApkSingleManifest.requireStandalone(apk); work.check();
            Selection selected = metadata(context, apk, name, sha256);
            reserve(context, dir, 0); work.check(); return selected;
        } catch (IOException | RuntimeException error) { work.cancelAndDelete(); throw error; }
    }
    private static void reserve(Context context, File dir, int nextBytes) throws IOException {
        if (dir.getUsableSpace() < ApkTransfer.FREE_RESERVE_BYTES + nextBytes) throw new IOException(context.getString(R.string.phone_apk_space_required));
    }
    private static String hash(File file, Work work) throws IOException {
        MessageDigest digest = ApkTransfer.sha256();
        try (InputStream in = work.own(new FileInputStream(file))) {
            byte[] buffer = new byte[65536]; int n;
            while ((n = in.read(buffer)) != -1) { work.check(); digest.update(buffer, 0, n); }
        }
        work.closeResources(); return ApkTransfer.hex(digest.digest());
    }
    @SuppressWarnings("deprecation")
    static Selection metadata(Context context, File apk, String name, String sha256) throws IOException {
        PackageManager manager = context.getPackageManager();
        // Older archive parsers collect/verify certificates only with GET_SIGNATURES.
        // Also request SigningInfo on API28+ to retain current-signer semantics.
        int flags = PackageManager.GET_SIGNATURES;
        if (Build.VERSION.SDK_INT >= 28) flags |= PackageManager.GET_SIGNING_CERTIFICATES;
        PackageInfo info = manager.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        if (info == null || info.applicationInfo == null || info.packageName == null || info.packageName.isEmpty()
            || info.splitNames != null && info.splitNames.length > 0
            || info.applicationInfo.splitSourceDirs != null && info.applicationInfo.splitSourceDirs.length > 0)
            throw new IOException(context.getString(R.string.phone_apk_bad_metadata));
        Signature[] certificates = Build.VERSION.SDK_INT >= 28
            ? info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners() : info.signatures;
        if (certificates == null || certificates.length == 0 || certificates.length > 32) throw new IOException(context.getString(R.string.phone_apk_signer_missing));
        StringBuilder signers = new StringBuilder();
        for (Signature certificate : certificates) {
            if (certificate == null || certificate.toByteArray().length == 0) throw new IOException(context.getString(R.string.phone_apk_signer_missing));
            if (signers.length() > 0) signers.append('\n');
            signers.append(ApkTransfer.hex(ApkTransfer.sha256().digest(certificate.toByteArray())));
        }
        info.applicationInfo.sourceDir = apk.getAbsolutePath(); info.applicationInfo.publicSourceDir = apk.getAbsolutePath();
        CharSequence label;
        try { label = manager.getApplicationLabel(info.applicationInfo); }
        catch (RuntimeException unavailableLabel) { label = info.packageName; }
        long versionCode = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
        return new Selection(apk, name, displayText(label == null ? info.packageName : label.toString(), 120),
            displayText(info.packageName, 200), displayText(info.versionName, 100) + " (" + versionCode + ")", sha256, signers.toString());
    }
    /** Bound labels and remove controls/bidi formatting before untrusted text reaches the UI. */
    static String displayText(String text, int max) {
        if (text == null) return "";
        StringBuilder value = new StringBuilder();
        for (int offset = 0; offset < text.length() && value.length() < max; ) {
            int cp = text.codePointAt(offset); offset += Character.charCount(cp);
            int type = Character.getType(cp);
            if (!Character.isISOControl(cp) && type != Character.FORMAT && type != Character.LINE_SEPARATOR
                && type != Character.PARAGRAPH_SEPARATOR && type != Character.SURROGATE) value.appendCodePoint(cp);
        }
        return value.toString();
    }
    private static void closeQuietly(Closeable resource) { try { resource.close(); } catch (IOException | RuntimeException ignored) {} }
}
