package com.nokoding.deck;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/**
 * Soft spotlight that follows the pointer. Drawn once into its display list with one
 * RadialGradient; moving it only changes translationX/Y (no redraw, no allocation).
 */
final class CursorGlow extends View {
    private final Paint paint = Ui.fill();
    private float r;

    CursorGlow(Context c) {
        super(c);
        setWillNotDraw(false);
    }

    @Override public boolean hasOverlappingRendering() { return false; }

    float radius() { return r; }

    void themeChanged() { rebuild(); invalidate(); }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { rebuild(); }

    private void rebuild() {
        r = Math.min(getWidth(), getHeight()) / 2f;
        if (r <= 0f) return;
        Ui.Theme t = Ui.T;
        paint.setShader(new RadialGradient(r, r, r,
                new int[]{Ui.alpha(t.a, 0.26f), Ui.alpha(t.b, 0.10f), 0}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
    }

    void moveTo(float x, float y) {
        setTranslationX(x - r);
        setTranslationY(y - r);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (r > 0f) c.drawCircle(r, r, r, paint);
    }
}
