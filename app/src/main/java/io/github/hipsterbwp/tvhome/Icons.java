package io.github.hipsterbwp.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Thin white line icons for the top-bar buttons, drawn in code so they stay crisp at any size. */
final class Icons {
    private Icons() {}

    static Drawable grid() {
        return new Line() {
            @Override
            void paint(Canvas c, float s, Paint p) {
                float g = s * 0.14f, w = (s - g) / 2 - p.getStrokeWidth();
                float o = p.getStrokeWidth() / 2;
                RectF r = new RectF();
                for (int i = 0; i < 4; i++) {
                    float x = o + (i % 2) * (w + g + o), y = o + (i / 2) * (w + g + o);
                    r.set(x, y, x + w, y + w);
                    c.drawRoundRect(r, s * 0.08f, s * 0.08f, p);
                }
            }
        };
    }

    static Drawable gear() {
        return new Line() {
            @Override
            void paint(Canvas c, float s, Paint p) {
                float cx = s / 2, outer = s * 0.40f, inner = s * 0.30f, hole = s * 0.13f;
                for (int i = 0; i < 8; i++) {
                    c.save();
                    c.rotate(i * 45f, cx, cx);
                    c.drawLine(cx, cx - inner, cx, cx - outer, p);
                    c.restore();
                }
                c.drawCircle(cx, cx, inner, p);
                c.drawCircle(cx, cx, hole, p);
            }
        };
    }

    private abstract static class Line extends Drawable {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Line() {
            p.setColor(0xFFFFFFFF);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
        }

        abstract void paint(Canvas c, float size, Paint p);

        @Override
        public void draw(Canvas c) {
            float s = Math.min(getBounds().width(), getBounds().height());
            p.setStrokeWidth(Math.max(1.5f, s * 0.09f));
            c.save();
            c.translate(getBounds().left, getBounds().top);
            paint(c, s, p);
            c.restore();
        }

        @Override
        public void setAlpha(int alpha) {
            p.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
            p.setColorFilter(cf);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
