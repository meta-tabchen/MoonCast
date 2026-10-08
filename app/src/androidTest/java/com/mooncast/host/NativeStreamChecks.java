package com.mooncast.host;

import android.app.*;
import android.content.*;
import android.os.*;
import com.mooncast.host.testclient.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.json.*;

/** Two real Moonlight-core clients/decoders on hardware-capable device loopback; no user's pairing files retained. */
final class NativeStreamChecks {
    private static final class Endpoint implements AutoCloseable {
        final Context context;final Intent intent;final int status;
        final AtomicReference<Messenger> remote=new AtomicReference<>();final AtomicReference<Bundle> snapshot=new AtomicReference<>();
        final Messenger reply=new Messenger(new Handler(Looper.getMainLooper(),m->{snapshot.set(m.getData());return true;}));
        final ServiceConnection connection=new ServiceConnection(){public void onServiceConnected(ComponentName n,IBinder b){remote.set(new Messenger(b));}public void onServiceDisconnected(ComponentName n){remote.set(null);}};
        boolean bound;
        Endpoint(Context c,Class<?> type,int status){context=c;intent=new Intent(c,type);this.status=status;}
        void bind(){bound=context.bindService(intent,connection,Context.BIND_AUTO_CREATE);}
        Bundle query()throws Exception{if(remote.get()!=null){Message m=Message.obtain(null,status);m.replyTo=reply;remote.get().send(m);}Thread.sleep(100);Bundle b=snapshot.get();return b==null?new Bundle():b;}
        void send(int what,Bundle data)throws Exception{Message m=Message.obtain(null,what);if(data!=null)m.setData(data);remote.get().send(m);}
        public void close(){try{if(remote.get()!=null)remote.get().send(Message.obtain(null,2));}catch(RemoteException ignored){}if(bound){context.unbindService(connection);bound=false;}}
    }
    static void verify(Instrumentation test,boolean phone)throws Exception{
        if(EncoderSupport.hardware("video/avc").isEmpty())throw new IllegalStateException("Native stream suite requires a Surface-input hardware AVC encoder; MuMu software codecs are unsupported");
        Context c=test.getTargetContext();Activity app=test.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        test.runOnMainSync(()->app.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
        File pairing=new File(c.getFilesDir(),"paired-clients.json");byte[] previous=pairing.isFile()?Files.readAllBytes(pairing.toPath()):null;
        Endpoint host=new Endpoint(c,HostService.class,HostService.STATUS),one=new Endpoint(c,ClientOneService.class,StreamClientService.STATUS),two=new Endpoint(c,ClientTwoService.class,StreamClientService.STATUS);
        try{
            Intent grant=null;
            if(phone){
                test.runOnMainSync(()->c.startActivity(new Intent(c,CaptureConsentProbe.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
                long consentEnd=SystemClock.elapsedRealtime()+90000;
                while(CaptureConsentProbe.grant==null && SystemClock.elapsedRealtime()<consentEnd)Thread.sleep(100);
                grant=CaptureConsentProbe.grant;if(grant==null)throw new AssertionError("system screen consent not granted");
            }else ShizukuSetupChecks.authorize(test);
            JSONArray clients=new JSONArray();
            for(String name:new String[]{"one","two"}){File dir=new File(c.getCacheDir(),"native-client-test/"+name);dir.mkdirs();HostIdentity.ensure(dir);clients.put(new JSONObject().put("name","generated test "+name).put("uuid",UUID.randomUUID().toString()).put("cert",new String(Files.readAllBytes(new File(dir,"host-cert.pem").toPath()),java.nio.charset.StandardCharsets.US_ASCII)));}
            Files.write(pairing.toPath(),new JSONObject().put("root",new JSONObject().put("uniqueid",UUID.randomUUID().toString()).put("named_devices",clients)).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            c.startForegroundService(new Intent(c,HostService.class).putExtra("source",phone?0:2).putExtra("grant",grant).putExtra("watchParty",true).putExtra("control",false).putExtra("audio",false).putExtra("hevc",false).putExtra("scaleMode",0).putExtra("name","MoonCast loopback test"));host.bind();
            long end=SystemClock.elapsedRealtime()+18000;
            while(SystemClock.elapsedRealtime()<end){Bundle b=host.query();if(!b.getString("error","").isEmpty())throw new AssertionError("host "+b.getString("error"));try(var socket=new java.net.Socket("127.0.0.1",47989)){break;}catch(IOException ignored){}}
            c.startService(new Intent(c,ClientOneService.class).putExtra("client","one").putExtra("width",640).putExtra("height",360));one.bind();
            long displayEnd=SystemClock.elapsedRealtime()+15000;Bundle first=new Bundle();while(SystemClock.elapsedRealtime()<displayEnd){first=host.query();Bundle client=one.query();if(!client.getString("error","").isEmpty())throw new AssertionError("client "+client);long[] active=first.getLongArray("receivers");if(phone?active!=null && active.length==1:first.getInt("displayId",-1)>=0)break;}
            long[] firstIds=first.getLongArray("receivers");if(firstIds==null || firstIds.length!=1)throw new AssertionError("first session missing "+first);
            if(phone)test.startActivitySync(new Intent(c,TestPatternActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            else{Bundle launch=new Bundle();launch.putString("component",new ComponentName(c,TestPatternActivity.class).flattenToString());host.send(HostService.LAUNCH_APP,launch);}
            waitDecoded(one,10,20000);
            android.util.Log.i("MoonCastStreamTest","Receiver decoded at least 10 frames");
            c.startService(new Intent(c,ClientTwoService.class).putExtra("client","two").putExtra("width",800).putExtra("height",600));two.bind();waitDecoded(two,10,20000);
            Bundle both=host.query();long[] ids=both.getLongArray("receivers");if(ids==null || ids.length!=2)throw new AssertionError("two concurrent native sessions missing "+both);
            int survivor=two.query().getInt("decoded");Bundle disconnect=new Bundle();disconnect.putLong("session",firstIds[0]);host.send(HostService.DISCONNECT,disconnect);
            long stopEnd=SystemClock.elapsedRealtime()+8000;while(SystemClock.elapsedRealtime()<stopEnd){long[] remaining=host.query().getLongArray("receivers");if(remaining!=null && remaining.length==1)break;}
            ids=host.query().getLongArray("receivers");if(ids==null || ids.length!=1 || ids[0]==firstIds[0])throw new AssertionError("individual disconnect failed");
            waitDecoded(two,survivor+10,10000);
            android.util.Log.i("MoonCastStreamTest","Survivor decoded after individual disconnect");
            disconnect.putLong("session",ids[0]);host.send(HostService.DISCONNECT,disconnect);
            long idleEnd=SystemClock.elapsedRealtime()+8000;boolean idle=false;
            while(SystemClock.elapsedRealtime()<idleEnd){long[] remaining=host.query().getLongArray("receivers");if(remaining!=null && remaining.length==0){idle=true;break;}}
            if(!idle || host.remote.get()==null)throw new AssertionError("last disconnect did not keep host available");
            one.close();two.close();Thread.sleep(4500);one.snapshot.set(null);one.remote.set(null);
            c.startService(new Intent(c,ClientOneService.class).putExtra("client","one").putExtra("width",640).putExtra("height",360));one.bind();
            waitDecoded(one,10,20000);
            ids=host.query().getLongArray("receivers");if(ids==null || ids.length!=1)throw new AssertionError("reconnect missing receiver");
            android.util.Log.i("MoonCastStreamTest","Reconnected receiver decoded without new capture consent");
            host.send(HostService.STOP,null);Thread.sleep(4500);
            if(host.remote.get()!=null)throw new AssertionError("explicit Stop did not end host");
        }finally{
            one.close();two.close();if(host.remote.get()!=null)try{host.send(HostService.STOP,null);}catch(Exception ignored){}if(host.bound){c.unbindService(host.connection);host.bound=false;}Thread.sleep(700);
            if(previous!=null)Files.write(pairing.toPath(),previous);else Files.deleteIfExists(pairing.toPath());
            for(String name:new String[]{"one","two"}){File dir=new File(c.getCacheDir(),"native-client-test/"+name);Files.deleteIfExists(new File(dir,"host-cert.pem").toPath());Files.deleteIfExists(new File(dir,"host-key.pem").toPath());dir.delete();}
            test.runOnMainSync(app::finish);
        }
    }
    private static void waitDecoded(Endpoint endpoint,int frames,int timeout)throws Exception{
        long end=SystemClock.elapsedRealtime()+timeout;Bundle last=new Bundle();while(SystemClock.elapsedRealtime()<end){last=endpoint.query();if(!last.getString("error","").isEmpty())throw new AssertionError("client "+last);if(last.getInt("decoded")>=frames)return;}throw new AssertionError("decoder timeout "+last);
    }
}
