package com.mooncast.tv;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.view.TextureView;

/** Uniform source-space crop and fit. Sampling runs only while acquiring a cinema crop. */
final class VideoFraming {
    static final int FIT=0,FILL=1,CINEMA=2,MANUAL=3;
    private final TextureView texture;
    private final CinemaCropTracker tracker=new CinemaCropTracker();
    private int width=1920,height=1080,mode=FIT,horizontal,vertical;
    private Bitmap sample;
    private final int[] pixels=new int[160*90];
    private final byte[] rgba=new byte[160*90*4];
    VideoFraming(TextureView texture){this.texture=texture;}
    void videoSize(int w,int h){if(w>0&&h>0&&(w!=width||h!=height)){width=w;height=h;tracker.reset();apply();}}
    void mode(int value){mode=value;tracker.reset();apply();}
    int mode(){return mode;}
    void manual(int x,int y){horizontal=Math.max(0,Math.min(25,x));vertical=Math.max(0,Math.min(25,y));tracker.reset();apply();}
    int horizontal(){return horizontal;} int vertical(){return vertical;}
    void reset(){tracker.reset();apply();}
    boolean locked(){return tracker.locked();}
    void sample(){
        if(mode!=CINEMA||tracker.locked()||!texture.isAvailable())return;
        if(sample==null)sample=Bitmap.createBitmap(160,90,Bitmap.Config.ARGB_8888);
        try{
            if(texture.getBitmap(sample)==null)return;
            sample.getPixels(pixels,0,160,0,0,160,90);
            for(int y=0;y<90;y++)for(int x=0;x<160;x++){
                int c=pixels[y*160+x],i=((89-y)*160+x)*4;
                rgba[i]=(byte)(c>>16);rgba[i+1]=(byte)(c>>8);rgba[i+2]=(byte)c;rgba[i+3]=(byte)255;
            }
            if(tracker.consider(CropGeometry.detect(rgba,160,90)))apply();
        }catch(IllegalStateException ignored){/* Surface disappeared between callbacks. */}
    }
    void apply(){
        float vw=texture.getWidth(),vh=texture.getHeight();if(vw<=0||vh<=0)return;
        CropGeometry.Bounds b=mode==CINEMA?tracker.bounds():mode==MANUAL?new CropGeometry.Bounds(horizontal/100f,vertical/100f,1-horizontal/100f,1-vertical/100f):CropGeometry.Bounds.FULL;
        float scale=mode==FILL?Math.max(vw/(width*b.width()),vh/(height*b.height())):Math.min(vw/(width*b.width()),vh/(height*b.height()));
        float sx=width*scale/vw,sy=height*scale/vh;
        float left=(vw-width*b.width()*scale)/2-width*b.left()*scale;
        float top=(vh-height*b.height()*scale)/2-height*(1-b.top())*scale;
        Matrix matrix=new Matrix();matrix.setValues(new float[]{sx,0,left,0,sy,top,0,0,1});texture.setTransform(matrix);
        int clipW=Math.min((int)vw,Math.round(width*b.width()*scale)),clipH=Math.min((int)vh,Math.round(height*b.height()*scale));
        int clipX=Math.max(0,Math.round((vw-clipW)/2)),clipY=Math.max(0,Math.round((vh-clipH)/2));
        texture.setClipBounds(new Rect(clipX,clipY,clipX+clipW,clipY+clipH));
    }
    void release(){if(sample!=null){sample.recycle();sample=null;}}
}
