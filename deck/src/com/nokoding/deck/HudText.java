package com.nokoding.deck;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

/** One line of HUD text with optional accent prefix and a short RGB split glitch. */
final class HudText extends View {
    static final int LEFT = 0, CENTER = 1, RIGHT = 2;
    private static final long GLITCH_MS = 110;

    final Paint paint, prefixPaint, ghost;
    String text = "", prefix = "";
    int align;
    boolean useAccent;
    private long glitchAt = -10000;
    private int seed = 99;
    boolean effects = true;
    private final float baseSize;
    private String shown = "";

    HudText(Context c, Typeface tf, float designSize, int align) {
        super(c);
        baseSize = Ui.px(designSize);
        paint = Ui.text(tf, baseSize);
        prefixPaint = Ui.text(tf, baseSize);
        ghost = Ui.text(tf, baseSize);
        this.align = align;
        setWillNotDraw(false);
        themeChanged();
    }

    @Override public boolean hasOverlappingRendering() { return false; }

    /** Letter spacing in em (Orbitron caps labels). */
    HudText spacing(float em) {
        paint.setLetterSpacing(em);
        prefixPaint.setLetterSpacing(em);
        ghost.setLetterSpacing(em);
        fit();
        return this;
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { fit(); }

    /** Wide fonts: shrink to fit the box (down to 65 percent), then cut with an ellipsis. */
    private void fit() {
        int w = getWidth();
        paint.setTextSize(baseSize);
        prefixPaint.setTextSize(baseSize);
        ghost.setTextSize(baseSize);
        shown = text;
        if (w <= 0 || text.isEmpty()) return;
        float pw = prefix.isEmpty() ? 0 : prefixPaint.measureText(prefix);
        float tw = paint.measureText(text);
        if (pw + tw <= w) return;
        float k = Math.max(0.65f, w / (pw + tw));
        float sz = baseSize * k;
        paint.setTextSize(sz);
        prefixPaint.setTextSize(sz);
        ghost.setTextSize(sz);
        pw = prefix.isEmpty() ? 0 : prefixPaint.measureText(prefix);
        if (pw + paint.measureText(text) > w) {
            CharSequence cs = android.text.TextUtils.ellipsize(text, new android.text.TextPaint(paint), Math.max(1f, w - pw), android.text.TextUtils.TruncateAt.END);
            shown = cs.toString();
        }
    }

    HudText accent() { useAccent = true; themeChanged(); return this; }

    HudText dim() { paint.setColor(Ui.T.dim); dimmed = true; return this; }
    private boolean dimmed;

    void themeChanged() {
        Ui.Theme t = Ui.T;
        paint.setColor(useAccent ? t.a : (dimmed ? t.dim : t.text));
        prefixPaint.setColor(t.a);
        invalidate();
    }

    void set(String p, String s) {
        if (s == null) s = "";
        if (p == null) p = "";
        if (s.equals(text) && p.equals(prefix)) return;
        text = s;
        prefix = p;
        fit();
        invalidate();
    }

    void set(String s) { set(prefix, s); }

    void glitch() {
        if (!effects) return;
        glitchAt = SystemClock.uptimeMillis();
        seed = (int) glitchAt;
        invalidate();
    }

    private float rnd() {
        seed = seed * 1103515245 + 12345;
        return ((seed >>> 8) & 0xFFFF) / 65535f - 0.5f;
    }

    @Override
    protected void onDraw(Canvas c) {
        float pw = prefix.isEmpty() ? 0 : prefixPaint.measureText(prefix);
        String text = shown;
        float tw = paint.measureText(text);
        float total = pw + tw;
        float x = align == LEFT ? 0 : (align == CENTER ? (getWidth() - total) / 2f : getWidth() - total);
        float base = getHeight() - paint.descent() - Ui.px(2);
        long dt = SystemClock.uptimeMillis() - glitchAt;
        if (effects && dt < GLITCH_MS) {
            Ui.Theme t = Ui.T;
            float j = Ui.px(6);
            ghost.setColor(Ui.alpha(t.b, 0.8f));
            c.drawText(text, x + pw + rnd() * j, base + rnd() * Ui.px(2), ghost);
            ghost.setColor(Ui.alpha(t.a, 0.7f));
            c.drawText(text, x + pw + rnd() * j, base, ghost);
            postInvalidateOnAnimation();
        }
        if (pw > 0) c.drawText(prefix, x, base, prefixPaint);
        c.drawText(text, x + pw, base, paint);
    }
}
