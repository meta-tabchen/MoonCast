// SPDX-License-Identifier: GPL-3.0-or-later
package com.mooncast.tv.gamestream.hosts;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Main-thread state, independently testable without NSD, sockets, or Android. */
final class DiscoveryLedger<T> {
    static final int MAX_SERVICES = 32;
    static final int MAX_RESOLVE_ATTEMPTS = 64;
    static final class Entry<T> {
        final String key;
        final T service;
        final long generation;
        DiscoveredHost result;
        Entry(String key, T service, long generation) { this.key = key; this.service = service; this.generation = generation; }
    }
    private final Map<String, Entry<T>> entries = new LinkedHashMap<>();
    private final ArrayDeque<Entry<T>> waiting = new ArrayDeque<>();
    private Entry<T> active;
    private long generation;
    private boolean running;
    private int attempts;

    long begin() {
        stop();
        running = true;
        attempts = 0;
        return generation;
    }
    void stop() {
        running = false;
        generation++;
        entries.clear();
        waiting.clear();
        // Before API 34 NSD cannot cancel resolution. Keep the active slot occupied
        // across stop/restart until its callback, avoiding overlapping resolve calls.
    }
    boolean current(long generation) { return running && generation == this.generation; }
    boolean found(long generation, String key, T service) {
        if (!current(generation) || key == null || key.length() > 255 || entries.containsKey(key)
                || entries.size() >= MAX_SERVICES || attempts >= MAX_RESOLVE_ATTEMPTS) return false;
        Entry<T> entry = new Entry<>(key, service, generation);
        entries.put(key, entry);
        waiting.addLast(entry);
        return true;
    }
    boolean lost(long generation, String key) {
        if (!current(generation)) return false;
        Entry<T> entry = entries.remove(key);
        if (entry == null) return false;
        waiting.remove(entry);
        return entry.result != null;
    }
    Entry<T> next() {
        if (!running || active != null || attempts >= MAX_RESOLVE_ATTEMPTS) return null;
        active = waiting.pollFirst();
        if (active != null) attempts++;
        return active;
    }
    boolean complete(Entry<T> entry, DiscoveredHost result) {
        if (active != entry) return false;
        active = null;
        if (!current(entry.generation) || entries.get(entry.key) != entry) return false;
        entry.result = result;
        return result != null;
    }
    List<DiscoveredHost> snapshot() {
        Map<HostEndpoint, DiscoveredHost> unique = new LinkedHashMap<>();
        for (Entry<T> entry : entries.values()) if (entry.result != null) unique.putIfAbsent(entry.result.endpoint, entry.result);
        List<DiscoveredHost> result = new ArrayList<>(unique.values());
        result.sort((left, right) -> left.displayName.compareToIgnoreCase(right.displayName));
        return Collections.unmodifiableList(result);
    }
}
