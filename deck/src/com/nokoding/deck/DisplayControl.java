package com.nokoding.deck;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.Process;
import android.view.Display;
import android.view.WindowManager;

import java.lang.reflect.Method;

/**
 * 2.8: changes the whole display's size and density, the way "adb shell wm size" and "wm density" do.
 * This is the DECK DISPLAY method of a game profile. It is experimental and has not run on a TV.
 *
 * What was read (Android 14 WindowManagerService): setForcedDisplaySize, clearForcedDisplaySize,
 * setForcedDisplayDensityForUser and clearForcedDisplayDensityForUser each check
 * checkCallingOrSelfPermission(WRITE_SECURE_SETTINGS) and nothing else. An app holding that permission
 * (adb shell pm grant com.nokoding.deck android.permission.WRITE_SECURE_SETTINGS) passes that check.
 * What was not read: whether Android 14's hidden API rules let an ordinary app reach IWindowManager by
 * reflection. So every call here is in a try block and reports a short reason, and the callers fall back
 * to showing the adb commands. Nothing here is needed for the rest of Deck.
 *
 * The permission IS in the manifest since 2.15, but installing the app does not give it: WRITE_SECURE_SETTINGS is protected, only adb pm grant
 * can give it, and a manifest entry for it would make lint and the installer complain for nothing.
 */
@SuppressLint({"PrivateApi", "DiscouragedPrivateApi"})
@SuppressWarnings("deprecation")
final class DisplayControl {
    private DisplayControl() {}

    static final String GRANT = "adb shell pm grant com.nokoding.deck android.permission.WRITE_SECURE_SETTINGS";
    static final String PERM = "android.permission.WRITE_SECURE_SETTINGS";

    /** True when adb pm grant was run for Deck. */
    static boolean granted(Context c) {
        try {
            return c.checkSelfPermission(PERM) == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    /** The panel's own size, or null. Display.Mode reports the real mode, not a wm size override. */
    static int[] panelSize(Context c) {
        try {
            WindowManager wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
            Display d = wm.getDefaultDisplay();
            Display.Mode m = d.getMode();
            int w = m.getPhysicalWidth(), h = m.getPhysicalHeight();
            if (w < h) { int t = w; w = h; h = t; }
            return w > 0 && h > 0 ? new int[]{w, h} : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** The density Android uses right now (a density override shows here, a size override does not). */
    static int currentDpi(Context c) {
        try {
            return c.getResources().getDisplayMetrics().densityDpi;
        } catch (Throwable t) {
            return 0;
        }
    }

    /** The size Android uses right now (an override shows here). */
    static int[] currentSize(Context c) {
        try {
            android.graphics.Point p = new android.graphics.Point();
            WindowManager wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
            wm.getDefaultDisplay().getRealSize(p);
            int w = p.x, h = p.y;
            if (w < h) { int t = w; w = h; h = t; }
            return new int[]{w, h};
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object wms() throws Exception {
        Class<?> sm = Class.forName("android.os.ServiceManager");
        Object binder = sm.getMethod("getService", String.class).invoke(null, "window");
        Class<?> stub = Class.forName("android.view.IWindowManager$Stub");
        return stub.getMethod("asInterface", IBinder.class).invoke(null, binder);
    }

    private static int userId() { return Process.myUid() / 100000; }

    private static String why(Throwable t) {
        Throwable c = t instanceof java.lang.reflect.InvocationTargetException && t.getCause() != null ? t.getCause() : t;
        String n = c.getClass().getSimpleName();
        if (c instanceof SecurityException) return "NOT ALLOWED (RUN THE GRANT COMMAND)";
        if (c instanceof NoSuchMethodException || c instanceof ClassNotFoundException || c instanceof NoSuchFieldException) return "THIS TV BOX HIDES THE CALL. USE THE PHONE HELPER";
        return n.toUpperCase(java.util.Locale.ROOT);
    }

    /**
     * Sets the size and density. Values must already be checked by DisplayRules.
     * @return null on success, otherwise a short reason in capitals
     */
    static String apply(Context c, int w, int h, int dpi) {
        if (!granted(c)) return "NEEDS THE GRANT COMMAND";
        try {
            Object s = wms();
            if (s == null) return "NO WINDOW SERVICE";
            Class<?> k = s.getClass();
            Method size = k.getMethod("setForcedDisplaySize", int.class, int.class, int.class);
            Method den = k.getMethod("setForcedDisplayDensityForUser", int.class, int.class, int.class);
            size.invoke(s, Display.DEFAULT_DISPLAY, w, h);
            den.invoke(s, Display.DEFAULT_DISPLAY, dpi, userId());
            return null;
        } catch (Throwable t) {
            return why(t);
        }
    }

    /**
     * Puts the size and density back to the panel's own values (the same as wm size reset and wm density
     * reset). Does not read any saved value, so it works with a corrupt settings file.
     * @return null on success, otherwise a short reason in capitals
     */
    static String restore(Context c) {
        if (!granted(c)) return "NEEDS THE GRANT COMMAND";
        String err = null;
        try {
            Object s = wms();
            if (s == null) return "NO WINDOW SERVICE";
            Class<?> k = s.getClass();
            try {
                k.getMethod("clearForcedDisplaySize", int.class).invoke(s, Display.DEFAULT_DISPLAY);
            } catch (Throwable t) { err = why(t); }
            try {
                k.getMethod("clearForcedDisplayDensityForUser", int.class, int.class).invoke(s, Display.DEFAULT_DISPLAY, userId());
            } catch (Throwable t) { if (err == null) err = why(t); }
        } catch (Throwable t) {
            err = why(t);
        }
        return err;
    }
}
