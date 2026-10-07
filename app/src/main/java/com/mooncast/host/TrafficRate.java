package com.mooncast.host;

/** Counter reset/unavailable handling for Android's whole-app UID transmission counter. */
final class TrafficRate {
    private long previous=-1,previousMs;
    double sample(long bytes,long nowMs){
        if(bytes<0){previous=-1;return -1;}
        double result=previous<0 || bytes<previous || nowMs<=previousMs?-1:(bytes-previous)*8d/(nowMs-previousMs)/1000d;
        previous=bytes;previousMs=nowMs;return result;
    }
}
