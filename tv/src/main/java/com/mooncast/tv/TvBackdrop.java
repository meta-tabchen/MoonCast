package com.mooncast.tv;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

/** Original static Canvas landscapes plus the existing generated moon-lake asset. No animation. */
final class TvBackdrop {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF destination = new RectF();
    private final Bitmap moonlake;
    private final LinearGradient auroraSky = new LinearGradient(0, 0, 0, 900,
            new int[]{0xff040b19, 0xff0b2635, 0xff07121e}, null, Shader.TileMode.CLAMP);
    private final RadialGradient auroraGlow = new RadialGradient(1100, 190, 780,
            new int[]{0x8059bfae, 0x302e628d, 0x000b1423}, null, Shader.TileMode.CLAMP);
    private final LinearGradient ribbon = new LinearGradient(0, 70, 0, 520,
            new int[]{0x001aa991, 0x7045bba3, 0x001a7198}, null, Shader.TileMode.CLAMP);
    private final LinearGradient mistSky = new LinearGradient(0, 0, 0, 900,
            new int[]{0xff0d1426, 0xff253548, 0xff111d2c}, null, Shader.TileMode.CLAMP);
    private final RadialGradient mistGlow = new RadialGradient(1250, 260, 760,
            new int[]{0x504f7294, 0x0023364e}, null, Shader.TileMode.CLAMP);
    private final LinearGradient midnight = new LinearGradient(0, 0, 1600, 900,
            new int[]{0xff05070e, 0xff11172b, 0xff070b16}, null, Shader.TileMode.CLAMP);
    private final RadialGradient midnightGlow = new RadialGradient(1350, 100, 950,
            new int[]{0x304a59a5, 0x00101423}, null, Shader.TileMode.CLAMP);
    private static final int[] MIST_COLORS = {0xff263649,0xff19283a,0xff0c1727};
    private static final int[] AURORA_COLORS = {0xff122637,0xff0b1b2a,0xff07111e};
    private final Path ribbonPath = new Path();
    private final Path[] mountains = new Path[3];

    TvBackdrop(Resources resources) {
        BitmapFactory.Options options = new BitmapFactory.Options(); options.inScaled = false;
        moonlake = BitmapFactory.decodeResource(resources, R.drawable.cinema_background, options);
        ribbonPath.moveTo(-100, 220);
        ribbonPath.cubicTo(420, 600, 690, -100, 1150, 100);
        ribbonPath.cubicTo(1380, 190, 1510, 230, 1700, 80);
        ribbonPath.lineTo(1700, 250);
        ribbonPath.cubicTo(1260, 440, 1120, 120, 820, 280);
        ribbonPath.cubicTo(450, 590, 190, 620, -100, 400); ribbonPath.close();
        for (int i = 0; i < mountains.length; i++) {
            Path p = new Path(); float base = 490 + i * 110;
            p.moveTo(-100, base + 100);
            p.cubicTo(100, base - 30, 220, base + 80, 430, base - 70);
            p.cubicTo(600, base - 150, 780, base + 90, 960, base - 50);
            p.cubicTo(1170, base - 190, 1350, base - 90, 1700, base + 70);
            p.lineTo(1700, 950); p.lineTo(-100, 950); p.close(); mountains[i] = p;
        }
    }
    void draw(Canvas canvas, int width, int height, int theme) {
        if (width <= 0 || height <= 0) return;
        paint.setShader(null); paint.setStyle(Paint.Style.FILL);
        if (theme == 0 && moonlake != null) {
            float scale = Math.max((float) width / moonlake.getWidth(), (float) height / moonlake.getHeight());
            float w = moonlake.getWidth() * scale, h = moonlake.getHeight() * scale;
            destination.set((width-w)/2, (height-h)/2, (width+w)/2, (height+h)/2);
            canvas.drawBitmap(moonlake, null, destination, paint);
        } else {
            canvas.save(); canvas.scale(width / 1600f, height / 900f);
            paint.setShader(theme == 1 ? auroraSky : theme == 2 ? mistSky : midnight);
            canvas.drawRect(0, 0, 1600, 900, paint);
            paint.setShader(theme == 1 ? auroraGlow : theme == 2 ? mistGlow : midnightGlow);
            canvas.drawRect(0, 0, 1600, 900, paint);
            if (theme == 1) { paint.setShader(ribbon); canvas.drawPath(ribbonPath, paint); }
            paint.setShader(null);
            if (theme == 1 || theme == 2) {
                for (int i = 0; i < mountains.length; i++) {
                    paint.setColor(theme == 2 ? MIST_COLORS[i]
                            : AURORA_COLORS[i]);
                    canvas.drawPath(mountains[i], paint);
                }
            }
            canvas.restore();
        }
        canvas.drawColor(0x40040a17);
    }
}
