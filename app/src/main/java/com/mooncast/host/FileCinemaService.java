package com.mooncast.host;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import java.net.*;
import java.util.*;

/** Separate process and notification: stopping mirroring does not stop file sharing. */
public final class FileCinemaService extends Service {
    static final int STATUS=1,STOP=2;
    private final Handler main=new Handler(Looper.getMainLooper());
    private OriginalMediaServer server;
    private int generation;
    private String name="",error="",urls="";
    private boolean stopped;
    private PowerManager.WakeLock wake;
    private final Messenger binder=new Messenger(new Handler(Looper.getMainLooper(),msg->{
        if(msg.what==STOP){shutdown();stopSelf();return true;}
        if(msg.what==STATUS && msg.replyTo!=null)try{Message reply=Message.obtain(null,STATUS);Bundle b=new Bundle();b.putBoolean("stopped",stopped);b.putString("name",name);b.putString("urls",urls);b.putString("error",error);reply.setData(b);msg.replyTo.send(reply);}catch(RemoteException ignored){}
        return true;
    }));
    @Override public IBinder onBind(Intent intent){return binder.getBinder();}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent==null || "stop".equals(intent.getAction())){shutdown();stopSelf();return START_NOT_STICKY;}
        stopped=false;
        NotificationManager notifications=getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel("files",getString(R.string.ui_original_cinema),NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,10,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,11,new Intent(this,FileCinemaService.class).setAction("stop"),PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(this,"files").setSmallIcon(R.drawable.ic_launcher).setContentTitle(getString(R.string.ui_original_cinema)).setContentText(getString(R.string.ui_file_sharing_active)).setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null,getString(R.string.ui_stop),stop).build()).build();
        if(Build.VERSION.SDK_INT>=29)startForeground(8,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);else startForeground(8,n);
        if(wake==null){wake=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"MoonCast:files");wake.acquire(6*60*60*1000L);}
        final int selected=++generation;if(server!=null){server.close();server=null;}name="";error="";urls="";
        var uri=intent.getData();boolean chinese=getResources().getConfiguration().getLocales().get(0).getLanguage().equals("zh");
        new Thread(()->{
            OriginalMediaServer candidate=null;
            try{
                if(uri==null)throw new IllegalArgumentException("No selected document");
                DocumentMedia media=new DocumentMedia(this,uri);
                // Fail before publishing a link if the document can no longer be opened.
                try(var ignored=media.open()){}
                candidate=new OriginalMediaServer(media,InetAddress.getByName("0.0.0.0"),chinese);
                OriginalMediaServer ready=candidate;String links=addresses(ready);
                main.post(()->{if(selected!=generation){ready.close();return;}server=ready;name=media.name();urls=links;});
            }catch(Exception e){if(candidate!=null)candidate.close();main.post(()->{if(selected==generation)error=getString(R.string.ui_file_open_failed)+" "+e.getClass().getSimpleName();});}
        },"OriginalMediaStart").start();
        return START_NOT_STICKY;
    }
    private static String addresses(OriginalMediaServer server) throws Exception {
        ArrayList<String> links=new ArrayList<>();var interfaces=NetworkInterface.getNetworkInterfaces();
        while(interfaces.hasMoreElements()){var network=interfaces.nextElement();if(!network.isUp() || network.isLoopback())continue;var addresses=network.getInetAddresses();while(addresses.hasMoreElements()){var address=addresses.nextElement();if(address instanceof Inet4Address && address.isSiteLocalAddress())links.add("http://"+address.getHostAddress()+":"+server.port()+server.path());}}
        return String.join("\n",links);
    }
    private void shutdown(){stopped=true;++generation;urls="";if(server!=null){server.close();server=null;}if(wake!=null){if(wake.isHeld())wake.release();wake=null;}stopForeground(STOP_FOREGROUND_REMOVE);}
    @Override public void onDestroy(){shutdown();super.onDestroy();}
}
