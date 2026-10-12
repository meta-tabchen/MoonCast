package android.content;
/** Resource-only host adapter. This does not model Android configuration/lifecycle. */
public abstract class Context {
    public abstract android.content.res.Resources getResources();
    public abstract String getString(int id, Object... args);
}
