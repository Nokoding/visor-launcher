package com.nokoding.deck;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.os.SystemClock;
import android.view.View;

/**
 * Lock-on reticle. While focus travels the outline slides and the corner brackets
 * stay open. The host calls {@link #lock} once, after the outline has arrived and
 * input has been idle; the brackets then close onto the element one time
 * (140 ms, small glitch, light sheen). {@link #unlock} opens them again quickly.
 * There is no repeating in and out loop.
 */
final class FocusFrame extends View {
    static final float MARGIN = 34f;   // design px around the target
    static final long OPEN_MS = 110, LOCK_MS = 140, GLITCH_MS = 90, SHEEN_MS = 380;

    private final Path card = new Path();
    private final Path g1 = new Path(), g2 = new Path(), g3 = new Path();
    private final Paint outline = Ui.stroke(1), inner = Ui.stroke(1), glow = Ui.stroke(1);
    private final Paint bracket = Ui.stroke(1), slice = Ui.fill(), sheen = Ui.fill();
    private final Paint tagBg = Ui.fill(), tagText = Ui.text(Ui.MONO_B, 14), arcPaint = Ui.stroke(1);
    private final Matrix arcM = new Matrix();
    private float arcAngle, arcAlpha;
    private final Matrix m = new Matrix();
    private LinearGradient sheenShader;
    private long animAt = -100000;
    private boolean locked;
    private int seed = 1234567;
    boolean effects = true;    // lock pulse (off with Reduce effects)
    boolean detail = true;     // glitch and sheen (off in lite mode)
    float cutBig = 0.13f, cutSmall = 0.035f;
    String tag;
    private float cl, ct, cr, cb;

    FocusFrame(Context c) {
        super(c);
        bracket.setStrokeCap(Paint.Cap.SQUARE);
        setWillNotDraw(false);
        themeChanged();
    }

    @Override public boolean hasOverlappingRendering() { return false; }

    void themeChanged() {
        Ui.Theme t = Ui.T;
        outline.setColor(t.a);
        inner.setColor(Ui.alpha(t.b, 0.55f));
        bracket.setColor(t.a);
        slice.setColor(Ui.alpha(t.b, 0.75f));
        tagBg.setColor(t.a);
        tagText.setColor(t.bg0);
        // centered at 0,0 and moved by the local matrix, so resizing never allocates
        arcPaint.setShader(new SweepGradient(0f, 0f, new int[]{t.a, Ui.alpha(t.a, 0f), Ui.alpha(t.a, 0f), t.a},
                new float[]{0f, 0.14f, 0.86f, 1f}));
        rebuild();
        invalidate();
    }

    void setCut(float big, float small) {
        if (big == cutBig && small == cutSmall) return;
        cutBig = big;
        cutSmall = small;
        rebuild();
        invalidate();
    }

    void setTag(String t) {
        if (t == null ? tag == null : t.equals(tag)) return;
        tag = t;
        invalidate();
    }

    boolean isLocked() { return locked; }

    /** Pointer mode: light the edge that faces the cursor. alpha 0 turns it off. */
    void setArc(float angleDeg, float alpha) {
        if (Math.abs(angleDeg - arcAngle) < 0.5f && Math.abs(alpha - arcAlpha) < 0.02f) return;
        arcAngle = angleDeg;
        arcAlpha = alpha;
        invalidate();
    }

    void lock(long now) {
        if (locked) return;
        locked = true;
        animAt = now;
        seed = (int) now * 1103515245 + 12345;
        invalidate();
    }

    void unlock(long now) {
        if (!locked) return;
        locked = false;
        animAt = now;
        invalidate();
    }

    /** Immediate state change with no animation (first frame, rebuilds). */
    void setLocked(boolean l) {
        locked = l;
        animAt = -100000;
        invalidate();
    }

    /** 1 when the brackets sit on the element, 0 when fully open. */
    private float closed(long now) {
        long dt = now - animAt;
        if (!effects) return locked ? 1f : 0f;
        if (locked) {
            float p = Math.min(1f, dt / (float) LOCK_MS);
            return 1f - (1f - p) * (1f - p) * (1f - p);
        }
        float p = Math.min(1f, dt / (float) OPEN_MS);
        return (1f - p) * (1f - p);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) { rebuild(); }

    private void rebuild() {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float mg = Ui.px(MARGIN);
        cl = mg; ct = mg; cr = w - mg; cb = h - mg;
        float ch = Math.max(1f, cb - ct);
        float big = ch * cutBig, small = ch * cutSmall;
        Ui.cut(card, cl, ct, cr, cb, big, small);
        float i1 = Ui.px(3), i2 = Ui.px(8), i3 = Ui.px(15);
        Ui.cut(g1, cl - i1, ct - i1, cr + i1, cb + i1, big + i1 * 0.6f, small + i1 * 0.6f);
        Ui.cut(g2, cl - i2, ct - i2, cr + i2, cb + i2, big + i2 * 0.6f, small + i2 * 0.6f);
        Ui.cut(g3, cl - i3, ct - i3, cr + i3, cb + i3, big + i3 * 0.6f, small + i3 * 0.6f);
        outline.setStrokeWidth(Ui.px(2.6f));
        inner.setStrokeWidth(Math.max(1f, Ui.px(1f)));
        bracket.setStrokeWidth(Ui.px(4f));
        tagText.setTextSize(Ui.px(15));
        arcPaint.setStrokeWidth(Ui.px(5f));
        if (sheenShader == null) {
            sheenShader = new LinearGradient(0, 0, 1, 0,
                    new int[]{0x00FFFFFF, 0x30FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP);
            sheen.setShader(sheenShader);
        }
    }

    private float rnd() {
        seed = seed * 1103515245 + 12345;
        return ((seed >>> 8) & 0xFFFF) / 65535f;
    }

    @Override
    protected void onDraw(Canvas c) {
        if (getWidth() <= 0 || cr <= cl) return;
        Ui.Theme t = Ui.T;
        long now = SystemClock.uptimeMillis();
        long dt = now - animAt;
        float k = closed(now);
        boolean animating = effects && dt < Math.max(LOCK_MS, SHEEN_MS) + 20;

        float gl = 0.55f + 0.45f * k;
        glow.setColor(Ui.alpha(t.a, 0.07f * gl)); glow.setStrokeWidth(Ui.px(13)); c.drawPath(g3, glow);
        glow.setColor(Ui.alpha(t.a, 0.16f * gl)); glow.setStrokeWidth(Ui.px(8));  c.drawPath(g2, glow);
        glow.setColor(Ui.alpha(t.a, 0.42f * gl)); glow.setStrokeWidth(Ui.px(3.5f)); c.drawPath(g1, glow);

        float jx = 0f;
        boolean glitching = locked && effects && detail && dt < GLITCH_MS;
        if (glitching) {
            jx = (rnd() - 0.5f) * Ui.px(12);
            c.save();
            c.translate(jx, 0);
            c.drawPath(card, inner);
            c.restore();
            for (int i = 0; i < 2; i++) {
                float y = ct + rnd() * (cb - ct);
                float x0 = cl + rnd() * (cr - cl) * 0.6f;
                c.drawRect(x0, y, x0 + (cr - cl) * (0.15f + rnd() * 0.3f), y + Ui.px(2 + rnd() * 4), slice);
            }
        }
        c.drawPath(card, outline);
        if (arcAlpha > 0.01f) {
            arcM.setRotate(arcAngle);
            arcM.postTranslate(getWidth() / 2f, getHeight() / 2f);
            arcPaint.getShader().setLocalMatrix(arcM);
            arcPaint.setAlpha(Math.round(255 * Math.min(1f, arcAlpha)));
            c.drawPath(card, arcPaint);
        }

        if (locked && effects && detail && dt < SHEEN_MS && sheenShader != null) {
            float p = dt / (float) SHEEN_MS;
            p = 1f - (1f - p) * (1f - p);
            float band = Math.max(8f, (cr - cl) * 0.22f);
            float x = cl - band + p * ((cr - cl) + band * 2f);
            m.reset();
            m.setScale(band, 1f);
            m.postSkew(-0.45f, 0f);
            m.postTranslate(x, 0);
            sheenShader.setLocalMatrix(m);
            c.save();
            c.clipPath(card);
            c.drawRect(cl, ct, cr, cb, sheen);
            c.restore();
        }

        // corner brackets: open while traveling, closed once locked
        float spread = Ui.px(20) * (1f - k);
        bracket.setColor(Ui.alpha(t.a, 0.55f + 0.45f * k));
        float o = Ui.px(9) + spread, arm = Math.max(Ui.px(14), Math.min(Ui.px(30), (cb - ct) * 0.28f));
        float l = cl - o + jx * 0.5f, r = cr + o, tp = ct - o, b = cb + o;
        c.drawLine(l, tp, l + arm, tp, bracket); c.drawLine(l, tp, l, tp + arm, bracket);
        c.drawLine(r, tp, r - arm, tp, bracket); c.drawLine(r, tp, r, tp + arm, bracket);
        c.drawLine(l, b, l + arm, b, bracket);   c.drawLine(l, b, l, b - arm, bracket);
        c.drawLine(r, b, r - arm, b, bracket);   c.drawLine(r, b, r, b - arm, bracket);

        if (tag != null && !tag.isEmpty() && k > 0.5f) {
            float th = Ui.px(22), tw = tagText.measureText(tag) + Ui.px(14);
            float tx = cl + (cb - ct) * cutBig, ty = ct - th - Ui.px(4);
            if (ty > 0) {
                c.drawRect(tx, ty, tx + tw, ty + th, tagBg);
                c.drawText(tag, tx + Ui.px(7), ty + th - Ui.px(6), tagText);
            }
        }
        if (animating) postInvalidateOnAnimation();
    }
}
