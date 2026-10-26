package io.github.hipsterbwp.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Every enabled TV app, alphabetically, over the blurred wallpaper. */
public class AllAppsActivity extends Activity {
    private static final int COLUMNS = 7;

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        return Nav.move(this, e) || super.dispatchKeyEvent(e);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Nav.warmUp(this);
        final PackageManager pm = getPackageManager();

        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> apps = new ArrayList<>();
        for (ResolveInfo r : pm.queryIntentActivities(query, 0)) {
            if (!r.activityInfo.packageName.equals(getPackageName())) apps.add(r);
        }
        Collections.sort(apps, new Comparator<ResolveInfo>() {
            @Override
            public int compare(ResolveInfo a, ResolveInfo b) {
                return a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString());
            }
        });

        DisplayMetrics m = getResources().getDisplayMetrics();
        int gap = Tiles.dp(this, 14);
        int pad = Tiles.dp(this, 64);
        int width = (m.widthPixels - 2 * pad - (COLUMNS - 1) * gap) / COLUMNS;

        List<View> items = new ArrayList<>();
        for (ResolveInfo r : apps) {
            final Intent launch = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                    .setClassName(r.activityInfo.packageName, r.activityInfo.name);
            items.add(Tiles.item(this, r.loadIcon(pm), r.loadLabel(pm), width, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Tiles.launch(AllAppsActivity.this, launch);
                }
            }));
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {0xFF3D6E8F, 0xFF1C3B57, 0xFF0D1B2A}));
        final ImageView bg = new ImageView(this);
        bg.setScaleType(ImageView.ScaleType.FIT_XY);
        root.addView(bg, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        View shade = new View(this);
        shade.setBackgroundColor(0x59000000);
        root.addView(shade, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setPadding(pad, Tiles.dp(this, 40), pad, Tiles.dp(this, 40));
        TextView title = new TextView(this);
        title.setText("All apps");
        title.setTextColor(Color.WHITE);
        title.setTypeface(Tiles.LIGHT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.bottomMargin = Tiles.dp(this, 20);
        col.addView(title, tp);
        col.addView(Tiles.grid(this, items, COLUMNS, gap));

        ScrollView scroll = new ScrollView(this);
        scroll.setClipChildren(false);
        scroll.addView(col);
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        if (!items.isEmpty()) items.get(0).requestFocus();

        new Wallpaper(this).load(new Wallpaper.Callback() {
            @Override
            public void ready(Bitmap photo, Bitmap blur, String credit) {
                bg.setImageBitmap(blur);
            }
        });
    }
}
