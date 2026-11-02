package com.nokoding.deck;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

/** Small angular button for the top bar and the dock. Shows a vector glyph or an app thumbnail. */
final class Chip extends View {
    static final int G_NONE = 0, G_GEAR = 1, G_WIFI = 2, G_ETH = 3, G_OFF = 4, G_INPUT = 5, G_HOME = 6, G_PLUS = 7;

    int glyph;
    String label = "";
    AppStore.App app;
    IconCache.Icon icon;
    Spring focus;
    Runnable action;
    String id = "";
    /** Recent app chip in the dock: icon only, the name shows in the top bar. */
    boolean compact;

    private final Path shape = new Path(), gp = new Path();
    private final Paint plate = Ui.fill(), line = Ui.stroke(1), gpaint = Ui.stroke(1), gfill = Ui.fill();
    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final RectF r = new RectF();
    private final Path plateShape = new Path();
    private final Paint glass = Ui.fill(), glassEdge = Ui.stroke(1);
    private CharSequence shown = "";

    Chip(Context c) {
        super(c);
        text.setTypeface(Ui.COND_B);
        text.setLetterSpacing(Ui.LABEL_SPACING * 0.5f);
        gpaint.setStrokeCap(Paint.Cap.ROUND);
        gpaint.setStrokeJoin(Paint.Join.ROUND);
        setWillNotDraw(false);
    }

    @Override public boolean hasOverlappingRendering() { return false; }

    void setGlyph(int g) { if (g != glyph) { glyph = g; invalidate(); } }

    void setApp(AppStore.App a, IconCache.Icon ic) {
        app = a;
        icon = ic;
        label = a == null ? "" : a.label;
        rebuild();
        invalidate();
    }

    void setIcon(IconCache.Icon ic) { if (ic != icon) { icon = ic; rebuild(); invalidate(); } }

    void setLabel(String l) { label = l == null ? "" : l; rebuild(); invalidate(); }

    void themeChanged() { rebuild(); invalidate(); }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { rebuild(); }

    private void rebuild() {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        Ui.Theme t = Ui.T;
        Ui.cut(shape, 0, 0, w, h, h * 0.24f, h * 0.06f);
        plate.setColor(Ui.alpha(t.panel, 0.88f));
        line.setColor(Ui.alpha(t.a, 0.35f));
        line.setStrokeWidth(Math.max(1f, Ui.px(1.5f)));
        gpaint.setColor(t.text);
        gpaint.setStrokeWidth(Math.max(1.5f, h * 0.05f));
        gfill.setColor(t.text);
        text.setColor(t.text);
        text.setTextSize(h * 0.27f);
        glass.setColor(Ui.alpha(t.bg0, 0.80f));
        glassEdge.setColor(Ui.alpha(t.a, 0.50f));
        glassEdge.setStrokeWidth(Math.max(1f, Ui.px(1.2f)));
        float avail = w - thumbW(h) - h * 0.55f;
        if (!compact && app != null && avail < h * 0.9f) compact = true;
        shown = label.isEmpty() ? "" : TextUtils.ellipsize(label.toUpperCase(java.util.Locale.ROOT), text, Math.max(10, avail), TextUtils.TruncateAt.END);
    }

    private float thumbW(int h) {
        if (label.isEmpty()) return 0;
        float th = h * 0.56f;
        if (app != null && icon != null && icon.banner) return th * 16f / 9f;
        return th;
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w <= 0) return;
        c.drawPath(shape, plate);
        c.drawPath(shape, line);
        float th = h * 0.56f, ty = (h - th) / 2f;
        if (label.isEmpty()) {
            drawGlyph(c, glyph, w / 2f, h / 2f, h * 0.56f);
            return;
        }
        if (compact && app != null) {
            if (icon != null && icon.bmp != null && icon.banner) {
                float bw = Math.min(w * 0.82f, th * 1.9f), bh = bw * 9f / 16f;
                r.set((w - bw) / 2f, (h - bh) / 2f, (w + bw) / 2f, (h + bh) / 2f);
                c.drawBitmap(icon.bmp, null, r, bmpPaint);
            } else if (icon != null && icon.bmp != null) {
                if (!IconCache.originalMode) drawPlate(c, w / 2f - th * 0.6f, h / 2f - th * 0.6f, th * 1.2f);
                r.set(w / 2f - th / 2f, h / 2f - th / 2f, w / 2f + th / 2f, h / 2f + th / 2f);
                c.drawBitmap(icon.bmp, null, r, bmpPaint);
            } else {
                drawGlyph(c, G_NONE, w / 2f, h / 2f, th);
            }
            return;
        }
        float tx = h * 0.28f;
        float tw = th;
        if (app != null) {
            if (icon != null && icon.bmp != null) {
                tw = icon.banner ? th * 16f / 9f : th;
                r.set(tx, ty, tx + tw, ty + th);
                if (!icon.banner && !IconCache.originalMode) {
                    drawPlate(c, tx - th * 0.08f, ty - th * 0.08f, th * 1.16f);
                    r.inset(th * 0.06f, th * 0.06f);
                }
                c.drawBitmap(icon.bmp, null, r, bmpPaint);
            } else {
                drawGlyph(c, G_NONE, tx + th / 2f, h / 2f, th);
            }
        } else {
            drawGlyph(c, glyph, tx + th / 2f, h / 2f, th * 0.9f);
        }
        float lx = tx + tw + h * 0.22f;
        c.drawText(shown, 0, shown.length(), lx, h / 2f - (text.ascent() + text.descent()) / 2f, text);
    }

    private void drawPlate(Canvas c, float l, float t, float s) {
        Ui.cut(plateShape, l, t, l + s, t + s, s * 0.18f, s * 0.05f);
        c.drawPath(plateShape, glass);
        c.drawPath(plateShape, glassEdge);
    }

    private void drawGlyph(Canvas c, int g, float cx, float cy, float s) {
        Ui.Theme t = Ui.T;
        gp.reset();
        float hs = s / 2f;
        switch (g) {
            case G_GEAR: {
                for (int i = 0; i < 6; i++) {
                    double a = Math.PI / 3 * i;
                    float x = cx + (float) Math.cos(a) * hs * 0.82f, y = cy + (float) Math.sin(a) * hs * 0.82f;
                    if (i == 0) gp.moveTo(x, y); else gp.lineTo(x, y);
                }
                gp.close();
                c.drawPath(gp, gpaint);
                c.drawCircle(cx, cy, hs * 0.30f, gpaint);
                break;
            }
            case G_WIFI:
            case G_OFF: {
                r.set(cx - hs, cy - hs * 0.55f, cx + hs, cy + hs * 1.45f);
                c.drawArc(r, 225, 90, false, gpaint);
                r.inset(hs * 0.32f, hs * 0.32f);
                c.drawArc(r, 225, 90, false, gpaint);
                r.inset(hs * 0.32f, hs * 0.32f);
                c.drawArc(r, 225, 90, false, gpaint);
                c.drawCircle(cx, cy + hs * 0.45f, hs * 0.11f, gfill);
                if (g == G_OFF) {
                    gpaint.setColor(t.a);
                    c.drawLine(cx - hs * 0.8f, cy - hs * 0.7f, cx + hs * 0.8f, cy + hs * 0.7f, gpaint);
                    gpaint.setColor(t.text);
                }
                break;
            }
            case G_ETH: {
                float w = hs * 1.3f, top = cy - hs * 0.55f, bot = cy + hs * 0.6f;
                gp.moveTo(cx - w / 2, bot);
                gp.lineTo(cx - w / 2, top + hs * 0.25f);
                gp.lineTo(cx - w / 4, top + hs * 0.25f);
                gp.lineTo(cx - w / 4, top);
                gp.lineTo(cx + w / 4, top);
                gp.lineTo(cx + w / 4, top + hs * 0.25f);
                gp.lineTo(cx + w / 2, top + hs * 0.25f);
                gp.lineTo(cx + w / 2, bot);
                gp.close();
                c.drawPath(gp, gpaint);
                for (int i = -1; i <= 1; i++) c.drawLine(cx + i * w * 0.2f, bot - hs * 0.15f, cx + i * w * 0.2f, bot - hs * 0.4f, gpaint);
                break;
            }
            case G_INPUT: {
                float w = hs * 1.5f, top = cy - hs * 0.35f, bot = cy + hs * 0.35f;
                gp.moveTo(cx - w / 2, top);
                gp.lineTo(cx + w / 2, top);
                gp.lineTo(cx + w / 2, bot - hs * 0.12f);
                gp.lineTo(cx + w / 2 - hs * 0.2f, bot);
                gp.lineTo(cx - w / 2 + hs * 0.2f, bot);
                gp.lineTo(cx - w / 2, bot - hs * 0.12f);
                gp.close();
                c.drawPath(gp, gpaint);
                c.drawLine(cx - w * 0.3f, cy, cx + w * 0.3f, cy, gpaint);
                break;
            }
            case G_HOME: {
                gp.moveTo(cx - hs * 0.75f, cy - hs * 0.05f);
                gp.lineTo(cx, cy - hs * 0.7f);
                gp.lineTo(cx + hs * 0.75f, cy - hs * 0.05f);
                gp.moveTo(cx - hs * 0.55f, cy - hs * 0.2f);
                gp.lineTo(cx - hs * 0.55f, cy + hs * 0.65f);
                gp.lineTo(cx + hs * 0.55f, cy + hs * 0.65f);
                gp.lineTo(cx + hs * 0.55f, cy - hs * 0.2f);
                c.drawPath(gp, gpaint);
                break;
            }
            case G_PLUS: {
                Ui.cut(gp, cx - hs * 0.8f, cy - hs * 0.8f, cx + hs * 0.8f, cy + hs * 0.8f, hs * 0.3f, hs * 0.08f);
                c.drawPath(gp, gpaint);
                c.drawLine(cx - hs * 0.4f, cy, cx + hs * 0.4f, cy, gpaint);
                c.drawLine(cx, cy - hs * 0.4f, cx, cy + hs * 0.4f, gpaint);
                break;
            }
            default: {
                Ui.cut(gp, cx - hs, cy - hs, cx + hs, cy + hs, hs * 0.4f, hs * 0.1f);
                c.drawPath(gp, line);
                break;
            }
        }
    }
}
