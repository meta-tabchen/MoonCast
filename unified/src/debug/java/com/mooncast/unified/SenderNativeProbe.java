package com.mooncast.unified;
public final class SenderNativeProbe extends NativeProbe {
 protected void load(){System.loadLibrary("sunshine");if(!io.github.jqssun.displaymirror.sunshine.NativeSessions.available())throw new AssertionError("Session bridge unavailable");System.loadLibrary("gojni");}
}
