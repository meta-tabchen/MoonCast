package com.mooncast.tv;

/** One source-pixel scale for both axes; the TextureView's default stretch is undone. */
public record FramingTransform(float scale,float matrixX,float matrixY,float translateX,
                               float translateY,float contentLeft,float contentTop,
                               float contentWidth,float contentHeight,
                               int clipLeft,int clipTop,int clipRight,int clipBottom) {
    public static FramingTransform calculate(int sourceWidth,int sourceHeight,int viewWidth,int viewHeight,
                                            CropGeometry.Bounds bounds,FramingSettings settings){
        if(sourceWidth<=0||sourceHeight<=0||viewWidth<=0||viewHeight<=0||bounds.width()<=0||bounds.height()<=0)
            throw new IllegalArgumentException("Positive source, viewport and crop required");
        float w=sourceWidth*bounds.width(),h=sourceHeight*bounds.height();
        float sx=viewWidth/w,sy=viewHeight/h;
        float scale=(settings.mode()==FramingSettings.FILL?Math.max(sx,sy):Math.min(sx,sy))*settings.zoom()/100f;
        float displayedWidth=w*scale,displayedHeight=h*scale;
        // Pan is percent of available travel, including letterbox space. The selected region
        // stays inside the view when smaller; when larger, pan cannot reveal beyond that region.
        float left=(viewWidth-displayedWidth)/2+Math.abs(viewWidth-displayedWidth)/2*settings.panX()/100f;
        float top=(viewHeight-displayedHeight)/2+Math.abs(viewHeight-displayedHeight)/2*settings.panY()/100f;
        float tx=left-sourceWidth*bounds.left()*scale;
        float ty=top-sourceHeight*(1-bounds.top())*scale;
        // Ceil inward so a fraction of a deliberately removed edge cannot bleed through.
        int clipL=Math.max(0,(int)Math.ceil(left)),clipT=Math.max(0,(int)Math.ceil(top));
        int clipR=Math.min(viewWidth,(int)Math.floor(left+displayedWidth));
        int clipB=Math.min(viewHeight,(int)Math.floor(top+displayedHeight));
        return new FramingTransform(scale,sourceWidth*scale/viewWidth,sourceHeight*scale/viewHeight,
                tx,ty,left,top,displayedWidth,displayedHeight,clipL,clipT,clipR,clipB);
    }
}
