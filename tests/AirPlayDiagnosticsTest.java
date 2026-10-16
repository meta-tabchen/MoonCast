package com.mooncast.host;

public final class AirPlayDiagnosticsTest {
    private static int checks;
    private static void equal(String want, String actual) {
        checks++;
        if (!want.equals(actual)) throw new AssertionError(want + " != " + actual);
    }
    public static void main(String[] args) {
        equal("stage=SETUP/session; status=501; RTSP/1.0", AirPlayDiagnostics.failure(
            "setup_mirror: SETUP phase 0 (session): HTTP 501 (body: PRIVATE password=1234)", "PAIR"));
        equal("stage=GET/info; status=403; RTSP/1.0", AirPlayDiagnostics.failure(
            "getinfo: HTTP 403 (body: SETUP phase 0 HTTP 501 SECRET)", "CONNECT"));
        equal("stage=SETUP/video; status=455; RTSP/1.0", AirPlayDiagnostics.failure(
            "setup_mirror: SETUP phase 2 (video): HTTP 455 (body: abc)", "SETUP"));
        equal("stage=CONNECT; connection-refused; RTSP/1.0", AirPlayDiagnostics.failure(
            "connect: dial tcp 192.168.0.100:7002: connection refused", "GET/info"));
        equal("stage=POST/fp-setup; connection-closed; RTSP/1.0", AirPlayDiagnostics.failure(
            "fairplay setup: EOF", "PAIR"));
        equal("stage=PAIR; timeout; RTSP/1.0", AirPlayDiagnostics.failure("deadline exceeded", "PAIR"));
        equal("stage=CONNECT; protocol-error; RTSP/1.0", AirPlayDiagnostics.failure(null, "SECRET"));
        equal("PAIR", AirPlayDiagnostics.progress("[AIRPLAY2] connected to: PRIVATE (model: PRIVATE)"));
        equal("POST/fp-setup", AirPlayDiagnostics.progress("[AIRPLAY2] pairing complete"));
        equal("SETUP", AirPlayDiagnostics.progress("[AIRPLAY2] FairPlay setup complete"));
        equal("STREAM", AirPlayDiagnostics.progress("[AIRPLAY] mirror session ready, data port=1234"));
        for (String value : new String[]{"secret", "[INFO] key: SECRET", "[HTTP] -> SETUP PRIVATE", "[AIRPLAY2] features SECRET"}) {
            if (AirPlayDiagnostics.progress(value) != null) throw new AssertionError("Unsafe progress forwarded");
            checks++;
        }
        // Retention covers the short-lived host/UI polling race and rejects stale sessions.
        try {
            java.nio.file.Path path = java.nio.file.Files.createTempDirectory("airplay-result-");
            try {
                String error = AirPlayDiagnostics.failure("setup_mirror: SETUP phase 0: HTTP 501 (body: SECRET)", "SETUP");
                HostFailureStore.failure(path.toFile(), 58, "SETUP", error, () -> {}, () -> {});
                equal(error, HostFailureStore.read(path.toFile(), 58).error());
                if (HostFailureStore.read(path.toFile(), 57) != null) throw new AssertionError("stale session");
                checks++;
            } finally {
                try (var files = java.nio.file.Files.list(path)) { for (var f : files.toList()) java.nio.file.Files.delete(f); }
                java.nio.file.Files.delete(path);
            }
        } catch (java.io.IOException failure) { throw new AssertionError(failure); }
        System.out.println("PASS " + checks + " AirPlay diagnostic, privacy and exit-retention checks");
    }
}
