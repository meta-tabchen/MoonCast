// SPDX-License-Identifier: GPL-3.0-or-later
package com.mooncast.tv.gamestream.hosts;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import com.limelight.nvstream.http.LimelightCryptoProvider;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Date;
import java.util.Objects;
import javax.security.auth.x500.X500Principal;

/**
 * Runtime-only TV pairing identity. No private-key export, software-key fallback, logs,
 * account, or remote registration. AndroidKeyStore hardware backing depends on the TV.
 * Android TLS/host interoperability needs device validation, separately from compilation.
 */
public final class AndroidPairingIdentity implements LimelightCryptoProvider {
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String ALIAS = "mooncast.tv.gamestream.pairing.v1";
    private static final Object IDENTITY_LOCK = new Object();
    private final PrivateKey privateKey;
    private final X509Certificate certificate;
    private final byte[] certificatePem;
    private final String clientId;

    private AndroidPairingIdentity(PrivateKey privateKey, X509Certificate certificate) throws GeneralSecurityException {
        this.privateKey = privateKey;
        this.certificate = certificate;
        // Fails closed if a device provider returns an unusable self-signed certificate.
        certificate.verify(certificate.getPublicKey());
        String base64 = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(certificate.getEncoded());
        certificatePem = ("-----BEGIN CERTIFICATE-----\n" + base64 + "\n-----END CERTIFICATE-----\n").getBytes(StandardCharsets.US_ASCII);
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
        StringBuilder id = new StringBuilder(16);
        for (int i = 0; i < 8; i++) { id.append(Character.forDigit((hash[i] >>> 4) & 15, 16)); id.append(Character.forDigit(hash[i] & 15, 16)); }
        clientId = id.toString();
    }

    /** Loading never creates, replaces, repairs, or deletes an identity. Null means absent. */
    public static AndroidPairingIdentity loadExisting(Context context) throws GeneralSecurityException, IOException {
        Objects.requireNonNull(context);
        synchronized (IDENTITY_LOCK) { return read(open()); }
    }

    /**
     * Invoke on a worker only after the user explicitly starts PIN pairing on the TV.
     * Never invoke during startup, discovery, browsing, or automated inspection.
     * Existing invalid entries raise an error rather than silently creating a new identity.
     */
    public static AndroidPairingIdentity getOrCreateForUserPairing(Context context) throws GeneralSecurityException, IOException {
        Objects.requireNonNull(context);
        synchronized (IDENTITY_LOCK) {
            KeyStore store = open();
            AndroidPairingIdentity existing = read(store);
            if (existing != null) return existing;
            KeyPairGenerator generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, KEYSTORE);
            generator.initialize(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                    .setKeySize(2048)
                    .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1, KeyProperties.SIGNATURE_PADDING_RSA_PSS)
                    .setUserAuthenticationRequired(false)
                    .setCertificateSubject(new X500Principal("CN=MoonCast TV"))
                    .setCertificateSerialNumber(BigInteger.ONE)
                    .setCertificateNotBefore(new Date(1577836800000L)) // 2020-01-01 UTC
                    .setCertificateNotAfter(new Date(4733510400000L))  // 2120-01-01 UTC
                    .build());
            generator.generateKeyPair();
            AndroidPairingIdentity created = read(open());
            if (created == null) throw new GeneralSecurityException("Pairing identity was not retained by AndroidKeyStore");
            return created;
        }
    }

    private static KeyStore open() throws GeneralSecurityException, IOException {
        KeyStore store = KeyStore.getInstance(KEYSTORE);
        store.load(null);
        return store;
    }

    private static AndroidPairingIdentity read(KeyStore store) throws GeneralSecurityException {
        if (!store.containsAlias(ALIAS)) return null;
        Key key = store.getKey(ALIAS, null);
        Certificate certificate = store.getCertificate(ALIAS);
        if (!(key instanceof PrivateKey) || !(certificate instanceof X509Certificate) || !"RSA".equals(key.getAlgorithm())
                || key.getFormat() != null) throw new GeneralSecurityException("Existing pairing identity is not a non-exportable RSA key");
        return new AndroidPairingIdentity((PrivateKey)key, (X509Certificate)certificate);
    }

    /** Compatibility label only; the certificate, not this label, authenticates the TV. */
    public String getClientId() { return clientId; }
    @Override public X509Certificate getClientCertificate() { return certificate; }
    @Override public PrivateKey getClientPrivateKey() { return privateKey; }
    @Override public byte[] getPemEncodedClientCertificate() { return certificatePem.clone(); }
    @Override public String encodeBase64String(byte[] data) { return Base64.getEncoder().encodeToString(data); }
}
