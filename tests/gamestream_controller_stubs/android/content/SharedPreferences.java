// SPDX-License-Identifier: GPL-3.0-or-later
package android.content;
/** Only the API exercised by the real GameStreamHosts. */
public interface SharedPreferences {
    String getString(String key, String fallback);
    Editor edit();
    interface Editor { Editor putString(String key, String value); void apply(); }
}
