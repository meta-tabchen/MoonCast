package com.mooncast.host;
import android.content.Context;import android.net.*;import android.os.*;import java.io.*;import java.lang.reflect.*;
/** Independent, owner-bound restoration helper. No capture, input, certificates or network listeners. */
public final class RootPanelDaemon {
 public static void main(String[] args){
  DisplayPowerLease lease=null;LocalSocket socket=null;java.util.concurrent.ScheduledExecutorService timer=null;
  try{
   if(args.length!=1||android.os.Process.myUid()!=0)throw new SecurityException("Root panel owner required");
   if(Looper.myLooper()==null)Looper.prepareMainLooper();Class<?> type=Class.forName("android.app.ActivityThread");Method system=type.getDeclaredMethod("systemMain");system.setAccessible(true);Object thread=system.invoke(null);Context context=(Context)type.getDeclaredMethod("getSystemContext").invoke(thread);
   lease=new DisplayPowerLease(new PhysicalDisplayPower(context));DisplayPowerLease owned=lease;
   timer=java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r->new Thread(r,"PanelLease"));timer.scheduleWithFixedDelay(()->{try{owned.tick(SystemClock.elapsedRealtime());}catch(Exception ignored){}},1,1,java.util.concurrent.TimeUnit.SECONDS);
   socket=new LocalSocket();socket.connect(new LocalSocketAddress(args[0]));DataInputStream commands=new DataInputStream(socket.getInputStream());DataOutputStream replies=new DataOutputStream(socket.getOutputStream());
   for(;;){String command=commands.readUTF();if(command.equals("STOP"))break;if(!command.matches("(ON|OFF) [0-9]+"))continue;String[] pieces=command.split(" ");boolean ok=false;
    try{if(pieces[0].equals("OFF"))owned.renew(SystemClock.elapsedRealtime(),30000);else owned.restore();ok=true;}catch(Exception ignored){}
    replies.writeUTF(pieces[1]+" "+(ok?1:0));replies.flush();
   }
  }catch(Exception ignored){}
  finally{
   // EOF/Stop first closes the only command endpoint; it can never renew/off again.
   try{if(socket!=null)socket.close();}catch(IOException ignored){}if(timer!=null)timer.shutdownNow();
   // Bounded recovery-only lifetime. Persistent vendor failure is UNSUPPORTED, not success.
   long deadline=SystemClock.elapsedRealtime()+60000;
   while(lease!=null&&lease.pending()&&SystemClock.elapsedRealtime()<deadline){try{lease.restore();}catch(Exception ignored){}if(lease.pending())SystemClock.sleep(1000);}
   System.exit(lease!=null&&lease.pending()?2:0);
  }
 }
}
