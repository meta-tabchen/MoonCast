// SPDX-License-Identifier: GPL-3.0-or-later
package android.os;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
/** A manually pumped fake main queue; nothing runs inline on the posting worker. */
public final class Looper {
    private static final Looper MAIN = new Looper(Thread.currentThread());
    private final Thread owner;
    private final LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
    private Looper(Thread owner) { this.owner = owner; }
    public static Looper getMainLooper() { return MAIN; }
    public static Looper myLooper() { return Thread.currentThread() == MAIN.owner ? MAIN : null; }
    void post(Runnable action) { queue.add(action); }
    public static void drain() { requireMain(); Runnable action; while ((action = MAIN.queue.poll()) != null) action.run(); }
    public static void pump(long timeoutMillis) throws InterruptedException {
        requireMain(); Runnable action = MAIN.queue.poll(timeoutMillis, TimeUnit.MILLISECONDS); if (action != null) action.run();
    }
    public static int pending() { return MAIN.queue.size(); }
    public static void requireMain() { if (myLooper() != MAIN) throw new AssertionError("Expected synthetic main thread"); }
}
