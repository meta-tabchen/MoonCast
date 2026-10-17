package com.mooncast.host;

import android.app.*;import android.content.*;import android.graphics.Rect;import android.os.*;import android.view.*;import android.widget.*;

/** Real production control windows with explicitly synthetic session callbacks. */
public final class ControlsFixtureService extends Service {
 private CastControls controls;private boolean paused,stopped,connected=true;private int pauseCommands;private boolean oldOverlay;
 private final Messenger endpoint=new Messenger(new Handler(Looper.getMainLooper(),message->{
  Bundle data=new Bundle();data.putInt("nonce",message.getData().getInt("nonce"));
  try{
   if(message.what==1){connected=false;paused=false;controls.disconnected();}else if(message.what==2){controls.close();stopped=true;}
   data.putInt("pid",android.os.Process.myPid());data.putInt("instance",System.identityHashCode(this));data.putBoolean("paused",paused);data.putBoolean("stopped",stopped);data.putInt("pauseCommands",pauseCommands);
   android.graphics.Point physical=new android.graphics.Point();getSystemService(WindowManager.class).getDefaultDisplay().getRealSize(physical);data.putInt("screenWidth",physical.x);data.putInt("screenHeight",physical.y);data.putFloat("density",getResources().getDisplayMetrics().density);data.putFloat("fontScale",getResources().getConfiguration().fontScale);
   java.lang.reflect.Field field=CastControls.class.getDeclaredField("overlay");field.setAccessible(true);View root=(View)field.get(controls);
   data.putBoolean("overlay",root!=null);if(root!=null){bounds(data,"window",root);Rect usable=new Rect();root.getWindowVisibleDisplayFrame(usable);data.putParcelable("usable",usable);data.putBoolean("layoutRequested",root.isLayoutRequested());walk(data,root);}
  }catch(Throwable error){data.putString("failure",android.util.Log.getStackTraceString(error));}
  if(message.replyTo!=null)try{Message reply=Message.obtain();reply.setData(data);message.replyTo.send(reply);}catch(RemoteException ignored){}
  return true;
 }));
 @Override public void onCreate(){super.onCreate();oldOverlay=getSharedPreferences("cast-controls",0).getBoolean("overlay",false);getSharedPreferences("cast-controls",0).edit().putBoolean("overlay",true).commit();getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("generated-controls","Generated controls",NotificationManager.IMPORTANCE_LOW));
  controls=new CastControls(this,"generated-controls",91,new CastControls.Session(){public boolean connected(){return connected;}public boolean pause(boolean value){if(!connected)return false;paused=value;pauseCommands++;return true;}public void stop(){stopped=true;controls.close();}},false);
 }
 private void walk(Bundle data,View view){if(view instanceof ScrollView){bounds(data,"scroll",view);ScrollView scroll=(ScrollView)view;data.putInt("scrollY",scroll.getScrollY());data.putInt("scrollViewport",scroll.getHeight());data.putInt("scrollContent",scroll.getChildCount()>0?scroll.getChildAt(0).getHeight():0);}if(view instanceof Button){String text=((Button)view).getText().toString();for(int id:new int[]{R.string.controls_bubble,R.string.controls_collapse,R.string.controls_pause,R.string.controls_resume,R.string.ui_stop})if(text.equals(getString(id)))bounds(data,getResources().getResourceEntryName(id),view);}if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)walk(data,((ViewGroup)view).getChildAt(i));}
 private static void bounds(Bundle data,String key,View view){int[] where=new int[2];view.getLocationOnScreen(where);Rect full=new Rect(where[0],where[1],where[0]+view.getWidth(),where[1]+view.getHeight()),visible=new Rect();boolean shown=view.isShown()&&view.getLocalVisibleRect(visible);data.putParcelable(key,full);data.putBoolean(key+"Visible",shown&&visible.equals(new Rect(0,0,view.getWidth(),view.getHeight()))&&full.width()>0&&full.height()>0);}
 @Override public IBinder onBind(Intent intent){return endpoint.getBinder();}
 @Override public int onStartCommand(Intent intent,int flags,int id){return START_NOT_STICKY;}
 @Override public void onDestroy(){if(controls!=null)controls.close();getSystemService(NotificationManager.class).cancel(91);getSharedPreferences("cast-controls",0).edit().putBoolean("overlay",oldOverlay).commit();super.onDestroy();}
}
