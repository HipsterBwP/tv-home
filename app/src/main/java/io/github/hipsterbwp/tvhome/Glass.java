package io.github.hipsterbwp.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.widget.LinearLayout;

/**
 * A frosted-glass panel: draws the blurred wallpaper that lies behind it, a light tint and a
 * hairline edge, then its children. Android 10 has no live blur, so the blur is precomputed.
 */
final class Glass extends LinearLayout {
    private final Paint photo = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF box = new RectF();
    private final RectF dst = new RectF();
    private final int[] here = new int[2];
    private final int[] root = new int[2];
    private final float radius;
    private Bitmap blur;

    Glass(Context c, float radius) {
        super(c);
        this.radius = radius;
        tint.setColor(0x38FFFFFF);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(Tiles.dp(c, 1));
        edge.setColor(0x40FFFFFF);
        setClipChildren(false);
        setWillNotDraw(false);
    }

    void setBlur(Bitmap b) {
        blur = b;
        tint.setColor(b == null ? 0x55101820 : 0x38FFFFFF);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        box.set(0, 0, getWidth(), getHeight());
        clip.reset();
        clip.addRoundRect(box, radius, radius, Path.Direction.CW);
        c.save();
        c.clipPath(clip);
        if (blur != null) {
            View top = getRootView();
            getLocationInWindow(here);
            top.getLocationInWindow(root);
            float x = here[0] - root[0], y = here[1] - root[1];
            dst.set(-x, -y, top.getWidth() - x, top.getHeight() - y);
            c.drawBitmap(blur, null, dst, photo);
        }
        c.drawRect(box, tint);
        c.restore();
        float h = edge.getStrokeWidth() / 2;
        box.inset(h, h);
        c.drawRoundRect(box, radius, radius, edge);
    }
}
