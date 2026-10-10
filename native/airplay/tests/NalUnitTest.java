package com.mooncast.tv.airplay;
/** Host-side Annex-B parser regression test; no device interoperability claims. */
public final class NalUnitTest {
    private static int checks;
    private static void check(boolean condition) { checks++; if (!condition) throw new AssertionError("NAL check " + checks); }
    public static void main(String[] args) {
        check(MirrorDecoder.hasNal(new byte[]{0,0,0,1,0x67,1}, false, true));
        check(MirrorDecoder.hasNal(new byte[]{0,0,1,0x68,1}, false, true));
        check(MirrorDecoder.hasNal(new byte[]{0,0,0,1,0x65,1}, false, false));
        check(!MirrorDecoder.hasNal(new byte[]{0,0,0,1,0x41,1}, false, false));
        check(MirrorDecoder.hasNal(new byte[]{0,0,0,1,0x40,1}, true, true));
        check(MirrorDecoder.hasNal(new byte[]{0,0,0,1,0x26,1}, true, false));
        check(!MirrorDecoder.hasNal(new byte[]{0,0,0,1}, false, true));
        check(!MirrorDecoder.hasNal(new byte[]{0,0,1}, true, true));
        for (int type = 1; type <= 5; type++) {
            check(MirrorDecoder.hasVcl(new byte[]{0,0,1,(byte)type,1}, false));
            check(MirrorDecoder.hasVcl(new byte[]{0,0,0,1,(byte)type,1}, false));
        }
        // AVC SEI, SPS/PPS, AUD, end-of-sequence/stream and filler contain no picture.
        for (int type : new int[]{0,6,7,8,9,10,11,12})
            check(!MirrorDecoder.hasVcl(new byte[]{0,0,0,1,(byte)type,1}, false));
        byte[] avcMixed = {0,0,0,1,0x67,1,0,0,1,0x65,1};
        check(MirrorDecoder.hasNal(avcMixed, false, true));
        check(MirrorDecoder.hasVcl(avcMixed, false));
        for (int type = 0; type <= 31; type++)
            check(MirrorDecoder.hasVcl(new byte[]{0,0,1,(byte)(type << 1),1,1}, true));
        // HEVC VPS/SPS/PPS, AUD, EOS/EOB, filler and both SEI classes are non-VCL.
        for (int type = 32; type <= 40; type++)
            check(!MirrorDecoder.hasVcl(new byte[]{0,0,0,1,(byte)(type << 1),1,1}, true));
        byte[] hevcMixed = {0,0,1,0x40,1,1,0,0,0,1,0x26,1,1};
        check(MirrorDecoder.hasNal(hevcMixed, true, true));
        check(MirrorDecoder.hasVcl(hevcMixed, true));
        check(!MirrorDecoder.hasVcl(null, false));
        check(!MirrorDecoder.hasVcl(new byte[]{0,0,1}, false));
        check(!MirrorDecoder.hasVcl(new byte[]{0,0,0,1}, false));
        check(!MirrorDecoder.hasVcl(new byte[]{0,0,1,0x26}, true));
        check(!MirrorDecoder.hasVcl(new byte[]{0,0,0,1,0x26}, true));
        SessionWatchdog idle = new SessionWatchdog(); idle.connectionOpened(); idle.beginMirror(0);
        idle.videoPacket(1, MirrorDecoder.hasVcl(new byte[]{0,0,1,6,1,0,0,1,9,1}, false));
        check(idle.check(60_000) == null); // SEI/AUD-only static sender must not recover.
        idle.videoPacket(60_001, MirrorDecoder.hasVcl(avcMixed, false));
        check(idle.check(75_001) != null); // Mixed config + VCL does expect decoder output.
        System.out.println(checks + " Annex-B parser/watchdog checks passed");
    }
}
