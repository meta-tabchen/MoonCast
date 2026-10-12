package com.mooncast.host;

import android.content.Context;
import android.content.res.Resources;
import org.json.JSONArray;
import org.json.JSONException;

/** Presentation-only messages for helpers that run with a system Context (Root/Shizuku).
 * Transport event kinds and exceptions remain unchanged. Resolve only at the app UI boundary;
 * never try to load app resources through the privileged capture/display Context. */
final class PhoneMessages {
    private static final char MARKER = '\u001e';
    private static final int MAX_MESSAGE = 8192;
    private PhoneMessages() {}

    static String message(int resource, Object... args) {
        JSONArray values = new JSONArray(); values.put(resource);
        for (Object arg : args) values.put(arg == null ? "" : arg);
        String json = values.toString();
        return MARKER + Integer.toString(json.length()) + ":" + json;
    }

    /** Event routing must use an identifier rather than localized display text. */
    static boolean is(String message, int resource) {
        if (message == null || message.isEmpty() || message.charAt(0) != MARKER) return false;
        try {
            int colon = message.indexOf(':', 1);
            if (colon < 2 || colon > 6) return false;
            int length = Integer.parseInt(message.substring(1, colon));
            if (length < 1 || length > MAX_MESSAGE || colon + 1 + length != message.length()) return false;
            return new JSONArray(message.substring(colon + 1)).getInt(0) == resource;
        } catch (JSONException | NumberFormatException ignored) { return false; }
    }

    static boolean hasMessage(String message) { return message != null && message.indexOf(MARKER) >= 0; }

    static String render(Context context, String message) { return render(context, message, 0); }
    private static String render(Context context, String message, int depth) {
        if (message == null) return "";
        if (depth > 6) return context.getString(R.string.phone_diagnostic_unavailable);
        // These two errors originate in the Android-independent volume state machine.
        message = message.replace("系统未允许修改媒体音量", context.getString(R.string.phone_volume_denied))
            .replace("系统未恢复媒体音量，将在下次开始投屏时重试", context.getString(R.string.phone_volume_restore_denied));
        StringBuilder output = new StringBuilder();
        int cursor = 0, count = 0;
        while (cursor < message.length()) {
            int marker = message.indexOf(MARKER, cursor);
            if (marker < 0 || ++count > 16) { output.append(message.substring(cursor)); break; }
            output.append(message, cursor, marker);
            try {
                int colon = message.indexOf(':', marker + 1);
                if (colon < marker + 2 || colon > marker + 6) throw new IllegalArgumentException();
                int length = Integer.parseInt(message.substring(marker + 1, colon));
                if (length < 1 || length > MAX_MESSAGE || length > message.length() - colon - 1)
                    throw new IllegalArgumentException();
                JSONArray values = new JSONArray(message.substring(colon + 1, colon + 1 + length));
                int resource = values.getInt(0);
                // Do not resolve arbitrary resources supplied by external diagnostic text.
                if (!context.getResources().getResourceEntryName(resource).startsWith("phone_"))
                    throw new IllegalArgumentException();
                Object[] args = new Object[values.length() - 1];
                for (int i = 0; i < args.length; i++) {
                    Object value = values.get(i + 1);
                    args[i] = value instanceof String ? render(context, (String) value, depth + 1) : value;
                }
                output.append(context.getString(resource, args));
                cursor = colon + 1 + length;
            } catch (JSONException | IllegalArgumentException | Resources.NotFoundException ignored) {
                output.append(context.getString(R.string.phone_diagnostic_unavailable));
                // Do not expose a broken internal payload in the UI.
                return output.toString();
            }
        }
        return output.toString();
    }

    /** Parser diagnostics are secondary evidence, never a claim to translate server responses. */
    static boolean isApkParserDiagnostic(String message) {
        return message.startsWith("Invalid APK") || message.startsWith("APK ZIP")
            || message.startsWith("APK manifest") || message.startsWith("APK resource")
            || message.startsWith("Truncated APK") || message.startsWith("Missing APK")
            || message.startsWith("ZIP64") || message.startsWith("Multi-disk APK")
            || message.startsWith("File is not an ordinary APK")
            || message.startsWith("Split APKs") || message.startsWith("Choose one ordinary");
    }
}
