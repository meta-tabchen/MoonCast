// SPDX-License-Identifier: GPL-3.0-or-later
package android.media;
public final class AudioAttributes {
    public static final int USAGE_MEDIA = 1, CONTENT_TYPE_MOVIE = 3;
    public static final class Builder {
        public Builder setUsage(int value) { return this; }
        public Builder setContentType(int value) { return this; }
        public AudioAttributes build() { return new AudioAttributes(); }
    }
}
