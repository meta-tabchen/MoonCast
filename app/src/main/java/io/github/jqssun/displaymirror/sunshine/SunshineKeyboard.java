package io.github.jqssun.displaymirror.sunshine;
import com.mooncast.host.InputBridge;
public final class SunshineKeyboard { public static void handleKeyboardEvent(int key,boolean release,int flags){InputBridge.Receiver receiver=InputBridge.receiver;if(receiver!=null)receiver.key(key,release,flags);} }
