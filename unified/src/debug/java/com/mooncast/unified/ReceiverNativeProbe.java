package com.mooncast.unified;
public final class ReceiverNativeProbe extends NativeProbe {
 protected void load(){System.loadLibrary("airplay_native");io.github.jqssun.airplay.bridge.NativeBridge.INSTANCE.nativeSetDefaultStreamValues(48000,192);System.loadLibrary("moonlight-core");}
}
