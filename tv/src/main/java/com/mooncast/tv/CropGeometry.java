package com.mooncast.tv;

/** Pure geometry / sampled-border detection, independent of Android and the encoder. */
public final class CropGeometry {
    public static final int SCREEN=0, VIDEO_FIT=1, VIDEO_FILL=2, VIDEO_REGION=3, CINEMA=4;
    public record Bounds(float left,float bottom,float right,float top) {
        public static final Bounds FULL=new Bounds(0,0,1,1);
        public float width(){return right-left;}
        public float height(){return top-bottom;}
        public boolean near(Bounds b){return Math.abs(left-b.left)<.018f&&Math.abs(right-b.right)<.018f&&Math.abs(top-b.top)<.018f&&Math.abs(bottom-b.bottom)<.018f;}
    }
    public record Viewport(int x,int y,int width,int height) {}
    public record Point(float x,float y) {}
    /** Encoder is top-down to clients; GL bounds and viewports are bottom-up. */
    public record Mapping(int sourceW,int sourceH,int screenW,int screenH,int outW,int outH,Bounds region,Viewport viewport){
        public Point map(float x,float y){
            if(!Float.isFinite(x)||!Float.isFinite(y)||x<0||x>1||y<0||y>1)return null;
            float px=x*outW,py=y*outH,top=outH-viewport.y()-viewport.height();
            if(px<viewport.x()||px>viewport.x()+viewport.width()||py<top||py>top+viewport.height())return null;
            float u=(px-viewport.x())/viewport.width(),v=(py-top)/viewport.height();
            return new Point(Math.max(0,Math.min(screenW-1,(region.left()+u*region.width())*screenW)),
                Math.max(0,Math.min(screenH-1,(1-region.top()+v*region.height())*screenH)));
        }
    }
    /** Fixed centered 16:9 player area. Portrait keeps the whole phone screen. */
    public static Bounds videoRegion(int sourceW,int sourceH) {
        if(sourceW<=sourceH)return Bounds.FULL;
        float aspect=sourceW/(float)sourceH,videoAspect=16f/9f;
        if(aspect>=videoAspect){float edge=(1-videoAspect/aspect)/2;return new Bounds(edge,0,1-edge,1);}
        float edge=(1-aspect/videoAspect)/2;return new Bounds(0,edge,1,1-edge);
    }
    public static Viewport viewport(int sourceW,int sourceH,Bounds b,int outW,int outH,boolean fill) {
        float w=sourceW*b.width(),h=sourceH*b.height();
        float scale=fill?Math.max(outW/w,outH/h):Math.min(outW/w,outH/h);
        int width=Math.max(1,Math.round(w*scale)),height=Math.max(1,Math.round(h*scale));
        return new Viewport((outW-width)/2,(outH-height)/2,width,height);
    }
    /** Confidence is evidence quality, not a calibrated probability. */
    public record Detection(Bounds bounds,float confidence,boolean wideBorders) {}
    /** RGBA bottom-up source sample. Null means insufficient evidence; keep the prior crop. */
    public static Bounds detect(byte[] rgba,int w,int h) {
        Detection detected=detectDetailed(rgba,w,h);
        return detected==null?null:detected.bounds();
    }
    /**
     * Wide pillarboxing (9:16 inside 16:9 is about 34% per edge) requires bright, distributed
     * interior content and sustained tracker evidence. Bright subtitle/indicator pixels stop
     * a bar scan, even when sparse. Uncertain bars are deliberately left alone.
     */
    public static Detection detectDetailed(byte[] rgba,int w,int h) {
        if(rgba==null||w<16||h<16||(long)w*h*4>rgba.length)return null;
        int active=0,lit=0;
        for(int i=0;i<w*h;i++){
            if(!black(rgba,i*4))active++;
            if(bright(rgba,i*4))lit++;
        }
        if(active<w*h*.12f||lit<w*h*.10f)return null;
        int limitX=(int)Math.floor(w*.45f),limitY=(int)Math.floor(h*.45f);
        int left=0,right=0,bottom=0,top=0;
        while(left<limitX&&lineBlack(rgba,w,h,left,true))left++;
        while(right<limitX&&lineBlack(rgba,w,h,w-1-right,true))right++;
        while(bottom<limitY&&lineBlack(rgba,w,h,bottom,false))bottom++;
        while(top<limitY&&lineBlack(rgba,w,h,h-1-top,false))top++;
        if(Math.abs(left-right)>Math.max(2,w*.02f)||left>=limitX||right>=limitX)left=right=0;
        if(Math.abs(top-bottom)>Math.max(2,h*.02f)||top>=limitY||bottom>=limitY)top=bottom=0;
        // Retain the wider-content member of each pair to protect asymmetric edge content.
        left=right=Math.min(left,right);bottom=top=Math.min(bottom,top);
        if(left<2)left=right=0;
        if(bottom<2)bottom=top=0;
        if(left==0&&bottom==0)return new Detection(Bounds.FULL,1,false);
        int contentW=w-left-right,contentH=h-bottom-top;
        if(contentW<w*.20f||contentH<h*.20f)return null;
        boolean wide=left>=w*.29f||bottom>=h*.29f;
        // Small bright rectangles in dark scenery are ambiguous. 9:16 phone pillars retain
        // ~32% source area; require at least 24%, beyond the global dark-frame guard.
        if(wide&&(long)contentW*contentH<w*h*.24f)return null;
        int insideActive=0,insideLit=0;
        int[] cells=new int[16],brightCells=new int[16];
        for(int y=bottom;y<h-top;y++)for(int x=left;x<w-right;x++){
            int index=(y*w+x)*4;
            if(!black(rgba,index))insideActive++;
            if(bright(rgba,index))insideLit++;
            int cell=Math.min(3,(x-left)*4/contentW)+Math.min(3,(y-bottom)*4/contentH)*4;
            cells[cell]++;if(bright(rgba,index))brightCells[cell]++;
        }
        int distributed=0;
        for(int i=0;i<16;i++)if(brightCells[i]>=Math.max(1,cells[i]*.18f))distributed++;
        int area=contentW*contentH;
        if(insideActive<area*(wide?.50f:.35f)||insideLit<area*(wide?.35f:.22f)
                ||distributed<(wide?12:8))return null;
        // Require visible transitions at each edge instead of merely a bright global histogram.
        if(left>0&&(!visibleStrip(rgba,w,left,bottom,left+3,h-top)
                ||!visibleStrip(rgba,w,w-right-3,bottom,w-right,h-top)))return null;
        if(bottom>0&&(!visibleStrip(rgba,w,left,bottom,w-right,bottom+3)
                ||!visibleStrip(rgba,w,left,h-top-3,w-right,h-top)))return null;
        // One sample pixel of safety retains filtering/antialiasing at the content boundary.
        left=Math.max(0,left-1);right=left;bottom=Math.max(0,bottom-1);top=bottom;
        Bounds bounds=new Bounds(left/(float)w,bottom/(float)h,1-right/(float)w,1-top/(float)h);
        float confidence=Math.min(1,.75f+distributed/64f);
        return new Detection(bounds,confidence,wide);
    }
    /** Safety-only check after acquisition. It can reveal more source pixels, never hide more. */
    public static Bounds expandForVisibleContent(byte[] rgba,int w,int h,Bounds crop){
        if(rgba==null||w<16||h<16||(long)w*h*4>rgba.length||crop==null)return crop;
        int left=Math.max(0,(int)Math.floor(crop.left()*w));
        int right=Math.min(w,(int)Math.ceil(crop.right()*w));
        int bottom=Math.max(0,(int)Math.floor(crop.bottom()*h));
        int top=Math.min(h,(int)Math.ceil(crop.top()*h));
        // Small groups of clearly visible pixels qualify; isolated codec specks do not. The
        // tracker still requires consecutive evidence, so a single overlay frame cannot jump.
        boolean horizontal=visiblePixels(rgba,w,0,0,left,h)>=4
                ||visiblePixels(rgba,w,right,0,w,h)>=4;
        boolean vertical=visiblePixels(rgba,w,0,0,w,bottom)>=4
                ||visiblePixels(rgba,w,0,top,w,h)>=4;
        return new Bounds(horizontal?0:crop.left(),vertical?0:crop.bottom(),
                horizontal?1:crop.right(),vertical?1:crop.top());
    }
    private static int visiblePixels(byte[] p,int w,int left,int bottom,int right,int top){
        int count=0;
        for(int y=bottom;y<top;y++)for(int x=left;x<right;x++){
            int i=(y*w+x)*4;
            if(Math.max(p[i]&255,Math.max(p[i+1]&255,p[i+2]&255))>=72&&++count>=4)return count;
        }
        return count;
    }
    private static boolean visibleStrip(byte[] p,int w,int left,int bottom,int right,int top){
        int count=0;
        for(int y=bottom;y<top;y++)for(int x=left;x<right;x++)if(bright(p,(y*w+x)*4))count++;
        return count>=(right-left)*(top-bottom)*.18f;
    }
    private static boolean lineBlack(byte[] p,int w,int h,int line,boolean column){
        int size=column?h:w,blackCount=0;
        for(int position=0;position<size;position++){
            int index=(column?position*w+line:line*w+position)*4;
            if(bright(p,index))return false; // Never discard a subtitle or home indicator.
            if(black(p,index))blackCount++;
        }
        return blackCount>=size*.99f;
    }
    private static boolean black(byte[] p,int i){return (p[i]&255)<=24&&(p[i+1]&255)<=24&&(p[i+2]&255)<=24;}
    private static boolean bright(byte[] p,int i){return Math.max(p[i]&255,Math.max(p[i+1]&255,p[i+2]&255))>=52;}
}
