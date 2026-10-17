package com.mooncast.host;
import android.content.*;import android.os.*;
/** The recovery owner lives outside :host, so a mandatory capture-process exit cannot remove its guard. */
final class PanelRecoveryClient implements AutoCloseable {
 private final Context context;private final boolean root;private final Binder owner=new Binder();
 private IPanelRecovery remote;private boolean closed,bound;
 private final ServiceConnection connection=new ServiceConnection(){
  public void onServiceConnected(ComponentName n,IBinder b){if(closed)return;remote=IPanelRecovery.Stub.asInterface(b);try{remote.initialize(owner,root);}catch(RemoteException ignored){}}
  public void onServiceDisconnected(ComponentName n){remote=null;}
 };
 PanelRecoveryClient(Context context,boolean root){this.context=context;this.root=root;bound=context.bindService(new Intent(context,PanelRecoveryService.class),connection,Context.BIND_AUTO_CREATE);}
 void requestOff(){if(remote!=null&&!closed)try{remote.requestOff();}catch(RemoteException ignored){}else android.widget.Toast.makeText(context,R.string.controls_panel_unavailable,android.widget.Toast.LENGTH_LONG).show();}
 void disconnected(){if(remote!=null)try{remote.disconnected();}catch(RemoteException ignored){}}
 void restore(){if(remote!=null)try{remote.restore();}catch(RemoteException ignored){}}
 @Override public void close(){if(closed)return;closed=true;if(remote!=null)try{remote.ownerStopped();}catch(RemoteException ignored){}remote=null;if(bound){context.unbindService(connection);bound=false;}}
}
