package io.github.hipsterbwp.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * The background picture and a blurred copy. The source is "photo" (the built-in mountain
 * night photo, the default), "night" (a starry sky drawn on the TV), "daily" (Bing's photo
 * of the day), "none" (a plain gradient), or an https URL of your own picture.
 */
final class Wallpaper {
    interface Callback {
        void ready(Bitmap photo, Bitmap blur, String credit);
    }

    private static Bitmap photo, blur;
    private static String credit = "";
    private static String loadedKey;

    private final Context app;
    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());

    Wallpaper(Context c) {
        app = c.getApplicationContext();
        prefs = app.getSharedPreferences("wallpaper", Context.MODE_PRIVATE);
    }

    void setSource(String source) {
        prefs.edit().putString("source", source.trim()).remove("day").remove("credit").apply();
        new File(app.getFilesDir(), "wallpaper.jpg").delete();
        loadedKey = null;
    }

    /** Calls back on the main thread with the current photo, or nulls for the plain gradient. */
    void load(final Callback cb) {
        final String source = prefs.getString("source", "photo");
        final String day = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
        final String key = source + "|" + ("daily".equals(source) ? day : "");
        if (key.equals(loadedKey)) {
            cb.ready(photo, blur, credit);
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                Bitmap p = null;
                String c = "";
                if ("photo".equals(source)) {
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inScaled = false;
                    Bitmap raw = BitmapFactory.decodeResource(app.getResources(), R.drawable.background, o);
                    p = raw == null ? null : fit(raw);
                } else if ("night".equals(source)) {
                    DisplayMetrics m = app.getResources().getDisplayMetrics();
                    p = NightSky.draw(Math.max(m.widthPixels, m.heightPixels),
                            Math.min(m.widthPixels, m.heightPixels));
                } else if (!"none".equals(source)) {
                    File file = new File(app.getFilesDir(), "wallpaper.jpg");
                    boolean fresh = file.exists() && day.equals(prefs.getString("day", ""));
                    boolean needed = "daily".equals(source) ? !fresh : !file.exists();
                    if (needed) {
                        try {
                            download(source, file);
                            prefs.edit().putString("day", day).apply();
                        } catch (Exception ignored) {
                            // Offline or blocked: keep showing the last photo if there is one.
                        }
                    }
                    p = decode(file);
                    c = prefs.getString("credit", "");
                }
                final Bitmap fp = p;
                final Bitmap fb = p == null ? null : blur(p);
                final String fc = c;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        photo = fp;
                        blur = fb;
                        credit = fc;
                        loadedKey = fp != null || "none".equals(source) ? key : null;
                        cb.ready(fp, fb, fc);
                    }
                });
            }
        }, "wallpaper").start();
    }

    private void download(String source, File file) throws Exception {
        String url = source;
        String c = "";
        if ("daily".equals(source)) {
            JSONObject img = new JSONObject(Net.get(
                    "https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=en-US"))
                    .getJSONArray("images").getJSONObject(0);
            url = "https://www.bing.com" + img.getString("urlbase") + "_1920x1080.jpg";
            c = img.optString("copyright", "");
        } else if (!source.startsWith("https://")) {
            return;
        }
        byte[] data = Net.bytes(url, 15 * 1024 * 1024);
        File tmp = new File(file.getPath() + ".tmp");
        FileOutputStream out = new FileOutputStream(tmp);
        try {
            out.write(data);
        } finally {
            out.close();
        }
        if (!tmp.renameTo(file)) throw new java.io.IOException("could not save wallpaper");
        prefs.edit().putString("credit", c).apply();
    }

    /** Decodes and centre-crops the photo to the screen's shape and size. */
    private Bitmap decode(File file) {
        if (!file.exists()) return null;
        DisplayMetrics m = app.getResources().getDisplayMetrics();
        int sw = Math.max(m.widthPixels, m.heightPixels), sh = Math.min(m.widthPixels, m.heightPixels);
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        o.inJustDecodeBounds = false;
        o.inSampleSize = 1;
        while (o.outWidth / (o.inSampleSize * 2) >= sw && o.outHeight / (o.inSampleSize * 2) >= sh) {
            o.inSampleSize *= 2;
        }
        Bitmap src = BitmapFactory.decodeFile(file.getPath(), o);
        return src == null ? null : fit(src);
    }

    /** Centre-crops and scales a picture to fill the screen. */
    private Bitmap fit(Bitmap src) {
        DisplayMetrics m = app.getResources().getDisplayMetrics();
        int sw = Math.max(m.widthPixels, m.heightPixels), sh = Math.min(m.widthPixels, m.heightPixels);
        float scale = Math.max(sw / (float) src.getWidth(), sh / (float) src.getHeight());
        int cw = Math.round(sw / scale), ch = Math.round(sh / scale);
        int x = (src.getWidth() - cw) / 2, y = (src.getHeight() - ch) / 2;
        Bitmap cropped = Bitmap.createBitmap(src, x, y, Math.min(cw, src.getWidth() - x), Math.min(ch, src.getHeight() - y));
        return Bitmap.createScaledBitmap(cropped, sw, sh, true);
    }

    /** A soft blur: shrink to 1/10, three box-blur passes, then drawn scaled up with filtering. */
    static Bitmap blur(Bitmap src) {
        int w = Math.max(1, src.getWidth() / 10), h = Math.max(1, src.getHeight() / 10);
        Bitmap small = Bitmap.createScaledBitmap(src, w, h, true).copy(Bitmap.Config.ARGB_8888, true);
        int[] px = new int[w * h];
        small.getPixels(px, 0, w, 0, 0, w, h);
        int[] tmp = new int[px.length];
        for (int pass = 0; pass < 3; pass++) {
            box(px, tmp, w, h, 4, true);
            box(tmp, px, w, h, 4, false);
        }
        small.setPixels(px, 0, w, 0, 0, w, h);
        return small;
    }

    private static void box(int[] in, int[] out, int w, int h, int r, boolean horizontal) {
        int outer = horizontal ? h : w, inner = horizontal ? w : h;
        for (int o = 0; o < outer; o++) {
            for (int i = 0; i < inner; i++) {
                int rs = 0, gs = 0, bs = 0, n = 0;
                for (int k = Math.max(0, i - r); k <= Math.min(inner - 1, i + r); k++) {
                    int p = in[horizontal ? o * w + k : k * w + o];
                    rs += (p >> 16) & 0xFF;
                    gs += (p >> 8) & 0xFF;
                    bs += p & 0xFF;
                    n++;
                }
                out[horizontal ? o * w + i : i * w + o] = 0xFF000000 | (rs / n) << 16 | (gs / n) << 8 | (bs / n);
            }
        }
    }
}
