/* SPDX-License-Identifier: GPL-3.0-only */
package com.mooncast.tv;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Surface;
import com.limelight.nvstream.jni.MoonBridge;
import com.mooncast.tv.gamestream.GameStreamSurfaces;
import com.mooncast.tv.gamestream.render.GameStreamAudioRenderer;
import com.mooncast.tv.gamestream.render.GameStreamVideoRenderer;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Real Android renderer-boundary tests, with generated local media only.
 * Deliberately never starts TvActivity, host discovery, pairing, or a network session.
 * A codec counter alone is insufficient: decoded red/blue pixels are sampled through GLES.
 * Audio assertions concern successful AudioTrack writes, never audible speaker output.
 */
public final class GameStreamRendererDeviceTest {
    private static final int WIDTH = 160, HEIGHT = 96;
    private static final long DEVICE_TIMEOUT_MS = 30_000;
    private final Instrumentation runner;
    private final JSONArray checks = new JSONArray();
    private final JSONObject report = new JSONObject();
    private File directory;
    private int frameNumber;

    public GameStreamRendererDeviceTest(Instrumentation runner, Bundle arguments) {
        this.runner = runner;
    }

    public void run() {
        Bundle result = new Bundle();
        try {
            directory = new File(runner.getTargetContext().getExternalFilesDir(null),
                    "gamestream-renderer-validation");
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("Cannot create report directory");
            report.put("suite", "generated-media GameStream renderer boundary");
            report.put("sdk", android.os.Build.VERSION.SDK_INT);
            report.put("model", android.os.Build.MODEL);
            report.put("abi", android.os.Build.SUPPORTED_ABIS[0]);
            report.put("checks", checks);
            report.put("scope", "Real Android MediaCodec H.264 decode into SurfaceTexture and GLES pixel readback; "
                    + "real AudioTrack PCM16 writes and renderer lifecycle. No GameStream transport, discovery, "
                    + "pairing, native Opus decoding, speaker audibility, physical TV, A/V synchronization, "
                    + "performance, or interoperability claim.");
            boolean identityBefore = pairingIdentityExists();
            report.put("pairingIdentityPresentBefore", identityBefore);
            videoChecks();
            audioChecks();
            boolean identityAfter = pairingIdentityExists();
            report.put("pairingIdentityPresentAfter", identityAfter);
            check("Renderer tests do not create a GameStream pairing identity", identityAfter == identityBefore);
            report.put("passed", true);
            writeReport();
            result.putString("stream", "GAMESTREAM_RENDERER_VALIDATION_PASSED " + directory + "\n");
            runner.finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            try {
                report.put("passed", false);
                report.put("failure", android.util.Log.getStackTraceString(failure));
                if (directory != null) writeReport();
            } catch (Exception ignored) { }
            result.putString("stream", "GAMESTREAM_RENDERER_VALIDATION_FAILED\n" + android.util.Log.getStackTraceString(failure));
            runner.finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void videoChecks() throws Exception {
        Fixture fixture = readFixture();
        check("Generated Annex-B fixture has distinct SPS, PPS, IDR and P-picture units",
                fixture.sps != null && fixture.pps != null && fixture.idr != null && fixture.p != null);
        AtomicInteger detachEvents = new AtomicInteger(), releaseEvents = new AtomicInteger();
        GameStreamVideoRenderer renderer = new GameStreamVideoRenderer(new GameStreamVideoRenderer.Listener() {
            @Override public void onSurfaceDetached(Surface surface) { detachEvents.incrementAndGet(); }
            @Override public void onReleased() { releaseEvents.incrementAndGet(); }
        });
        PixelSink first = new PixelSink(), second = null;
        try {
            renderer.setSurface(first.surface);
            check("Real H.264 MediaCodec setup succeeds", renderer.setup(MoonBridge.VIDEO_FORMAT_H264, WIDTH, HEIGHT, 30) == 0);
            renderer.start();
            check("No decode output before submitting generated media", renderer.getStats().frames == 0);
            feedUntilImage(renderer, first, fixture);
            assertPixels(first, "first-surface");
            GameStreamVideoRenderer.Stats decoded = renderer.getStats();
            check("Actual codec output reports expected decoded dimensions", decoded.decodedWidth == WIDTH && decoded.decodedHeight == HEIGHT);
            check("Actual output statistics advance", decoded.frames > 0 && decoded.lastOutputNs > 0 && decoded.receivedFrames > 0);
            check("Video renderer does not advertise unimplemented direct-submit features", renderer.getCapabilities() == 0);
            check("Video decode has no runtime error", renderer.getLastError() == null);
            report.put("decoder", decoded.decoderName);
            report.put("decoderImplementation", decoded.implementation);
            report.put("initialDecodedFrames", decoded.frames);

            second = new PixelSink();
            renderer.setSurface(second.surface);
            check("Replacing Surface confirms old decoder detachment", renderer.awaitSurfaceDetached(first.surface, DEVICE_TIMEOUT_MS));
            check("Detached caller-owned Surface remains valid", first.surface.isValid());
            await("New Surface is attached to MediaCodec", () -> renderer.getStats().surfaceAttached, DEVICE_TIMEOUT_MS);
            feedUntilImage(renderer, second, fixture);
            assertPixels(second, "replacement-surface");
            first.close(); first = null;

            renderer.setSurface(null);
            check("Explicit null Surface detaches the replacement", renderer.awaitSurfaceDetached(second.surface, DEVICE_TIMEOUT_MS));
            check("Detached state reports no codec Surface", !renderer.getStats().surfaceAttached);
            int rejected = submit(renderer, fixture.p, MoonBridge.BUFFER_TYPE_PICDATA, MoonBridge.FRAME_TYPE_PFRAME, ++frameNumber);
            check("Picture while detached requests a fresh IDR", rejected == MoonBridge.DR_NEED_IDR);
            check("Detached Surface remains caller-owned before reuse", second.surface.isValid());
            second.clearFrameSignal();
            renderer.setSurface(second.surface);
            await("Same Surface can attach again", () -> renderer.getStats().surfaceAttached, DEVICE_TIMEOUT_MS);
            feedUntilImage(renderer, second, fixture);
            assertPixels(second, "reattached-surface");

            renderer.stop(); renderer.cleanup();
            check("Video stop/cleanup confirms codec release", renderer.awaitReleased(DEVICE_TIMEOUT_MS) && renderer.isReleased());
            check("Cleanup clears pending compressed frames", renderer.getStats().queuedFrames == 0 && renderer.getStats().queuedBytes == 0);
            check("Video teardown retains caller Surface", second.surface.isValid());
            check("Stopped renderer rejects further pictures", submit(renderer, fixture.idr, MoonBridge.BUFFER_TYPE_PICDATA,
                    MoonBridge.FRAME_TYPE_IDR, ++frameNumber) == MoonBridge.DR_NEED_IDR);
            renderer.cleanup();
            check("Repeated video cleanup is safe", renderer.isReleased());
            await("Video release callback arrives", () -> releaseEvents.get() >= 1, DEVICE_TIMEOUT_MS);

            second.clearFrameSignal();
            check("Released renderer can create a fresh codec session", renderer.setup(MoonBridge.VIDEO_FORMAT_H264, WIDTH, HEIGHT, 30) == 0);
            renderer.start(); feedUntilImage(renderer, second, fixture);
            assertPixels(second, "new-session");
            GameStreamSurfaces.retire(second.surface, second.texture, renderer);
            PixelSink retiring = second;
            await("Production surface retirement releases only after codec detaches", () -> !retiring.surface.isValid(), DEVICE_TIMEOUT_MS);
            check("Production retirement clears codec Surface", !renderer.getStats().surfaceAttached);
            second.externallyRetired = true;
            renderer.stop(); renderer.cleanup();
            check("Retired second session releases", renderer.awaitReleased(DEVICE_TIMEOUT_MS));
            await("Second video release callback arrives", () -> releaseEvents.get() >= 2, DEVICE_TIMEOUT_MS);
            check("Surface replacement, detach, cleanup and retirement were observed", detachEvents.get() >= 4);
            report.put("surfaceDetachCallbacks", detachEvents.get());
            report.put("videoReleaseCallbacks", releaseEvents.get());
        } finally {
            renderer.setSurface(null); renderer.stop(); renderer.cleanup();
            boolean safe = renderer.awaitReleased(DEVICE_TIMEOUT_MS);
            // Deliberately retain producer objects if a vendor codec cannot confirm release.
            if (safe) { if (first != null) first.close(); if (second != null) second.close(); }
        }
    }

    private void audioChecks() throws Exception {
        AtomicInteger releases = new AtomicInteger();
        GameStreamAudioRenderer renderer = new GameStreamAudioRenderer(new GameStreamAudioRenderer.Listener() {
            @Override public void onReleased() { releases.incrementAndGet(); }
        });
        try {
            check("Real stereo AudioTrack setup succeeds", renderer.setup(MoonBridge.AUDIO_CONFIGURATION_STEREO, 48_000, 480) == 0);
            check("Initialized AudioTrack has a positive bounded sink buffer",
                    renderer.getStats().outputBufferFrames > 0 && renderer.getStats().outputBufferMs() <= 500);
            renderer.start();
            feedAudioUntilWritten(renderer);
            GameStreamAudioRenderer.Stats written = renderer.getStats();
            check("Real AudioTrack accepts generated stereo PCM frames", written.writtenFrames >= 480 && written.lastWriteNs > 0);
            check("Audio callback reports negotiated sample format", written.sampleRate == 48_000 && written.channels == 2);
            check("Audio software FIFO stays within 60 ms", written.queuedMs() <= 60);
            check("Audio writes have no runtime error", renderer.getLastError() == null);
            report.put("pcmWrittenFrames", written.writtenFrames);
            report.put("audioOutputBufferFrames", written.outputBufferFrames);
            report.put("audioEvidence", "Generated PCM16 stereo is supplied at the native-Opus-decoded callback boundary. "
                    + "Successful AudioTrack writes are checked; Opus decode and audible output are not tested.");
            renderer.stop(); renderer.cleanup();
            check("Audio stop/cleanup releases output", renderer.awaitReleased(DEVICE_TIMEOUT_MS) && renderer.isReleased());
            long stoppedFrames = renderer.getStats().writtenFrames;
            renderer.playDecodedAudio(pcm()); SystemClock.sleep(100);
            check("Stopped audio does not accept late callbacks", renderer.getStats().writtenFrames == stoppedFrames);
            renderer.cleanup(); check("Repeated audio cleanup is safe", renderer.isReleased());
            await("Audio release callback arrives", () -> releases.get() >= 1, DEVICE_TIMEOUT_MS);
            check("Released audio renderer supports a fresh session", renderer.setup(MoonBridge.AUDIO_CONFIGURATION_STEREO, 48_000, 480) == 0);
            renderer.start(); feedAudioUntilWritten(renderer);
            renderer.playDecodedAudio(new short[3]);
            check("Malformed native PCM callback releases output safely", renderer.awaitReleased(DEVICE_TIMEOUT_MS));
            check("Malformed PCM has an explicit diagnostic", renderer.getLastError() != null && renderer.getLastError().contains("frame size"));
            await("Second audio release callback arrives", () -> releases.get() >= 2, DEVICE_TIMEOUT_MS);
            report.put("audioReleaseCallbacks", releases.get());
        } finally { renderer.stop(); renderer.cleanup(); renderer.awaitReleased(DEVICE_TIMEOUT_MS); }
    }

    private void feedAudioUntilWritten(GameStreamAudioRenderer renderer) throws Exception {
        long deadline = SystemClock.uptimeMillis() + DEVICE_TIMEOUT_MS;
        while (renderer.getStats().writtenFrames < 480 && SystemClock.uptimeMillis() < deadline) {
            if (renderer.isReleased()) throw new AssertionError("Audio stopped: " + renderer.getLastError());
            short[] borrowed = pcm(); renderer.playDecodedAudio(borrowed);
            Arrays.fill(borrowed, (short) 0); // JNI is entitled to reuse this array immediately.
            SystemClock.sleep(20);
        }
        check("Audio worker makes real write progress", renderer.getStats().writtenFrames >= 480);
    }

    private static short[] pcm() {
        short[] result = new short[960];
        for (int i = 0; i < 480; i++) {
            result[i * 2] = (short) (3000 * Math.sin(2 * Math.PI * 440 * i / 48_000));
            result[i * 2 + 1] = (short) (3000 * Math.sin(2 * Math.PI * 660 * i / 48_000));
        }
        return result;
    }

    private void feedUntilImage(GameStreamVideoRenderer renderer, PixelSink sink, Fixture fixture) throws Exception {
        int initial = sink.available.get();
        long deadline = SystemClock.uptimeMillis() + DEVICE_TIMEOUT_MS;
        while (sink.available.get() <= initial && SystemClock.uptimeMillis() < deadline) {
            if (renderer.isReleased()) throw new AssertionError("Video stopped: " + renderer.getLastError());
            int number = ++frameNumber;
            submit(renderer, fixture.sps, MoonBridge.BUFFER_TYPE_SPS, MoonBridge.FRAME_TYPE_IDR, number);
            submit(renderer, fixture.pps, MoonBridge.BUFFER_TYPE_PPS, MoonBridge.FRAME_TYPE_IDR, number);
            submit(renderer, fixture.idr, MoonBridge.BUFFER_TYPE_PICDATA, MoonBridge.FRAME_TYPE_IDR, number);
            SystemClock.sleep(100);
        }
        check("MediaCodec produces a real SurfaceTexture buffer", sink.available.get() > initial);
    }

    private static int submit(GameStreamVideoRenderer renderer, byte[] data, int type, int frameType, int number) {
        long nowUs = System.nanoTime() / 1000;
        byte[] borrowed = data.clone();
        int result = renderer.submitDecodeUnit(borrowed, borrowed.length, type, number, frameType, (char) 0, nowUs, nowUs);
        Arrays.fill(borrowed, (byte) 0); // Exercise ownership rather than leaving borrowed storage valid.
        return result;
    }

    private void assertPixels(PixelSink sink, String name) throws Exception {
        ByteBuffer pixels = sink.readPixels();
        int left = (HEIGHT / 2 * WIDTH + WIDTH / 4) * 4;
        int right = (HEIGHT / 2 * WIDTH + WIDTH * 3 / 4) * 4;
        int red = pixels.get(left) & 255, redGreen = pixels.get(left + 1) & 255, redBlue = pixels.get(left + 2) & 255;
        int blueRed = pixels.get(right) & 255, blueGreen = pixels.get(right + 1) & 255, blue = pixels.get(right + 2) & 255;
        check(name + " decoded left patch is red (RGB " + red + "," + redGreen + "," + redBlue + ")",
                red > 150 && redGreen < 90 && redBlue < 90);
        check(name + " decoded right patch is blue (RGB " + blueRed + "," + blueGreen + "," + blue + ")",
                blue > 150 && blueRed < 90 && blueGreen < 90);
        Bitmap image = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        pixels.rewind(); image.copyPixelsFromBuffer(pixels);
        try (FileOutputStream stream = new FileOutputStream(new File(directory, name + ".png"))) {
            if (!image.compress(Bitmap.CompressFormat.PNG, 100, stream)) throw new IllegalStateException("Cannot save decoded pixels");
        } finally { image.recycle(); }
    }

    private Fixture readFixture() throws Exception {
        byte[] bytes;
        try (InputStream stream = runner.getContext().getAssets().open("gamestream-renderer/red-blue-160x96.h264");
             ByteArrayOutputStream all = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int count;
            while ((count = stream.read(buffer)) != -1) all.write(buffer, 0, count);
            bytes = all.toByteArray();
        }
        Fixture fixture = new Fixture();
        for (int at = 0; at < bytes.length;) {
            int prefix = prefix(bytes, at);
            if (prefix == 0) throw new IllegalStateException("Fixture is not Annex-B");
            int end = at + prefix + 1;
            while (end < bytes.length && prefix(bytes, end) == 0) end++;
            byte[] nal = Arrays.copyOfRange(bytes, at, end);
            switch (bytes[at + prefix] & 31) {
                case 7: fixture.sps = nal; break;
                case 8: fixture.pps = nal; break;
                case 5: fixture.idr = nal; break;
                case 1: fixture.p = nal; break;
                default: break; // Encoder SEI is not part of the picture callback fixture.
            }
            at = end;
        }
        return fixture;
    }

    private static int prefix(byte[] b, int at) {
        if (at + 3 <= b.length && b[at] == 0 && b[at + 1] == 0) {
            if (b[at + 2] == 1) return 3;
            if (at + 4 <= b.length && b[at + 2] == 0 && b[at + 3] == 1) return 4;
        }
        return 0;
    }
    private static final class Fixture { byte[] sps, pps, idr, p; }
    private interface Condition { boolean get(); }
    private void await(String name, Condition condition, long timeoutMs) throws Exception {
        long deadline = SystemClock.uptimeMillis() + timeoutMs;
        while (!condition.get() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(20);
        check(name, condition.get());
    }
    private void check(String name, boolean passed) throws Exception {
        checks.put(new JSONObject().put("check", name).put("passed", passed));
        android.util.Log.i("GameStreamDeviceTest", (passed ? "PASS " : "FAIL ") + name);
        if (!passed) throw new AssertionError(name);
    }
    private void writeReport() throws Exception {
        try (FileOutputStream stream = new FileOutputStream(new File(directory, "report.json"))) {
            stream.write(report.toString(2).getBytes(StandardCharsets.UTF_8));
        }
    }

    private static boolean pairingIdentityExists() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        // Read only the known alias's existence. Never request or export key material.
        return store.containsAlias("mooncast.tv.gamestream.pairing.v1");
    }

    /** Off-screen EGL consumer: pixels are observed from the actual decoder's Surface. */
    private static final class PixelSink implements AutoCloseable {
        private static int liveSinks;
        final AtomicInteger available = new AtomicInteger();
        final EGLDisplay display;
        final EGLContext context;
        final EGLSurface pbuffer;
        final int textureId, program;
        final SurfaceTexture texture;
        final Surface surface;
        boolean externallyRetired, closed;
        PixelSink() {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            int[] version = new int[2];
            require(EGL14.eglInitialize(display, version, 0, version, 1), "EGL initialize");
            EGLConfig[] configs = new EGLConfig[1]; int[] count = new int[1];
            require(EGL14.eglChooseConfig(display, new int[]{EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_NONE}, 0, configs, 0, 1, count, 0)
                    && count[0] == 1, "EGL config");
            context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
                    new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE}, 0);
            pbuffer = EGL14.eglCreatePbufferSurface(display, configs[0],
                    new int[]{EGL14.EGL_WIDTH, WIDTH, EGL14.EGL_HEIGHT, HEIGHT, EGL14.EGL_NONE}, 0);
            makeCurrent();
            int[] names = new int[1]; GLES20.glGenTextures(1, names, 0); textureId = names[0];
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            texture = new SurfaceTexture(textureId); texture.setDefaultBufferSize(WIDTH, HEIGHT);
            texture.setOnFrameAvailableListener(ignored -> available.incrementAndGet(), new Handler(Looper.getMainLooper()));
            surface = new Surface(texture);
            int vertex = shader(GLES20.GL_VERTEX_SHADER, "attribute vec2 p; attribute vec2 uv; uniform mat4 m; varying vec2 v; "
                    + "void main(){gl_Position=vec4(p,0.,1.); v=(m*vec4(uv,0.,1.)).xy;}");
            int fragment = shader(GLES20.GL_FRAGMENT_SHADER, "#extension GL_OES_EGL_image_external : require\n"
                    + "precision mediump float; uniform samplerExternalOES s; varying vec2 v; void main(){gl_FragColor=texture2D(s,v);}");
            program = GLES20.glCreateProgram(); GLES20.glAttachShader(program, vertex); GLES20.glAttachShader(program, fragment);
            GLES20.glLinkProgram(program); int[] status = new int[1]; GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0);
            require(status[0] == GLES20.GL_TRUE, "GLES program link: " + GLES20.glGetProgramInfoLog(program));
            GLES20.glDeleteShader(vertex); GLES20.glDeleteShader(fragment);
            liveSinks++;
        }
        void clearFrameSignal() { available.set(0); }
        void makeCurrent() { require(EGL14.eglMakeCurrent(display, pbuffer, pbuffer, context), "EGL make current"); }
        ByteBuffer readPixels() {
            makeCurrent(); texture.updateTexImage(); float[] matrix = new float[16]; texture.getTransformMatrix(matrix);
            GLES20.glViewport(0, 0, WIDTH, HEIGHT); GLES20.glUseProgram(program);
            FloatBuffer vertices = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            vertices.put(new float[]{-1,-1,0,0, 1,-1,1,0, -1,1,0,1, 1,1,1,1}); vertices.position(0);
            int p = GLES20.glGetAttribLocation(program, "p"), uv = GLES20.glGetAttribLocation(program, "uv");
            GLES20.glEnableVertexAttribArray(p); GLES20.glVertexAttribPointer(p, 2, GLES20.GL_FLOAT, false, 16, vertices);
            vertices.position(2); GLES20.glEnableVertexAttribArray(uv); GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, 16, vertices);
            GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "m"), 1, false, matrix, 0);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "s"), 0); GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            ByteBuffer pixels = ByteBuffer.allocateDirect(WIDTH * HEIGHT * 4).order(ByteOrder.nativeOrder());
            GLES20.glReadPixels(0, 0, WIDTH, HEIGHT, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixels);
            require(GLES20.glGetError() == GLES20.GL_NO_ERROR, "GLES pixel readback"); pixels.rewind(); return pixels;
        }
        @Override public void close() {
            if (closed) return; closed = true; makeCurrent();
            if (!externallyRetired) { surface.release(); texture.release(); }
            GLES20.glDeleteProgram(program); GLES20.glDeleteTextures(1, new int[]{textureId}, 0);
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
            EGL14.eglDestroySurface(display, pbuffer); EGL14.eglDestroyContext(display, context);
            // Both sinks share EGL_DEFAULT_DISPLAY; terminate only after all test sinks finish.
            if (--liveSinks == 0) { EGL14.eglTerminate(display); EGL14.eglReleaseThread(); }
        }
        private static int shader(int type, String source) {
            int result = GLES20.glCreateShader(type); GLES20.glShaderSource(result, source); GLES20.glCompileShader(result);
            int[] status = new int[1]; GLES20.glGetShaderiv(result, GLES20.GL_COMPILE_STATUS, status, 0);
            require(status[0] == GLES20.GL_TRUE, "GLES shader: " + GLES20.glGetShaderInfoLog(result)); return result;
        }
        private static void require(boolean okay, String message) { if (!okay) throw new AssertionError(message); }
    }
}
