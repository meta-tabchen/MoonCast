// SPDX-License-Identifier: GPL-3.0-or-later
package com.mooncast.tv.gamestream.hosts;
import android.app.Activity;
import com.mooncast.tv.net.LanAccess;
import java.util.List;
public final class HostDiscovery {
    public interface Listener {
        void onHostsChanged(List<DiscoveredHost> hosts); void onError(String code); void onDiscoveryStateChanged(boolean scanning);
    }
    public HostDiscovery(Activity activity, LanAccess lan, Listener listener) { }
    public void start() { }
    public void stop() { }
}
