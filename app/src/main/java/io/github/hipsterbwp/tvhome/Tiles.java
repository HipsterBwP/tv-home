package io.github.hipsterbwp.tvhome;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** App lookup and launching, plus the focusable rows, grid items and buttons used by both screens. */
final class Tiles {
    private Tiles() {}

    static final Typeface LIGHT = Typeface.create("sans-serif-light", Typeface.NORMAL);
    static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    /** The launch intent for a TV app, or null when it is missing or disabled. */
    static Intent launchIntent(PackageManager pm, String pkg) {
        try {
            ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
            if (!info.enabled) return null;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        return i;
    }

    static void launch(Context c, Intent i) {
        Intent copy = new Intent(i).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(copy);
        } catch (ActivityNotFoundException | SecurityException ignored) {
            // App vanished or refused; the home screen simply stays put.
        }
    }

    static CharSequence label(PackageManager pm, String pkg, CharSequence fallback) {
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
        } catch (PackageManager.NameNotFoundException e) {
            return fallback;
        }
    }

    static Drawable icon(PackageManager pm, String pkg) {
        try {
            return pm.getApplicationIcon(pkg);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    static GradientDrawable rounded(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    /** Icon over a name; focus brightens it and adds a soft light pill behind it. */
    static View item(Context c, Drawable icon, CharSequence name, int width, View.OnClickListener onClick) {
        final LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(c, 6), dp(c, 12), dp(c, 6), dp(c, 10));
        box.setFocusable(true);
        box.setClickable(true);
        box.setContentDescription(name);
        box.setOnClickListener(onClick);

        final ImageView img = new ImageView(c);
        img.setImageDrawable(icon);
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        final int corner = dp(c, 12);
        img.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), corner);
            }
        });
        img.setClipToOutline(true);
        int s = dp(c, 52);
        box.addView(img, new LinearLayout.LayoutParams(s, s));

        final TextView t = new TextView(c);
        t.setText(name);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setTypeface(MEDIUM);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setShadowLayer(dp(c, 3), 0, dp(c, 1), 0x66000000);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tp.topMargin = dp(c, 8);
        box.addView(t, tp);

        final GradientDrawable pill = rounded(0x40FFFFFF, dp(c, 18));
        img.setAlpha(0.82f);
        t.setAlpha(0.78f);
        box.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean focused) {
                box.setBackground(focused ? pill : null);
                img.animate().alpha(focused ? 1f : 0.82f).setDuration(150).start();
                t.animate().alpha(focused ? 1f : 0.78f).setDuration(150).start();
                v.animate().scaleX(focused ? 1.06f : 1f).scaleY(focused ? 1.06f : 1f)
                        .setDuration(150).start();
            }
        });
        box.setLayoutParams(new LinearLayout.LayoutParams(width, LinearLayout.LayoutParams.WRAP_CONTENT));
        return box;
    }

    /** A home-list row: a white logo in a fixed slot, then the name; soft pill on focus. */
    static View row(Context c, Drawable logo, CharSequence name, View.OnClickListener onClick) {
        final LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(c, 12), dp(c, 5), dp(c, 18), dp(c, 5));
        r.setFocusable(true);
        r.setClickable(true);
        r.setContentDescription(name);
        r.setOnClickListener(onClick);

        // Every logo gets the same slot, about the height of the name's capitals, so the logos
        // read as one size and every name starts at the same x.
        ImageView img = new ImageView(c);
        img.setImageDrawable(logo);
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(c, 30), dp(c, 24));
        ip.topMargin = dp(c, 2);
        r.addView(img, ip);

        TextView t = new TextView(c);
        t.setText(name);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        t.setSingleLine(true);
        t.setShadowLayer(dp(c, 4), 0, dp(c, 1), 0x55000000);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tp.leftMargin = dp(c, 12);
        r.addView(t, tp);

        final GradientDrawable pill = rounded(0x33FFFFFF, dp(c, 14));
        r.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean focused) {
                r.setBackground(focused ? pill : null);
            }
        });
        return r;
    }

    /** A small corner button: text then a line icon, with a light pill when focused. */
    static View button(Context c, Drawable icon, String text, View.OnClickListener onClick) {
        final LinearLayout b = new LinearLayout(c);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        b.setPadding(dp(c, 12), dp(c, 6), dp(c, 12), dp(c, 6));
        b.setFocusable(true);
        b.setClickable(true);
        b.setOnClickListener(onClick);
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.setShadowLayer(dp(c, 3), 0, dp(c, 1), 0x66000000);
        b.addView(t);
        ImageView img = new ImageView(c);
        img.setImageDrawable(icon);
        int s = dp(c, 15);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(s, s);
        ip.leftMargin = dp(c, 10);
        b.addView(img, ip);
        final GradientDrawable pill = rounded(0x33FFFFFF, dp(c, 16));
        b.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean focused) {
                b.setBackground(focused ? pill : null);
            }
        });
        return b;
    }

    /** Lays views out in rows of {@code columns}. */
    static LinearLayout grid(Context c, List<View> items, int columns, int gap) {
        LinearLayout rows = new LinearLayout(c);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setClipChildren(false);
        LinearLayout row = null;
        for (int i = 0; i < items.size(); i++) {
            if (i % columns == 0) {
                row = new LinearLayout(c);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rp.bottomMargin = gap;
                rows.addView(row, rp);
            }
            View v = items.get(i);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) v.getLayoutParams();
            if (i % columns != 0) lp.leftMargin = gap;
            row.addView(v, lp);
        }
        return rows;
    }
}
