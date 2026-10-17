/* SPDX-License-Identifier: GPL-3.0-only */
package com.mooncast.host;
import android.app.Service;import android.content.Intent;import android.os.*;
import airplaylib.*;import java.io.File;import org.json.JSONObject;
/** Debug-only generated-media sender in :stream. No capture grant or user media. */
public final class AirPlayFixtureSender extends Service {
    private static final class Run {final int token;volatile Session session;volatile Thread worker;volatile boolean stopping;volatile String cleanupFailure;Run(int token){this.token=token;}}
    private volatile Run current;
    private final Messenger binder=new Messenger(new Handler(Looper.getMainLooper(),message->{
        Messenger reply=message.replyTo;Bundle input=message.getData();int token=input.getInt("token");
        if(message.what==2){
            final Run run=current;if(run==null||token!=run.token)return true;run.stopping=true;
            // Capture exact ownership before scheduling; a late STOP cannot clear a newer run.
            final Thread sending=run.worker;
            new Thread(()->{
                try{Session session=run.session;if(session!=null)session.disconnect();if(sending!=null)sending.join(5000);
                    if(sending!=null&&sending.isAlive())throw new IllegalStateException("sender-loop-exit-timeout");
                    if(run.cleanupFailure!=null)throw new IllegalStateException(run.cleanupFailure);
                    if(current==run)current=null;reply(reply,token,"stopped",null);
                }catch(Throwable failure){run.cleanupFailure="disconnect-failed";reply(reply,token,"failure",failure.getClass().getSimpleName());}
            },"FixtureStop").start();
        }else if(message.what==1){
            Run old=current;if(old!=null){reply(reply,token,"failure","previous-sender-not-retired");return true;}
            Run run=new Run(token);current=run;run.worker=new Thread(()->start(input,reply,run),"FixtureStart");run.worker.start();
        }
        return true;
    }));
    @Override public IBinder onBind(Intent intent){return binder.getBinder();}
    private void start(Bundle input,Messenger reply,Run run){
        Session owned=null;int token=run.token;
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
            owned=created;run.session=created;if(run.stopping)return;created.setAudioEnabled(false);created.setClockReference(System.nanoTime()/1000);
            created.connect("127.0.0.1",input.getInt("port"),"",640,360,30);
            long deadline=SystemClock.elapsedRealtime()+25000;
            while(!run.stopping&&!created.isConnected()&&SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(50);
            if(run.stopping)return;if(!created.isConnected())throw new IllegalStateException("handshake-timeout");
            int attempts=0;
            while(!run.stopping&&current==run){
                if(!created.isConnected()){statistics(reply,token,created,attempts);throw new IllegalStateException("transport-disconnected");}
                created.sendFrameAt(frame,true,System.nanoTime()/1000);attempts++;
                if(attempts%10==0)statistics(reply,token,created,attempts);
                SystemClock.sleep(100);
            }
        }catch(Throwable failure){if(!run.stopping)reply(reply,token,"failure",failure.getClass().getSimpleName());}
        finally{if(owned!=null){try{owned.disconnect();run.session=null;}catch(Throwable failure){run.cleanupFailure="disconnect-failed";reply(reply,token,"failure",run.cleanupFailure);}}}
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
    @Override public void onDestroy(){Run run=current;if(run!=null){run.stopping=true;Session session=run.session;if(session!=null)new Thread(session::disconnect,"FixtureDestroy").start();}super.onDestroy();}
}
