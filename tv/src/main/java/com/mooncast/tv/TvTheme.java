package com.mooncast.tv;

import android.content.SharedPreferences;

/** Stable identifiers survive reordering and updates. Themes affect standby artwork only. */
final class TvTheme {
    static final String KEY = "appearance.standby_theme";
    static final String[] IDS = {"moonlake", "aurora", "mist", "midnight"};
    static final int[] LABELS = {R.string.theme_moonlake, R.string.theme_aurora,
            R.string.theme_mist, R.string.theme_midnight};
    private TvTheme() {}
    static int read(SharedPreferences preferences) {
        String saved;
        try { saved = preferences.getString(KEY, IDS[0]); }
        catch (ClassCastException corrupt) { return 0; }
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(saved)) return i;
        return 0;
    }
    static void save(SharedPreferences preferences, int index) {
        if (index < 0 || index >= IDS.length) throw new IllegalArgumentException("Unknown theme");
        preferences.edit().putString(KEY, IDS[index]).apply();
    }
}
