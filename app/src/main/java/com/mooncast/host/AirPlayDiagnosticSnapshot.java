package com.mooncast.host;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Chooses the newest structural snapshot, including one saved during teardown. */
final class AirPlayDiagnosticSnapshot {
    private static long number(String report, String key) {
        Matcher m=Pattern.compile("(?m)^"+key+"=([0-9]{1,19})$").matcher(report);
        if(!m.find())return -1;
        try{return Long.parseLong(m.group(1));}catch(NumberFormatException ignored){return -1;}
    }
    private static long stamp(String report,long nowElapsed,long nowWall) {
        long elapsed=number(report,"snapshotElapsedMs"), wall=number(report,"snapshotWallMs");
        if(elapsed<0 || elapsed>nowElapsed || wall<0)return -1;
        // A retained report from a previous boot (or a changed wall clock) must
        // not be presented as a fresh snapshot of the current connection.
        long origin=wall-elapsed, current=nowWall-nowElapsed;
        return origin>=current-60000 && origin<=current+60000 ? elapsed : -1;
    }
    static String newest(String cache,String stored,long nowElapsed,long nowWall) {
        cache=cache==null?"":cache;stored=stored==null?"":stored;
        long c=stamp(cache,nowElapsed,nowWall),s=stamp(stored,nowElapsed,nowWall);
        boolean disk=!stored.isEmpty() && (cache.isEmpty() || s>=c);
        String report=disk?stored:cache;if(report.isEmpty())return "";
        long at=disk?s:c;
        return report+"\ncopySource="+(disk?"private-store":"service-cache")+
            ";reportAgeMs="+(at<0?"unknown":Long.toString(nowElapsed-at))+
            ";copiedElapsedMs="+nowElapsed;
    }
}
