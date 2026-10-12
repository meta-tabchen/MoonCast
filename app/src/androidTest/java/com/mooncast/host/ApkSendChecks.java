package com.mooncast.host;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.EditText;
import com.mooncast.transfer.ApkTransfer;
import java.io.File;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Real Android content-provider, archive metadata, cancellation, and Activity lifecycle checks. */
final class ApkSendChecks {
    static void verify(Instrumentation test) throws Exception {
        Context context = test.getTargetContext();
        Uri root = Uri.parse("content://" + test.getContext().getPackageName() + ".apkfixture/");
        Intent clipped = new Intent(); clipped.setClipData(android.content.ClipData.newRawUri("fixture", root));
        check(root.equals(ApkSendActivity.selectedDocument(clipped)), "single ClipData-only picker result accepted");
        check(ApkSendActivity.selectedDocument(new Intent()) == null, "empty picker result diagnosed");
        check(ApkEndpoint.parse("192.168.1.20:1245") != null, "private numeric endpoint");
        for (String value : new String[]{"127.0.0.1:80", "8.8.8.8:80", "http://192.168.1.1:80", "localhost:80", "192.168.01.2:80", "192.168.1.2:0"})
            check(ApkEndpoint.parse(value) == null, "reject nonprivate, ambiguous or URL endpoint");
        check("safe.apk".equals(ApkStager.displayText("sa\u202Efe\n.apk", 80)), "remove untrusted Unicode controls");
        AtomicReference<ApkSendController> controller = new AtomicReference<>();
        try {
            CountDownLatch ready = new CountDownLatch(1); AtomicReference<ApkStager.Selection> prepared = new AtomicReference<>();
            test.runOnMainSync(() -> {
                controller.set(new ApkSendController(context, new QuietListener() {
                    public void ready(ApkStager.Selection selection) { prepared.set(selection); ready.countDown(); }
                    public void failed(boolean network, String reason) { ready.countDown(); }
                }));
                controller.get().prepare(root.buildUpon().appendPath("ordinary").build());
            });
            check(ready.await(20, TimeUnit.SECONDS), "SAF prepare deadline");
            ApkStager.Selection apk = prepared.get(); check(apk != null, "actual signed test APK prepared");
            check(apk.packageName.equals(test.getContext().getPackageName()), "PackageManager package identity");
            check(apk.signers.matches("[a-f0-9]{64}(\\n[a-f0-9]{64})*"), "PackageManager signer digest");
            check(apk.bytes == new File(test.getContext().getApplicationInfo().sourceDir).length(), "exact spool length");
            check(apk.sha256.equals(ApkTransfer.hash(new File(test.getContext().getApplicationInfo().sourceDir))), "finalized exact spool SHA-256");
            test.runOnMainSync(controller.get()::cancel); awaitDeleted(apk.file);
            test.runOnMainSync(controller.get()::close);

            for (String mode : new String[]{"missing-name", "extensionless", "query-unavailable"}) {
                CountDownLatch done = new CountDownLatch(1); AtomicReference<ApkStager.Selection> result = new AtomicReference<>();
                test.runOnMainSync(() -> {
                    controller.set(new ApkSendController(context, new QuietListener() {
                        public void ready(ApkStager.Selection value) { result.set(value); done.countDown(); }
                        public void failed(boolean network, String reason) { done.countDown(); }
                    })); controller.get().prepare(root.buildUpon().appendPath(mode).build());
                });
                check(done.await(20, TimeUnit.SECONDS) && result.get() != null, "valid signed APK with optional provider metadata: " + mode);
                check(result.get().name.equals("selected.apk") && result.get().packageName.equals(test.getContext().getPackageName()), "content identity with safe fallback name");
                test.runOnMainSync(controller.get()::close); awaitEmpty(context);
            }

            for (String mode : new String[]{"bundle", "oversize", "invalid"}) {
                CountDownLatch failed = new CountDownLatch(1); AtomicInteger readyCount = new AtomicInteger();
                test.runOnMainSync(() -> {
                    controller.set(new ApkSendController(context, new QuietListener() {
                        public void ready(ApkStager.Selection selection) { readyCount.incrementAndGet(); failed.countDown(); }
                        public void failed(boolean network, String reason) { check(reason != null && !reason.isEmpty(), "specific failure reason"); if (!network) failed.countDown(); }
                    })); controller.get().prepare(root.buildUpon().appendPath(mode).build());
                });
                check(failed.await(10, TimeUnit.SECONDS) && readyCount.get() == 0, "reject " + mode);
                test.runOnMainSync(controller.get()::close); awaitEmpty(context);
            }
            for (String mode : new String[]{"blocked", "slow-query", "slow-open"}) {
                CountDownLatch subsequentReady = new CountDownLatch(1); AtomicInteger failures = new AtomicInteger();
                test.runOnMainSync(() -> {
                    controller.set(new ApkSendController(context, new QuietListener() {
                        public void ready(ApkStager.Selection selection) { subsequentReady.countDown(); }
                        public void failed(boolean network, String reason) { failures.incrementAndGet(); }
                    })); controller.get().prepare(root.buildUpon().appendPath(mode).build());
                });
                Thread.sleep(350);
                test.runOnMainSync(() -> { controller.get().cancel(); controller.get().prepare(root.buildUpon().appendPath("ordinary").build()); });
                check(subsequentReady.await(10, TimeUnit.SECONDS), "cancel " + mode + " releases worker for explicit retry");
                check(failures.get() == 0, "no stale failure after " + mode + " cancellation");
                test.runOnMainSync(controller.get()::close); awaitEmpty(context);
            }
        } finally { if (controller.get() != null) test.runOnMainSync(controller.get()::close); }
        verifyActivity(test, root); awaitEmpty(context); MyTvChecks.verify(test);
    }

    private static void verifyActivity(Instrumentation test, Uri root) throws Exception {
        Context context = test.getTargetContext();
        ApkSendActivity activity = (ApkSendActivity) test.startActivitySync(new Intent(context, ApkSendActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        AtomicReference<ApkSendActivity> latestActivity = new AtomicReference<>(activity);
        try {
            AtomicReference<Bundle> saved = new AtomicReference<>();
            test.runOnMainSync(() -> {
                check(activity.findViewById(android.R.id.content).findViewWithTag("apk_code") == null, "no pairing code field");
                check(activity.findViewById(android.R.id.content).findViewWithTag("apk_manual_address") == null, "manual entry belongs only to shared My TV picker");
                Bundle state = new Bundle(); test.callActivityOnSaveInstanceState(activity, state); saved.set(state);
            });
            AtomicReference<ApkSendController> controller = new AtomicReference<>();
            test.runOnMainSync(() -> {
                try {
                    controller.set((ApkSendController) field(activity, "controller"));
                    controller.get().prepare(root.buildUpon().appendPath("blocked").build());
                    test.callActivityOnPause(activity); test.callActivityOnStop(activity);
                    check(field(activity, "selection") == null, "background clears selection");
                    test.callActivityOnStart(activity); test.callActivityOnResume(activity);
                } catch (Exception error) { throw new AssertionError(error); }
            });
            awaitEmpty(context);
            // Simulate SAF's result contract. Stopped picker handoff keeps no active transfer and
            // the returned content URI is prepared only when the Activity is resumed.
            test.runOnMainSync(() -> {
                try {
                    set(activity, "awaitingDocument", true); test.callActivityOnPause(activity); test.callActivityOnStop(activity);
                    activity.onActivityResult(81, Activity.RESULT_OK, new Intent().setData(root.buildUpon().appendPath("ordinary").build()));
                    check(field(activity, "selection") == null, "picker result waits for foreground");
                    test.callActivityOnStart(activity); test.callActivityOnResume(activity);
                } catch (Exception error) { throw new AssertionError(error); }
            });
            long end = SystemClock.elapsedRealtime() + 20000;
            AtomicReference<Object> selected = new AtomicReference<>();
            while (SystemClock.elapsedRealtime() < end) {
                test.runOnMainSync(() -> { try { selected.set(field(activity, "selection")); } catch (Exception error) { throw new AssertionError(error); } });
                if (selected.get() != null) break; Thread.sleep(50);
            }
            check(selected.get() != null, "SAF handoff does not immediately cancel preparation");
            test.runOnMainSync(() -> {
                View cancel = activity.findViewById(android.R.id.content).findViewWithTag("apk_cancel"); cancel.performClick();
            });
            awaitEmpty(context);
            // A recreated controller never uses saved credentials or resumes a network operation.
            Instrumentation.ActivityMonitor monitor = test.addMonitor(ApkSendActivity.class.getName(), null, false);
            try {
                test.runOnMainSync(activity::recreate);
                Activity recreated = test.waitForMonitorWithTimeout(monitor, 10000);
                check(recreated instanceof ApkSendActivity, "Activity recreated"); latestActivity.set((ApkSendActivity) recreated);
                test.runOnMainSync(() -> {
                    try {
                        check(recreated.findViewById(android.R.id.content).findViewWithTag("apk_code") == null, "recreation has no code field");
                        check(field(recreated, "selection") == null && field(recreated, "endpoint") == null,
                            "recreation drops selection and endpoint without restarting network transfer");
                    } catch (Exception error) { throw new AssertionError(error); }
                });
            } finally { test.removeMonitor(monitor); }
        } finally { test.runOnMainSync(() -> latestActivity.get().finish()); }
    }
    private abstract static class QuietListener implements ApkSendController.Listener {
        public void preparing(long bytes) {} public void ready(ApkStager.Selection selection) {}
        public void sending(long sent, long total) {} public void received() {} public void failed(boolean network, String reason) {}
    }
    private static Object field(Object target, String name) throws Exception { Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
    private static void set(Object target, String name, Object value) throws Exception { Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value); }
    private static void awaitDeleted(File file) throws Exception { long end = SystemClock.elapsedRealtime() + 5000; while (file.exists() && SystemClock.elapsedRealtime() < end) Thread.sleep(25); check(!file.exists(), "temporary APK deleted"); }
    private static void awaitEmpty(Context context) throws Exception { long end = SystemClock.elapsedRealtime() + 5000; while (SystemClock.elapsedRealtime() < end) { File[] files = ApkStager.directory(context).listFiles(); if (files == null || files.length == 0) return; Thread.sleep(25); } throw new AssertionError("staging directory did not clear"); }
    private static void check(boolean pass, String message) { if (!pass) throw new AssertionError(message); }
}
