package com.mooncast.host;
import android.app.Service;import android.content.Intent;import android.os.*;import android.opengl.*;import android.view.Surface;
import java.io.*;import java.nio.file.*;import java.util.*;import java.util.concurrent.*;import org.json.*;
import io.github.jqssun.displaymirror.sunshine.*;
/** Generated Surface producer -> actual production Sunshine encoder and transport. Disposable emulator only. */
public final class GameStreamHostFixture extends Service implements SunshineServer.Listener {
 private final HandlerThread glThread=new HandlerThread("GeneratedEncoderSurface");private Handler gl;
 private EGLDisplay display;private EGLContext context;private EGLConfig config;
 private final Map<Long,Window> windows=new LinkedHashMap<>();private final ConcurrentHashMap<Long,Stats> active=new ConcurrentHashMap<>();
 private record Window(EGLSurface surface,int width,int height){}private static final class Stats {volatile int width,height,fps;volatile int samples;volatile long lastStatsElapsed;volatile double encodedFps;Stats(int w,int h,int f){width=w;height=h;fps=f;}}
 private volatile String error="";private boolean initialized,audio;private volatile boolean closing;private volatile int ended;
 private final Messenger binder=new Messenger(new Handler(Looper.getMainLooper(),m->{
  Bundle b=new Bundle();b.putLong("nonce",m.getData().getLong("nonce"));
  if(m.what==2){close();return true;}
  if(m.what==3)b.putBoolean("disconnected",NativeSessions.disconnect(m.getData().getLong("session")));
  b.putInt("pid",android.os.Process.myPid());b.putString("error",error);b.putInt("ended",ended);
  long[] ids=new long[active.size()];int at=0;for(long id:active.keySet())if(at<ids.length)ids[at++]=id;b.putLongArray("sessions",Arrays.copyOf(ids,at));
  for(long id:active.keySet()){Stats s=active.get(id);if(s!=null){b.putInt("samples-"+id,s.samples);b.putDouble("fps-"+id,s.encodedFps);b.putInt("targetFps-"+id,s.fps);b.putLong("lastStatsElapsed-"+id,s.lastStatsElapsed);b.putInt("width-"+id,s.width);b.putInt("height-"+id,s.height);b.putBoolean("active-"+id,NativeSessions.active(id));}}
  if(m.replyTo!=null)try{Message reply=Message.obtain();reply.setData(b);m.replyTo.send(reply);}catch(RemoteException ignored){}return true;
 }));
 @Override public void onCreate(){super.onCreate();glThread.start();gl=new Handler(glThread.getLooper());}
 @Override public IBinder onBind(Intent intent){if(!initialized){initialized=true;String run=intent.getStringExtra("run");if(run==null||!run.matches("[a-f0-9]{32}")||!Build.FINGERPRINT.contains("generic")&&!Build.MODEL.contains("sdk")){error="Disposable emulator fixture required";return binder.getBinder();}new Thread(()->startHost(run),"GeneratedHost").start();}return binder.getBinder();}
 private void startHost(String run){try{
  File base=new File(getCacheDir(),"native-generated-test/"+run),host=new File(base,"host");if(!host.mkdirs())throw new IOException("fresh fixture directory required");HostIdentity.ensure(host);JSONArray devices=new JSONArray();
  for(String name:new String[]{"one","two"}){File client=new File(base,name);if(!client.mkdir())throw new IOException("fresh client directory required");HostIdentity.ensure(client);devices.put(new JSONObject().put("name","generated "+name).put("uuid",UUID.randomUUID().toString()).put("cert",new String(Files.readAllBytes(new File(client,"host-cert.pem").toPath()),java.nio.charset.StandardCharsets.US_ASCII)));}
  File pairing=new File(host,"paired-clients.json");Files.write(pairing.toPath(),new JSONObject().put("root",new JSONObject().put("uniqueid",UUID.randomUUID().toString()).put("named_devices",devices)).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
  SunshineServer.listener=this;SunshineServer.setSunshineName("Generated fixture");SunshineServer.setCertPath(new File(host,"host-cert.pem").getPath());SunshineServer.setPkeyPath(new File(host,"host-key.pem").getPath());SunshineServer.setFileStatePath(pairing.getPath());SunshineServer.setHevcSupported(false);if(!NativeSessions.available())throw new IllegalStateException("Native session bridge unavailable");SunshineServer.start();
 }catch(Throwable failure){error=failure.getClass().getName()+": "+failure.getMessage();}}
 public void pinRequested(){error="Unexpected pairing prompt";}public void uuid(String ignored){}public void error(String message){error=message;}
 public synchronized void createDisplay(long id,int width,int height,int fps,int packet,Surface surface,boolean audioRequested){
  if(closing)return;active.put(id,new Stats(width,height,fps));
  // Return before first swap: the native encoder starts AFTER this callback returns.
  gl.post(()->{try{initializeGl();EGLSurface window=EGL14.eglCreateWindowSurface(display,config,surface,new int[]{EGL14.EGL_NONE},0);if(window==EGL14.EGL_NO_SURFACE)throw new IllegalStateException("encoder EGL surface");windows.put(id,new Window(window,width,height));}catch(Throwable failure){error="EGL producer: "+failure+", EGL="+EGL14.eglGetError();}});
  if(!audio){audio=true;SunshineServer.startAudioRecording(new Silence(),48*Math.max(1,packet));}
 }
 public void stopDisplay(long id){CountDownLatch done=new CountDownLatch(1);gl.post(()->{try{Window w=windows.remove(id);if(w!=null){if(!EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT))error="EGL unbind failed: "+EGL14.eglGetError();if(!EGL14.eglDestroySurface(display,w.surface()))error="Encoder surface destruction failed";}}finally{active.remove(id);ended++;done.countDown();}});try{if(!done.await(3,TimeUnit.SECONDS))error="Encoder surface retirement timed out";}catch(InterruptedException failure){Thread.currentThread().interrupt();error="Encoder retirement interrupted";}}
 public void streamStats(long id,int w,int h,int targetFps,int targetKbps,double fps,double mbps){Stats s=active.get(id);if(s!=null){s.width=w;s.height=h;s.fps=targetFps;s.encodedFps=fps;s.lastStatsElapsed=SystemClock.elapsedRealtime();s.samples++;}}
 public final class Silence {public int read(float[] b,int offset,int count,int mode){Arrays.fill(b,offset,offset+count,0f);SystemClock.sleep(Math.max(1,count*1000L/96000));return count;}}
 private void initializeGl(){if(display!=null)return;display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);int[] v=new int[2];if(!EGL14.eglInitialize(display,v,0,v,1))throw new IllegalStateException("EGL initialize");EGLConfig[] configs=new EGLConfig[1];int[] n=new int[1];if(!EGL14.eglChooseConfig(display,new int[]{EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_SURFACE_TYPE,EGL14.EGL_WINDOW_BIT,EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,0x3142,1,EGL14.EGL_NONE},0,configs,0,1,n,0)||n[0]!=1)throw new IllegalStateException("recordable EGL configuration");config=configs[0];context=EGL14.eglCreateContext(display,config,EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE},0);if(context==EGL14.EGL_NO_CONTEXT)throw new IllegalStateException("EGL context: "+EGL14.eglGetError());gl.post(draw);}
 private final Runnable draw=new Runnable(){public void run(){if(closing)return;try{for(Window w:windows.values()){if(!EGL14.eglMakeCurrent(display,w.surface(),w.surface(),context))throw new IllegalStateException("EGL make current");GLES20.glViewport(0,0,w.width(),w.height());GLES20.glDisable(GLES20.GL_SCISSOR_TEST);GLES20.glClearColor(1,0,0,1);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);GLES20.glEnable(GLES20.GL_SCISSOR_TEST);GLES20.glScissor(w.width()/2,0,w.width()-w.width()/2,w.height());GLES20.glClearColor(0,0,1,1);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);EGLExt.eglPresentationTimeANDROID(display,w.surface(),System.nanoTime());if(!EGL14.eglSwapBuffers(display,w.surface()))throw new IllegalStateException("EGL swap");}}catch(Throwable failure){error="Generated Surface producer: "+failure+", EGL="+EGL14.eglGetError();}gl.postDelayed(this,33);}};
 // Session teardown is asserted separately; final fixture cleanup terminates only this
 // disposable process. exitServer is unsafe before native global initialization completes.
 private void close(){if(closing)return;closing=true;android.os.Process.killProcess(android.os.Process.myPid());}
 @Override public void onDestroy(){close();super.onDestroy();}
}
