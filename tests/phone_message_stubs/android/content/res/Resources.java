package android.content.res;
/** Resource-name lookup used by the production message renderer. */
public abstract class Resources {
    public static class NotFoundException extends RuntimeException {}
    public abstract String getResourceEntryName(int id);
}
