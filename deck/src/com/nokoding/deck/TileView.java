package com.nokoding.deck;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewOutlineProvider;

/** A big angular app card. Draws only when rebound; all motion is View properties. */
final class TileView extends View {
    static final float CUT_BIG = 0.13f, CUT_SMALL = 0.035f;

    AppStore.App app;
    IconCache.Icon icon;
    boolean hiddenTag;
    int index = -1;          // position in the current list
    int vIndex = Integer.MIN_VALUE; // virtual wheel index (unbounded), MIN_VALUE when unused
    float pdx, pdy;          // pointer push displacement (grid)
    Spring focus;            // used by grid tiles
    boolean shadow;

    private final Path card = new Path();
    private final RectF rect = new RectF();
    private final Paint plate = Ui.fill();
    private final Paint line = Ui.stroke(1);
    private final Path plateShape = new Path();
    private final Paint glass = Ui.fill(), glassEdge = Ui.stroke(1);
    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final TextPaint label = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint tag = Ui.text(Ui.MONO_B, 10);
    private final Paint tagBg = Ui.fill();
    private CharSequence shownLabel = "";
    private String idxText = "";
    private int plateTint;

    TileView(Context c) {
        super(c);
        label.setTypeface(Ui.COND_B);
        label.setLetterSpacing(Ui.LABEL_SPACING * 0.5f);
        setOutlineProvider(new ViewOutlineProvider() {
            @SuppressWarnings("deprecation")
            @Override public void getOutline(View v, Outline o) {
                if (getWidth() <= 0) return;
                try {
                    if (Build.VERSION.SDK_INT >= 30) o.setPath(card);
                    else o.setConvexPath(card);
                } catch (Throwable t) {
                    o.setRect(0, 0, getWidth(), getHeight());
                }
                o.setAlpha(shadow ? 0.55f : 0f);
            }
        });
        setWillNotDraw(false);
    }

    void bind(AppStore.App a, IconCache.Icon ic, boolean hidden, int idx) {
        boolean same = a == app && ic == icon && hidden == hiddenTag;
        app = a;
        icon = ic;
        hiddenTag = hidden;
        if (idx != index) {
            index = idx;
            idxText = idx >= 0 && idx < 99 ? String.format(java.util.Locale.ROOT, "%02d", idx + 1) : "";
            same = false;
        }
        if (same) return;
        rebuild();
        invalidate();
    }

    void setIcon(IconCache.Icon ic) {
        if (ic == icon) return;
        icon = ic;
        rebuild();
        invalidate();
    }

    void setShadow(boolean on) {
        if (on == shadow) return;
        shadow = on;
        try { invalidateOutline(); } catch (Throwable ignored) {}
    }

    void themeChanged() { rebuild(); invalidate(); }

    @Override public boolean hasOverlappingRendering() { return false; }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        rebuild();
        try { invalidateOutline(); } catch (Throwable ignored) {}
    }

    private void rebuild() {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        rect.set(0, 0, w, h);
        Ui.cut(card, 0, 0, w, h, h * CUT_BIG, h * CUT_SMALL);
        Ui.Theme t = Ui.T;
        int base = icon != null && !icon.banner && icon.bmp != null ? icon.color : t.b;
        plateTint = Ui.mix(t.panel, base, 0.30f);
        plate.setShader(new LinearGradient(0, 0, w * 0.3f, h, Ui.mix(plateTint, 0xFFFFFFFF, 0.06f),
                Ui.mix(t.panel, 0xFF000000, 0.25f), Shader.TileMode.CLAMP));
        line.setStrokeWidth(Math.max(1f, 1.5f * Ui.u * h / 315f * 1.6f));
        line.setColor(Ui.alpha(t.a, 0.38f));
        label.setTextSize(h * 0.12f);
        // 2.1 icon plate: dark glass with a thin accent edge, so low res icons look intentional
        float ps = h * 0.52f, pl = (w - ps) / 2f, pt = h * 0.09f;
        Ui.cut(plateShape, pl, pt, pl + ps, pt + ps, ps * 0.16f, ps * 0.05f);
        glass.setShader(new LinearGradient(pl, pt, pl, pt + ps, Ui.alpha(Ui.mix(t.bg1, 0xFFFFFFFF, 0.05f), 0.80f),
                Ui.alpha(t.bg0, 0.86f), Shader.TileMode.CLAMP));
        glassEdge.setStrokeWidth(Math.max(1f, h * 0.006f));
        glassEdge.setColor(Ui.alpha(t.a, 0.55f));
        label.setColor(t.text);
        tag.setTextSize(Math.max(9f, h * 0.06f));
        if (app != null) {
            shownLabel = TextUtils.ellipsize(app.label.toUpperCase(java.util.Locale.ROOT), label, w * 0.84f, TextUtils.TruncateAt.END);
        } else {
            shownLabel = "";
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || app == null) return;
        Ui.Theme t = Ui.T;
        boolean banner = icon != null && icon.banner && icon.bmp != null;
        if (banner) {
            c.drawBitmap(icon.bmp, null, rect, bmpPaint);
        } else {
            c.drawPath(card, plate);
            float labelY;
            if (icon != null && icon.bmp != null) {
                float s, top;
                if (IconCache.originalMode) {
                    // ORIGINAL mode: the 2.0 look, a bigger icon straight on the card
                    s = h * 0.44f;
                    top = h * 0.13f;
                } else {
                    c.drawPath(plateShape, glass);
                    c.drawPath(plateShape, glassEdge);
                    s = h * 0.40f;
                    top = h * 0.15f;
                }
                float l = (w - s) / 2f;
                RectF r = tmpRect;
                r.set(l, top, l + s, top + s);
                c.drawBitmap(icon.bmp, null, r, bmpPaint);
                labelY = h * 0.83f;
            } else {
                labelY = h * 0.56f;
            }
            float tw = label.measureText(shownLabel, 0, shownLabel.length());
            c.drawText(shownLabel, 0, shownLabel.length(), (w - tw) / 2f, labelY, label);
            // index readout in the corner, HUD style
            tag.setColor(Ui.alpha(t.dim, 0.8f));
            c.drawText(idxText, h * CUT_BIG + h * 0.03f, h * 0.11f, tag);
        }
        c.drawPath(card, line);
        if (hiddenTag) {
            float th = h * 0.13f, tw = tag.measureText("HIDDEN") + th;
            tagBg.setColor(Ui.alpha(t.bg0, 0.85f));
            c.drawRect(w - tw - h * 0.08f, h - th - h * 0.06f, w - h * 0.08f, h - h * 0.06f, tagBg);
            tag.setColor(t.a);
            c.drawText("HIDDEN", w - tw - h * 0.08f + th / 2f, h - h * 0.06f - th * 0.3f, tag);
        }
    }

    private final RectF tmpRect = new RectF();
}
