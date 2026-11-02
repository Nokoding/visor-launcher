package com.nokoding.deck;

import android.annotation.TargetApi;
import android.content.Context;
import android.hardware.BatteryState;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.lights.Light;
import android.hardware.lights.LightState;
import android.hardware.lights.LightsManager;
import android.hardware.lights.LightsRequest;
import android.os.Build;
import android.os.VibratorManager;
import android.view.InputDevice;
import android.view.KeyEvent;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 2.1 controller report: what Android actually exposes for each controller (ids, sources,
 * rumble motors, sensors, lights), plus a light test that shows the exact result or
 * exception. Nothing is guessed. The same text goes to files/controller-report.txt.
 * Only created on Android 12 (API 31) and newer.
 */
@TargetApi(31)
final class PadReport {
    static final class Line {
        final String title, body;
        Line(String title, String body) { this.title = title; this.body = body; }
    }

    final ArrayList<Line> lines = new ArrayList<>();
    /** First light with RGB control on any controller, or null. */
    Light rgb;
    int rgbDevice = -1;
    String rgbSummary = "NOT EXPOSED BY THIS BOX (COMING SOON)";
    private LightsManager.LightsSession session;

    private void add(String t, String b) { lines.add(new Line(t, b == null ? "" : b)); }

    static String hex4(int v) { return String.format(Locale.ROOT, "0x%04X", v & 0xFFFF); }

    static String sources(int s) {
        StringBuilder b = new StringBuilder();
        int[] v = {InputDevice.SOURCE_KEYBOARD, InputDevice.SOURCE_DPAD, InputDevice.SOURCE_GAMEPAD, InputDevice.SOURCE_TOUCHSCREEN,
                InputDevice.SOURCE_MOUSE, InputDevice.SOURCE_STYLUS, InputDevice.SOURCE_TRACKBALL, InputDevice.SOURCE_MOUSE_RELATIVE,
                InputDevice.SOURCE_TOUCHPAD, InputDevice.SOURCE_TOUCH_NAVIGATION, InputDevice.SOURCE_ROTARY_ENCODER,
                InputDevice.SOURCE_JOYSTICK, InputDevice.SOURCE_HDMI, InputDevice.SOURCE_SENSOR};
        String[] n = {"KEYBOARD", "DPAD", "GAMEPAD", "TOUCHSCREEN", "MOUSE", "STYLUS", "TRACKBALL", "MOUSE_RELATIVE",
                "TOUCHPAD", "TOUCH_NAVIGATION", "ROTARY", "JOYSTICK", "HDMI", "SENSOR"};
        for (int i = 0; i < v.length; i++) if ((s & v[i]) == v[i]) b.append(b.length() > 0 ? " " : "").append(n[i]);
        return b.append(String.format(Locale.ROOT, "  (0x%08X)", s)).toString();
    }

    static String lightType(int t) {
        switch (t) {
            case Light.LIGHT_TYPE_INPUT: return "INPUT";
            case Light.LIGHT_TYPE_PLAYER_ID: return "PLAYER_ID";
            case Light.LIGHT_TYPE_MICROPHONE: return "MICROPHONE";
            default: return "TYPE " + t;
        }
    }

    private static String yn(boolean b) { return b ? "YES" : "NO"; }

    static boolean wanted(InputDevice d, int padId) {
        if (d == null || d.isVirtual()) return false;
        if (d.getId() == padId) return true;
        int s = d.getSources();
        if (InputMode.has(s, InputMode.SRC_GAMEPAD) || InputMode.has(s, InputMode.SRC_JOYSTICK)) return true;
        try { return d.isExternal(); } catch (Throwable t) { return false; }
    }

    static PadReport build(int padId, boolean psSeen, String deckVersion) {
        PadReport r = new PadReport();
        r.add("REPORT", deckVersion + "  " + new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(new Date()));
        r.add("SYSTEM", "ANDROID " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")  " + Build.MANUFACTURER + " " + Build.MODEL);
        r.add("KERNEL", String.valueOf(System.getProperty("os.version")) + "  (DUALSENSE LIGHTS NEED THE hid-playstation DRIVER, LINUX 5.12+)");
        r.add("PS BUTTON SEEN BY VISOR", yn(psSeen));
        int count = 0;
        try {
            for (int id : InputDevice.getDeviceIds()) {
                InputDevice d = InputDevice.getDevice(id);
                if (!wanted(d, padId)) continue;
                count++;
                r.device(d, count, id == padId);
            }
        } catch (Throwable t) {
            r.add("DEVICES", "ERROR " + t);
        }
        if (count == 0) r.add("DEVICES", "NO CONTROLLER FOUND. PRESS A BUTTON ON IT, THEN OPEN THIS AGAIN.");
        String leds;
        try {
            String[] l = new File("/sys/class/leds").list();
            leds = l == null ? "NOT READABLE (NORMAL, SELINUX BLOCKS APPS)" : (l.length == 0 ? "EMPTY" : String.join(", ", l));
        } catch (Throwable t) {
            leds = "NOT READABLE (" + t.getClass().getSimpleName() + ")";
        }
        r.add("/SYS/CLASS/LEDS", leds);
        r.add("LIGHT BAR", r.rgb != null ? "FOUND: '" + r.rgb.getName() + "' ON DEVICE " + r.rgbDevice : r.rgbSummary);
        return r;
    }

    private void device(InputDevice d, int n, boolean current) {
        String pre = "DEVICE " + n + (current ? " (IN USE)" : "");
        add(pre, d.getName());
        String ext;
        try { ext = yn(d.isExternal()); } catch (Throwable t) { ext = "?"; }
        add(pre + " IDS", "VENDOR " + hex4(d.getVendorId()) + "  PRODUCT " + hex4(d.getProductId()) + "  ID " + d.getId() + "  EXTERNAL " + ext);
        String desc = d.getDescriptor();
        add(pre + " DESCRIPTOR", desc == null ? "" : (desc.length() > 20 ? desc.substring(0, 20) + "..." : desc));
        add(pre + " SOURCES", sources(d.getSources()));
        try {
            boolean[] k = d.hasKeys(KeyEvent.KEYCODE_BUTTON_MODE, KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_HOME);
            add(pre + " KEYS", "PS/MODE " + yn(k[0]) + "  A " + yn(k[1]) + "  START " + yn(k[2]) + "  HOME " + yn(k[3]));
        } catch (Throwable t) {
            add(pre + " KEYS", "ERROR " + t.getClass().getSimpleName());
        }
        try {
            VibratorManager vm = d.getVibratorManager();
            int[] ids = vm == null ? null : vm.getVibratorIds();
            StringBuilder b = new StringBuilder();
            b.append(ids == null ? 0 : ids.length).append(ids != null && ids.length == 1 ? " VIBRATOR" : " VIBRATORS");
            if (ids != null) for (int id : ids) b.append("  #").append(id).append(" AMPLITUDE ").append(yn(vm.getVibrator(id).hasAmplitudeControl()));
            add(pre + " RUMBLE", b.toString());
        } catch (Throwable t) {
            add(pre + " RUMBLE", "ERROR " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
        try {
            SensorManager sm = d.getSensorManager();
            List<Sensor> ss = sm == null ? null : sm.getSensorList(Sensor.TYPE_ALL);
            StringBuilder b = new StringBuilder();
            if (ss == null || ss.isEmpty()) b.append("NONE");
            else for (Sensor s : ss) b.append(b.length() > 0 ? ", " : "").append(s.getName()).append(" (TYPE ").append(s.getType()).append(')');
            add(pre + " SENSORS", b.toString());
        } catch (Throwable t) {
            add(pre + " SENSORS", "ERROR " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
        try {
            BatteryState bs = d.getBatteryState();
            add(pre + " BATTERY", bs == null || !bs.isPresent() ? "NOT REPORTED" : Math.round(bs.getCapacity() * 100) + "%  STATUS " + bs.getStatus());
        } catch (Throwable t) {
            add(pre + " BATTERY", "ERROR " + t.getClass().getSimpleName());
        }
        try {
            LightsManager lm = d.getLightsManager();
            List<Light> ls = lm == null ? null : lm.getLights();
            add(pre + " LIGHTS", lm == null ? "NO LIGHTSMANAGER" : (ls == null ? "NULL LIST" : ls.size() + " EXPOSED"));
            if (ls != null) {
                int k = 0;
                for (Light l : ls) {
                    k++;
                    add(pre + " LIGHT " + k, "NAME '" + l.getName() + "'  ID " + l.getId() + "  ORDINAL " + l.getOrdinal() + "  TYPE " + lightType(l.getType())
                            + "  RGB " + yn(l.hasRgbControl()) + "  BRIGHTNESS " + yn(l.hasBrightnessControl()));
                    if (l.hasRgbControl() && rgb == null) { rgb = l; rgbDevice = d.getId(); }
                }
                if (rgb == null && !ls.isEmpty()) rgbSummary = "NOT EXPOSED BY THIS BOX (COMING SOON). " + ls.size() + " LIGHT(S), NONE WITH RGB";
            }
        } catch (Throwable t) {
            add(pre + " LIGHTS", "ERROR " + t.getClass().getName() + ": " + t.getMessage());
        }
    }

    /** Sends one color to the RGB light. Returns the exact result or exception text. */
    String testLight(int argb) {
        if (rgb == null || rgbDevice < 0) return "NO RGB LIGHT EXPOSED";
        try {
            InputDevice d = InputDevice.getDevice(rgbDevice);
            if (d == null) return "CONTROLLER DISCONNECTED";
            LightsManager lm = d.getLightsManager();
            if (session == null) session = lm.openSession();
            session.requestLights(new LightsRequest.Builder().addLight(rgb, new LightState.Builder().setColor(argb).build()).build());
            String back;
            try {
                LightState st = lm.getLightState(rgb);
                back = st == null ? "NULL" : String.format(Locale.ROOT, "#%08X", st.getColor());
            } catch (Throwable t) {
                back = "READ FAILED " + t.getClass().getSimpleName();
            }
            return String.format(Locale.ROOT, "OK: SENT #%08X TO '%s', READ BACK %s", argb, rgb.getName(), back);
        } catch (Throwable t) {
            return "ERROR: " + t.getClass().getName() + ": " + t.getMessage();
        }
    }

    void close() {
        try { if (session != null) session.close(); } catch (Throwable ignored) {}
        session = null;
    }

    String text() {
        StringBuilder b = new StringBuilder("Visor controller report\n\n");
        for (Line l : lines) b.append(l.title).append(": ").append(l.body).append('\n');
        return b.toString();
    }

    /** @return the file path or an error line. */
    static String write(Context c, String text) {
        try {
            File dir = c.getExternalFilesDir(null);
            if (dir == null) return "NO FILES FOLDER";
            File f = new File(dir, "controller-report.txt");
            try (FileOutputStream o = new FileOutputStream(f)) { o.write(text.getBytes(StandardCharsets.UTF_8)); }
            return "Android/data/" + c.getPackageName() + "/files/controller-report.txt";
        } catch (Throwable t) {
            return "WRITE FAILED: " + t.getClass().getSimpleName();
        }
    }
}
