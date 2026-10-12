// SPDX-License-Identifier: GPL-3.0-or-later
package com.mooncast.receiver;

import com.limelight.nvstream.http.NvApp;
import java.util.List;

/** No fixed app ID and no fallback to launching an unrelated host application. */
public final class DesktopSelection {
    private DesktopSelection() {}

    public static NvApp requireDesktop(List<NvApp> apps) {
        NvApp desktop = null;
        for (NvApp app : apps) {
            if (app != null && "Desktop".equalsIgnoreCase(app.getAppName())) {
                if (!app.isInitialized() || app.getAppId() <= 0) {
                    throw new IllegalArgumentException("Desktop has no valid host-provided app ID");
                }
                if (desktop != null) {
                    throw new IllegalArgumentException("Host advertises more than one Desktop application");
                }
                desktop = app;
            }
        }
        if (desktop == null) throw new IllegalArgumentException("Host does not advertise Desktop");
        return desktop;
    }

    public static void validatePin(String pin) {
        if (pin == null || !pin.matches("[0-9]{4}")) {
            throw new IllegalArgumentException("Pairing PIN must contain exactly four ASCII digits");
        }
    }
}
