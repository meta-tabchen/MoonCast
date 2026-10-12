// SPDX-License-Identifier: GPL-3.0-or-later
package com.mooncast.tv.gamestream.hosts;
import android.content.Context;
import com.mooncast.tv.gamestream.testing.Synthetic;
public final class AndroidPairingIdentity {
    public static AndroidPairingIdentity loadExisting(Context context) {
        Synthetic.workerOnly("loadExisting"); Synthetic.identityLoads.incrementAndGet(); Synthetic.identity.pass();
        return new AndroidPairingIdentity();
    }
    public static AndroidPairingIdentity getOrCreateForUserPairing(Context context) {
        Synthetic.workerOnly("getOrCreateForUserPairing"); Synthetic.identityCreates.incrementAndGet();
        return new AndroidPairingIdentity();
    }
    public String getClientId() { return "synthetic-no-key-identity"; }
}
