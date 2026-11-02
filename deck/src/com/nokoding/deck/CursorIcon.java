package com.nokoding.deck;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.PointerIcon;

/**
 * 2.1 themed cursor: a small accent colored reticle with a soft glow, made as a bitmap
 * PointerIcon (API 24+) and set on the root view, so it only shows while the pointer is
 * over Deck. Rebuilt when the theme or custom color changes. Reduce effects drops the glow.
 */
final class CursorIcon {
    private CursorIcon() {}

    private static PointerIcon cached;
    private static int cachedColor, cachedSize;
    private static boolean cachedGlow;

    /** Returns null if anything fails, so the caller falls back to the system arrow. */
    static PointerIcon get(boolean glow) {
        try {
            Ui.Theme t = Ui.T;
            int size = Math.max(32, Math.round(44 * Math.max(1f, Ui.u)));
            if (cached != null && cachedColor == t.a && cachedSize == size && cachedGlow == glow) return cached;
            Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(b);
            float cx = size / 2f, cy = size / 2f, r = size * 0.21f;
            if (glow) {
                Paint g = Ui.fill();
                g.setShader(new RadialGradient(cx, cy, size / 2f,
                        new int[]{Ui.alpha(t.a, 0.42f), Ui.alpha(t.b, 0.14f), 0}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
                cv.drawCircle(cx, cy, size / 2f, g);
            }
            float sw = Math.max(1.5f, size / 22f);
            // dark under stroke so the reticle reads on bright banners
            Paint under = Ui.stroke(sw + 2f);
            under.setColor(Ui.alpha(t.bg0, 0.75f));
            under.setStrokeCap(Paint.Cap.SQUARE);
            Paint line = Ui.stroke(sw);
            line.setColor(t.a);
            line.setStrokeCap(Paint.Cap.SQUARE);
            for (Paint p : new Paint[]{under, line}) {
                // ring with four gaps, then four ticks outside it
                for (int i = 0; i < 4; i++) cv.drawArc(cx - r, cy - r, cx + r, cy + r, i * 90 + 15, 60, false, p);
                float a = r * 1.35f, z = size * 0.47f;
                cv.drawLine(cx, cy - a, cx, cy - z, p);
                cv.drawLine(cx, cy + a, cx, cy + z, p);
                cv.drawLine(cx - a, cy, cx - z, cy, p);
                cv.drawLine(cx + a, cy, cx + z, cy, p);
            }
            Paint dot = Ui.fill();
            dot.setColor(t.text);
            cv.drawCircle(cx, cy, Math.max(1.5f, sw * 0.8f), dot);
            PointerIcon pi = PointerIcon.create(b, cx, cy);
            cached = pi;
            cachedColor = t.a;
            cachedSize = size;
            cachedGlow = glow;
            return pi;
        } catch (Throwable e) {
            return null;
        }
    }

    static void invalidate() { cached = null; }
}
