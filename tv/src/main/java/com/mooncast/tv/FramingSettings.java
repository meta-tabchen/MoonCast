package com.mooncast.tv;

/** Immutable, validated remote-control values. Edges use top-down source coordinates. */
public record FramingSettings(int mode, int left, int top, int right, int bottom,
                              int zoom, int panX, int panY) {
    public static final int FIT=0, FILL=1, CINEMA=2, MANUAL=3;
    public static final int LEFT=0, TOP=1, RIGHT=2, BOTTOM=3;
    public static final int MAX_EDGE=45, MIN_CONTENT=20, MIN_ZOOM=100, MAX_ZOOM=300;
    public static final FramingSettings DEFAULT=new FramingSettings(FIT,0,0,0,0,100,0,0);
    public FramingSettings {
        mode=clamp(mode,FIT,MANUAL);
        left=clamp(left,0,MAX_EDGE); right=clamp(right,0,MAX_EDGE);
        top=clamp(top,0,MAX_EDGE); bottom=clamp(bottom,0,MAX_EDGE);
        // Normal edits clamp only the selected edge in withEdge(), preserving its opposite.
        if(left+right>100-MIN_CONTENT)right=100-MIN_CONTENT-left;
        if(top+bottom>100-MIN_CONTENT)bottom=100-MIN_CONTENT-top;
        zoom=clamp(zoom,MIN_ZOOM,MAX_ZOOM);
        panX=clamp(panX,-100,100); panY=clamp(panY,-100,100);
    }
    public FramingSettings withMode(int value){return new FramingSettings(value,left,top,right,bottom,zoom,panX,panY);}
    public int edge(int edge){return switch(edge){case LEFT->left;case TOP->top;case RIGHT->right;case BOTTOM->bottom;default->throw new IllegalArgumentException("Unknown edge");};}
    public FramingSettings withEdge(int edge,int value){
        int opposite=switch(edge){case LEFT->right;case RIGHT->left;case TOP->bottom;case BOTTOM->top;default->throw new IllegalArgumentException("Unknown edge");};
        value=clamp(value,0,Math.min(MAX_EDGE,100-MIN_CONTENT-opposite));
        return new FramingSettings(MANUAL,edge==LEFT?value:left,edge==TOP?value:top,
                edge==RIGHT?value:right,edge==BOTTOM?value:bottom,zoom,panX,panY);
    }
    public FramingSettings withZoom(int value){return new FramingSettings(mode,left,top,right,bottom,value,panX,panY);}
    public FramingSettings withPan(int x,int y){return new FramingSettings(mode,left,top,right,bottom,zoom,x,y);}
    public CropGeometry.Bounds manualBounds(){return new CropGeometry.Bounds(left/100f,bottom/100f,1-right/100f,1-top/100f);}
    private static int clamp(int v,int lo,int hi){return Math.max(lo,Math.min(hi,v));}
}
