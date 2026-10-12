package com.mooncast.host;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.security.*;
import java.security.cert.*;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

/** Calls the production identity initializer; identities exist only in test temp directories. */
public final class HostIdentityCompatibilityTest {
    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[1]); Files.createDirectories(dir);
        if (args[0].equals("expect-mixed-failure")) {
            try { HostIdentity.ensure(dir.toFile()); }
            catch (LinkageError failure) {
                if (!String.valueOf(failure.getMessage()).contains("org/bouncycastle/")) throw failure;
                System.out.println("EXPECTED mixed-version first-start failure: " + failure);
                return;
            }
            throw new AssertionError("Mixed dependencies unexpectedly generated a fresh identity");
        }
        Path keyFile=dir.resolve("host-key.pem"), certFile=dir.resolve("host-cert.pem");
        byte[] oldKey=Files.exists(keyFile)?Files.readAllBytes(keyFile):null;
        byte[] oldCert=Files.exists(certFile)?Files.readAllBytes(certFile):null;
        FileTime oldKeyTime=oldKey==null?null:Files.getLastModifiedTime(keyFile);
        FileTime oldCertTime=oldCert==null?null:Files.getLastModifiedTime(certFile);
        HostIdentity.ensure(dir.toFile());
        if(oldKey!=null && (!Arrays.equals(oldKey,Files.readAllBytes(keyFile))
                || !Arrays.equals(oldCert,Files.readAllBytes(certFile))
                || !oldKeyTime.equals(Files.getLastModifiedTime(keyFile))
                || !oldCertTime.equals(Files.getLastModifiedTime(certFile))))
            throw new AssertionError("Existing test identity was rewritten");
        X509Certificate cert;
        try(InputStream input=Files.newInputStream(certFile)) {
            cert=(X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(input);
        }
        cert.checkValidity(); cert.verify(cert.getPublicKey());
        BouncyCastleProvider provider=new BouncyCastleProvider();
        PrivateKey key;
        try(PEMParser parser=new PEMParser(Files.newBufferedReader(keyFile))) {
            key=new JcaPEMKeyConverter().setProvider(provider).getPrivateKey((PrivateKeyInfo)parser.readObject());
        }
        byte[] message="MoonCast fixture, no real host identity".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Signature signature=Signature.getInstance("SHA256withRSA",provider);
        signature.initSign(key);signature.update(message);byte[] signed=signature.sign();
        signature.initVerify(cert.getPublicKey());signature.update(message);
        if(!signature.verify(signed))throw new AssertionError("RSA/certificate compatibility");
        Cipher cipher=Cipher.getInstance("AES/ECB/NoPadding",provider);
        SecretKeySpec aes=new SecretKeySpec(new byte[16],"AES");
        cipher.init(Cipher.ENCRYPT_MODE,aes);byte[] encrypted=cipher.doFinal(new byte[16]);
        cipher.init(Cipher.DECRYPT_MODE,aes);
        if(!Arrays.equals(new byte[16],cipher.doFinal(encrypted)))throw new AssertionError("TV pairing AES primitive compatibility");
        // A fixed past timestamp makes same-byte rewrites observable even on coarse filesystems.
        // These files were created exclusively under the runner's TemporaryDirectory.
        FileTime sentinel=FileTime.fromMillis(946684800000L);
        Files.setLastModifiedTime(keyFile,sentinel);Files.setLastModifiedTime(certFile,sentinel);
        byte[] beforeKey=Files.readAllBytes(keyFile), beforeCert=Files.readAllBytes(certFile);
        FileTime beforeKeyTime=Files.getLastModifiedTime(keyFile), beforeCertTime=Files.getLastModifiedTime(certFile);
        HostIdentity.ensure(dir.toFile());
        if(!Arrays.equals(beforeKey,Files.readAllBytes(keyFile))
                || !Arrays.equals(beforeCert,Files.readAllBytes(certFile))
                || !beforeKeyTime.equals(Files.getLastModifiedTime(keyFile))
                || !beforeCertTime.equals(Files.getLastModifiedTime(certFile)))
            throw new AssertionError("Repeated ensure rewrote test PEM bytes or modification times");
        System.out.println("PASS " + args[0] + ": production identity init, preserved existing and repeated PEM bytes/mtime, certificate parse, RSA signature and TV pairing AES primitives; not device interoperability");
    }
}
