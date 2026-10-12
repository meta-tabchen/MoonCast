/* SPDX-License-Identifier: GPL-3.0-only */
package com.limelight.nvstream.jni;
/** Test ABI facade only: production compiles against the pinned upstream MoonBridge. */
public final class MoonBridge {
    public static final int VIDEO_FORMAT_H264=1, DR_OK=0, DR_NEED_IDR=-1;
    public static final class AudioConfiguration {
        public final int channelCount,channelMask;
        public AudioConfiguration(int count,int mask){channelCount=count;channelMask=mask;}
    }
}
