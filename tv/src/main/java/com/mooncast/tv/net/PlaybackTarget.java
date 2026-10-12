package com.mooncast.tv.net;

/** Player boundary. Mutations arrive on receiver workers; implementations must marshal to their player thread.
 * snapshot() must return promptly and be safe from any thread. A mutation should update its observable state
 * before returning, or enqueue mutations in order. Throw IllegalArgumentException to reject unsupported media. */
public interface PlaybackTarget {
    void setMedia(String uri, String metadata);
    void play();
    void pause();
    void stop();
    void seekTo(long positionMillis);
    void setVolume(int percent);
    void setMuted(boolean muted);
    Snapshot snapshot();

    final class Snapshot {
        public final String state, uri, metadata;
        public final long durationMillis, positionMillis;
        public final int volume;
        public final boolean muted, error;
        public Snapshot(String state, String uri, String metadata, long durationMillis,
                        long positionMillis, int volume, boolean muted, boolean error) {
            switch (state) {
                case "NO_MEDIA_PRESENT": case "STOPPED": case "TRANSITIONING":
                case "PLAYING": case "PAUSED_PLAYBACK": break;
                default: throw new IllegalArgumentException("Invalid transport state");
            }
            this.state = state;
            this.uri = uri == null ? "" : uri;
            this.metadata = metadata == null ? "" : metadata;
            this.durationMillis = Math.max(0, durationMillis);
            this.positionMillis = Math.max(0, positionMillis);
            this.volume = Math.max(0, Math.min(100, volume));
            this.muted = muted;
            this.error = error;
        }
        public static Snapshot empty() {
            return new Snapshot("NO_MEDIA_PRESENT", "", "", 0, 0, 100, false, false);
        }
    }
}
