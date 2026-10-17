/* SPDX-License-Identifier: GPL-3.0-only */
package com.mooncast.host;

import android.app.Service;
import android.content.Intent;
import android.os.*;
import airplaylib.*;
import java.io.File;

/** Debug-only generated-media sender in :stream. No capture grant or user media. */
public final class AirPlayFixtureSender extends Service {
    private volatile Session active;
    private volatile boolean running;
    private final Messenger binder=new Messenger(new Handler(Looper.getMainLooper(),message->{
        Messenger reply=message.replyTo;Bundle input=message.getData();
        if(message.what==2){running=false;new Thread(()->{try{Session old=active;active=null;if(old!=null)old.disconnect();reply(reply,"stopped",null);}catch(Throwable e){reply(reply,"failure",e.getClass().getSimpleName());}},"FixtureStop").start();}
        else if(message.what==1)new Thread(()->start(input,reply),"FixtureStart").start();
        return true;
    }));
    @Override public IBinder onBind(Intent intent){return binder.getBinder();}
    private void start(Bundle input,Messenger reply){
        try{
            byte[] frame=input.getByteArray("frame");if(frame==null||frame.length>65536)throw new IllegalArgumentException("fixture");
            Airplaylib.setAirPlay1Mode(false);
            Airplaylib.setCredentialsPath(new File(getCacheDir(),"generated-fixture-credentials.json").getAbsolutePath());
            Session created=Airplaylib.newSession(new EventHandler(){
                public void onDeviceFound(String value){}
                public void onConnected(){reply(reply,"connected",null);}
                public void onDisconnected(String reason){reply(reply,"disconnected",null);}
                public void onPinRequired(){reply(reply,"failure","pin-unexpected");}
                public void onError(String reason){reply(reply,"failure","handshake-or-transport");}
                public void onLog(String value){}
            });
            active=created;created.setAudioEnabled(false);created.setClockReference(System.nanoTime()/1000);
            created.connect("127.0.0.1",input.getInt("port"),"",640,360,30);
            long deadline=SystemClock.elapsedRealtime()+25000;
            while(!created.isConnected()&&SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(50);
            if(!created.isConnected())throw new IllegalStateException("handshake-timeout");
            running=true;int sent=0;
            while(running&&active==created){created.sendFrameAt(frame,true,System.nanoTime()/1000);sent++;if(sent%10==0){Message m=Message.obtain();Bundle b=new Bundle();b.putString("event","frames");b.putInt("frames",sent);m.setData(b);if(reply!=null)reply.send(m);}SystemClock.sleep(100);}
        }catch(Throwable e){reply(reply,"failure",e.getClass().getSimpleName());}
    }
    private static void reply(Messenger target,String event,String failure){if(target==null)return;try{Message m=Message.obtain();Bundle b=new Bundle();b.putString("event",event);b.putString("failure",failure);m.setData(b);target.send(m);}catch(RemoteException ignored){}}
    @Override public void onDestroy(){running=false;Session old=active;active=null;if(old!=null)new Thread(old::disconnect,"FixtureDestroy").start();super.onDestroy();}
}
