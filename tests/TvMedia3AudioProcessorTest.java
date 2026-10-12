package com.mooncast.tv.media;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.audio.AudioProcessingPipeline;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.exoplayer.audio.AudioOffloadSupport;
import androidx.media3.exoplayer.audio.AudioSink;
import com.google.common.collect.ImmutableList;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/** Runs against actual pinned Media3 classes, without replacing them with test stubs. */
public final class TvMedia3AudioProcessorTest {
    private static int assertions;
    public static void main(String[] args) throws Exception {
        testPipeline();
        testFormatLifecycle();
        testPcmOnlySink();
        System.out.println("TV real Media3 PCM pipeline tests passed (" + assertions + " assertions)");
    }

    private static void testPipeline() throws Exception {
        AudioOffsetProcessor processor = new AudioOffsetProcessor(0);
        AudioProcessingPipeline pipeline = new AudioProcessingPipeline(ImmutableList.of(processor));
        for (int rate : new int[] {44100, 48000}) {
            for (int channels : new int[] {1, 2, 6}) {
                AudioProcessor.AudioFormat format = new AudioProcessor.AudioFormat(rate, channels, C.ENCODING_PCM_16BIT);
                for (int offset = -500; offset <= 500; offset += 50) {
                    processor.setOffsetMs(offset);
                    check(pipeline.configure(format).equals(format), "format retained");
                    pipeline.flush();
                    check(pipeline.isOperational(), "zero and nonzero offsets stay operational");
                    byte[] input = new byte[(rate / 2 + 17) * channels * 2];
                    for (int i = 0; i < input.length; i++) input[i] = (byte) (i * 31 + i / 13);
                    ByteArrayOutputStream actual = new ByteArrayOutputStream();
                    for (int start = 0; start < input.length;) {
                        int end = Math.min(input.length, start + 307);
                        ByteBuffer chunk = buffer(Arrays.copyOfRange(input, start, end));
                        int loops = 0;
                        do {
                            pipeline.queueInput(chunk);
                            drain(pipeline, actual);
                            check(++loops < 10, "pipeline makes progress");
                        } while (chunk.hasRemaining());
                        start = end;
                    }
                    pipeline.queueEndOfStream();
                    int loops = 0;
                    while (!pipeline.isEnded()) {
                        drain(pipeline, actual);
                        check(++loops < 10, "pipeline drains EOS");
                    }
                    int shift = (int) ((long) Math.abs(offset) * rate / 1000) * channels * 2;
                    byte[] expected;
                    if (offset < 0) expected = Arrays.copyOfRange(input, shift, input.length);
                    else {
                        expected = new byte[input.length + shift];
                        System.arraycopy(input, 0, expected, shift, input.length);
                    }
                    check(Arrays.equals(expected, actual.toByteArray()), "Media3 sample bytes offset=" + offset);
                }
            }
        }
        pipeline.reset();
        check(!processor.isActive(), "pipeline reset releases processor format");
    }

    private static void testFormatLifecycle() throws Exception {
        AudioOffsetProcessor processor = new AudioOffsetProcessor(100);
        check(!processor.isActive(), "initially unconfigured");
        boolean rejected = false;
        try {
            processor.configure(new AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_FLOAT));
        } catch (AudioProcessor.UnhandledAudioFormatException expected) { rejected = true; }
        check(rejected, "float cannot silently bypass PCM16 policy");
        processor.configure(new AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT));
        processor.flush();
        processor.queueEndOfStream();
        check(processor.isEnded(), "empty actual processor stream ends");
        processor.reset();
        check(!processor.isActive(), "reset unconfigures");
        check(processor.getOffsetMs() == 100, "reset preserves calibration");
        processor.setOffsetMs(0);
        processor.configure(new AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT));
        processor.flush();
        byte[] frame = {1, 2, 3, 4};
        processor.queueInput(buffer(frame));
        processor.queueEndOfStream();
        check(!processor.isEnded(), "actual adapter holds final output");
        ByteBuffer output = processor.getOutput();
        byte[] actual = new byte[output.remaining()];
        output.get(actual);
        check(Arrays.equals(frame, actual), "zero offset retains output bytes");
        check(processor.isEnded(), "adapter EOS after output transfer");
    }

    private static void testPcmOnlySink() {
        int[] offload = {-1};
        boolean[] skipSilence = {true};
        int[] disabledTunneling = {0};
        AudioSink delegate = (AudioSink) Proxy.newProxyInstance(AudioSink.class.getClassLoader(),
                new Class<?>[] {AudioSink.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getFormatSupport": return AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY;
                        case "supportsFormat": return true;
                        case "setOffloadMode": offload[0] = (int) args[0]; return null;
                        case "setSkipSilenceEnabled": skipSilence[0] = (boolean) args[0]; return null;
                        case "disableTunneling": disabledTunneling[0]++; return null;
                        default: throw new AssertionError("Unexpected sink call " + method.getName());
                    }
                });
        CalibratedRenderersFactory.PcmOnlyAudioSink sink = new CalibratedRenderersFactory.PcmOnlyAudioSink(delegate);
        Format raw = new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_RAW)
                .setPcmEncoding(C.ENCODING_PCM_16BIT).setSampleRate(48000).setChannelCount(2).build();
        Format encoded = new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AC3).build();
        check(sink.supportsFormat(raw), "raw PCM is supported");
        check(!sink.supportsFormat(encoded), "encoded AC3 cannot bypass decoding");
        check(sink.getFormatSupport(encoded) == AudioSink.SINK_FORMAT_UNSUPPORTED, "encoded support code rejected");
        check(sink.getFormatOffloadSupport(encoded) == AudioOffloadSupport.DEFAULT_UNSUPPORTED,
                "offload unconditionally unavailable");
        sink.setOffloadMode(AudioSink.OFFLOAD_MODE_ENABLED_GAPLESS_NOT_REQUIRED);
        check(offload[0] == AudioSink.OFFLOAD_MODE_DISABLED, "offload requests remain disabled");
        sink.setSkipSilenceEnabled(true);
        check(!skipSilence[0], "calibration silence cannot be skipped");
        sink.enableTunnelingV21();
        check(disabledTunneling[0] == 1, "tunneling cannot bypass processor");
    }

    private static ByteBuffer buffer(byte[] bytes) {
        ByteBuffer result = ByteBuffer.allocateDirect(bytes.length).order(ByteOrder.nativeOrder());
        result.put(bytes).flip();
        return result;
    }
    private static void drain(AudioProcessingPipeline pipeline, ByteArrayOutputStream actual) {
        ByteBuffer out = pipeline.getOutput();
        byte[] bytes = new byte[out.remaining()];
        out.get(bytes);
        actual.writeBytes(bytes);
    }
    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
}
