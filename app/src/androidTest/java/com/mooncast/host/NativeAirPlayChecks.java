package com.mooncast.host;

import airplaylib.Airplaylib;
import airplaylib.EventHandler;
import airplaylib.Session;
import android.os.SystemClock;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Loads the packaged JNI runtime on Android and exercises generated loopback
 * connection cancellation. It never pairs, captures, or contacts a LAN receiver. */
final class NativeAirPlayChecks {
    static void verify(android.app.Instrumentation instrumentation) throws Exception {
        Airplaylib.setAirPlay1Mode(false);
        AtomicInteger connected = new AtomicInteger();
        EventHandler events = new EventHandler() {
            public void onConnected() { connected.incrementAndGet(); }
            public void onDeviceFound(String json) {}
            public void onDisconnected(String reason) {}
            public void onError(String reason) {}
            public void onLog(String message) {}
            public void onPinRequired() { throw new AssertionError("Loopback must not pair"); }
        };
        Session session = Airplaylib.newSession(events);
        if (session.hasAudio() || session.streamWidth() != 0 || session.streamHeight() != 0) throw new AssertionError("new session state");
        session.sendFrame(new byte[]{0,0,0,1,0x65,42},true);
        session.sendAudio(new byte[1408]);
        session.disconnect(); session.disconnect();
        audioTransportEnded(instrumentation, session);
        CountDownLatch requested = new CountDownLatch(1), closed = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        try (ServerSocket server = new ServerSocket(0,1,java.net.InetAddress.getByName("127.0.0.1"))) {
            server.setSoTimeout(15000);
            Thread fixture = new Thread(() -> {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(15000);
                    if (socket.getInputStream().read() < 0) throw new AssertionError("Missing /info request");
                    requested.countDown();
                    while (socket.getInputStream().read() >= 0) {}
                    closed.countDown();
                } catch (Throwable failure) { error.set(failure); requested.countDown(); closed.countDown(); }
            },"AirPlayLoopbackFixture"); fixture.start();
            Session pending = Airplaylib.newSession(events);
            try {
                pending.connect("127.0.0.1",server.getLocalPort(),"",1280,720,30);
                if (!requested.await(15,TimeUnit.SECONDS)) throw new AssertionError("Native handshake never reached loopback");
                if (error.get()!=null) throw new AssertionError(error.get());
                pending.disconnect();
                if (!closed.await(5,TimeUnit.SECONDS)) throw new AssertionError("Disconnect retained a blocked RTSP socket");
                if (error.get()!=null) throw new AssertionError(error.get());
                SystemClock.sleep(250);
                if (connected.get()!=0) throw new AssertionError("Canceled handshake reported connected");
                pending.disconnect();
            } finally { pending.disconnect(); fixture.join(1000); }
        }
    }
    private static void audioTransportEnded(android.app.Instrumentation instrumentation, Session transport) throws Exception {
        android.content.Context context = instrumentation.getTargetContext();
        AirPlayHostService service = new AirPlayHostService();
        var attach = android.content.ContextWrapper.class.getDeclaredMethod("attachBaseContext", android.content.Context.class);
        attach.setAccessible(true); attach.invoke(service, context);
        LocalAudioMute mute = new LocalAudioMute(context);
        android.media.AudioManager manager = context.getSystemService(android.media.AudioManager.class);
        int original = manager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);
        CountDownLatch audioClosed = new CountDownLatch(1);
        AirPlayPlaybackAudio recording = new AirPlayPlaybackAudio() {
            @Override public void close() { super.close(); audioClosed.countDown(); }
        };
        AirPlayVideoEncoder video = new AirPlayVideoEncoder(context, new AirPlayVideoEncoder.Output() {
            public void video(byte[] bytes, boolean keyframe) {}
            public void error(String message) {}
            public void log(String message) {}
        });
        put(service,"session",transport); put(service,"localMute",mute); put(service,"audio",recording); put(service,"video",video);
        var generation = AirPlayHostService.class.getDeclaredField("generation"); generation.setAccessible(true);
        int token = ((AtomicInteger)generation.get(service)).incrementAndGet();
        var watch = AirPlayHostService.class.getDeclaredMethod("watchAudio",Session.class,int.class); watch.setAccessible(true);
        try {
            if (!manager.isVolumeFixed()) mute.silence();
            instrumentation.runOnMainSync(() -> { try { watch.invoke(service,transport,token); } catch(Exception failure) { throw new IllegalStateException(failure); } });
            if (!audioClosed.await(5,TimeUnit.SECONDS)) throw new AssertionError("Lost native audio did not close capture");
            var audio = AirPlayHostService.class.getDeclaredField("audio"); audio.setAccessible(true);
            var retained = AirPlayHostService.class.getDeclaredField("video"); retained.setAccessible(true);
            if (audio.get(service)!=null || retained.get(service)!=video) throw new AssertionError("Audio failure ended video or retained capture");
            if (manager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)!=original) throw new AssertionError("Phone remained muted after transport audio ended");
            String diagnostic = AirPlayHostService.boundedDiagnostic("x".repeat(100000));
            if (diagnostic.length()!=320) throw new AssertionError("Unbounded diagnostic IPC");
        } finally { mute.restore(); recording.close(); video.close(); }
    }
    private static void put(AirPlayHostService service, String name, Object value) throws Exception {
        var field=AirPlayHostService.class.getDeclaredField(name); field.setAccessible(true); field.set(service,value);
    }

}
