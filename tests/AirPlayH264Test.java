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
        AirPlayH264 changed = new AirPlayH264();
        changed.configuration(new byte[]{0,0,0,1,0x67,11},new byte[]{0,0,0,1,0x68,22});
        changed.configuration(new byte[]{0,0,0,1,0x68,33});
        equal(changed.frame(new byte[]{0,0,0,1,0x65,44},true),0,0,0,1,0x67,11,0,0,0,1,0x68,33,0,0,0,1,0x65,44);
        changed.configuration(new byte[]{0,0,0,1,0x67,55});
        equal(changed.frame(new byte[]{0,0,0,1,0x65,44},true),0,0,0,1,0x67,55,0,0,0,1,0x68,33,0,0,0,1,0x65,44);
        // A replacement encoder has no parameter sets from the previous session.
        equal(new AirPlayH264().frame(new byte[]{0,0,0,1,0x65,44},true),0,0,0,1,0x65,44);
        AirPlayH264 declared = new AirPlayH264();
        declared.configuration(new byte[]{1,66,0,40,(byte)0xff,(byte)0xe1,0,2,0x67,12,1,0,2,0x68,34});
        // Every 256..511-byte first NAL resembles a three-byte start code.
        // Compare the entire payload including a second NAL, not only a prefix.
        for(int n=256;n<=511;n++) {
            byte[] avcc=new byte[n+10], expected=new byte[n+10];
            java.nio.ByteBuffer.wrap(avcc).putInt(n);avcc[4]=0x41;
            Arrays.fill(avcc,5,n+4,(byte)0x5a);
            java.nio.ByteBuffer.wrap(avcc,n+4,4).putInt(2);avcc[n+8]=0x41;avcc[n+9]=0x55;
            System.arraycopy(avcc,0,expected,0,avcc.length);
            expected[0]=expected[1]=expected[2]=0;expected[3]=1;
            expected[n+4]=expected[n+5]=expected[n+6]=0;expected[n+7]=1;
            checks++;if(!Arrays.equals(declared.frame(avcc,false),expected))throw new AssertionError("AVCC length mistaken for start code: "+n);
        }
        checks++;if(!declared.diagnostics().contains("resolved=256"))throw new AssertionError("resolved format evidence missing");
        byte[] truncated=new byte[303];truncated[2]=1;truncated[3]=44;truncated[4]=0x41;
        rejected(()->declared.frame(truncated,false));
        // A new Annex B format clears the old avcC framing declaration.
        declared.configuration(new byte[]{0,0,0,1,0x67,12},new byte[]{0,0,0,1,0x68,34});
        equal(declared.frame(new byte[]{0,0,1,0x41,0x55},false),0,0,1,0x41,0x55);
        Object owner=new Object(), other=new Object(); checks++; if(!SenderOwnership.acquire(owner))throw new AssertionError("initial owner");
        checks++; if(!SenderOwnership.acquire(owner))throw new AssertionError("repeat owner");
        checks++; if(SenderOwnership.acquire(other))throw new AssertionError("protocol overlap");
        checks++; if(SenderOwnership.acquire(new Object()))throw new AssertionError("teardown lease released before process death");
        System.out.println("AirPlay H.264 / capture ownership: " + checks + " checks passed");
    }
}
