// SPDX-License-Identifier: GPL-3.0-or-later
package android.media;
import android.os.Looper;
public final class AudioManager {
    public static final int AUDIOFOCUS_GAIN = 1, AUDIOFOCUS_LOSS = -1, AUDIOFOCUS_REQUEST_GRANTED = 1;
    public interface OnAudioFocusChangeListener { void onAudioFocusChange(int change); }
    public int result = AUDIOFOCUS_REQUEST_GRANTED, requested, abandoned;
    public AudioFocusRequest current;
    public int requestAudioFocus(AudioFocusRequest request) {
        Looper.requireMain(); requested++; current = request; return result;
    }
    public int abandonAudioFocusRequest(AudioFocusRequest request) {
        Looper.requireMain(); abandoned++; if (current == request) current = null; return 1;
    }
}
