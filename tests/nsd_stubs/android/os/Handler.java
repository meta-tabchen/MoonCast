package android.os;
import java.util.ArrayList;
public final class Handler {
    private record Task(Handler owner, Runnable run, long at, long order) {}
    private static final ArrayList<Task> tasks = new ArrayList<>();
    private static long now, sequence;
    public Handler(Looper looper) {}
    public boolean post(Runnable run) { return postDelayed(run, 0); }
    public boolean postDelayed(Runnable run, long delay) { tasks.add(new Task(this, run, now + delay, sequence++)); return true; }
    public void removeCallbacks(Runnable run) { tasks.removeIf(t -> t.owner == this && t.run == run); }
    public void removeCallbacksAndMessages(Object token) { tasks.removeIf(t -> t.owner == this); }
    public static void drain() { advance(0); }
    public static void advance(long millis) {
        long target = now + millis;
        while (true) {
            Task first = null;
            for (Task t : tasks) if (t.at <= target && (first == null || t.at < first.at || t.at == first.at && t.order < first.order)) first = t;
            if (first == null) break;
            tasks.remove(first); now = first.at; first.run.run();
        }
        now = target;
    }
    public static long now() { return now; }
}
