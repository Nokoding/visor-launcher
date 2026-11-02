package com.nokoding.deck;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** Absolute layout in real pixels. Positions come from design px times {@link Ui#u}. */
class Abs extends ViewGroup {
    interface SizeListener { void onSize(int w, int h); }

    static final class LP extends ViewGroup.LayoutParams {
        int x, y;
        LP(int x, int y, int w, int h) { super(w, h); this.x = x; this.y = y; }
    }

    SizeListener sizeListener;

    Abs(Context c) {
        super(c);
        setClipChildren(false);
        setClipToPadding(false);
    }

    /** Place a child using design pixels. */
    void put(View v, float x, float y, float w, float h) {
        putPx(v, Math.round(Ui.px(x)), Math.round(Ui.px(y)), Math.round(Ui.px(w)), Math.round(Ui.px(h)));
    }

    /** Place a child at a specific z index (design pixels). */
    void putAt(View v, int index, float x, float y, float w, float h) {
        LP lp = new LP(Math.round(Ui.px(x)), Math.round(Ui.px(y)), Math.round(Ui.px(w)), Math.round(Ui.px(h)));
        if (v.getParent() != null) removeView(v);
        addView(v, Math.max(0, Math.min(index, getChildCount())), lp);
    }

    void putPx(View v, int x, int y, int w, int h) {
        LP lp = new LP(x, y, Math.max(0, w), Math.max(0, h));
        if (v.getParent() == null) addView(v, lp);
        else v.setLayoutParams(lp);
    }

    /** Move and resize a child immediately without a layout pass (used for the focus frame). */
    static void setBoundsNow(View v, int x, int y, int w, int h) {
        ViewGroup.LayoutParams p = v.getLayoutParams();
        if (p instanceof LP) {
            LP lp = (LP) p;
            lp.x = x; lp.y = y; lp.width = w; lp.height = h;
        }
        if (v.getLeft() != x || v.getTop() != y || v.getWidth() != w || v.getHeight() != h) {
            v.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
            v.layout(x, y, x + w, y + h);
        }
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec), h = MeasureSpec.getSize(hSpec);
        setMeasuredDimension(w, h);
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i);
            ViewGroup.LayoutParams p = c.getLayoutParams();
            int cw = p.width >= 0 ? p.width : w, ch = p.height >= 0 ? p.height : h;
            c.measure(MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(ch, MeasureSpec.EXACTLY));
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i);
            ViewGroup.LayoutParams p = c.getLayoutParams();
            int x = 0, y = 0;
            if (p instanceof LP) { x = ((LP) p).x; y = ((LP) p).y; }
            c.layout(x, y, x + c.getMeasuredWidth(), y + c.getMeasuredHeight());
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (sizeListener != null) sizeListener.onSize(w, h);
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() { return new LP(0, 0, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT); }

    @Override
    protected boolean checkLayoutParams(LayoutParams p) { return p instanceof LP; }

    @Override
    protected LayoutParams generateLayoutParams(LayoutParams p) { return new LP(0, 0, p.width, p.height); }

    @Override
    public boolean shouldDelayChildPressedState() { return false; }
}
