package com.mooncast.host;

public final class AirPlayDiagnosticSnapshotTest {
    private static String report(long elapsed,long origin,String state) {
        return "MoonCast\nsnapshotElapsedMs="+elapsed+"\nsnapshotWallMs="+(origin+elapsed)+"\nfailure="+state;
    }
    private static void has(String s,String wanted) {if(!s.contains(wanted))throw new AssertionError(wanted+" missing: "+s);}
    public static void main(String[] args) {
        String old=report(1000,100000,"none"),terminal=report(1800,100000,"EOF");
        String picked=AirPlayDiagnosticSnapshot.newest(old,terminal,2000,102000);
        has(picked,"failure=EOF");has(picked,"copySource=private-store");has(picked,"reportAgeMs=200");
        picked=AirPlayDiagnosticSnapshot.newest(terminal,old,2000,102000);
        has(picked,"copySource=service-cache");has(picked,"failure=EOF");
        picked=AirPlayDiagnosticSnapshot.newest("failure=none","failure=closed",2000,102000);
        has(picked,"failure=closed");has(picked,"reportAgeMs=unknown");
        picked=AirPlayDiagnosticSnapshot.newest(terminal,report(50000,100000,"old-boot"),2000,102000);
        has(picked,"failure=EOF");has(picked,"copySource=service-cache");
        picked=AirPlayDiagnosticSnapshot.newest(terminal,report(1900,500000,"other-boot"),2000,102000);
        has(picked,"failure=EOF");
        picked=AirPlayDiagnosticSnapshot.newest("",report(1000,500000,"old"),2000,102000);
        has(picked,"reportAgeMs=unknown");
        if(!AirPlayDiagnosticSnapshot.newest("","",2000,102000).isEmpty())throw new AssertionError("invented report");
        System.out.println("PASS: newest terminal/live snapshot selection, stale legacy report marking, boot/clock/future checks and empty state");
    }
}
