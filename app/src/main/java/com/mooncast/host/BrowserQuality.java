package com.mooncast.host;

/** Immutable limits for one browser sharing lifetime; never upscales the source. */
final class BrowserQuality {
    static final BrowserQuality DEFAULT = new BrowserQuality(1280, 30, 8_000_000);
    final int longEdge, fps, maxBitrateBps;

    private BrowserQuality(int longEdge, int fps, int maxBitrateBps) {
        this.longEdge = longEdge;
        this.fps = fps;
        this.maxBitrateBps = maxBitrateBps;
    }

    static BrowserQuality fromPreset(int preset) {
        switch (preset) {
            case 0: return of(720, 24, 2_000_000);
            case 1: return DEFAULT;
            case 2: return of(1920, 30, 12_000_000);
            default: throw new IllegalArgumentException("Unknown browser quality preset");
        }
    }

    static BrowserQuality of(int longEdge, int fps, int maxBitrateBps) {
        if (longEdge < 640 || longEdge > 3840 || (longEdge & 1) != 0
                || fps < 15 || fps > 60
                || maxBitrateBps < 1_000_000 || maxBitrateBps > 40_000_000)
            throw new IllegalArgumentException("Browser quality outside supported limits");
        return new BrowserQuality(longEdge, fps, maxBitrateBps);
    }

    int initialBitrateBps() { return Math.min(2_000_000, maxBitrateBps); }

    /** Even codec dimensions, same policy for initial capture and later resize/rotation. */
    int[] dimensions(int sourceWidth, int sourceHeight) {
        if (sourceWidth < 1 || sourceHeight < 1)
            throw new IllegalArgumentException("Invalid browser source size");
        double scale = Math.min(1d, (double) longEdge / Math.max(sourceWidth, sourceHeight));
        return new int[] {Math.max(2, ((int) (sourceWidth * scale)) & ~1),
                Math.max(2, ((int) (sourceHeight * scale)) & ~1)};
    }
}
