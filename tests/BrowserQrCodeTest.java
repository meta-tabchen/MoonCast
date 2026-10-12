package com.mooncast.host;

import android.graphics.Bitmap;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.ResultMetadataType;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

public final class BrowserQrCodeTest {
    private static int checks;
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void roundTrip(String synthetic, int size) throws Exception {
        Bitmap qr = BrowserQrCode.encode(synthetic, size);
        require(qr.getWidth() == size && qr.getHeight() == size, "exact size");
        int minX = size, minY = size, maxX = -1, maxY = -1;
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            int value = qr.getPixel(x, y);
            require(value == 0xff000000 || value == 0xffffffff, "opaque monochrome pixels");
            if (value == 0xff000000) {
                minX = Math.min(minX, x); minY = Math.min(minY, y);
                maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
            }
        }
        int run = 0;
        while (minX + run < size && qr.getPixel(minX + run, minY) == 0xff000000) run++;
        require(run % 7 == 0, "integer finder-pattern scaling");
        int module = run / 7;
        require(module >= 2, "minimum module size");
        require(minX >= 4 * module && minY >= 4 * module
                && size - 1 - maxX >= 4 * module && size - 1 - maxY >= 4 * module,
                "four-module quiet zone on every side");
        BinaryBitmap image = new BinaryBitmap(new HybridBinarizer(
                new RGBLuminanceSource(size, size, qr.pixels())));
        Result decoded = new QRCodeReader().decode(image);
        require(synthetic.equals(decoded.getText()), "complete payload including query/Unicode");
        require("M".equals(decoded.getResultMetadata().get(ResultMetadataType.ERROR_CORRECTION_LEVEL)),
                "medium error correction");
        checks++;
    }
    private static void rejects(String value, int size) {
        try { BrowserQrCode.encode(value, size); throw new AssertionError("expected rejection"); }
        catch (IllegalArgumentException expected) {
            require(value == null || value.isEmpty() || !expected.getMessage().contains(value),
                    "error must not include payload");
            require(expected.getCause() == null, "no payload diagnostics through exception cause");
            checks++;
        }
    }
    public static void main(String[] args) throws Exception {
        String token = "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff";
        String lan = "http://192.168.1.42:8080/browser/?token=" + token + "&viewer=synthetic";
        for (int size : new int[] {128, 129, 256, 383, 512, 1024}) roundTrip(lan, size);
        roundTrip(lan + "&label=合成测试-🌙", 512);
        roundTrip(lan + "&escaped=%E6%B5%8B%E8%AF%95&literal=a+b%2Bc", 512);
        String prefix = "http://192.168.1.42/?token=";
        roundTrip(prefix + "q".repeat(2048 - prefix.length()), 1024);
        rejects(null, 256); rejects("", 256); rejects("   ", 256);
        rejects(lan, 127); rejects(lan, 1025); rejects(lan, Integer.MAX_VALUE);
        rejects("q".repeat(2049), 1024);
        rejects("http://192.168.1.42/?q=" + "合".repeat(700), 1024);
        rejects(lan + "\ud800", 512);
        rejects("q".repeat(2048), 128);
        System.out.println("BrowserQrCode: " + checks + " checks passed (synthetic pixels, not camera scanning)");
    }
}
