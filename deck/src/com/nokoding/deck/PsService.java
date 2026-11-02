package com.nokoding.deck;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.input.InputManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 2.4: the PS menu over other apps. An accessibility service is the only public way for a
 * normal app to see a key while another app is in front. It does three small things:
 *
 * - Remembers which package is in front (TYPE_WINDOW_STATE_CHANGED, package name only, never
 *   stored or logged) and checks it against one set: the apps that keep the PS button.
 * - In onKeyEvent asks PsGate whether to take the key. Closed, it only ever takes BUTTON_MODE
 *   from a gamepad; open, it takes the menu keys so the app underneath gets nothing.
 * - Shows Deck's QuickMenu in a TYPE_ACCESSIBILITY_OVERLAY window (no SYSTEM_ALERT_WINDOW).
 *   The window is display only for keys (FLAG_NOT_FOCUSABLE), so there is no focus fight;
 *   the keys come through onKeyEvent. It is built when the menu opens and freed when it closes.
 *
 * 2.6: the same filter also carries button remap. While the PS menu is closed and another app is in
 * front, onKeyEvent asks RemapGate (pure) whether a button with a saved rule is taken. A taken press
 * runs its action (a global action, an app, media, volume or one of Deck's screens), and its repeats
 * and release are taken with it. Deck reads its own keys, so the service leaves them alone while
 * Deck is in front. LEARN A BUTTON makes the service report what it sees and take nothing.
 *
 * Never trap the user: every callback is wrapped, an error resets the gate so nothing stays
 * swallowed, holding PS for 4 s lets go of the app in front, Home always reaches the system,
 * Back closes, a controller that disconnects closes the menu, and Setup has an off switch.
 */
public final class PsService extends AccessibilityService implements Engine.Host {

    /** Set while the service is connected (same process as HomeActivity). */
    static volatile PsService live;
    static final String PREF_SEEN = "psSeenSvc", PREF_LAUNCH = "psLaunch", PREF_CAPTURE = "psCapture", PREF_HOME_N = "homeSeenN", PREF_HOME_AT = "homeSeenAt";
    private static final String SYSTEMUI = "com.android.systemui";
    private static final String HOME_CLASS = "com.nokoding.deck.HomeActivity";
    private static final long LAUNCH_CHECK_MS = 2500;

    private final Handler h = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private PsGate gate;
    /** 2.6 button remap: decides every key the filter hands over while another app is in front. */
    private RemapGate remap;
    private ComboGate combos;
    private boolean quietOpen;
    private String pendingWhat = "the PS menu";
    private boolean seenSvc;
    private BroadcastReceiver pkgReceiver;
    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener = (sp, key) -> {
        if (key == null) return;
        if (key.equals(PsRules.KEY_PASS) || key.equals(PsRules.KEY_DECK) || key.equals(PsRules.KEY_GAMES)) rebuildPass();
        else if (key.equals(PREF_CAPTURE) && gate != null) gate.capture = sp.getBoolean(PREF_CAPTURE, true);
        else if (RemapPrefs.watches(key)) reloadRemap();
        else if ((key.startsWith("tv") || key.startsWith("sv"))) loadTouchVol();
    };

    // only while the menu is open (or closing)
    private WindowManager wm;
    private QuickMenu menu;
    private Engine engine;
    private Sounds sounds;
    private IconCache icons;
    private AppStore store;
    private boolean testMenu;
    private int menuDevice = -1;
    private InputManager inputs;
    private final TickScheduler ticks = new TickScheduler();
    private final TextReveal.Params textP = new TextReveal.Params();
    private final PsStick vert = new PsStick(), horiz = new PsStick();
    private String pendingLaunch, pendingLabel;

    private final InputManager.InputDeviceListener devListener = new InputManager.InputDeviceListener() {
        @Override public void onInputDeviceAdded(int id) {}
        @Override public void onInputDeviceChanged(int id) {}
        @Override public void onInputDeviceRemoved(int id) {
            if (id == menuDevice) safe(() -> closeMenu(false));
        }
    };

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onServiceConnected() {
        try {
            prefs = getSharedPreferences("deck", MODE_PRIVATE);
            gate = new PsGate(getPackageName());
            remap = new RemapGate(getPackageName());
            combos = new ComboGate(getPackageName());
            reloadRemap();
            gate.capture = prefs.getBoolean(PREF_CAPTURE, true);
            seenSvc = prefs.getBoolean(PREF_SEEN, false);
            prefs.registerOnSharedPreferenceChangeListener(prefListener);
            registerPackages();
            rebuildPass();
            live = this;
            ProfileRunner pr = ProfileRunner.get(this);
            pr.serviceFeeds = true;
            pr.setServiceUi(profileUi);
            pr.startupRestore();
            loadTouchVol();
        } catch (Throwable t) {
            live = null;
        }
    }

    @Override
    public void onInterrupt() { safe(() -> closeMenu(false)); }

    @Override
    public boolean onUnbind(Intent intent) {
        shutdown();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        shutdown();
        super.onDestroy();
    }

    private void shutdown() {
        if (live == this) live = null;
        try { ProfileRunner pr = ProfileRunner.get(this); pr.serviceFeeds = false; pr.setServiceUi(null); } catch (Throwable ignored) {}
        try { if (prefs != null) prefs.unregisterOnSharedPreferenceChangeListener(prefListener); } catch (Throwable ignored) {}
        try { if (pkgReceiver != null) unregisterReceiver(pkgReceiver); } catch (Throwable ignored) {}
        pkgReceiver = null;
        teardown();
        h.removeCallbacksAndMessages(null);
    }

    private void safe(Runnable r) {
        try { r.run(); } catch (Throwable t) { fail(); }
    }

    /** Something went wrong: drop the menu and every half press, so no key stays swallowed. */
    private void fail() {
        try { stickVol.release(); } catch (Throwable ignored) {}
        try { if (gate != null) gate.reset(); } catch (Throwable ignored) {}
        try { if (remap != null) remap.reset(); } catch (Throwable ignored) {}
        try { if (combos != null) combos.reset(); } catch (Throwable ignored) {}
        try { teardown(); } catch (Throwable ignored) {}
    }

    // ------------------------------------------------------------------ pass-through set

    private void registerPackages() {
        pkgReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) { rebuildPass(); }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addDataScheme("package");
        try {
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(pkgReceiver, f, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(pkgReceiver, f);
        } catch (Throwable t) {
            pkgReceiver = null;
        }
    }

    /**
     * Games plus the user's marks, built off the main thread (the package scan is binder work)
     * and swapped in as one set. The key filter then only does one contains().
     */
    private void rebuildPass() {
        final SharedPreferences p = prefs;
        final PsGate g = gate;
        if (p == null || g == null) return;
        final Context app = getApplicationContext();
        Thread t = new Thread(() -> {
            try {
                PsRules r = PsRules.load(p.getString(PsRules.KEY_PASS, ""), p.getString(PsRules.KEY_DECK, ""), p.getBoolean(PsRules.KEY_GAMES, true));
                Set<String> eff = Collections.unmodifiableSet(r.effective(games(app)));
                g.pass = eff;
                RemapGate rg = remap;
                if (rg != null) rg.pass = eff;
            } catch (Throwable ignored) {}
        }, "deck-ps-pass");
        t.setDaemon(true);
        t.start();
    }

    /** Packages Android marks as games. */
    @SuppressWarnings("deprecation")
    static ArrayList<String> games(Context c) {
        ArrayList<String> out = new ArrayList<>();
        try {
            List<ApplicationInfo> all = c.getPackageManager().getInstalledApplications(0);
            for (ApplicationInfo ai : all) if (ai != null && PsRules.isGame(ai.category, ai.flags)) out.add(ai.packageName);
        } catch (Throwable ignored) {}
        return out;
    }

    @SuppressWarnings("deprecation")
    static boolean isGame(PackageManager pm, String pkg) {
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            return PsRules.isGame(ai.category, ai.flags);
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------------------ events

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        try {
            if (gate == null || e == null || e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
            CharSequence pc = e.getPackageName();
            if (pc == null) return;
            String pkg = pc.toString();
            if (pkg.equals(SYSTEMUI)) return;   // volume and notification panels float over the real app
            if (pkg.equals(getPackageName())) {
                // Deck's own overlay window is not "Visor in front"; only its home screen is
                CharSequence cls = e.getClassName();
                if (cls == null || !HOME_CLASS.contentEquals(cls)) return;
            }
            if (pkg.equals(pendingLaunch)) launchSeen(true);
            try { ProfileRunner.get(this).onForeground(pkg); } catch (Throwable ignored) {}
            if (!pkg.equals(getPackageName())) {
                // 2.12 performance mode: a game came to the front (also when it was not started from Deck)
                final String pm = PerfRunner.consider(this, prefs, pkg);
                if (pm != null) h.post(() -> toast(pm));
            }
            if (gate.setForeground(pkg) && menu != null && !menu.closing && !(testMenu && pkg.equals(getPackageName()))) closeMenu(false);
        } catch (Throwable t) {
            fail();
        }
    }

    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        PsGate g = gate;
        if (g == null || e == null) return false;
        try {
            int code = e.getKeyCode(), act = e.getAction();
            if (act != KeyEvent.ACTION_DOWN && act != KeyEvent.ACTION_UP) return false;
            boolean down = act == KeyEvent.ACTION_DOWN;
            if (stickVol.enabled() && stickVol.onKey(e)) { applyMotion(); h.removeCallbacks(stickCheck); if (stickVol.held()) h.postDelayed(stickCheck, 5000); }   // 2.11: the hold key is tracked first, before anything can return early, and never taken
            if (code == KeyEvent.KEYCODE_BUTTON_MODE && down && !seenSvc) {
                seenSvc = true;
                prefs.edit().putBoolean(PREF_SEEN, true).apply();
            }
            if (code == KeyEvent.KEYCODE_HOME && down && e.getRepeatCount() == 0) {
                // 2.17 HOME KEY CHECK: proof that the key filter really sees Home on this box
                prefs.edit().putInt(PREF_HOME_N, prefs.getInt(PREF_HOME_N, 0) + 1).putLong(PREF_HOME_AT, System.currentTimeMillis()).apply();
            }
            // 2.6 LEARN A BUTTON is open: report what the filter sees and take nothing
            RemapLearnView lv = RemapLearnView.learning;
            if (lv != null) {
                if (down && e.getRepeatCount() == 0) lv.fromService(e);
                return false;
            }
            if (keepView != null) {
                // 2.8: the KEEP or REVERT prompt of a game profile owns A, B, OK and Back while it shows
                if (down && e.getRepeatCount() == 0) {
                    if (code == KeyEvent.KEYCODE_BUTTON_A || code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER) ProfileRunner.get(this).keep();
                    else if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_BUTTON_B || code == KeyEvent.KEYCODE_ESCAPE) ProfileRunner.get(this).revert();
                }
                return true;
            }
            if (!g.menuOpen && touchVol.enabled() && touchVol.onKey(this, e)) return true;
            // fast path: nothing open, not the PS button, not the end of a press Deck took.
            // 2.6: the remap decides here, and only here, while the PS menu is closed.
            if (!g.menuOpen && code != KeyEvent.KEYCODE_BUTTON_MODE && !g.eaten(code)) return remapKey(e, g, down);
            RemapGate rg = remap;
            if (g.menuOpen && rg != null && rg.busy()) rg.reset();
            int src = e.getSource();
            boolean pad = InputMode.has(src, InputDevice.SOURCE_GAMEPAD) || InputMode.has(src, InputDevice.SOURCE_JOYSTICK);
            long now = SystemClock.uptimeMillis();
            boolean wasOpen = g.menuOpen;
            final int a = g.onKey(code, down, e.getRepeatCount(), pad, now);
            if (code == KeyEvent.KEYCODE_BUTTON_MODE && down && e.getRepeatCount() == 0 && PsGate.consumes(a)) {
                h.removeCallbacks(killCheck);
                h.postDelayed(killCheck, PsKill.HOLD_MS + 20);
            }
            if (a == PsGate.OPEN) {
                final int dev = e.getDeviceId(), vendor = vendorOf(e);
                h.post(() -> safe(() -> openMenu(false, dev, vendor, pad)));
            } else if (a != PsGate.PASS && a != PsGate.SWALLOW) {
                h.post(() -> safe(() -> act(a)));
            } else if (a == PsGate.PASS && wasOpen && isVolume(code) && !down) {
                h.postDelayed(() -> safe(() -> { if (menu != null) { menu.readVolume(); menu.invalidate(); } }), 250);
            }
            return PsGate.consumes(a);
        } catch (Throwable t) {
            h.post(this::fail);
            return false;
        }
    }

    // ------------------------------------------------------------------ 2.6 button remap

    private void reloadRemap() {
        RemapGate rg = remap;
        SharedPreferences p = prefs;
        if (rg == null || p == null) return;
        rg.table = RemapPrefs.liveTable(p);
        rg.flags = RemapPrefs.flags(p) | RemapGate.F_SERVICE;
        ComboGate cg = combos;
        if (cg != null) {
            cg.table = RemapPrefs.liveCombos(p);
            cg.flags = RemapPrefs.flags(p);
        }
    }

    /**
     * One key while the PS menu is closed. The service takes a button only when RemapGate says so:
     * it has a rule, the app in front is known and is not Deck (Deck reads its own keys), and the
     * advanced switches allow it. Everything else, and any error, passes.
     */
    private boolean remapKey(KeyEvent e, PsGate g, boolean down) {
        RemapGate rg = remap;
        if (rg == null) return false;
        String fg = g.fg;
        if (fg == null) return false;
        int code = e.getKeyCode();
        long key = code == 0 ? RemapKey.pack(0, e.getScanCode(), vendorOf(e), productOf(e)) : RemapKey.pack(code, 0, 0, 0);
        if (!RemapKey.valid(key)) return false;
        if (fg.equals(getPackageName()) && !rg.holds(key)) return false;
        long now = SystemClock.uptimeMillis();
        // 2.9 combos first: the second button of a held pair is taken
        ComboGate cg = combos;
        if (cg != null && cg.table.size() > 0) {
            int c = cg.onKey(key, down, e.getRepeatCount(), now, fg);
            if (c == ComboGate.ACT) {
                final int ca = cg.act;
                final String carg = cg.arg;
                h.post(() -> safe(() -> runRemap(ca, carg)));
                return true;
            }
            if (c == ComboGate.SWALLOW) return true;
        }
        int r = rg.onKey(key, down, e.getRepeatCount(), now, fg);
        if (rg.takeHomeEscape()) { RemapPrefs.homeEscape(prefs); reloadRemap(); toast("Home remap switched off"); }
        if (rg.busy()) armRemap(now);
        switch (r) {
            case RemapGate.SWALLOW:
                return true;
            case RemapGate.ACT: {
                final int a = rg.act;
                final String arg = rg.arg;
                h.post(() -> safe(() -> runRemap(a, arg)));
                return true;
            }
            default:
                return false;
        }
    }

    private void armRemap(long now) {
        RemapGate rg = remap;
        h.removeCallbacks(remapTick);
        long d = rg == null ? -1 : rg.nextDelay(now);
        if (d >= 0) h.postDelayed(remapTick, d + 5);
    }

    /** Long presses and the Back escape come from this timer, because the key filter sends no repeats. */
    private final Runnable remapTick = () -> safe(() -> {
        RemapGate rg = remap;
        PsGate g = gate;
        if (rg == null || g == null) return;
        long now = SystemClock.uptimeMillis();
        int r;
        while ((r = rg.poll(now, g.fg)) != RemapGate.NONE) {
            if (r == RemapGate.ESCAPE) {
                if (RemapPrefs.escape(prefs)) toast("Navigation remaps cleared");
                reloadRemap();
            } else {
                runRemap(rg.act, rg.arg);
            }
        }
        armRemap(now);
    });

    /** The remap actions that are Android global actions. Also used by Deck's own window when the service is on. */
    boolean runGlobal(int a) {
        try {
            switch (a) {
                case RemapAction.HOME: return performGlobalAction(GLOBAL_ACTION_HOME);
                case RemapAction.BACK: return performGlobalAction(GLOBAL_ACTION_BACK);
                case RemapAction.RECENTS: return performGlobalAction(GLOBAL_ACTION_RECENTS);
                case RemapAction.QUICK: return performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS);
                case RemapAction.POWER_MENU: return performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);
                case RemapAction.SCREENSHOT: return performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT);
                default: return false;
            }
        } catch (Throwable t) {
            return false;
        }
    }

    private void runRemap(int a, String arg) {
        boolean ok = true;
        switch (a) {
            case RemapAction.HOME: case RemapAction.BACK: case RemapAction.RECENTS: case RemapAction.QUICK:
            case RemapAction.POWER_MENU: case RemapAction.SCREENSHOT:
                ok = runGlobal(a);
                break;
            case RemapAction.TOGGLE_PROFILE: {
                // 2.8: the chord that turns the game profile of the app in front on or off
                String fg = gate == null ? null : gate.fg;
                toast(fg == null || fg.equals(getPackageName()) ? "Press it while the game is in front" : ProfileRunner.get(this).toggle(fg).toLowerCase(java.util.Locale.ROOT));
                break;
            }
            case RemapAction.EXIT_GAME: {
                String r = PerfRunner.exitGame(this, prefs, gate == null ? null : gate.fg);
                toast(r == null ? "No game to exit" : "Closing the game");
                break;
            }
            case RemapAction.PERF_TOGGLE:
                PerfRunner.toggle(prefs);
                toast(PerfRunner.mode(prefs) == PerfMode.OFF ? "Performance mode off" : "Performance mode on");
                break;
            case RemapAction.APP: ok = launchPackage(arg); break;
            case RemapAction.DECK_HOME: ok = openDeck(null); break;
            case RemapAction.DECK_SETUP: ok = openDeck(DeckCmd.NAMES[DeckCmd.OPEN_SETUP]); break;
            case RemapAction.INPUT_PICKER: ok = openDeck(DeckCmd.NAMES[DeckCmd.OPEN_INPUTS]); break;
            case RemapAction.DECK_PS: ok = openFromRemap(); break;
            case RemapAction.PREV_APP: ok = previousApp(); break;
            case RemapAction.PLAY_PAUSE: case RemapAction.NEXT: case RemapAction.PREV: case RemapAction.STOP:
                ok = RemapRunner.media(this, a);
                break;
            case RemapAction.VOL_UP: case RemapAction.VOL_DOWN: case RemapAction.MUTE:
                ok = RemapRunner.volume(this, a);
                break;
            case RemapAction.ANDROID_SETTINGS: ok = RemapRunner.androidSettings(this); break;
            default: break;
        }
        if (!ok) toast("Could not run " + RemapAction.name(a) + " from the remote button");
    }

    private boolean launchPackage(String pkg) {
        if (!PsRules.plainPackage(pkg)) return false;
        Intent li = null;
        try {
            PackageManager pm = getPackageManager();
            li = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (li == null) li = pm.getLaunchIntentForPackage(pkg);
        } catch (Throwable ignored) {}
        if (li == null) return false;
        pendingWhat = "a remote button";
        return start(li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED), pkg, pkg);
    }

    /** Brings Deck forward: its home screen, or a DeckCmd. */
    private boolean openDeck(String cmd) {
        Intent i = new Intent(Intent.ACTION_MAIN).setClassName(getPackageName(), HOME_CLASS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (cmd == null) i.addCategory(Intent.CATEGORY_HOME); else i.putExtra(DeckCmd.EXTRA, cmd);
        pendingWhat = "a remote button";
        return start(i, getPackageName(), "Visor");
    }

    private boolean previousApp() {
        ArrayList<String> keys = Recents.pick(Recents.load(prefs.getString("recents", "")).list, Recents.MAX_KEEP, null, null);
        String k = RemapAction.previousApp(keys, gate == null ? null : gate.fg, getPackageName());
        if (k == null) { toast("No previous app yet. Visor remembers the apps you open with it."); return true; }
        android.content.ComponentName cn = android.content.ComponentName.unflattenFromString(k);
        return cn != null && launchPackage(cn.getPackageName());
    }

    /** DECK_PS outside Deck: the PS menu over the app in front, without the OVERLAY TEST tag. */
    private boolean openFromRemap() {
        try {
            if (menu != null) return true;
            quietOpen = true;
            openMenu(true, -1, 0, false);
            return menu != null;
        } catch (Throwable t) {
            fail();
            return false;
        } finally {
            quietOpen = false;
        }
    }

    private static int productOf(KeyEvent e) {
        try {
            InputDevice d = e.getDevice();
            return d == null ? 0 : d.getProductId();
        } catch (Throwable t) {
            return 0;
        }
    }

    private static boolean isVolume(int code) {
        return code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN || code == KeyEvent.KEYCODE_VOLUME_MUTE;
    }

    private static int vendorOf(KeyEvent e) {
        try {
            InputDevice d = e.getDevice();
            return d == null ? 0 : d.getVendorId();
        } catch (Throwable t) {
            return 0;
        }
    }

    /** Kill switch: PS still held 4 s after Deck took it. */
    private final Runnable killCheck = () -> safe(() -> {
        if (gate == null || !gate.kill.due(SystemClock.uptimeMillis())) return;
        boolean had = menu != null;
        gate.killSwitch();
        if (had) closeMenu(false);
        toast("PS button left to this app until you leave it");
    });

    /** Gamepad sticks and the D-pad hat (a DualSense D-pad is axes, not keys). Android 14 and newer, only while open. */
    @Override
    public void onMotionEvent(MotionEvent e) {
        try {
            if (e == null) return;
            // 2.10 stick volume: the right stick while the hold key is down
            if (stickVol.held() && !(menu != null && !menu.closing) && stickVol.onMotion(this, e)) return;
            // 2.8 touchpad volume: touchpad and pointer events, while it is switched on
            if (touchVol.enabled() && !InputMode.has(e.getSource(), InputDevice.SOURCE_JOYSTICK)) { touchVol.onMotion(this, e); return; }
            if (menu == null || menu.closing) return;
            if (!InputMode.has(e.getSource(), InputDevice.SOURCE_JOYSTICK)) return;
            long now = SystemClock.uptimeMillis();
            float hy = e.getAxisValue(MotionEvent.AXIS_HAT_Y), hx = e.getAxisValue(MotionEvent.AXIS_HAT_X);
            float ly = e.getAxisValue(MotionEvent.AXIS_Y), lx = e.getAxisValue(MotionEvent.AXIS_X);
            float ry = e.getAxisValue(MotionEvent.AXIS_RZ);
            float vy = Math.abs(hy) > 0.5f ? hy : (Math.abs(ly) >= Math.abs(ry) ? ly : ry);
            float vx = Math.abs(hx) > 0.5f ? hx : lx;
            int dy = vert.feed(vy, now), dx = horiz.feed(vx, now);
            if (dy != 0) act(dy < 0 ? PsGate.MOVE_UP : PsGate.MOVE_DOWN);
            if (dx != 0) act(dx < 0 ? PsGate.LEFT : PsGate.RIGHT);
            h.removeCallbacks(stickRepeat);
            if (vert.dir() != 0 || horiz.dir() != 0) h.postDelayed(stickRepeat, 50);
        } catch (Throwable t) {
            fail();
        }
    }

    private final Runnable stickRepeat = new Runnable() {
        @Override public void run() {
            safe(() -> {
                if (menu == null || menu.closing) return;
                long now = SystemClock.uptimeMillis();
                int dy = vert.poll(now), dx = horiz.poll(now);
                if (dy != 0) act(dy < 0 ? PsGate.MOVE_UP : PsGate.MOVE_DOWN);
                if (dx != 0) act(dx < 0 ? PsGate.LEFT : PsGate.RIGHT);
                if (vert.dir() != 0 || horiz.dir() != 0) h.postDelayed(this, 50);
            });
        }
    };

    /** Asks Android for raw joystick events only while the menu is open, so games keep their sticks. */
    private void captureSticks(boolean on) {
        sticksOn = on;
        applyMotion();
        vert.reset();
        horiz.reset();
    }

    // ------------------------------------------------------------------ menu

    /** Setup's TEST PS OVERLAY: opens the overlay over Deck itself. @return false if it could not. */
    boolean openTest() {
        try {
            if (menu != null) return true;
            openMenu(true, -1, 0, false);
            return menu != null;
        } catch (Throwable t) {
            fail();
            return false;
        }
    }

    private void openMenu(boolean test, int device, int vendor, boolean pad) {
        if (gate == null) return;
        if (menu != null) {
            if (menu.closing) teardown(); else return;
        }
        if (!gate.menuOpen && !test) return;   // closed again before we got here
        testMenu = test;
        menuDevice = device;
        Ui.loadFonts(getAssets());
        initMetrics();
        HomeActivity.themeFromPrefs(prefs);
        boolean reduce = prefs.getBoolean("reduce", false);
        AnimSpec anim = AnimConfig.flyout();
        anim.load(new AnimSpec.Store() {
            @Override public String get(String k) { try { return prefs.getString(k, null); } catch (Throwable t) { return null; } }
            @Override public void put(String k, String v) {}
            @Override public void remove(String k) {}
        });
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        engine = new Engine(this);
        engine.start();
        sounds = new Sounds(this);
        sounds.master = Math.max(0, Math.min(100, prefs.getInt("volume", 70))) / 100f;
        sounds.mute = prefs.getBoolean("mute", false);
        sounds.ensure();
        String pack = prefs.getString("soundPack", "");
        if (pack != null && !pack.isEmpty()) sounds.loadPack(pack, false);
        store = new AppStore(this, prefs);
        store.loadCached();
        icons = new IconCache(this);
        try {
            java.io.File ext = getExternalFilesDir(null);
            icons.overrideDir = ext == null ? null : new java.io.File(ext, "icons");
        } catch (Throwable ignored) {}
        String ip = prefs.getString("iconPack", "");
        icons.iconPack = ip == null ? "" : ip;
        icons.overrideOff = new HashSet<>(OrderList.splitSet(prefs.getString("iconOff", "")));
        IconCache.originalMode = prefs.getInt("iconMode", 0) == 1;
        icons.setListener((key, ic) -> { if (menu != null) menu.invalidate(); });

        QuickMenu q = new QuickMenu(this, ccHost);
        q.effects = !reduce;
        q.gamepad = pad;
        q.style = InputMode.style(vendor);
        AnimConfig.reveal(anim, AnimConfig.QUICK, reduce, textP);
        final boolean tickSound = AnimConfig.sound(anim);
        q.anim(engine, ticks, rate -> { if (sounds != null && tickSound) sounds.play(SoundMap.TYPE_TICK, rate); },
                textP, AnimConfig.glass(anim), AnimConfig.rtl(anim));
        AnimConfig.flyout(anim, AnimConfig.QUICK, reduce, q.fa.p);
        if (test && !quietOpen) q.tag("OVERLAY TEST");
        q.setOnTouchListener((v, ev) -> {
            if (ev.getActionMasked() != MotionEvent.ACTION_UP) return true;
            v.performClick();
            safe(() -> {
                if (menu == null || menu.closing) return;
                int r = menu.hit(ev.getX(), ev.getY());
                if (r >= 0) { menu.focusRow(r); act(PsGate.OK); }
                else if (r <= -QuickMenu.TAB_HIT) { if (menu.tabClick(-(r + QuickMenu.TAB_HIT))) play(SoundMap.TOGGLE); }
                else if (r == -1) closeMenu(true);
            });
            return true;
        });
        q.setOnHoverListener((v, ev) -> {
            if (menu != null && !menu.closing) {
                int r = menu.hit(ev.getX(), ev.getY());
                if (r >= 0 && menu.focusRow(r) && sounds != null) sounds.play(SoundMap.MOVE);
            }
            return false;
        });

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.setTitle("Visor PS menu");
        wm.addView(q, lp);
        menu = q;
        if (test) gate.opened();
        q.startOpen(engine.now());
        engine.kick();
        sounds.playSoon(tickSound ? SoundMap.FLYOUT_OPEN : SoundMap.MENU_OPEN);
        captureSticks(true);
        try {
            inputs = (InputManager) getSystemService(Context.INPUT_SERVICE);
            if (inputs != null) inputs.registerInputDeviceListener(devListener, h);
        } catch (Throwable ignored) { inputs = null; }
        h.removeCallbacks(safetyTeardown);
    }

    @SuppressWarnings("deprecation")
    private void initMetrics() {
        int w = 1920, hh = 1080;
        try {
            WindowManager m = (WindowManager) getSystemService(WINDOW_SERVICE);
            if (Build.VERSION.SDK_INT >= 30) {
                Rect b = m.getMaximumWindowMetrics().getBounds();
                w = b.width(); hh = b.height();
            } else {
                DisplayMetrics dm = new DisplayMetrics();
                m.getDefaultDisplay().getRealMetrics(dm);
                w = dm.widthPixels; hh = dm.heightPixels;
            }
        } catch (Throwable ignored) {}
        if (w < hh) { int t = w; w = hh; hh = t; }
        Ui.init(w, hh);
    }

    private void act(int a) {
        QuickMenu q = menu;
        if (q == null || q.closing) {
            if (a == PsGate.CLOSE || a == PsGate.CLOSE_PASS) closeMenu(false);
            return;
        }
        switch (a) {
            case PsGate.CLOSE:
                if (q.back()) { play(SoundMap.BACK); break; }   // 2.8: B leaves the app actions page first
                closeMenu(true);
                break;
            case PsGate.TAB_PREV: case PsGate.TAB_NEXT:
                q.finishAnim();
                if (q.tab(a == PsGate.TAB_PREV ? -1 : 1)) play(SoundMap.TOGGLE);
                break;
            case PsGate.CLOSE_PASS: closeMenu(false); break;
            case PsGate.MOVE_UP: case PsGate.MOVE_DOWN:
                q.finishAnim();
                if (q.move(a == PsGate.MOVE_UP ? -1 : 1)) play(SoundMap.MOVE);
                break;
            case PsGate.LEFT: case PsGate.RIGHT:
                q.finishAnim();
                if (q.change(a == PsGate.LEFT ? -1 : 1)) play(SoundMap.TOGGLE);
                break;
            case PsGate.OK:
                q.finishAnim();
                play(SoundMap.SELECT);
                q.ok();
                break;
            case PsGate.SETUP:
                openSetup();
                break;
            default: break;
        }
    }

    private void play(int ev) { if (sounds != null) sounds.play(ev); }

    void closeMenu(boolean sound) {
        if (gate != null) gate.closed();
        captureSticks(false);
        try { if (inputs != null) inputs.unregisterInputDeviceListener(devListener); } catch (Throwable ignored) {}
        inputs = null;
        menuDevice = -1;
        h.removeCallbacks(stickRepeat);
        QuickMenu q = menu;
        if (q == null || q.closing) return;
        q.startClose(engine != null ? engine.now() : SystemClock.uptimeMillis());
        if (sound) play(q.fa.p.instant ? SoundMap.MENU_CLOSE : SoundMap.FLYOUT_CLOSE);
        if (q.gone()) teardown();
        else {
            if (engine != null) engine.kick();
            h.removeCallbacks(safetyTeardown);
            h.postDelayed(safetyTeardown, 1500);   // in case frames stop coming
        }
    }

    private final Runnable safetyTeardown = this::teardown;

    /** Removes the window and frees everything the menu used. The sound pool goes a moment later so the close sound finishes. */
    private void teardown() {
        if (gate != null) gate.closed();
        captureSticks(false);
        try { if (inputs != null) inputs.unregisterInputDeviceListener(devListener); } catch (Throwable ignored) {}
        inputs = null;
        h.removeCallbacks(safetyTeardown);
        h.removeCallbacks(stickRepeat);
        QuickMenu q = menu;
        menu = null;
        testMenu = false;
        if (q != null && wm != null) { try { wm.removeViewImmediate(q); } catch (Throwable ignored) {} }
        if (engine != null) { engine.stop(); engine = null; }
        if (icons != null) { icons.shutdown(); icons = null; }
        store = null;
        final Sounds s = sounds;
        sounds = null;
        if (s != null) h.postDelayed(s::release, 700);
    }

    // ------------------------------------------------------------------ Engine.Host

    @Override
    public void onFrame(long now, float dt) {
        QuickMenu q = menu;
        if (q == null) return;
        q.stepAnim(now);
        if (q.gone()) h.post(this::teardown);
    }

    @Override
    public boolean wantsFrames(long now) { return menu != null && (menu.animating() || menu.closing); }

    @Override
    public boolean onSlowFrames() { return false; }

    // ------------------------------------------------------------------ 2.8 control center host

    private final CcHost ccHost = new CcHost(new CcHost.Env() {
        @Override public Context ctx() { return PsService.this; }
        @Override public SharedPreferences prefs() { return prefs; }
        @Override public AppStore store() { return store; }
        @Override public IconCache.Icon icon(AppStore.App a) {
            if (icons == null || a == null) return null;
            IconCache.Icon ic = icons.get(a.key);
            if (ic == null) icons.request(a);
            return ic;
        }
        @Override public String foreground() {
            String fg = gate == null ? null : gate.fg;
            return fg == null || fg.equals(getPackageName()) ? null : fg;
        }
        @Override public boolean service() { return true; }
        @Override public boolean global(int action) { return performGlobalAction(action); }
        @Override public boolean forceStop(String pkg) { return PsService.this.forceStop(pkg); }
        @Override public void closeThen(Runnable r, long ms) {
            closeMenu(false);
            h.postDelayed(() -> safe(r), ms);
        }
        @Override public void startThenClose(Runnable r) {
            // Start while the overlay window is still on screen: a visible window is one of Android's
            // exemptions for starting an activity from the background.
            pendingWhat = "the PS menu";
            safe(r);
            closeMenu(false);
        }
        @Override public void setup() { openSetup(); }
        @Override public void close() { closeMenu(true); }
        @Override public boolean live() { return false; }
        @Override public boolean liveOn(String id) { return false; }
        @Override public String liveText(String id) { return null; }
        @Override public String liveAct(String id) { return null; }
        @Override public String profile(String pkg) {
            GameProfiles.Profile g = ProfileRunner.get(PsService.this).profiles().get(pkg);
            if (g == null) return "NO PROFILE: SET ONE IN VISOR, SETUP, SYSTEM";
            if (g.method != GameProfiles.M_DISPLAY_DECK) return ProfileCmd.adb(ProfileCmd.on(g.method, pkg, g.scalePct, DisplayState.parse(prefs.getString(ProfileRunner.PREF_ORIG, "")))[0]);
            return ProfileRunner.get(PsService.this).toggle(pkg);
        }
        @Override public String profileQuick() {
            String fg = gate == null ? null : gate.fg;
            if (fg == null || fg.equals(getPackageName())) return "OPEN AN APP FIRST";
            return ProfileRunner.get(PsService.this).toggle(fg);
        }
        @Override public String restoreDisplay() { return ProfileRunner.get(PsService.this).restoreNow(); }
        @Override public String profileText() {
            int n = ProfileRunner.get(PsService.this).profiles().count();
            return n == 0 ? "NONE SET" : n + " SET";
        }
        @Override public String restartDeck() {
            closeMenu(false);
            RemapRunner.deck(PsService.this, null);
            return null;
        }
        @Override public void toast(String t) { PsService.this.toast(t); }
    });

    boolean global(int action) {
        try { return performGlobalAction(action); } catch (Throwable t) { return false; }
    }

    // ---- experimental FORCE STOP: App Info page, the Force stop button, the confirm button

    private String fsPkg;
    private int fsStage;
    private long fsStart;

    private static final class FsNode implements ForceStopMatcher.Node {
        final android.view.accessibility.AccessibilityNodeInfo i;
        FsNode(android.view.accessibility.AccessibilityNodeInfo i) { this.i = i; }
        @Override public String id() { return i.getViewIdResourceName(); }
        @Override public String text() { CharSequence t = i.getText(); return t == null ? null : t.toString(); }
        @Override public String desc() { CharSequence t = i.getContentDescription(); return t == null ? null : t.toString(); }
        @Override public boolean clickable() { return i.isClickable(); }
        @Override public boolean enabled() { return i.isEnabled(); }
        @Override public int childCount() { return i.getChildCount(); }
        @Override public ForceStopMatcher.Node child(int k) {
            android.view.accessibility.AccessibilityNodeInfo c = i.getChild(k);
            return c == null ? null : new FsNode(c);
        }
    }

    /**
     * Opens the app's App Info page and presses Force stop and its confirm button for you. Experimental: Google
     * TV's Settings differs between builds, so it matches by view id first and text second, and gives up with a
     * toast showing the adb line when the page looks different. The node tree is read only while this runs.
     */
    boolean forceStop(String pkg) {
        if (!KillCmd.validPackage(pkg) || pkg.equals(getPackageName())) return false;
        try { startActivity(TaskSource.appInfoIntent(pkg)); } catch (Throwable t) { return false; }
        fsPkg = pkg;
        fsStage = 0;
        fsStart = SystemClock.uptimeMillis();
        h.removeCallbacks(fsPoll);
        h.postDelayed(fsPoll, 800);
        return true;
    }

    private final Runnable fsPoll = () -> safe(this::fsStep);

    private void fsDone(String msg) {
        h.removeCallbacks(fsPoll);
        String pkg = fsPkg;
        fsPkg = null;
        if (msg != null) toast(msg);
        if (pkg != null) h.postDelayed(() -> safe(() -> performGlobalAction(GLOBAL_ACTION_HOME)), 700);
    }

    private void fsStep() {
        if (fsPkg == null) return;
        if (SystemClock.uptimeMillis() - fsStart > 10000) {
            fsDone("Force stop did not work here. From your phone: " + KillCmd.forceStopAdb(fsPkg));
            return;
        }
        android.view.accessibility.AccessibilityNodeInfo root = null;
        try { root = getRootInActiveWindow(); } catch (Throwable ignored) {}
        if (root == null) { h.postDelayed(fsPoll, 500); return; }
        ForceStopMatcher.Node n = new FsNode(root);
        int st = ForceStopMatcher.state(n);
        if (st == ForceStopMatcher.ALREADY_STOPPED) { fsDone("Already stopped"); return; }
        if (st == ForceStopMatcher.APP_INFO && fsStage == 0) {
            ForceStopMatcher.Node b = ForceStopMatcher.findForceStop(n);
            if (b instanceof FsNode && ((FsNode) b).i.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) fsStage = 1;
        } else if (st == ForceStopMatcher.CONFIRM && fsStage <= 1) {
            ForceStopMatcher.Node b = ForceStopMatcher.findConfirm(n);
            if (b instanceof FsNode && ((FsNode) b).i.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) {
                fsDone("Force stop pressed");
                return;
            }
        }
        h.postDelayed(fsPoll, 600);
    }

    // ---- KEEP or REVERT prompt of a game profile, over the app in front

    private KeepView keepView;
    private final ProfileRunner.Ui profileUi = new ProfileRunner.Ui() {
        @Override public void showKeep(GameProfiles.Profile pr) {
            safe(() -> {
                if (keepView != null) return;
                if (wm == null) wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                Ui.loadFonts(getAssets());
                initMetrics();
                HomeActivity.themeFromPrefs(prefs);
                KeepView k = new KeepView(PsService.this);
                k.set(pr.pkg.toUpperCase(java.util.Locale.ROOT) + "  " + pr.scalePct + " PERCENT", DisplayRules.KEEP_SECONDS, true);
                WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                        PixelFormat.TRANSLUCENT);
                lp.setTitle("Visor keep display");
                wm.addView(k, lp);
                keepView = k;
            });
        }
        @Override public void keepTick(int seconds) { if (keepView != null) keepView.setSeconds(seconds); }
        @Override public void hideKeep() {
            KeepView k = keepView;
            keepView = null;
            if (k != null && wm != null) { try { wm.removeViewImmediate(k); } catch (Throwable ignored) {} }
        }
        @Override public void toast(String t) { PsService.this.toast(t); }
    };

    // ---- touchpad volume

    private final TouchVolInput touchVol = new TouchVolInput();
    private final StickVolInput stickVol = new StickVolInput();
    private boolean sticksOn;

    private void loadTouchVol() {
        if (prefs == null) return;
        touchVol.load(prefs);
        stickVol.load(prefs);
        applyMotion();
    }

    /** While the hold key is down: if its up was lost, let go (HoldKey) and stop asking for the stick. */
    private final Runnable stickCheck = new Runnable() {
        @Override public void run() {
            safe(() -> {
                if (stickVol.held()) h.postDelayed(this, 5000);
                else applyMotion();
            });
        }
    };

    /** Joystick events while the menu is open, touchpad and pointer events while TOUCHPAD VOLUME is on. */
    private void applyMotion() {
        if (Build.VERSION.SDK_INT < 34) return;
        try {
            AccessibilityServiceInfo info = getServiceInfo();
            if (info == null) return;
            // Android rebuilds its input filter when the set of features changes (any motion source on or off), and the
            // key up that follows can then be lost, which made a hold key act like a toggle. So with stick volume or
            // touchpad volume on, a source no game pad produces (a trackball) stays requested all the time, and only
            // the joystick part comes and goes with the hold key. That keeps the feature set the same.
            int base = stickVol.enabled() || touchVol.enabled() ? InputDevice.SOURCE_TRACKBALL : 0;
            int rest = (sticksOn || stickVol.held() ? InputDevice.SOURCE_JOYSTICK : 0) | (touchVol.enabled() ? TouchVolInput.sources() : 0);
            if (!setSources(info, base | rest) && base != 0) setSources(info, rest);   // if Android refuses the placeholder, go without it
        } catch (Throwable ignored) {}
    }

    private boolean setSources(AccessibilityServiceInfo info, int want) {
        if (Build.VERSION.SDK_INT < 34) return false;
        try {
            if (info.getMotionEventSources() == want) return true;
            info.setMotionEventSources(want);
            setServiceInfo(info);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** startActivity plus a check that the app really came to the front (a blocked background start fails silently). */
    private boolean start(Intent i, String pkg, String label) {
        try {
            startActivity(i);
        } catch (Throwable t) {
            return false;
        }
        if (gate != null && pkg.equals(gate.fg)) return true;   // already in front, no window change will come
        pendingLaunch = pkg;
        pendingLabel = label;
        h.removeCallbacks(launchCheck);
        h.postDelayed(launchCheck, LAUNCH_CHECK_MS);
        return true;
    }

    private final Runnable launchCheck = () -> safe(() -> launchSeen(false));

    private void launchSeen(boolean ok) {
        if (pendingLaunch == null) return;
        String label = pendingLabel, what = pendingWhat;
        pendingLaunch = null;
        pendingLabel = null;
        pendingWhat = "the PS menu";
        h.removeCallbacks(launchCheck);
        prefs.edit().putString(PREF_LAUNCH, ok ? "ok" : "blocked").apply();
        if (!ok) toast("Android did not open " + (label == null ? "the app" : label) + " from " + what + ". Setup, PAD, PS OVERLAY has the details.");
    }

    void openSetup() {
        pendingWhat = "the PS menu";
        Intent i = new Intent(Intent.ACTION_MAIN)
                .setClassName(getPackageName(), HOME_CLASS)
                .putExtra(DeckCmd.EXTRA, DeckCmd.NAMES[DeckCmd.OPEN_SETUP])
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        boolean front = gate != null && getPackageName().equals(gate.fg);
        if (front) {
            try { startActivity(i); } catch (Throwable ignored) {}
        } else if (!start(i, getPackageName(), "Visor")) {
            play(SoundMap.ERROR);
            return;
        }
        play(SoundMap.SELECT);
        closeMenu(false);
    }

    private void toast(String s) {
        try { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); } catch (Throwable ignored) {}
    }

    /** For Setup: the pass-through set the service is using now (size only). */
    int passCount() {
        Set<String> s = gate == null ? null : gate.pass;
        return s == null ? 0 : s.size();
    }

    View menuView() { return menu; }
}
