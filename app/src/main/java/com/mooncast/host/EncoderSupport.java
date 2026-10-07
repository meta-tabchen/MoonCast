package com.mooncast.host;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;
import java.util.ArrayList;
import java.util.List;

final class EncoderSupport {
    static List<String> hardware(String mime) {
        List<String> names = new ArrayList<>();
        for (MediaCodecInfo codec : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
            if (!codec.isEncoder()) continue;
            boolean hw = Build.VERSION.SDK_INT >= 29 ? codec.isHardwareAccelerated() :
                !(codec.getName().startsWith("OMX.google.") || codec.getName().startsWith("c2.android."));
            if (!hw) continue;
            for (String type : codec.getSupportedTypes()) {
                if (mime.equalsIgnoreCase(type)) {
                    int[] formats = codec.getCapabilitiesForType(type).colorFormats;
                    for (int f : formats) if (f == MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface) {
                        names.add(codec.getName()); break;
                    }
                }
            }
        }
        return names;
    }
    static String describe() {
        return "H.264: " + String.join(", ", hardware("video/avc"))
            + "\nHEVC: " + String.join(", ", hardware("video/hevc"));
    }
}
