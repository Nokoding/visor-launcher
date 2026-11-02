package com.nokoding.deck;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/**
 * 2.7: the front layer of the screensaver: the dim, and the clock block in the HUD style (a big
 * time, the date under it, an optional second clock line and an optional stats line, between four
 * corner brackets). The block moves to a new place every 60 seconds (BurnIn). The time is drawn from
 * a char array and the paints are made once, so a frame allocates nothing.
 */
final class DreamHud extends View {
    private static final int MARGIN = 72;
    // what to show
    boolean showClock = true, solidBackground, glide = true;
    int dimPct;
    boolean is24, seconds;
    String zoneId = "";
    String stats = "";
    private String date = "";
    private final char[] time = new char[ClockFmt.MAX_CHARS], zone = new char[ClockFmt.ZONE_LINE_CHARS];
    private int timeLen, zoneLen;
    // when it started
    private long startUptime;
    private long startSlot;
    // paints and measures
    private final Paint timeP, dateP, smallP, statsP, rule = Ui.stroke(2), bracket = Ui.stroke(3), dimP = Ui.fill();
    private final double[] frac = new double[2];
    private final int[] pos = new int[2];
    private float blockW, blockH, timeAdv;
    private boolean sized;

    DreamHud(Context c) {
        super(c);
        setWillNotDraw(false);
        timeP = Ui.text(Ui.MONO, Ui.px(190));
        dateP = Ui.text(Ui.HEAD_M, Ui.px(40));
        dateP.setLetterSpacing(Ui.LABEL_SPACING);
        smallP = Ui.text(Ui.MONO, Ui.px(44));
        statsP = Ui.text(Ui.MONO, Ui.px(26));
        bracket.setStrokeCap(Paint.Cap.SQUARE);
        themeChanged();
    }

    @Override public boolean hasOverlappingRendering() { return false; }

    void themeChanged() {
        Ui.Theme t = Ui.T;
        timeP.setColor(t.text);
        dateP.setColor(t.a);
        smallP.setColor(t.dim);
        statsP.setColor(t.dim);
        rule.setColor(Ui.alpha(t.a, 0.7f));
        rule.setStrokeWidth(Math.max(1f, Ui.px(2)));
        bracket.setColor(Ui.alpha(t.a, 0.8f));
        bracket.setStrokeWidth(Math.max(2f, Ui.px(3)));
        invalidate();
    }

    void begin(long uptimeMs, long wallMs) {
        startUptime = uptimeMs;
        startSlot = BurnIn.startSlot(wallMs);
    }

    /** New time text. Call once a second (seconds on) or a minute. date = the date line, already formatted. */
    void setTime(long wallMs, long offsetMs, String dateLine) {
        timeLen = ClockFmt.format(time, ClockFmt.hour(wallMs, offsetMs), ClockFmt.minute(wallMs, offsetMs), ClockFmt.second(wallMs, offsetMs), is24, seconds);
        zoneLen = ClockFmt.zoneLine(zone, zoneId, wallMs, is24, seconds);
        date = dateLine == null ? "" : dateLine;
        measure();
        invalidate();
    }

    void setStats(String s) {
        String n = s == null ? "" : s;
        if (n.equals(stats)) return;
        stats = n;
        measure();
        invalidate();
    }

    private void measure() {
        if (!showClock) { blockW = 0; blockH = 0; return; }
        timeAdv = timeP.measureText("0");
        float w = timeLen * timeAdv;
        w = Math.max(w, dateP.measureText(date));
        if (zoneLen > 0) w = Math.max(w, smallP.measureText(zone, 0, zoneLen));
        if (!stats.isEmpty()) w = Math.max(w, statsP.measureText(stats));
        float h = Ui.px(190) * 0.80f + Ui.px(30) + Ui.px(40) + Ui.px(18);
        if (zoneLen > 0) h += Ui.px(44) + Ui.px(18);
        if (!stats.isEmpty()) h += Ui.px(26) + Ui.px(22);
        blockW = w + Ui.px(60);
        blockH = h + Ui.px(40);
        sized = true;
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { measure(); }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        if (solidBackground) c.drawColor(Ui.T.bg0);
        else if (dimPct > 0) {
            dimP.setColor(((Math.round(Math.min(95, dimPct) * 2.55f)) << 24));
            c.drawRect(0, 0, w, h, dimP);
        }
        if (!showClock || !sized || timeLen == 0) return;
        long elapsed = android.os.SystemClock.uptimeMillis() - startUptime;
        BurnIn.at(startSlot, elapsed, glide, frac);
        BurnIn.place(frac[0], frac[1], w, h, Math.round(blockW), Math.round(blockH), Math.round(Ui.px(MARGIN)), pos);
        float x = pos[0] + Ui.px(30), y = pos[1] + Ui.px(20) + Ui.px(190) * 0.78f;
        c.drawText(time, 0, timeLen, x, y, timeP);
        y += Ui.px(30);
        c.drawLine(x, y - Ui.px(8), x + blockW - Ui.px(60), y - Ui.px(8), rule);
        y += Ui.px(40);
        c.drawText(date, x, y, dateP);
        if (zoneLen > 0) { y += Ui.px(18) + Ui.px(44); c.drawText(zone, 0, zoneLen, x, y, smallP); }
        if (!stats.isEmpty()) { y += Ui.px(22) + Ui.px(26); c.drawText(stats, x, y, statsP); }
        // corner brackets
        float l = pos[0], t = pos[1], r = pos[0] + blockW, b = pos[1] + blockH, k = Ui.px(34);
        c.drawLine(l, t, l + k, t, bracket); c.drawLine(l, t, l, t + k, bracket);
        c.drawLine(r, t, r - k, t, bracket); c.drawLine(r, t, r, t + k, bracket);
        c.drawLine(l, b, l + k, b, bracket); c.drawLine(l, b, l, b - k, bracket);
        c.drawLine(r, b, r - k, b, bracket); c.drawLine(r, b, r, b - k, bracket);
    }
}
