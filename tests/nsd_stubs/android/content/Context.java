package android.content;
public abstract class Context {
    public static final String WIFI_SERVICE = "wifi";
    public Context getApplicationContext() { return this; }
    public abstract <T> T getSystemService(Class<T> type);
    public abstract Object getSystemService(String name);
}
