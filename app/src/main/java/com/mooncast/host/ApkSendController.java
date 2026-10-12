package com.mooncast.host;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import com.mooncast.transfer.ApkTransfer;
import com.mooncast.transfer.ApkTransferClient;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Activity-owned one-shot sender. No service, persisted credential, saved URI, or automatic retry. */
final class ApkSendController implements AutoCloseable {
    interface Listener {
        void preparing(long bytes);
        void ready(ApkStager.Selection selection);
        void sending(long sent, long total);
        void received();
        void failed(boolean network, String reason);
    }
    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(1), r -> new Thread(r, "apk-send-work"));
    private final ExecutorService cleanup = Executors.newSingleThreadExecutor(r -> new Thread(r, "apk-send-cleanup"));
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "apk-prepare-deadline"));
    private volatile int generation;
    private volatile boolean closed;
    private ApkStager.Work work;
    private ApkStager.Selection selected;
    private volatile ApkTransferClient client;
    private Future<?> task;

    ApkSendController(Context context, Listener listener) {
        // This controller has the Activity lifetime. Keep its newly wrapped locale after a
        // language change; the process-wide Application can still have the previous locale.
        this.context = context; this.listener = listener;
        worker.execute(() -> ApkStager.clearStale(this.context));
    }
    void prepare(Uri uri) {
        cancel(); if (closed) return;
        final int run = generation;
        final ApkStager.Work current = new ApkStager.Work(); work = current;
        listener.preparing(0);
        submit(run, false, () -> {
            ScheduledFuture<?> timeout = timer.schedule(() -> { current.canceled = true; current.cancelAndDelete(); }, ApkTransfer.SESSION_MILLIS, TimeUnit.MILLISECONDS);
            try {
                ApkStager.Selection value = ApkStager.prepare(context, uri, current,
                    bytes -> post(run, () -> listener.preparing(bytes)));
                post(run, () -> { selected = value; listener.ready(value); });
            } finally { timeout.cancel(false); }
        });
    }
    void send(ApkEndpoint endpoint) {
        if (closed || selected == null || work == null || endpoint == null) return;
        final int run = generation;
        final ApkStager.Selection apk = selected;
        final ApkStager.Work current = work;
        // Prevent repeated Send taps even before the worker starts.
        selected = null; listener.sending(0, apk.bytes);
        submit(run, true, () -> {
            ApkTransferClient connection = new ApkTransferClient(endpoint.address, endpoint.port);
            client = connection;
            try {
                if (run != generation || current.canceled) return;
                connection.send(apk.file, apk.name, apk.sha256,
                    (sent, total) -> post(run, () -> listener.sending(sent, total)));
            } finally {
                connection.close(); if (client == connection) client = null;
                current.cancelAndDelete();
            }
            post(run, listener::received);
        });
    }
    private interface Job { void run() throws Exception; }
    private void submit(int run, boolean network, Job job) {
        try {
            task = worker.submit(() -> {
                try { if (run == generation && !closed) job.run(); }
                catch (Exception error) {
                    ApkStager.Work current = work;
                    if (run == generation && current != null) current.cancelAndDelete();
                    post(run, () -> { selected = null; listener.failed(network, failureReason(error, network)); });
                }
            });
        } catch (RejectedExecutionException ignored) {
            cancel(); if (!closed) listener.failed(network, context.getString(R.string.apk_busy_error));
        }
    }
    private String failureReason(Exception error, boolean network) {
        if (error instanceof SecurityException) return context.getString(R.string.apk_access_error);
        if (error instanceof java.io.InterruptedIOException)
            return context.getString(network ? R.string.phone_apk_transfer_timeout : R.string.apk_timeout_error);
        if (error instanceof java.io.IOException && error.getMessage() != null) {
            String detail = ApkStager.displayText(error.getMessage(), 240);
            if (network) return context.getString(R.string.phone_technical_details,
                context.getString(R.string.phone_apk_network_detail), detail);
            if (PhoneMessages.isApkParserDiagnostic(detail)) return context.getString(R.string.phone_technical_details,
                context.getString(R.string.phone_apk_invalid_container), detail);
            return context.getString(R.string.phone_technical_details, context.getString(R.string.apk_prepare_error), detail);
        }
        return context.getString(R.string.apk_metadata_error, error.getClass().getSimpleName());
    }
    private void post(int run, Runnable callback) { main.post(() -> { if (run == generation && !closed) callback.run(); }); }
    void cancel() {
        ++generation; selected = null;
        ApkStager.Work old = work; work = null;
        if (old != null) old.canceled = true;
        ApkTransferClient oldClient = client; client = null;
        if (task != null) { task.cancel(true); task = null; }
        worker.getQueue().clear();
        if (old != null || oldClient != null) {
            try { cleanup.execute(() -> { if (oldClient != null) oldClient.close(); if (old != null) old.cancelAndDelete(); }); }
            catch (RejectedExecutionException ignored) { /* All resources were already closed by close(). */ }
        }
    }
    @Override public void close() {
        if (closed) return;
        closed = true; cancel(); worker.shutdownNow(); timer.shutdownNow(); cleanup.shutdown(); main.removeCallbacksAndMessages(null);
    }
}
