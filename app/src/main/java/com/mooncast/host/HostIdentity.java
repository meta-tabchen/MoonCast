package com.mooncast.host;

import android.util.Base64;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.math.BigInteger;
import java.security.KeyPairGenerator;
import java.util.Date;
import javax.security.auth.x500.X500Principal;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

final class HostIdentity {
    static synchronized void ensure(File dir) throws Exception {
        File cert = new File(dir, "host-cert.pem"), key = new File(dir, "host-key.pem");
        if (cert.isFile() && key.isFile()) return;
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA"); gen.initialize(2048);
        var pair = gen.generateKeyPair();
        var dn = new X500Principal("CN=MoonCast");
        long now = System.currentTimeMillis();
        var builder = new JcaX509v3CertificateBuilder(dn, BigInteger.valueOf(now),
            new Date(now-86400000L), new Date(now+10L*365*86400000L), dn, pair.getPublic());
        var signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption").build(pair.getPrivate());
        var x509 = new JcaX509CertificateConverter().getCertificate(builder.build(signer));
        // Private app storage; never include these identity files in exported logs/backups.
        Files.write(key.toPath(), pem("PRIVATE KEY", pair.getPrivate().getEncoded()));
        Files.write(cert.toPath(), pem("CERTIFICATE", x509.getEncoded()));
    }
    private static byte[] pem(String type, byte[] data) {
        return ("-----BEGIN " + type + "-----\n" + Base64.encodeToString(data, Base64.DEFAULT)
            + "-----END " + type + "-----\n").getBytes(StandardCharsets.US_ASCII);
    }
}
