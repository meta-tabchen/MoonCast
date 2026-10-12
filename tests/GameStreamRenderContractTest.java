/* SPDX-License-Identifier: GPL-3.0-only */
package com.mooncast.tv.gamestream.render;

import java.util.Arrays;
import java.util.Random;

/** Generated boundary fixtures, not decodable video or device/interoperability evidence. */
public final class GameStreamRenderContractTest {
    private static final byte[] SPS = {0, 0, 0, 1, 0x67, 66, 0, 31, (byte) 0x80};
    private static final byte[] PPS = {0, 0, 1, 0x68, (byte) 0x80};
    private static final byte[] IDR = {0, 0, 0, 1, 0x65, (byte) 0x80};
    private static final byte[] P = {0, 0, 1, 0x41, (byte) 0x80};
    private static int assertions;
    public static void main(String[] args) {
        separateParameterSetsAndSlices(); borrowedArraysAreCopied(); requiresCompleteConfigAndIdr();
        malformedAndUnsupportedNal(); boundsAndFrameCounterWrap(); videoQueueOverflowAndRecovery();
        absentSurfaceKeepsOnlyConfig(); videoByteBound(); concurrentQueueReset();
        pcmCopiesAndBounds(); pcmStress(); honestTiming();
        System.out.println("GameStream render boundary tests passed (" + assertions + " assertions). No device/latency claims.");
    }
    private static void separateParameterSetsAndSlices() {
        DecodeUnitAssembler a = new DecodeUnitAssembler();
        eq(null, accept(a, SPS, 1, 1, 1)); eq(null, accept(a, PPS, 2, 1, 1));
        DecodeUnitAssembler.AccessUnit unit = accept(a, cat(IDR, IDR), 0, 1, 1);
        check(unit.keyFrame); bytes(cat(SPS, PPS), unit.codecConfig); bytes(cat(IDR, IDR), unit.picture);
        a.markQueued(unit); eq(null, accept(a, P, 0, 2, 0).codecConfig);
        a.requireIdr(); reject(() -> accept(a, P, 0, 3, 0));
        unit = accept(a, IDR, 0, 4, 1); bytes(cat(SPS, PPS), unit.codecConfig);
    }
    private static void borrowedArraysAreCopied() {
        DecodeUnitAssembler a = new DecodeUnitAssembler(); byte[] borrowed = new byte[64];
        System.arraycopy(SPS, 0, borrowed, 0, SPS.length); a.accept(borrowed, SPS.length, 1, 1, 1);
        System.arraycopy(PPS, 0, borrowed, 0, PPS.length); a.accept(borrowed, PPS.length, 2, 1, 1);
        System.arraycopy(IDR, 0, borrowed, 0, IDR.length);
        DecodeUnitAssembler.AccessUnit unit = a.accept(borrowed, IDR.length, 0, 1, 1);
        Arrays.fill(borrowed, (byte) 99); bytes(cat(SPS, PPS), unit.codecConfig); bytes(IDR, unit.picture);
    }
    private static void requiresCompleteConfigAndIdr() {
        reject(() -> accept(new DecodeUnitAssembler(), IDR, 0, 1, 1));
        DecodeUnitAssembler a = new DecodeUnitAssembler(); accept(a, SPS, 1, 1, 1);
        reject(() -> accept(a, IDR, 0, 1, 1));
        a.requireIdr(); accept(a, SPS, 1, 2, 1); accept(a, PPS, 2, 2, 1); accept(a, IDR, 0, 2, 1);
        reject(() -> accept(a, P, 0, 3, 0));
        a.requireIdr(); DecodeUnitAssembler.AccessUnit idr = accept(a, IDR, 0, 4, 1); a.markQueued(idr);
        check(!accept(a, P, 0, 5, 0).keyFrame);
        accept(a, SPS, 1, 6, 1); reject(() -> accept(a, IDR, 0, 6, 1));
    }
    private static void malformedAndUnsupportedNal() {
        reject(() -> accept(new DecodeUnitAssembler(), SPS, 3, 1, 1));
        reject(() -> accept(new DecodeUnitAssembler(), new byte[]{0x65, 1, 2, 3}, 0, 1, 1));
        reject(() -> accept(new DecodeUnitAssembler(), new byte[]{0,0,1,(byte)0xe5,1}, 0, 1, 1));
        reject(() -> accept(new DecodeUnitAssembler(), PPS, 1, 1, 1));
        byte[] high = {0,0,1,0x67,100,0,31,(byte)0xac};
        eq(null, accept(new DecodeUnitAssembler(), high, 1, 1, 1));
        byte[] high10 = high.clone(); high10[4] = 110;
        reject(() -> accept(new DecodeUnitAssembler(), high10, 1, 1, 1));
        high[7] = (byte)0x93; reject(() -> accept(new DecodeUnitAssembler(), high, 1, 1, 1));
        DecodeUnitAssembler a = ready(); reject(() -> accept(a, IDR, 0, 1, 1));
        reject(() -> accept(a, P, 0, 2, 1)); reject(() -> accept(a, IDR, 0, 3, 0));
        reject(() -> accept(a, IDR, 0, 4, 100));
    }
    private static void boundsAndFrameCounterWrap() {
        DecodeUnitAssembler a = new DecodeUnitAssembler();
        reject(() -> a.accept(null, 1, 1, 1, 1)); reject(() -> a.accept(SPS, SPS.length + 1, 1, 1, 1));
        reject(() -> a.accept(new byte[DecodeUnitAssembler.MAX_PICTURE_BYTES + 1], DecodeUnitAssembler.MAX_PICTURE_BYTES+1, 0, 1, 1));
        byte[] large = new byte[DecodeUnitAssembler.MAX_CONFIG_BYTES]; System.arraycopy(SPS,0,large,0,SPS.length);
        accept(a, large, 1, 1, 1); reject(() -> accept(a, PPS, 2, 1, 1));
        DecodeUnitAssembler wrapped = new DecodeUnitAssembler(); accept(wrapped,SPS,1,Integer.MAX_VALUE,1); accept(wrapped,PPS,2,Integer.MAX_VALUE,1);
        wrapped.markQueued(accept(wrapped,IDR,0,Integer.MAX_VALUE,1)); check(accept(wrapped,P,0,Integer.MIN_VALUE,0) != null);
    }
    private static void videoQueueOverflowAndRecovery() {
        VideoIngress q = new VideoIngress(); offerIdr(q, 1, true);
        eq(0, q.accept(P,P.length,0,2,0,0)); eq(0,q.accept(P,P.length,0,3,0,0));
        long old = q.generation(); eq(-1,q.accept(P,P.length,0,4,0,0));
        eq(0,q.size()); eq(0,q.bytes()); eq(4L,q.droppedPictures()); check(q.generation()>old);
        long recoveryGeneration=q.generation(); eq(-1,q.accept(P,P.length,0,5,0,0));
        eq(recoveryGeneration,q.generation()); check(q.awaitingIdr());
        eq(0,q.accept(IDR,IDR.length,0,6,1,0)); check(!q.awaitingIdr());
        VideoIngress.Frame recovered=q.poll(); check(recovered.unit.keyFrame); bytes(cat(SPS,PPS),recovered.unit.codecConfig);
        q.dropInFlight(); check(q.awaitingIdr()); check(q.generation()>recovered.generation);
        q.close(); eq(-1,q.accept(IDR,IDR.length,0,7,1,0)); eq(null,q.poll());
    }
    private static void absentSurfaceKeepsOnlyConfig() {
        VideoIngress q = new VideoIngress(); offerIdr(q,1,false);
        eq(0,q.size()); eq(0,q.bytes()); check(q.awaitingIdr());
        eq(-1,q.accept(P,P.length,0,2,0,0,false));
        eq(0,q.accept(IDR,IDR.length,0,3,1,0,true));
        bytes(cat(SPS,PPS),q.poll().unit.codecConfig);
    }
    private static void videoByteBound() {
        VideoIngress q=new VideoIngress(); offerIdr(q,1,true); q.poll();
        byte[] big=new byte[DecodeUnitAssembler.MAX_PICTURE_BYTES]; Arrays.fill(big,(byte)42);
        System.arraycopy(P,0,big,0,P.length);
        eq(0,q.accept(big,big.length,0,2,0,0)); eq(0,q.accept(big,big.length,0,3,0,0));
        eq(VideoIngress.MAX_BYTES,q.bytes()); eq(-1,q.accept(P,P.length,0,4,0,0)); eq(0,q.bytes());
    }
    private static void concurrentQueueReset() {
        VideoIngress q=new VideoIngress(); offerIdr(q,1,true);
        Thread consumer=new Thread(()->{for(int i=0;i<5_000;i++){q.poll();if(i%11==0)q.requireIdr();}});
        consumer.start();
        for(int i=2;i<5_000;i++) { if(i%7==0) q.accept(IDR,IDR.length,0,i,1,i); else q.accept(P,P.length,0,i,0,i); }
        try {consumer.join(5_000);}catch(InterruptedException e){throw new AssertionError(e);}
        check(!consumer.isAlive()); check(q.size()<=VideoIngress.MAX_FRAMES); check(q.bytes()<=VideoIngress.MAX_BYTES);
    }
    private static void pcmCopiesAndBounds() {
        PcmQueue q=new PcmQueue(2,4); short[] a={1,2,3,4};q.offer(a,1);a[0]=99;
        eq((short)1,q.poll().samples[0]);q.offer(a,2);q.offer(a,3);q.offer(a,4);
        eq(4,q.queuedFrames());eq(2L,q.droppedFrames());eq(3L,q.poll().receivedNs);eq(4L,q.poll().receivedNs);
        short[] oversized=new short[10];q.offer(oversized,5);eq(7L,q.droppedFrames());eq(0,q.size());
        reject(()->q.offer(new short[1],6));reject(()->q.offer(null,6));
        q.offer(a,7);q.discardQueued();eq(9L,q.droppedFrames());eq(0,q.queuedFrames());
        q.close();q.offer(a,8);eq(0,q.size());
    }
    private static void pcmStress() {
        PcmQueue q=new PcmQueue(2,2_880);Random r=new Random(37);
        for(int i=0;i<10_000;i++){
            int frames=1+r.nextInt(480);short[] samples=new short[frames*2];q.offer(samples,i);
            if(r.nextBoolean())q.poll();check(q.queuedFrames()<=2880);check(q.size()<=PcmQueue.MAX_BLOCKS);
        }
        q.close();eq(0,q.queuedFrames());
    }
    private static void honestTiming() {
        check(!GameStreamTiming.LOW_LATENCY.supportsSignedOffset);check(!GameStreamTiming.LOW_LATENCY.measuredSourceClockSync);
        eq(0,GameStreamTiming.withRelativeAudioOffsetMs(0).relativeAudioOffsetMs);
        reject(()->GameStreamTiming.withRelativeAudioOffsetMs(-1));reject(()->GameStreamTiming.withRelativeAudioOffsetMs(1));
    }
    private static void offerIdr(VideoIngress q,int number,boolean surface){
        eq(0,q.accept(SPS,SPS.length,1,number,1,0,surface));eq(0,q.accept(PPS,PPS.length,2,number,1,0,surface));
        eq(surface?0:-1,q.accept(IDR,IDR.length,0,number,1,0,surface));
    }
    private static DecodeUnitAssembler ready(){DecodeUnitAssembler a=new DecodeUnitAssembler();accept(a,SPS,1,1,1);accept(a,PPS,2,1,1);accept(a,IDR,0,1,1);return a;}
    private static DecodeUnitAssembler.AccessUnit accept(DecodeUnitAssembler a,byte[] d,int type,int frame,int kind){return a.accept(d,d.length,type,frame,kind);}
    private static byte[] cat(byte[]... arrays){int n=0;for(byte[] a:arrays)n+=a.length;byte[] b=new byte[n];int p=0;for(byte[] a:arrays){System.arraycopy(a,0,b,p,a.length);p+=a.length;}return b;}
    private static void bytes(byte[] a,byte[] b){check(Arrays.equals(a,b));}
    private static void eq(Object a,Object b){check(a==null?b==null:a.equals(b));}
    private static void check(boolean v){assertions++;if(!v)throw new AssertionError("Assertion "+assertions+" failed");}
    private static void reject(Runnable r){try{r.run();throw new AssertionError("Expected rejection");}catch(IllegalArgumentException expected){assertions++;}}
}
