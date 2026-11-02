package com.nokoding.deck;

import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * 2.3: draws part of a pre-measured string for TextReveal, with no allocation per frame.
 * A Piece is measured once (full width plus per character advances) when its text changes.
 * Left to right shows the first k characters from the piece's left edge. Right to left shows
 * the last k characters ending at its right edge, so the line grows from its end toward its
 * start. Either way the characters sit exactly where the full string puts them.
 */
final class TypeDraw {
    private TypeDraw() {}

    static final class Piece {
        String s = "";
        int n;
        float w;
        float[] adv = new float[16];

        void set(CharSequence text, Paint p) {
            s = text == null ? "" : text.toString();
            n = s.length();
            if (adv.length < n) adv = new float[Math.max(n, adv.length * 2)];
            w = n == 0 ? 0f : p.measureText(s);
            if (n > 0) p.getTextWidths(s, adv);
        }

        float head(int k) {
            float x = 0f;
            for (int i = 0; i < k && i < n; i++) x += adv[i];
            return x;
        }

        float tail(int k) {
            float x = 0f;
            for (int i = Math.max(0, n - k); i < n; i++) x += adv[i];
            return x;
        }
    }

    /**
     * Draws k characters of pc, whose full text spans x0 to x0 + pc.w. Paint align must be LEFT.
     * @return x of the typing head, where the cursor goes
     */
    static float draw(Canvas c, Piece pc, int k, float x0, float y, Paint paint, boolean rtl) {
        int n = pc.n;
        if (k > n) k = n;
        if (k <= 0) return rtl ? x0 + pc.w : x0;
        if (!rtl) {
            c.drawText(pc.s, 0, k, x0, y, paint);
            return x0 + (k == n ? pc.w : pc.head(k));
        }
        float tw = k == n ? pc.w : pc.tail(k), xs = x0 + pc.w - tw;
        c.drawText(pc.s, n - k, n, xs, y, paint);
        return xs;
    }

    /** Block cursor beside the typing head: right of it when typing left to right, left of it otherwise. */
    static void cursor(Canvas c, float hx, float baseline, Paint text, Paint fill, boolean rtl) {
        float h = -text.ascent() * 0.82f, w = text.getTextSize() * 0.48f, gap = text.getTextSize() * 0.06f;
        float l = rtl ? hx - gap - w : hx + gap;
        c.drawRect(l, baseline - h, l + w, baseline + text.descent() * 0.15f, fill);
    }
}
