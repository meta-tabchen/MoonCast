package com.mooncast.tv.apk;

/** OEM Settings may return an Activity result before leaving, or omit it entirely. */
public final class ApkSettingsReturn {
    private boolean awaiting, away;
    public void begin() { awaiting = true; away = false; }
    public void onPause() { if (awaiting) away = true; }
    public boolean onResume() {
        if (!awaiting || !away) return false;
        reset(); return true;
    }
    public boolean awaiting() { return awaiting; }
    public void reset() { awaiting = false; away = false; }
}
