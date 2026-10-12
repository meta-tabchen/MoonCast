// SPDX-License-Identifier: GPL-3.0-or-later
package com.mooncast.tv.gamestream.hosts;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Objects;

/** Immutable metadata; the exact leaf certificate is the paired server identity. */
public final class HostRecord {
    public static final int MAX_CERTIFICATE_BYTES = 16384;
    public final HostEndpoint endpoint;
    public final String displayName;
    public final int httpsPort;
    public final String serverUuid;
    public final X509Certificate pinnedCertificate;
    private final byte[] certificateDer;

    public HostRecord(HostEndpoint endpoint, String displayName, int httpsPort,
            String serverUuid, X509Certificate pinnedCertificate) {
        this.endpoint = Objects.requireNonNull(endpoint);
        if (httpsPort < 0 || httpsPort > 65535) throw new IllegalArgumentException("Invalid HTTPS port");
        this.displayName = safeDisplayName(displayName);
        this.httpsPort = httpsPort;
        this.serverUuid = validateUuid(serverUuid);
        try {
            certificateDer = pinnedCertificate == null ? null : pinnedCertificate.getEncoded().clone();
            this.pinnedCertificate = certificateDer == null ? null : decodeCertificate(certificateDer);
        } catch (CertificateException e) { throw new IllegalArgumentException("Invalid server certificate", e); }
    }

    public boolean isPaired() { return certificateDer != null; }
    public boolean hasSamePin(HostRecord other) {
        if (certificateDer == null || other.certificateDer == null) return certificateDer == other.certificateDer;
        return MessageDigest.isEqual(certificateDer, other.certificateDer);
    }
    byte[] encodedCertificate() { return certificateDer == null ? null : certificateDer.clone(); }

    static X509Certificate decodeCertificate(byte[] bytes) throws CertificateException {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_CERTIFICATE_BYTES) throw new CertificateException("Invalid certificate size");
        ByteArrayInputStream input = new ByteArrayInputStream(bytes);
        X509Certificate certificate = (X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(input);
        if (input.available() != 0 || !MessageDigest.isEqual(bytes, certificate.getEncoded())) throw new CertificateException("Expected one DER certificate");
        return certificate;
    }

    static String safeDisplayName(String text) {
        if (text == null) return "MoonCast host";
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length() && i < 512 && result.length() < 96;) {
            int codepoint = text.codePointAt(i);
            i += Character.charCount(codepoint);
            int type = Character.getType(codepoint);
            if (!Character.isISOControl(codepoint) && type != Character.FORMAT && type != Character.LINE_SEPARATOR
                    && type != Character.PARAGRAPH_SEPARATOR && type != Character.SURROGATE) {
                if (result.length() + Character.charCount(codepoint) <= 96) result.appendCodePoint(codepoint);
            }
        }
        String cleaned = result.toString().trim();
        return cleaned.isEmpty() ? "MoonCast host" : cleaned;
    }

    private static String validateUuid(String value) {
        if (value == null || value.isEmpty()) return "";
        // Upstream host UUID is an identifier, not a URL, path, or discovery authority.
        if (value.length() > 128 || !value.matches("[A-Za-z0-9_-]{1,128}")) throw new IllegalArgumentException("Invalid host identifier");
        return value;
    }
}
