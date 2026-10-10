package com.mooncast.tv.airplay;
/** Host-side Annex-B parser regression test; no device interoperability claims. */
public final class NalUnitTest {
    private static void check(boolean condition) { if (!condition) throw new AssertionError(); }
    public static void main(String[] args) {
        check(MirrorDecoder.hasNal(new byte[]{0,0,0,1,0x67,1}, false, true));
        check(MirrorDecoder.hasNal(new byte[]{0,0,1,0x68,1}, false, true));
        check(MirrorDecoder.hasNal(new byte[]{0,0,0,1,0x65,1}, false, false));
        check(!MirrorDecoder.hasNal(new byte[]{0,0,0,1,0x41,1}, false, false));
        check(MirrorDecoder.hasNal(new byte[]{0,0,0,1,0x40,1}, true, true));
        check(MirrorDecoder.hasNal(new byte[]{0,0,0,1,0x26,1}, true, false));
        check(!MirrorDecoder.hasNal(new byte[]{0,0,0,1}, false, true));
        check(!MirrorDecoder.hasNal(new byte[]{0,0,1}, true, true));
        System.out.println("8 Annex-B parser checks passed");
    }
}
