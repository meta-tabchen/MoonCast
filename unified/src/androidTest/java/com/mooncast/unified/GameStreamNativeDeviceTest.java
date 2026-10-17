package com.mooncast.unified;

import android.app.*;
import android.content.*;
import android.os.*;
import java.io.File;
import java.net.Socket;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Generated Surface -> shipped Sunshine encode/transport -> Moonlight JNI -> MediaCodec.
 * Counts actual buffer-mode decoded outputs; no rendered-pixel or screen-capture claim. */
public final class GameStreamNativeDeviceTest {
    private final Instrumentation runner;
    private final Context context;
    private final String run=UUID.randomUUID().toString().replace("-","");
    private String phase="initializing";
    public GameStreamNativeDeviceTest(Instrumentation i,Bundle arguments){runner=i;context=i.getTargetContext();}
    public void run(){
        Bundle result=new Bundle();
        int code=Activity.RESULT_CANCELED;
        Endpoint host=null,one=null,two=null;
        try{
            require(Build.FINGERPRINT.contains("generic")||Build.MODEL.contains("sdk"),"Disposable emulator only");
            host=new Endpoint("GameStreamHostFixture",null,640,360);host.bind();
            phase="host startup";
            long end=SystemClock.elapsedRealtime()+30000;boolean ready=false;
            while(SystemClock.elapsedRealtime()<end){
                healthy(host.query());
                try(Socket socket=new Socket("127.0.0.1",47989)){ready=true;break;}
                catch(java.io.IOException ignored){SystemClock.sleep(100);}
            }
            require(ready,"native host ready");int pid=host.query().getInt("pid");
            phase="first 640x360@30 client";
            one=new Endpoint("GameStreamClientOneFixture","one",640,360);one.bind();decoded(host,one,10,0,45000);
            Bundle first=host.query();healthy(first);long[] initial=first.getLongArray("sessions");
            require(initial!=null&&initial.length==1,"one native session");long firstId=initial[0];
            phase="second 640x360@30 client";
            two=new Endpoint("GameStreamClientTwoFixture","two",640,360);two.bind();decoded(host,two,10,0,45000);
            Bundle both=stats(host,2,0,30000);long secondId=0;boolean found=false;
            for(long id:both.getLongArray("sessions"))if(id!=firstId){secondId=id;found=true;}
            require(found,"distinct second native handle");
            phase="first disconnect and survivor";
            Bundle stop=new Bundle();stop.putLong("session",firstId);
            require(host.query(3,stop).getBoolean("disconnected"),"native first disconnect accepted");
            waitSessions(host,1,15000);
            Bundle remaining=host.query();
            require(remaining.getLongArray("sessions")[0]==secondId&&remaining.getBoolean("active-"+secondId),"survivor handle remains active");
            // Both baselines are after confirmed removal. Buffered pre-disconnect counters cannot pass.
            long barrier=SystemClock.elapsedRealtime();
            int before=two.query().getInt("decoded"),samples=host.query().getInt("samples-"+secondId);
            decoded(host,two,before+40,barrier,45000);
            Bundle live=freshStats(host,secondId,samples,barrier,30000);
            require(live.getInt("pid")==pid,"same host PID after disconnect");
            require(!host.query(3,stop).getBoolean("disconnected"),"stale first handle rejected");
            stop.putLong("session",secondId);
            require(host.query(3,stop).getBoolean("disconnected"),"last disconnect accepted");
            waitSessions(host,0,15000);
            require(!host.query(3,stop).getBoolean("disconnected"),"stale last handle rejected");
            one.close();one=null;two.close();two=null;
            for(int[] size:new int[][]{{1280,720},{1920,1080},{3840,2160},{1280,720}}){
                phase="resolution reconnect "+size[0]+"x"+size[1]+"@30";
                one=new Endpoint("GameStreamClientOneFixture","one",size[0],size[1]);one.bind();
                decoded(host,one,10,0,60000);Bundle output=one.query();
                require(output.getInt("width")==size[0]&&output.getInt("height")==size[1],"actual visible decoder dimensions: "+output);
                Bundle again=stats(host,1,0,30000);long current=again.getLongArray("sessions")[0];
                require(again.getInt("width-"+current)==size[0]&&again.getInt("height-"+current)==size[1]&&again.getInt("targetFps-"+current)==30&&again.getInt("pid")==pid,"actual encoder dimensions/target FPS and host survival: "+again);
                Bundle disconnect=new Bundle();disconnect.putLong("session",current);
                require(host.query(3,disconnect).getBoolean("disconnected"),"resolution transition disconnect");
                waitSessions(host,0,15000);one.close();one=null;
            }
            result.putString("stream","GAMESTREAM_NATIVE_SESSION_PASSED two real clients; after confirmed first removal, survivor decoded 40 new outputs and produced fresh encoder stats; last disconnect and 720p/1080p/4K/720p@30 reconnects; actual native handles and same host PID. Generated video/paced silence only; no capture, rendered pixel, LAN pairing or physical TV claim.\n");
            code=Activity.RESULT_OK;
        }catch(Throwable failure){
            result.putString("stream","GAMESTREAM_NATIVE_SESSION_FAILED_OR_UNSUPPORTED phase="+phase+"\n"+android.util.Log.getStackTraceString(failure));
        }finally{
            boolean allDead=true;
            for(Endpoint endpoint:new Endpoint[]{one,two,host})if(endpoint!=null)try{endpoint.close();}catch(Throwable failure){
                allDead=false;code=Activity.RESULT_CANCELED;
                result.putString("stream",result.getString("stream","")+"\nFixture cleanup failed: "+failure);
            }
            // Delete only this generated run after every process is confirmed dead. Never user identities.
            if(allDead)try{deleteGeneratedRun();}catch(Throwable failure){code=Activity.RESULT_CANCELED;result.putString("stream",result.getString("stream","")+"\nGenerated identity cleanup failed: "+failure);}
            if(code!=Activity.RESULT_OK)result.putString("stream",result.getString("stream","").replace("GAMESTREAM_NATIVE_SESSION_PASSED","GAMESTREAM_NATIVE_SESSION_FAILED"));
        }
        runner.finish(code,result);
    }
    private void deleteGeneratedRun()throws Exception{
        Path root=new File(context.getCacheDir(),"native-generated-test/"+run).toPath();
        if(Files.exists(root))try(var files=Files.walk(root)){for(Path file:files.sorted(Comparator.reverseOrder()).toArray(Path[]::new))Files.delete(file);}
        require(!Files.exists(root),"generated identity directory removed");
    }
    private static void require(boolean ok,String what){if(!ok)throw new AssertionError(what);}
    private static void healthy(Bundle b){require(b.getString("error","").isEmpty(),b.getString("error","missing reply"));}
    private void decoded(Endpoint host,Endpoint client,int number,long after,int timeout)throws Exception{
        long until=SystemClock.elapsedRealtime()+timeout;Bundle b=null;
        while(SystemClock.elapsedRealtime()<until){
            healthy(host.query());b=client.query();healthy(b);
            if(b.getInt("decoded")>=number&&b.getLong("lastDecodeElapsed")>after&&SystemClock.elapsedRealtime()-b.getLong("lastDecodeElapsed")<3000)return;
            SystemClock.sleep(100);
        }
        throw new AssertionError("Real decoder output timeout phase="+phase+", client="+b+", host="+host.query());
    }
    private void waitSessions(Endpoint e,int count,int timeout)throws Exception{
        long until=SystemClock.elapsedRealtime()+timeout;
        while(SystemClock.elapsedRealtime()<until){Bundle b=e.query();healthy(b);long[] ids=b.getLongArray("sessions");if(ids!=null&&ids.length==count)return;SystemClock.sleep(100);}
        throw new AssertionError("session teardown timeout");
    }
    private Bundle stats(Endpoint e,int count,long after,int timeout)throws Exception{
        long until=SystemClock.elapsedRealtime()+timeout;
        while(SystemClock.elapsedRealtime()<until){
            Bundle b=e.query();healthy(b);long[] ids=b.getLongArray("sessions");boolean ok=ids!=null&&ids.length==count;
            if(ok)for(long id:ids)ok&=b.getInt("samples-"+id)>=2&&b.getDouble("fps-"+id)>0&&b.getInt("targetFps-"+id)==30&&b.getBoolean("active-"+id)&&b.getLong("lastStatsElapsed-"+id)>after&&SystemClock.elapsedRealtime()-b.getLong("lastStatsElapsed-"+id)<3000;
            if(ok)return b;SystemClock.sleep(100);
        }
        throw new AssertionError("fresh real streamStats/active handles missing phase="+phase+", host="+e.query());
    }
    private Bundle freshStats(Endpoint e,long id,int baseline,long after,int timeout)throws Exception{
        long until=SystemClock.elapsedRealtime()+timeout;
        while(SystemClock.elapsedRealtime()<until){Bundle b=stats(e,1,after,Math.max(1,(int)(until-SystemClock.elapsedRealtime())));if(b.getInt("samples-"+id)>baseline)return b;SystemClock.sleep(100);}
        throw new AssertionError("no new survivor encoder sample after teardown");
    }
    private final class Endpoint implements AutoCloseable {
        final Intent intent;
        final CountDownLatch connected=new CountDownLatch(1),dead=new CountDownLatch(1);
        final AtomicLong nonce=new AtomicLong();
        final ConcurrentHashMap<Long,CompletableFuture<Bundle>> replies=new ConcurrentHashMap<>();
        volatile Messenger remote;volatile boolean endpointFailed;volatile IBinder firstBinder;boolean bound,closeRequested,confirmedDead;
        final Messenger answer=new Messenger(new Handler(Looper.getMainLooper(),m->{Bundle b=m.getData();CompletableFuture<Bundle> future=replies.get(b.getLong("nonce"));if(future!=null)future.complete(b);return true;}));
        final ServiceConnection connection=new ServiceConnection(){
            public void onServiceConnected(ComponentName name,IBinder binder){
                if(firstBinder!=null||endpointFailed||closeRequested){
                    endpointFailed=true;
                    try{new Messenger(binder).send(Message.obtain(null,2));}catch(RemoteException ignored){}
                    return;
                }
                firstBinder=binder;remote=new Messenger(binder);
                try{binder.linkToDeath(()->{if(!closeRequested)endpointFailed=true;remote=null;dead.countDown();for(var f:replies.values())f.completeExceptionally(new IllegalStateException("fixture process died"));},0);}
                catch(RemoteException failure){remote=null;dead.countDown();}
                connected.countDown();
            }
            public void onServiceDisconnected(ComponentName name){if(!closeRequested)endpointFailed=true;remote=null;dead.countDown();}
        };
        Endpoint(String cls,String client,int width,int height){intent=new Intent().setClassName(context.getPackageName(),"com.mooncast.host."+cls).putExtra("run",run).putExtra("client",client).putExtra("width",width).putExtra("height",height);}
        void bind()throws Exception{bound=context.bindService(intent,connection,Context.BIND_AUTO_CREATE);require(bound&&connected.await(20,TimeUnit.SECONDS)&&remote!=null,"fixture binding");}
        Bundle query()throws Exception{return query(1,new Bundle());}
        Bundle query(int what,Bundle data)throws Exception{
            Messenger current=remote;require(current!=null&&!closeRequested&&!endpointFailed,"fixture still alive without unexpected restart");long id=nonce.incrementAndGet();data.putLong("nonce",id);
            CompletableFuture<Bundle> future=new CompletableFuture<>();replies.put(id,future);Message m=Message.obtain(null,what);m.setData(data);m.replyTo=answer;
            try{current.send(m);return future.get(15,TimeUnit.SECONDS);}finally{replies.remove(id);}
        }
        public void close()throws Exception{
            if(confirmedDead)return;
            closeRequested=true;Messenger current=remote;
            try{if(current!=null)current.send(Message.obtain(null,2));}
            catch(RemoteException failure){if(current.getBinder().isBinderAlive())throw failure;dead.countDown();}
            finally{if(bound){context.unbindService(connection);bound=false;}}
            require(dead.await(5,TimeUnit.SECONDS)&&firstBinder!=null&&!firstBinder.isBinderAlive(),"fixture process did not exit; reconnect and cleanup prohibited");
            require(!endpointFailed,"unexpected fixture process death/rebind; cannot pass cleanup");
            confirmedDead=true;remote=null;
        }
    }
}
