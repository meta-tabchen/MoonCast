package com.mooncast.host;

public final class BrowserQualityTest {
    static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        require(BrowserQuality.fromPreset(1) == BrowserQuality.DEFAULT, "Keep verified default");
        BrowserQuality q = BrowserQuality.DEFAULT;
        int[] landscape = q.dimensions(3200, 2136), portrait = q.dimensions(2136, 3200);
        require(landscape[0] == 1280 && landscape[1] == 854, "Verified tablet size");
        require(portrait[0] == 854 && portrait[1] == 1280, "Rotation preserves profile");
        for (int preset = 0; preset < 3; preset++) {
            q = BrowserQuality.fromPreset(preset);
            int[] d = q.dimensions(3200, 2136), r = q.dimensions(2136, 3200);
            require(Math.max(d[0], d[1]) == q.longEdge, "Long edge cap");
            require(d[0] == r[1] && d[1] == r[0], "Resize uses selected preset");
            require((d[0] & 1) == 0 && (d[1] & 1) == 0, "Codec alignment");
            require(q.initialBitrateBps() <= q.maxBitrateBps, "Initial bitrate bounded");
        }
        int[] small = BrowserQuality.fromPreset(2).dimensions(640, 360);
        require(small[0] == 640 && small[1] == 360, "Do not invent source pixels");
        require(BrowserQuality.of(640, 15, 1_000_000).initialBitrateBps() == 1_000_000,
                "Low budget cannot start above limit");
        int[] high = BrowserQuality.of(3840, 15, 24_000_000).dimensions(4096, 2160);
        require(high[0] == 3840 && high[1] == 2024, "Higher detail, lower temporal rate");
        int[][] bad = {{639,30,8_000_000},{3842,30,8_000_000},{721,24,2_000_000},
            {1280,14,8_000_000},{1280,61,8_000_000},{1280,30,999_999},{1280,30,40_000_001}};
        for (int[] b : bad) {
            try { BrowserQuality.of(b[0],b[1],b[2]); throw new AssertionError("Bad input accepted"); }
            catch (IllegalArgumentException expected) { }
        }
        try { q.dimensions(0, 100); throw new AssertionError("Bad source accepted"); }
        catch (IllegalArgumentException expected) { }
        System.out.println("BrowserQualityTest PASS: default, presets, rotation, no upscale, budgets and invalid limits");
    }
}
