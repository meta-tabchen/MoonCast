/* SPDX-License-Identifier: GPL-3.0-only */
package com.limelight.nvstream.av.video;
/** Shape-only test ABI, checked separately against actual v12.2 upstream sources. */
public abstract class VideoDecoderRenderer {
    public abstract int setup(int format,int width,int height,int redrawRate);
    public abstract void start();public abstract void stop();public abstract void cleanup();public abstract int getCapabilities();
    public abstract void setHdrMode(boolean enabled,byte[] metadata);
    public abstract int submitDecodeUnit(byte[] data,int length,int type,int frame,int frameType,char hostLatency,long received,long enqueued);
}
