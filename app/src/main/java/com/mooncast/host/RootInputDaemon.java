package com.mooncast.host;

import android.net.*;
import java.io.*;
import org.json.JSONObject;

/** Separate privilege broker: Root input can accompany ordinary MediaProjection capture. */
public final class RootInputDaemon {
    public static void main(String[] args){
        RootInputInjector injector=null;
        if(android.os.Looper.myLooper()==null)android.os.Looper.prepareMainLooper();
        try(LocalSocket socket=new LocalSocket()){
            if(args.length!=2 || android.os.Process.myUid()!=0)throw new SecurityException("uid 0 required");
            socket.connect(new LocalSocketAddress(args[0]));
            if(socket.getPeerCredentials().getUid()!=Integer.parseInt(args[1]))throw new SecurityException("unexpected owner");
            DataInputStream input=new DataInputStream(socket.getInputStream());DataOutputStream output=new DataOutputStream(socket.getOutputStream());
            injector=new RootInputInjector();output.writeUTF("READY");output.flush();
            for(;;){
                String command=input.readUTF();if(command.equals("STOP"))break;
                JSONObject event=new JSONObject(command);String kind=event.getString("kind");
                if(kind.equals("touch"))injector.touch(event.getInt("type"),event.getInt("id"),(float)event.getDouble("x"),(float)event.getDouble("y"),(float)event.getDouble("pressure"));
                else if(kind.equals("key"))injector.key(event.getInt("key"),event.getBoolean("release"),event.getInt("flags"));
                else if(kind.equals("cancel"))injector.cancel();
            }
        }catch(EOFException ignored){}
        catch(Throwable e){android.util.Log.e("MoonCastInput","Root control failed",e);}
        finally{if(injector!=null)try{injector.cancel();}catch(RuntimeException ignored){}}
    }
}
