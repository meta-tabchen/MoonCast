package com.limelight.nvstream.jni;

/** Test-only JNI callback adapter for the official Moonlight Android 12.2 core. */
public final class MoonBridge {
    public interface Observer {
        int setup(int format,int width,int height,int fps);
        int frame(byte[] bytes,int length,int type);
        void started();void failed(int stage,int code);void terminated(int code);
    }
    public static volatile Observer observer;
    static{System.loadLibrary("moonlight-core");init();}
    public static int bridgeDrSetup(int f,int w,int h,int r){return observer.setup(f,w,h,r);}
    public static void bridgeDrStart(){}public static void bridgeDrStop(){}public static void bridgeDrCleanup(){}
    public static int bridgeDrSubmitDecodeUnit(byte[] b,int l,int t,int n,int f,char latency,long received,long enqueued){return observer.frame(b,l,t);}
    public static int bridgeArInit(int c,int r,int s){return 0;}
    public static void bridgeArStart(){}public static void bridgeArStop(){}public static void bridgeArCleanup(){}public static void bridgeArPlaySample(short[] samples){}
    public static void bridgeClStageStarting(int stage){}public static void bridgeClStageComplete(int stage){}
    public static void bridgeClStageFailed(int stage,int code){observer.failed(stage,code);}
    public static void bridgeClConnectionStarted(){observer.started();}
    public static void bridgeClConnectionTerminated(int code){observer.terminated(code);}
    public static void bridgeClRumble(short n,short l,short h){}public static void bridgeClConnectionStatusUpdate(int status){}
    public static void bridgeClSetHdrMode(boolean e,byte[] m){}public static void bridgeClRumbleTriggers(short n,short l,short r){}
    public static void bridgeClSetMotionEventState(short n,byte t,short h){}public static void bridgeClSetControllerLED(short n,byte r,byte g,byte b){}
    public static native int startConnection(String address,String appVersion,String gfeVersion,String rtspUrl,int codecs,int width,int height,int fps,int bitrate,int packetSize,int remotely,int audioConfiguration,int formats,int refresh,byte[] key,byte[] iv,int capabilities,int colorSpace,int colorRange);
    public static native void interruptConnection();public static native void stopConnection();public static native String getLaunchUrlQueryParameters();private static native void init();
}
