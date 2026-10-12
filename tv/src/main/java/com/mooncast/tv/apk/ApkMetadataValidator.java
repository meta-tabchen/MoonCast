package com.mooncast.tv.apk;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Locale;

/** Metadata and byte-integrity checks, not a malware scan or trust decision. */
public final class ApkMetadataValidator {
    public static final class Metadata {
        public final String label, packageName, version, certificateSha256, sha256;
        public final long bytes;
        Metadata(String label, String packageName, String version, String cert, String hash, long bytes) {
            this.label=label;this.packageName=packageName;this.version=version;certificateSha256=cert;sha256=hash;this.bytes=bytes;
        }
    }
    private ApkMetadataValidator() {}
    public static void requirePrivateFile(File directory, File file) throws IOException {
        if(file==null || !directory.getCanonicalFile().equals(file.getCanonicalFile().getParentFile())
                || !file.isFile() || !file.canRead() || !file.getName().endsWith(".apk"))
            throw new IOException("Private APK is missing or outside the receiving folder");
    }
    public static String sha256(File file) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(FileInputStream in=new FileInputStream(file)) {
            byte[] buffer=new byte[65536];int count;
            while((count=in.read(buffer))!=-1) {
                if(Thread.currentThread().isInterrupted())throw new IOException("Validation cancelled");
                digest.update(buffer,0,count);
            }
        }
        return hex(digest.digest());
    }
    public static Metadata inspect(PackageManager pm, File directory, File file, String expectedHash) throws Exception {
        requirePrivateFile(directory,file);
        long length=file.length();
        if(length<1 || length>com.mooncast.transfer.ApkTransfer.MAX_BYTES)throw new IOException("Unsupported APK size");
        String hash=sha256(file);
        if(expectedHash==null || !MessageDigest.isEqual(hash.getBytes(java.nio.charset.StandardCharsets.US_ASCII),expectedHash.getBytes(java.nio.charset.StandardCharsets.US_ASCII)))throw new IOException("APK changed or checksum does not match");
        // Standalone validation runs bounded ZIP/CEN/resource preflight before parsing.
        ApkSingleManifest.requireStandalone(file);
        // Android 9/10 archive parsing collects certificates only when the legacy
        // GET_SIGNATURES bit is present, even when GET_SIGNING_CERTIFICATES is set.
        // Request both: verification still happens in PackageParser (skipVerify=false),
        // and SigningInfo remains preferred for current signers on modern Android.
        int flags=PackageManager.GET_SIGNATURES;
        if(Build.VERSION.SDK_INT>=28)flags|=PackageManager.GET_SIGNING_CERTIFICATES;
        PackageInfo info=pm.getPackageArchiveInfo(file.getAbsolutePath(),flags);
        if(info==null || info.applicationInfo==null || info.packageName==null || info.packageName.isEmpty()
                || (info.splitNames!=null && info.splitNames.length>0)
                || (info.applicationInfo.splitSourceDirs!=null && info.applicationInfo.splitSourceDirs.length>0))throw new IOException("Only a complete, ordinary APK is supported");
        Signature[] signatures=Build.VERSION.SDK_INT>=28 && info.signingInfo!=null
                ?info.signingInfo.getApkContentsSigners():info.signatures;
        if(signatures==null || signatures.length==0)throw new IOException("APK signing certificate could not be read");
        StringBuilder certs=new StringBuilder();
        for(Signature signature:signatures) {
            if(certs.length()>0)certs.append('\n');
            certs.append(hex(MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())));
        }
        info.applicationInfo.sourceDir=file.getAbsolutePath();info.applicationInfo.publicSourceDir=file.getAbsolutePath();
        String label=safe(String.valueOf(pm.getApplicationLabel(info.applicationInfo)));
        long code=Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode;
        return new Metadata(label,safe(info.packageName),safe(info.versionName)+" ("+code+")",certs.toString(),hash,length);
    }
    private static String safe(String text) {
        if(text==null)return "";
        StringBuilder result=new StringBuilder();
        text.codePoints().limit(180).filter(c->!Character.isISOControl(c)&&Character.getType(c)!=Character.FORMAT
                && Character.getType(c)!=Character.LINE_SEPARATOR && Character.getType(c)!=Character.PARAGRAPH_SEPARATOR
                && Character.getType(c)!=Character.SURROGATE).forEach(result::appendCodePoint);
        return result.toString();
    }
    private static String hex(byte[] bytes) {
        StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();
    }
}
