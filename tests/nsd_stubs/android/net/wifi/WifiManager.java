package android.net.wifi;
public class WifiManager {
    public final MulticastLock lock = new MulticastLock();
    public MulticastLock createMulticastLock(String name) { return lock; }
    public static class MulticastLock {
        private boolean held; public int acquired, released;
        public void setReferenceCounted(boolean b) {}
        public void acquire() { held = true; acquired++; }
        public void release() { held = false; released++; }
        public boolean isHeld() { return held; }
    }
}
