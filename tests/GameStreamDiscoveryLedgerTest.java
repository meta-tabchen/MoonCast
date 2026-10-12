// SPDX-License-Identifier: GPL-3.0-or-later
package com.mooncast.tv.gamestream.hosts;

/** Deterministic cancellation/restart, boundedness, and late-callback tests. No networking. */
public final class GameStreamDiscoveryLedgerTest {
    private static int assertions;
    private static final DiscoveredHost HOST = new DiscoveredHost(HostEndpoint.parse("192.168.1.20"), "Host");
    public static void main(String[] args) {
        DiscoveryLedger<String> ledger = new DiscoveryLedger<>();
        long first = ledger.begin();
        check(ledger.found(first, "one", "one"), "Queue first service");
        check(!ledger.found(first, "one", "duplicate"), "Deduplicate service");
        DiscoveryLedger.Entry<String> active = ledger.next();
        check(active != null && active.service.equals("one"), "Start first resolve");
        check(ledger.found(first, "two", "two"), "Queue second service");
        check(ledger.next() == null, "Never overlap resolves");
        check(ledger.complete(active, HOST), "Accept current result");
        check(ledger.snapshot().size() == 1, "Result visible");
        DiscoveryLedger.Entry<String> second = ledger.next();
        check(second != null && second.service.equals("two"), "Resolve sequentially");
        check(ledger.complete(second, HOST), "Accept duplicate endpoint hint");
        check(ledger.snapshot().size() == 1, "Deduplicate endpoints across names");
        check(ledger.lost(first, "one"), "Remove one advertisement");
        check(ledger.snapshot().size() == 1, "Other advertisement retains endpoint");
        check(ledger.lost(first, "two") && ledger.snapshot().isEmpty(), "Remove last advertisement");
        check(ledger.found(first, "lost", "lost"), "Queue lost candidate");
        DiscoveryLedger.Entry<String> lost = ledger.next();
        ledger.lost(first, "lost");
        check(!ledger.complete(lost, HOST) && ledger.snapshot().isEmpty(), "Late resolve cannot resurrect lost service");
        ledger.found(first, "old", "old");
        DiscoveryLedger.Entry<String> stale = ledger.next();
        ledger.stop();
        check(!ledger.current(first) && ledger.snapshot().isEmpty(), "Stop invalidates generation");
        check(!ledger.found(first, "late", "late"), "Ignore late discovery callback");
        long restart = ledger.begin();
        ledger.found(restart, "new", "new");
        check(ledger.next() == null, "Old API26 resolve keeps active slot across restart");
        check(!ledger.complete(stale, HOST), "Ignore stale resolve result");
        DiscoveryLedger.Entry<String> fresh = ledger.next();
        check(fresh != null && fresh.service.equals("new"), "Resume queue after stale operation finishes");
        check(!ledger.complete(stale, HOST), "Duplicate callback cannot complete new resolve");
        check(ledger.complete(fresh, HOST) && ledger.snapshot().size() == 1, "Fresh generation can publish result");
        long bound = ledger.begin();
        for (int i = 0; i < DiscoveryLedger.MAX_SERVICES; i++) check(ledger.found(bound, "host-" + i, "service"), "Accept bounded service");
        check(!ledger.found(bound, "overflow", "service"), "Bound service memory");
        check(!ledger.found(bound, "x".repeat(256), "service"), "Bound service key");
        check(!ledger.found(bound, null, "service"), "Reject missing key");
        ledger.stop();
        check(ledger.next() == null, "Do not resolve while stopped");
        long attempts = ledger.begin();
        for (int i = 0; i < DiscoveryLedger.MAX_RESOLVE_ATTEMPTS; i++) {
            check(ledger.found(attempts, "cycling", "service"), "Accept within resolve attempt bound");
            DiscoveryLedger.Entry<String> entry = ledger.next();
            check(entry != null, "Bounded resolve issued");
            ledger.complete(entry, null);
            ledger.lost(attempts, "cycling");
        }
        check(!ledger.found(attempts, "cycling", "service") && ledger.next() == null, "Service churn cannot create unlimited resolves");
        System.out.println("GameStream discovery lifecycle: " + assertions + " assertions passed (pure state fixtures, no device/network).");
    }
    private static void check(boolean ok, String label) { assertions++; if (!ok) throw new AssertionError(label); }
}
