package com.nokoding.deck;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.text.TextPaint;
import android.view.View;

/**
 * 2.8: the "keep this display?" prompt of a game profile. Shown in front of Deck (LAUNCH WITH PROFILE) or by the
 * PS overlay over the app, the first time a profile value is applied. It counts down and the host reverts when it
 * reaches zero (ProfileMachine does that, this view only draws). A or OK keeps it, B or Back reverts at once.
 * One small view, no per frame work: the host calls setSeconds about twice a second.
 */
final class KeepView extends View {
    private final TextPaint head, big, small;
    private final Paint fill = Ui.fill(), edge = Ui.stroke(2), scrim = Ui.fill();
    private final Path shape = new Path();
    private String line = "";
    private int seconds;
    private boolean gamepad;

    KeepView(Context c) {
        super(c);
        head = tp(Ui.HEAD, 30);
        big = tp(Ui.COND_B, 38);
        small = tp(Ui.MONO, 20);
        setWillNotDraw(false);
    }

    private static TextPaint tp(android.graphics.Typeface tf, float size) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setTypeface(tf);
        p.setTextSize(Ui.px(size));
        return p;
    }

    @Override public boolean hasOverlappingRendering() { return false; }

    @Override public boolean performClick() { return super.performClick(); }

    void set(String profileLine, int secondsLeft, boolean pad) {
        line = profileLine == null ? "" : profileLine;
        seconds = Math.max(0, secondsLeft);
        gamepad = pad;
        invalidate();
    }

    void setSeconds(int s) {
        if (s == seconds) return;
        seconds = Math.max(0, s);
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        Ui.Theme t = Ui.T;
        scrim.setColor(Ui.alpha(t.bg0, 0.6f));
        c.drawRect(0, 0, getWidth(), getHeight(), scrim);
        float w = Ui.px(900), h = Ui.px(330), x = (Ui.W - w) / 2f, y = (Ui.H - h) / 2f;
        Ui.cut(shape, x, y, x + w, y + h, Ui.px(30), Ui.px(10));
        fill.setColor(Ui.alpha(t.panel, 0.98f));
        c.drawPath(shape, fill);
        edge.setColor(Ui.alpha(t.a, 0.9f));
        c.drawPath(shape, edge);
        head.setColor(t.a);
        c.drawText("//", x + Ui.px(40), y + Ui.px(70), head);
        head.setColor(t.text);
        c.drawText("KEEP THIS DISPLAY?", x + Ui.px(40) + head.measureText("// "), y + Ui.px(70), head);
        big.setColor(t.text);
        c.drawText(line, x + Ui.px(40), y + Ui.px(140), big);
        small.setColor(t.dim);
        c.drawText("REVERTING BY ITSELF IN " + seconds + " SECONDS", x + Ui.px(40), y + Ui.px(190), small);
        small.setColor(t.dim);
        c.drawText(gamepad ? "A KEEP IT     B PUT IT BACK NOW" : "OK KEEP IT     BACK PUT IT BACK NOW", x + Ui.px(40), y + h - Ui.px(40), small);
    }
}
