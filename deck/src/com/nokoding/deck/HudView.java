package com.nokoding.deck;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.view.View;

/** Static HUD chrome: top bar panel, safe area brackets, rails, and input hints with a mode transition. */
final class HudView extends View {
    static final long FLASH_MS = 380;

    private final Path bar = new Path(), mark = new Path();
    private final Paint barFill = Ui.fill(), barLine = Ui.stroke(1), accent = Ui.fill();
    private final Paint frame = Ui.stroke(1), tick = Ui.stroke(1), glyphLine = Ui.stroke(1), slice = Ui.fill();
    private final Paint brand, brandSub, small, smallA, glyphText;
    String brandName = "VISOR";
    int mode = InputMode.REMOTE, style = InputMode.STYLE_GENERIC;
    boolean rightStick = true, effects = true;
    private long flashAt = -100000;

    HudView(Context c) {
        super(c);
        brand = Ui.text(Ui.HEAD, Ui.px(34));
        brand.setLetterSpacing(Ui.HEAD_SPACING * 2f);
        brandSub = Ui.text(Ui.MONO_B, Ui.px(15));
        small = Ui.text(Ui.MONO, Ui.px(17));
        smallA = Ui.text(Ui.MONO_B, Ui.px(17));
        glyphText = Ui.text(Ui.MONO_B, Ui.px(17));
        glyphLine.setStrokeCap(Paint.Cap.ROUND);
        setWillNotDraw(false);
        themeChanged();
    }

    @Override public boolean hasOverlappingRendering() { return false; }

    void themeChanged() {
        Ui.Theme t = Ui.T;
        barFill.setColor(Ui.alpha(t.panel, 0.70f));
        barLine.setColor(Ui.alpha(t.a, 0.50f));
        barLine.setStrokeWidth(Math.max(1f, Ui.px(1.5f)));
        accent.setColor(t.a);
        frame.setColor(Ui.alpha(t.a, 0.70f));
        frame.setStrokeWidth(Ui.px(3));
        tick.setColor(Ui.alpha(t.dim, 0.35f));
        tick.setStrokeWidth(Math.max(1f, Ui.px(1.2f)));
        glyphLine.setStrokeWidth(Math.max(1.5f, Ui.px(2f)));
        slice.setColor(Ui.alpha(t.b, 0.5f));
        brand.setColor(t.text);
        brandSub.setColor(t.a);
        rebuild();
        invalidate();
    }

    void setMode(int m, int st, boolean right, boolean animate) {
        if (m == mode && st == style && right == rightStick) return;
        mode = m;
        style = st;
        rightStick = right;
        if (animate) flashAt = SystemClock.uptimeMillis();
        invalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { rebuild(); }

    private void rebuild() {
        Ui.cut(bar, Ui.px(96), Ui.px(48), Ui.px(1824), Ui.px(128), Ui.px(26), Ui.px(8));
        mark.reset();
        float x = Ui.px(118), y = Ui.px(66);
        mark.moveTo(x + Ui.px(10), y);
        mark.lineTo(x + Ui.px(18), y);
        mark.lineTo(x + Ui.px(8), y + Ui.px(44));
        mark.lineTo(x, y + Ui.px(44));
        mark.close();
    }

    private float hint(Canvas c, float x, float y, String key, String word) {
        c.drawText(key, x, y, smallA);
        x += smallA.measureText(key) + Ui.px(10);
        c.drawText(word, x, y, small);
        return x + small.measureText(word) + Ui.px(38);
    }

    private float pad(Canvas c, float x, float y, int btn, String word) {
        float r = Ui.px(14), cy = y - Ui.px(6);
        x += Glyphs.draw(c, style, btn, rightStick, x, cy, r, glyphLine, glyphText) + Ui.px(10);
        c.drawText(word, x, y, small);
        return x + small.measureText(word) + Ui.px(34);
    }

    @Override
    protected void onDraw(Canvas c) {
        Ui.Theme t = Ui.T;
        c.drawPath(bar, barFill);
        c.drawPath(bar, barLine);
        c.drawPath(mark, accent);
        c.drawText(brandName, Ui.px(146), Ui.px(102), brand);
        c.drawText("//HOME", Ui.px(154) + brand.measureText(brandName), Ui.px(102), brandSub);
        for (int i = 0; i < 12; i++) {
            float tx = Ui.px(420 + i * 10);
            c.drawLine(tx, Ui.px(128), tx, Ui.px(128 + (i % 4 == 0 ? 10 : 5)), tick);
        }

        float l = Ui.px(70), tp = Ui.px(30), r = Ui.px(1850), b = Ui.px(1050), a = Ui.px(42);
        c.drawLine(l, tp + a, l, tp, frame); c.drawLine(l, tp, l + a, tp, frame);
        c.drawLine(r - a, tp, r, tp, frame); c.drawLine(r, tp, r, tp + a, frame);
        c.drawLine(l, b - a, l, b, frame); c.drawLine(l, b, l + a, b, frame);
        c.drawLine(r - a, b, r, b, frame); c.drawLine(r, b, r, b - a, frame);
        for (int i = 0; i <= 20; i++) {
            float ty = Ui.px(250 + i * 22), len = Ui.px(i % 5 == 0 ? 16 : 7);
            c.drawLine(Ui.px(74), ty, Ui.px(74) + len, ty, tick);
            c.drawLine(Ui.px(1846), ty, Ui.px(1846) - len, ty, tick);
        }

        // input hints with a short slide and glitch when the input mode changes
        long dt = SystemClock.uptimeMillis() - flashAt;
        float p = effects ? Math.min(1f, dt / (float) FLASH_MS) : 1f;
        float e = 1f - (1f - p) * (1f - p);
        small.setColor(Ui.alpha(t.dim, e));
        smallA.setColor(Ui.alpha(t.a, e));
        glyphLine.setColor(Ui.alpha(t.text, e));
        glyphText.setColor(Ui.alpha(t.text, e));
        float hy = Ui.px(1012), hx = Ui.px(100) + (1f - e) * Ui.px(30);
        if (mode == InputMode.POINTER) {
            hx = hint(c, hx, hy, "CLICK", "OPEN");
            hx = hint(c, hx, hy, "SCROLL", "SPIN / SCROLL");
            hint(c, hx, hy, "ANY KEY", "BACK TO REMOTE OR GAMEPAD");
        } else if (mode == InputMode.GAMEPAD) {
            hx = pad(c, hx, hy, Glyphs.A, "OPEN");
            hx = pad(c, hx, hy, Glyphs.X, "OPTIONS");
            hx = pad(c, hx, hy, Glyphs.Y, "WHEEL / GRID");
            hx = pad(c, hx, hy, Glyphs.STICK, "SPIN");
            hx = pad(c, hx, hy, Glyphs.START, "SETUP");
            hint(c, hx, hy, style == InputMode.STYLE_SONY ? "PS" : "MODE", "QUICK MENU");
        } else {
            hx = hint(c, hx, hy, "OK", "OPEN");
            hx = hint(c, hx, hy, "HOLD OK", "OPTIONS");
            hint(c, hx, hy, "MENU / HOLD BACK", "SETUP");
        }
        String tag = mode == InputMode.GAMEPAD ? "INPUT // GAMEPAD" : (mode == InputMode.POINTER ? "INPUT // POINTER" : "INPUT // REMOTE");
        Paint tp2 = dt < 1600 ? smallA : small;
        c.drawText(tag, Ui.px(1820) - tp2.measureText(tag), hy, tp2);
        if (effects && dt < 120) {
            float y = hy - Ui.px(18) + ((int) dt % 3) * Ui.px(7);
            c.drawRect(Ui.px(100), y, Ui.px(100) + Ui.px(420) * (1f - p), y + Ui.px(3), slice);
        }
        small.setColor(t.dim);
        smallA.setColor(t.a);
        if (p < 1f || dt < 1700) postInvalidateOnAnimation();
    }
}
