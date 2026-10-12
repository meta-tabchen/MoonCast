// SPDX-License-Identifier: GPL-3.0-or-later
package com.mooncast.tv.gamestream.hosts;

import android.content.Context;
import android.util.AtomicFile;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** App-private, backup-excluded host metadata and certificate pins. Use on a worker thread. */
public final class HostStore {
    public static final int MAX_HOSTS = HostStoreCodec.MAX_HOSTS;
    private static final Object STORAGE_LOCK = new Object();
    private final AtomicFile storage;

    public HostStore(Context context) {
        storage = new AtomicFile(new File(context.getApplicationContext().getNoBackupFilesDir(), "gamestream-hosts-v1.bin"));
    }

    /** Corruption is an error, never an empty list that could silently discard trust history. */
    public List<HostRecord> load() throws IOException {
        synchronized (STORAGE_LOCK) { return readLocked(); }
    }

    /** Call only after the user selected/added this host; pins come from successful PIN pairing. */
    public void save(HostRecord record) throws IOException {
        synchronized (STORAGE_LOCK) { writeLocked(HostStoreCodec.upsert(readLocked(), Objects.requireNonNull(record))); }
    }

    /** Local removal only. It does not contact the host or revoke the host's trust in this TV. */
    public void forget(HostEndpoint endpoint) throws IOException {
        synchronized (STORAGE_LOCK) {
            List<HostRecord> previous = readLocked();
            List<HostRecord> remaining = new ArrayList<>();
            for (HostRecord host : previous) if (!host.endpoint.equals(endpoint)) remaining.add(host);
            if (remaining.size() != previous.size()) writeLocked(remaining);
        }
    }

    private List<HostRecord> readLocked() throws IOException {
        try (FileInputStream in = storage.openRead(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = in.read(buffer)) != -1) {
                if (bytes.size() + count > HostStoreCodec.MAX_FILE_BYTES) throw new IOException("Saved host data is too large");
                bytes.write(buffer, 0, count);
            }
            return HostStoreCodec.decode(bytes.toByteArray());
        } catch (FileNotFoundException e) {
            File base = storage.getBaseFile();
            if (base.exists() || new File(base.getPath() + ".bak").exists()) throw new IOException("Saved hosts cannot be read", e);
            return Collections.emptyList();
        }
    }

    private void writeLocked(List<HostRecord> records) throws IOException {
        byte[] bytes = HostStoreCodec.encode(records);
        FileOutputStream output = null;
        try {
            output = storage.startWrite();
            output.write(bytes);
            storage.finishWrite(output);
        } catch (IOException | RuntimeException e) {
            if (output != null) storage.failWrite(output);
            throw new IOException("Could not save host trust data", e);
        }
    }
}
