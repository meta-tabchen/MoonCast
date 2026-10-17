package com.mooncast.host;
public final class OutputSizePolicyTest {
    private static void require(boolean v){if(!v)throw new AssertionError();}
    public static void main(String[] args){
        int limit=OutputSizePolicy.liveLimit(1920,1280,720);require(limit==1280);
        limit=OutputSizePolicy.liveLimit(limit,1920,1080);require(limit==1920);
        limit=OutputSizePolicy.liveLimit(limit,3840,2160);require(limit==3840);
        limit=OutputSizePolicy.liveLimit(limit,1280,720,3840,2160);require(limit==3840);
        limit=OutputSizePolicy.liveLimit(limit,1920,1080);require(limit==1920);
        require(OutputSizePolicy.liveLimit(limit)==1920);
        require(OutputSizePolicy.liveLimit(limit,720,1280)==1280);
        try{OutputSizePolicy.liveLimit(limit,0,1080);throw new AssertionError();}catch(IllegalArgumentException expected){}
        System.out.println("PASS live-output sizing: 720p/1080p/4K, rotation, removals, reconnect gap");
    }
}
