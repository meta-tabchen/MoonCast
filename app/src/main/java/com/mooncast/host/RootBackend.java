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
    private final java.util.concurrent.ConcurrentHashMap<Integer,java.util.concurrent.CompletableFuture<Boolean>> panelReplies=new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicInteger panelSequence=new java.util.concurrent.atomic.AtomicInteger();
    RootBackend(Context c,Events e) { context=c; events=e; }
    synchronized void start(String name,boolean hevc,int scaleMode,boolean control,int frameLimit,boolean watchParty,boolean panelOff) throws Exception {
        if(stopping) throw new IOException(PhoneMessages.message(R.string.phone_root_stopped));
        String endpoint="mooncast-"+UUID.randomUUID();
        server=new LocalServerSocket(endpoint);
        String command="CLASSPATH="+quote(context.getApplicationInfo().sourceDir)
            +" /system/bin/app_process / com.mooncast.host.RootDaemon "
            +quote(endpoint)+" "+quote(context.getFilesDir().getPath())+" "
            +quote(context.getApplicationInfo().nativeLibraryDir)+" "+quote(name)+" "+hevc+" "+scaleMode+" "+control+" "+frameLimit+" "+watchParty+" "+panelOff;
        process=new ProcessBuilder("su","-c",command).redirectErrorStream(true).start();
        new Thread(() -> {
            try (BufferedReader reader=new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line; while ((line=reader.readLine())!=null) android.util.Log.i("MoonCastRoot",line);
                int code=process.waitFor();
                if (!stopping) events.event("error",PhoneMessages.message(R.string.phone_root_exited , code));
            } catch (Exception e) { if (!stopping) events.event("error",e.getMessage()); }
        },"RootOutput").start();
        new Thread(() -> {
            try {
                LocalSocket accepted=server.accept();
                if (accepted.getPeerCredentials().getUid()!=0) { accepted.close(); throw new IOException(PhoneMessages.message(R.string.phone_root_connection_rejected)); }
                socket=accepted;
                synchronized (this) { commands=new DataOutputStream(socket.getOutputStream()); }
                DataInputStream input=new DataInputStream(socket.getInputStream());
                while (!stopping) {
                    JSONObject event=new JSONObject(input.readUTF());
                    if("panelResult".equals(event.getString("kind"))){JSONObject response=new JSONObject(event.optString("text"));var waiting=panelReplies.get(response.getInt("token"));if(waiting!=null)waiting.complete(response.getBoolean("success"));}
                    else events.event(event.getString("kind"),event.optString("text"));
                }
            } catch (Exception e) { if (!stopping) events.event("error",PhoneMessages.message(R.string.phone_root_channel_lost , e.getMessage())); }
        },"RootEvents").start();
    }
    synchronized void disconnect(long id){send("DISCONNECT "+id);}
    synchronized void pin(String pin) { send("PIN "+pin); }
    synchronized void scale(int mode){send("SCALE "+mode);}
    synchronized boolean ready(){return commands!=null&&!stopping;}
    void panelOff(){if(!panelPower(true))throw new IllegalStateException("Root panel request failed");}
    boolean panelPower(boolean off){
        if(!ready())return false;int token=panelSequence.incrementAndGet();var response=new java.util.concurrent.CompletableFuture<Boolean>();panelReplies.put(token,response);
        synchronized(this){send("PANEL_SET "+token+" "+(off?1:0));}
        try{return response.get(2,java.util.concurrent.TimeUnit.SECONDS);}catch(Exception e){return false;}finally{panelReplies.remove(token);}
    }
    synchronized void pause(boolean value){send(value?"PAUSE 1":"PAUSE 0");}
    synchronized void panelHeartbeat(){send("PANEL_HEARTBEAT");}
    synchronized void restoreDisplay(){send("RESTORE_DISPLAY");}
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
        // Let the root owner watchdog restore the physical panel before terminating.
        Process owned=process;if(owned!=null)new Thread(()->{try{if(!owned.waitFor(3,java.util.concurrent.TimeUnit.SECONDS))owned.destroy();}catch(InterruptedException e){Thread.currentThread().interrupt();}},"RootStop").start();
    }
    static String quote(String s) { return "'"+s.replace("'","'\\''")+"'"; }
}
