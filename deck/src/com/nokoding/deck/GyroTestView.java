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
 * Setup > PAD > GYRO TEST. A low poly controller drawn with Canvas only (ControllerDraw)
 * follows the real controller. Orientation comes from Attitude, a complementary filter on the
 * controller's gyroscope and accelerometer. Also a tilt level with a one second trail, the raw
 * numbers, four corner markers and SWAY PREVIEW, a tile driven by the real gyro sway
 * settings. Sensor listeners exist only while this screen is open and resumed.
 *
 * 2.3: raw samples go through the controller's saved GyroCal (bias removed, measured axes),
 * with the time step from the sensor timestamps. While the controller lies still the bias is
 * tracked online and yaw slowly recenters. X (or the CALIBRATE chip) opens GYRO CALIBRATION.
 */
final class GyroTestView extends TestScreen implements PadSensors.Sink {

    static final int C_RECENTER = 0, C_CAL = 1, C_SWAY = 2, C_EXIT = 3;
    static final float MX = 720, MY = 560;            // model center, design px
    static final int TRAIL = 90;
    static final long TRAIL_MS = 1000;

    final Attitude att = new Attitude();
    private final ControllerDraw draw = new ControllerDraw();
    // trail ring buffer
    private final float[] trX = new float[TRAIL], trY = new float[TRAIL];
    private final long[] trT = new long[TRAIL];
    private int trHead, trCount;
    // sensors and calibration
    private final PadSensors sensors = new PadSensors(this);
    private final GyroCalStore store;
    private GyroCal cal = new GyroCal();
    private InputDevice dev;
    private final Stillness still = new Stillness();
    private final float[] mv = new float[3];
    private boolean resumed = true, seededOnce, calDirty;
    private float ax, ay, az = 0f;
    private String devLine = "", calLine = "NOT CALIBRATED";
    // sway preview with the real settings
    private boolean sway;
    private final GyroFilter swayFilter = new GyroFilter();
    private final float[] mapped = new float[2];
    private float swayWx, swayWy;
    private final boolean follow, swayOn;
    private final String swayLine;
    private static final String[] CORNERS = {"TL", "TR", "BL", "BR"};
    private static final float[] CXS = {200, 1240, 200, 1240}, CYS = {300, 300, 940, 940};
    private static final float TW = 300, TH = 168;
    private static final float[] TPX = {-TW / 2, TW / 2, TW / 2, -TW / 2}, TPY = {-TH / 2, -TH / 2, TH / 2, TH / 2};

    GyroTestView(Context c, Host host, boolean reduce, int preferredPad, boolean follow, int strengthPct, boolean gyroSwayOn, GyroCalStore store) {
        super(c, host, reduce, true);
        this.follow = follow;
        this.swayOn = gyroSwayOn;
        this.store = store;
        swayFilter.scale = GyroFilter.clampScale(strengthPct);
        swayLine = "DIRECTION " + (follow ? "FOLLOW" : "OPPOSITE") + "  //  STRENGTH " + Math.round(swayFilter.scale * 100) + "%"
                + (reduce ? "  //  REDUCE EFFECTS IS ON: NO SWAY" : (gyroSwayOn ? "" : "  //  GYRO SWAY IS OFF IN SETUP"));
        setChips(new String[]{"RECENTER", "CALIBRATE", "SWAY PREVIEW", "EXIT"}, new float[]{170, 180, 230, 120});
        InputDevice d = GyroCalStore.pad(preferredPad);
        if (d != null) bind(d);
    }

    // ---------------------------------------------------------------- sensors

    @Override
    void bind(InputDevice d) {
        saveOnline();
        sensors.unregister();
        dev = d;
        deviceId = d.getId();
        int v = d.getVendorId(), p = d.getProductId();
        String name = d.getName() == null ? "" : d.getName();
        int type = PadTest.detect(v, p, name);
        draw.setType(type);
        devLine = PadTest.TYPE_NAMES[type] + "  //  " + name.toUpperCase(Locale.ROOT) + "  //  VENDOR " + PadTest.hex4(v) + "  PRODUCT " + PadTest.hex4(p);
        cal = store.load(d);
        calDirty = false;
        calLine = cal.status() + "  //  X CALIBRATES";
        att.seeded = false;
        att.pitch = att.roll = att.yaw = 0f;
        att.recenter();
        seededOnce = false;
        trCount = 0;
        still.reset();
        sensors.lookup(d);
        still.hasAccel = sensors.hasAccel();
        if (resumed) sensors.register();
        invalidate();
    }

    /** Online bias updates of a calibrated controller are kept for next time. */
    private void saveOnline() {
        if (calDirty && dev != null && cal.calibrated()) store.save(dev, cal);
        calDirty = false;
    }

    @Override void pause() { resumed = false; sensors.unregister(); swayWx = swayWy = 0f; saveOnline(); }

    @Override void resume() { resumed = true; sensors.register(); invalidate(); }

    @Override void stop() { sensors.release(); swayWx = swayWy = 0f; saveOnline(); }

    @Override
    public void onGyro(float x, float y, float z, float dt, long ts) {
        still.feedGyro(x, y, z, dt, ts);
        float sf = still.stillFor(ts);
        if (GyroBias.online(cal, x, y, z, dt, sf)) calDirty = true;
        cal.gyro(x, y, z, mv);
        if (dt > 0f) {
            att.gyro(mv[0], mv[1], mv[2], dt);
            if (sf >= GyroBias.ONLINE_AFTER_S) att.settleYaw(dt);
        } else {
            att.rateX = mv[0]; att.rateY = mv[1]; att.rateZ = mv[2];
        }
        GyroFilter.axes(mv[0], mv[1], mv[2], follow, mapped);
        swayWx = mapped[0];
        swayWy = mapped[1];
        invalidate();
    }

    @Override
    public void onAccel(float x, float y, float z, float dt, long ts) {
        still.feedAccel(x, y, z, dt, ts);
        cal.accel(x, y, z, mv);
        ax = mv[0]; ay = mv[1]; az = mv[2];
        if (cal.accOk) {
            boolean used = att.accel(mv[0], mv[1], mv[2], dt);
            if (used && !seededOnce) { seededOnce = true; att.recenter(); }   // start from the pose it is held in
        }
        invalidate();
    }

// ---------------------------------------------------------------- input

    private void recenter() {
        att.recenter();
        trCount = 0;
        swayFilter.rx = swayFilter.ry = 0f;
        host.testFx(SoundMap.SELECT);
    }

    private void toggleSway() {
        sway = !sway;
        chipOn[C_SWAY] = sway;
        swayFilter.rx = swayFilter.ry = 0f;
        host.testFx(SoundMap.TOGGLE);
    }

    @Override
    void padKey(KeyEvent e, int code, boolean down, boolean first) {
        if (!first) {
            if (!down && code == KeyEvent.KEYCODE_BUTTON_B) exit(null);
            return;
        }
        switch (code) {
            case KeyEvent.KEYCODE_BUTTON_A: recenter(); break;
            case KeyEvent.KEYCODE_BUTTON_X: host.testCalibrate(); break;
            case KeyEvent.KEYCODE_BUTTON_Y: toggleSway(); break;
            default: break;
        }
    }

    @Override
    void otherKey(int code, boolean down, boolean first) {
        if (!first) return;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER: recenter(); break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT: toggleSway(); break;
            default: break;
        }
    }

    @Override
    void motion(MotionEvent e) {
        InputDevice dev = e.getDevice();
        if (deviceId < 0 && isPad(dev) && InputMode.has(e.getSource(), InputMode.SRC_JOYSTICK)
                && Math.abs(e.getAxisValue(MotionEvent.AXIS_X)) + Math.abs(e.getAxisValue(MotionEvent.AXIS_Y)) > 0.6f) bind(dev);
    }

    @Override
    void chipClicked(int idx) {
        if (idx == C_RECENTER) recenter();
        else if (idx == C_CAL) host.testCalibrate();
        else if (idx == C_SWAY) toggleSway();
        else exit(null);
    }

    @Override
    boolean animating(long now) {
        return sensors.live(now, 500) || (sway && !swayFilter.resting());
    }

    // ---------------------------------------------------------------- drawing

    @Override
    void content(Canvas c, long now, float dt) {
        Ui.Theme t = Ui.T;
        head.setColor(t.a);
        c.drawText("GYRO TEST", 96, 110, head);
        label.setColor(t.dim);
        c.drawText("TILT AND TURN THE CONTROLLER. A (OK ON THE REMOTE) RECENTERS. X CALIBRATES. Y SWITCHES SWAY PREVIEW.", 96, 152, label);
        mono.setColor(t.text);
        c.drawText(sensors.status, 96, 200, mono);
        mono.setColor(t.dim);
        c.drawText(devLine, 96, 232, mono);
        mono.setColor(cal.calibrated() ? t.a : t.dim);
        c.drawText(calLine, 96, 264, mono);

        float right = att.tiltRight(), up = att.tiltUp();
        drawCorners(c, t, Mat3D.corner(right, up, 10f));
        float cy = sway ? MY - 70 : MY;
        drawModel(c, t, cy);
        if (sway) drawSway(c, t, now, dt);
        drawReadout(c, t);
        drawLevel(c, t, now, right, up);

        small.setColor(t.dim);
        small.setTextAlign(Paint.Align.CENTER);
        c.drawText("EXIT: BACK ON THE REMOTE OR B  //  HOLD START + SELECT FOR 2 S  //  CLICK EXIT", 960, 1046, small);
        small.setTextAlign(Paint.Align.LEFT);
    }

    private void drawCorners(Canvas c, Ui.Theme t, int lit) {
        float[] xs = CXS, ys = CYS;
        for (int i = 0; i < 4; i++) {
            boolean on = lit == i;
            float x = xs[i], y = ys[i], sx = i % 2 == 0 ? 1 : -1, sy = i < 2 ? 1 : -1;
            line.setColor(on ? t.a : Ui.alpha(t.dim, 0.5f));
            line.setStrokeWidth(on ? 5 : 2);
            c.drawLine(x, y, x + 70 * sx, y, line);
            c.drawLine(x, y, x, y + 70 * sy, line);
            line.setStrokeWidth(2);
            if (on) {
                fill.setColor(Ui.alpha(t.a, 0.85f));
                rr.set(x + (sx > 0 ? 14 : -70), y + (sy > 0 ? 14 : -50), x + (sx > 0 ? 70 : -14), y + (sy > 0 ? 50 : -14));
                c.drawRect(rr, fill);
            }
            label.setColor(on ? t.bg0 : t.dim);
            label.setTextAlign(Paint.Align.CENTER);
            c.drawText(CORNERS[i], x + 42 * sx, y + (sy > 0 ? 42 : -22), label);
            label.setTextAlign(Paint.Align.LEFT);
        }
    }

    private void drawModel(Canvas c, Ui.Theme t, float cy) {
        draw.draw(c, t, att.relYaw(), att.relPitch(), att.relRoll(), MX, cy, fill, line, path);
    }

    private void drawSway(Canvas c, Ui.Theme t, long now, float dt) {
        boolean fresh = sensors.live(now, 120);
        if (!reduce && swayOn) swayFilter.update(fresh ? swayWx : 0f, fresh ? swayWy : 0f, dt);
        else { swayFilter.rx = 0f; swayFilter.ry = 0f; }
        // same as a View with rotationX = rx, rotationY = ry and translationX = shiftX:
        // positive rotationX tips the bottom edge away, positive rotationY tips the right edge away
        float cx = MX + swayFilter.shiftX(), cy = 900, h = TH;
        double a = Math.toRadians(swayFilter.rx), b = Math.toRadians(swayFilter.ry);
        float ca = (float) Math.cos(a), sa = (float) Math.sin(a), cb = (float) Math.cos(b), sb = (float) Math.sin(b);
        float[] px = TPX, py = TPY;
        path.reset();
        for (int i = 0; i < 4; i++) {
            float y1 = py[i] * ca, z1 = -py[i] * sa;
            float x2 = px[i] * cb, z2 = z1 * cb - px[i] * sb;
            float k = 1200f / (1200f - z2);
            float X = cx + x2 * k, Y = cy + y1 * k;
            if (i == 0) path.moveTo(X, Y); else path.lineTo(X, Y);
        }
        path.close();
        fill.setColor(Ui.alpha(t.panel, 0.95f));
        c.drawPath(path, fill);
        line.setColor(t.a);
        line.setStrokeWidth(3);
        c.drawPath(path, line);
        line.setStrokeWidth(2);
        label.setColor(t.text);
        label.setTextAlign(Paint.Align.CENTER);
        c.drawText("SWAY PREVIEW", cx, cy + 9, label);
        small.setColor(t.dim);
        small.setTextAlign(Paint.Align.CENTER);
        c.drawText(swayLine, MX, cy + h / 2 + 40, small);
        small.setTextAlign(Paint.Align.LEFT);
        label.setTextAlign(Paint.Align.LEFT);
    }

    private void drawReadout(Canvas c, Ui.Theme t) {
        float x = 1440, y = 320;
        label.setColor(t.dim);
        c.drawText("ANGLES (DEGREES)", x, y, label);
        y += 44;
        y = row(c, t, "PITCH UP  ", att.tiltUp(), 1, x, y);
        y = row(c, t, "ROLL RIGHT", att.tiltRight(), 1, x, y);
        y = row(c, t, "YAW LEFT  ", Attitude.deg(att.relYaw()), 1, x, y);
        y += 20;
        label.setColor(t.dim);
        c.drawText("GYRO RATES (RAD/S)", x, y, label);
        y += 40;
        y = row(c, t, "X", att.rateX, 2, x, y);
        y = row(c, t, "Y", att.rateY, 2, x, y);
        y = row(c, t, "Z", att.rateZ, 2, x, y);
        y += 20;
        label.setColor(t.dim);
        c.drawText("ACCEL (M/S2)", x, y, label);
        y += 40;
        int k = Fmt.fixed(buf, 0, ax, 1);
        k = Fmt.put(buf, k, ' ');
        k = Fmt.fixed(buf, k, ay, 1);
        k = Fmt.put(buf, k, ' ');
        k = Fmt.fixed(buf, k, az, 1);
        mono.setColor(t.text);
        text(c, k, x, y, mono);
    }

    private float row(Canvas c, Ui.Theme t, String name, float v, int dec, float x, float y) {
        mono.setColor(t.text);
        int k = Fmt.str(buf, 0, name);
        k = Fmt.put(buf, k, ' ');
        k = Fmt.fixed(buf, k, v, dec);
        text(c, k, x, y, mono);
        return y + 32;
    }

    private void drawLevel(Canvas c, Ui.Theme t, long now, float right, float up) {
        float cx = 1620, cy = 880, r = 120;
        line.setColor(Ui.alpha(t.dim, 0.5f));
        c.drawCircle(cx, cy, r, line);
        c.drawCircle(cx, cy, r / 3f, line);
        c.drawCircle(cx, cy, r * 2f / 3f, line);
        c.drawLine(cx - r, cy, cx + r, cy, line);
        c.drawLine(cx, cy - r, cx, cy + r, line);
        float dx = Ui.clamp(right / 45f, -1f, 1f) * r, dy = -Ui.clamp(up / 45f, -1f, 1f) * r;
        // trail: the last second of tilt
        trX[trHead] = dx;
        trY[trHead] = dy;
        trT[trHead] = now;
        trHead = (trHead + 1) % TRAIL;
        if (trCount < TRAIL) trCount++;
        for (int i = 0; i < trCount; i++) {
            int j = (trHead - 1 - i + TRAIL * 2) % TRAIL;
            long age = now - trT[j];
            if (age > TRAIL_MS) break;
            float a = 1f - age / (float) TRAIL_MS;
            fill.setColor(Ui.alpha(t.b, 0.5f * a));
            c.drawCircle(cx + trX[j], cy + trY[j], 3 + 5 * a, fill);
        }
        fill.setColor(t.a);
        c.drawCircle(cx + dx, cy + dy, 14, fill);
        label.setColor(t.dim);
        label.setTextAlign(Paint.Align.CENTER);
        c.drawText("TILT LEVEL (45 DEG AT THE RIM)", cx, cy - r - 22, label);
        label.setTextAlign(Paint.Align.LEFT);
    }
}
