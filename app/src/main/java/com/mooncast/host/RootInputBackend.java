package com.mooncast.host;

import android.content.Context;
import android.net.*;
import java.io.*;
import java.util.UUID;
import java.util.function.Consumer;
import org.json.JSONObject;

final class RootInputBackend implements RemoteInputController.Backend {
    private final Context context;
    private final Consumer<String> log;
    private volatile boolean ready,closed;
    private volatile int displayId;
    void display(int id){displayId=id;if(ready())sendDisplay();}
    private void sendDisplay(){try{send(new JSONObject().put("kind","display").put("id",displayId));}catch(org.json.JSONException e){throw new IllegalArgumentException(e);}}
    private LocalServerSocket server;
    private LocalSocket socket;
    private DataOutputStream commands;
    private Process process;
    RootInputBackend(Context context,Consumer<String> log){this.context=context;this.log=log;}
    void start(){new Thread(()->{
        try{
            String endpoint="mooncast-input-"+UUID.randomUUID();server=new LocalServerSocket(endpoint);
            String command="CLASSPATH="+RootBackend.quote(context.getApplicationInfo().sourceDir)+" /system/bin/app_process / com.mooncast.host.RootInputDaemon "+RootBackend.quote(endpoint)+" "+android.os.Process.myUid();
            process=new ProcessBuilder("su","-c",command).redirectErrorStream(true).start();
            new Thread(()->{try(BufferedReader reader=new BufferedReader(new InputStreamReader(process.getInputStream()))){String line;while((line=reader.readLine())!=null)android.util.Log.i("MoonCastRootInput",line);int code=process.waitFor();if(!closed){ready=false;log.accept("Root 控制进程已结束 ("+code+")；检查 Root 授权");try{server.close();}catch(IOException ignored){}}}catch(Exception ignored){}},"RootInputOutput").start();
            LocalSocket accepted=server.accept();if(closed){accepted.close();return;}
            if(accepted.getPeerCredentials().getUid()!=0){accepted.close();throw new SecurityException("拒绝非 Root 输入通道");}
            socket=accepted;commands=new DataOutputStream(socket.getOutputStream());
            DataInputStream input=new DataInputStream(socket.getInputStream());
            if(!input.readUTF().equals("READY"))throw new IOException("Root 输入初始化失败");
            ready=true;sendDisplay();log.accept("Root 反控已就绪；支持持续触摸与键盘");
            // EOF means the broker died. No input may be sent after that point.
            try{while(input.read()!=-1){}}finally{ready=false;}
        }catch(Exception e){if(!closed)log.accept("Root 反控不可用: "+e.getMessage());}
    },"RootInputConnect").start();}
    public boolean ready(){return ready && !closed;}
    private synchronized void send(JSONObject event){
        if(!ready())return;
        try{commands.writeUTF(event.toString());commands.flush();}
        catch(IOException e){ready=false;throw new IllegalStateException("Root 输入通道已断开",e);}
    }
    public void touch(int type,int id,float x,float y,float pressure){try{send(new JSONObject().put("kind","touch").put("type",type).put("id",id).put("x",x).put("y",y).put("pressure",pressure));}catch(org.json.JSONException e){throw new IllegalArgumentException(e);}}
    public void key(int key,boolean release,int flags){try{send(new JSONObject().put("kind","key").put("key",key).put("release",release).put("flags",flags));}catch(org.json.JSONException e){throw new IllegalArgumentException(e);}}
    public void cancel(){try{send(new JSONObject().put("kind","cancel"));}catch(org.json.JSONException e){throw new IllegalArgumentException(e);}}
    public synchronized void close(){
        if(closed)return;
        try{cancel();if(commands!=null){commands.writeUTF("STOP");commands.flush();}}catch(Exception ignored){}
        closed=true;ready=false;
        try{if(socket!=null)socket.close();}catch(IOException ignored){}
        try{if(server!=null)server.close();}catch(IOException ignored){}
        // Give the broker time to release held pointers/keys on STOP or EOF.
        Process child=process;
        if(child!=null)new Thread(()->{try{if(!child.waitFor(2,java.util.concurrent.TimeUnit.SECONDS))child.destroy();}catch(InterruptedException e){Thread.currentThread().interrupt();}},"RootInputCleanup").start();
    }
}
