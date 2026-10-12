package com.mooncast.host;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/** Instrumentation APK only: exposes its own signed APK or synthetic bounded error fixtures. */
public final class ApkFixtureProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    private String mode(Uri uri) { String mode = uri.getLastPathSegment(); return mode == null ? "invalid" : mode; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        return query(uri, projection, selection, args, order, null);
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order, CancellationSignal signal) {
        String mode = mode(uri);
        if ("slow-query".equals(mode)) pause(signal);
        if ("query-unavailable".equals(mode)) throw new UnsupportedOperationException("No optional columns");
        MatrixCursor cursor = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        cursor.addRow(new Object[]{"missing-name".equals(mode) ? null : "extensionless".equals(mode) ? "Developer build" : "bundle".equals(mode) ? "fixture.apks" : "fixture.apk",
            "oversize".equals(mode) ? 1024L * 1024 * 1024 + 1 : "ordinary".equals(mode) ? new File(getContext().getApplicationInfo().sourceDir).length() : -1L});
        return cursor;
    }
    @Override public AssetFileDescriptor openAssetFile(Uri uri, String mode, CancellationSignal signal) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException();
        String fixture = mode(uri);
        if ("slow-open".equals(fixture)) pause(signal);
        try {
            if ("ordinary".equals(fixture) || "missing-name".equals(fixture) || "extensionless".equals(fixture) || "query-unavailable".equals(fixture)) return new AssetFileDescriptor(
                ParcelFileDescriptor.open(new File(getContext().getApplicationInfo().sourceDir), ParcelFileDescriptor.MODE_READ_ONLY), 0, AssetFileDescriptor.UNKNOWN_LENGTH);
            if ("blocked".equals(fixture)) {
                ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
                Thread writer = new Thread(() -> {
                    try (FileOutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                        for (int i = 0; i < 30; i++) { out.write(0); out.flush(); Thread.sleep(200); }
                    } catch (IOException | InterruptedException ignored) {}
                }, "apk-fixture-pipe");
                writer.setDaemon(true); writer.start();
                return new AssetFileDescriptor(pipe[0], 0, AssetFileDescriptor.UNKNOWN_LENGTH);
            }
            File invalid = new File(getContext().getCacheDir(), "apk-invalid-fixture.bin");
            try (FileOutputStream out = new FileOutputStream(invalid)) { out.write(new byte[]{1, 2, 3, 4}); }
            return new AssetFileDescriptor(ParcelFileDescriptor.open(invalid, ParcelFileDescriptor.MODE_READ_ONLY), 0, 4);
        } catch (IOException ignored) { throw new FileNotFoundException("Fixture unavailable"); }
    }
    private void pause(CancellationSignal signal) {
        long end = SystemClock.elapsedRealtime() + 15000;
        while (SystemClock.elapsedRealtime() < end) { if (signal != null) signal.throwIfCanceled(); SystemClock.sleep(20); }
        if (signal != null) signal.throwIfCanceled();
    }
    @Override public AssetFileDescriptor openAssetFile(Uri uri, String mode) throws FileNotFoundException { return openAssetFile(uri, mode, null); }
    @Override public String getType(Uri uri) { return "application/vnd.android.package-archive"; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String where, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String where, String[] args) { throw new UnsupportedOperationException(); }
}
