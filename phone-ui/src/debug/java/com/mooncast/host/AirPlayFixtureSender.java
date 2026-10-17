/* SPDX-License-Identifier: GPL-3.0-only */
package com.mooncast.host;
import android.app.Service;import android.content.Intent;import android.os.*;
import airplaylib.*;import java.io.File;import org.json.JSONObject;
/** Debug-only generated-media sender in :stream. No capture grant or user media. */
public final class AirPlayFixtureSender extends Service {
    private volatile Session active;private volatile Thread worker;private volatile boolean stopRequested;
    private volatile int sessionToken;
    private final Messenger binder=new Messenger(new Handler(Looper.getMainLooper(),message->{
        Messenger reply=message.replyTo;Bundle input=message.getData();int token=input.getInt("token");
        if(message.what==2){
            if(token!=sessionToken)return true;stopRequested=true;
            new Thread(()->{
                try{Session old=active;if(old!=null)old.disconnect();Thread sending=worker;if(sending!=null)sending.join(5000);
                    if(sending!=null&&sending.isAlive())throw new IllegalStateException("sender-loop-exit-timeout");active=null;worker=null;reply(reply,token,"stopped",null);
                }catch(Throwable failure){reply(reply,token,"failure",failure.getClass().getSimpleName());}
            },"FixtureStop").start();
        }else if(message.what==1){
            if(worker!=null&&worker.isAlive()){reply(reply,token,"failure","previous-sender-loop-still-alive");return true;}
            sessionToken=token;stopRequested=false;worker=new Thread(()->start(input,reply,token),"FixtureStart");worker.start();
        }
        return true;
    }));
    @Override public IBinder onBind(Intent intent){return binder.getBinder();}
    private void start(Bundle input,Messenger reply,int token){
        try{
            byte[] frame=input.getByteArray("frame");if(frame==null||frame.length>65536)throw new IllegalArgumentException("fixture");
            Airplaylib.setAirPlay1Mode(false);
            Airplaylib.setCredentialsPath(new File(getCacheDir(),"generated-fixture-credentials.json").getAbsolutePath());
            Session created=Airplaylib.newSession(new EventHandler(){
                public void onDeviceFound(String value){}
                public void onConnected(){reply(reply,token,"connected",null);}
                public void onDisconnected(String reason){reply(reply,token,"disconnected",null);}
                public void onPinRequired(){reply(reply,token,"failure","pin-unexpected");}
                public void onError(String reason){reply(reply,token,"failure","handshake-or-transport");}
                public void onLog(String value){}
            });
            active=created;created.setAudioEnabled(false);created.setClockReference(System.nanoTime()/1000);
            created.connect("127.0.0.1",input.getInt("port"),"",640,360,30);
            long deadline=SystemClock.elapsedRealtime()+25000;
            while(!stopRequested&&!created.isConnected()&&SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(50);
            if(stopRequested)return;if(!created.isConnected())throw new IllegalStateException("handshake-timeout");
            int attempts=0;
            while(!stopRequested&&active==created&&token==sessionToken){
                if(!created.isConnected()){statistics(reply,token,created,attempts);throw new IllegalStateException("transport-disconnected");}
                created.sendFrameAt(frame,true,System.nanoTime()/1000);attempts++;
                if(attempts%10==0)statistics(reply,token,created,attempts);
                SystemClock.sleep(100);
            }
        }catch(Throwable failure){if(!stopRequested)reply(reply,token,"failure",failure.getClass().getSimpleName());}
    }
    private static void statistics(Messenger reply,int token,Session session,int attempts)throws Exception{
        JSONObject raw=new JSONObject(session.diagnostics());JSONObject last=raw.optJSONObject("last");if(last!=null)raw=last;
        // Fixed structural fields only. Never retain arbitrary native errors, addresses or media.
        JSONObject safe=new JSONObject();safe.put("connected",session.isConnected()).put("sendCalls",attempts);
        String transport=raw.optString("transport","");safe.put("transport",transport.equals("legacy-http")||transport.equals("idle-or-modern")?transport:"other");
        for(String name:new String[]{"video","bytes","lastWriteMs","elapsedMs","ptsAgeMs"})safe.put(name,raw.optLong(name,-1));
        Message m=Message.obtain();Bundle b=new Bundle();b.putString("event","stats");b.putInt("token",token);b.putInt("frames",attempts);b.putString("stats",safe.toString());m.setData(b);if(reply!=null)reply.send(m);
    }
    private static void reply(Messenger target,int token,String event,String failure){if(target==null)return;try{Message m=Message.obtain();Bundle b=new Bundle();b.putInt("token",token);b.putString("event",event);b.putString("failure",failure);m.setData(b);target.send(m);}catch(RemoteException ignored){}}
    @Override public void onDestroy(){stopRequested=true;Session old=active;active=null;if(old!=null)new Thread(old::disconnect,"FixtureDestroy").start();super.onDestroy();}
}
