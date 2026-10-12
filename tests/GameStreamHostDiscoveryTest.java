// SPDX-License-Identifier: GPL-3.0-or-later
package com.mooncast.tv.gamestream.hosts;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.os.Handler;
import com.mooncast.tv.net.LanAccess;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/** Real discovery wrapper with deterministic fake Android NSD/clock. No network or device. */
public final class GameStreamHostDiscoveryTest {
    private static int assertions;
    private static final class Results implements HostDiscovery.Listener {
        List<DiscoveredHost> hosts = List.of();
        final List<Boolean> states = new ArrayList<>();
        final List<String> errors = new ArrayList<>();
        Runnable onHosts;
        @Override public void onHostsChanged(List<DiscoveredHost> value) { hosts = value; if (onHosts != null) onHosts.run(); }
        @Override public void onError(String error) { errors.add(error); }
        @Override public void onDiscoveryStateChanged(boolean state) { states.add(state); }
    }
    private static final class Fixture {
        final NsdManager nsd = new NsdManager();
        final Results results = new Results();
        final HostDiscovery discovery;
        Fixture(int api) throws Exception {
            Handler.reset(); Build.VERSION.SDK_INT = api;
            discovery = new HostDiscovery(new Context(nsd), new LanAccess((Inet4Address)ip("192.168.1.2"), 24), results);
        }
        void found(String name) throws Exception { nsd.browsers.get(nsd.browsers.size() - 1).onServiceFound(service(name, "192.168.1.20")); Handler.flush(); }
        NsdManager.ResolveListener resolver(int index) { return nsd.resolves.get(index); }
    }
    public static void main(String[] args) throws Exception {
        Fixture basic = new Fixture(26);
        basic.discovery.start(); basic.discovery.start();
        check(basic.nsd.browsers.size() == 1 && basic.results.states.equals(List.of(true)), "Idempotent start");
        basic.found("first"); basic.found("second"); basic.found("first");
        check(basic.nsd.resolves.size() == 1, "Sequential and deduplicated resolution");
        basic.resolver(0).onServiceResolved(service("first", "192.168.2.20")); Handler.flush();
        check(basic.results.hosts.isEmpty() && basic.nsd.resolves.size() == 2, "Reject off-subnet result and continue");
        basic.resolver(1).onServiceResolved(service("second", "192.168.1.21")); Handler.flush();
        check(basic.results.hosts.size() == 1 && basic.results.hosts.get(0).endpoint.address.equals("192.168.1.21"), "Publish selected LAN host");
        basic.nsd.browsers.get(0).onServiceLost(service("second", "192.168.1.21")); Handler.flush();
        check(basic.results.hosts.isEmpty(), "Remove lost result");
        basic.discovery.stop(); basic.discovery.stop();
        check(basic.results.states.equals(List.of(true, false)), "Idempotent stop");
        basic.found("late");
        check(basic.nsd.resolves.size() == 2, "Stopped callback cannot resolve");

        Fixture restart = new Fixture(26);
        restart.discovery.start(); restart.found("old"); restart.discovery.stop(); restart.discovery.start(); restart.found("new");
        check(restart.nsd.resolves.size() == 1, "Legacy resolve remains exclusive across restart");
        restart.resolver(0).onServiceResolved(service("old", "192.168.1.22")); Handler.flush();
        check(restart.results.hosts.isEmpty() && restart.nsd.resolves.size() == 2, "Stale result ignored; restart queue resumes");
        restart.resolver(0).onResolveFailed(service("old", "192.168.1.22"), 1); Handler.flush();
        restart.resolver(1).onServiceResolved(service("new", "192.168.1.23")); Handler.flush();
        check(restart.results.hosts.size() == 1 && restart.results.hosts.get(0).displayName.equals("new"), "Duplicate old callback cannot clear current resolver");
        restart.discovery.stop();

        Fixture timeout = new Fixture(26);
        timeout.discovery.start(); timeout.found("slow"); Handler.advanceBy(10000);
        check(timeout.results.states.equals(List.of(true, false)) && timeout.results.errors.size() == 1, "Resolve timeout stops scan with useful error");
        timeout.resolver(0).onServiceResolved(service("slow", "192.168.1.24")); Handler.flush();
        check(timeout.results.hosts.isEmpty(), "Timeout result cannot reappear");

        Fixture scan = new Fixture(26);
        scan.discovery.start(); Handler.advanceBy(30000);
        check(scan.results.states.equals(List.of(true, false)) && scan.results.errors.isEmpty(), "Scan has bounded lifetime");

        Fixture modern = new Fixture(34);
        modern.discovery.start(); modern.found("mixed");
        NsdServiceInfo dual = service("mixed", "192.168.1.25");
        dual.addresses = List.of(InetAddress.getByAddress(new byte[16]), ip("192.168.2.20"), ip("192.168.1.25"));
        modern.resolver(0).onServiceResolved(dual); Handler.flush();
        check(modern.results.hosts.size() == 1, "API34 chooses allowed IPv4 among mixed addresses");
        modern.found("cancel"); modern.discovery.stop();
        check(modern.nsd.cancelled.size() == 1, "API34 cancels pending resolver");
        modern.resolver(1).onResolutionStopped(service("cancel", "192.168.1.20")); Handler.flush();
        check(modern.results.hosts.size() == 1, "Stopping does not publish extra results");

        Fixture bounded = new Fixture(26);
        bounded.discovery.start();
        for (int i = 0; i < 200; i++) bounded.found("bounded-" + i);
        for (int i = 0; i < DiscoveryLedger.MAX_SERVICES; i++) { bounded.resolver(i).onResolveFailed(service("bounded-" + i, "192.168.1.20"), 1); Handler.flush(); }
        check(bounded.nsd.resolves.size() == DiscoveryLedger.MAX_SERVICES, "Bound 200 service advertisements to 32 resolutions");
        bounded.discovery.stop();

        Fixture stoppedDuringStart = new Fixture(26);
        stoppedDuringStart.results.onHosts = stoppedDuringStart.discovery::stop;
        stoppedDuringStart.discovery.start();
        check(stoppedDuringStart.nsd.browsers.isEmpty() && !stoppedDuringStart.results.states.contains(true), "Synchronous UI dismissal cannot start stale scan");

        Fixture unavailable = new Fixture(26);
        unavailable.nsd.throwOnDiscover = true; unavailable.discovery.start();
        check(unavailable.results.errors.size() == 1 && unavailable.results.states.equals(List.of(true, false)), "Discovery exception cleans state");
        check(!unavailable.results.errors.get(0).contains("private"), "Platform exception details not exposed");
        System.out.println("GameStream NSD wrapper: " + assertions + " assertions passed (fake Android NSD/clock only).");
    }
    private static InetAddress ip(String value) throws Exception {
        String[] parts = value.split("\\."); byte[] bytes = new byte[4]; for (int i = 0; i < 4; i++) bytes[i] = (byte)Integer.parseInt(parts[i]);
        return InetAddress.getByAddress(bytes);
    }
    private static NsdServiceInfo service(String name, String address) throws Exception { return new NsdServiceInfo(name, "_nvstream._tcp.", 47989, ip(address)); }
    private static void check(boolean value, String label) { assertions++; if (!value) throw new AssertionError(label); }
}
