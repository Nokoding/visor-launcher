package com.nokoding.deck;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.net.TrafficStats;
import android.os.Environment;
import android.os.Process;
import android.os.StatFs;
import android.os.SystemClock;
import android.view.View;

import java.io.RandomAccessFile;

/**
 * Telemetry strip under the top bar: network down and up, RAM, storage, Deck FPS and
 * CPU. Real values only; CPU is hidden when nothing is readable. Sampled at 1 Hz by
 * the activity while Deck is visible. The sample and draw paths allocate nothing.
 */
final class StatsView extends View {
    static final int HIST = 40;

    private static final class Txt {
        final char[] c = new char[40];
        int n;
    }

    boolean enabled = true, effects = true;
    private int apps;
    private boolean gridMode;

    private final float[] dn = new float[HIST], up = new float[HIST], fps = new float[HIST];
    private int head;
    private long lastRx = -2, lastTx = -2, lastT, lastCpuMs = -1, lastSysTotal = -1, lastSysIdle;
    private float ramFrac, stoFrac, cpuFrac = -1f, ramShown, stoShown, cpuShown;
    private boolean netOk = true, sysCpu;
    private final int cores = Math.max(1, Runtime.getRuntime().availableProcessors());

    private final Txt tApps = new Txt(), tLayout = new Txt(), tDn = new Txt(), tUp = new Txt(), tRam = new Txt(),
            tRamSub = new Txt(), tSto = new Txt(), tStoSub = new Txt(), tFps = new Txt(), tCpu = new Txt();

    private final ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
    private StatFs statFs;
    private RandomAccessFile procStat;
    private final byte[] statBuf = new byte[512];

    private final Paint boxFill = Ui.fill(), boxLine = Ui.stroke(1), spark = Ui.stroke(1), segOn = Ui.stroke(1),
            segOff = Ui.stroke(1), segBad = Ui.stroke(1);
    private final Paint lab = Ui.text(Ui.MONO_B, 16), val = Ui.text(Ui.MONO_B, 17), dim = Ui.text(Ui.MONO, 15);
    private final Path box = new Path();
    private final RectF arc = new RectF();
    private final float[] pts = new float[4 * HIST];

    StatsView(Context c) {
        super(c);
        setWillNotDraw(false);
        lab.setTextSize(Ui.px(16));
        val.setTextSize(Ui.px(17));
        dim.setTextSize(Ui.px(15));
        try { statFs = new StatFs(Environment.getDataDirectory().getPath()); } catch (Throwable ignored) {}
        try {
            procStat = new RandomAccessFile("/proc/stat", "r");
            sysCpu = readProcStat();
        } catch (Throwable t) {
            sysCpu = false;
        }
        if (!sysCpu) closeProc();
        themeChanged();
    }

    @Override public boolean hasOverlappingRendering() { return false; }

    void themeChanged() {
        Ui.Theme t = Ui.T;
        boxFill.setColor(Ui.alpha(t.panel, 0.55f));
        boxLine.setColor(Ui.alpha(t.a, 0.30f));
        boxLine.setStrokeWidth(Math.max(1f, Ui.px(1.2f)));
        spark.setColor(t.a);
        spark.setStrokeWidth(Math.max(1f, Ui.px(1.8f)));
        float sw = Ui.px(5);
        segOn.setColor(t.a); segOn.setStrokeWidth(sw);
        segOff.setColor(Ui.alpha(t.dim, 0.25f)); segOff.setStrokeWidth(sw);
        segBad.setColor(Ui.DANGER); segBad.setStrokeWidth(sw);
        lab.setColor(t.a);
        val.setColor(t.text);
        dim.setColor(t.dim);
        invalidate();
    }

    void setTags(int appCount, boolean grid) {
        apps = appCount;
        gridMode = grid;
        tApps.n = Fmt.num(tApps.c, Fmt.str(tApps.c, 0, "APPS "), appCount);
        tLayout.n = Fmt.str(tLayout.c, 0, grid ? "LAYOUT GRID" : "LAYOUT WHEEL");
        invalidate();
    }

    void detach() { closeProc(); }

    private void closeProc() {
        try { if (procStat != null) procStat.close(); } catch (Throwable ignored) {}
        procStat = null;
    }

    private boolean readProcStat() {
        try {
            procStat.seek(0);
            int n = procStat.read(statBuf, 0, statBuf.length);
            if (n < 8 || statBuf[0] != 'c' || statBuf[1] != 'p' || statBuf[2] != 'u') return false;
            long total = 0, idle = 0;
            int field = 0, i = 3;
            while (i < n && statBuf[i] != '\n') {
                while (i < n && statBuf[i] == ' ') i++;
                long v = 0;
                boolean any = false;
                while (i < n && statBuf[i] >= '0' && statBuf[i] <= '9') { v = v * 10 + (statBuf[i] - '0'); i++; any = true; }
                if (!any) break;
                total += v;
                if (field == 3 || field == 4) idle += v;
                field++;
            }
            if (field < 4) return false;
            if (lastSysTotal >= 0 && total > lastSysTotal) {
                cpuFrac = 1f - (idle - lastSysIdle) / (float) (total - lastSysTotal);
            }
            lastSysTotal = total;
            lastSysIdle = idle;
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Called once a second while visible. */
    void sample(ActivityManager am, FrameStats frames) {
        if (!enabled) return;
        long now = SystemClock.elapsedRealtime();
        long dtMs = lastT == 0 ? 1000 : Math.max(1, now - lastT);
        lastT = now;
        int i = head;
        head = (head + 1) % HIST;

        long rx = TrafficStats.getTotalRxBytes(), tx = TrafficStats.getTotalTxBytes();
        netOk = rx != TrafficStats.UNSUPPORTED && tx != TrafficStats.UNSUPPORTED;
        long dr = lastRx >= 0 && rx >= lastRx ? (rx - lastRx) * 1000 / dtMs : 0;
        long dtt = lastTx >= 0 && tx >= lastTx ? (tx - lastTx) * 1000 / dtMs : 0;
        lastRx = rx;
        lastTx = tx;
        dn[i] = dr;
        up[i] = dtt;
        tDn.n = Fmt.size(tDn.c, 0, dr);
        tUp.n = Fmt.size(tUp.c, 0, dtt);

        try {
            if (am != null) {
                am.getMemoryInfo(mi);
                long used = mi.totalMem - mi.availMem;
                ramFrac = mi.totalMem > 0 ? used / (float) mi.totalMem : 0f;
                int o = Fmt.num(tRam.c, Fmt.str(tRam.c, 0, "RAM "), Math.round(ramFrac * 100));
                tRam.n = Fmt.put(tRam.c, o, '%');
                o = Fmt.size(tRamSub.c, 0, used);
                o = Fmt.put(tRamSub.c, o, '/');
                tRamSub.n = Fmt.size(tRamSub.c, o, mi.totalMem);
            }
        } catch (Throwable ignored) {}

        try {
            if (statFs != null) {
                statFs.restat(Environment.getDataDirectory().getPath());
                long total = statFs.getTotalBytes(), avail = statFs.getAvailableBytes();
                stoFrac = total > 0 ? (total - avail) / (float) total : 0f;
                int o = Fmt.num(tSto.c, Fmt.str(tSto.c, 0, "STO "), Math.round(stoFrac * 100));
                tSto.n = Fmt.put(tSto.c, o, '%');
                o = Fmt.size(tStoSub.c, 0, total - avail);
                o = Fmt.put(tStoSub.c, o, '/');
                tStoSub.n = Fmt.size(tStoSub.c, o, total);
            }
        } catch (Throwable ignored) {}

        float f = frames.takeFps();
        fps[i] = Math.max(0f, f);
        if (f < 0) tFps.n = Fmt.str(tFps.c, 0, "FPS IDLE");
        else tFps.n = Fmt.num(tFps.c, Fmt.str(tFps.c, 0, "FPS "), Math.round(Math.min(f, 99f)));

        if (sysCpu) {
            sysCpu = readProcStat();
            if (!sysCpu) closeProc();
        }
        if (!sysCpu) {
            long cpu = Process.getElapsedCpuTime();
            if (lastCpuMs >= 0 && cpu >= lastCpuMs) cpuFrac = Math.min(1f, (cpu - lastCpuMs) / (float) (dtMs * cores));
            lastCpuMs = cpu;
        }
        if (cpuFrac >= 0) {
            int o = Fmt.str(tCpu.c, 0, sysCpu ? "CPU " : "VISOR CPU ");
            o = Fmt.num(tCpu.c, o, Math.round(cpuFrac * 100));
            tCpu.n = Fmt.put(tCpu.c, o, '%');
        }
        if (!effects) { ramShown = ramFrac; stoShown = stoFrac; cpuShown = cpuFrac; }
        invalidate();
    }

    private float boxAt(Canvas c, float x, float w) {
        float h = getHeight();
        Ui.cut(box, x, 0, x + w, h, Ui.px(12), Ui.px(4));
        c.drawPath(box, boxFill);
        c.drawPath(box, boxLine);
        return x;
    }

    private void text(Canvas c, Txt t, float x, float y, Paint p) { c.drawText(t.c, 0, t.n, x, y, p); }

    private float ring(Canvas c, float cx, float cy, float frac, boolean dangerHigh) {
        float r = Ui.px(15);
        arc.set(cx - r, cy - r, cx + r, cy + r);
        int segs = 10, on = Math.round(Math.max(0f, Math.min(1f, frac)) * segs);
        Paint p = dangerHigh && frac > 0.9f ? segBad : segOn;
        for (int k = 0; k < segs; k++) c.drawArc(arc, 135 + k * 27f, 21f, false, k < on ? p : segOff);
        return r;
    }

    private void sparkline(Canvas c, float[] h, float x, float y, float w, float hh, float floorMax) {
        float max = floorMax;
        for (float v : h) if (v > max) max = v;
        int n = 0;
        float step = w / (HIST - 1);
        for (int k = 0; k < HIST - 1; k++) {
            float a = h[(head + k) % HIST] / max, b = h[(head + k + 1) % HIST] / max;
            pts[n++] = x + k * step; pts[n++] = y + hh - a * hh;
            pts[n++] = x + (k + 1) * step; pts[n++] = y + hh - b * hh;
        }
        c.drawLines(pts, 0, n, spark);
    }

    private boolean ease() {
        float d1 = ramFrac - ramShown, d2 = stoFrac - stoShown, d3 = cpuFrac - cpuShown;
        ramShown += d1 * 0.25f;
        stoShown += d2 * 0.25f;
        cpuShown += d3 * 0.25f;
        return Math.abs(d1) + Math.abs(d2) + Math.abs(d3) > 0.004f;
    }

    @Override
    protected void onDraw(Canvas c) {
        float h = getHeight(), w = getWidth(), base = h / 2f - (val.ascent() + val.descent()) / 2f, cy = h / 2f;
        if (!enabled) {
            text(c, tApps, 0, base, lab);
            text(c, tLayout, w - dim.measureText(tLayout.c, 0, tLayout.n), base, dim);
            return;
        }
        boolean more = effects && ease();
        if (!effects) { ramShown = ramFrac; stoShown = stoFrac; cpuShown = cpuFrac; }
        float pad = Ui.px(14), x;

        x = boxAt(c, 0, Ui.px(140));
        text(c, tApps, x + pad, base, lab);

        x = boxAt(c, Ui.px(152), Ui.px(450));
        if (netOk) {
            float sx = x + pad;
            c.drawText("DN", sx, base, lab);
            sparkline(c, dn, sx + Ui.px(30), cy - Ui.px(12), Ui.px(100), Ui.px(24), 32 * 1024);
            text(c, tDn, sx + Ui.px(138), base, val);
            sx += Ui.px(220);
            c.drawText("UP", sx, base, lab);
            sparkline(c, up, sx + Ui.px(30), cy - Ui.px(12), Ui.px(100), Ui.px(24), 32 * 1024);
            text(c, tUp, sx + Ui.px(138), base, val);
        } else {
            c.drawText("NET N/A", x + pad, base, dim);
        }

        x = boxAt(c, Ui.px(614), Ui.px(250));
        float r = ring(c, x + pad + Ui.px(15), cy, ramShown, true);
        text(c, tRam, x + pad + 2 * r + Ui.px(10), base, val);
        text(c, tRamSub, x + pad + 2 * r + Ui.px(108), base, dim);

        x = boxAt(c, Ui.px(876), Ui.px(250));
        ring(c, x + pad + Ui.px(15), cy, stoShown, true);
        text(c, tSto, x + pad + 2 * r + Ui.px(10), base, val);
        text(c, tStoSub, x + pad + 2 * r + Ui.px(108), base, dim);

        x = boxAt(c, Ui.px(1138), Ui.px(230));
        sparkline(c, fps, x + pad, cy - Ui.px(12), Ui.px(90), Ui.px(24), 60f);
        text(c, tFps, x + pad + Ui.px(100), base, val);

        if (cpuFrac >= 0) {
            x = boxAt(c, Ui.px(1380), Ui.px(200));
            ring(c, x + pad + Ui.px(15), cy, cpuShown, false);
            text(c, tCpu, x + pad + 2 * r + Ui.px(10), base, val);
        }
        text(c, tLayout, w - dim.measureText(tLayout.c, 0, tLayout.n), base, dim);
        if (more) postInvalidateOnAnimation();
    }
}
