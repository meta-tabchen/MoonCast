// SPDX-License-Identifier: GPL-3.0-or-later
package android.media;
import android.os.Handler;
public final class AudioFocusRequest {
    public AudioManager.OnAudioFocusChangeListener listener;
    public Handler handler;
    public static final class Builder {
        private final AudioFocusRequest request = new AudioFocusRequest();
        public Builder(int gain) { }
        public Builder setAudioAttributes(AudioAttributes attrs) { return this; }
        public Builder setAcceptsDelayedFocusGain(boolean value) { return this; }
        public Builder setWillPauseWhenDucked(boolean value) { return this; }
        public Builder setOnAudioFocusChangeListener(AudioManager.OnAudioFocusChangeListener listener, Handler handler) {
            request.listener = listener; request.handler = handler; return this;
        }
        public AudioFocusRequest build() { return request; }
    }
}
