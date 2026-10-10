package com.mooncast.tv;

/** Generated frames exercise the exact TV-port geometry and cinema-lock source. */
public final class TvGeometryTest {
    public static void main(String[] args) {
        int width=192,height=128;
        byte[] frame=new byte[width*height*4];
        for(int y=0;y<height;y++) for(int x=20;x<172;x++) { int i=(y*width+x)*4; frame[i]=40; frame[i+1]=110; frame[i+2]=(byte)200; frame[i+3]=(byte)255; }
        CropGeometry.Bounds crop=CropGeometry.detect(frame,width,height);
        require(crop!=null && Math.abs(crop.left()-19f/192)<.001,"TV pillar detection");
        require(CropGeometry.detect(new byte[frame.length],width,height)==null,"dark scene keeps prior crop");
        CinemaCropTracker cinema=new CinemaCropTracker();
        for(int i=0;i<20;i++) cinema.consider(CropGeometry.Bounds.FULL);
        require(!cinema.locked(),"full-screen controls do not lock");
        for(int i=0;i<7;i++) cinema.consider(crop);
        require(!cinema.locked(),"requires stable samples");
        cinema.consider(crop); require(cinema.locked(),"locks stable video region");
        for(int i=0;i<100;i++) { cinema.consider(null); cinema.consider(CropGeometry.Bounds.FULL); }
        require(cinema.bounds().near(crop),"dark scenes/overlays retain framing");
        cinema.reset(); require(!cinema.locked() && cinema.bounds().near(CropGeometry.Bounds.FULL),"rescan reset");
        CropGeometry.Bounds fixed=CropGeometry.videoRegion(2412,1080);
        require(Math.abs(fixed.left()-246f/2412)<.001,"centered phone video preset");
        require(CropGeometry.videoRegion(1080,2412).near(CropGeometry.Bounds.FULL),"portrait full frame");
        CropGeometry.Viewport fit=CropGeometry.viewport(1920,1080,CropGeometry.Bounds.FULL,1920,1200,false);
        require(fit.width()==1920 && fit.height()==1080 && fit.y()==60,"fit preserves full image");
        CropGeometry.Viewport fill=CropGeometry.viewport(1920,1080,CropGeometry.Bounds.FULL,1920,1200,true);
        require(fill.height()==1200 && fill.width()>1920 && fill.x()<0,"fill is deliberate edge crop");
        System.out.println("PASS: TV sampled border geometry, cinema lock/rescan, portrait fallback and fit/fill");
    }
    private static void require(boolean value,String label) { if(!value) throw new AssertionError(label); }
}
