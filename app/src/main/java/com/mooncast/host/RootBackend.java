package com.mooncast.host;

import android.content.Context;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import java.io.*;
import java.util.UUID;
import org.json.JSONObject;

/** Root is requested only when the user explicitly selects Root mode and presses Start. */
final class RootBackend {
    interface Events { void event(String kind,String text); }
    private final Context context;
    private final Events events;
    private LocalServerSocket server;
    private LocalSocket socket;
    private DataOutputStream commands;
    private Process process;
    private volatile boolean stopping;
    RootBackend(Context c,Events e) { context=c; events=e; }
    synchronized void start(String name,boolean hevc,int scaleMode,boolean control,int frameLimit) throws Exception {
        if(stopping) throw new IOException("Root 发射端已停止");
        String endpoint="mooncast-"+UUID.randomUUID();
        server=new LocalServerSocket(endpoint);
        String command="CLASSPATH="+quote(context.getApplicationInfo().sourceDir)
            +" /system/bin/app_process / com.mooncast.host.RootDaemon "
            +quote(endpoint)+" "+quote(context.getFilesDir().getPath())+" "
            +quote(context.getApplicationInfo().nativeLibraryDir)+" "+quote(name)+" "+hevc+" "+scaleMode+" "+control+" "+frameLimit;
        process=new ProcessBuilder("su","-c",command).redirectErrorStream(true).start();
        new Thread(() -> {
            try (BufferedReader reader=new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line; while ((line=reader.readLine())!=null) android.util.Log.i("MoonCastRoot",line);
                int code=process.waitFor();
                if (!stopping) events.event("error","Root 进程结束 ("+code+")；检查 Root 授权与 ROM 支持");
            } catch (Exception e) { if (!stopping) events.event("error",e.getMessage()); }
        },"RootOutput").start();
        new Thread(() -> {
            try {
                LocalSocket accepted=server.accept();
                if (accepted.getPeerCredentials().getUid()!=0) { accepted.close(); throw new IOException("拒绝非 Root 的本地连接"); }
                socket=accepted;
                synchronized (this) { commands=new DataOutputStream(socket.getOutputStream()); }
                DataInputStream input=new DataInputStream(socket.getInputStream());
                while (!stopping) {
                    JSONObject event=new JSONObject(input.readUTF());
                    events.event(event.getString("kind"),event.optString("text"));
                }
            } catch (Exception e) { if (!stopping) events.event("error","Root 通道断开: "+e.getMessage()); }
        },"RootEvents").start();
    }
    synchronized void pin(String pin) { send("PIN "+pin); }
    synchronized void scale(int mode){send("SCALE "+mode);}
    synchronized void rescan(){send("RESCAN");}
    synchronized void control(boolean enabled){send("CONTROL "+(enabled?1:0));}
    private void send(String command) {
        try { if (commands!=null) { commands.writeUTF(command); commands.flush(); } }
        catch (IOException e) { if (!stopping) events.event("error",e.getMessage()); }
    }
    synchronized void stop() {
        stopping=true; send("STOP");
        // EOF is the daemon's watchdog: it exits if the owning host process disappears.
        try { if (socket!=null) socket.close(); } catch (IOException ignored) {}
        try { if (server!=null) server.close(); } catch (IOException ignored) {}
        if (process!=null) process.destroy();
    }
    static String quote(String s) { return "'"+s.replace("'","'\\''")+"'"; }
}
