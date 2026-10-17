package com.mooncast.host;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** Last structural report survives host process death; app-private, never uploaded. */
final class AirPlayDiagnosticStore {
    private static final int MAX_BYTES = 24576;
    static void save(File directory, String report) {
        byte[] bytes = report.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) return;
        File pending = new File(directory,"airplay-diagnostic.tmp");
        try {
            Files.write(pending.toPath(),bytes);
            Files.move(pending.toPath(),new File(directory,"airplay-diagnostic").toPath(),
                StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException ignored) {}
    }
    static String read(File directory) {
        File file = new File(directory,"airplay-diagnostic");
        try {
            if (Files.size(file.toPath()) > MAX_BYTES) return "";
            return new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException ignored) { return ""; }
    }
}
