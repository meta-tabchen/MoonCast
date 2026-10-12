package com.mooncast.host;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Rebuild from live service facts, so losing one service removes only its capability. */
final class MyTvRegistry {
    private final LinkedHashMap<String, MyTvDevice> services = new LinkedHashMap<>();
    void put(String service, MyTvDevice device) { if (services.size() < 64 || services.containsKey(service)) services.put(service, device); }
    void remove(String service) { services.remove(service); }
    void clear() { services.clear(); }
    List<MyTvDevice> snapshot() {
        ArrayList<MyTvDevice> result = new ArrayList<>();
        for (MyTvDevice fact : services.values()) {
            // A conflicting identity anywhere on this address prevents inference from address alone.
            boolean conflict = false;
            for (MyTvDevice a : services.values())
                for (MyTvDevice b : services.values())
                    if (fact.host.equals(a.host) && a.host.equals(b.host) && !a.canMerge(b)) conflict = true;
            int match = -1;
            for (int i = 0; i < result.size(); i++) {
                MyTvDevice row = result.get(i);
                boolean sameId = !fact.identity.isEmpty() && fact.identity.equals(row.identity)
                    || !fact.airplayIdentity.isEmpty() && fact.airplayIdentity.equals(row.airplayIdentity);
                if (row.canMerge(fact) && (!conflict || sameId)) {
                    if (match >= 0) { match = -1; break; }
                    match = i;
                }
            }
            if (match >= 0) result.set(match, result.get(match).merge(fact)); else result.add(fact);
        }
        return result;
    }
}
