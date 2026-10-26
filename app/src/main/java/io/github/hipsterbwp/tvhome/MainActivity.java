package io.github.hipsterbwp.tvhome;

import android.app.Activity;
import android.content.Intent;
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
 * The home screen: a full-screen photo, the time with date and weather at top left, All apps
 * and Settings at top right, and a frosted dock of the chosen apps along the bottom.
 * Options, set from adb with {@code am start -n io.github.hipsterbwp.tvhome/.MainActivity}:
 * {@code --es place <ZIP or town>}, {@code --es units c|f},
 * {@code --es wallpaper daily|none|https://...}.
 */
public class MainActivity extends Activity {
    /** Package and fallback name, in dock order. Missing or disabled apps are skipped. */
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
    private Glass dock;
    private TextView weatherView, creditView;
    private View allApps;
    private Weather weather;
    private Wallpaper wallpaper;
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
        applyExtras(getIntent());

        FrameLayout root = new FrameLayout(this);
        root.setClipChildren(false);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {0xFF3D6E8F, 0xFF1C3B57, 0xFF0D1B2A}));

        photo = new ImageView(this);
        photo.setScaleType(ImageView.ScaleType.FIT_XY);
        root.addView(photo, match());

        View scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0x66000000, 0x00000000, 0x00000000, 0x80000000}));
        root.addView(scrim, match());

        int side = Tiles.dp(this, 56);

        LinearLayout clock = new LinearLayout(this);
        clock.setOrientation(LinearLayout.VERTICAL);
        TextClock time = new TextClock(this);
        time.setFormat12Hour("h:mm");
        time.setFormat24Hour("H:mm");
        time.setTextColor(Color.WHITE);
        time.setTypeface(Tiles.LIGHT);
        time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 46);
        time.setShadowLayer(Tiles.dp(this, 6), 0, Tiles.dp(this, 1), 0x55000000);
        clock.addView(time);
        LinearLayout sub = new LinearLayout(this);
        sub.setOrientation(LinearLayout.HORIZONTAL);
        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE, MMMM d");
        date.setFormat24Hour("EEEE, MMMM d");
        styleSub(date);
        sub.addView(date);
        weatherView = new TextView(this);
        styleSub(weatherView);
        weatherView.setText(weather.cached());
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wp.leftMargin = Tiles.dp(this, 18);
        sub.addView(weatherView, wp);
        clock.addView(sub);
        FrameLayout.LayoutParams cp = wrap(Gravity.TOP | Gravity.START);
        cp.leftMargin = side;
        cp.topMargin = Tiles.dp(this, 36);
        root.addView(clock, cp);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
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
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.leftMargin = Tiles.dp(this, 8);
        actions.addView(settings, sp);
        FrameLayout.LayoutParams ap = wrap(Gravity.TOP | Gravity.END);
        ap.rightMargin = side - Tiles.dp(this, 14);
        ap.topMargin = Tiles.dp(this, 40);
        root.addView(actions, ap);

        dock = new Glass(this, Tiles.dp(this, 28));
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER);
        dock.setPadding(Tiles.dp(this, 14), Tiles.dp(this, 10), Tiles.dp(this, 14), Tiles.dp(this, 10));
        FrameLayout.LayoutParams dp = wrap(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        dp.bottomMargin = Tiles.dp(this, 52);
        root.addView(dock, dp);

        creditView = new TextView(this);
        creditView.setTextColor(0x99FFFFFF);
        creditView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        creditView.setSingleLine(true);
        FrameLayout.LayoutParams crp = wrap(Gravity.BOTTOM | Gravity.END);
        crp.rightMargin = Tiles.dp(this, 20);
        crp.bottomMargin = Tiles.dp(this, 14);
        root.addView(creditView, crp);

        setContentView(root);
    }

    private void styleSub(TextView t) {
        t.setTextColor(0xE6FFFFFF);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        t.setShadowLayer(Tiles.dp(this, 4), 0, Tiles.dp(this, 1), 0x66000000);
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
        buildDock();
        wallpaper.load(new Wallpaper.Callback() {
            @Override
            public void ready(Bitmap p, Bitmap blur, String credit) {
                photo.setImageBitmap(p);
                dock.setBlur(blur);
                creditView.setText(credit);
            }
        });
        handler.removeCallbacks(weatherTick);
        handler.post(weatherTick);
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
    }

    private void buildDock() {
        PackageManager pm = getPackageManager();
        List<View> items = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        for (String[] f : FAVORITES) {
            final Intent launch = Tiles.launchIntent(pm, f[0]);
            if (launch != null) keys.add(f[0]);
        }
        DisplayMetrics m = getResources().getDisplayMetrics();
        int maxDock = m.widthPixels - Tiles.dp(this, 112 + 28);
        int width = Math.min(Tiles.dp(this, 112), maxDock / Math.max(1, keys.size()));

        for (final String pkg : keys) {
            final Intent launch = Tiles.launchIntent(pm, pkg);
            String fallback = pkg;
            for (String[] f : FAVORITES) if (f[0].equals(pkg)) fallback = f[1];
            View item = Tiles.item(this, Tiles.icon(pm, pkg), Tiles.label(pm, pkg, fallback), width,
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            lastFocused = pkg;
                            Tiles.launch(MainActivity.this, launch);
                        }
                    });
            item.setNextFocusUpId(allApps.getId());
            items.add(item);
        }
        dock.removeAllViews();
        for (View v : items) dock.addView(v);
        dock.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);

        int focus = lastFocused == null ? 0 : keys.indexOf(lastFocused);
        if (focus >= 0 && focus < items.size()) items.get(focus).requestFocus();
        else allApps.requestFocus();
    }
}
