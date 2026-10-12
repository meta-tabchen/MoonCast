package com.mooncast.host;

public final class CastSessionCoordinatorTest {
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static CastSessionCoordinator recreate(CastSessionCoordinator flow) {
        return new CastSessionCoordinator(flow.selected(), flow.active(), flow.target(), flow.phase(), flow.generation());
    }
    private static void everyProtocolPair() {
        int[] protocols = {CastSessionCoordinator.MOONLIGHT, CastSessionCoordinator.AIRPLAY, CastSessionCoordinator.BROWSER};
        for (int from : protocols) for (int to : protocols) {
            if (from == to) continue;
            CastSessionCoordinator flow = new CastSessionCoordinator(from);
            int oldGrant = flow.begin();
            check(!flow.select(to), "switch while consent pending requires no host teardown");
            check(!flow.accepts(oldGrant, from) && !flow.accepts(oldGrant, to), "old grant cannot cross any protocol boundary");
            int freshGrant = flow.begin();
            flow = recreate(flow);
            check(flow.start(freshGrant, to), "fresh grant still starts selected protocol after rotation");
            check(flow.active() == to, "browser and native service identities stay distinct");
            flow.connected();
            check(flow.select(from), "every active protocol must stop before switching");
            flow = recreate(flow);
            check(flow.active() == to && flow.selected() == to && flow.target() == from, "rotation preserves outgoing service and target");
            check(flow.begin() == -1 && !flow.start(freshGrant, to), "consent cannot be reused during teardown");
            flow.connected();
            check(flow.phase() == CastSessionCoordinator.STOPPING, "late status cannot undo stop");
            flow.hostGone();
            check(flow.selected() == from && flow.active() == -1 && !flow.busy(), "teardown selects target without automatically starting it");
            check(!flow.accepts(freshGrant, from), "consumed grant is invalid after teardown");
        }
    }
    public static void main(String[] args) {
        CastSessionCoordinator flow = new CastSessionCoordinator(CastSessionCoordinator.MOONLIGHT);
        int first = flow.begin();
        check(first > 0 && flow.begin() == -1, "repeated Start must not request another grant");
        check(!flow.select(CastSessionCoordinator.AIRPLAY), "pending consent switch needs no service stop");
        check(!flow.accepts(first, CastSessionCoordinator.MOONLIGHT), "old tab consent discarded");
        int air = flow.begin();
        check(!flow.start(first, CastSessionCoordinator.MOONLIGHT), "stale result cannot launch a host");
        check(flow.start(air, CastSessionCoordinator.AIRPLAY), "fresh AirPlay grant starts");
        check(flow.select(CastSessionCoordinator.MOONLIGHT), "active host must stop before switching");
        check(flow.selected() == CastSessionCoordinator.AIRPLAY && flow.phase() == CastSessionCoordinator.STOPPING,
            "UI stays on old protocol until teardown barrier");
        check(flow.begin() == -1 && !flow.select(CastSessionCoordinator.MOONLIGHT), "no starts/repeated switching during teardown");
        flow.connected();
        check(flow.phase() == CastSessionCoordinator.STOPPING, "late status cannot undo pending stop");
        CastSessionCoordinator restored = new CastSessionCoordinator(flow.selected(), flow.active(), flow.target(), flow.phase(), flow.generation());
        check(restored.target() == CastSessionCoordinator.MOONLIGHT && restored.phase() == CastSessionCoordinator.STOPPING,
            "rotation preserves stop barrier and target");
        restored.hostGone();
        check(restored.selected() == CastSessionCoordinator.MOONLIGHT && !restored.busy(), "death completes switch without automatic start");
        check(!restored.accepts(air, CastSessionCoordinator.AIRPLAY), "consumed grant cannot cross the barrier");
        int fresh = restored.begin();
        restored.denied(fresh);
        check(!restored.busy(), "denied projection restores idle");
        int cancelled = restored.begin(); restored.stop();
        check(!restored.start(cancelled, CastSessionCoordinator.MOONLIGHT), "cancelled grant cannot start later");
        int next = restored.begin();
        CastSessionCoordinator rotated = new CastSessionCoordinator(restored.selected(), restored.active(), restored.target(), restored.phase(), restored.generation());
        check(rotated.accepts(next, CastSessionCoordinator.MOONLIGHT), "rotation preserves an outstanding consent request");
        check(rotated.start(next, CastSessionCoordinator.MOONLIGHT), "result delivered after rotation accepted once");
        check(!rotated.start(next, CastSessionCoordinator.MOONLIGHT), "same result not accepted twice");
        rotated.connected(); rotated.stop(); rotated.hostGone();
        check(rotated.begin() > next, "stop/restart requires a new request generation");
        CastSessionCoordinator process = new CastSessionCoordinator(1); process.restoreHost(0);
        check(process.selected() == 0 && process.hasHost(), "existing host recovers its own tab");
        everyProtocolPair();
        CastSessionCoordinator browser = new CastSessionCoordinator(CastSessionCoordinator.BROWSER);
        int browserGrant = browser.begin();
        browser = recreate(browser);
        browser.denied(browserGrant);
        check(browser.selected() == CastSessionCoordinator.BROWSER && !browser.busy(), "browser denial keeps the tab and allows retry");
        int cancelledBrowserGrant = browser.begin();
        browser.stop();
        check(!browser.start(cancelledBrowserGrant, CastSessionCoordinator.BROWSER), "cancelled browser grant cannot restart capture");
        browser.restoreHost(CastSessionCoordinator.BROWSER);
        check(browser.active() == CastSessionCoordinator.BROWSER && browser.hasHost(), "browser host recovers after activity process restart");
        browser.stop();browser.hostGone();
        check(browser.begin() > cancelledBrowserGrant, "browser stop requires fresh consent");
        check(new CastSessionCoordinator(99).selected() == CastSessionCoordinator.MOONLIGHT, "unknown stored protocol falls back safely");
        System.out.println("PASS: three-protocol session consent, teardown gating, cancellation and restore");
    }
}
