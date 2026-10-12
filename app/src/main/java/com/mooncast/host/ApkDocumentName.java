package com.mooncast.host;

import com.mooncast.transfer.ApkTransfer;
import java.io.IOException;

/** Provider display names are hints, not proof of content or safe filesystem paths. */
final class ApkDocumentName {
    private ApkDocumentName() {}
    static String wireName(String displayName) {
        try { ApkTransfer.checkName(displayName); return displayName; }
        catch (IOException ignored) { return "selected.apk"; }
    }
}
