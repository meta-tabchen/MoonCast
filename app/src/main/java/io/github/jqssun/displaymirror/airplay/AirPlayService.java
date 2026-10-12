package io.github.jqssun.displaymirror.airplay;
/** Sunshine JNI ABI callback. The exclusive AirPlay sender lives in AirPlayHostService;
 * GameStream frames are deliberately not piggybacked across protocol sessions. */
public final class AirPlayService { public static void onNativeVideoFrame(byte[] data, boolean keyframe) {} }
