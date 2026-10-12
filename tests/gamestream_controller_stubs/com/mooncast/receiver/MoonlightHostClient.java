// SPDX-License-Identifier: GPL-3.0-or-later
package com.mooncast.receiver;
import android.content.Context;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.http.ComputerDetails;
import com.mooncast.tv.gamestream.hosts.AndroidPairingIdentity;
import com.mooncast.tv.gamestream.testing.Synthetic;
import java.security.cert.X509Certificate;
/** All methods are in-memory fixtures. No HTTP, certificate, PIN, or key I/O. */
public final class MoonlightHostClient implements AutoCloseable {
    public volatile boolean closed;
    public MoonlightHostClient(Context context, AndroidPairingIdentity identity, String id) {
        Synthetic.workerOnly("client construction"); Synthetic.clients.incrementAndGet(); Synthetic.client = this;
    }
    public PreparedDesktop prepareDesktop(ComputerDetails.AddressTuple address, int port, X509Certificate certificate, StreamSpec spec) {
        Synthetic.workerOnly("prepareDesktop"); Synthetic.prepares.incrementAndGet(); Synthetic.prepare.pass();
        return new PreparedDesktop(new NvConnection(), spec, 1, Runnable::run);
    }
    public ComputerDetails inspect(ComputerDetails.AddressTuple address, int port, X509Certificate certificate) {
        Synthetic.workerOnly("inspect"); Synthetic.inspects.incrementAndGet(); return new ComputerDetails();
    }
    public X509Certificate pair(ComputerDetails.AddressTuple address, int port, String pin) {
        Synthetic.workerOnly("pair"); Synthetic.pairs.incrementAndGet(); Synthetic.pair.pass(); return null;
    }
    public void close() { closed = true; Synthetic.closes.incrementAndGet(); }
}
