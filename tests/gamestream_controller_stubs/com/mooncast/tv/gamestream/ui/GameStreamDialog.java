// SPDX-License-Identifier: GPL-3.0-or-later
package com.mooncast.tv.gamestream.ui;
import android.app.Activity;
import com.mooncast.tv.gamestream.hosts.*;
import com.mooncast.tv.gamestream.testing.Synthetic;
import java.util.List;
public final class GameStreamDialog {
    public enum Quality { FULL_HD_1080_60 }
    public interface Listener {
        void onHostSelected(HostEndpoint endpoint, String name, HostRecord record);
        void onPairConfirmed(HostEndpoint endpoint, String name);
        void onForgetHost(HostRecord record); void onQualitySelected(Quality quality);
        void onRefreshRequested(); void onDismissed();
    }
    public final Listener listener;
    public int updates, errors, confirmations, shownPins;
    public boolean busy;
    public List<HostRecord> saved = List.of();
    public GameStreamDialog(Activity activity, Listener listener) { this.listener = listener; Synthetic.dialog = this; }
    public void confirmPairing(HostEndpoint endpoint, String name) { confirmations++; }
    public void setQuality(Quality quality) { }
    public void setBusy(boolean busy) { this.busy = busy; }
    public void show() { }
    public void dismiss() { listener.onDismissed(); }
    public void setDiscoveryStatus(String status) { }
    public void updateHosts(List<HostRecord> saved, List<DiscoveredHost> discovered) { updates++; this.saved = List.copyOf(saved); }
    public void showError(String error) { errors++; }
    public void showPairingPin(String name, String pin, Runnable cancel) { shownPins++; }
    public void dismissPairing() { }
}
