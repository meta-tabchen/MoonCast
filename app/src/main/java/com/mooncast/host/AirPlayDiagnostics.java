package com.mooncast.host;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Structural diagnostics only: never forward receiver bodies, identities or keys. */
final class AirPlayDiagnostics {
    private static final Pattern STATUS = Pattern.compile("\\bHTTP ([1-5][0-9]{2})\\b");

    static String failure(String message, String previousStage) {
        String text = message == null ? "" : message;
        // A status body is arbitrary receiver-controlled data. Do not inspect it,
        // including for stage/status words that could spoof the actual failure.
        int body = text.indexOf(" (body:");
        if (body >= 0) text = text.substring(0, body);
        if (text.length() > 512) text = text.substring(0, 512);
        String stage = stage(text, previousStage);
        Matcher status = STATUS.matcher(text);
        String reason;
        if (status.find()) reason = "status=" + status.group(1);
        else if (text.contains("decode info plist") || text.contains("unmarshal") || text.contains("no server_port")) reason = "invalid-response";
        else if (text.contains("connection refused")) reason = "connection-refused";
        else if (text.contains("timeout") || text.contains("deadline")) reason = "timeout";
        else if (text.contains("EOF") || text.contains("reset by peer")) reason = "connection-closed";
        else reason = "protocol-error";
        return "stage=" + stage + "; " + reason + "; RTSP/1.0";
    }

    private static String stage(String text, String fallback) {
        if (text.startsWith("getinfo")) return "GET/info";
        if (text.startsWith("connect:") || text.startsWith("reconnect:")) return "CONNECT";
        if (text.startsWith("pairing failed:")) return "PAIR";
        if (text.startsWith("fairplay setup:")) return "POST/fp-setup";
        if (text.startsWith("setup_mirror:")) {
            if (text.contains("SETUP phase 0")) return "SETUP/session";
            if (text.contains("SETUP phase 1")) return "SETUP/audio";
            if (text.contains("SETUP phase 2")) return "SETUP/video";
            if (text.contains("airplay1 SETUP /video")) return "SETUP/legacy-video";
            if (text.contains("RECORD")) return "RECORD";
            return "SETUP";
        }
        return safeStage(fallback);
    }

    static String safeStage(String stage) {
        return stage != null && stage.matches("CONNECT|GET/info|PAIR|POST/fp-setup|SETUP(?:/session|/audio|/video|/legacy-video)?|RECORD|STREAM") ? stage : "CONNECT";
    }

    /** Only recognize fixed wrapper messages; full native debug logging stays off. */
    static String progress(String message) {
        if (message == null) return null;
        if (message.startsWith("[AIRPLAY2] connected to:")) return "PAIR";
        if (message.equals("[AIRPLAY2] pairing complete")) return "POST/fp-setup";
        if (message.equals("[AIRPLAY2] FairPlay setup complete") || message.startsWith("[AIRPLAY2] FairPlay SAP unsupported")) return "SETUP";
        if (message.startsWith("[AIRPLAY] mirror session ready, data port=")) return "STREAM";
        return null;
    }
}
