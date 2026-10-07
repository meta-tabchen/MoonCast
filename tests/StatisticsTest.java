package com.mooncast.host;

public final class StatisticsTest {
    public static void main(String[] args){
        FrameStatistics frames=new FrameStatistics();frames.sample(1_000_000_000);
        for(int i=0;i<60;i++)frames.submitted(1_000_000_001L+i*10_000_000L,1_002_000_001L+i*10_000_000L);
        var active=frames.sample(2_000_000_000L);
        if(active.fps()!=60 || active.submissionMs()!=2 || active.totalFrames()!=60)throw new AssertionError("frame accounting: "+active);
        var paused=frames.sample(3_000_000_000L);
        if(paused.fps()!=0 || paused.submissionMs()!=0 || paused.totalFrames()!=60)throw new AssertionError("stale FPS after pause");
        TrafficRate rate=new TrafficRate();if(rate.sample(1_000,1000)!=-1)throw new AssertionError("initial counter");
        if(rate.sample(1_001_000,2000)!=8)throw new AssertionError("Mbps units");
        if(rate.sample(0,3000)!=-1 || rate.sample(-1,4000)!=-1)throw new AssertionError("reset/unavailable");
        if(rate.sample(50,5000)!=-1 || rate.sample(50,6000)!=0)throw new AssertionError("idle");
        System.out.println("PASS: frame rates, submission timing, paused frames, Mbps units, unsupported/reset/idle counters");
    }
}
