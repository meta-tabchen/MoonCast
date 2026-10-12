/* SPDX-License-Identifier: GPL-3.0-only */
package android.media;
public final class MediaCodecList {
    public static final int REGULAR_CODECS=0;
    public MediaCodecList(int kind){}
    public MediaCodecInfo[] getCodecInfos(){return new MediaCodecInfo[]{new MediaCodecInfo()};}
}
