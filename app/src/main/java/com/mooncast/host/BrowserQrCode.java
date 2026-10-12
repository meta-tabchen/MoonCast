package com.mooncast.host;

import android.graphics.Bitmap;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

/** Offline rendering only. Session links stay in memory and are never logged or fetched. */
public final class BrowserQrCode {
    private static final int MIN_PIXELS = 128;
    private static final int MAX_PIXELS = 1024;
    private static final int MAX_UTF8_BYTES = 2048;

    private BrowserQrCode() { }

    /** Returns exactly pixels × pixels, or throws so callers can offer copying the link. */
    public static Bitmap encode(String link, int pixels) {
        if (pixels < MIN_PIXELS || pixels > MAX_PIXELS)
            throw new IllegalArgumentException("QR image size must be between 128 and 1024 pixels");
        if (link == null || link.trim().isEmpty())
            throw new IllegalArgumentException("QR link must not be empty");
        if (link.length() > MAX_UTF8_BYTES || !StandardCharsets.UTF_8.newEncoder().canEncode(link)
                || link.getBytes(StandardCharsets.UTF_8).length > MAX_UTF8_BYTES)
            throw new IllegalArgumentException("QR link exceeds the supported UTF-8 size or is malformed");

        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 4);
        final BitMatrix modules;
        try {
            // Zero requested dimensions give the QR's natural modules including the quiet zone.
            modules = new QRCodeWriter().encode(link, BarcodeFormat.QR_CODE, 0, 0, hints);
        } catch (WriterException failure) {
            // Do not forward encoder diagnostics or a cause that could contain the session link.
            throw new IllegalArgumentException("QR link cannot be encoded");
        }
        int scale = pixels / modules.getWidth();
        if (scale < 2)
            throw new IllegalArgumentException("QR image is too small for this link");
        int left = (pixels - modules.getWidth() * scale) / 2;
        int top = (pixels - modules.getHeight() * scale) / 2;
        int[] colors = new int[pixels * pixels];
        Arrays.fill(colors, 0xffffffff);
        for (int y = 0; y < modules.getHeight(); y++) {
            for (int x = 0; x < modules.getWidth(); x++) {
                if (!modules.get(x, y)) continue;
                for (int row = 0; row < scale; row++) {
                    int start = (top + y * scale + row) * pixels + left + x * scale;
                    Arrays.fill(colors, start, start + scale, 0xff000000);
                }
            }
        }
        return Bitmap.createBitmap(colors, pixels, pixels, Bitmap.Config.ARGB_8888);
    }
}
