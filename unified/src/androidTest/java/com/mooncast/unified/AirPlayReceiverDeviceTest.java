/* SPDX-License-Identifier: GPL-3.0-only */
package com.mooncast.unified;
import android.app.*;import android.content.*;import android.os.*;import android.graphics.SurfaceTexture;
import android.opengl.*;import android.view.Surface;import java.nio.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicInteger;
import com.mooncast.tv.airplay.AirPlayEngine;import com.mooncast.tv.airplay.ReceiverDiagnostics;
/** Real loopback AirPlay handshake/native callbacks/MediaCodec/GL pixels and process survival.
 * Generated media only; no LAN discovery, screen capture, physical TV or audible audio claim. */
public final class AirPlayReceiverDeviceTest {
 private static final int WIDTH=160,HEIGHT=96;
 private final Instrumentation runner;private final Bundle arguments;
 private volatile Messenger sender;private volatile String senderError;private volatile int stopped,sentFrames,sessionToken;private volatile boolean stopping;private volatile String senderStats="";
 private final AtomicInteger recoveries=new AtomicInteger();
 private com.mooncast.tv.TvActivity tvActivity;private android.view.TextureView tvTexture;
 private boolean tvMode(){return "tv-ui".equals(arguments.getString("surface","reuse"));}
 public AirPlayReceiverDeviceTest(Instrumentation runner,Bundle arguments){this.runner=runner;this.arguments=arguments;}
 public void run(){Bundle result=new Bundle();int resultCode=Activity.RESULT_CANCELED;AirPlayEngine engine=null;PixelSink sink=null;ServiceConnection connection=null;StringBuilder progress=new StringBuilder();
  try{
   if(!android.os.Build.FINGERPRINT.contains("generic")&&!android.os.Build.MODEL.contains("sdk"))throw new AssertionError("Disposable emulator only");
   int pid=android.os.Process.myPid();ReceiverDiagnostics.initialize(runner.getTargetContext());
   if(tvMode()){
    tvActivity=(com.mooncast.tv.TvActivity)runner.startActivitySync(new Intent(runner.getTargetContext(),com.mooncast.tv.TvActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));runner.waitForIdleSync();
    engine=(AirPlayEngine)tvField("airplay");tvTexture=(android.view.TextureView)tvField("mirrorTexture");waitTvReady(engine);
   }else{sink=new PixelSink();
   engine=new AirPlayEngine(runner.getTargetContext(),new AirPlayEngine.Listener(){
    public void onStatus(AirPlayEngine.Status s,String m){}public void onVideoSize(int w,int h){}
    public void onVideoPlay(String location,float position){}public void onVideoScrub(float v){}public void onVideoRate(float v){}public void onVideoStop(){}
    public void onRecoveryRequired(String reason){recoveries.incrementAndGet();}
   });engine.setWatchdogTimeoutSeconds(15);engine.setSurface(sink.surface);}
   CountDownLatch bound=new CountDownLatch(1);
   connection=new ServiceConnection(){public void onServiceConnected(ComponentName name,IBinder binder){sender=new Messenger(binder);bound.countDown();}public void onServiceDisconnected(ComponentName name){senderError="sender-process-lost";}};
   require(runner.getTargetContext().bindService(new Intent().setClassName(runner.getTargetContext().getPackageName(),"com.mooncast.host.AirPlayFixtureSender"),connection,Context.BIND_AUTO_CREATE),"sender bind");
   require(bound.await(20,TimeUnit.SECONDS),"sender ready");
   long frames=0;
   for(int cycle=0;cycle<2;cycle++){
    sessionToken=cycle+1;stopping=false;senderError=null;senderStats="";sentFrames=0;recoveries.set(0);
    if(cycle>0&&!tvMode()&&"fresh".equals(arguments.getString("surface","reuse"))){engine.setSurface(null);sink.close();sink=new PixelSink();engine.setSurface(sink.surface);}
    long priorTextureTimestamp=tvMode()?0:sink.texture.getTimestamp();
    if(tvMode()){if(cycle>0){tvAction("startReceiver");waitTvReady(engine);}}else{sink.clearFrameSignal();engine.start("MoonCast generated test",640,360,30,false);}
    int nativeGeneration=engineGeneration(engine);
    Bundle data=new Bundle();data.putInt("port",engine.getPort());data.putByteArray("frame",keyframe(cycle));send(1,data);
    long until=SystemClock.elapsedRealtime()+45000,lastSample=0,firstOutputAt=0;boolean pixels=false,probeAttempted=false;ArrayDeque<String> samples=new ArrayDeque<>();
    while(SystemClock.elapsedRealtime()<until){
     require(engineGeneration(engine)==nativeGeneration,"receiver generation stays stable during decode");
     if(senderError!=null){progress.append(String.join("\n",samples)).append("\nsender=").append(senderStats).append("\n");throw new AssertionError(senderError);}
     if(tvMode()){pixels=tvPixels(cycle);}else if(sink.available.get()>0){ByteBuffer rendered=sink.readPixels();if(sink.texture.getTimestamp()>priorTextureTimestamp){pixels=expectedPixels(rendered,cycle);}}
     if(firstOutputAt==0&&engine.getDecodedFrames()>0)firstOutputAt=SystemClock.elapsedRealtime();
     if(!tvMode()&&!probeAttempted&&Boolean.parseBoolean(arguments.getString("acquireWithoutSignal","false"))&&firstOutputAt!=0&&SystemClock.elapsedRealtime()-firstOutputAt>=500){probeAttempted=true;ByteBuffer rendered=sink.readPixels();if(sink.texture.getTimestamp()>priorTextureTimestamp){pixels=expectedPixels(rendered,cycle);}}
     long now=SystemClock.elapsedRealtime();if(now-lastSample>=1000){lastSample=now;AirPlayEngine.RuntimeStats sample=engine.getRuntimeStats();String row="cycle="+cycle+" receivedVcl="+sample.receivedVclCallbacks+" decoded="+sample.decodedFrames+" dropped="+sample.droppedFrames+" surfaceSignals="+(tvMode()?(Boolean.TRUE.equals(tvField("mirrorHasFrame"))?1:0):sink.available.get())+" textureNs="+(tvMode()?0:sink.texture.getTimestamp())+" priorTextureNs="+priorTextureTimestamp+" pixels="+pixels+" probeRan="+probeAttempted+" sender="+senderStats;android.util.Log.i("MoonCastReceiverFixture",row);samples.addLast(row);if(samples.size()>12)samples.removeFirst();}
     if(pixels&&engine.getDecodedFrames()>=20)break;SystemClock.sleep(50);
    }
    progress.append("cycle=").append(cycle).append(" surface=").append(arguments.getString("surface","reuse")).append(" sent=").append(sentFrames).append(" decoded=").append(engine.getDecodedFrames()).append(" surfaceSignals=").append(tvMode()?(Boolean.TRUE.equals(tvField("mirrorHasFrame"))?1:0):sink.available.get()).append(" pixels=").append(pixels).append(" probeRan=").append(probeAttempted).append(" recoveries=").append(tvMode()?-1:recoveries.get()).append('\n');
    if(!pixels||engine.getDecodedFrames()<20)progress.append(String.join("\n",samples)).append('\n');
    require(pixels&&engine.getDecodedFrames()>=20,"actual received and decoded frames");
    AirPlayEngine.RuntimeStats stats=engine.getRuntimeStats();
    require(stats.decodedWidth==WIDTH&&stats.decodedHeight==HEIGHT,"actual SPS dimensions distinct from request");
    long before=engine.getDecodedFrames(),hold=SystemClock.elapsedRealtime()+20000;
    while(SystemClock.elapsedRealtime()<hold){if(tvMode())require(tvPixels(cycle),"actual TV TextureView keeps current-cycle pixels");else if(sink.available.get()>0)require(expectedPixels(sink.readPixels(),cycle),"current-cycle generated pixels during hold");require(senderError==null,"sender alive");require(engineGeneration(engine)==nativeGeneration,"no receiver restart during valid static video");if(!tvMode())require(recoveries.get()==0,"no watchdog recovery during valid static video");SystemClock.sleep(100);}
    require(engine.getDecodedFrames()>before,"decode continues beyond watchdog interval");frames+=engine.getDecodedFrames();progress.append("static-hold decoded=").append(engine.getDecodedFrames()).append(" recovery=").append(tvMode()?-1:recoveries.get()).append('\n');
    int wasStopped=stopped;stopping=true;send(2,new Bundle());long stopDeadline=SystemClock.elapsedRealtime()+20000;
    while(stopped==wasStopped&&senderError==null&&SystemClock.elapsedRealtime()<stopDeadline)SystemClock.sleep(50);
    require(senderError==null,"sender cleanup succeeds: "+senderError);
    require(stopped>wasStopped,"sender disconnect completes");if(tvMode()){tvAction("stopReceiver");tvBarrier();}else engine.stop();require(!engine.hasVideoReleaseFailure(),"receiver decoder release confirmed");SystemClock.sleep(1500);
    require(android.os.Process.myPid()==pid,"receiver survives native callback thread exit");require(!engine.isRunning(),"receiver stopped");
   }
   result.putString("stream","AIRPLAY_RECEIVER_SESSION_PASSED cycles=2 decoded="+frames+" scope=loopback-generated-media-native-transport-and-decode surface="+arguments.getString("surface","reuse")+"\n"+progress+ReceiverDiagnostics.report()+"\n");
   resultCode=Activity.RESULT_OK;
  }catch(Throwable failure){result.putString("stream","AIRPLAY_RECEIVER_SESSION_FAILED\n"+android.util.Log.getStackTraceString(failure)+"\n"+progress+ReceiverDiagnostics.report());resultCode=Activity.RESULT_CANCELED;}
  finally{
   stopping=true;ArrayList<String> cleanup=new ArrayList<>();
   try{if(sender!=null)send(2,new Bundle());}catch(Throwable failure){cleanup.add("sender final stop: "+android.util.Log.getStackTraceString(failure));}
   try{if(tvMode()&&tvActivity!=null){tvAction("stopReceiver");tvBarrier();}if(engine!=null){engine.stop();require(!engine.hasVideoReleaseFailure(),"decoder release confirmed during final cleanup");}}catch(Throwable failure){cleanup.add("receiver final stop: "+android.util.Log.getStackTraceString(failure));}
   if(connection!=null)try{runner.getTargetContext().unbindService(connection);}catch(Throwable failure){cleanup.add("sender unbind: "+failure.getClass().getName());}
   if(sink!=null&&(engine==null||!engine.hasVideoReleaseFailure()))try{sink.close();}catch(Throwable failure){cleanup.add("surface final release: "+android.util.Log.getStackTraceString(failure));}
   if(tvActivity!=null)try{runner.runOnMainSync(tvActivity::finish);}catch(Throwable failure){cleanup.add("TV activity finish: "+failure.getClass().getName());}
   if(!cleanup.isEmpty()){resultCode=Activity.RESULT_CANCELED;result.putString("stream",result.getString("stream","").replace("AIRPLAY_RECEIVER_SESSION_PASSED","AIRPLAY_RECEIVER_SESSION_FAILED")+"\nCleanup failures (primary outcome preserved):\n"+String.join("\n",cleanup));}
  }
  runner.finish(resultCode,result);
 }
 private int engineGeneration(AirPlayEngine engine)throws Exception{java.lang.reflect.Field f=AirPlayEngine.class.getDeclaredField("generation");f.setAccessible(true);return f.getInt(engine);}
 private Object tvField(String name)throws Exception{java.lang.reflect.Field f=com.mooncast.tv.TvActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(tvActivity);}
 private void tvAction(String name)throws Exception{java.lang.reflect.Method method=com.mooncast.tv.TvActivity.class.getDeclaredMethod(name);method.setAccessible(true);Throwable[] error={null};runner.runOnMainSync(()->{try{method.invoke(tvActivity);}catch(Throwable failure){error[0]=failure;}});if(error[0]!=null)throw new AssertionError("TV lifecycle action "+name,error[0]);}
 private void tvBarrier()throws Exception{((ExecutorService)tvField("networkWorker")).submit(()->{}).get(30,TimeUnit.SECONDS);}
 private void waitTvReady(AirPlayEngine engine)throws Exception{long end=SystemClock.elapsedRealtime()+60000;while(SystemClock.elapsedRealtime()<end){require(!tvActivity.isFinishing()&&!tvActivity.isDestroyed(),"TV Activity survives");boolean[] available={false};runner.runOnMainSync(()->available[0]=tvTexture.isAvailable());if(engine.isRunning()&&engine.getPort()>0&&available[0])return;SystemClock.sleep(100);}throw new AssertionError("Actual TV receiver startup/TextureView unavailable: "+ReceiverDiagnostics.report());}
 private boolean tvPixels(int cycle)throws Exception{if(!Boolean.TRUE.equals(tvField("mirrorHasFrame")))return false;android.graphics.Bitmap[] bitmap={null};runner.runOnMainSync(()->bitmap[0]=tvTexture.getBitmap(WIDTH,HEIGHT));android.graphics.Bitmap b=bitmap[0];if(b==null)return false;int left=b.getPixel(WIDTH/4,HEIGHT/2),right=b.getPixel(WIDTH*3/4,HEIGHT/2);b.recycle();int red=cycle==0?left:right,blue=cycle==0?right:left;return android.graphics.Color.red(red)>150&&android.graphics.Color.blue(red)<90&&android.graphics.Color.blue(blue)>150&&android.graphics.Color.red(blue)<90;}
 private void send(int what,Bundle data)throws RemoteException{
  Message message=Message.obtain();message.what=what;data.putInt("token",sessionToken);message.setData(data);
  message.replyTo=new Messenger(new Handler(Looper.getMainLooper(),reply->{Bundle b=reply.getData();if(b.getInt("token")!=sessionToken)return true;
   String event=b.getString("event","");if("failure".equals(event))senderError=b.getString("failure");
   if("disconnected".equals(event)&&!stopping)senderError="unexpected-sender-disconnect";
   if("stopped".equals(event))stopped++;if("stats".equals(event)){sentFrames=b.getInt("frames");senderStats=b.getString("stats","");}return true;
  }));sender.send(message);
 }
 private byte[] keyframe(int cycle)throws Exception{
  byte[] bytes;try(var input=runner.getContext().getAssets().open("gamestream-renderer/"+(cycle==0?"red-blue-160x96.h264":"blue-red-160x96.h264.base64"))){java.io.ByteArrayOutputStream all=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[4096];int count;while((count=input.read(buffer))!=-1)all.write(buffer,0,count);bytes=all.toByteArray();}if(cycle!=0)bytes=android.util.Base64.decode(bytes,android.util.Base64.DEFAULT);
  java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();int found=0;
  for(int at=0;at<bytes.length;){int prefix=prefix(bytes,at);require(prefix>0,"Annex-B fixture");int end=at+prefix+1;while(end<bytes.length&&prefix(bytes,end)==0)end++;int type=bytes[at+prefix]&31;if(type==7||type==8||type==5){out.write(bytes,at,end-at);found++;}at=end;}
  require(found==3,"SPS/PPS/IDR");return out.toByteArray();
 }
 private static int prefix(byte[] b,int at){if(at+3<=b.length&&b[at]==0&&b[at+1]==0){if(b[at+2]==1)return 3;if(at+4<=b.length&&b[at+2]==0&&b[at+3]==1)return 4;}return 0;}
 private static boolean expectedPixels(ByteBuffer p,int cycle){int left=(HEIGHT/2*WIDTH+WIDTH/4)*4,right=(HEIGHT/2*WIDTH+WIDTH*3/4)*4;int red=cycle==0?left:right,blue=cycle==0?right:left;return (p.get(red)&255)>150&&(p.get(red+1)&255)<90&&(p.get(red+2)&255)<90&&(p.get(blue+2)&255)>150&&(p.get(blue)&255)<90&&(p.get(blue+1)&255)<90;}
 private static void require(boolean value,String name){if(!value)throw new AssertionError(name);}
    /** Off-screen EGL consumer: pixels are observed from the actual decoder's Surface. */
    private static final class PixelSink implements AutoCloseable {
        private static int liveSinks;
        final AtomicInteger available = new AtomicInteger();
        final EGLDisplay display;
        final EGLContext context;
        final EGLSurface pbuffer;
        final int textureId, program;
        final SurfaceTexture texture;
        final Surface surface;
        boolean externallyRetired, closed;
        PixelSink() {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            int[] version = new int[2];
            require(EGL14.eglInitialize(display, version, 0, version, 1), "EGL initialize");
            EGLConfig[] configs = new EGLConfig[1]; int[] count = new int[1];
            require(EGL14.eglChooseConfig(display, new int[]{EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_NONE}, 0, configs, 0, 1, count, 0)
                    && count[0] == 1, "EGL config");
            context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
                    new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE}, 0);
            pbuffer = EGL14.eglCreatePbufferSurface(display, configs[0],
                    new int[]{EGL14.EGL_WIDTH, WIDTH, EGL14.EGL_HEIGHT, HEIGHT, EGL14.EGL_NONE}, 0);
            makeCurrent();
            int[] names = new int[1]; GLES20.glGenTextures(1, names, 0); textureId = names[0];
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            texture = new SurfaceTexture(textureId); texture.setDefaultBufferSize(WIDTH, HEIGHT);
            texture.setOnFrameAvailableListener(ignored -> available.incrementAndGet(), new Handler(Looper.getMainLooper()));
            surface = new Surface(texture);
            int vertex = shader(GLES20.GL_VERTEX_SHADER, "attribute vec2 p; attribute vec2 uv; uniform mat4 m; varying vec2 v; "
                    + "void main(){gl_Position=vec4(p,0.,1.); v=(m*vec4(uv,0.,1.)).xy;}");
            int fragment = shader(GLES20.GL_FRAGMENT_SHADER, "#extension GL_OES_EGL_image_external : require\n"
                    + "precision mediump float; uniform samplerExternalOES s; varying vec2 v; void main(){gl_FragColor=texture2D(s,v);}");
            program = GLES20.glCreateProgram(); GLES20.glAttachShader(program, vertex); GLES20.glAttachShader(program, fragment);
            GLES20.glLinkProgram(program); int[] status = new int[1]; GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0);
            require(status[0] == GLES20.GL_TRUE, "GLES program link: " + GLES20.glGetProgramInfoLog(program));
            GLES20.glDeleteShader(vertex); GLES20.glDeleteShader(fragment);
            liveSinks++;
        }
        void clearFrameSignal() { available.set(0); }
        void makeCurrent() { require(EGL14.eglMakeCurrent(display, pbuffer, pbuffer, context), "EGL make current"); }
        ByteBuffer readPixels() {
            makeCurrent(); texture.updateTexImage(); float[] matrix = new float[16]; texture.getTransformMatrix(matrix);
            GLES20.glViewport(0, 0, WIDTH, HEIGHT); GLES20.glUseProgram(program);
            FloatBuffer vertices = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            vertices.put(new float[]{-1,-1,0,0, 1,-1,1,0, -1,1,0,1, 1,1,1,1}); vertices.position(0);
            int p = GLES20.glGetAttribLocation(program, "p"), uv = GLES20.glGetAttribLocation(program, "uv");
            GLES20.glEnableVertexAttribArray(p); GLES20.glVertexAttribPointer(p, 2, GLES20.GL_FLOAT, false, 16, vertices);
            vertices.position(2); GLES20.glEnableVertexAttribArray(uv); GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, 16, vertices);
            GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "m"), 1, false, matrix, 0);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "s"), 0); GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            ByteBuffer pixels = ByteBuffer.allocateDirect(WIDTH * HEIGHT * 4).order(ByteOrder.nativeOrder());
            GLES20.glReadPixels(0, 0, WIDTH, HEIGHT, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixels);
            require(GLES20.glGetError() == GLES20.GL_NO_ERROR, "GLES pixel readback"); pixels.rewind(); return pixels;
        }
        @Override public void close() {
            if (closed) return; closed = true; makeCurrent();
            if (!externallyRetired) { surface.release(); texture.release(); }
            GLES20.glDeleteProgram(program); GLES20.glDeleteTextures(1, new int[]{textureId}, 0);
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
            EGL14.eglDestroySurface(display, pbuffer); EGL14.eglDestroyContext(display, context);
            // Both sinks share EGL_DEFAULT_DISPLAY; terminate only after all test sinks finish.
            if (--liveSinks == 0) { EGL14.eglTerminate(display); EGL14.eglReleaseThread(); }
        }
        private static int shader(int type, String source) {
            int result = GLES20.glCreateShader(type); GLES20.glShaderSource(result, source); GLES20.glCompileShader(result);
            int[] status = new int[1]; GLES20.glGetShaderiv(result, GLES20.GL_COMPILE_STATUS, status, 0);
            require(status[0] == GLES20.GL_TRUE, "GLES shader: " + GLES20.glGetShaderInfoLog(result)); return result;
        }
        private static void require(boolean okay, String message) { if (!okay) throw new AssertionError(message); }
    }
}
