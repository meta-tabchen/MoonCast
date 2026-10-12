// SPDX-License-Identifier: GPL-3.0-or-later
package com.mooncast.tv.gamestream.hosts;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Bounded, versioned private format. Malformed input fails closed rather than discarding pins. */
final class HostStoreCodec {
    static final int MAX_HOSTS = 24;
    static final int MAX_FILE_BYTES = 512 * 1024;
    private static final int MAGIC = 0x4d434853; // MCHS
    private static final int VERSION = 1;
    private HostStoreCodec() {}

    static byte[] encode(List<HostRecord> hosts) throws IOException {
        if (hosts.size() > MAX_HOSTS) throw new IOException("Saved host limit reached");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(hosts.size());
            Set<HostEndpoint> endpoints = new HashSet<>();
            Set<String> identifiers = new HashSet<>();
            for (HostRecord host : hosts) {
                if (!endpoints.add(host.endpoint) || !host.serverUuid.isEmpty() && !identifiers.add(host.serverUuid)) throw new IOException("Duplicate saved host");
                out.writeUTF(host.endpoint.address);
                out.writeInt(host.endpoint.httpPort);
                out.writeUTF(host.displayName);
                out.writeInt(host.httpsPort);
                out.writeUTF(host.serverUuid);
                byte[] certificate = host.encodedCertificate();
                out.writeInt(certificate == null ? 0 : certificate.length);
                if (certificate != null) out.write(certificate);
            }
        }
        if (bytes.size() > MAX_FILE_BYTES) throw new IOException("Saved host data is too large");
        return bytes.toByteArray();
    }

    static List<HostRecord> decode(byte[] bytes) throws IOException {
        if (bytes.length > MAX_FILE_BYTES) throw new IOException("Saved host data is too large");
        List<HostRecord> result = new ArrayList<>();
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION) throw new IOException("Unsupported saved host format");
            int count = in.readInt();
            if (count < 0 || count > MAX_HOSTS) throw new IOException("Invalid saved host count");
            Set<HostEndpoint> endpoints = new HashSet<>();
            Set<String> identifiers = new HashSet<>();
            for (int i = 0; i < count; i++) {
                String address = in.readUTF();
                int port = in.readInt();
                String name = in.readUTF();
                int httpsPort = in.readInt();
                String uuid = in.readUTF();
                if (name.length() > 96 || uuid.length() > 128) throw new IOException("Invalid saved host metadata");
                int length = in.readInt();
                if (length < 0 || length > HostRecord.MAX_CERTIFICATE_BYTES || length > in.available()) throw new IOException("Invalid stored certificate size");
                X509Certificate certificate = null;
                if (length != 0) {
                    byte[] der = new byte[length];
                    in.readFully(der);
                    certificate = HostRecord.decodeCertificate(der);
                }
                HostRecord host = new HostRecord(HostEndpoint.of(address, port), name, httpsPort, uuid, certificate);
                if (!endpoints.add(host.endpoint) || !uuid.isEmpty() && !identifiers.add(uuid)) throw new IOException("Duplicate saved host");
                result.add(host);
            }
            if (in.read() != -1) throw new IOException("Unexpected trailing saved host data");
        } catch (CertificateException | IllegalArgumentException e) { throw new IOException("Invalid saved host data; existing pins were not changed", e); }
        return Collections.unmodifiableList(result);
    }

    static List<HostRecord> upsert(List<HostRecord> current, HostRecord replacement) throws IOException {
        List<HostRecord> next = new ArrayList<>();
        for (HostRecord previous : current) {
            boolean sameAddress = previous.endpoint.equals(replacement.endpoint);
            boolean sameId = !previous.serverUuid.isEmpty() && previous.serverUuid.equals(replacement.serverUuid);
            if (!sameAddress && !sameId) { next.add(previous); continue; }
            if (previous.isPaired() && !previous.hasSamePin(replacement)) {
                throw new IOException("Host certificate changed. The saved certificate was not replaced. Verify the host before explicitly forgetting and pairing again.");
            }
        }
        if (next.size() >= MAX_HOSTS) throw new IOException("Saved host limit reached. Forget a host before adding another.");
        next.add(replacement);
        return Collections.unmodifiableList(next);
    }
}
