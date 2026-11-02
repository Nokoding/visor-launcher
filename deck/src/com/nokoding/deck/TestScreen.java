package com.nokoding.deck;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.text.TextPaint;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

/**
 * Base for the full screen controller tests (2.2). While one is open HomeActivity sends it
 * every key and motion event, so nothing navigates or launches behind it. Exit always works:
 * Back from the remote, holding Start and Select for 2 seconds, a pointer click on EXIT, the
 * tested controller disconnecting, or Home. Draws in 1920x1080 design pixels.
 */
abstract class TestScreen extends View {

    interface Host {
        /** Remove the screen and go back to Setup. toast may be null. */
        void closeTest(String toast);
        void testFx(int soundEvent);
        /** 2.3: leave this screen for GYRO CALIBRATION and come back here afterwards. */
        void testCalibrate();
        /** 2.4.1: true when the left stick is the navigator (WHEEL STICK is the right one, the default). */
        boolean testNavStickLeft();
    }

    static final int MAX_CHIPS = 5;
    final Host host;
    final boolean reduce;
    /** The controller under test, or -1 until one sends a button. */
    int deviceId = -1;
    final PadTest.ExitHold hold = new PadTest.ExitHold();
    boolean startHeld, selectHeld;
    private boolean exiting;

    final RectF[] chip = new RectF[MAX_CHIPS];
    final String[] chipLabel = new String[MAX_CHIPS];
    final boolean[] chipOn = new boolean[MAX_CHIPS];
    int chips, hover = -1;
    private long lastPress = -100000, lastFrame;
    /** 2.4.1: which top chip has the focus ring, moved by the navigator stick, the D-pad and the remote. */
    final ChipFocus nav;
    private int hatX, hatY;

    final Paint bg = Ui.fill(), gridLine = Ui.stroke(1), fill = Ui.fill(), line = Ui.stroke(2), glow = Ui.stroke(6);
    final TextPaint head, label, mono, small, big;
    final Path path = new Path();
    final RectF rr = new RectF();
    final char[] buf = new char[64];
    private final float[] anchor = new float[2];

    /** @param padDpadStarts true where the tested pad's own D-pad may start chip focus (not where D-pad presses are test inputs) */
    TestScreen(Context c, Host host, boolean reduce, boolean padDpadStarts) {
        super(c);
        this.host = host;
        this.reduce = reduce;
        nav = new ChipFocus(0, padDpadStarts);
        head = tp(Ui.HEAD, 44);
        head.setLetterSpacing(Ui.HEAD_SPACING);
        big = tp(Ui.HEAD, 64);
        label = tp(Ui.COND_B, 26);
        mono = tp(Ui.MONO, 22);
        small = tp(Ui.MONO, 18);
        for (int i = 0; i < MAX_CHIPS; i++) chip[i] = new RectF();
        setWillNotDraw(false);
        setFocusable(false);
    }

    static TextPaint tp(android.graphics.Typeface tf, float size) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setTypeface(tf);
        p.setTextSize(size);   // design px, the canvas is scaled
        return p;
    }

    @Override public boolean hasOverlappingRendering() { return false; }

    /** Chips sit in the top right corner, right aligned, in design px. */
    void setChips(String[] labels, float[] widths) {
        chips = Math.min(MAX_CHIPS, labels.length);
        nav.setCount(chips);
        float x = 1824;
        for (int i = chips - 1; i >= 0; i--) {
            chip[i].set(x - widths[i], 64, x, 116);
            chipLabel[i] = labels[i];
            x -= widths[i] + 16;
        }
    }

    // ---------------------------------------------------------------- input

    static boolean isPad(InputDevice d) {
        if (d == null || d.isVirtual()) return false;
        int s = d.getSources();
        return InputMode.has(s, InputMode.SRC_GAMEPAD) || InputMode.has(s, InputMode.SRC_JOYSTICK);
    }

    /** True if this key event comes from the controller under test. */
    boolean fromTestPad(KeyEvent e) {
        InputDevice d = e.getDevice();
        return d != null && deviceId >= 0 && d.getId() == deviceId;
    }

    /** 2.6: sees every key event first, before the chips and the other handling. LEARN A BUTTON uses it. */
    void rawKey(KeyEvent e) {}

    /** 2.6: true where even the volume keys from the remote must stay in the screen (LEARN A BUTTON shows them). */
    boolean wantsAllKeys() { return false; }

    /** 2.17: true where holding B (Circle) for 1.5 s leaves the screen (the Gamepad test). */
    boolean holdBExits() { return false; }

    /** 2.17: where the exit ring is drawn, design px. Centre of the screen by default. */
    void holdAnchor(float[] out) { out[0] = 960; out[1] = 540; }

    /** True where A (Cross) pressed on a focused chip must still count as a button press (the Gamepad test). */
    boolean padKeyCounts() { return false; }

    /** Name of the A button for the hint line. */
    String aName() { return "A"; }

    /** Called for every key while the screen is open. Always consumes. */
    final void key(KeyEvent e) {
        if (exiting) return;
        rawKey(e);
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN, first = down && e.getRepeatCount() == 0;
        InputDevice dev = e.getDevice();
        boolean gamepadButton = KeyEvent.isGamepadButton(code);
        if (gamepadButton && isPad(dev) && dev.getId() != deviceId) bind(dev);
        if (fromTestPad(e)) {
            if (code == KeyEvent.KEYCODE_BUTTON_START) startHeld = down;
            if (code == KeyEvent.KEYCODE_BUTTON_SELECT) selectHeld = down;
            hold.set(startHeld, selectHeld, SystemClock.uptimeMillis());
            if (code == KeyEvent.KEYCODE_BUTTON_B && holdBExits()) hold.setEast(down, SystemClock.uptimeMillis());
            boolean skip = false;
            if (first) {
                if (code == KeyEvent.KEYCODE_BUTTON_A && nav.active()) {
                    // A presses the focused chip first, then still counts as a pressed button where buttons are tested
                    skip = !padKeyCounts();
                    chipClicked(nav.activate());
                    if (exiting) return;
                } else {
                    navDpad(code, true);
                }
            }
            if (!skip) padKey(e, code, down, first);
        } else {
            // the remote, a keyboard or anything that is not the tested controller
            if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
                if (!down) exit(null);
            } else if (first && (navDpad(code, false) || navOk(code))) {
                // the chips used this key
            } else {
                otherKey(code, down, first);
            }
        }
        navTimers();
        invalidate();
    }

    /** OK or Enter presses the focused chip. */
    private boolean navOk(int code) {
        if (!nav.active()) return false;
        if (code != KeyEvent.KEYCODE_DPAD_CENTER && code != KeyEvent.KEYCODE_ENTER && code != KeyEvent.KEYCODE_NUMPAD_ENTER) return false;
        chipClicked(nav.activate());
        return true;
    }

    /** D-pad keys into the chip focus. @return true if the chips used the key. */
    private boolean navDpad(int code, boolean fromPad) {
        int dx = code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : (code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : 0);
        int dy = code == KeyEvent.KEYCODE_DPAD_UP ? -1 : (code == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : 0);
        if (dx == 0 && dy == 0) return false;
        boolean used = nav.key(dx, dy, fromPad, SystemClock.uptimeMillis());
        if (used) host.testFx(SoundMap.MOVE);
        return used;
    }

    /** Joystick and hat events: the subclass first (it counts buttons), then the navigator stick and the hat. */
    final void joystick(MotionEvent e) {
        motion(e);
        if (exiting || deviceId < 0 || !InputMode.has(e.getSource(), InputMode.SRC_JOYSTICK)) return;
        InputDevice d = e.getDevice();
        if (d == null || d.getId() != deviceId) return;
        float x, y;
        if (host.testNavStickLeft()) {
            x = axisOf(e, d, MotionEvent.AXIS_X);
            y = axisOf(e, d, MotionEvent.AXIS_Y);
        } else {
            boolean zr = d.getMotionRange(MotionEvent.AXIS_Z, e.getSource()) != null;
            x = axisOf(e, d, zr ? MotionEvent.AXIS_Z : MotionEvent.AXIS_RX);
            y = axisOf(e, d, zr ? MotionEvent.AXIS_RZ : MotionEvent.AXIS_RY);
        }
        long now = SystemClock.uptimeMillis();
        boolean moved = nav.stick(x, y, now);
        // the hat is the pad's D-pad: one chip step per press
        float hx = axisOf(e, d, MotionEvent.AXIS_HAT_X), hy = axisOf(e, d, MotionEvent.AXIS_HAT_Y);
        int ix = hx <= -0.5f ? -1 : (hx >= 0.5f ? 1 : 0), iy = hy <= -0.5f ? -1 : (hy >= 0.5f ? 1 : 0);
        if (ix != hatX) { hatX = ix; if (ix != 0) moved |= nav.key(ix, 0, true, now); }
        if (iy != hatY) { hatY = iy; if (iy != 0) moved |= nav.key(0, iy, true, now); }
        if (moved) host.testFx(SoundMap.MOVE);
        navTimers();
        invalidate();
    }

    private static float axisOf(MotionEvent e, InputDevice d, int a) {
        if (d.getMotionRange(a, e.getSource()) == null) return 0f;
        float v = e.getAxisValue(a);
        return Float.isNaN(v) ? 0f : v;
    }

    // stick repeats and the idle release run on timers, because a held stick sends no new events
    private final Runnable navRepeat = new Runnable() {
        @Override public void run() {
            if (exiting) return;
            if (nav.poll(SystemClock.uptimeMillis())) { host.testFx(SoundMap.MOVE); invalidate(); }
            navTimers();
        }
    };
    private final Runnable navExpire = new Runnable() {
        @Override public void run() {
            if (nav.expire(SystemClock.uptimeMillis())) invalidate();
            navTimers();
        }
    };

    private void navTimers() {
        removeCallbacks(navRepeat);
        removeCallbacks(navExpire);
        long now = SystemClock.uptimeMillis();
        long w = nav.waitMs(now);
        if (w >= 0) postDelayed(navRepeat, Math.max(16, w));
        long x = nav.expireIn(now);
        if (x >= 0) postDelayed(navExpire, x + 20);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(navRepeat);
        removeCallbacks(navExpire);
        super.onDetachedFromWindow();
    }

    void exit(String toast) {
        if (exiting) return;
        exiting = true;
        stop();
        host.closeTest(toast);
    }

    /** The tested controller went away: leave, never wait on a controller that is gone. */
    void deviceRemoved(int id) {
        if (id == deviceId && deviceId >= 0) exit("Controller disconnected. Test closed.");
    }

    /** Hover highlights a chip, a button press clicks. Secondary button acts like Back. */
    void pointer(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_HOVER_ENTER:
            case MotionEvent.ACTION_HOVER_MOVE: {
                int h = chipAt(e.getX(), e.getY());
                if (h != hover) { hover = h; invalidate(); }
                break;
            }
            case MotionEvent.ACTION_HOVER_EXIT:
                if (hover != -1) { hover = -1; invalidate(); }
                break;
            case MotionEvent.ACTION_BUTTON_PRESS: {
                int b = e.getActionButton();
                lastPress = SystemClock.uptimeMillis();
                if (b == MotionEvent.BUTTON_SECONDARY || b == MotionEvent.BUTTON_BACK) { exit(null); break; }
                click(e.getX(), e.getY(), e.getDevice());
                break;
            }
            default:
                break;
        }
    }

    /** Touchpad or mouse clicks that arrive as touch events: one click on release. */
    void touch(MotionEvent e) {
        int src = e.getSource();
        if (!(InputMode.has(src, InputDevice.SOURCE_MOUSE) || InputMode.has(src, InputDevice.SOURCE_TOUCHPAD))) return;
        if (e.getActionMasked() == MotionEvent.ACTION_UP && SystemClock.uptimeMillis() - lastPress > 400) click(e.getX(), e.getY(), e.getDevice());
    }

    int chipAt(float xPx, float yPx) {
        float x = xPx / Ui.u, y = yPx / Ui.u;
        for (int i = 0; i < chips; i++) if (chip[i].contains(x, y)) return i;
        return -1;
    }

    private void click(float x, float y, InputDevice dev) {
        if (exiting) return;
        int c = chipAt(x, y);
        if (c >= 0) chipClicked(c); else clickElsewhere(dev);
        invalidate();
    }

    abstract void bind(InputDevice d);

    abstract void padKey(KeyEvent e, int code, boolean down, boolean first);

    abstract void otherKey(int code, boolean down, boolean first);

    abstract void motion(MotionEvent e);

    abstract void chipClicked(int idx);

    void clickElsewhere(InputDevice dev) {}

    /** Release sensors and anything else. Called on exit. */
    void stop() {}

    void pause() {}

    void resume() {}

    // ---------------------------------------------------------------- drawing

    @Override
    protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        float dt = lastFrame == 0 ? 1f / 60f : Ui.clamp((now - lastFrame) / 1000f, 0.001f, 0.1f);
        lastFrame = now;
        if (hold.done(now)) {
            hold.clear();
            post(() -> exit(null));   // never remove this view from inside its own draw
            return;
        }
        c.save();
        c.scale(Ui.u, Ui.u);
        Ui.Theme t = Ui.T;
        bg.setColor(t.bg0);
        c.drawRect(0, 0, 1920, 1080, bg);
        gridLine.setColor(Ui.alpha(t.text, 0.04f));
        for (int x = 0; x <= 1920; x += 80) c.drawLine(x, 0, x, 1080, gridLine);
        for (int y = 0; y <= 1080; y += 80) c.drawLine(0, y, 1920, y, gridLine);
        content(c, now, dt);
        drawChips(c, t);
        drawNavHint(c, t);
        drawHold(c, t, now);
        c.restore();
        boolean more = animating(now) || hold.holding();
        if (more) {
            if (reduce) postInvalidateDelayed(33); else postInvalidateOnAnimation();
        } else {
            lastFrame = 0;
        }
    }

    abstract void content(Canvas c, long now, float dt);

    abstract boolean animating(long now);

    void drawChips(Canvas c, Ui.Theme t) {
        label.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < chips; i++) {
            RectF r = chip[i];
            boolean on = chipOn[i], hv = hover == i;
            Ui.cut(path, r.left, r.top, r.right, r.bottom, 12, 4);
            fill.setColor(on ? Ui.alpha(t.a, 0.85f) : Ui.alpha(t.panel, 0.95f));
            c.drawPath(path, fill);
            line.setColor(hv ? t.text : (on ? t.a : Ui.alpha(t.dim, 0.7f)));
            line.setStrokeWidth(hv ? 3 : 2);
            c.drawPath(path, line);
            label.setColor(on ? t.bg0 : t.text);
            c.drawText(chipLabel[i], r.centerX(), r.centerY() + 9, label);
            if (nav.focus() == i) {
                // 2.4.1: the focus ring, a bright outer outline with a soft halo
                Ui.cut(path, r.left - 8, r.top - 8, r.right + 8, r.bottom + 8, 15, 6);
                glow.setColor(Ui.alpha(t.a, 0.35f));
                glow.setStrokeWidth(9);
                c.drawPath(path, glow);
                line.setColor(t.text);
                line.setStrokeWidth(4);
                c.drawPath(path, line);
            }
        }
        label.setTextAlign(Paint.Align.LEFT);
        line.setStrokeWidth(2);
    }

    /** One small line that explains how to reach the top chips with a stick. */
    void drawNavHint(Canvas c, Ui.Theme t) {
        if (chips == 0 || hold.holding()) return;
        String hint = nav.active()
                ? "TOP BUTTONS: LEFT OR RIGHT PICKS  //  " + aName() + " OR OK PRESSES  //  DOWN LEAVES"
                : "TOP BUTTONS: PUSH THE " + (host.testNavStickLeft() ? "LEFT" : "RIGHT") + " STICK UP (OR UP ON THE REMOTE)";
        small.setColor(nav.active() ? t.a : t.dim);
        small.setTextAlign(Paint.Align.CENTER);
        c.drawText(hint, 960, 1022, small);
        small.setTextAlign(Paint.Align.LEFT);
    }

    /** The Start + Select exit ring, in the middle of the screen while it fills. */
    void drawHold(Canvas c, Ui.Theme t, long now) {
        if (!hold.holding()) return;
        float p = hold.progress(now), r = 96;
        holdAnchor(anchor);
        if (!hold.eastLeads(now)) { anchor[0] = 960; anchor[1] = 540; }
        float cx = anchor[0], cy = anchor[1];
        boolean small = hold.eastLeads(now) && !(anchor[0] == 960 && anchor[1] == 540);
        if (small) r = 40;
        fill.setColor(Ui.alpha(t.bg0, 0.86f));
        c.drawCircle(cx, cy, r + (small ? 14 : 40), fill);
        line.setColor(Ui.alpha(t.dim, 0.5f));
        line.setStrokeWidth(10);
        c.drawCircle(cx, cy, r, line);
        line.setColor(t.a);
        rr.set(cx - r, cy - r, cx + r, cy + r);
        c.drawArc(rr, -90, 360 * p, false, line);
        line.setStrokeWidth(2);
        label.setTextAlign(Paint.Align.CENTER);
        label.setColor(t.text);
        if (!small) c.drawText("HOLD TO EXIT", cx, cy + 10, label);
        label.setTextAlign(Paint.Align.LEFT);
    }

    /** Draws chars buf[0..n) at x, y. */
    void text(Canvas c, int n, float x, float y, Paint p) { c.drawText(buf, 0, n, x, y, p); }
}
