package com.mooncast.host;

/** Recompute from live consumers, so a 4K -> 1080p reconnect can reduce capture cost. */
final class OutputSizePolicy {
    static int liveLimit(int previous,int... dimensions){
        if(dimensions.length%2!=0)throw new IllegalArgumentException("width/height pairs required");
        int limit=0;
        for(int dimension:dimensions){if(dimension<1||dimension>8192)throw new IllegalArgumentException("output size");limit=Math.max(limit,dimension);}
        return limit==0?previous:limit;
    }
}
