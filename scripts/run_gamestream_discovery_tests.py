"""Compile the production NSD wrapper against deterministic fake Android APIs.

No network, device, AndroidKeyStore, or host pairing is used. These test state and
callback wiring, not Android's real NSD implementation or Wi-Fi interoperability.
"""
from pathlib import Path
import argparse
import os
import subprocess
import tempfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--source-target-only', action='store_true', help='For stripped local JDKs lacking ct.sym; weaker than --release 17')
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
source = root / 'tv/src/main/java/com/mooncast/tv'
java_home = os.environ.get('JAVA_HOME')
bin_path = Path(java_home) / 'bin' if java_home else None
suffix = '.exe' if os.name == 'nt' else ''
javac = str(bin_path / ('javac' + suffix)) if bin_path else 'javac'
java = str(bin_path / ('java' + suffix)) if bin_path else 'java'
flags = ['-source', '17', '-target', '17'] if args.source_target_only else ['--release', '17']
stubs = {
    'android/content/Context.java': '''package android.content;
public final class Context {
    private final android.net.nsd.NsdManager nsd;
    public Context(android.net.nsd.NsdManager nsd) { this.nsd = nsd; }
    public Context getApplicationContext() { return this; }
    public <T> T getSystemService(Class<T> type) { return type.cast(nsd); }
}''',
    'android/os/Build.java': '''package android.os;
public final class Build { public static final class VERSION { public static int SDK_INT = 26; } }''',
    'android/os/Looper.java': '''package android.os;
public final class Looper {
    private static final Looper MAIN = new Looper();
    public static Looper getMainLooper() { return MAIN; }
    public static Looper myLooper() { return MAIN; }
}''',
    'android/os/Handler.java': '''package android.os;
import java.util.PriorityQueue;
public final class Handler {
    private record Item(long at, long sequence, Runnable action) implements Comparable<Item> {
        public int compareTo(Item other) { int time = Long.compare(at, other.at); return time == 0 ? Long.compare(sequence, other.sequence) : time; }
    }
    private static final PriorityQueue<Item> QUEUE = new PriorityQueue<>();
    private static long now, serial;
    public Handler(Looper looper) {}
    public boolean post(Runnable action) { return postDelayed(action, 0); }
    public boolean postDelayed(Runnable action, long delay) { QUEUE.add(new Item(now + delay, ++serial, action)); return true; }
    public void removeCallbacks(Runnable action) { QUEUE.removeIf(item -> item.action() == action); }
    public static void reset() { QUEUE.clear(); now = serial = 0; }
    public static void flush() { advanceBy(0); }
    public static void advanceBy(long delta) {
        long end = now + delta; int count = 0;
        while (!QUEUE.isEmpty() && QUEUE.peek().at() <= end) {
            if (++count > 10000) throw new AssertionError("Callback loop");
            Item item = QUEUE.remove(); now = item.at(); item.action().run();
        }
        now = end;
    }
}''',
    'android/net/nsd/NsdServiceInfo.java': '''package android.net.nsd;
import java.net.InetAddress;
import java.util.List;
public final class NsdServiceInfo {
    private final String name, type;
    private final int port;
    private final InetAddress host;
    public List<InetAddress> addresses;
    public NsdServiceInfo(String name, String type, int port, InetAddress host) {
        this.name = name; this.type = type; this.port = port; this.host = host; addresses = List.of(host);
    }
    public String getServiceName() { return name; }
    public String getServiceType() { return type; }
    public int getPort() { return port; }
    public InetAddress getHost() { return host; }
    public List<InetAddress> getHostAddresses() { return addresses; }
}''',
    'android/net/nsd/NsdManager.java': '''package android.net.nsd;
import java.util.List;
import java.util.ArrayList;
public final class NsdManager {
    public static final int PROTOCOL_DNS_SD = 1;
    public interface DiscoveryListener {
        void onDiscoveryStarted(String type); void onServiceFound(NsdServiceInfo service);
        void onServiceLost(NsdServiceInfo service); void onStartDiscoveryFailed(String type, int error);
        void onStopDiscoveryFailed(String type, int error); void onDiscoveryStopped(String type);
    }
    public interface ResolveListener {
        void onResolveFailed(NsdServiceInfo service, int error); void onServiceResolved(NsdServiceInfo service);
        default void onResolutionStopped(NsdServiceInfo service) {}
    }
    public final List<DiscoveryListener> browsers = new ArrayList<>(), stopped = new ArrayList<>();
    public final List<ResolveListener> resolves = new ArrayList<>(), cancelled = new ArrayList<>();
    public boolean throwOnDiscover;
    public void discoverServices(String type, int protocol, DiscoveryListener listener) {
        if (!type.equals("_nvstream._tcp.") || protocol != PROTOCOL_DNS_SD) throw new AssertionError("Wrong discovery protocol");
        if (throwOnDiscover) throw new IllegalStateException("private platform details");
        browsers.add(listener);
    }
    public void stopServiceDiscovery(DiscoveryListener listener) { stopped.add(listener); }
    public void resolveService(NsdServiceInfo service, ResolveListener listener) { resolves.add(listener); }
    public void stopServiceResolution(ResolveListener listener) { cancelled.add(listener); }
}''',
}
with tempfile.TemporaryDirectory(prefix='mooncast-gamestream-nsd-tests-') as directory:
    work = Path(directory)
    fake_sources = []
    for name, body in stubs.items():
        path = work / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(body, encoding='utf-8')
        fake_sources.append(path)
    hosts = source / 'gamestream/hosts'
    sources = [source / 'net/LanAccess.java'] + [hosts / (name + '.java') for name in
        ('HostEndpoint', 'HostRecord', 'DiscoveredHost', 'DiscoveryLedger', 'HostDiscovery')]
    sources += [root / 'tests/GameStreamHostDiscoveryTest.java', *fake_sources]
    subprocess.run([javac, *flags, '-encoding', 'UTF-8', '-d', directory, *map(str, sources)], check=True)
    subprocess.run([java, '-cp', directory, 'com.mooncast.tv.gamestream.hosts.GameStreamHostDiscoveryTest'], check=True, timeout=20)
