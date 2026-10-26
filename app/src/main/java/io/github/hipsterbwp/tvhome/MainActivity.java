package io.github.hipsterbwp.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * The home screen: a full-screen picture, a fading list of app names on the left, the time
 * at top right, and All apps and Settings at bottom right. Options, set from adb with
 * {@code am start -n io.github.hipsterbwp.tvhome/.MainActivity}:
 * {@code --es wallpaper photo|night|daily|none|https://...}, {@code --es weather show|hide},
 * {@code --es place <ZIP or town>}, {@code --es units c|f}.
 */
public class MainActivity extends Activity {
    /** Package and fallback name, in list order. Missing or disabled apps are skipped. */
    private static final String[][] FAVORITES = {
            {"com.google.android.youtube.tv", "YouTube"},
            {"com.netflix.ninja", "Netflix"},
            {"com.wbd.stream", "Max"},
            {"com.amazon.amazonvideo.livingroom", "Prime Video"},
            {"com.disney.disneyplus", "Disney+"},
            {"com.espn.score_center", "ESPN"},
            {"tv.twitch.android.app", "Twitch"},
            {"air.com.vudu.air.DownloaderTablet", "Fandango"},
            {"net.comeandsee.ott.thechosen", "The Chosen"},
    };
    private static final long WEATHER_REFRESH_MS = 30 * 60 * 1000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ImageView photo;
    private LinearLayout list;
    private TextView weatherView, creditView;
    private View allApps;
    private Weather weather;
    private Wallpaper wallpaper;
    private SharedPreferences ui;
    private String lastFocused;

    private final Runnable weatherTick = new Runnable() {
        @Override
        public void run() {
            weather.fetch(new Weather.Callback() {
                @Override
                public void done(String text) {
                    weatherView.setText(text);
                }
            });
            handler.postDelayed(this, WEATHER_REFRESH_MS);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        weather = new Weather(this);
        wallpaper = new Wallpaper(this);
        ui = getSharedPreferences("ui", MODE_PRIVATE);
        applyExtras(getIntent());

        DisplayMetrics m = getResources().getDisplayMetrics();
        int screenH = Math.min(m.widthPixels, m.heightPixels);
        int side = Tiles.dp(this, 56);

        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0xFF0A1424, 0xFF14304A, 0xFF2E5A78}));

        photo = new ImageView(this);
        photo.setScaleType(ImageView.ScaleType.FIT_XY);
        root.addView(photo, match());

        View scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] {0x40000000, 0x00000000, 0x00000000}));
        root.addView(scrim, match());

        LinearLayout corner = new LinearLayout(this);
        corner.setOrientation(LinearLayout.VERTICAL);
        corner.setGravity(Gravity.END);
        TextClock time = new TextClock(this);
        time.setFormat12Hour("h:mm");
        time.setFormat24Hour("H:mm");
        time.setTextColor(Color.WHITE);
        time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        time.setTypeface(Tiles.MEDIUM);
        time.setShadowLayer(Tiles.dp(this, 4), 0, Tiles.dp(this, 1), 0x55000000);
        corner.addView(time);
        weatherView = new TextView(this);
        weatherView.setTextColor(0xD9FFFFFF);
        weatherView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        weatherView.setShadowLayer(Tiles.dp(this, 3), 0, Tiles.dp(this, 1), 0x55000000);
        weatherView.setText(weather.cached());
        corner.addView(weatherView);
        FrameLayout.LayoutParams tp = wrap(Gravity.TOP | Gravity.END);
        tp.rightMargin = side;
        tp.topMargin = Tiles.dp(this, 34);
        root.addView(corner, tp);

        int fade = Tiles.dp(this, 64);
        FadeList scroller = new FadeList(this, fade);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(false);
        scroller.addView(list);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Math.round(screenH * 0.54f), Gravity.TOP | Gravity.START);
        lp.leftMargin = side - Tiles.dp(this, 12);
        lp.topMargin = Math.round(screenH * 0.20f);
        root.addView(scroller, lp);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setGravity(Gravity.END);
        allApps = Tiles.button(this, Icons.grid(), "All apps", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                lastFocused = "#all";
                startActivity(new Intent(MainActivity.this, AllAppsActivity.class));
            }
        });
        allApps.setId(View.generateViewId());
        actions.addView(allApps);
        View settings = Tiles.button(this, Icons.gear(), "Settings", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                lastFocused = "#settings";
                Tiles.launch(MainActivity.this, new Intent(Settings.ACTION_SETTINGS));
            }
        });
        actions.addView(settings);
        FrameLayout.LayoutParams ap = wrap(Gravity.BOTTOM | Gravity.END);
        ap.rightMargin = side - Tiles.dp(this, 12);
        ap.bottomMargin = Tiles.dp(this, 36);
        root.addView(actions, ap);

        creditView = new TextView(this);
        creditView.setTextColor(0x80FFFFFF);
        creditView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        creditView.setSingleLine(true);
        FrameLayout.LayoutParams crp = wrap(Gravity.BOTTOM | Gravity.START);
        crp.leftMargin = side;
        crp.bottomMargin = Tiles.dp(this, 14);
        root.addView(creditView, crp);

        setContentView(root);
    }

    private static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private static FrameLayout.LayoutParams wrap(int gravity) {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, gravity);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        applyExtras(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        buildList();
        wallpaper.load(new Wallpaper.Callback() {
            @Override
            public void ready(Bitmap p, Bitmap blur, String credit) {
                photo.setImageBitmap(p);
                creditView.setText(credit);
            }
        });
        boolean showWeather = "show".equals(ui.getString("weather", "hide"));
        weatherView.setVisibility(showWeather ? View.VISIBLE : View.GONE);
        handler.removeCallbacks(weatherTick);
        if (showWeather) handler.post(weatherTick);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(weatherTick);
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        // This is the home screen; there is nothing to go back to.
    }

    private void applyExtras(Intent intent) {
        if (intent == null) return;
        String place = intent.getStringExtra("place");
        if (place != null) weather.setPlace(place);
        String units = intent.getStringExtra("units");
        if (units != null) weather.setUnits(units);
        String wall = intent.getStringExtra("wallpaper");
        if (wall != null) wallpaper.setSource(wall);
        String show = intent.getStringExtra("weather");
        if (show != null) ui.edit().putString("weather", "show".equals(show.trim()) ? "show" : "hide").apply();
    }

    private void buildList() {
        PackageManager pm = getPackageManager();
        List<View> rows = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        for (String[] f : FAVORITES) {
            final String pkg = f[0];
            final Intent launch = Tiles.launchIntent(pm, pkg);
            if (launch == null) continue;
            View row = Tiles.row(this, Logos.forApp(this, pkg, Tiles.icon(pm, pkg)), Tiles.label(pm, pkg, f[1]),
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            lastFocused = pkg;
                            Tiles.launch(MainActivity.this, launch);
                        }
                    });
            row.setNextFocusRightId(allApps.getId());
            rows.add(row);
            keys.add(pkg);
        }
        list.removeAllViews();
        for (View r : rows) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            p.bottomMargin = Tiles.dp(this, 4);
            list.addView(r, p);
        }
        int focus = lastFocused == null ? 0 : keys.indexOf(lastFocused);
        if (focus >= 0 && focus < rows.size()) rows.get(focus).requestFocus();
        else allApps.requestFocus();
    }
}
