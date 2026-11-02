package com.nokoding.deck;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * Wide procedural cyberpunk panorama that wraps horizontally forever. Two skyline
 * layers are painted once into small half resolution bitmaps (about 3 MB at 1080p);
 * sky, haze and the grid floor are vector draws. Each frame only changes offsets.
 */
final class Panorama extends View {
    // periods and heights in design px
    static final float FAR_P = 2400f, FAR_H = 380f, FAR_RES = 0.5f;
    static final float MID_P = 2880f, MID_H = 500f, MID_RES = 0.6f;
    static final float FLOOR_SP = 150f;
    // pan multipliers: far moves least, the floor (near) most
    static final float FAR_M = 0.30f, MID_M = 0.62f, NEAR_M = 1.25f;
    static final float FAR_Y = 0.35f, MID_Y = 0.6f, NEAR_Y = 1.0f;
    static final float HORIZON = 0.60f;

    private Bitmap far, mid, scan;
    private final Paint sky = Ui.fill(), haze1 = Ui.fill(), haze2 = Ui.fill(), glow = Ui.fill(), floor = Ui.fill();
    private final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint grid = Ui.stroke(1), horizonLine = Ui.stroke(1), scanPaint = Ui.fill();
    private final RectF dst = new RectF();
    private final float[] vlines = new float[4 * 64];
    private final float[] hlines = new float[4 * 12];
    private double panX, panY;
    boolean scanlines = true;
    /** 2.7: true draws a plain dark background and keeps no skyline bitmaps (wallpaper OFF, or a wallpaper pack is on top). */
    private boolean plain;

    Panorama(Context c) {
        super(c);
        setWillNotDraw(false);
    }

    @Override public boolean hasOverlappingRendering() { return false; }

    /** Camera position in real px (already scaled). */
    void setPan(double x, double y) {
        if (Math.abs(x - panX) < 0.05 && Math.abs(y - panY) < 0.05) return;
        panX = x;
        panY = y;
        invalidate();
    }

    void themeChanged() { rebuild(); invalidate(); }

    /** 2.7: switch between the skyline and the plain dark background. The skyline bitmaps are freed in plain mode. */
    void setPlain(boolean p) {
        if (p == plain) return;
        plain = p;
        if (p) { far = null; mid = null; }
        else rebuild();
        invalidate();
    }

    boolean isPlain() { return plain; }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { rebuild(); }

    private void rebuild() {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        Ui.Theme t = Ui.T;
        float u = Ui.u, hz = h * HORIZON;
        sky.setShader(new LinearGradient(0, 0, 0, hz, new int[]{t.bg0, t.bg1, Ui.mix(t.bg1, t.b, 0.16f)},
                new float[]{0f, 0.62f, 1f}, Shader.TileMode.CLAMP));
        floor.setShader(new LinearGradient(0, 0, 0, h - hz, Ui.mix(t.bg1, t.bg0, 0.4f), t.bg0, Shader.TileMode.CLAMP));
        glow.setShader(new LinearGradient(0, -Ui.px(110), 0, Ui.px(10), 0, Ui.alpha(t.a, 0.20f), Shader.TileMode.CLAMP));
        float r1 = Ui.px(760), r2 = Ui.px(820);
        haze1.setShader(new RadialGradient(0, 0, r1, new int[]{Ui.alpha(t.a, 0.20f), Ui.alpha(t.a, 0.06f), 0},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        haze2.setShader(new RadialGradient(0, 0, r2, new int[]{Ui.alpha(t.b, 0.22f), Ui.alpha(t.b, 0.06f), 0},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        grid.setColor(Ui.alpha(t.a, 0.10f));
        grid.setStrokeWidth(Math.max(1f, Ui.px(1.2f)));
        horizonLine.setColor(Ui.alpha(t.a, 0.45f));
        horizonLine.setStrokeWidth(Math.max(1f, Ui.px(1.6f)));

        int sh = Math.max(3, Math.round(Ui.px(4)));
        scan = Bitmap.createBitmap(1, sh, Bitmap.Config.ARGB_8888);
        scan.eraseColor(0);
        scan.setPixel(0, 0, Ui.alpha(t.text, 0.03f));
        scan.setPixel(0, sh - 1, 0x22000000);
        scanPaint.setShader(new BitmapShader(scan, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));

        // Old bitmaps are left to the GC: a live display list may still point at them.
        if (plain) { far = null; mid = null; return; }
        far = paintFar(u);
        mid = paintMid(u);
    }

    private static int seed;

    private static float rnd() {
        seed = seed * 1103515245 + 12345;
        return ((seed >>> 8) & 0xFFFF) / 65535f;
    }

    private Bitmap paintFar(float u) {
        float s = u * FAR_RES;
        Bitmap b;
        try {
            b = Bitmap.createBitmap(Math.max(8, Math.round(FAR_P * s)), Math.max(8, Math.round(FAR_H * s)), Bitmap.Config.ARGB_8888);
        } catch (Throwable oom) {
            return null;
        }
        Canvas c = new Canvas(b);
        c.scale(s, s);
        Ui.Theme t = Ui.T;
        int body = Ui.mix(Ui.mix(t.bg1, t.b, 0.22f), t.bg0, 0.25f);
        Paint p = Ui.fill(), win = Ui.fill(), ant = Ui.stroke(2f);
        p.setColor(body);
        win.setColor(Ui.alpha(t.a, 0.30f));
        ant.setColor(Ui.alpha(body, 1f));
        float ground = FAR_H - 60f;
        c.drawRect(0, ground, FAR_P, FAR_H, p);
        seed = 7;
        float x = 0;
        while (x < FAR_P) {
            float bw = 40 + rnd() * 90, bh = 40 + rnd() * rnd() * 190;
            for (int k = -1; k <= 1; k++) {
                float ox = x + k * FAR_P;
                c.drawRect(ox, ground - bh, ox + bw, FAR_H, p);
                if (bh > 150) c.drawLine(ox + bw * 0.5f, ground - bh, ox + bw * 0.5f, ground - bh - 30, ant);
            }
            int lit = (int) (rnd() * 4);
            for (int i = 0; i < lit; i++) {
                float wx = x + 6 + rnd() * (bw - 12), wy = ground - bh + 10 + rnd() * (bh - 20);
                for (int k = -1; k <= 1; k++) c.drawRect(wx + k * FAR_P, wy, wx + k * FAR_P + 4, wy + 3, win);
            }
            x += bw + rnd() * 14;
        }
        return b;
    }

    private Bitmap paintMid(float u) {
        float s = u * MID_RES;
        Bitmap b;
        try {
            b = Bitmap.createBitmap(Math.max(8, Math.round(MID_P * s)), Math.max(8, Math.round(MID_H * s)), Bitmap.Config.ARGB_8888);
        } catch (Throwable oom) {
            return null;
        }
        Canvas c = new Canvas(b);
        c.scale(s, s);
        Ui.Theme t = Ui.T;
        int body = Ui.mix(t.bg0, t.bg1, 0.55f);
        Paint p = Ui.fill(), rim = Ui.stroke(2f), win = Ui.fill(), neon = Ui.fill(), neonGlow = Ui.fill(), board = Ui.stroke(2f);
        p.setColor(body);
        rim.setColor(Ui.alpha(t.b, 0.45f));
        win.setColor(Ui.alpha(t.text, 0.14f));
        board.setColor(Ui.alpha(t.b, 0.75f));
        float ground = MID_H - 60f;
        c.drawRect(0, ground, MID_P, MID_H, p);
        seed = 4242;
        float x = 20;
        int n = 0;
        while (x < MID_P - 10) {
            float bw = 90 + rnd() * 150, bh = 80 + rnd() * 190;
            boolean signs = rnd() < 0.55f, board1 = rnd() < 0.18f;
            int col = rnd() < 0.5f ? t.a : t.b;
            for (int k = -1; k <= 1; k++) {
                float ox = x + k * MID_P, top = ground - bh;
                c.drawRect(ox, top, ox + bw, MID_H, p);
                c.drawLine(ox, top, ox + bw, top, rim);
                // window grid, sparse
                for (float wy = top + 18; wy < ground - 14; wy += 22) {
                    for (float wx = ox + 10; wx < ox + bw - 14; wx += 18) {
                        if (((int) (wx * 7 + wy * 13 + n * 31) & 7) == 0) c.drawRect(wx, wy, wx + 8, wy + 5, win);
                    }
                }
                if (signs) {
                    float sx = ox + bw * 0.18f, sy = top + 30, shh = 70 + (n % 4) * 25;
                    neonGlow.setColor(Ui.alpha(col, 0.16f));
                    neon.setColor(Ui.alpha(col, 0.92f));
                    c.drawRect(sx - 10, sy - 10, sx + 22, sy + shh + 10, neonGlow);
                    c.drawRect(sx, sy, sx + 12, sy + shh, neon);
                    neon.setColor(Ui.alpha(Ui.T.bg0, 0.85f));
                    for (float gy = sy + 8; gy < sy + shh - 8; gy += 16) c.drawRect(sx + 3, gy, sx + 9, gy + 7, neon);
                }
                if (board1 && bw > 150) {
                    float bx = ox + bw * 0.25f, by = top - 70, bww = bw * 0.6f;
                    c.drawRect(bx, by, bx + bww, by + 50, board);
                    neon.setColor(Ui.alpha(t.b, 0.55f));
                    c.drawRect(bx + 10, by + 12, bx + bww * 0.6f, by + 20, neon);
                    c.drawRect(bx + 10, by + 28, bx + bww * 0.4f, by + 36, neon);
                    c.drawLine(bx + bww * 0.5f, by + 50, bx + bww * 0.5f, top, board);
                }
            }
            x += bw + 30 + rnd() * 60;
            n++;
        }
        return b;
    }

    private void tile(Canvas c, Bitmap b, double off, float period, float top, float height, int w) {
        if (b == null) return;
        float x0 = (float) PanModel.wrap(off, period) - period;
        for (float x = x0; x < w; x += period) {
            if (x + period <= 0) continue;
            dst.set(x, top, x + period, top + height);
            c.drawBitmap(b, null, dst, bmp);
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w <= 0) return;
        if (plain) { c.drawColor(Ui.T.bg0); return; }
        float u = Ui.u, hz = h * HORIZON;
        float yFar = (float) (panY * FAR_Y), yMid = (float) (panY * MID_Y), yNear = (float) (panY * NEAR_Y);

        c.drawRect(0, 0, w, h, sky);

        // haze in the far plane, wrapped like the skyline
        float pf = FAR_P * u;
        float ox = (float) PanModel.wrap(panX * FAR_M, pf);
        for (int k = -1; k <= 1; k++) {
            float bx = ox + k * pf;
            c.save(); c.translate(bx + pf * 0.15f, hz * 0.35f + yFar); c.drawCircle(0, 0, Ui.px(760), haze1); c.restore();
            c.save(); c.translate(bx + pf * 0.62f, hz * 0.85f + yFar); c.drawCircle(0, 0, Ui.px(820), haze2); c.restore();
        }

        // far and mid skylines
        tile(c, far, panX * FAR_M, pf, hz + yFar - (FAR_H - 60f) * u, FAR_H * u, w);
        c.save(); c.translate(0, hz + yMid); c.drawRect(0, -Ui.px(110), w, Ui.px(10), glow); c.restore();
        tile(c, mid, panX * MID_M, MID_P * u, hz + yMid - (MID_H - 60f) * u, MID_H * u, w);

        // grid floor: the near layer
        float hn = hz + yNear;
        c.save(); c.translate(0, hn); c.drawRect(0, 0, w, h - hn + 1, floor); c.restore();
        float sp = FLOOR_SP * u, off = (float) PanModel.wrap(panX * NEAR_M, sp), vx = w * 0.5f;
        int n = 0;
        for (float bx = -w + off - sp; bx < 2f * w && n + 4 <= vlines.length; bx += sp) {
            vlines[n++] = vx + (bx - vx) * 0.06f;
            vlines[n++] = hn;
            vlines[n++] = bx;
            vlines[n++] = h;
        }
        c.drawLines(vlines, 0, n, grid);
        n = 0;
        for (int k = 1; k <= 10 && n + 4 <= hlines.length; k++) {
            float f = k / 10f, y = hn + (h - hn) * f * f;
            hlines[n++] = 0; hlines[n++] = y; hlines[n++] = w; hlines[n++] = y;
        }
        c.drawLines(hlines, 0, n, grid);
        c.drawLine(0, hn, w, hn, horizonLine);

        if (scanlines) c.drawRect(0, 0, w, h, scanPaint);
    }
}
