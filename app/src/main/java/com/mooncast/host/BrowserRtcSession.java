package com.mooncast.host;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.media.AudioFormat;
import android.media.projection.MediaProjection;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Surface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.EglBase;
import org.webrtc.HardwareVideoEncoderFactory;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpCapabilities;
import org.webrtc.RtpParameters;
import org.webrtc.RtpSender;
import org.webrtc.RtpTransceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.audio.AudioProcessingOptions;
import org.webrtc.audio.JavaAudioDeviceModule;

/** One native screen source and one LAN WebRTC receiver. Projection ownership stays in the service. */
final class BrowserRtcSession implements AutoCloseable {
    interface Listener {
        void state(String state);
        void error(String message);
        default void audioUnavailable(String message) { }
    }
    static final class BusyException extends Exception {
        BusyException() { super("A browser is already connected"); }
    }
    private static final long LEASE_MS = 20_000;
    private static final long NEGOTIATION_MS = 10_000;
    private static boolean initialized;
    private final Context context;
    private final MediaProjection projection;
    private final boolean audioRequested;
    private final int width, height, fps;
    private final BrowserQuality quality;
    private final Listener listener;
    private final BrowserPlaybackAudio playback = new BrowserPlaybackAudio();
    private final ScheduledExecutorService leases = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "BrowserViewerLease"); thread.setDaemon(true); return thread;
    });
    private EglBase egl;
    private PeerConnectionFactory factory;
    private JavaAudioDeviceModule audioDevice;
    private SurfaceTextureHelper textures;
    private Surface surface;
    private VideoSource videoSource;
    private VideoTrack videoTrack;
    private AudioSource audioSource;
    private AudioTrack audioTrack;
    private volatile VideoPipeline pipeline;
    private volatile Session current;
    private volatile boolean closed;
    private volatile String audioError = "";
    private boolean started;
    private boolean wholeScreen = true;

    private static final class Session {
        final String id = UUID.randomUUID().toString();
        final String viewerId;
        final CountDownLatch iceComplete = new CountDownLatch(1);
        volatile String state = "connecting", aborted;
        volatile long heartbeat = SystemClock.elapsedRealtime();
        volatile SdpResult pending;
        PeerConnection peer;
        Session(String viewerId) { this.viewerId = viewerId; }
        void abort(String reason) {
            aborted = reason;
            SdpResult result = pending;
            if (result != null) result.onSetFailure(reason);
            iceComplete.countDown();
        }
    }

    BrowserRtcSession(Context context, MediaProjection projection, boolean audio,
            int width, int height, BrowserQuality quality, Listener listener) {
        this.context = context.getApplicationContext();
        this.projection = projection;
        this.audioRequested = audio;
        this.width = Math.max(2, Math.min(quality.longEdge, width) / 2 * 2);
        this.height = Math.max(2, Math.min(quality.longEdge, height) / 2 * 2);
        this.quality = quality;
        this.fps = quality.fps;
        this.listener = listener;
    }

    private static synchronized void initialize(Context context) {
        if (initialized) return;
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(false).createInitializationOptions());
        initialized = true;
    }

    synchronized void setWholeScreen(boolean value) {
        if (started) throw new IllegalStateException("Capture source is already active");
        wholeScreen = value;
    }

    synchronized void startCapture() throws Exception {
        if (closed || started) throw new IllegalStateException("Browser capture is not startable");
        try {
            initialize(context);
            egl = EglBase.create();
            HardwareVideoEncoderFactory encoders = new HardwareVideoEncoderFactory(egl.getEglBaseContext(), true, false);
            boolean browserCodec = false;
            for (org.webrtc.VideoCodecInfo codec : encoders.getSupportedCodecs()) {
                if ("H264".equalsIgnoreCase(codec.name) || "VP8".equalsIgnoreCase(codec.name)) browserCodec = true;
            }
            if (!browserCodec)
                throw new IllegalStateException("No compatible hardware WebRTC video encoder");
            audioDevice = JavaAudioDeviceModule.builder(context)
                .setSampleRate(BrowserPlaybackAudio.SAMPLE_RATE).setUseStereoInput(true)
                .setUseHardwareAcousticEchoCanceler(false).setUseHardwareNoiseSuppressor(false)
                .setAudioFormat(AudioFormat.ENCODING_PCM_16BIT).setEnableVolumeLogger(false)
                .setAudioBufferCallback((buffer, format, channels, rate, size, timestamp) ->
                    playback.fill(buffer, format, channels, rate)).createAudioDeviceModule();
            // This must happen before a PeerConnection or AudioTrack can start the ADM.
            audioDevice.setAudioRecordEnabled(false);
            PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
            options.networkIgnoreMask = PeerConnectionFactory.Options.ADAPTER_TYPE_CELLULAR;
            factory = PeerConnectionFactory.builder().setOptions(options).setAudioDeviceModule(audioDevice)
                .setVideoEncoderFactory(encoders)
                .setVideoDecoderFactory(new DefaultVideoDecoderFactory(egl.getEglBaseContext()))
                .createPeerConnectionFactory();
            videoSource = factory.createVideoSource(true);
            videoSource.adaptOutputFormat(width, height, fps);
            videoTrack = factory.createVideoTrack("mooncast-screen", videoSource);
            textures = SurfaceTextureHelper.create("BrowserScreenTexture", egl.getEglBaseContext());
            if (textures == null) throw new IllegalStateException("WebRTC texture helper unavailable");
            textures.setTextureSize(width, height);
            textures.startListening(frame -> {
                if (!closed) videoSource.getCapturerObserver().onFrameCaptured(frame);
            });
            surface = new Surface(textures.getSurfaceTexture());
            if (audioRequested) {
                try {
                    playback.start(context, projection, this::audioUnavailable);
                    audioSource = factory.createAudioSource(new MediaConstraints());
                    audioTrack = factory.createAudioTrack("mooncast-playback", audioSource);
                    audioTrack.setAudioProcessingOptions(AudioProcessingOptions.raw());
                } catch (RuntimeException error) {
                    playback.close();
                    audioUnavailable(error.getMessage());
                }
            }
            videoSource.getCapturerObserver().onCapturerStarted(true);
            pipeline = new VideoPipeline(context, surface, width, height, CropGeometry.SCREEN,
                new VideoPipeline.Events() {
                    @Override public void message(String message) { }
                    @Override public void error(String message) { if (!closed) listener.error(message); }
                });
            pipeline.setFrameLimit(fps);
            DisplayMetrics display = VideoPipeline.metrics(context.getSystemService(DisplayManager.class));
            pipeline.start(display.widthPixels, display.heightPixels, wholeScreen,
                (input, w, h) -> projection.createVirtualDisplay("MoonCast Browser", w, h, display.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, input, null, new Handler(Looper.getMainLooper())));
            started = true;
            leases.scheduleWithFixedDelay(this::expireViewer, 2, 2, TimeUnit.SECONDS);
            listener.state("waiting");
        } catch (Exception | LinkageError error) {
            close();
            throw error;
        }
    }

    private void audioUnavailable(String reason) {
        audioError = reason == null ? "Playback audio unavailable" : reason;
        if (!closed) listener.audioUnavailable(audioError);
    }

    /** Called on the bounded HTTP worker, never Android's main thread. Non-trickle SDP includes ICE. */
    synchronized String answer(String offerSdp, String previousSessionId, String viewerId) throws Exception {
        if (closed || !started) throw new IllegalStateException("Browser capture is stopped");
        if (viewerId == null || !viewerId.matches("[a-f0-9]{32}"))
            throw new IllegalArgumentException("Invalid browser viewer ID");
        if (offerSdp == null || offerSdp.length() < 10 || offerSdp.length() > 131_072)
            throw new IllegalArgumentException("Invalid SDP offer");
        Session old = current;
        // The stable per-tab ID also recovers an answer whose HTTP response never arrived.
        if (old != null && !old.id.equals(previousSessionId) && !old.viewerId.equals(viewerId)
                && SystemClock.elapsedRealtime() - old.heartbeat < LEASE_MS) throw new BusyException();
        closePeer();
        Session session = new Session(viewerId);
        current = session;
        listener.state("connecting");
        try {
            PeerConnection.RTCConfiguration config = new PeerConnection.RTCConfiguration(Collections.emptyList());
            config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
            config.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
            config.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
            config.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_ONCE;
            config.enableCpuOveruseDetection = true;
            config.screencastMinBitrate = 300; // libwebrtc expects kbps here, unlike setBitrate below.
            session.peer = factory.createPeerConnection(config, observer(session));
            if (session.peer == null) throw new IllegalStateException("WebRTC could not create the peer");
            PeerConnection peer = session.peer;
            peer.setAudioPlayout(false);
            RtpSender sender = peer.addTrack(videoTrack, Collections.singletonList("mooncast"));
            if (sender == null) throw new IllegalStateException("WebRTC could not add video");
            if (audioTrack != null && playback.available()) peer.addTrack(audioTrack, Collections.singletonList("mooncast"));
            if (!peer.setBitrate(300_000, quality.initialBitrateBps(), quality.maxBitrateBps))
                throw new IllegalStateException("WebRTC rejected browser bitrate limits");
            long deadline = SystemClock.elapsedRealtime() + NEGOTIATION_MS;
            SdpResult remote = pending(session);
            peer.setRemoteDescription(remote, new SessionDescription(SessionDescription.Type.OFFER, offerSdp));
            await(session, remote, deadline);
            // An incoming offer cannot turn this screen sender into a microphone/camera receiver.
            for (RtpTransceiver transceiver : peer.getTransceivers()) {
                MediaStreamTrack track = transceiver.getSender().track();
                transceiver.setDirection(track == null ? RtpTransceiver.RtpTransceiverDirection.INACTIVE
                    : RtpTransceiver.RtpTransceiverDirection.SEND_ONLY);
                if (transceiver.getMediaType() == MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO) {
                    List<RtpCapabilities.CodecCapability> codecs = new ArrayList<>(
                        factory.getRtpSenderCapabilities(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO).codecs);
                    boolean avc = BrowserEncoderCapabilities.supportsMime(context, quality, "video/avc");
                    boolean vp8 = BrowserEncoderCapabilities.supportsMime(context, quality, "video/x-vnd.on2.vp8");
                    codecs.removeIf(codec -> ("H264".equalsIgnoreCase(codec.name) && !avc)
                        || ("VP8".equalsIgnoreCase(codec.name) && !vp8)
                        || "VP9".equalsIgnoreCase(codec.name) || "AV1".equalsIgnoreCase(codec.name));
                    codecs.sort(Comparator.comparingInt(codec -> "H264".equalsIgnoreCase(codec.name) ? 0 : 1));
                    transceiver.setCodecPreferences(codecs);
                }
            }
            SdpResult answer = pending(session);
            peer.createAnswer(answer, new MediaConstraints());
            await(session, answer, deadline);
            SdpResult local = pending(session);
            peer.setLocalDescription(local, answer.description);
            await(session, local, deadline);
            long remaining = deadline - SystemClock.elapsedRealtime();
            if (remaining <= 0 || !session.iceComplete.await(remaining, TimeUnit.MILLISECONDS))
                throw new IllegalStateException("Local ICE gathering timed out");
            ensureActive(session);
            SessionDescription description = peer.getLocalDescription();
            if (description == null || !description.description.contains("a=candidate:"))
                throw new IllegalStateException("No local ICE network candidates available");
            RtpParameters parameters = sender.getParameters();
            for (RtpParameters.Encoding encoding : parameters.encodings) {
                encoding.maxBitrateBps = quality.maxBitrateBps;
                encoding.maxFramerate = fps;
            }
            if (!sender.setParameters(parameters))
                throw new IllegalStateException("WebRTC rejected browser video parameters");
            session.heartbeat = SystemClock.elapsedRealtime();
            return new JSONObject().put("id", session.id).put("type", "answer")
                .put("sdp", description.description).put("audio", audioTrack != null && playback.available())
                .put("audioError", audioError)
                .put("quality", new JSONObject().put("longEdge", quality.longEdge)
                    .put("maxFps", fps).put("maxVideoBitrateBps", quality.maxBitrateBps))
                .toString();
        } catch (Exception | LinkageError error) {
            if (current == session) closePeer();
            if (!closed) listener.state("waiting");
            throw error;
        } finally { session.pending = null; }
    }

    private SdpResult pending(Session session) {
        SdpResult result = new SdpResult(); session.pending = result;
        if (closed || session.aborted != null) result.onSetFailure("Browser sharing stopped");
        return result;
    }

    private void await(Session session, SdpResult result, long deadline) throws Exception {
        long remaining = deadline - SystemClock.elapsedRealtime();
        if (remaining <= 0 || !result.done.await(remaining, TimeUnit.MILLISECONDS))
            throw new IllegalStateException("WebRTC negotiation timed out");
        ensureActive(session);
        if (result.error != null) throw new IllegalArgumentException("WebRTC SDP: " + result.error);
    }

    private void ensureActive(Session session) {
        if (closed || current != session || session.aborted != null)
            throw new IllegalStateException("Browser session was stopped");
    }

    synchronized String status(String id) throws Exception {
        Session session = current;
        boolean matches = session != null && session.id.equals(id);
        if (matches) session.heartbeat = SystemClock.elapsedRealtime();
        return new JSONObject().put("state", closed ? "stopped" : matches ? session.state : "missing")
            .put("active", matches).put("busy", session != null && !matches)
            .put("audio", audioTrack != null && playback.available()).put("audioError", audioError)
                .put("quality", new JSONObject().put("longEdge", quality.longEdge)
                    .put("maxFps", fps).put("maxVideoBitrateBps", quality.maxBitrateBps))
                .toString();
    }

    synchronized void disconnect(String id) {
        if (current == null || !current.id.equals(id)) return;
        closePeer();
        if (!closed) listener.state("waiting");
    }

    private synchronized void expireViewer() {
        if (closed || current == null) return;
        if (SystemClock.elapsedRealtime() - current.heartbeat >= LEASE_MS) {
            closePeer(); listener.state("waiting");
        }
    }

    private PeerConnection.Observer observer(Session session) {
        return new PeerConnection.Observer() {
            @Override public void onConnectionChange(PeerConnection.PeerConnectionState state) {
                if (closed || current != session) return;
                session.state = state.name().toLowerCase(java.util.Locale.ROOT);
                listener.state(session.state);
            }
            @Override public void onIceGatheringChange(PeerConnection.IceGatheringState state) {
                if (state == PeerConnection.IceGatheringState.COMPLETE) session.iceComplete.countDown();
            }
            @Override public void onSignalingChange(PeerConnection.SignalingState state) { }
            @Override public void onIceConnectionChange(PeerConnection.IceConnectionState state) { }
            @Override public void onIceConnectionReceivingChange(boolean receiving) { }
            @Override public void onIceCandidate(IceCandidate candidate) { }
            @Override public void onIceCandidatesRemoved(IceCandidate[] candidates) { }
            @Override public void onAddStream(MediaStream stream) { }
            @Override public void onRemoveStream(MediaStream stream) { }
            @Override public void onDataChannel(DataChannel channel) { channel.close(); }
            @Override public void onRenegotiationNeeded() { }
        };
    }

    private static final class SdpResult implements SdpObserver {
        final CountDownLatch done = new CountDownLatch(1);
        volatile SessionDescription description;
        volatile String error;
        @Override public void onCreateSuccess(SessionDescription value) { description = value; done.countDown(); }
        @Override public void onSetSuccess() { done.countDown(); }
        @Override public void onCreateFailure(String reason) { error = reason; done.countDown(); }
        @Override public void onSetFailure(String reason) { error = reason; done.countDown(); }
    }

    void resize(int width, int height) {
        if (closed || width < 1 || height < 1) return;
        // Projection callbacks use the main looper. Serialize native work away from it.
        try { leases.execute(() -> resizeCapture(width, height)); }
        catch (java.util.concurrent.RejectedExecutionException ignored) { }
    }
    private synchronized void resizeCapture(int width, int height) {
        if (closed || pipeline == null || textures == null || videoSource == null) return;
        int[] size = quality.dimensions(width, height);
        int nextWidth = size[0], nextHeight = size[1];
        textures.setTextureSize(nextWidth, nextHeight);
        videoSource.adaptOutputFormat(nextWidth, nextHeight, fps);
        pipeline.resizeOutput(0, nextWidth, nextHeight);
        pipeline.resize(width, height);
    }
    void scale(int mode) { VideoPipeline active = pipeline; if (active != null) active.setMode(mode); }
    void rescan() { VideoPipeline active = pipeline; if (active != null) active.rescan(); }
    FrameStatistics.Sample statistics() {
        VideoPipeline active = pipeline;
        return active == null ? new FrameStatistics.Sample(0, 0, 0) : active.statistics();
    }

    private void closePeer() {
        Session session = current; current = null;
        if (session != null) {
            session.abort("Browser receiver disconnected");
            if (session.peer != null) { session.peer.close(); session.peer.dispose(); session.peer = null; }
        }
    }

    @Override public void close() {
        // Wake negotiation before taking its lock, so Stop does not wait for the ICE timeout.
        closed = true;
        Session session = current;
        if (session != null) session.abort("Browser sharing stopped");
        leases.shutdownNow();
        synchronized (this) {
            closePeer();
            VideoPipeline active = pipeline; pipeline = null;
            if (active != null) active.close();
            if (textures != null) textures.stopListening();
            if (videoSource != null) videoSource.getCapturerObserver().onCapturerStopped();
            playback.close();
            if (audioTrack != null) { audioTrack.dispose(); audioTrack = null; }
            if (videoTrack != null) { videoTrack.dispose(); videoTrack = null; }
            if (audioSource != null) { audioSource.dispose(); audioSource = null; }
            if (videoSource != null) { videoSource.dispose(); videoSource = null; }
            if (surface != null) { surface.release(); surface = null; }
            if (textures != null) { textures.dispose(); textures = null; }
            if (factory != null) { factory.dispose(); factory = null; }
            if (audioDevice != null) { audioDevice.release(); audioDevice = null; }
            if (egl != null) { egl.release(); egl = null; }
        }
    }
}
