/* SPDX-License-Identifier: GPL-3.0-only */
package android.media;
import java.util.HashMap;
public final class MediaFormat {
    public static final String MIMETYPE_VIDEO_AVC="video/avc", KEY_WIDTH="width", KEY_HEIGHT="height",
        KEY_FRAME_RATE="frame-rate", KEY_MAX_INPUT_SIZE="max-input-size", KEY_PRIORITY="priority", KEY_LOW_LATENCY="low-latency";
    private final HashMap<String,Integer> values=new HashMap<>();
    public static MediaFormat createVideoFormat(String mime,int width,int height){MediaFormat f=new MediaFormat();f.setInteger(KEY_WIDTH,width);f.setInteger(KEY_HEIGHT,height);return f;}
    public void setInteger(String key,int value){values.put(key,value);}
    public int getInteger(String key){return values.get(key);}
    public boolean containsKey(String key){return values.containsKey(key);}
}
