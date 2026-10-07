package com.mooncast.host;

/** JNI callbacks enter here; only a configured, active stream has a receiver. */
public final class InputBridge {
    public interface Receiver {
        void touch(long session,int type,int pointer,float x,float y,float pressure);
        void mouse(long session,float x,float y,float width,float height);
        void relative(long session,int x,int y);
        void button(long session,boolean release);
        void key(int key,boolean release,int flags);
    }
    public static volatile Receiver receiver;
    private InputBridge(){}
}
