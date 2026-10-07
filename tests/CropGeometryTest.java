package com.mooncast.host;

public final class CropGeometryTest {
    public static void main(String[] args) throws Exception {
        int w=192,h=128;
        byte[] image=new byte[w*h*4];
        paint(image,w,20,0,172,h,30,120,200);
        var crop=CropGeometry.detect(image,w,h);
        close(crop.left(),19f/192,"pillar bars");
        close(crop.right(),1-19f/192,"right pillar");
        close(crop.height(),1,"full height");
        paint(image,w,188,55,190,74,255,255,255);
        close(CropGeometry.detect(image,w,h).left(),19f/192,"home gesture in black bar");
        byte[] dark=new byte[w*h*4];
        if(CropGeometry.detect(dark,w,h)!=null)throw new AssertionError("dark frame must hold previous crop");
        byte[] asymmetric=new byte[w*h*4];paint(asymmetric,w,20,0,w,h,30,120,200);
        close(CropGeometry.detect(asymmetric,w,h).width(),1,"asymmetric content must stay intact");
        byte[] letterbox=new byte[w*h*4];paint(letterbox,w,0,16,w,112,30,120,200);
        close(CropGeometry.detect(letterbox,w,h).bottom(),15f/128,"letterbox bars");
        byte[] lifted=new byte[w*h*4];paint(lifted,w,0,0,w,h,18,18,18);paint(lifted,w,20,0,172,h,30,120,200);
        paint(lifted,w,186,52,190,72,220,180,80);
        close(CropGeometry.detect(lifted,w,h).left(),19f/192,"raised black and colored home indicator");
        AutoCropTracker tracker=new AutoCropTracker();
        tracker.consider(crop);tracker.consider(crop);if(!tracker.consider(crop))throw new AssertionError("three timer probes should settle");
        tracker.consider(CropGeometry.Bounds.FULL);tracker.consider(CropGeometry.Bounds.FULL);tracker.consider(null);
        close(tracker.bounds().left(),crop.left(),"brief controls and dark scene retain crop");
        for(int i=0;i<6;i++)tracker.consider(CropGeometry.Bounds.FULL);close(tracker.bounds().width(),1,"real fullscreen eventually expands");
        if(args.length>0){var actual=CropGeometry.detect(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(args[0])),w,h);if(actual==null || Math.abs(actual.left()-246f/2412)>.015f)throw new AssertionError("actual phone sample "+actual);System.out.println("PASS: real phone screenshot sample "+actual);}
        var fit=CropGeometry.viewport(1920,1080,CropGeometry.Bounds.FULL,2388,1668,false);
        var fixed=CropGeometry.videoRegion(2412,1080);
        close(fixed.left(),246f/2412,"actual phone fixed video left");
        close(fixed.width()*2412,1920,"actual phone fixed video width");
        var fixedFit=CropGeometry.viewport(2412,1080,fixed,2388,1668,false);
        if(fixedFit.width()!=2388 || Math.abs(fixedFit.height()-1343)>1)throw new AssertionError("fixed region fit: "+fixedFit);
        close(CropGeometry.videoRegion(1080,2412).width(),1,"fixed region portrait fallback");
        close(CropGeometry.videoRegion(1600,1000).height(),.9f,"fixed region top/bottom crop");
        var mapping=new CropGeometry.Mapping(2412,1080,2412,1080,2388,1668,fixed,fixedFit);
        var center=mapping.map(.5f,.5f);close(center.x(),1206,"input center x");
        if(Math.abs(center.y()-540)>1)throw new AssertionError("input center y");
        if(mapping.map(.5f,0)!=null || mapping.map(Float.NaN,.5f)!=null)throw new AssertionError("ignore aspect bars and invalid coordinates");
        var edge=mapping.map(0,.5f);if(Math.abs(edge.x()-246)>1)throw new AssertionError("crop input left edge: "+edge);
        var fillMap=new CropGeometry.Mapping(1920,1080,1920,1080,2388,1668,CropGeometry.Bounds.FULL,CropGeometry.viewport(1920,1080,CropGeometry.Bounds.FULL,2388,1668,true));
        if(fillMap.map(0,.5f).x()<=0)throw new AssertionError("fill cropped edge mapping");
        if(fit.width()!=2388 || Math.abs(fit.height()-1343)>1 || fit.x()!=0 || fit.y()<=0)throw new AssertionError("iPad fit: "+fit);
        var fill=CropGeometry.viewport(1920,1080,CropGeometry.Bounds.FULL,2388,1668,true);
        if(fill.height()!=1668 || fill.width()<=2388 || fill.x()>=0)throw new AssertionError("iPad fill: "+fill);
        var portrait=CropGeometry.viewport(1080,2412,CropGeometry.Bounds.FULL,1920,1080,false);
        if(portrait.height()!=1080 || portrait.width()>=1920)throw new AssertionError("portrait fit");
        System.out.println("PASS: pillars, home indicator, dark frames, asymmetric content, letterbox, iPad fit/fill and portrait geometry");
    }
    static void close(float a,float b,String label){if(Math.abs(a-b)>.001f)throw new AssertionError(label+": "+a+" != "+b);}
    static void paint(byte[] p,int w,int l,int b,int r,int t,int red,int green,int blue){for(int y=b;y<t;y++)for(int x=l;x<r;x++){int i=(y*w+x)*4;p[i]=(byte)red;p[i+1]=(byte)green;p[i+2]=(byte)blue;p[i+3]=(byte)255;}}
}
