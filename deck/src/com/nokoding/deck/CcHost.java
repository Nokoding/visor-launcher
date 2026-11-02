package com.nokoding.deck;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.input.InputManager;
import android.os.Build;
import android.view.InputDevice;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 2.8: what the control center's tiles do, shared by Deck's own window (HomeActivity) and the PS overlay
 * (PsService). Each of them gives this class an Env for the few things that differ: whether the overlay
 * service is running (global actions need it), how to close the menu, and the things only a live Deck
 * window can change (input mode, rumble test, player LEDs, sound pack, the screensaver).
 *
 * Prefs written here are read by Deck when it comes back to the front (HomeActivity.syncFromPrefs).
 */
final class CcHost implements QuickMenu.Host {

    interface Env {
        Context ctx();
        SharedPreferences prefs();
        AppStore store();
        IconCache.Icon icon(AppStore.App a);
        /** The app in front, or null when it is Deck itself or not known. */
        String foreground();
        /** Global actions (home, back, recents, screenshot, power dialog) are available. */
        boolean service();
        boolean global(int action);
        /** Experimental Force stop through App Info (service only). */
        boolean forceStop(String pkg);
        /** Closes the menu, then runs r after ms milliseconds (for actions that need the menu gone, like a screenshot). */
        void closeThen(Runnable r, long ms);
        /** Runs r (which starts an activity) and closes the menu. The overlay starts first: a visible window lets it. */
        void startThenClose(Runnable r);
        void setup();
        void close();
        /** Inside a live Deck window, where Deck's own state can be changed. */
        boolean live();
        boolean liveOn(String id);
        String liveText(String id);
        String liveAct(String id);
        /** Opens the game profile screen for an app (inside Deck) or shows the command (overlay). */
        String profile(String pkg);
        /** The profile quick switch for the app in front. */
        String profileQuick();
        String restoreDisplay();
        /** Text for the game profile tile: "75 PERCENT", "NONE"... */
        String profileText();
        String restartDeck();
        /** Short text for the toast and the top right label. */
        void toast(String s);
    }

    static final String PREF_TILES = "ccTiles";
    static final String PREF_PROTECT = "ccProtect";

    private final Env env;
    private CcPrefs cc;
    private String ccRaw;

    CcHost(Env env) { this.env = env; }

    private SharedPreferences p() { return env.prefs(); }

    // ---------------------------------------------------------------- settings

    @Override public CcPrefs ccPrefs() {
        String raw = p().getString(PREF_TILES, "");
        if (cc == null || !(raw == null ? "" : raw).equals(ccRaw == null ? "" : ccRaw)) {
            ccRaw = raw;
            cc = CcPrefs.parse(raw);
        }
        return cc;
    }

    static void saveTiles(SharedPreferences p, CcPrefs c) {
        p.edit().putString(PREF_TILES, c.save()).apply();
    }

    Set<String> protectUser() { return TaskList.parseProtect(p().getString(PREF_PROTECT, "")); }

    void saveProtect(Set<String> s) { p().edit().putString(PREF_PROTECT, TaskList.saveProtect(s)).apply(); }

    private String homePkg() {
        try {
            android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME);
            android.content.pm.ResolveInfo ri = env.ctx().getPackageManager().resolveActivity(i, 0);
            return ri == null || ri.activityInfo == null ? null : ri.activityInfo.packageName;
        } catch (Throwable t) {
            return null;
        }
    }

    // ---------------------------------------------------------------- Host

    @Override public IconCache.Icon quickIcon(String appKey) {
        AppStore s = env.store();
        if (s == null || appKey == null) return null;
        AppStore.App a = s.find(appKey);
        return a == null ? null : env.icon(a);
    }

    @Override public void quickSetup() { env.setup(); }

    @Override public void quickClose() { env.close(); }

    @Override public List<TaskList.Entry> ccTasks() {
        String fg = env.foreground();
        Set<String> protect = TaskList.withDefaults(protectUser(), env.ctx().getPackageName(), homePkg(), fg);
        return TaskSource.recent(env.ctx(), env.store(), fg, protect, 8);
    }

    @Override public boolean ccOn(String id) {
        SharedPreferences p = p();
        switch (id) {
            case "d.reduce": return p.getBoolean("reduce", false);
            case "c.gyro": return p.getBoolean("gyro", true);
            case "c.remap": return RemapPrefs.master(p);
            case "d.perf": return PerfRunner.mode(p) != PerfMode.OFF;
            case "s.ui": return !p.getBoolean("mute", false);
            default: return env.live() && env.liveOn(id);
        }
    }

    @Override public String ccText(String id) {
        switch (id) {
            case "d.ram": return TaskSource.ramText(env.ctx());
            case "c.battery": return batteryText();
            case "y.launcher": return launcherText();
            case "d.profile": return env.profileText();
            case "a.header": {
                return SysUsage.granted(env.ctx()) ? "" : "NEEDS USAGE ACCESS, SEE DOCS";
            }
            default: return env.live() ? env.liveText(id) : null;
        }
    }

    private boolean prefTile(String id) {
        return id.equals("d.reduce") || id.equals("c.gyro") || id.equals("c.remap") || id.equals("s.ui");
    }

    @Override public boolean ccEnabled(String id) {
        switch (id) {
            case "c.mode": case "c.rumble": case "c.leds": case "s.pack": case "d.saver":
                return env.live();
            case "a.closeall":
                return SysUsage.granted(env.ctx());
            case "q.exitgame":
                return PerfRunner.exitTarget(env.ctx(), p(), env.foreground()) != null;
            default:
                return true;
        }
    }

    @Override public boolean ccService() { return env.service(); }

    @Override public String ccAct(String id) {
        Context c = env.ctx();
        SharedPreferences p = p();
        switch (id) {
            case "q.playpause": return RemapRunner.media(c, RemapAction.PLAY_PAUSE) ? "SENT" : "NOT SENT";
            case "q.next": return RemapRunner.media(c, RemapAction.NEXT) ? "SENT" : "NOT SENT";
            case "q.prev": return RemapRunner.media(c, RemapAction.PREV) ? "SENT" : "NOT SENT";
            case "q.shot":
                env.closeThen(() -> env.global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT), 450);
                return null;
            case "q.sleep": case "y.power":
                env.closeThen(() -> env.global(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG), 150);
                return null;
            case "q.home":
                env.closeThen(() -> env.global(AccessibilityService.GLOBAL_ACTION_HOME), 100);
                return null;
            case "q.back":
                env.closeThen(() -> env.global(AccessibilityService.GLOBAL_ACTION_BACK), 100);
                return null;
            case "q.recents":
                env.closeThen(() -> env.global(AccessibilityService.GLOBAL_ACTION_RECENTS), 100);
                return null;
            case "q.exitgame": return PerfRunner.exitGame(env.ctx(), p, env.foreground());
            case "d.perf": PerfRunner.toggle(p); return PerfRunner.mode(p) == PerfMode.OFF ? "PERFORMANCE MODE OFF" : "PERFORMANCE MODE ON";
            case "q.deck":
                env.startThenClose(() -> RemapRunner.deck(c, null));
                return null;
            case "y.android":
                env.startThenClose(() -> RemapRunner.androidSettings(c));
                return null;
            case "y.restart": return env.restartDeck();
            case "d.profile": return env.profileQuick();
            case "d.restore": return env.restoreDisplay();
            case "d.reduce": p.edit().putBoolean("reduce", !p.getBoolean("reduce", false)).apply(); return null;
            case "c.gyro": p.edit().putBoolean("gyro", !p.getBoolean("gyro", true)).apply(); return null;
            case "c.remap": p.edit().putBoolean(RemapPrefs.KEY_MASTER, !RemapPrefs.master(p)).apply(); return RemapPrefs.master(p) ? "REMAPS ON" : "REMAPS OFF";
            case "s.ui": p.edit().putBoolean("mute", !p.getBoolean("mute", false)).apply(); return null;
            default: return env.live() ? env.liveAct(id) : null;
        }
    }

    @Override public int[] ccAppActions(TaskList.Entry e) {
        ArrayList<Integer> l = new ArrayList<>();
        boolean self = e.pkg.equals(env.ctx().getPackageName());
        l.add(QuickMenu.AA_OPEN);   // first, so OK twice still switches to the app like the old recent apps list did
        if (!e.foreground && !self) l.add(QuickMenu.AA_KILL);
        if (env.service() && !self) l.add(QuickMenu.AA_FORCE);
        l.add(QuickMenu.AA_INFO);
        l.add(QuickMenu.AA_PROFILE);
        if (!self) l.add(QuickMenu.AA_ADB);
        if (!self && !e.foreground) l.add(QuickMenu.AA_PROTECT);
        int[] out = new int[l.size()];
        for (int i = 0; i < out.length; i++) out[i] = l.get(i);
        return out;
    }

    @Override public String ccAppAct(int action, TaskList.Entry e) {
        Context c = env.ctx();
        switch (action) {
            case QuickMenu.AA_KILL: return TaskSource.kill(c, e.pkg) ? "KILLED " + e.label.toUpperCase(Locale.ROOT) : "ANDROID REFUSED";
            case QuickMenu.AA_FORCE:
                return env.forceStop(e.pkg) ? "TRYING FORCE STOP" : "COULD NOT START";
            case QuickMenu.AA_OPEN:
                env.startThenClose(() -> RemapRunner.launchComponent(c, e.key));
                return null;
            case QuickMenu.AA_INFO:
                env.startThenClose(() -> TaskSource.appInfo(c, e.pkg));
                return null;
            case QuickMenu.AA_PROFILE: return env.profile(e.pkg);
            case QuickMenu.AA_ADB: {
                String s = KillCmd.forceStopAdb(e.pkg);
                return s == null ? "BAD PACKAGE NAME" : s;
            }
            case QuickMenu.AA_PROTECT: {
                Set<String> u = protectUser();
                if (u.contains(e.pkg)) u.remove(e.pkg); else u.add(e.pkg);
                saveProtect(u);
                return u.contains(e.pkg) ? "PROTECTED" : "NOT PROTECTED";
            }
            default: return null;
        }
    }

    @Override public String ccCloseAll(List<TaskList.Entry> killable) {
        int n = 0;
        for (TaskList.Entry e : killable) if (TaskSource.kill(env.ctx(), e.pkg)) n++;
        return "CLOSED " + n + (n == 1 ? " APP" : " APPS");
    }

    // ---------------------------------------------------------------- status texts

    private String batteryText() {
        try {
            InputManager im = (InputManager) env.ctx().getSystemService(Context.INPUT_SERVICE);
            if (im == null || Build.VERSION.SDK_INT < 31) return "NOT AVAILABLE";
            for (int id : im.getInputDeviceIds()) {
                InputDevice d = im.getInputDevice(id);
                if (d == null || !InputMode.has(d.getSources(), InputDevice.SOURCE_GAMEPAD)) continue;
                android.hardware.BatteryState b = d.getBatteryState();
                if (b != null && b.isPresent() && b.getCapacity() >= 0f) return Math.round(b.getCapacity() * 100f) + " PERCENT";
            }
        } catch (Throwable ignored) {}
        return "NO CONTROLLER";
    }

    private String launcherText() {
        try {
            PackageManager pm = env.ctx().getPackageManager();
            String pkg = "com.google.android.apps.tv.launcherx";
            pm.getPackageInfo(pkg, 0);
            int s = pm.getApplicationEnabledSetting(pkg);
            boolean dis = s == PackageManager.COMPONENT_ENABLED_STATE_DISABLED || s == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                    || s == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED;
            return dis ? "TURNED OFF" : "ON";
        } catch (PackageManager.NameNotFoundException e) {
            return "NOT INSTALLED";
        } catch (Throwable t) {
            return "UNKNOWN";
        }
    }
}
