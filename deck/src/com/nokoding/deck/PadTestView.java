package com.nokoding.deck;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.util.Locale;

/**
 * Setup > PAD > GAMEPAD TEST. Shows every button of the controller and lights each one when
 * it is pressed. FULL: buttons stay lit, all 16 required ones finish the run with confetti and
 * a sound. QUICK: lit only while held. Logic lives in PadTest, this class reads input and draws.
 */
final class PadTestView extends TestScreen {

    static final int C_FULL = 0, C_QUICK = 1, C_RESET = 2, C_SKIP = 3, C_EXIT = 4;
    static final float CX = 960, CY = 620, CM = 50;   // controller center and scale (design px per cm)

    final PadTest.State state = new PadTest.State();
    private final Confetti confetti = new Confetti();
    private int type = PadTest.GENERIC, product, layout = PadTest.GENERIC;
    private String devLine = "NO CONTROLLER YET. PRESS ANY BUTTON ON THE CONTROLLER YOU WANT TO TEST.";
    private String typeLine = "DETECTED: NONE", hint = "";
    private final String[] labels = new String[PadTest.COUNT];
    // button centers (design px, relative to CX, CY) and sizes
    private final float[] bx = new float[PadTest.COUNT], by = new float[PadTest.COUNT];
    private float lsX, lsY, rsX, rsY;
    // live analog values
    private float lx, ly, rx, ry, l2, r2;
    private boolean hatL, hatR, hatU, hatD, axL2, axR2;
    private final String[] unknown = new String[6];
    private int unknownN;
    private long touchUntil;
    private boolean celebrate;
    private final int[] colors = new int[4];

    PadTestView(Context c, Host host, boolean reduce, boolean full, int preferredPad) {
        super(c, host, reduce, false);
        setChips(new String[]{"FULL TEST", "QUICK TEST", "RESET", "SKIP NEXT", "EXIT"}, new float[]{190, 200, 140, 190, 120});
        state.setFull(full);
        markMode();
        setLayout(PadTest.GENERIC, 0);
        InputDevice d = null;
        try { d = preferredPad >= 0 ? InputDevice.getDevice(preferredPad) : null; } catch (Throwable ignored) {}
        if (!isPad(d)) {
            try {
                for (int id : InputDevice.getDeviceIds()) {
                    InputDevice x = InputDevice.getDevice(id);
                    if (isPad(x)) { d = x; break; }
                }
            } catch (Throwable ignored) {}
        }
        if (isPad(d)) bind(d);
    }

    /** A on a focused chip presses the chip and still counts as a pressed button. */
    @Override boolean padKeyCounts() { return true; }

    @Override boolean holdBExits() { return true; }

    @Override void holdAnchor(float[] out) {
        out[0] = CX + bx[PadTest.EAST];
        out[1] = CY + by[PadTest.EAST];
    }

    /** 2.17 rumble check after the buttons: 0 not tried, 1 tried and waiting, 2 felt, 3 not felt. */
    private int rumble;

    @Override String aName() { return labels[PadTest.SOUTH] == null ? "A" : labels[PadTest.SOUTH]; }

    private void markMode() {
        chipOn[C_FULL] = state.full;
        chipOn[C_QUICK] = !state.full;
    }

    private void setLayout(int t, int prod) {
        type = t;
        product = prod;
        layout = PadTest.layout(t);
        for (int i = 0; i < PadTest.COUNT; i++) labels[i] = PadTest.label(t, prod, i);
        hint = "EXIT: BACK ON THE REMOTE  //  HOLD " + labels[PadTest.EAST] + " FOR 1.5 S  //  OR " + labels[PadTest.START] + " + " + labels[PadTest.SELECT]
                + " FOR 2 S  //  CLICK EXIT  //  REMOTE: LEFT FULL, RIGHT QUICK, OK RESET";
        // the button positions of the flat drawing (PadOutline.positions)
        float[] pos = PadOutline.positions(layout == PadTest.XBOX ? PadTest.XBOX : PadTest.PLAYSTATION);
        for (int i = 0; i < PadTest.COUNT; i++) put(i, pos[i * 2], pos[i * 2 + 1]);
        lsX = pos[PadTest.L3 * 2]; lsY = pos[PadTest.L3 * 2 + 1];
        rsX = pos[PadTest.R3 * 2]; rsY = pos[PadTest.R3 * 2 + 1];
        if (layout != PadTest.PLAYSTATION && layout != PadTest.XBOX) { put(PadTest.HOME, 0, 40); put(PadTest.TOUCHPAD, 0, 0); }
    }

    private void put(int b, float x, float y) { bx[b] = x; by[b] = y; }

    @Override
    void bind(InputDevice d) {
        if (d == null) return;
        deviceId = d.getId();
        int v = d.getVendorId(), p = d.getProductId();
        String name = d.getName() == null ? "" : d.getName();
        int t = PadTest.detect(v, p, name);
        state.setType(t);
        setLayout(t, p);
        String model = PadTest.model(v, p);
        typeLine = "DETECTED: " + PadTest.TYPE_NAMES[t] + (model.isEmpty() ? "" : "  //  " + model);
        devLine = name.toUpperCase(Locale.ROOT) + "  //  VENDOR " + PadTest.hex4(v) + "  PRODUCT " + PadTest.hex4(p);
        unknownN = 0;
        celebrate = false;
        confetti.clear();
        hold.clear();
        startHeld = selectHeld = false;
    }

    private void resetRun() {
        state.reset();
        rumble = 0;
        celebrate = false;
        confetti.clear();
        unknownN = 0;
    }

    private void setMode(boolean full) {
        if (state.full == full) { resetRun(); return; }
        state.setFull(full);
        celebrate = false;
        confetti.clear();
        markMode();
        host.testFx(SoundMap.TOGGLE);
    }

    @Override
    void padKey(KeyEvent e, int code, boolean down, boolean first) {
        if (state.complete() && first) {
            // the run is finished: X tries the rumble, A means I felt it, Y means I did not
            if (code == KeyEvent.KEYCODE_BUTTON_X) { rumble = 1; host.testFx(SoundMap.TEST_COMPLETE); invalidate(); }
            else if (code == KeyEvent.KEYCODE_BUTTON_A && rumble == 1) { rumble = 2; invalidate(); }
            else if (code == KeyEvent.KEYCODE_BUTTON_Y && rumble == 1) { rumble = 3; invalidate(); }
        }
        int b = PadTest.buttonFor(code);
        if (b >= 0 && PadTest.has(type, b)) {
            if (down && !first) return;
            state.set(b, down, PadTest.State.SRC_KEY);
            if (down) afterPress();
        } else if (first) {
            String n;
            try { n = KeyEvent.keyCodeToString(code); } catch (Throwable t) { n = "KEY"; }
            if (n.startsWith("KEYCODE_")) n = n.substring(8);
            n = n + " (" + code + ")";
            for (int i = 0; i < unknownN; i++) if (n.equals(unknown[i])) return;
            if (unknownN == unknown.length) { System.arraycopy(unknown, 1, unknown, 0, unknown.length - 1); unknownN--; }
            unknown[unknownN++] = n;
        }
    }

    private void afterPress() {
        if (state.takeCompletion()) {
            celebrate = true;
            host.testFx(SoundMap.TEST_COMPLETE);
            if (!reduce) {
                Ui.Theme t = Ui.T;
                colors[0] = t.a; colors[1] = t.b; colors[2] = t.text; colors[3] = 0xFFFFE14D;
                confetti.burst(CX, CY + 40, 1f, 4, (int) SystemClock.uptimeMillis());
            }
        }
    }

    @Override
    void otherKey(int code, boolean down, boolean first) {
        if (!first) return;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT: setMode(true); break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: setMode(false); break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                resetRun();
                host.testFx(SoundMap.SELECT);
                break;
            default:
                break;
        }
    }

    private static float axis(MotionEvent e, InputDevice dev, int a) {
        if (dev != null && dev.getMotionRange(a, e.getSource()) == null) return 0f;
        float v = e.getAxisValue(a);
        return Float.isNaN(v) ? 0f : v;
    }

    @Override
    void motion(MotionEvent e) {
        if (!InputMode.has(e.getSource(), InputMode.SRC_JOYSTICK)) return;
        InputDevice dev = e.getDevice();
        if (dev == null) return;
        if (dev.getId() != deviceId) {
            float m = Math.max(Math.abs(e.getAxisValue(MotionEvent.AXIS_X)), Math.abs(e.getAxisValue(MotionEvent.AXIS_Y)));
            if (isPad(dev) && m > 0.6f) bind(dev); else return;
        }
        boolean zr = dev.getMotionRange(MotionEvent.AXIS_Z, e.getSource()) != null;
        lx = axis(e, dev, MotionEvent.AXIS_X);
        ly = axis(e, dev, MotionEvent.AXIS_Y);
        rx = axis(e, dev, zr ? MotionEvent.AXIS_Z : MotionEvent.AXIS_RX);
        ry = axis(e, dev, zr ? MotionEvent.AXIS_RZ : MotionEvent.AXIS_RY);
        l2 = Math.max(axis(e, dev, MotionEvent.AXIS_LTRIGGER), axis(e, dev, MotionEvent.AXIS_BRAKE));
        r2 = Math.max(axis(e, dev, MotionEvent.AXIS_RTRIGGER), axis(e, dev, MotionEvent.AXIS_GAS));
        float hx = axis(e, dev, MotionEvent.AXIS_HAT_X), hy = axis(e, dev, MotionEvent.AXIS_HAT_Y);
        axL2 = analog(PadTest.L2, l2, axL2);
        axR2 = analog(PadTest.R2, r2, axR2);
        hatL = analog(PadTest.LEFT, hx < 0 ? hx : 0f, hatL);
        hatR = analog(PadTest.RIGHT, hx > 0 ? hx : 0f, hatR);
        hatU = analog(PadTest.UP, hy < 0 ? hy : 0f, hatU);
        hatD = analog(PadTest.DOWN, hy > 0 ? hy : 0f, hatD);
        invalidate();
    }

    private boolean analog(int b, float v, boolean was) {
        boolean now = PadTest.analogHeld(v, was);
        if (now != was) {
            state.set(b, now, PadTest.State.SRC_AXIS);
            if (now) afterPress();
        }
        return now;
    }

    @Override
    void chipClicked(int idx) {
        switch (idx) {
            case C_FULL: setMode(true); break;
            case C_QUICK: setMode(false); break;
            case C_RESET: resetRun(); host.testFx(SoundMap.SELECT); break;
            case C_SKIP: {
                int b = state.skipNext();
                if (b >= 0) { host.testFx(SoundMap.TOGGLE); afterPress(); } else host.testFx(SoundMap.ERROR);
                break;
            }
            default: exit(null); break;
        }
    }

    /** A click from the PlayStation touchpad (it is a separate pointer device) counts as the touchpad button. */
    @Override
    void clickElsewhere(InputDevice dev) {
        if (type != PadTest.PLAYSTATION || dev == null) return;
        String n = dev.getName() == null ? "" : dev.getName().toLowerCase(Locale.ROOT);
        if (dev.getVendorId() == PadTest.SONY || n.contains("touchpad")) {
            state.set(PadTest.TOUCHPAD, true, PadTest.State.SRC_POINTER);
            touchUntil = SystemClock.uptimeMillis() + 250;
            afterPress();
        }
    }

    @Override
    void stop() { confetti.clear(); }

    @Override
    boolean animating(long now) {
        return confetti.active() || touchUntil != 0;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    void content(Canvas c, long now, float dt) {
        Ui.Theme t = Ui.T;
        if (touchUntil != 0 && now >= touchUntil) {
            touchUntil = 0;
            state.set(PadTest.TOUCHPAD, false, PadTest.State.SRC_POINTER);
        }
        head.setColor(t.a);
        c.drawText("GAMEPAD TEST", 96, 110, head);
        label.setColor(t.dim);
        c.drawText(state.full ? "FULL TEST: A BUTTON STAYS LIT ONCE PRESSED. PRESS EVERY REQUIRED BUTTON."
                : "QUICK TEST: A BUTTON LIGHTS ONLY WHILE HELD.", 96, 152, label);
        mono.setColor(t.text);
        c.drawText(typeLine, 96, 200, mono);
        mono.setColor(t.dim);
        c.drawText(devLine, 96, 232, mono);

        drawBody(c, t);
        for (int b = 0; b < PadTest.COUNT; b++) if (PadTest.has(type, b)) drawButton(c, t, b);
        drawSidebar(c, t);

        small.setColor(t.dim);
        small.setTextAlign(Paint.Align.CENTER);
        c.drawText(hint, 960, 1046, small);
        small.setTextAlign(Paint.Align.LEFT);

        if (celebrate && state.complete()) drawWin(c, t);
        if (confetti.active()) {
            confetti.update(dt);
            for (int i = 0; i < Confetti.N; i++) {
                float a = confetti.alpha(i);
                if (a <= 0f) continue;
                glow.setColor(Ui.alpha(colors[confetti.color[i]], a));
                glow.setStrokeWidth(7);
                float h = confetti.len[i] * 0.5f, cs = (float) Math.cos(confetti.rot[i]) * h, sn = (float) Math.sin(confetti.rot[i]) * h;
                c.drawLine(confetti.x[i] - cs, confetti.y[i] - sn, confetti.x[i] + cs, confetti.y[i] + sn, glow);
            }
        }
    }

    private final android.graphics.Path bodyPath = new android.graphics.Path();
    private int bodyLayout = -1;
    private void buildBody() {
        int l = layout == PadTest.XBOX ? PadTest.XBOX : PadTest.PLAYSTATION;
        if (bodyLayout == l) return;
        bodyLayout = l;
        bodyPath.reset();
        float[] s = PadOutline.start(l), g = PadOutline.segments(l);
        int n = g.length / 6;
        bodyPath.moveTo(CX + s[0], CY + s[1]);
        for (int i = 0; i < n; i++) bodyPath.cubicTo(CX + g[i * 6], CY + g[i * 6 + 1], CX + g[i * 6 + 2], CY + g[i * 6 + 3], CX + g[i * 6 + 4], CY + g[i * 6 + 5]);
        for (int i = n - 1; i >= 0; i--) {
            float x0 = i == 0 ? s[0] : g[(i - 1) * 6 + 4], y0 = i == 0 ? s[1] : g[(i - 1) * 6 + 5];
            bodyPath.cubicTo(CX - g[i * 6 + 2], CY + g[i * 6 + 3], CX - g[i * 6], CY + g[i * 6 + 1], CX - x0, CY + y0);
        }
        bodyPath.close();
    }

    private void drawBody(Canvas c, Ui.Theme t) {
        buildBody();
        fill.setColor(Ui.alpha(t.panel, 0.92f));
        c.drawPath(bodyPath, fill);
        if (!reduce) {
            glow.setColor(Ui.alpha(t.b, 0.18f));
            glow.setStrokeWidth(10);
            c.drawPath(bodyPath, glow);
        }
        line.setColor(Ui.alpha(t.b, 0.9f));
        c.drawPath(bodyPath, line);
        line.setColor(Ui.alpha(t.dim, 0.5f));
        float[] r = PadOutline.rings(bodyLayout);
        for (int i = 0; i + 2 < r.length; i += 3) c.drawCircle(CX + r[i], CY + r[i + 1], r[i + 2], line);
        float[] sh = PadOutline.shoulders();
        for (int i = 0; i + 4 < sh.length; i += 5) rect5(c, sh, i);
        float[] rc = PadOutline.rects(bodyLayout);
        for (int i = 0; i + 4 < rc.length; i += 5) rect5(c, rc, i);
        float[] ln = PadOutline.lines(bodyLayout);
        for (int i = 0; i + 3 < ln.length; i += 4) c.drawLine(CX + ln[i], CY + ln[i + 1], CX + ln[i + 2], CY + ln[i + 3], line);
    }

    private final android.graphics.RectF rect5 = new android.graphics.RectF();

    private void rect5(Canvas c, float[] a, int i) {
        rect5.set(CX + a[i] - a[i + 2] / 2f, CY + a[i + 1] - a[i + 3] / 2f, CX + a[i] + a[i + 2] / 2f, CY + a[i + 1] + a[i + 3] / 2f);
        c.drawRoundRect(rect5, a[i + 4], a[i + 4], line);
    }

    private boolean lit(int b) { return state.lit(b); }

    private void style(Ui.Theme t, int b, boolean optional) {
        boolean on = lit(b), held = state.held(b);
        fill.setColor(on ? (held ? t.text : Ui.alpha(t.a, 0.9f)) : Ui.alpha(t.bg0, 0.9f));
        line.setColor(on ? t.a : Ui.alpha(optional ? t.dim : t.text, optional ? 0.45f : 0.6f));
        label.setColor(on ? t.bg0 : (optional ? t.dim : t.text));
    }

    private void drawButton(Canvas c, Ui.Theme t, int b) {
        float x = CX + bx[b], y = CY + by[b];
        boolean opt = (PadTest.optional(type) & (1 << b)) != 0;
        style(t, b, opt);
        label.setTextAlign(Paint.Align.CENTER);
        switch (b) {
            case PadTest.SOUTH: case PadTest.EAST: case PadTest.WEST: case PadTest.NORTH: {
                c.drawCircle(x, y, 34, fill);
                int st = layout == PadTest.PLAYSTATION ? InputMode.STYLE_SONY : InputMode.STYLE_GENERIC;
                line.setStrokeWidth(3);
                label.setColor(lit(b) ? t.bg0 : t.text);
                line.setColor(lit(b) ? t.bg0 : Ui.alpha(t.text, 0.8f));
                Glyphs.draw(c, st, b, false, x - 26, y, 26, line, label);
                line.setStrokeWidth(2);
                line.setColor(lit(b) ? t.a : Ui.alpha(t.text, 0.6f));
                c.drawCircle(x, y, 34, line);
                break;
            }
            case PadTest.UP: case PadTest.DOWN: case PadTest.LEFT: case PadTest.RIGHT: {
                boolean v = b == PadTest.UP || b == PadTest.DOWN;
                float w = v ? 24 : 30, h = v ? 30 : 24;
                rr.set(x - w, y - h, x + w, y + h);
                c.drawRect(rr, fill);
                c.drawRect(rr, line);
                arrow(c, x, y, b, lit(b) ? t.bg0 : t.text);
                break;
            }
            case PadTest.L1: case PadTest.R1:
                Ui.cut(path, x - 90, y - 20, x + 90, y + 20, 10, 4);
                c.drawPath(path, fill);
                c.drawPath(path, line);
                c.drawText(labels[b], x, y + 9, label);
                break;
            case PadTest.L2: case PadTest.R2: {
                float v = Ui.clamp(b == PadTest.L2 ? l2 : r2, 0f, 1f);
                Ui.cut(path, x - 90, y - 22, x + 90, y + 22, 10, 4);
                c.drawPath(path, fill);
                if (v > 0.01f && !lit(b)) {
                    fill.setColor(Ui.alpha(t.b, 0.55f));
                    c.drawRect(x - 86, y - 18, x - 86 + 172 * v, y + 18, fill);
                }
                c.drawPath(path, line);
                c.drawText(labels[b], x, y + 9, label);
                break;
            }
            case PadTest.L3: case PadTest.R3: {
                boolean left = b == PadTest.L3;
                float sx = left ? lx : rx, sy = left ? ly : ry;
                c.drawCircle(x, y, 58, fill);
                c.drawCircle(x, y, 58, line);
                line.setColor(Ui.alpha(t.dim, 0.4f));
                c.drawCircle(x, y, 40, line);
                fill.setColor(lit(b) ? t.bg0 : t.a);
                c.drawCircle(x + Ui.clamp(sx, -1f, 1f) * 40, y + Ui.clamp(sy, -1f, 1f) * 40, 14, fill);
                label.setColor(lit(b) ? t.a : t.dim);
                c.drawText(labels[b], x, y - 68, label);
                break;
            }
            case PadTest.START: case PadTest.SELECT:
                rr.set(x - 46, y - 16, x + 46, y + 16);
                c.drawRoundRect(rr, 16, 16, fill);
                c.drawRoundRect(rr, 16, 16, line);
                small.setColor(lit(b) ? t.bg0 : t.text);
                small.setTextAlign(Paint.Align.CENTER);
                c.drawText(labels[b], x, y + 6, small);
                small.setTextAlign(Paint.Align.LEFT);
                break;
            case PadTest.HOME:
                c.drawCircle(x, y, 26, fill);
                c.drawCircle(x, y, 26, line);
                small.setColor(lit(b) ? t.bg0 : t.dim);
                small.setTextAlign(Paint.Align.CENTER);
                c.drawText(labels[b], x, y + 6, small);
                small.setTextAlign(Paint.Align.LEFT);
                break;
            case PadTest.TOUCHPAD:
                Ui.cut(path, x - 135, y - 62, x + 135, y + 62, 14, 6);
                c.drawPath(path, fill);
                c.drawPath(path, line);
                c.drawText(labels[b], x, y + 9, label);
                break;
            default:
                break;
        }
        label.setTextAlign(Paint.Align.LEFT);
    }

    private void arrow(Canvas c, float x, float y, int b, int color) {
        fill.setColor(color);
        path.reset();
        float s = 10;
        switch (b) {
            case PadTest.UP: path.moveTo(x, y - s); path.lineTo(x + s, y + s * 0.6f); path.lineTo(x - s, y + s * 0.6f); break;
            case PadTest.DOWN: path.moveTo(x, y + s); path.lineTo(x + s, y - s * 0.6f); path.lineTo(x - s, y - s * 0.6f); break;
            case PadTest.LEFT: path.moveTo(x - s, y); path.lineTo(x + s * 0.6f, y - s); path.lineTo(x + s * 0.6f, y + s); break;
            default: path.moveTo(x + s, y); path.lineTo(x - s * 0.6f, y - s); path.lineTo(x - s * 0.6f, y + s); break;
        }
        path.close();
        c.drawPath(path, fill);
    }

    private void drawSidebar(Canvas c, Ui.Theme t) {
        float x = 1460, y = 330;
        label.setColor(t.dim);
        c.drawText("REQUIRED BUTTONS", x, y, label);
        big.setColor(state.complete() ? t.a : t.text);
        int k = Fmt.num(buf, 0, state.count());
        k = Fmt.put(buf, k, '/');
        k = Fmt.num(buf, k, state.total());
        text(c, k, x, y + 74, big);
        y += 130;
        label.setColor(t.dim);
        c.drawText("OPTIONAL (NOT COUNTED)", x, y, label);
        y += 36;
        optRow(c, t, PadTest.HOME, x, y);
        if (type == PadTest.PLAYSTATION) { y += 32; optRow(c, t, PadTest.TOUCHPAD, x, y); }
        y += 60;
        label.setColor(t.dim);
        c.drawText("TRIGGERS", x, y, label);
        y += 34;
        trig(c, t, labels[PadTest.L2], l2, x, y);
        y += 32;
        trig(c, t, labels[PadTest.R2], r2, x, y);
        y += 60;
        label.setColor(t.dim);
        c.drawText("STICKS (SHOWN, NOT REQUIRED)", x, y, label);
        y += 34;
        stickRow(c, t, "L", lx, ly, x, y);
        y += 32;
        stickRow(c, t, "R", rx, ry, x, y);

        // left column: keys that are not in the layout
        float lx0 = 96, ly0 = 330;
        label.setColor(t.dim);
        c.drawText("OTHER KEYS SEEN", lx0, ly0, label);
        mono.setColor(unknownN == 0 ? t.dim : t.text);
        if (unknownN == 0) c.drawText("NONE YET", lx0, ly0 + 36, mono);
        for (int i = 0; i < unknownN; i++) c.drawText(unknown[i], lx0, ly0 + 36 + i * 32, mono);
        float hy = 700;
        label.setColor(t.dim);
        c.drawText("STATUS", lx0, hy, label);
        mono.setColor(t.text);
        c.drawText(deviceId < 0 ? "WAITING FOR A CONTROLLER" : (state.complete() ? "ALL BUTTONS WORK" : (state.full ? "PRESS THE DARK BUTTONS" : "HOLD ANY BUTTON")), lx0, hy + 36, mono);
        if (type == PadTest.SWITCH) {
            mono.setColor(t.dim);
            c.drawText("SWITCH: GENERIC LAYOUT", lx0, hy + 68, mono);
        }
        mono.setColor(t.dim);
        c.drawText("DRAWING: " + (layout == PadTest.XBOX ? "XBOX ONE" : "DUALSENSE") + (layout == PadTest.PLAYSTATION || layout == PadTest.XBOX ? "" : " (DEFAULT)"), lx0, hy + 100, mono);
        if (state.complete()) {
            mono.setColor(t.text);
            c.drawText("REPORT", lx0, hy + 150, label);
            c.drawText("BUTTONS " + state.count() + "/" + state.total() + (state.skippedCount() > 0 ? "  SKIPPED " + state.skippedCount() : ""), lx0, hy + 182, mono);
            c.drawText("RUMBLE " + (rumble == 2 ? "FELT" : rumble == 3 ? "NOT FELT" : rumble == 1 ? "WAITING FOR YOUR ANSWER" : "NOT CHECKED"), lx0, hy + 214, mono);
        } else if (!state.full || state.nextWanted() < 0) {
            // nothing to say
        } else if (deviceId >= 0) {
            mono.setColor(t.dim);
            c.drawText("NEXT: " + labels[state.nextWanted()] + "   (SKIP NEXT IF THE BOX NEVER SHOWS IT)", lx0, hy + 132, mono);
        }
    }

    private void optRow(Canvas c, Ui.Theme t, int b, float x, float y) {
        mono.setColor(state.seen(b) ? t.a : t.dim);
        int k = Fmt.str(buf, 0, labels[b]);
        k = Fmt.str(buf, k, state.seen(b) ? "  SEEN" : "  NOT SEEN");
        text(c, k, x, y, mono);
    }

    private void trig(Canvas c, Ui.Theme t, String name, float v, float x, float y) {
        mono.setColor(t.text);
        int k = Fmt.str(buf, 0, name);
        text(c, k, x, y, mono);
        float bx0 = x + 70, w = 230;
        fill.setColor(Ui.alpha(t.dim, 0.25f));
        c.drawRect(bx0, y - 16, bx0 + w, y - 2, fill);
        fill.setColor(t.a);
        c.drawRect(bx0, y - 16, bx0 + w * Ui.clamp(v, 0f, 1f), y - 2, fill);
        k = Fmt.fixed(buf, 0, Ui.clamp(v, 0f, 1f), 2);
        text(c, k, bx0 + w + 12, y, mono);
    }

    private void stickRow(Canvas c, Ui.Theme t, String name, float x0, float y0, float x, float y) {
        mono.setColor(t.text);
        int k = Fmt.str(buf, 0, name);
        k = Fmt.str(buf, k, "  X");
        k = Fmt.fixed(buf, k, x0, 2);
        k = Fmt.str(buf, k, "  Y");
        k = Fmt.fixed(buf, k, y0, 2);
        text(c, k, x, y, mono);
    }

    private void drawWin(Canvas c, Ui.Theme t) {
        Ui.cut(path, 460, 470, 1460, 630, 26, 8);
        fill.setColor(Ui.alpha(t.bg0, 0.9f));
        c.drawPath(path, fill);
        line.setColor(t.a);
        line.setStrokeWidth(3);
        c.drawPath(path, line);
        line.setStrokeWidth(2);
        big.setColor(t.a);
        big.setTextAlign(Paint.Align.CENTER);
        c.drawText(state.skippedCount() > 0 ? "DONE" : "ALL BUTTONS WORK", 960, 552, big);
        big.setTextAlign(Paint.Align.LEFT);
        mono.setColor(t.text);
        mono.setTextAlign(Paint.Align.CENTER);
        String q = rumble == 0 ? "RUMBLE CHECK: PRESS " + labels[PadTest.WEST] + " AND FEEL THE CONTROLLER"
                : rumble == 1 ? "DID YOU FEEL IT?  " + labels[PadTest.SOUTH] + " = YES   " + labels[PadTest.NORTH] + " = NO"
                : rumble == 2 ? "RUMBLE WORKS" : "NO RUMBLE: CHECK RUMBLE IN SETUP, OR THE CONTROLLER";
        c.drawText(q, 960, 600, mono);
        mono.setTextAlign(Paint.Align.LEFT);
    }
}
