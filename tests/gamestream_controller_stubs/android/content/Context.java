// SPDX-License-Identifier: GPL-3.0-or-later
package android.content;
import android.media.AudioManager;
public class Context {
    public static final String AUDIO_SERVICE = "audio";
    public final AudioManager audioManager = new AudioManager();
    public Context getApplicationContext() { return this; }
    public Object getSystemService(String name) { return AUDIO_SERVICE.equals(name) ? audioManager : null; }
}
