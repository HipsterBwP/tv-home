package io.github.hipsterbwp.tvhome;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.widget.ScrollView;

/**
 * A vertical list that glides to keep the focused row clear of its edges, and fades rows out
 * at whichever edge has more rows beyond it.
 */
final class FadeList extends ScrollView {
    private final Rect rect = new Rect();
    private boolean measuring;

    FadeList(Context c, int fadeLength) {
        super(c);
        setVerticalScrollBarEnabled(false);
        setVerticalFadingEdgeEnabled(true);
        setFadingEdgeLength(fadeLength);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setClipChildren(false);
    }

    // ScrollView jumps to focused children through this; return 0 so we can glide instead.
    @Override
    protected int computeScrollDeltaToGetChildRectOnScreen(Rect r) {
        return measuring ? super.computeScrollDeltaToGetChildRectOnScreen(r) : 0;
    }

    @Override
    public void requestChildFocus(View child, View focused) {
        super.requestChildFocus(child, focused);
        focused.getDrawingRect(rect);
        offsetDescendantRectToMyCoords(focused, rect);
        measuring = true;
        int delta = computeScrollDeltaToGetChildRectOnScreen(rect);
        measuring = false;
        if (delta != 0) smoothScrollBy(0, delta);
    }
}
