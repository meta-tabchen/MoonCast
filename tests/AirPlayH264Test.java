package com.mooncast.host;

import java.util.Arrays;

public final class AirPlayH264Test {
    private static int checks;
    private static void equal(byte[] actual, int... expected) {
        checks++; if (actual.length != expected.length) throw new AssertionError(Arrays.toString(actual));
        for (int i = 0; i < expected.length; i++) if ((actual[i] & 255) != expected[i]) throw new AssertionError("byte " + i);
    }
    private static void rejected(Runnable action) { checks++; try { action.run(); } catch (IllegalArgumentException expected) { return; } throw new AssertionError("malformed AVC accepted"); }
    public static void main(String[] args) {
        AirPlayH264 codec = new AirPlayH264();
        byte[] frame = {0,0,0,1,0x65,42}; equal(codec.frame(frame, true),0,0,0,1,0x65,42);
        codec.configuration(new byte[]{0,0,0,1,0x67,12},new byte[]{0,0,0,1,0x68,34});
        equal(codec.frame(frame,true),0,0,0,1,0x67,12,0,0,0,1,0x68,34,0,0,0,1,0x65,42);
        equal(codec.frame(frame,false),0,0,0,1,0x65,42);
        frame[5]=0; equal(codec.frame(new byte[]{0,0,0,2,0x41,17},false),0,0,0,1,0x41,17);
        codec.configuration(new byte[]{1,66,0,40,(byte)0xff,(byte)0xe1,0,2,0x67,12,1,0,2,0x68,34});
        equal(codec.frame(new byte[]{0,0,0,2,0x65,42},true),0,0,0,1,0x67,12,0,0,0,1,0x68,34,0,0,0,1,0x65,42);
        codec.configuration(new byte[]{1,66,0,40,(byte)0xfd,(byte)0xe1,0,2,0x67,12,1,0,2,0x68,34});
        equal(codec.frame(new byte[]{0,2,0x41,2,0,2,0x41,3},false),0,0,0,1,0x41,2,0,0,0,1,0x41,3);
        rejected(()->codec.frame(new byte[]{0},false));
        rejected(()->codec.frame(new byte[]{0,4,0x41},false));
        rejected(()->codec.frame(new byte[]{0,0},false));
        rejected(()->codec.configuration(new byte[]{1,66,0,40,(byte)0xff,(byte)0xe1,0,9,0x67}));
        Object owner=new Object(), other=new Object(); checks++; if(!SenderOwnership.acquire(owner))throw new AssertionError("initial owner");
        checks++; if(!SenderOwnership.acquire(owner))throw new AssertionError("repeat owner");
        checks++; if(SenderOwnership.acquire(other))throw new AssertionError("protocol overlap");
        checks++; if(SenderOwnership.acquire(new Object()))throw new AssertionError("teardown lease released before process death");
        System.out.println("AirPlay H.264 / capture ownership: " + checks + " checks passed");
    }
}
