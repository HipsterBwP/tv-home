package io.github.hipsterbwp.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import java.util.Random;

/** Draws the default background: a starry blue night over a dusk-lit mountain. Same picture every time. */
final class NightSky {
    private NightSky() {}

    static Bitmap draw(int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Random rnd = new Random(20261026L);

        // Sky: deep navy at the top, clear blue glow towards the horizon.
        p.setShader(new LinearGradient(0, 0, 0, h * 0.75f,
                new int[] {0xFF0B1626, 0xFF183852, 0xFF34627F, 0xFF7098AE},
                new float[] {0f, 0.45f, 0.8f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(new RadialGradient(w * 0.42f, h * 0.68f, w * 0.45f,
                0x44A9C6D6, 0x00A9C6D6, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);

        // Stars, denser and brighter higher up.
        for (int i = 0; i < 900; i++) {
            float y = (float) Math.pow(rnd.nextFloat(), 1.6) * h * 0.66f;
            float x = rnd.nextFloat() * w;
            float fade = 1f - y / (h * 0.66f);
            int a = (int) (40 + 190 * fade * rnd.nextFloat());
            float r = (0.5f + rnd.nextFloat() * (rnd.nextFloat() < 0.05f ? 1.6f : 0.9f)) * w / 1920f;
            p.setColor(a << 24 | 0xE8F0FF);
            c.drawCircle(x, y, r, p);
        }

        // Far hills: cool blue-grey haze on the horizon.
        Path far = ridge(w, h, rnd, 0.70f, 0.025f, 0.012f);
        p.setShader(new LinearGradient(0, h * 0.66f, 0, h, 0xFF3A4D60, 0xFF1C2532, Shader.TileMode.CLAMP));
        c.drawPath(far, p);

        // The mountain: a broad, rugged ridge with a rounded summit, warm dusk light on its face.
        float[] xs = {0f, 0.08f, 0.16f, 0.24f, 0.30f, 0.35f, 0.39f, 0.42f, 0.445f, 0.47f, 0.50f, 0.53f,
                0.56f, 0.60f, 0.65f, 0.71f, 0.78f, 0.86f, 0.94f, 1f};
        float[] ys = {0.780f, 0.770f, 0.755f, 0.740f, 0.720f, 0.690f, 0.650f, 0.615f, 0.590f, 0.578f,
                0.582f, 0.600f, 0.630f, 0.660f, 0.685f, 0.700f, 0.715f, 0.730f, 0.740f, 0.750f};
        Path m = new Path();
        m.moveTo(0, h);
        m.lineTo(0, ys[0] * h);
        for (int i = 1; i < xs.length; i++) {
            for (int k = 1; k <= 6; k++) {
                float t = k / 6f;
                float x = xs[i - 1] + (xs[i] - xs[i - 1]) * t;
                float y = ys[i - 1] + (ys[i] - ys[i - 1]) * t;
                if (k < 6) y += (rnd.nextFloat() - 0.5f) * 0.008f;
                m.lineTo(x * w, y * h);
            }
        }
        m.lineTo(w, h);
        m.close();
        float peakX = w * 0.47f, peakY = h * 0.578f;
        p.setShader(new LinearGradient(0, peakY, 0, h * 0.86f,
                new int[] {0xFFC08462, 0xFF8E5C46, 0xFF4E3832, 0xFF2C2426},
                new float[] {0f, 0.25f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        c.drawPath(m, p);
        p.setShader(null);
        // Rock and gully texture on the face.
        c.save();
        c.clipPath(m);
        p.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 1800; i++) {
            float x = rnd.nextFloat() * w, y = peakY + rnd.nextFloat() * (h - peakY);
            float len = (6 + rnd.nextFloat() * 26) * w / 1920f;
            boolean light = rnd.nextFloat() < 0.35f;
            p.setColor((light ? 0x0A : 0x12) << 24 | (light ? 0xFFD8B0 : 0x140C0A));
            p.setStrokeWidth((1 + rnd.nextFloat() * 2.5f) * w / 1920f);
            float dx = (rnd.nextFloat() - 0.5f) * len, dy = len * (0.4f + rnd.nextFloat());
            c.drawLine(x, y, x + dx, y + dy, p);
        }
        p.setStyle(Paint.Style.FILL);
        // Shade the far side so the peak reads as lit from the left.
        p.setShader(new LinearGradient(peakX, 0, w * 0.8f, 0, 0x00000000, 0x6612121A, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        c.restore();

        // Dark foreground slopes.
        Path near = ridge(w, h, rnd, 0.89f, 0.025f, 0.012f);
        p.setShader(new LinearGradient(0, h * 0.85f, 0, h, 0xFF2A2322, 0xFF141112, Shader.TileMode.CLAMP));
        c.drawPath(near, p);

        // Gentle vignette at the bottom.
        p.setShader(new LinearGradient(0, h * 0.7f, 0, h, 0x00000000, 0x55000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        return b;
    }

    /** A rolling skyline at {@code level} of the height, closed along the bottom edge. */
    private static Path ridge(int w, int h, Random rnd, float level, float swell, float rough) {
        Path p = new Path();
        p.moveTo(0, h);
        float phase = rnd.nextFloat() * 6f;
        for (int i = 0; i <= 64; i++) {
            float x = i / 64f;
            float y = level + swell * (float) Math.sin(x * 5.5 + phase) * 0.6f
                    + swell * (float) Math.sin(x * 13 + phase * 2) * 0.4f
                    + (rnd.nextFloat() - 0.5f) * rough;
            p.lineTo(x * w, y * h);
        }
        p.lineTo(w, h);
        p.close();
        return p;
    }
}
