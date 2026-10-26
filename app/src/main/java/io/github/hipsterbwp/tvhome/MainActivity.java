package io.github.hipsterbwp.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
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
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
    private View inputs, allApps;
    private LinearLayout scoresBox;
    private List<Scores.League> scoreLeagues = new ArrayList<>();
    private final List<FrameLayout> scoreSlots = new ArrayList<>();
    private final Map<String, Integer> scorePos = new HashMap<>();
    private TextView verseText, verseRef;
    private int verseDay = -1;
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

    private final Runnable scoresTick = new Runnable() {
        @Override
        public void run() {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final List<Scores.League> leagues = Scores.load();
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            showScores(leagues);
                        }
                    });
                }
            }, "scores").start();
        }
    };

    private final Runnable rotateTick = new Runnable() {
        @Override
        public void run() {
            rotateScores();
            handler.postDelayed(this, 5000);
        }
    };

    private final Runnable verseTick = new Runnable() {
        @Override
        public void run() {
            showVerse();
            handler.postDelayed(this, 10 * 60 * 1000L);
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

        scoresBox = new LinearLayout(this);
        scoresBox.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams sbp = new FrameLayout.LayoutParams(
                Tiles.dp(this, 170), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        sbp.rightMargin = side;
        sbp.topMargin = Tiles.dp(this, 86);
        root.addView(scoresBox, sbp);

        LinearLayout verse = new LinearLayout(this);
        verse.setOrientation(LinearLayout.VERTICAL);
        verse.setGravity(Gravity.CENTER_HORIZONTAL);
        verseText = new TextView(this);
        verseText.setTextColor(Color.WHITE);
        verseText.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
        verseText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        verseText.setGravity(Gravity.CENTER_HORIZONTAL);
        verseText.setLineSpacing(0, 1.15f);
        verseText.setShadowLayer(Tiles.dp(this, 5), 0, Tiles.dp(this, 1), 0xB3000000);
        verse.addView(verseText);
        verseRef = new TextView(this);
        verseRef.setTextColor(0xCCFFFFFF);
        verseRef.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        verseRef.setLetterSpacing(0.12f);
        verseRef.setShadowLayer(Tiles.dp(this, 4), 0, Tiles.dp(this, 1), 0xB3000000);
        LinearLayout.LayoutParams vrp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vrp.topMargin = Tiles.dp(this, 6);
        verse.addView(verseRef, vrp);
        FrameLayout.LayoutParams vp = new FrameLayout.LayoutParams(
                Tiles.dp(this, 500), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        vp.bottomMargin = Tiles.dp(this, 34);
        root.addView(verse, vp);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setGravity(Gravity.END);
        inputs = Tiles.button(this, Icons.input(), "Inputs", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                lastFocused = "#inputs";
                startActivity(new Intent(MainActivity.this, InputsActivity.class));
            }
        });
        inputs.setId(View.generateViewId());
        actions.addView(inputs);
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
        handler.removeCallbacks(verseTick);
        handler.post(verseTick);
        handler.removeCallbacks(scoresTick);
        handler.post(scoresTick);
        handler.removeCallbacks(rotateTick);
        handler.postDelayed(rotateTick, 5000);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(weatherTick);
        handler.removeCallbacks(verseTick);
        handler.removeCallbacks(scoresTick);
        handler.removeCallbacks(rotateTick);
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
            row.setNextFocusRightId(inputs.getId());
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

    private void showVerse() {
        Calendar now = Calendar.getInstance();
        int day = Verse.dayKey(now);
        if (day == verseDay) return;
        Verse v = Verse.forDay(this, now);
        if (v == null) return;
        verseDay = day;
        verseText.setText("\u201C" + v.text + "\u201D");
        verseRef.setText(v.reference.toUpperCase(Locale.US) + " \u00B7 NKJV");
    }

    /** Rebuilds the league sections and schedules the next refresh: every minute around games. */
    private void showScores(List<Scores.League> leagues) {
        scoresBox.removeAllViews();
        scoreSlots.clear();
        long now = System.currentTimeMillis();
        boolean busy = false;
        Map<String, Integer> kept = new HashMap<>(scorePos);
        scorePos.clear();
        scoreLeagues = leagues;
        for (Scores.League league : leagues) {
            for (Scores.Game g : league.games) busy |= g.live || g.upcoming && g.start - now < 30 * 60 * 1000L;
            TextView heading = scoreText(league.heading, 10, 0xBFFFFFFF);
            heading.setLetterSpacing(0.15f);
            heading.setTypeface(Tiles.MEDIUM);
            heading.setPadding(0, 0, 0, Tiles.dp(this, 4));
            FrameLayout slot = new FrameLayout(this);
            Integer at = kept.get(league.heading);
            int pos = at == null ? 0 : at % league.games.size();
            scorePos.put(league.heading, pos);
            slot.addView(gameView(league.games.get(pos)));
            scoreSlots.add(slot);
            LinearLayout section = new LinearLayout(this);
            section.setOrientation(LinearLayout.VERTICAL);
            section.addView(heading);
            section.addView(slot);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bp.bottomMargin = Tiles.dp(this, 20);
            scoresBox.addView(section, bp);
        }
        handler.removeCallbacks(scoresTick);
        handler.postDelayed(scoresTick, busy ? 60 * 1000L : 15 * 60 * 1000L);
    }

    /** Moves every league with more than one game on to its next game, with a short fade. */
    private void rotateScores() {
        for (int i = 0; i < scoreLeagues.size() && i < scoreSlots.size(); i++) {
            final Scores.League league = scoreLeagues.get(i);
            if (league.games.size() < 2) continue;
            final int next = (scorePos.get(league.heading) + 1) % league.games.size();
            scorePos.put(league.heading, next);
            final FrameLayout slot = scoreSlots.get(i);
            slot.animate().alpha(0f).setDuration(300).withEndAction(new Runnable() {
                @Override
                public void run() {
                    slot.removeAllViews();
                    slot.addView(gameView(league.games.get(next)));
                    slot.animate().alpha(1f).setDuration(300).start();
                }
            }).start();
        }
    }

    private View gameView(Scores.Game g) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.addView(scoreLine(g.awayRank, g.awayName, g.awayScore, g.awayWon));
        block.addView(scoreLine(g.homeRank, g.homeName, g.homeScore, g.homeWon));
        TextView st = scoreText(g.live ? "\u25CF LIVE  " + g.status : g.status, 10, 0xBFFFFFFF);
        if (g.live) {
            SpannableString span = new SpannableString(st.getText());
            span.setSpan(new ForegroundColorSpan(0xFFFF5A5A), 0, 6, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            st.setText(span);
        }
        block.addView(st);
        return block;
    }

    private TextView scoreText(String text, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setShadowLayer(Tiles.dp(this, 3), 0, Tiles.dp(this, 1), 0x80000000);
        return t;
    }

    private View scoreLine(int rank, String team, String score, boolean won) {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        int color = won ? Color.WHITE : 0xD9FFFFFF;
        String label = team == null ? "" : rank > 0 ? "#" + rank + " " + team : team;
        TextView name = scoreText(label, 16, color);
        TextView pts = scoreText(score == null ? "" : score, 16, color);
        if (won) {
            name.setTypeface(Typeface.DEFAULT_BOLD);
            pts.setTypeface(Typeface.DEFAULT_BOLD);
        }
        line.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        line.addView(pts);
        return line;
    }
}
