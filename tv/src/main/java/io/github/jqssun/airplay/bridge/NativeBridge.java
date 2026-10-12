/* SPDX-License-Identifier: GPL-3.0-only
 * JNI declarations adapted from jqssun/android-airplay-server v0.0.31.
 * Keep package/method names stable: they are the upstream native ABI.
 */
package io.github.jqssun.airplay.bridge;

import java.util.Map;

public final class NativeBridge {
    static { System.loadLibrary("airplay_native"); }
    public static final NativeBridge INSTANCE = new NativeBridge();
    private NativeBridge() {}
    public native long nativeInit(RaopCallbackHandler callback, byte[] hwAddr, String name,
            String keyFile, boolean nohold, boolean requirePin);
    public native int nativeStart(long handle, int port);
    public native void nativeStop(long handle);
    public native void nativeDestroy(long handle);
    public native void nativeSetDisplaySize(long handle, int w, int h, int fps);
    public native void nativeSetPlist(long handle, String key, int value);
    public native void nativeSetH265Enabled(long handle, boolean enabled);
    public native void nativeSetCodecs(long handle, boolean alac, boolean aac);
    public native void nativeSetHlsEnabled(long handle, boolean enabled);
    public native void nativeSetLang(long handle, String requested, String subtitles, String system);
    public native void nativeSetAudioEnabled(long handle, boolean enabled);
    public native void nativeUpdatePlaybackInfo(long handle, float position, float duration, float rate, boolean ready);
    public native Map<String, String> nativeGetRaopTxtRecords(long handle);
    public native Map<String, String> nativeGetAirplayTxtRecords(long handle);
    public native String nativeGetRaopServiceName(long handle);
    public native String nativeGetServerName(long handle);
    public native void nativeSetDefaultStreamValues(int sampleRate, int framesPerBurst);
    public native boolean nativeServerAudioConfigure(long handle, int cushionMs, int percentile,
            int bufferFrames, boolean softwareAlac, boolean realtime, boolean lowLatency, boolean benchmark);
    public native boolean nativeServerAudioStart(long handle);
    public native void nativeServerAudioStop(long handle);
    public native void nativeServerAudioFormat(long handle, int ct, int spf);
    public native boolean nativeServerAudioDebug(long handle, java.nio.ByteBuffer buffer);
}
