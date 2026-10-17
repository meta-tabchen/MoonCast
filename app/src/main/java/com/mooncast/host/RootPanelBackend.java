package com.mooncast.host;
import android.content.Context;import android.net.*;import java.io.*;import java.util.UUID;import java.util.concurrent.*;
/** Dedicated Root panel helper. It has no media/input/network listener capabilities. */
final class RootPanelBackend implements AutoCloseable {
 private final Context context;private LocalServerSocket server;private LocalSocket socket;private DataOutputStream commands;
 private volatile boolean closed;private int sequence;private final ConcurrentHashMap<Integer,CompletableFuture<Boolean>> replies=new ConcurrentHashMap<>();
 RootPanelBackend(Context context){this.context=context;}
 void start(){new Thread(()->{
  try{
   String endpoint="mooncast-panel-"+UUID.randomUUID();server=new LocalServerSocket(endpoint);
   String command="CLASSPATH="+RootBackend.quote(context.getApplicationInfo().sourceDir)+" /system/bin/app_process / com.mooncast.host.RootPanelDaemon "+RootBackend.quote(endpoint);
   Process process=new ProcessBuilder("su","-c",command).redirectErrorStream(true).start();
   new Thread(()->{try(InputStream input=process.getInputStream()){byte[] b=new byte[1024];while(input.read(b)!=-1){/* no identifiers or privileged output retained */}}catch(IOException ignored){}},"RootPanelOutput").start();
   LocalSocket accepted=server.accept();if(closed||accepted.getPeerCredentials().getUid()!=0){accepted.close();return;}
   socket=accepted;synchronized(this){commands=new DataOutputStream(accepted.getOutputStream());}
   DataInputStream input=new DataInputStream(accepted.getInputStream());
   while(!closed){String[] response=input.readUTF().split(" ");if(response.length==2){CompletableFuture<Boolean> waiting=replies.get(Integer.parseInt(response[0]));if(waiting!=null)waiting.complete("1".equals(response[1]));}}
  }catch(Exception ignored){}finally{close();}
 },"RootPanelChannel").start();}
 boolean ended(){return closed;}
 synchronized boolean ready(){return !closed&&commands!=null;}
 boolean power(boolean off){
  CompletableFuture<Boolean> future=new CompletableFuture<>();int token;
  synchronized(this){if(!ready())return false;token=++sequence;replies.put(token,future);try{commands.writeUTF((off?"OFF ":"ON ")+token);commands.flush();}catch(IOException failure){replies.remove(token);return false;}}
  try{return future.get(2,TimeUnit.SECONDS);}catch(Exception failure){return false;}finally{replies.remove(token);}
 }
 @Override public synchronized void close(){if(closed)return;closed=true;try{if(commands!=null){commands.writeUTF("STOP");commands.flush();}}catch(IOException ignored){}try{if(socket!=null)socket.close();}catch(IOException ignored){}try{if(server!=null)server.close();}catch(IOException ignored){}for(CompletableFuture<Boolean> reply:replies.values())reply.complete(false);replies.clear();commands=null;}
}
