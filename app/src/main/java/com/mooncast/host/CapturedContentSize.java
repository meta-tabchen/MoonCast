package com.mooncast.host;

/** Per-projection content size; retain callbacks that arrive before the encoder. */
final class CapturedContentSize {
    private int width, height;
    boolean update(int w, int h) {
        if (w < 2 || h < 2 || w > 16384 || h > 16384) return false;
        boolean changed = w != width || h != height;
        width = w; height = h; return changed;
    }
    int width(int fallback) { return width > 0 ? width : fallback; }
    int height(int fallback) { return height > 0 ? height : fallback; }
}
