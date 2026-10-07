package com.mooncast.host;

/** Submitted capture frames and CPU/driver submission time; not decoder FPS or GPU execution time. */
final class FrameStatistics {
    record Sample(double fps,double submissionMs,long totalFrames){}
    private long started,total,windowFrames,windowCost;
    synchronized void submitted(long start,long finish){
        if(started==0)started=start;
        total++;windowFrames++;windowCost+=Math.max(0,finish-start);
    }
    synchronized Sample sample(long now){
        if(started==0){started=now;return new Sample(0,0,total);}
        long elapsed=now-started;
        if(elapsed<=0)return new Sample(0,0,total);
        Sample sample=new Sample(windowFrames*1_000_000_000d/elapsed,windowFrames==0?0:windowCost/(windowFrames*1_000_000d),total);
        started=now;windowFrames=windowCost=0;return sample;
    }
}
