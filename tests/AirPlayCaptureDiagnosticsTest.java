package com.mooncast.host;

import java.nio.file.Files;

public final class AirPlayCaptureDiagnosticsTest {
    public static void main(String[] args) throws Exception {
        CapturedContentSize s=new CapturedContentSize();
        require(s.update(2400,1080),"pre-encoder callback");
        require(s.width(1080)==2400 && s.height(2400)==1080,"callback must survive handshake");
        require(!s.update(0,0) && s.width(0)==2400,"zero size must not replace valid rotation");
        require(s.update(1080,2400) && s.height(0)==2400,"rotation");
        require(new CapturedContentSize().width(0)==0,"new projection must not inherit prior dimensions");
        int w=192,h=128;byte[] pixels=new byte[w*h*4];
        for(int y=48;y<80;y++)for(int x=0;x<w;x++)for(int c=0;c<3;c++)pixels[(y*w+x)*4+c]=100;
        var before=CropGeometry.viewport(720,1600,CropGeometry.Bounds.FULL,1280,720,false);
        var region=CropGeometry.detectPortraitVideo(pixels,w,h);
        require(region!=null,"landscape player in portrait capture must be detected");
        var after=CropGeometry.viewport(720,1600,region,1280,720,false);
        require(before.width()<400 && after.width()>1200,"automatic video fit must remove double letterboxing");
        var dir=Files.createTempDirectory("airplay-diagnostic-");
        try {
            AirPlayDiagnosticStore.save(dir.toFile(),"MoonCast 0.7.4/62\nstopOrigin=stream-read;error=EOF");
            require(AirPlayDiagnosticStore.read(dir.toFile()).contains("stream-read"),"diagnostic must survive host teardown");
            AirPlayDiagnosticStore.save(dir.toFile(),"x".repeat(25000));
            require(AirPlayDiagnosticStore.read(dir.toFile()).startsWith("MoonCast"),"oversized update must not replace last report");
        } finally {try(var files=Files.list(dir)){for(var p:files.toList())Files.delete(p);}Files.delete(dir);}
        require(AirPlayDiagnostics.origin("legacy heartbeat: secret PIN").equals("heartbeat-write"),"safe origin classification");
        System.out.println("PASS: pre-encoder/rotation sizes, zero/stale isolation, portrait-player double-letterbox reproduction and fit, bounded retained diagnostics and error origin");
    }
    private static void require(boolean value,String why){if(!value)throw new AssertionError(why);}
}
