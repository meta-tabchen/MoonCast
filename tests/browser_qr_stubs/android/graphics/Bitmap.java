package android.graphics;

/** JVM pixel-storage test double only; no Android rendering or camera behavior is simulated. */
public final class Bitmap {
    public enum Config { ARGB_8888 }
    private final int[] colors;
    private final int width, height;
    private Bitmap(int[] colors, int width, int height) {
        this.colors = colors.clone(); this.width = width; this.height = height;
    }
    public static Bitmap createBitmap(int[] colors, int width, int height, Config config) {
        if (colors.length != width * height) throw new IllegalArgumentException();
        return new Bitmap(colors, width, height);
    }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getPixel(int x, int y) { return colors[y * width + x]; }
    public int[] pixels() { return colors.clone(); }
}
