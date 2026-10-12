package com.mooncast.host;

public final class SenderLivenessTest {
    private static void expect(long expected, long actual, String message) {
        if(expected!=actual)throw new AssertionError(message+": "+actual);
    }
    public static void main(String[] args) {
        expect(700, SenderLiveness.absenceGrace(123,true,false,true,false), "old sender PID gone, even if another :host process exists");
        expect(-1, SenderLiveness.absenceGrace(123,true,true,true,false), "old sender still cleaning up");
        expect(-1, SenderLiveness.absenceGrace(123,true,false,true,true), "actual sender service present must be rebound");
        expect(-1, SenderLiveness.absenceGrace(123,false,false,true,false), "missing process snapshot is not PID death");
        expect(5500, SenderLiveness.absenceGrace(-1,true,true,true,false), "unknown sender PID uses service absence, not unrelated process liveness");
        expect(5500, SenderLiveness.absenceGrace(-1,false,false,true,false), "unknown PID fallback exceeds teardown watchdog");
        expect(-1, SenderLiveness.absenceGrace(-1,true,false,true,true), "actual sender present without PID");
        expect(-1, SenderLiveness.absenceGrace(-1,true,false,false,false), "unknown service snapshot is not absence");
        System.out.println("PASS: stale sender PID recovery, shared-process isolation and conservative no-PID teardown grace");
    }
}
