package io.github.jqssun.displaymirror.sunshine;
import com.mooncast.host.InputBridge;
/** Stable native ABI; disabled control is dropped by the session receiver. */
public final class SunshineMouse {
    public static void handleTouchPacket(long s,int t,int r,int p,float x,float y,float pressure,float major,float minor){InputBridge.Receiver receiver=InputBridge.receiver;if(receiver!=null)receiver.touch(s,t,p,x,y,pressure);}
    public static void handleAbsMouseMovePacket(long s,float x,float y,float w,float h){InputBridge.Receiver receiver=InputBridge.receiver;if(receiver!=null)receiver.mouse(s,x,y,w,h);}
    public static void handleRelMouseMovePacket(long s,short x,short y){InputBridge.Receiver receiver=InputBridge.receiver;if(receiver!=null)receiver.relative(s,x,y);}
    public static void handleLeftMouseButton(long s,boolean release){InputBridge.Receiver receiver=InputBridge.receiver;if(receiver!=null)receiver.button(s,release);}
}
