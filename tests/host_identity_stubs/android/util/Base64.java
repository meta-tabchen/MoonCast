package android.util;
/** Test-only Android DEFAULT PEM encoding, backed by the host JDK. */
public final class Base64 {
    public static final int DEFAULT = 0;
    public static String encodeToString(byte[] data, int flags) {
        return java.util.Base64.getMimeEncoder(76, new byte[]{'\n'}).encodeToString(data) + "\n";
    }
}
