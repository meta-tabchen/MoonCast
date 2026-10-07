package com.mooncast.host;

/** Pure geometry / sampled-border detection, independent of Android and the encoder. */
public final class CropGeometry {
    public static final int SCREEN=0, VIDEO_FIT=1, VIDEO_FILL=2, VIDEO_REGION=3, CINEMA=4;
    public record Bounds(float left,float bottom,float right,float top) {
        public static final Bounds FULL=new Bounds(0,0,1,1);
        public float width(){return right-left;}
        public float height(){return top-bottom;}
        public boolean near(Bounds b){return Math.abs(left-b.left)<.018f && Math.abs(right-b.right)<.018f && Math.abs(top-b.top)<.018f && Math.abs(bottom-b.bottom)<.018f;}
    }
    public record Viewport(int x,int y,int width,int height) {}
    public record Point(float x,float y) {}
    /** Encoder is top-down to clients; GL bounds and viewports are bottom-up. */
    public record Mapping(int sourceW,int sourceH,int screenW,int screenH,int outW,int outH,Bounds region,Viewport viewport){
        public Point map(float x,float y){
            if(!Float.isFinite(x) || !Float.isFinite(y) || x<0 || x>1 || y<0 || y>1)return null;
            float px=x*outW,py=y*outH,top=outH-viewport.y()-viewport.height();
            if(px<viewport.x() || px>viewport.x()+viewport.width() || py<top || py>top+viewport.height())return null;
            float u=(px-viewport.x())/viewport.width(),v=(py-top)/viewport.height();
            return new Point(Math.max(0,Math.min(screenW-1,(region.left()+u*region.width())*screenW)),
                Math.max(0,Math.min(screenH-1,(1-region.top()+v*region.height())*screenH)));
        }
    }
    /** Fixed centered 16:9 player area. Portrait keeps the whole phone screen. */
    public static Bounds videoRegion(int sourceW,int sourceH) {
        if(sourceW<=sourceH)return Bounds.FULL;
        float aspect=sourceW/(float)sourceH, videoAspect=16f/9f;
        if(aspect>=videoAspect){float edge=(1-videoAspect/aspect)/2;return new Bounds(edge,0,1-edge,1);}
        float edge=(1-aspect/videoAspect)/2;return new Bounds(0,edge,1,1-edge);
    }
    public static Viewport viewport(int sourceW,int sourceH,Bounds b,int outW,int outH,boolean fill) {
        float w=sourceW*b.width(), h=sourceH*b.height();
        float scale=fill?Math.max(outW/w,outH/h):Math.min(outW/w,outH/h);
        int width=Math.max(1,Math.round(w*scale)), height=Math.max(1,Math.round(h*scale));
        return new Viewport((outW-width)/2,(outH-height)/2,width,height);
    }
    /** RGBA bottom-up GL sample. Null means a dark/fade frame: keep the previous crop. */
    public static Bounds detect(byte[] rgba,int w,int h) {
        int active=0;
        for(int i=0;i<w*h;i++) if(!black(rgba,i*4)) active++;
        if(active<w*h*.12f) return null;
        int left=0,right=0,bottom=0,top=0;
        while(left<w*.30f && columnBlack(rgba,w,h,left)) left++;
        while(right<w*.30f && columnBlack(rgba,w,h,w-1-right)) right++;
        while(bottom<h*.30f && rowBlack(rgba,w,h,bottom)) bottom++;
        while(top<h*.30f && rowBlack(rgba,w,h,h-1-top)) top++;
        // Require paired borders; sparse home indicators may be ignored, subtitles within video remain.
        if(Math.abs(left-right)>Math.max(3,w*.035f) || left>=w*.30f || right>=w*.30f) left=right=0;
        if(Math.abs(top-bottom)>Math.max(3,h*.035f) || top>=h*.30f || bottom>=h*.30f) top=bottom=0;
        // One sample pixel of safety margin protects edge content and avoids filtering into a cut edge.
        left=Math.max(0,Math.min(left,right)-1); right=left;
        bottom=Math.max(0,Math.min(bottom,top)-1); top=bottom;
        return new Bounds(left/(float)w,bottom/(float)h,1-right/(float)w,1-top/(float)h);
    }
    private static boolean columnBlack(byte[] p,int w,int h,int x) {
        int n=0,black=0,white=0;
        for(int y=2;y<h-2;y++){n++;int i=(y*w+x)*4;if(black(p,i))black++;else if(white(p,i))white++;}
        // A narrow, neutral home indicator in a pillar bar must not prevent detection.
        return black>=n*.80f || (black>=n*.78f && black+white>=n*.98f);
    }
    private static boolean rowBlack(byte[] p,int w,int h,int y) {
        int n=0,black=0;
        for(int x=2;x<w-2;x++){n++;if(black(p,(y*w+x)*4))black++;}
        return black>=n*.92f;
    }
    private static boolean black(byte[] p,int i){return (p[i]&255)<=24 && (p[i+1]&255)<=24 && (p[i+2]&255)<=24;}
    private static boolean white(byte[] p,int i){int r=p[i]&255,g=p[i+1]&255,b=p[i+2]&255;return Math.min(r,Math.min(g,b))>=140 && Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))<=10;}
}
