package com.nokoding.deck;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Thin vertical divider between pinned apps and recent apps in the dock. Not focusable. */
final class Rule extends View {
    private final Paint line = Ui.stroke(1), dot = Ui.fill();

    Rule(Context c) {
        super(c);
        setWillNotDraw(false);
        themeChanged();
    }

    @Override public boolean hasOverlappingRendering() { return false; }

    void themeChanged() {
        line.setColor(Ui.alpha(Ui.T.a, 0.45f));
        line.setStrokeWidth(Math.max(1f, Ui.px(2)));
        dot.setColor(Ui.T.a);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float x = getWidth() / 2f, h = getHeight();
        c.drawLine(x, h * 0.18f, x, h * 0.82f, line);
        c.drawRect(x - Ui.px(3), h * 0.18f - Ui.px(3), x + Ui.px(3), h * 0.18f + Ui.px(3), dot);
    }
}
