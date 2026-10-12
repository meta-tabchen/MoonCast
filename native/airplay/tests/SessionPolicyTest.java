/* SPDX-License-Identifier: GPL-3.0-only */
package com.mooncast.tv.airplay;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
/** No Android/JNI calls. Exercises actual session policy and packed native debug parser. */
public final class SessionPolicyTest {
    public static void main(String[] args) {
        staticScreen(); disconnect(); reconnect(); multipleConnections(); decoderStall(); disabled(); timing(); nativeLayout();
        System.out.println("AirPlay session policy / timing / native metrics tests passed");
    }
    private static void staticScreen() {
        SessionWatchdog w = new SessionWatchdog();
        w.connectionOpened(); w.beginMirror(0); w.videoPacket(1,true); w.videoOutput();
        check(w.check(600_000)==null,"static screen with open control connection");
        w.reset(); w.beginMirror(0); w.videoPacket(1,true); w.videoOutput();
        check(w.check(600_000)==null,"no connection callback is not a witnessed disconnect");
    }
    private static void disconnect() {
        SessionWatchdog w = new SessionWatchdog();
        w.connectionOpened(); w.beginMirror(0); w.videoPacket(1,true); w.videoOutput(); w.connectionClosed(1000);
        check(w.check(15_999)==null,"full disconnect grace");
        check("Sender disconnected".equals(w.check(16_000)),"disconnect recovery");
        check(w.check(60_000)==null,"only one recovery request");
    }
    private static void reconnect() {
        SessionWatchdog w = new SessionWatchdog();
        w.connectionOpened(); w.beginMirror(0); w.connectionClosed(1000); w.connectionOpened();
        check(w.check(60_000)==null,"reconnection cancels deadline");
        w.connectionClosed(61_000); w.videoPacket(75_000,true); w.videoOutput();
        check(w.check(76_001)==null,"fresh packets keep closed-control session alive");
        check(w.check(90_000)!=null,"later absence expires");
    }
    private static void multipleConnections() {
        SessionWatchdog w=new SessionWatchdog();w.connectionOpened();w.connectionOpened();w.beginMirror(0);
        w.connectionClosed(1000);check(w.connections()==1,"one control socket remains");
        check(w.check(60000)==null,"HLS/auxiliary connection close is not mirror disconnect");
        w.connectionClosed(61000);check(w.check(76000)!=null,"last connection close expires");
    }
    private static void decoderStall() {
        SessionWatchdog w = new SessionWatchdog();
        w.connectionOpened(); w.beginMirror(0); w.videoPacket(1,false);
        check(w.check(60_000)==null,"codec config is not a video frame");
        w.videoPacket(60_001,true); w.videoPacket(74_000,true);
        check(w.check(75_000)==null,"full decoder grace");
        check(w.check(75_001)!=null,"no decoder output recovers");
        w.reset(); w.connectionOpened(); w.beginMirror(0); w.videoPacket(1,true); w.videoOutput();
        check(w.check(60_000)==null,"rendered paused frame is retained");
    }
    private static void disabled() {
        SessionWatchdog w = new SessionWatchdog();
        w.setTimeoutSeconds(0); w.beginMirror(0); w.videoPacket(1,true);
        check(w.check(60_000)==null,"watchdog disabled");
        w.setTimeoutSeconds(5); check(w.check(6000)!=null,"configured timeout");
        w.reset(); check(w.check(120_000)==null,"stop clears timers");
        w.beginMirror(0); w.connectionClosed(1); w.endMirror();
        check(w.check(120_000)==null,"HLS/ended mirror has no watchdog");
        boolean rejected=false;try{w.setTimeoutSeconds(2);}catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"invalid timeout rejected");
    }
    private static void timing() {
        VideoTiming t=new VideoTiming();long now=5_000_000_000L,pts=1_800_000_000_000_000L;
        check(t.target(pts,now)==now+150_000_000L,"movie video real holdback");
        check(t.target(pts+33_333,now+30_000_000L)==now+183_333_000L,"source PTS pacing");
        check(t.target(pts+60_000_000,now+50_000_000L)==now+200_000_000L,"forward jump bounded");
        check(t.target(0,now+100_000_000L)==now+250_000_000L,"backward jump bounded");
        t.configure(false,0);check(t.target(pts,now)==now,"low latency immediate output");
        t.configure(true,500);check(t.target(pts,now)==now+500_000_000L,"real maximum holdback");
    }
    private static void nativeLayout() {
        ByteBuffer b=ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
        b.putShort((short)150).putShort((short)200).putInt(-1).putInt(2).putInt(3).putInt(4)
                .putInt(500).putInt(900).putShort((short)7).putInt(8).putInt(9);b.rewind();
        NativeAudioStats a=NativeAudioStats.read(b);
        check(a.backlogMs==150&&a.cushionMs==200,"u16 header layout");
        check(a.trims==0xffffffffL&&a.droppedPackets==2&&a.insertedSilences==3&&a.underruns==4,"u32 counters");
        check(a.decodeMeanUs==500&&a.decodeMaxUs==900&&a.heldBuffers==7&&a.xruns==8&&a.decodeErrors==9,"packed tail has no padding");
        check(b.position()==0,"reader preserves buffer position");
        b.limit(37);boolean rejected=false;try{NativeAudioStats.read(b);}catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"reject incomplete packed native record");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
