// SPDX-License-Identifier: GPL-3.0-or-later
package com.mooncast.tv.gamestream;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Looper;
import android.view.Surface;
import com.limelight.nvstream.NvConnection;
import com.mooncast.receiver.StreamSpec;
import com.mooncast.tv.gamestream.hosts.HostEndpoint;
import com.mooncast.tv.gamestream.hosts.HostRecord;
import com.mooncast.tv.gamestream.render.GameStreamAudioRenderer;
import com.mooncast.tv.gamestream.render.GameStreamVideoRenderer;
import com.mooncast.tv.gamestream.testing.Synthetic;
import com.mooncast.tv.gamestream.ui.GameStreamDialog;
import com.mooncast.tv.net.LanAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Actual production controller/session/host coordinator, with explicit synthetic collaborators. */
public final class GameStreamControllerContractTest {
    private static final List<GameStreamController> controllers = new ArrayList<>();
    private static final List<GameStreamHosts> browsers = new ArrayList<>();
    private static final HostRecord HOST = new HostRecord(new HostEndpoint(), "Synthetic host", 47984, "test", null);
    private static final LanAccess LAN = new LanAccess();
    private static final StreamSpec SPEC = StreamSpec.conservative1080p();

    public static void main(String[] args) throws Exception {
        Looper.getMainLooper(); // Bind the fake main looper before any worker exists.
        if (args.length != 1) throw new IllegalArgumentException("One isolated scenario is required");
        try {
            switch (args[0]) {
                case "double-connect-identity": doubleConnectBeforeIdentity(); break;
                case "focus-audio-release": focusHeldUntilAudioRelease(); break;
                case "stop-prepare": stopDuringPrepare(); break;
                case "stop-start": stopDuringStart(); break;
                case "late-callbacks": lateCallbacks(); break;
                case "recreation-barrier": recreatedControllerBarrier(); break;
                case "failed-release-barrier": failedReleaseBarrier(); break;
                case "focus-denied": focusDenied(); break;
                case "focus-loss-release": focusLossRelease(); break;
                case "error-failed-release": errorFailedRelease(); break;
                case "main-thread-contract": mainThreadContract(); break;
                case "hosts-cancel-pair": hostsCancelPair(); break;
                case "hosts-cancel-save": hostsCancelSave(); break;
                case "hosts-corrupt-store": hostsCorruptStore(); break;
                default: throw new IllegalArgumentException("Unknown scenario: " + args[0]);
            }
            check(Synthetic.identityCreates.get() == (args[0].startsWith("hosts-cancel-") ? 1 : 0),
                    "Connect must never create pairing identities");
        } finally {
            Synthetic.releaseAll();
            for (GameStreamHosts browser : browsers) browser.close();
            for (GameStreamController controller : controllers) controller.close();
            pumpUntil(() -> GameStreamController.globalReleaseBarrier().isDone(), "final release");
            Looper.drain();
        }
        System.out.println("PASS " + args[0] + " (synthetic scheduling; no device/network evidence)");
    }

    private static Fixture fixture() {
        Context context = new Context(); Recording listener = new Recording();
        GameStreamController controller = new GameStreamController(context, listener);
        controllers.add(controller); return new Fixture(context, listener, controller);
    }
    private static void connect(Fixture fixture) { fixture.controller.connect(HOST, LAN, SPEC, new Surface()); }
    private static void started(Fixture fixture) throws Exception {
        connect(fixture); Synthetic.start.awaitEntered();
        pumpUntil(() -> Synthetic.connection != null && Synthetic.connection.listener != null, "session callback installation");
    }
    private static void released(Fixture fixture) throws Exception {
        CompletableFuture<Void> stopped = fixture.controller.stopAsync();
        pumpUntil(stopped::isDone, "controller release");
        check(!stopped.isCompletedExceptionally(), "Expected successful release");
    }

    private static void doubleConnectBeforeIdentity() throws Exception {
        Synthetic.identity.hold(); Fixture first = fixture(); connect(first);
        Synthetic.identity.awaitEntered();
        expectIllegal(() -> connect(first), "same controller accepted a second pending Connect");
        Fixture second = fixture();
        expectIllegal(() -> connect(second), "different controller bypassed process-wide pending ownership");
        check(Synthetic.clients.get() == 0, "Identity gate did not precede client construction");
        check(Synthetic.audios.get() == 1 && Synthetic.videos.get() == 1, "Rejected Connect created renderers");
        check(first.context.audioManager.requested == 1 && second.context.audioManager.requested == 0,
                "Rejected Connect requested focus");
        AtomicReference<CompletableFuture<Void>> stopped = new AtomicReference<>();
        returnsWithoutGate(() -> stopped.set(first.controller.stopAsync()), Synthetic.identity, "Stop during identity load");
        check(!stopped.get().isDone(), "Release completed before pending worker relinquished ownership");
        Synthetic.identity.release(); pumpUntil(stopped.get()::isDone, "cancelled identity load");
        check(Synthetic.prepares.get() == 0 && Synthetic.starts.get() == 0, "Cancelled identity load started protocol work");
        check(Synthetic.identityLoads.get() == 1, "Duplicate Connect reached identity lookup");
    }

    private static void focusHeldUntilAudioRelease() throws Exception {
        Synthetic.audioRelease.hold(); Fixture fixture = fixture(); started(fixture);
        CompletableFuture<Void> stopped = fixture.controller.stopAsync();
        Synthetic.audioRelease.awaitEntered();
        check(fixture.controller.audio().stopCalls > 0, "Stop did not promptly silence audio");
        check(fixture.context.audioManager.abandoned == 0, "Focus abandoned before audio release");
        check(!stopped.isDone() && stopped == GameStreamController.globalReleaseBarrier(), "Output barrier released early");
        check(fixture.controller.stopAsync() == stopped, "Repeated Stop returned another ownership barrier");
        Synthetic.audioRelease.release(); pumpUntil(stopped::isDone, "audio release");
        check(!stopped.isCompletedExceptionally(), "Audio release failed");
        check(fixture.context.audioManager.abandoned == 1, "Focus must be abandoned exactly once after release");
        check(Synthetic.stops.get() == 1, "Repeated Stop stopped the same session more than once");
    }

    private static void stopDuringPrepare() throws Exception {
        Synthetic.prepare.hold(); Fixture fixture = fixture(); connect(fixture); Synthetic.prepare.awaitEntered();
        AtomicReference<CompletableFuture<Void>> stopped = new AtomicReference<>();
        returnsWithoutGate(() -> stopped.set(fixture.controller.stopAsync()), Synthetic.prepare, "Stop during prepare");
        check(Synthetic.client.closed, "Stop did not cancel the preparing client promptly");
        check(fixture.controller.audio().stopCalls > 0, "Audio silence was not requested promptly");
        check(!stopped.get().isDone() && fixture.context.audioManager.abandoned == 0, "Prepare released ownership too soon");
        Synthetic.prepare.release(); pumpUntil(stopped.get()::isDone, "prepare cancellation");
        check(!stopped.get().isCompletedExceptionally(), "Cancelled preparation did not release");
        check(Synthetic.starts.get() == 0, "Cancelled preparation started a native session");
        check(fixture.controller.audio().cleanupCalls == 1 && fixture.controller.video().cleanupCalls == 1,
                "Cancelled preparation did not clean both renderers");
    }

    private static void stopDuringStart() throws Exception {
        Synthetic.start.hold(); Fixture fixture = fixture(); connect(fixture); Synthetic.start.awaitEntered();
        AtomicReference<CompletableFuture<Void>> stopped = new AtomicReference<>();
        returnsWithoutGate(() -> stopped.set(fixture.controller.stopAsync()), Synthetic.start,
                "Stop waited for a provider start while holding a main-thread cancellation lock");
        check(Synthetic.client.closed, "Stop during start failed to cancel client");
        check(!stopped.get().isDone(), "Pending provider start was considered released");
        Synthetic.start.release(); pumpUntil(stopped.get()::isDone, "start cancellation");
        check(!stopped.get().isCompletedExceptionally(), "Cancelled start failed release");
        check(Synthetic.stops.get() == 1, "Session returned after cancellation was orphaned");
        check(fixture.controller.audio().cleanupCalls == 1 && fixture.controller.video().cleanupCalls == 1,
                "Session returned after cancellation leaked renderers");
        check(fixture.listener.states.equals(List.of(GameStreamController.State.CONNECTING)), "Cancelled start delivered stale state");
    }

    private static void lateCallbacks() throws Exception {
        Fixture fixture = fixture(); started(fixture);
        NvConnection oldConnection = Synthetic.connection;
        GameStreamVideoRenderer oldVideo = fixture.controller.video();
        GameStreamAudioRenderer oldAudio = fixture.controller.audio();
        oldConnection.listener.connectionStarted(); // Queued, deliberately not drained before Stop.
        oldVideo.listener.onVideoSizeChanged(1920, 1080);
        oldAudio.listener.onError("ignored untrusted text");
        released(fixture);
        check(fixture.listener.states.equals(List.of(GameStreamController.State.CONNECTING)), "Queued old session callback escaped Stop");
        check(fixture.listener.sizes == 0, "Queued old size callback escaped Stop");
        connect(fixture);
        pumpUntil(() -> Synthetic.starts.get() == 2, "replacement session start");
        oldVideo.listener.onVideoSizeChanged(640, 480);
        oldAudio.listener.onError("stale error");
        oldConnection.listener.connectionTerminated(99);
        Looper.drain();
        check(fixture.listener.states.equals(List.of(GameStreamController.State.CONNECTING, GameStreamController.State.CONNECTING)),
                "Old callbacks changed replacement state");
        check(fixture.listener.sizes == 0 && Synthetic.stops.get() == 1, "Stale callback stopped replacement or resized UI");
        Synthetic.connection.listener.connectionStarted(); Looper.drain();
        check(fixture.listener.states.get(2) == GameStreamController.State.ACTIVE, "Current callback did not reach listener");
    }

    private static void recreatedControllerBarrier() throws Exception {
        Synthetic.audioRelease.hold(); Fixture original = fixture(); started(original);
        original.controller.close(); Synthetic.audioRelease.awaitEntered();
        CompletableFuture<Void> barrier = GameStreamController.globalReleaseBarrier(); Fixture replacement = fixture();
        check(replacement.controller.stopAsync() == barrier, "New controller forgot the old Activity's release barrier");
        expectIllegal(() -> connect(replacement), "Recreated Activity started before old output released");
        check(replacement.context.audioManager.requested == 0, "Recreated Activity stole audio focus");
        Synthetic.audioRelease.release(); pumpUntil(barrier::isDone, "old Activity output release");
        check(original.context.audioManager.abandoned == 1, "Closing Activity failed to abandon focus after release");
        connect(replacement); pumpUntil(() -> Synthetic.starts.get() == 2, "recreated controller connection");
        check(replacement.context.audioManager.requested == 1, "Released ownership did not allow new controller");
    }

    private static void failedReleaseBarrier() throws Exception {
        Synthetic.audioReleaseResult = false; Fixture original = fixture(); started(original);
        CompletableFuture<Void> stopped = original.controller.stopAsync(); pumpUntil(stopped::isDone, "failed audio release");
        check(stopped.isCompletedExceptionally(), "Unreleased audio incorrectly completed successfully");
        check(original.context.audioManager.abandoned == 0, "Unreleased audio lost focus protection");
        original.controller.close(); Fixture replacement = fixture();
        check(replacement.controller.stopAsync() == stopped && GameStreamController.globalReleaseBarrier() == stopped,
                "Activity recreation forgot failed output release");
        expectIllegal(() -> connect(replacement), "Failed release admitted a new source");
        check(Synthetic.starts.get() == 1 && replacement.context.audioManager.requested == 0,
                "Failed release created new output resources");
    }

    private static void focusDenied() throws Exception {
        Fixture denied = fixture(); denied.context.audioManager.result = 0;
        expectIllegal(() -> connect(denied), "Connect accepted denied focus");
        check(GameStreamController.globalReleaseBarrier().isDone() && !GameStreamController.globalReleaseBarrier().isCompletedExceptionally(),
                "Denied focus poisoned process ownership without creating output");
        check(Synthetic.clients.get() == 0 && Synthetic.audios.get() == 0, "Denied focus started protocol/output work");
        denied.context.audioManager.result = AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        started(denied); released(denied);
        check(denied.context.audioManager.requested == 2 && denied.context.audioManager.abandoned == 2,
                "Focus denied/retry lifecycle leaked focus");
    }

    private static void focusLossRelease() throws Exception {
        Synthetic.audioRelease.hold(); Fixture fixture = fixture(); started(fixture);
        var focus = fixture.context.audioManager.current;
        focus.handler.post(() -> focus.listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS));
        Looper.drain(); Synthetic.audioRelease.awaitEntered();
        check(fixture.listener.states.equals(List.of(GameStreamController.State.CONNECTING)),
                "Focus loss reported completion before output release");
        check(fixture.context.audioManager.abandoned == 0 && !GameStreamController.globalReleaseBarrier().isDone(),
                "Focus loss surrendered output while audio remained held");
        Synthetic.audioRelease.release(); pumpUntil(() -> fixture.listener.states.size() == 2, "focus-loss terminal callback");
        check(fixture.listener.states.get(1) == GameStreamController.State.FAILED &&
                fixture.listener.codes.get(1).equals("AUDIO_FOCUS_LOST"), "Focus-loss terminal callback lost its reason");
        check(fixture.context.audioManager.abandoned == 1, "Focus loss failed to abandon focus after release");
    }

    private static void errorFailedRelease() throws Exception {
        Synthetic.audioRelease.hold(); Synthetic.audioReleaseResult = false;
        Fixture fixture = fixture(); started(fixture);
        fixture.controller.audio().listener.onError("untrusted failure detail");
        Looper.drain(); Synthetic.audioRelease.awaitEntered();
        check(fixture.listener.states.size() == 1, "Error notified terminal state before release result");
        Synthetic.audioRelease.release(); pumpUntil(() -> fixture.listener.states.size() == 2, "failed-release error callback");
        check(fixture.listener.states.get(1) == GameStreamController.State.FAILED &&
                fixture.listener.codes.get(1).equals("RELEASE_PENDING"), "Failed cleanup was presented as released output");
        check(GameStreamController.globalReleaseBarrier().isCompletedExceptionally() && fixture.context.audioManager.abandoned == 0,
                "Failed cleanup dropped ownership/focus protection");
    }

    private static void mainThreadContract() throws Exception {
        Fixture fixture = fixture();
        for (Runnable mutation : new Runnable[] {() -> connect(fixture), fixture.controller::stopAsync,
                () -> fixture.controller.setSurface(new Surface()), fixture.controller::close}) {
            AtomicReference<Throwable> result = new AtomicReference<>();
            Thread caller = new Thread(() -> { try { mutation.run(); } catch (Throwable e) { result.set(e); } }, "wrong-thread-test");
            caller.start(); caller.join(2000);
            check(!caller.isAlive() && result.get() instanceof IllegalStateException, "Public mutation accepted a non-main caller");
        }
        check(Synthetic.identityLoads.get() == 0 && fixture.context.audioManager.requested == 0, "Wrong-thread mutation changed state");
    }

    private static GameStreamHosts browser(AtomicInteger connections) {
        SharedPreferences preferences = new SharedPreferences() {
            public String getString(String key, String fallback) { return fallback; }
            public Editor edit() {
                return new Editor() {
                    public Editor putString(String key, String value) { return this; }
                    public void apply() { }
                };
            }
        };
        GameStreamHosts browser = new GameStreamHosts(new Activity(), preferences, () -> LAN,
                (host, quality) -> connections.incrementAndGet());
        browsers.add(browser); browser.show(); return browser;
    }

    private static void hostsCancelPair() throws Exception {
        Synthetic.pair.hold(); AtomicInteger connections = new AtomicInteger(); GameStreamHosts browser = browser(connections);
        GameStreamDialog oldDialog = Synthetic.dialog;
        pumpUntil(() -> !oldDialog.busy, "initial saved-host load");
        oldDialog.listener.onPairConfirmed(HOST.endpoint, "Synthetic host"); Synthetic.pair.awaitEntered();
        returnsWithoutGate(browser::dismiss, Synthetic.pair, "Dialog cancellation waited for pairing worker");
        check(Synthetic.client.closed, "Dialog cancellation did not close pending client");
        Synthetic.pair.release();
        // show() queues a load after the cancelled pair task, providing a deterministic worker fence.
        browser.show(); pumpUntil(() -> Synthetic.loads.get() == 2, "pair cancellation worker fence"); Looper.drain();
        check(Synthetic.saves.get() == 0 && connections.get() == 0, "Cancelled pairing saved trust or auto-connected");
        check(oldDialog.errors == 0, "Cancelled pairing delivered an error to dismissed UI");
    }

    private static void hostsCancelSave() throws Exception {
        Synthetic.save.hold(); AtomicInteger connections = new AtomicInteger(); GameStreamHosts browser = browser(connections);
        pumpUntil(() -> !Synthetic.dialog.busy, "initial saved-host load");
        Synthetic.dialog.listener.onPairConfirmed(HOST.endpoint, "Synthetic host"); Synthetic.save.awaitEntered();
        returnsWithoutGate(browser::dismiss, Synthetic.save, "Dialog cancellation waited for authorized trust fsync");
        Synthetic.save.release(); browser.show();
        pumpUntil(() -> Synthetic.loads.get() == 2, "trust save cancellation worker fence"); Looper.drain();
        check(Synthetic.saves.get() == 1, "Already-started authorized trust write was incorrectly rolled back");
        check(connections.get() == 0, "A finished trust write auto-connected after dismissal");
    }

    private static void hostsCorruptStore() throws Exception {
        Synthetic.savedHosts = List.of(HOST);
        AtomicInteger connections = new AtomicInteger(); GameStreamHosts browser = browser(connections);
        GameStreamDialog original = Synthetic.dialog;
        pumpUntil(() -> !original.busy, "initial trusted host load");
        check(original.saved.equals(List.of(HOST)), "Initial trusted host did not populate dialog");
        Synthetic.storeLoad.hold(); Synthetic.storeLoadFailure = new IllegalStateException("Synthetic corrupt trust store");
        browser.show(); GameStreamDialog blocked = Synthetic.dialog;
        check(blocked.saved.isEmpty(), "Reopening retained a cached trusted host before trust reload");
        attemptHostActions(blocked);
        check(connections.get() == 0 && blocked.confirmations == 0 && blocked.shownPins == 0,
                "Pending trust load admitted cached/manual/discovered host or explicit Pair");
        int pendingErrors = blocked.errors;
        Synthetic.storeLoad.release(); pumpUntil(() -> blocked.errors > pendingErrors && !blocked.busy, "corrupt trust-store report");
        check(blocked.saved.isEmpty(), "Failed load exposed stale cached host trust");
        attemptHostActions(blocked); Looper.drain();
        check(connections.get() == 0 && blocked.confirmations == 0 && blocked.shownPins == 0 && !blocked.busy,
                "Corrupt trust store permitted cached/manual host, Pair confirmation, or Forget");
        check(Synthetic.identityCreates.get() == 0 && Synthetic.pairs.get() == 0 && Synthetic.clients.get() == 0 &&
                Synthetic.saves.get() == 0 && Synthetic.forgets.get() == 0,
                "Corrupt trust store caused pairing, identity creation, trust mutation, or client construction");
    }

    private static void attemptHostActions(GameStreamDialog dialog) {
        dialog.listener.onHostSelected(HOST.endpoint, "Cached paired host", HOST);
        dialog.listener.onHostSelected(HOST.endpoint, "Manual/discovered host", null);
        dialog.listener.onPairConfirmed(HOST.endpoint, "Explicit stale confirmation");
        dialog.listener.onForgetHost(HOST);
    }

    /** No latency benchmark: a watchdog only breaks regressions that block main behind a held worker gate. */
    private static void returnsWithoutGate(Runnable action, Synthetic.Gate gate, String message) throws Exception {
        CountDownLatch returned = new CountDownLatch(1); AtomicBoolean blocked = new AtomicBoolean();
        Thread watchdog = new Thread(() -> {
            try {
                if (!returned.await(2, TimeUnit.SECONDS)) { blocked.set(true); gate.release(); }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, "main-blocking-regression-watchdog");
        watchdog.setDaemon(true); watchdog.start();
        try { action.run(); } finally { returned.countDown(); }
        watchdog.join(3000); check(!blocked.get(), message);
    }
    private static void pumpUntil(BooleanSupplier condition, String label) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(6);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) throw new AssertionError("Timed out: " + label);
            Looper.pump(10);
        }
    }
    private static void expectIllegal(Runnable action, String message) {
        try { action.run(); } catch (IllegalStateException expected) { return; }
        throw new AssertionError(message);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private record Fixture(Context context, Recording listener, GameStreamController controller) { }
    private static final class Recording implements GameStreamController.Listener {
        final List<GameStreamController.State> states = new ArrayList<>();
        final List<String> codes = new ArrayList<>(); int sizes;
        public void onState(GameStreamController.State state, String code) { Looper.requireMain(); states.add(state); codes.add(code); }
        public void onVideoSize(int width, int height) { Looper.requireMain(); sizes++; }
    }
}
