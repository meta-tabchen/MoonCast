package com.mooncast.host;

/** Layout policy using actual measured text widths, without scaling or truncating labels. */
final class ProtocolTabLayout {
    record Plan(boolean stacked, int contentWidth) {}

    static Plan plan(int viewportWidth, int[] textWidths, int icon, int gap,
                     int tabInset, int groupInset, int margin) {
        if (viewportWidth < 1 || icon < 0 || gap < 0 || tabInset < 0 || groupInset < 0 || margin < 0)
            throw new IllegalArgumentException("Invalid tab layout dimensions");
        int longest = 0;
        for (int width : textWidths) {
            if (width < 0) throw new IllegalArgumentException("Invalid text width");
            longest = Math.max(longest, width);
        }
        int count = textWidths.length;
        if (count == 0) return new Plan(false, viewportWidth);
        int extras = groupInset * 2 + margin * (count - 1);
        int horizontal = extras + count * (longest + icon + gap + tabInset * 2);
        if (horizontal <= viewportWidth) return new Plan(false, viewportWidth);
        int stacked = extras + count * (longest + tabInset * 2);
        return new Plan(true, Math.max(viewportWidth, stacked));
    }
}
