package io.github.hipsterbwp.tvhome;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import java.util.Arrays;

/**
 * White, background-free logos for the home list. Known apps use a crisp outline from
 * Simple Icons; any other app gets its own icon's artwork cut out from the icon background.
 */
final class Logos {
    private Logos() {}

    static Drawable forApp(Context c, String pkg, Drawable icon) {
        int res = outline(pkg);
        if (res != 0) return c.getDrawable(res);
        return icon == null ? null : cutOut(c.getResources(), icon);
    }

    private static int outline(String pkg) {
        switch (pkg) {
            case "com.google.android.youtube.tv": return R.drawable.logo_youtube;
            case "com.netflix.ninja": return R.drawable.logo_netflix;
            case "com.wbd.stream": return R.drawable.logo_hbomax;
            case "tv.twitch.android.app": return R.drawable.logo_twitch;
            case "air.com.vudu.air.DownloaderTablet": return R.drawable.logo_fandango;
            default: return 0;
        }
    }

    /**
     * Keeps the parts of the icon that stand out from its background colour (taken from the
     * icon's edge), paints them white, and trims to the artwork's bounds.
     */
    static Drawable cutOut(Resources r, Drawable icon) {
        final int size = 192;
        Bitmap src = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(src);
        icon.setBounds(0, 0, size, size);
        icon.draw(canvas);
        int[] px = new int[size * size];
        src.getPixels(px, 0, size, 0, 0, size, size);

        // Background colour: median of an inset ring, away from rounded corners.
        int inset = size / 10, n = 0;
        int[] rs = new int[4 * size], gs = new int[4 * size], bs = new int[4 * size];
        for (int i = inset; i < size - inset; i++) {
            int[] ring = {px[inset * size + i], px[(size - 1 - inset) * size + i],
                    px[i * size + inset], px[i * size + size - 1 - inset]};
            for (int p : ring) {
                rs[n] = Color.red(p);
                gs[n] = Color.green(p);
                bs[n] = Color.blue(p);
                n++;
            }
        }
        int br = median(rs, n), bg = median(gs, n), bb = median(bs, n);

        // Thresholds scale with the strongest contrast in the icon, so gentle background
        // gradients drop out while the artwork, which stands out most, stays.
        double[] dist = new double[px.length];
        double max = 0;
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            if (Color.alpha(p) < 128) continue;
            int dr = Color.red(p) - br, dg = Color.green(p) - bg, db = Color.blue(p) - bb;
            dist[i] = Math.sqrt(dr * dr + dg * dg + db * db);
            max = Math.max(max, dist[i]);
        }
        double lo = Math.max(45, max * 0.5), hi = Math.max(lo + 20, max * 0.8);

        int minX = size, minY = size, maxX = -1, maxY = -1;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int p = px[y * size + x];
                int a = (int) Math.max(0, Math.min(255, (dist[y * size + x] - lo) * 255 / (hi - lo)));
                a = a * Color.alpha(p) / 255;
                px[y * size + x] = a << 24 | 0xFFFFFF;
                if (a > 40) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < 0) return null;
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        out.setPixels(px, 0, size, 0, 0, size, size);
        out = Bitmap.createBitmap(out, minX, minY, maxX - minX + 1, maxY - minY + 1);
        return new BitmapDrawable(r, out);
    }

    private static int median(int[] v, int n) {
        int[] copy = Arrays.copyOf(v, n);
        Arrays.sort(copy);
        return copy[n / 2];
    }
}
