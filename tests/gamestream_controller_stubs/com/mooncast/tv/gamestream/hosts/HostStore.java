// SPDX-License-Identifier: GPL-3.0-or-later
package com.mooncast.tv.gamestream.hosts;
import android.content.Context;
import com.mooncast.tv.gamestream.testing.Synthetic;
import java.util.List;
public final class HostStore {
    public HostStore(Context context) { }
    public List<HostRecord> load() {
        Synthetic.workerOnly("load"); Synthetic.loads.incrementAndGet(); Synthetic.storeLoad.pass();
        if (Synthetic.storeLoadFailure != null) throw Synthetic.storeLoadFailure;
        return Synthetic.savedHosts;
    }
    public void save(HostRecord record) {
        Synthetic.workerOnly("save"); Synthetic.save.pass(); Synthetic.saves.incrementAndGet();
    }
    public void forget(HostEndpoint endpoint) { Synthetic.workerOnly("forget"); Synthetic.forgets.incrementAndGet(); }
}
