package com.nokoding.deck;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.text.TextPaint;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.util.Locale;

/**
 * 2.3: Setup > PAD > GYRO CALIBRATION (also X in the gyro test). Three steps with prompts and
 * a progress ring, usable from the remote, the controller and the pointer:
 *  1. BIAS: flat on a table, untouched. 3 s of still samples (Stillness) are averaged into the
 *     zero rate per axis. Any motion restarts it.
 *  2. AXES: three guided turns (AxisMap) give the signed permutation from raw sensor axes to
 *     Deck's model axes, checked to be a valid right handed one. Gravity from step 1 then
 *     says whether the accelerometer can be trusted (GyroCal.gravityCheck).
 *  3. CHECK: the model with the new calibration; turn about 90 degrees and back, the leftover
 *     angle shows. SAVE stores it for this controller (GyroCalStore), REDO starts over.
 * Sensors run only while this screen is open and resumed.
 */
final class GyroCalView extends TestScreen implements PadSensors.Sink {

    static final int S_BIAS = 0, S_BIAS_OK = 1, S_AXES = 2, S_AXES_BAD = 3, S_CHECK = 4, S_DONE = 5;
    static final int P_CALM = 0, P_MEASURE = 1, P_GOT = 2;
    static final int C_REDO = 0, C_SAVE = 1, C_EXIT = 2;
    /** Hand held "calm": bias corrected rates below this (rad/s). Looser than Stillness on purpose. */
    static final float CALM = 0.25f, CALM_S = 0.3f;
    static final long GOT_MS = 700;
    static final String[] STEPS = {"1  BIAS", "2  AXES", "3  CHECK"};

    private final GyroCalStore store;
    private final PadSensors sensors = new PadSensors(this);
    private final Stillness still = new Stillness();
    private final GyroBias bias = new GyroBias();
    private final AxisMap axis = new AxisMap();
    private final CalCheck check = new CalCheck();
    private final Attitude att = new Attitude();
    private final ControllerDraw draw = new ControllerDraw();
    private GyroCal cal = new GyroCal();
    private InputDevice dev;
    private boolean resumed = true;

    private int state = S_BIAS, gesture, phase;
    private final int[] rawAxis = new int[3], rawSign = new int[3];
    private final float[] mtmp = new float[9], mv = new float[3];
    private float calmFor, rx, ry, rz;
    private long gotAt;
    private int seenRestarts;
    private String note = "", devLine = "", biasLine = "", axesLine = "", doneLine = "", accLine = "";
    private final TextPaint prompt;

    GyroCalView(Context c, Host host, boolean reduce, int preferredPad, GyroCalStore store) {
        super(c, host, reduce, true);
        this.store = store;
        prompt = tp(Ui.COND_B, 40);
        setChips(new String[]{"REDO", "SAVE", "EXIT"}, new float[]{130, 130, 120});
        InputDevice d = GyroCalStore.pad(preferredPad);
        if (d != null) bind(d);
    }

    // ---------------------------------------------------------------- binding and steps

    @Override
    void bind(InputDevice d) {
        sensors.unregister();
        dev = d;
        deviceId = d.getId();
        String name = d.getName() == null ? "" : d.getName();
        int v = d.getVendorId(), p = d.getProductId();
        int type = PadTest.detect(v, p, name);
        draw.setType(type);
        devLine = PadTest.TYPE_NAMES[type] + "  //  " + name.toUpperCase(Locale.ROOT) + "  //  NOW: " + store.load(d).status();
        sensors.lookup(d);
        still.hasAccel = sensors.hasAccel();
        if (resumed) sensors.register();
        startBias();
    }

    private void startBias() {
        cal = new GyroCal();
        state = S_BIAS;
        bias.reset();
        bias.restarts = 0;
        seenRestarts = 0;
        still.reset();
        note = "";
        biasLine = axesLine = doneLine = accLine = "";
        chipOn[C_SAVE] = false;
        invalidate();
    }

    private void startAxes() {
        state = S_AXES;
        gesture = 0;
        phase = P_CALM;
        calmFor = 0f;
        axis.begin();
        note = "";
        axesLine = "";
        invalidate();
    }

    private void startCheck() {
        state = S_CHECK;
        check.reset();
        att.seeded = false;
        att.pitch = att.roll = att.yaw = 0f;
        att.recenter();
        calmFor = 0f;
        chipOn[C_SAVE] = false;
        doneLine = "";
        invalidate();
    }

    private void finishAxes() {
        if (!AxisMap.build(rawAxis, rawSign, mtmp)) {
            state = S_AXES_BAD;
            note = "THAT DID NOT ADD UP (ONE TURN WENT THE OTHER WAY OR THE SAME AXIS CAME TWICE).";
            host.testFx(SoundMap.ERROR);
            return;
        }
        System.arraycopy(mtmp, 0, cal.m, 0, 9);
        StringBuilder s = new StringBuilder("AXES  ");
        String[] names = {"X", "Y", "Z"};
        for (int r = 0; r < 3; r++) {
            for (int k = 0; k < 3; k++) {
                float m = cal.m[r * 3 + k];
                if (m != 0f) s.append(names[r]).append(" = ").append(m > 0 ? "+" : "-").append("RAW").append(k).append("   ");
            }
        }
        boolean same = true;
        for (int i = 0; i < 9; i++) if (cal.m[i] != (i % 4 == 0 ? 1f : 0f)) same = false;
        s.append(same ? "(SAME AS THE 2.2 GUESS)" : "(DIFFERENT FROM THE 2.2 GUESS)");
        axesLine = s.toString();
        if (bias.hasGravity()) {
            int g = cal.gravityCheck(bias.gx(), bias.gy(), bias.gz());
            accLine = g == 1 ? "ACCELEROMETER: MATCHES, TILT CORRECTION ON"
                    : (g == -1 ? "ACCELEROMETER: SIGN FLIPPED, FIXED, TILT CORRECTION ON" : "ACCELEROMETER: AXES DO NOT MATCH THE GYRO, TILT CORRECTION OFF");
        } else {
            cal.accOk = sensors.hasAccel();
            accLine = sensors.hasAccel() ? "ACCELEROMETER: NO GRAVITY READING YET" : "NO ACCELEROMETER";
        }
        host.testFx(SoundMap.SELECT);
        startCheck();
    }

    private void save() {
        if (state != S_DONE || dev == null) { host.testFx(SoundMap.ERROR); return; }
        cal.savedAt = System.currentTimeMillis();
        cal.checkErr = check.errDeg;
        store.save(dev, cal);
        host.testFx(SoundMap.TEST_COMPLETE);
        exit("Gyro calibration saved");
    }

    private void primary() {
        switch (state) {
            case S_BIAS_OK:
            case S_AXES_BAD: host.testFx(SoundMap.SELECT); startAxes(); break;
            case S_DONE: save(); break;
            default: break;
        }
    }

    private void redo() {
        host.testFx(SoundMap.BACK);
        switch (state) {
            case S_AXES:
            case S_AXES_BAD: startAxes(); break;
            case S_CHECK: startCheck(); break;
            default: startBias(); break;   // BIAS, BIAS_OK and DONE (redo it all)
        }
    }

    // ---------------------------------------------------------------- sensors

    @Override
    public void onGyro(float x, float y, float z, float dt, long ts) {
        rx = x; ry = y; rz = z;
        still.feedGyro(x, y, z, dt, ts);
        if (!(dt > 0f)) return;
        float cx = x - cal.bx, cy = y - cal.by, cz = z - cal.bz;
        float n = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
        calmFor = n < CALM ? calmFor + dt : 0f;
        long now = SystemClock.uptimeMillis();
        switch (state) {
            case S_BIAS: {
                boolean done = bias.feed(x, y, z, dt, still.still());
                if (bias.restarts != seenRestarts) { seenRestarts = bias.restarts; note = "MOVED. STARTING OVER."; host.testFx(SoundMap.TOGGLE); }
                if (done) {
                    cal.setBias(bias.bx(), bias.by(), bias.bz());
                    biasLine = String.format(Locale.ROOT, "BIAS  X %+.4f   Y %+.4f   Z %+.4f RAD/S   (%.2f DEG/S IN ALL)",
                            cal.bx, cal.by, cal.bz, Math.toDegrees(cal.biasMag()));
                    note = "";
                    state = S_BIAS_OK;
                    host.testFx(SoundMap.SELECT);
                }
                break;
            }
            case S_AXES:
                if (phase == P_CALM) {
                    if (calmFor >= CALM_S) { phase = P_MEASURE; axis.begin(); }
                } else if (phase == P_MEASURE) {
                    int r = axis.feed(cx, cy, cz, dt);
                    if (r == AxisMap.DONE) {
                        rawAxis[gesture] = axis.axis;
                        rawSign[gesture] = axis.sign;
                        phase = P_GOT;
                        gotAt = now;
                        note = "";
                        host.testFx(SoundMap.TOGGLE);
                    } else if (r == AxisMap.MIXED) {
                        note = "TOO MIXED. HOLD IT STILL, THEN TRY THAT ONE AGAIN.";
                        phase = P_CALM;
                        calmFor = 0f;
                        host.testFx(SoundMap.ERROR);
                    }
                } else if (now - gotAt >= GOT_MS) {
                    gesture++;
                    if (gesture >= 3) finishAxes();
                    else { phase = P_CALM; calmFor = 0f; }
                }
                break;
            case S_CHECK:
            case S_DONE: {
                cal.gyro(x, y, z, mv);
                att.gyro(mv[0], mv[1], mv[2], dt);
                if (state == S_CHECK) {
                    float a = check.angleDeg(att.relYaw(), att.relPitch(), att.relRoll());
                    if (check.feed(a, calmFor > 0f, calmFor)) att.recenter();
                    if (check.state == CalCheck.DONE) {
                        state = S_DONE;
                        chipOn[C_SAVE] = true;
                        doneLine = String.format(Locale.ROOT, "LEFTOVER ERROR %.1f DEG AFTER A %.0f DEG TURN", check.errDeg, check.maxDeg);
                        host.testFx(SoundMap.SELECT);
                    }
                }
                break;
            }
            default:
                break;
        }
        invalidate();
    }

    @Override
    public void onAccel(float x, float y, float z, float dt, long ts) {
        still.feedAccel(x, y, z, dt, ts);
        if (state == S_BIAS) bias.feedAccel(x, y, z);
        if ((state == S_CHECK || state == S_DONE) && cal.accOk) {
            cal.accel(x, y, z, mv);
            if (att.accel(mv[0], mv[1], mv[2], dt) && !att.seeded) att.recenter();
        }
    }

    @Override void pause() { resumed = false; sensors.unregister(); }

    @Override void resume() { resumed = true; sensors.register(); }

    @Override void stop() { sensors.release(); }

    // ---------------------------------------------------------------- input

    @Override
    void padKey(KeyEvent e, int code, boolean down, boolean first) {
        if (!first) {
            if (!down && code == KeyEvent.KEYCODE_BUTTON_B) exit(null);
            return;
        }
        if (code == KeyEvent.KEYCODE_BUTTON_A) primary();
        else if (code == KeyEvent.KEYCODE_BUTTON_X) redo();
    }

    @Override
    void otherKey(int code, boolean down, boolean first) {
        if (!first) return;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER: primary(); break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT: redo(); break;
            default: break;
        }
    }

    @Override
    void motion(MotionEvent e) {
        InputDevice d = e.getDevice();
        if (deviceId < 0 && isPad(d) && InputMode.has(e.getSource(), InputMode.SRC_JOYSTICK)
                && Math.abs(e.getAxisValue(MotionEvent.AXIS_X)) + Math.abs(e.getAxisValue(MotionEvent.AXIS_Y)) > 0.6f) bind(d);
    }

    @Override
    void chipClicked(int idx) {
        if (idx == C_REDO) redo();
        else if (idx == C_SAVE) save();
        else exit(null);
    }

    @Override
    void clickElsewhere(InputDevice d) { primary(); }

    @Override
    boolean animating(long now) { return sensors.live(now, 500); }

    // ---------------------------------------------------------------- drawing

    private int stepIndex() { return state <= S_BIAS_OK ? 0 : (state <= S_AXES_BAD ? 1 : 2); }

    private String promptText() {
        switch (state) {
            case S_BIAS: return "PUT THE CONTROLLER FLAT ON A TABLE AND DO NOT TOUCH IT";
            case S_BIAS_OK: return "DONE. PICK IT UP, HOLD IT NORMALLY, THEN PRESS A OR OK";
            case S_AXES:
                if (phase == P_GOT) return "GOT IT";
                if (phase == P_CALM) return "HOLD IT STILL IN YOUR HANDS";
                return AxisMap.PROMPTS[gesture];
            case S_AXES_BAD: return "PRESS A OR OK TO REDO THE THREE TURNS";
            case S_CHECK:
                if (check.state == CalCheck.HOLD) return "HOLD IT STILL";
                if (check.state == CalCheck.ROTATE) return "TURN IT ABOUT 90 DEGREES, ANY WAY";
                return "NOW BRING IT BACK AND HOLD STILL";
            default: return "A OR OK SAVES.  X OR LEFT / RIGHT STARTS OVER.";
        }
    }

    private float ringProgress() {
        switch (state) {
            case S_BIAS: return bias.progress();
            case S_BIAS_OK: return 1f;
            case S_AXES: return phase == P_CALM ? Math.min(1f, calmFor / CALM_S) * 0.15f : axis.progress();
            case S_CHECK: return check.state == CalCheck.ROTATE ? Math.min(1f, check.maxDeg / CalCheck.TURN_DEG)
                    : (check.state == CalCheck.RETURN ? 1f - 0.5f * Math.min(1f, Math.max(0f, calmFor / CalCheck.BACK_S)) : 0f);
            default: return 1f;
        }
    }

    @Override
    void content(Canvas c, long now, float dt) {
        Ui.Theme t = Ui.T;
        head.setColor(t.a);
        c.drawText("GYRO CALIBRATION", 96, 110, head);
        label.setColor(t.dim);
        c.drawText(devLine.isEmpty() ? "PRESS A BUTTON ON THE CONTROLLER TO CALIBRATE" : devLine, 96, 152, label);
        mono.setColor(t.text);
        c.drawText(sensors.status, 96, 200, mono);

        // step strip
        int cur = stepIndex();
        for (int i = 0; i < 3; i++) {
            float x = 96 + i * 250, y = 240;
            Ui.cut(path, x, y, x + 230, y + 52, 12, 4);
            boolean on = i == cur, done = i < cur;
            fill.setColor(on ? Ui.alpha(t.a, 0.85f) : Ui.alpha(t.panel, 0.95f));
            c.drawPath(path, fill);
            line.setColor(on || done ? t.a : Ui.alpha(t.dim, 0.6f));
            c.drawPath(path, line);
            label.setColor(on ? t.bg0 : (done ? t.a : t.dim));
            c.drawText(STEPS[i], x + 24, y + 35, label);
        }

        // prompt and note
        prompt.setColor(t.text);
        c.drawText(promptText(), 96, 378, prompt);
        if (!note.isEmpty()) {
            label.setColor(Ui.DANGER);
            c.drawText(note, 96, 424, label);
        }
        if (state == S_AXES) {
            small.setColor(t.dim);
            int k = Fmt.str(buf, 0, "TURN ");
            k = Fmt.put(buf, k, (char) ('1' + Math.min(gesture, 2)));
            k = Fmt.str(buf, k, " OF 3");
            text(c, k, 96, 462, small);
        }

        // progress ring
        float cx = 400, cy = 680, r = 130;
        line.setColor(Ui.alpha(t.dim, 0.4f));
        line.setStrokeWidth(12);
        c.drawCircle(cx, cy, r, line);
        line.setColor(t.a);
        rr.set(cx - r, cy - r, cx + r, cy + r);
        c.drawArc(rr, -90, 360 * ringProgress(), false, line);
        line.setStrokeWidth(2);
        big.setColor(t.text);
        big.setTextAlign(Paint.Align.CENTER);
        int k = Fmt.fixed(buf, 0, ringProgress() * 100f, 0);
        k = Fmt.put(buf, k, '%');
        c.drawText(buf, 0, k, cx, cy + 22, big);
        big.setTextAlign(Paint.Align.LEFT);
        if (state == S_BIAS) {
            small.setTextAlign(Paint.Align.CENTER);
            small.setColor(still.still() ? t.a : t.dim);
            c.drawText(still.still() ? "STILL, MEASURING" : "WAITING FOR IT TO SETTLE", cx, cy + r + 48, small);
            small.setTextAlign(Paint.Align.LEFT);
        }

        // model while checking, results otherwise
        if (state == S_CHECK || state == S_DONE) {
            draw.draw(c, t, att.relYaw(), att.relPitch(), att.relRoll(), 1180, 660, fill, line, path);
            mono.setColor(t.text);
            int n = Fmt.str(buf, 0, "FROM START ");
            n = Fmt.fixed(buf, n, check.angleDeg(att.relYaw(), att.relPitch(), att.relRoll()), 1);
            n = Fmt.str(buf, n, " DEG");
            text(c, n, 1440, 340, mono);
        }
        float y = 890;
        mono.setColor(t.text);
        if (!biasLine.isEmpty()) { c.drawText(biasLine, 96, y, mono); y += 34; }
        if (!axesLine.isEmpty()) { c.drawText(axesLine, 96, y, mono); y += 34; }
        if (!accLine.isEmpty()) { mono.setColor(cal.accOk ? t.dim : Ui.DANGER); c.drawText(accLine, 96, y, mono); y += 34; }
        if (!doneLine.isEmpty()) { mono.setColor(t.a); c.drawText(doneLine, 96, y, mono); }

        // live raw rates
        small.setColor(t.dim);
        int m = Fmt.str(buf, 0, "RAW GYRO ");
        m = Fmt.fixed(buf, m, rx, 3);
        m = Fmt.put(buf, m, ' ');
        m = Fmt.fixed(buf, m, ry, 3);
        m = Fmt.put(buf, m, ' ');
        m = Fmt.fixed(buf, m, rz, 3);
        m = Fmt.str(buf, m, " RAD/S");
        text(c, m, 1300, 1000, small);

        small.setTextAlign(Paint.Align.CENTER);
        c.drawText("A OR OK: CONTINUE OR SAVE  //  X OR LEFT / RIGHT: REDO  //  B OR BACK: EXIT  //  HOLD START + SELECT 2 S", 960, 1046, small);
        small.setTextAlign(Paint.Align.LEFT);
    }
}
