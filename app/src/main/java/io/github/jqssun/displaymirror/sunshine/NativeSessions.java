package io.github.jqssun.displaymirror.sunshine;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

/** Never expose arbitrary native handles to IPC. Guarded by the encoder callbacks. */
public final class NativeSessions {
    private static final Set<Long> live=ConcurrentHashMap.newKeySet();
    private static boolean loaded;
    static {
        try{String dir=System.getProperty("mooncast.nativeDir");if(dir==null)System.loadLibrary("mooncast_sessions");else System.load(dir+"/libmooncast_sessions.so");loaded=true;}
        catch(LinkageError ignored){loaded=false;}
    }
    private NativeSessions(){}
    static synchronized void created(long session){if(session!=0)live.add(session);}
    static synchronized void ended(long session){live.remove(session);}
    public static boolean active(long session){return live.contains(session);}
    public static synchronized boolean available(){return loaded && availableNative();}
    public static boolean packaged(){return loaded;}
    public static synchronized boolean disconnect(long session){
        // stopVirtualDisplay removes the handle before returning to native encoder cleanup.
        // Holding this same monitor prevents join/free until this non-joining stop call returns.
        return live.contains(session) && available() && stopNative(session);
    }
    private static native boolean availableNative();
    private static native boolean stopNative(long session);
}
