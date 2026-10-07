package com.mooncast.host;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.display.*;
import android.opengl.*;
import android.os.*;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Surface;
import java.nio.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** SurfaceTexture -> OpenGL ES -> encoder Surface. No full-resolution CPU readback or Bitmap copies. */
public final class VideoPipeline {
    public interface Factory { VirtualDisplay create(Surface input,int width,int height); }
    public interface Events { void message(String message); void error(String message); default void geometry(CropGeometry.Mapping mapping){} }
    private final Surface output;
    private final int outW,outH;
    private final Events events;
    private final DisplayManager manager;
    private final HandlerThread thread=new HandlerThread("MoonCastGL");
    private Handler handler;
    private volatile boolean closed;
    private boolean followsDisplay;
    private int mode,sourceW,sourceH,screenW,screenH;
    private VirtualDisplay display;
    private Surface input;
    private SurfaceTexture texture;
    private EGLDisplay egl=EGL14.EGL_NO_DISPLAY;
    private EGLContext context=EGL14.EGL_NO_CONTEXT;
    private EGLSurface window=EGL14.EGL_NO_SURFACE,home=EGL14.EGL_NO_SURFACE;
    private int program,texId,sampleTex,fbo;
    private int positionLocation,uvLocation,transformLocation;
    private final float[] transform=new float[16];
    private final FloatBuffer vertices=buffer(new float[]{-1,-1,1,-1,-1,1,1,1});
    private final FloatBuffer coords=buffer(new float[]{0,0,1,0,0,1,1,1});
    private final ByteBuffer pixels=ByteBuffer.allocateDirect(192*128*4).order(ByteOrder.nativeOrder());
    private final byte[] sample=new byte[192*128*4];
    private final AutoCropTracker tracker=new AutoCropTracker();
    private final CinemaCropTracker cinema=new CinemaCropTracker();
    private boolean haveFrame;
    private long frames,lastSubmitted,lastProbeLog;
    private final Runnable probe=new Runnable(){public void run(){
        if(closed)return;
        try{if(haveFrame && autoMode() && sourceW>sourceH && detect())render(System.nanoTime());}
        catch(Exception e){events.error("视频区域采样失败: "+e.getMessage());close();return;}
        handler.postDelayed(this,250);
    }};
    private boolean autoMode(){return mode==CropGeometry.VIDEO_FIT || mode==CropGeometry.VIDEO_FILL || (mode==CropGeometry.CINEMA && !cinema.locked());}
    private final DisplayManager.DisplayListener listener=new DisplayManager.DisplayListener(){
        @Override public void onDisplayAdded(int id){}
        @Override public void onDisplayRemoved(int id){}
        @Override public void onDisplayChanged(int id){if(id==Display.DEFAULT_DISPLAY && followsDisplay){DisplayMetrics m=metrics(manager); resizeInternal(m.widthPixels,m.heightPixels);}}
    };
    public VideoPipeline(Context c,Surface encoder,int width,int height,int mode,Events events){
        this.output=encoder;outW=width;outH=height;this.mode=mode;this.events=events;manager=c.getSystemService(DisplayManager.class);
    }
    public static DisplayMetrics metrics(DisplayManager m){DisplayMetrics result=new DisplayMetrics();m.getDisplay(Display.DEFAULT_DISPLAY).getRealMetrics(result);return result;}
    public void start(int width,int height,boolean followDisplay,Factory factory){
        followsDisplay=followDisplay;
        thread.start();handler=new Handler(thread.getLooper());
        handler.post(()->{
            if(closed)return;
            try{
                setup();setInputSize(width,height);
                texture=new SurfaceTexture(texId);texture.setDefaultBufferSize(sourceW,sourceH);
                texture.setOnFrameAvailableListener(t->frame(),handler);input=new Surface(texture);
                display=factory.create(input,sourceW,sourceH);
                if(followsDisplay)manager.registerDisplayListener(listener,handler);
                events.message("GPU 捕获 "+sourceW+"×"+sourceH+" → 编码 "+outW+"×"+outH);
                handler.post(probe);
            }catch(Exception e){events.error("GPU 初始化失败: "+e.getMessage());close();}
        });
    }
    public void setMode(int value){if(handler!=null)handler.post(()->{
        int next=Math.max(0,Math.min(CropGeometry.CINEMA,value));if(mode==next)return;
        mode=next;tracker.reset();cinema.reset();lastProbeLog=0;
        if(haveFrame && !closed)try{render(System.nanoTime());}catch(Exception e){events.error("显示模式切换失败: "+e.getMessage());}
    });}
    public void rescan(){if(handler!=null)handler.post(()->{
        tracker.reset();cinema.reset();lastProbeLog=0;
        if(haveFrame && !closed)render(System.nanoTime());
    });}
    public void resize(int width,int height){if(handler!=null)handler.post(()->resizeInternal(width,height));}
    private void setInputSize(int width,int height){
        if(width<1 || height<1)throw new IllegalArgumentException("Invalid capture size");
        screenW=width;screenH=height;
        float scale=Math.min(1f,Math.max(outW,outH)/(float)Math.max(width,height));
        sourceW=Math.max(2,Math.round(width*scale)/2*2);sourceH=Math.max(2,Math.round(height*scale)/2*2);
    }
    private void resizeInternal(int width,int height){
        if(closed || texture==null)return;
        int oldW=sourceW,oldH=sourceH;setInputSize(width,height);
        if(sourceW==oldW && sourceH==oldH)return;
        texture.setDefaultBufferSize(sourceW,sourceH);
        if(display!=null)display.resize(sourceW,sourceH,160);
        tracker.reset();cinema.reset();lastProbeLog=0;haveFrame=false;
        events.message("捕获尺寸更新: "+sourceW+"×"+sourceH);
    }
    private void setup(){
        egl=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);int[] version=new int[2];
        check(EGL14.eglInitialize(egl,version,0,version,1),"eglInitialize");
        EGLConfig[] configs=new EGLConfig[1];int[] count=new int[1];
        int[] attributes={EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,EGL14.EGL_SURFACE_TYPE,EGL14.EGL_WINDOW_BIT|EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,0x3142,1,EGL14.EGL_NONE};
        check(EGL14.eglChooseConfig(egl,attributes,0,configs,0,1,count,0) && count[0]>0,"eglChooseConfig");
        context=EGL14.eglCreateContext(egl,configs[0],EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE},0);
        check(context!=EGL14.EGL_NO_CONTEXT,"eglCreateContext");
        home=EGL14.eglCreatePbufferSurface(egl,configs[0],new int[]{EGL14.EGL_WIDTH,1,EGL14.EGL_HEIGHT,1,EGL14.EGL_NONE},0);
        window=EGL14.eglCreateWindowSurface(egl,configs[0],output,new int[]{EGL14.EGL_NONE},0);
        check(window!=EGL14.EGL_NO_SURFACE,"eglCreateWindowSurface");current(home);
        int vertex=shader(GLES20.GL_VERTEX_SHADER,"attribute vec2 aPosition; attribute vec2 aUv; uniform mat4 uTransform; varying vec2 vUv; void main(){gl_Position=vec4(aPosition,0.,1.);vUv=(uTransform*vec4(aUv,0.,1.)).xy;}");
        int fragment=shader(GLES20.GL_FRAGMENT_SHADER,"#extension GL_OES_EGL_image_external : require\nprecision mediump float; uniform samplerExternalOES uTexture; varying vec2 vUv; void main(){gl_FragColor=texture2D(uTexture,vUv);}");
        program=GLES20.glCreateProgram();GLES20.glAttachShader(program,vertex);GLES20.glAttachShader(program,fragment);GLES20.glLinkProgram(program);
        int[] success=new int[1];GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,success,0);
        if(success[0]==0)throw new IllegalStateException(GLES20.glGetProgramInfoLog(program));
        GLES20.glDeleteShader(vertex);GLES20.glDeleteShader(fragment);
        positionLocation=GLES20.glGetAttribLocation(program,"aPosition");uvLocation=GLES20.glGetAttribLocation(program,"aUv");transformLocation=GLES20.glGetUniformLocation(program,"uTransform");
        int[] names=new int[1];GLES20.glGenTextures(1,names,0);texId=names[0];GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texId);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glGenTextures(1,names,0);sampleTex=names[0];GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,sampleTex);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_RGBA,192,128,0,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,null);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
        GLES20.glGenFramebuffers(1,names,0);fbo=names[0];GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,fbo);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,GLES20.GL_COLOR_ATTACHMENT0,GLES20.GL_TEXTURE_2D,sampleTex,0);
        if(GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)!=GLES20.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Incomplete sample FBO");
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,0);
    }
    private void frame(){
        if(closed)return;
        try{
            current(home);texture.updateTexImage();texture.getTransformMatrix(transform);
            haveFrame=true;render(texture.getTimestamp());
            if(++frames==1)events.message("GPU 首帧已送入编码器");
        }catch(Exception e){if(!closed){events.error("GPU 渲染失败: "+e.getMessage());close();}}
    }
    private boolean detect(){
        current(home);GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,fbo);GLES20.glViewport(0,0,192,128);draw(CropGeometry.Bounds.FULL);
        pixels.clear();GLES20.glReadPixels(0,0,192,128,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,pixels);
        int error=GLES20.glGetError();if(error!=GLES20.GL_NO_ERROR)throw new IllegalStateException("glReadPixels 0x"+Integer.toHexString(error));
        pixels.position(0);pixels.get(sample);GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,0);
        CropGeometry.Bounds detected=CropGeometry.detect(sample,192,128);
        long now=SystemClock.elapsedRealtime();
        if(now-lastProbeLog>5000){
            lastProbeLog=now;events.message("自动区域采样 "+sourceW+"×"+sourceH+": "+(detected==null?"暗场，保留区域":Math.round(detected.width()*100)+"% × "+Math.round(detected.height()*100)+"%"));
        }
        boolean changed=mode==CropGeometry.CINEMA?cinema.consider(detected):tracker.consider(detected);
        CropGeometry.Bounds active=mode==CropGeometry.CINEMA?cinema.bounds():tracker.bounds();
        if(changed)events.message((mode==CropGeometry.CINEMA?"影院区域已锁定: ":"视频区域已更新: ")+Math.round(active.width()*100)+"% × "+Math.round(active.height()*100)+"%");
        return changed;
    }
    private void render(long timestamp){
        CropGeometry.Bounds region=mode==CropGeometry.VIDEO_REGION?CropGeometry.videoRegion(sourceW,sourceH):
            (mode==CropGeometry.SCREEN || sourceW<=sourceH?CropGeometry.Bounds.FULL:mode==CropGeometry.CINEMA?cinema.bounds():tracker.bounds());
        current(window);GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,0);
        GLES20.glClearColor(0,0,0,1);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        CropGeometry.Viewport v=CropGeometry.viewport(sourceW,sourceH,region,outW,outH,mode==CropGeometry.VIDEO_FILL && sourceW>sourceH);
        GLES20.glViewport(v.x(),v.y(),v.width(),v.height());draw(region);
        events.geometry(new CropGeometry.Mapping(sourceW,sourceH,screenW,screenH,outW,outH,region,v));
        lastSubmitted=Math.max(timestamp,lastSubmitted+1);
        EGLExt.eglPresentationTimeANDROID(egl,window,lastSubmitted);check(EGL14.eglSwapBuffers(egl,window),"eglSwapBuffers");
    }
    private void draw(CropGeometry.Bounds b){
        coords.position(0);coords.put(new float[]{b.left(),b.bottom(),b.right(),b.bottom(),b.left(),b.top(),b.right(),b.top()});coords.position(0);vertices.position(0);
        GLES20.glUseProgram(program);GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texId);
        GLES20.glUniformMatrix4fv(transformLocation,1,false,transform,0);
        GLES20.glEnableVertexAttribArray(positionLocation);GLES20.glEnableVertexAttribArray(uvLocation);
        GLES20.glVertexAttribPointer(positionLocation,2,GLES20.GL_FLOAT,false,0,vertices);GLES20.glVertexAttribPointer(uvLocation,2,GLES20.GL_FLOAT,false,0,coords);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
    }
    private void current(EGLSurface s){check(EGL14.eglMakeCurrent(egl,s,s,context),"eglMakeCurrent");}
    private static int shader(int kind,String text){int shader=GLES20.glCreateShader(kind);GLES20.glShaderSource(shader,text);GLES20.glCompileShader(shader);int[] good=new int[1];GLES20.glGetShaderiv(shader,GLES20.GL_COMPILE_STATUS,good,0);if(good[0]==0)throw new IllegalStateException(GLES20.glGetShaderInfoLog(shader));return shader;}
    private static void check(boolean good,String action){if(!good)throw new IllegalStateException(action+" / EGL 0x"+Integer.toHexString(EGL14.eglGetError()));}
    private static FloatBuffer buffer(float[] values){FloatBuffer b=ByteBuffer.allocateDirect(values.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();b.put(values);b.position(0);return b;}
    public void close(){
        if(closed)return;closed=true;
        if(handler==null)return;
        if(Looper.myLooper()==handler.getLooper()){release();thread.quitSafely();return;}
        CountDownLatch done=new CountDownLatch(1);
        handler.post(()->{try{release();}finally{done.countDown();thread.quitSafely();}});
        try{done.await(2,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
    }
    private void release(){
        handler.removeCallbacks(probe);
        if(followsDisplay)try{manager.unregisterDisplayListener(listener);}catch(Exception ignored){}
        if(display!=null){display.release();display=null;}
        if(texture!=null)texture.setOnFrameAvailableListener(null);
        if(egl!=EGL14.EGL_NO_DISPLAY && context!=EGL14.EGL_NO_CONTEXT && home!=EGL14.EGL_NO_SURFACE){
            EGL14.eglMakeCurrent(egl,home,home,context);
            if(program!=0)GLES20.glDeleteProgram(program);
            GLES20.glDeleteTextures(2,new int[]{texId,sampleTex},0);if(fbo!=0)GLES20.glDeleteFramebuffers(1,new int[]{fbo},0);
        }
        if(input!=null)input.release();if(texture!=null)texture.release();
        if(egl!=EGL14.EGL_NO_DISPLAY){
            EGL14.eglMakeCurrent(egl,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
            if(window!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(egl,window);
            if(home!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(egl,home);
            if(context!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(egl,context);
            EGL14.eglTerminate(egl);EGL14.eglReleaseThread();
        }
    }
}
