package io.github.hipsterbwp.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * The TV's inputs (HDMI ports, Live TV and so on) in the same list style as the home screen,
 * named as they are in the TV's settings. Choosing one switches the TV to it.
 */
public class InputsActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        DisplayMetrics m = getResources().getDisplayMetrics();
        int screenH = Math.min(m.widthPixels, m.heightPixels);
        int side = Tiles.dp(this, 56);

        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0xFF0A1424, 0xFF14304A, 0xFF2E5A78}));
        final ImageView bg = new ImageView(this);
        bg.setScaleType(ImageView.ScaleType.FIT_XY);
        root.addView(bg, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        View shade = new View(this);
        shade.setBackgroundColor(0x59000000);
        root.addView(shade, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView title = new TextView(this);
        title.setText("Inputs");
        title.setTextColor(Color.WHITE);
        title.setTypeface(Tiles.LIGHT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        tp.leftMargin = side;
        tp.topMargin = Tiles.dp(this, 40);
        root.addView(title, tp);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(false);
        List<View> rows = new ArrayList<>();
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim != null) {
            for (final TvInputInfo info : tim.getTvInputList()) {
                if (info.isHidden(this) || info.getParentId() != null) continue;
                CharSequence custom = info.loadCustomLabel(this);
                CharSequence name = custom != null && custom.length() > 0 ? custom : info.loadLabel(this);
                boolean connected = tim.getInputState(info.getId()) != TvInputManager.INPUT_STATE_DISCONNECTED;
                View row = Tiles.row(this, Icons.input(), name, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        open(info);
                    }
                });
                row.setAlpha(connected ? 1f : 0.55f);
                rows.add(row);
            }
        }
        if (rows.isEmpty()) {
            TextView none = new TextView(this);
            none.setText("No inputs found");
            none.setTextColor(Color.WHITE);
            none.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
            list.addView(none);
        }
        for (View r : rows) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            p.bottomMargin = Tiles.dp(this, 4);
            list.addView(r, p);
        }
        FadeList scroller = new FadeList(this, Tiles.dp(this, 64));
        scroller.addView(list);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Math.round(screenH * 0.62f), Gravity.TOP | Gravity.START);
        lp.leftMargin = side - Tiles.dp(this, 12);
        lp.topMargin = Math.round(screenH * 0.20f);
        root.addView(scroller, lp);

        setContentView(root);
        if (!rows.isEmpty()) rows.get(0).requestFocus();

        new Wallpaper(this).load(new Wallpaper.Callback() {
            @Override
            public void ready(Bitmap photo, Bitmap blur, String credit) {
                bg.setImageBitmap(blur);
            }
        });
    }

    /** HDMI and other pass-through inputs open directly; tuner inputs open their channels. */
    private void open(TvInputInfo info) {
        Intent i = info.isPassthroughInput()
                ? new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(info.getId()))
                : new Intent(Intent.ACTION_VIEW, TvContract.buildChannelsUriForInput(info.getId()));
        if (i.resolveActivity(getPackageManager()) == null) {
            // No app takes the input link (some TVs only open Live TV directly): use the TV app.
            i = Tiles.launchIntent(getPackageManager(), "com.sony.dtv.tvx");
            if (i == null) return;
        }
        Tiles.launch(this, i);
    }
}
