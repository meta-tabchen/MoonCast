package io.github.jqssun.displaymirror.sunshine;

import android.view.Surface;

/** ABI adapter for the unmodified GPLv3 Mirror/Sunshine native core. */
public final class SunshineServer {
    public interface Listener {
        void pinRequested();
        void createDisplay(long session, int width, int height, int fps, int audioPacketMs, Surface surface, boolean captureAudio);
        void stopDisplay(long session);
        void error(String error);
        void uuid(String uuid);
    }
    public static volatile Listener listener;
    static {
        String nativeDir = System.getProperty("mooncast.nativeDir");
        if (nativeDir == null) System.loadLibrary("sunshine");
        else {
            if (new java.io.File(nativeDir,"libc++_shared.so").isFile()) System.load(nativeDir + "/libc++_shared.so");
            System.load(nativeDir + "/libcrypto.so");
            System.load(nativeDir + "/libssl.so");
            System.load(nativeDir + "/libsunshine.so");
        }
    }
    private SunshineServer() {}
    public static native void start();
    public static native void setSunshineName(String name);
    public static native void setPkeyPath(String path);
    public static native void setCertPath(String path);
    public static native void setFileStatePath(String path);
    public static native void submitPin(String pin);
    public static native void setHevcSupported(boolean supported);
    public static native void startAudioRecording(Object recorder, int framesPerPacket);
    public static native boolean exitServer();
    public static void onPinRequested() { if (listener != null) listener.pinRequested(); }
    public static void createVirtualDisplay(long s, int w, int h, int f, int p, Surface surface, boolean audio) {
        NativeSessions.created(s);
        if (listener != null) listener.createDisplay(s,w,h,f,p,surface,audio);
    }
    public static void stopVirtualDisplay(long s) { NativeSessions.ended(s);if (listener != null) listener.stopDisplay(s); }
    public static void showEncoderError(String error) { if (listener != null) listener.error(error); }
    public static void onMirrorClientDiscovered(String ignored) {}
    public static void setMirrorServerUuid(String uuid) { if (listener != null) listener.uuid(uuid); }
}
