package android.net.nsd;
public abstract class NsdManager {
    public static final int PROTOCOL_DNS_SD = 1;
    public interface DiscoveryListener {
        void onDiscoveryStarted(String t); void onDiscoveryStopped(String t);
        void onStartDiscoveryFailed(String t, int e); void onStopDiscoveryFailed(String t, int e);
        void onServiceFound(NsdServiceInfo i); void onServiceLost(NsdServiceInfo i);
    }
    public interface ResolveListener {
        void onResolveFailed(NsdServiceInfo i, int e); void onServiceResolved(NsdServiceInfo i);
        default void onResolutionStopped(NsdServiceInfo i) {} default void onStopResolutionFailed(NsdServiceInfo i, int e) {}
    }
    public abstract void discoverServices(String type, int protocol, DiscoveryListener listener);
    public abstract void stopServiceDiscovery(DiscoveryListener listener);
    public abstract void resolveService(NsdServiceInfo info, ResolveListener listener);
    public abstract void stopServiceResolution(ResolveListener listener);
}
