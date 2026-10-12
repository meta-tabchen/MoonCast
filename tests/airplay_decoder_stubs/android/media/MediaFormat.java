/* SPDX-License-Identifier: GPL-3.0-only */
package android.media;

import java.util.HashMap;
import java.util.Map;

public final class MediaFormat {
    public static final String MIMETYPE_VIDEO_AVC = "video/avc", MIMETYPE_VIDEO_HEVC = "video/hevc";
    public static final String KEY_WIDTH = "width", KEY_HEIGHT = "height", KEY_FRAME_RATE = "frame-rate",
            KEY_MAX_INPUT_SIZE = "max-input-size", KEY_LOW_LATENCY = "low-latency";
    private final Map<String, Integer> integers = new HashMap<>();
    public static MediaFormat createVideoFormat(String mime, int width, int height) {
        MediaFormat result = new MediaFormat();
        result.setInteger(KEY_WIDTH, width); result.setInteger(KEY_HEIGHT, height);
        return result;
    }
    public void setInteger(String key, int value) { integers.put(key, value); }
    public int getInteger(String key) { return integers.get(key); }
    public boolean containsKey(String key) { return integers.containsKey(key); }
}
