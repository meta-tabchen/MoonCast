package com.mooncast.tv;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.view.TextureView;

/** Uniform source-space framing with transactional preview and source/orientation profiles. */
final class VideoFraming extends FramingSession {
    private final TextureView texture;
    private Bitmap sample;
    private final int[] pixels=new int[160*90];
    private final byte[] rgba=new byte[160*90*4];
    VideoFraming(TextureView texture){this(texture,null,"media");}
    VideoFraming(TextureView texture,SharedPreferences preferences,String sourceKey){
        super(profiles(preferences),sourceKey);
        this.texture=texture;texture.setOpaque(false);
    }
    private static FramingProfiles profiles(SharedPreferences preferences){
        return new FramingProfiles(preferences==null?null:new FramingProfiles.Store(){
            @Override public String read(String key){
                try{return preferences.getString(key,null);}catch(ClassCastException invalid){return null;}
            }
            @Override public void write(String key,String value){preferences.edit().putString(key,value).apply();}
        });
    }
    int viewportWidth(){return texture.getWidth();}
    int viewportHeight(){return texture.getHeight();}
    private FramingTransform transform(){
        if(viewportWidth()<=0||viewportHeight()<=0)return null;
        return FramingTransform.calculate(width,height,viewportWidth(),viewportHeight(),effectiveBounds(),settings);
    }
    Rect displayRect(){FramingTransform t=transform();return t==null?new Rect():new Rect(t.clipLeft(),t.clipTop(),t.clipRight(),t.clipBottom());}
    int fittedWidth(){FramingTransform t=transform();return t==null?0:Math.round(t.contentWidth());}
    int fittedHeight(){FramingTransform t=transform();return t==null?0:Math.round(t.contentHeight());}
    boolean cropsContent(){
        FramingTransform t=transform();
        return sourceCropped()||(t!=null&&
                (t.contentWidth()>viewportWidth()+1||t.contentHeight()>viewportHeight()+1));
    }
    void sample(){
        if(settings.mode()!=CINEMA||editing)return;
        if(!texture.isAvailable()){tracker.rejectSample();return;}
        if(sample==null)sample=Bitmap.createBitmap(160,90,Bitmap.Config.ARGB_8888);
        try{
            // A failed readback can leave the bitmap unchanged. Clear so stale bright frames
            // cannot accumulate false stability after the surface disappears.
            sample.eraseColor(0);
            if(texture.getBitmap(sample)==null){tracker.rejectSample();return;}
            sample.getPixels(pixels,0,160,0,0,160,90);
            for(int y=0;y<90;y++)for(int x=0;x<160;x++){
                int c=pixels[y*160+x],i=((89-y)*160+x)*4;
                rgba[i]=(byte)(c>>16);rgba[i+1]=(byte)(c>>8);rgba[i+2]=(byte)c;rgba[i+3]=(byte)255;
            }
            boolean changed=tracker.locked()
                    ?tracker.protect(CropGeometry.expandForVisibleContent(rgba,160,90,tracker.bounds()))
                    :tracker.considerDetection(CropGeometry.detectDetailed(rgba,160,90));
            if(changed)apply();
        }catch(IllegalStateException ignored){tracker.rejectSample();}
    }
    @Override void apply(){
        FramingTransform t=transform();if(t==null)return;
        Matrix matrix=new Matrix();
        matrix.setValues(new float[]{t.matrixX(),0,t.translateX(),0,t.matrixY(),t.translateY(),0,0,1});
        texture.setTransform(matrix);
        texture.setClipBounds(new Rect(t.clipLeft(),t.clipTop(),t.clipRight(),t.clipBottom()));
    }
    void release(){if(sample!=null){sample.recycle();sample=null;}}
}
