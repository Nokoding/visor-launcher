package com.nokoding.deck;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.role.RoleManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.tv.TvContract;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.PointerIcon;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Deck: a D-pad and gamepad first HUD home screen. Single activity, plain framework views. */
public final class HomeActivity extends Activity implements Engine.Host, IconCache.Listener, TestScreen.Host {

    static final String STOCK_HOME = "com.google.android.apps.tv.launcherx";
    static final int Z_TOP = 0, Z_MAIN = 1, Z_DOCK = 2;
    static final long LONG_MS = 550, TRAVEL_MS = 200, IDLE_MS = 120;
    static final int NONE = Integer.MIN_VALUE;
    static final int T_LOOK = 0, T_MOTION = 1, T_ANIM = 2, T_SOUND = 3, T_PAD = 4, T_APPS = 5, T_SYSTEM = 6, T_REMAP = 7;

    // wheel geometry (design px)
    static final float WCX = 960, WCY = 470, CW = 560, CH = 315, WR = 860, STEP = 26f;
    static final int SLOTS = 9;
    // grid geometry
    static final int COLS = 5;
    static final float GX = 96, GY = 216, GW = 288, GH = 162, GPX = 360, GPY = 214;
    // custom color presets: primary h s v, secondary h s v
    static final String[] PRESET_NAMES = {"PICK", "NEON ROSE", "ACID", "DEEP SEA", "SUNSET", "ICE"};
    static final int[][] PRESETS = {
            null,
            {330, 82, 100, 271, 66, 97},
            {80, 90, 100, 150, 85, 90},
            {190, 90, 100, 230, 70, 100},
            {20, 90, 100, 340, 80, 100},
            {200, 20, 100, 210, 60, 90}};

    private static boolean guardInstalled;

    SharedPreferences prefs;
    AppStore store;
    IconCache icons;
    Engine engine;
    Sounds sounds;
    Gyro gyro;
    Pad pad;
    final HumModel hum = new HumModel();
    final Handler h = new Handler(Looper.getMainLooper());
    private ExecutorService work;
    private boolean safeMode, destroyed, resumed, iconsReleased, refreshing, refreshAgain;
    private long lastRefresh;

    // settings
    boolean grid, reduce, lite, clock24, showHidden, showAll, statsOn, gyroOn, panFollow, stickRight;
    // 2.7 settings: clock seconds and second clock, wallpaper, screensaver
    boolean clockSec, saverMotion, saverStats;
    String clock2 = "";
    String wallSel = Wallpapers.SKYLINE;
    int saverStyle = DreamRules.CLOCK_AND_WALLPAPER, saverDim = DreamRules.DIM_DEFAULT;
    int themeIdx, volume;
    String packName;
    final int[] custom = new int[6];
    boolean hapticsOn, humOn, lightPulse, pointerFx, hideCursor;
    // 2.1 settings
    boolean gyroFollow, themedCursor, recentsOn, recentsSystem, psSeen;
    int recentsCount, playerLedMode;
    // 2.2 settings
    int stickSpeed = StickRoute.DEFAULT_SPEED, gyroStrength = 100;
    String iconPack = "";
    private final StickRoute.Limiter spinLimit = new StickRoute.Limiter();
    /** 2.2 full screen controller test (gamepad or gyro), or null. It gets every input while open. */
    TestScreen test;
    private String testRow = "";
    /** 2.6: where closeTest goes instead of back to PAD (the remap screens). Set after showTest, cleared when the test goes. */
    private Runnable testBack;
    // 2.6 button remap: Deck's own copy of the decision (the overlay service has the other, for other apps)
    private RemapGate remapGate;
    /** 2.9 combos: two held buttons run an action. Checked before the single button rules. */
    private ComboGate comboGate;
    private SharedPreferences.OnSharedPreferenceChangeListener remapListener;
    private KeyEvent remapSwapped;
    private android.hardware.input.InputManager.InputDeviceListener testDevices;
    /** 2.3 gyro calibration: saved per controller. calFromTest: return to the gyro test after it. */
    GyroCalStore calStore;
    private boolean calFromTest;
    // 2.3 options flyout animation: one spec for every menu, its values in prefs under anim.flyout.KEY
    AnimSpec anim;
    private AnimSpec.Store animStore;
    final TickScheduler typeTicks = new TickScheduler();
    HudToast hudToast;
    /** The tile or chip whose icon is flying in the open flyout (it fades while the icon is away). */
    private View flySource;
    private final TextReveal.Params scratchText = new TextReveal.Params(), scratchConfirm = new TextReveal.Params();
    private final FlyoutAnim.Params scratchFly = new FlyoutAnim.Params();
    private final RectF flyRect = new RectF();
    private boolean pushedBack;
    final Panel.Fx typeFx = rate -> { if (sounds != null) sounds.play(SoundMap.TYPE_TICK, rate); };
    private String iconZipStatus = "NO ICON ZIPS INSTALLED THIS RUN";
    Recents recents = new Recents();
    private List<Recents.Entry> sysEntries;
    private boolean usageGranted;
    private String dockSig = "";
    QuickMenu quick;
    /** 2.4: a quick menu playing its close (text clears, panel retracts), removed when gone. */
    private QuickMenu quickOut;
    Rule dockRule;
    File iconsDir;
    private String zipStatus = "NO ZIPS INSTALLED THIS RUN";
    private PadReport report;
    int rumble, feel, lightMode, lightBright, pointerStrength, glowSize;
    final int[] lightHsv = new int[3];
    // pointer (DualSense touchpad cursor, mouse): hover to focus, click, scroll
    private boolean cursorIn, pointerAnim;
    private float cursorX, cursorY, scrollAcc;
    private int pointerEvents;
    private long lastButtonPress = -100000;
    private final float[] push = new float[2];
    private final RectF hit = new RectF();
    CursorGlow glow;

    // views
    Abs root, wheelBox, gridBox, gridInner;
    Panorama pano;
    /** 2.7: the wallpaper pack drawn over the panorama (visible only while a pack is loaded). */
    WallpaperView wall;
    VideoLayer videoLayer;
    HudView hud;
    StatsView stats;
    HudText focusLabel, status, clock, clock2v, date, title, sub, empty;
    Chip netBtn, sysBtn;
    final ArrayList<Chip> dock = new ArrayList<>();
    final TileView[] slots = new TileView[SLOTS];
    final ArrayList<TileView> gridTiles = new ArrayList<>();
    FocusFrame frame;
    Panel panel;

    // state
    ArrayList<AppStore.App> list = new ArrayList<>();
    int zone = Z_MAIN, mainIdx, wheelTarget, topIdx, dockIdx, gridFirstRow = -1;
    Spring wheelPos, tiltX, tiltY, gridScroll, offX, offY;
    final PanModel pan = new PanModel();
    private final RectF tr = new RectF(), cur = new RectF(), from = new RectF();
    private long tweenStart = -100000;
    private boolean frameSnap = true, lockArmed;
    private float tRotX, tRotY, tCam;
    private String mainTag = "";
    private ArrayList<AppStore.App> scanCache;

    // input
    int mode = InputMode.REMOTE, glyphStyle = InputMode.STYLE_GENERIC, padId = -1;
    private long lastInput;
    private boolean keyHeld;
    private boolean okTracking, okLong, backTracking, backLong;
    private long okDown, backDown;
    final StickDial dial = new StickDial();
    private int navDx, navDy;

    private SimpleDateFormat fmtDate;
    private TimeZone tz = TimeZone.getDefault();
    private final char[] clockBuf = new char[ClockFmt.MAX_CHARS];
    private long dateMinute = -1;
    // 2.7 wallpaper: one decoded wallpaper at a time
    File wallDir;
    private WallpaperSpec wallSpec;
    private AnimSpec wallOpts;
    private PrefStore wallStore;
    private int wallGen;
    private boolean wallReleased;
    String wallStatus = "BUILT-IN SKYLINE";
    private String wallZipStatus = "NO WALLPAPER ZIPS INSTALLED THIS RUN";
    private Panel.Row wallStatRow;
    private Panel wallStatPanel;
    // 2.7 screensaver preview inside Deck (the real one is DeckDream)
    private DreamRoot preview;
    private boolean saverPageMissing;
    private ActivityManager am;
    private BroadcastReceiver pkgReceiver;
    private String link = "OFF";

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        prefs = getSharedPreferences("deck", MODE_PRIVATE);
        installCrashGuard(prefs);
        earlyCmd(getIntent());
        // 2.8: a game profile left the display changed (Deck was killed): undo it before anything else
        try { ProfileRunner.get(this).startupRestore(); } catch (Throwable ignored) {}
        calStore = new GyroCalStore(prefs);
        animStore = new AnimSpec.Store() {
            @Override public String get(String k) { try { return prefs.getString(k, null); } catch (Throwable t) { return null; } }
            @Override public void put(String k, String v) { prefs.edit().putString(k, v).apply(); }
            @Override public void remove(String k) { prefs.edit().remove(k).apply(); }
        };
        if (shouldSafeMode()) {
            showSafeMode();
            return;
        }
        readSettings();
        remapInit();
        Ui.loadFonts(getAssets());
        initMetrics();
        store = new AppStore(this, prefs);
        icons = new IconCache(this);
        icons.setListener(this);
        try {
            File ext = getExternalFilesDir(null);
            iconsDir = ext == null ? null : new File(ext, "icons");
        } catch (Throwable t) {
            iconsDir = null;
        }
        icons.overrideDir = iconsDir;
        try {
            File ext2 = getExternalFilesDir(null);
            wallDir = ext2 == null ? null : new File(ext2, "wallpapers");
            ControllerDraw.modelsDir = ext2 == null ? null : new File(ext2, "models");
            if (ControllerDraw.modelsDir != null) ControllerDraw.modelsDir.mkdirs();   // 2.17: so adb push has a folder
            final android.content.res.AssetManager am = getAssets();
            ModelObj.bundled = name -> am.open("models/" + name);   // 2.18: the models that ship in the app
        } catch (Throwable t) {
            wallDir = null;
        }
        wallStore = new PrefStore(prefs);
        icons.iconPack = iconPack;
        icons.overrideOff = new HashSet<>(OrderList.splitSet(prefs.getString("iconOff", "")));
        IconCache.originalMode = prefs.getInt("iconMode", 0) == 1;
        engine = new Engine(this);
        sounds = new Sounds(this);
        sounds.master = volume / 100f;
        sounds.mute = prefs.getBoolean("mute", false);
        if (Build.VERSION.SDK_INT >= 31) {
            try { gyro = new Gyro(); gyro.follow = gyroFollow; gyro.filter.scale = gyroStrength / 100f; gyro.store = calStore; } catch (Throwable t) { gyro = null; }
            try { pad = new Pad(); } catch (Throwable t) { pad = null; }
        }
        am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        work = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(() -> {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                r.run();
            }, "deck-work");
            t.setDaemon(true);
            return t;
        });
        String focusKey;
        if (saved != null) {
            focusKey = saved.getString("focusKey");
            zone = saved.getInt("zone", Z_MAIN);
        } else {
            focusKey = prefs.getString("lastKey", null);
        }
        if (!store.loadCached()) {
            try { store.apply(AppStore.query(this, showAll, store.extras), new ArrayList<AppStore.Input>()); } catch (Throwable ignored) {}
        }
        buildUi();
        rebuildLists(focusKey);
        registerPackageReceiver();
        prepareIconFolder();
        prepareWallpaperFolder();
        handleCmd(getIntent());
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (safeMode) return;
        engine.start();
        if (iconsReleased) {
            iconsReleased = false;
            rebindIcons();
        }
        if (wallReleased && preview == null) {
            wallReleased = false;
            applyWallpaper();
        }
        // 2.4: the PS overlay records launches too, so pick up its writes
        try { recents = Recents.load(prefs.getString("recents", "")); } catch (Throwable ignored) {}
        refresh(false);
        h.removeCallbacks(soundInit);
        h.postDelayed(soundInit, 400);
    }

    private final Runnable soundInit = () -> {
        if (destroyed || sounds == null) return;
        sounds.ensure();
        installZips(false, () -> {});
        installIconZips(false);
        installWallpaperZips(false);
    };

    /**
     * 2.2: unpacks new icon pack zips in files/icons on the worker thread, then reloads icons.
     * pick = true (deck_cmd reload_icons): if exactly one new pack came in, switch to it.
     */
    private void installIconZips(final boolean pick) { installIconZips(pick, null); }

    private void installIconZips(final boolean pick, final Runnable after) {
        final File dir = iconsDir;
        if (work == null || dir == null) { reloadIcons(); if (after != null) after.run(); return; }
        try {
            work.execute(() -> {
                ArrayList<PackZip.Result> rs = null;
                try { rs = IconPacks.installZips(dir); } catch (Throwable ignored) {}
                final ArrayList<PackZip.Result> res = rs == null ? new ArrayList<PackZip.Result>() : rs;
                h.post(() -> {
                    if (destroyed) return;
                    int okN = 0;
                    String only = null;
                    StringBuilder st = new StringBuilder();
                    for (PackZip.Result r : res) {
                        if (st.length() > 0) st.append("  //  ");
                        if (r.ok) { okN++; only = r.name; st.append("INSTALLED ").append(r.name.toUpperCase(Locale.ROOT)).append(" (").append(r.files).append(" ICONS)"); }
                        else st.append(r.zip.toUpperCase(Locale.ROOT)).append(": ").append(r.error);
                    }
                    if (st.length() > 0) iconZipStatus = st.toString();
                    if (pick && okN == 1 && !only.equals(iconPack)) {
                        setIconPack(only);
                        toast("Icon pack " + only + " installed and selected");
                    } else if (okN > 0) {
                        toast(okN == 1 ? "Icon pack " + only + " installed" : okN + " icon packs installed");
                    } else if (!res.isEmpty()) {
                        toast("Icon pack zip failed: " + res.get(0).error);
                    }
                    if (okN > 0 || pick || after != null) reloadIcons();
                    if (after != null) after.run();
                });
            });
        } catch (Throwable t) {
            reloadIcons();
            if (after != null) after.run();
        }
    }

    private void setIconPack(String name) {
        iconPack = name == null ? "" : name;
        prefs.edit().putString("iconPack", iconPack).apply();
        if (icons != null) icons.iconPack = iconPack;
    }

    /**
     * Unpacks new zips on the worker thread, then reloads the current pack if needed.
     * pick = true (from deck_cmd): if exactly one new pack came in, switch to it.
     */
    private void installZips(final boolean pick, final Runnable after) {
        if (work == null || sounds == null) return;
        try {
            work.execute(() -> {
                ArrayList<PackZip.Result> rs = null;
                try { rs = sounds.installZips(); } catch (Throwable ignored) {}
                final ArrayList<PackZip.Result> res = rs == null ? new ArrayList<PackZip.Result>() : rs;
                h.post(() -> {
                    if (destroyed || sounds == null) return;
                    boolean reloadCurrent = false;
                    String only = null;
                    int okN = 0;
                    StringBuilder st = new StringBuilder();
                    for (PackZip.Result r : res) {
                        if (st.length() > 0) st.append("  //  ");
                        if (r.ok) {
                            okN++;
                            only = r.name;
                            if (r.name.equals(packName)) reloadCurrent = true;
                            st.append("INSTALLED ").append(r.name.toUpperCase(Locale.ROOT)).append(" (").append(r.files).append(" FILES)");
                        } else {
                            st.append(r.zip.toUpperCase(Locale.ROOT)).append(": ").append(r.error);
                        }
                    }
                    if (st.length() > 0) zipStatus = st.toString();
                    if (pick && okN == 1 && only != null && !only.equals(packName)) {
                        packName = only;
                        prefs.edit().putString("soundPack", packName).apply();
                        toast("Sound pack " + only + " installed and selected");
                        reloadCurrent = true;
                    } else if (okN > 0) {
                        toast(okN == 1 ? "Sound pack " + only + " installed" : okN + " sound packs installed");
                    } else if (!res.isEmpty()) {
                        toast("Sound pack zip failed: " + res.get(0).error);
                    }
                    if (reloadCurrent || !packName.equals(sounds.pack) || pick) sounds.loadPack(packName, pick || okN > 0);
                    after.run();
                });
            });
        } catch (Throwable t) {
            after.run();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (safeMode) return;
        resumed = true;
        if (prefs != null) PerfRunner.bundleHome(this, prefs);   // 2.17: a game profile's volume goes back when you return to Deck
        refreshFormats();
        h.removeCallbacks(clockTick);
        h.removeCallbacks(statusTick);
        h.removeCallbacks(statsTick);
        if (wall != null) wall.setRunning(true);
        clockTick.run();
        statusTick.run();
        statsTick.run();
        h.removeCallbacks(stable);
        h.postDelayed(stable, 15000);
        syncFromPrefs();
        if (!offerScheduled) { offerScheduled = true; h.postDelayed(this::maybeOfferTutorial, 2500); }
        updateGyro();
        updatePad();
        refreshRecents();
        if (test != null) test.resume();
        ProfileRunner.get(this).setDeckUi(profileUi);
    }

    /** 2.8: the control center in the PS overlay writes some switches to prefs while another app is in front. */
    private void syncFromPrefs() {
        try {
            boolean g = prefs.getBoolean("gyro", true);
            if (g != gyroOn) { gyroOn = g; }
            boolean rd = prefs.getBoolean("reduce", false);
            if (rd != reduce) { reduce = rd; applyEffects(); }
            if (sounds != null) sounds.mute = prefs.getBoolean("mute", false);
            loadTouchVol();
        } catch (Throwable ignored) {}
    }

    @Override
    protected void onPause() {
        ProfileRunner.get(this).setDeckUi(null);
        stickVol.release();   // the up of a held key goes to whatever comes to the front
        super.onPause();
        if (safeMode) return;
        resumed = false;
        if (preview != null) stopPreview(false);
        if (wall != null) wall.setRunning(false);
        h.removeCallbacks(clockTick);
        h.removeCallbacks(statusTick);
        h.removeCallbacks(statsTick);
        h.removeCallbacks(stable);
        h.removeCallbacks(navRepeat);
        navDx = 0;
        navDy = 0;
        keyHeld = false;
        h.removeCallbacks(remapTick);
        if (comboGate != null) comboGate.reset();
        if (remapGate != null) remapGate.reset();   // the up of a held button would go to the app we are leaving
        if (gyro != null) gyro.stop();
        if (pad != null) pad.detach();
        cursorIn = false;
        AppStore.App a = focusedApp();
        if (a != null) prefs.edit().putString("lastKey", a.key).apply();
        if (report != null) { report.close(); report = null; }
        if (test != null) test.pause();   // sensor listeners only while the test is on screen
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (safeMode) return;
        engine.stop();
        releaseWallpaper();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        h.removeCallbacksAndMessages(null);
        try { if (remapListener != null) prefs.unregisterOnSharedPreferenceChangeListener(remapListener); } catch (Throwable ignored) {}
        if (!safeMode) {
            try { if (pkgReceiver != null) unregisterReceiver(pkgReceiver); } catch (Throwable ignored) {}
            if (engine != null) engine.stop();
            if (icons != null) icons.shutdown();
            if (work != null) work.shutdownNow();
            if (sounds != null) sounds.release();
            if (gyro != null) gyro.stop();
            if (pad != null) pad.detach();
            if (stats != null) stats.detach();
            if (test != null) removeTest();
        }
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (safeMode) return;
        AppStore.App a = focusedApp();
        if (a != null) out.putString("focusKey", a.key);
        out.putInt("zone", zone);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (earlyCmd(intent)) {
            if (!safeMode && root != null) goTopLevel();
            return;
        }
        if (safeMode || root == null) return;
        if (handleCmd(intent)) return;
        // Home pressed while we are in front (MAIN + HOME, delivered here because of singleTask):
        // close overlays and return to the top level view. Other relaunches do the same.
        goTopLevel();
    }

    /** Close the quick menu and any panel, put focus back on the apps. */
    private void goTopLevel() {
        if (preview != null) stopPreview(false);
        if (test != null) removeTest();
        if (quick != null) closeQuick(false);
        if (panel != null) { panel.onBack = null; panel.afterClose = null; closePanel(); }
        if (!list.isEmpty() && zone != Z_MAIN) setZone(Z_MAIN, 0, 0);
    }

    /** deck_cmd extra from adb (see DeckCmd). Unknown values are ignored. @return true if handled. */
    private boolean handleCmd(Intent intent) {
        if (intent == null || safeMode) return false;
        String v;
        try {
            v = intent.getStringExtra(DeckCmd.EXTRA);
        } catch (Throwable t) {
            return false;
        }
        int cmd = DeckCmd.parse(v);
        try { intent.removeExtra(DeckCmd.EXTRA); } catch (Throwable ignored) {}
        switch (cmd) {
            case DeckCmd.RELOAD_PACKS:
                if (sounds != null) { sounds.ensure(); installZips(true, () -> {}); }
                return true;
            case DeckCmd.RELOAD_ICONS:
                installIconZips(true);
                toast("Icons reloaded");
                return true;
            case DeckCmd.RELOAD_WALLPAPERS:
                // 2.7: unpack new wallpaper zips and reload the wallpaper
                installWallpaperZips(true);
                toast("Wallpapers reloaded");
                return true;
            case DeckCmd.RELOAD_THEMES:
                // 2.17: a theme file was pushed with adb; nothing is applied until it is loaded in the theme editor
                toast(ThemeStore.list(themesDir()).size() + " theme files found. Load one in Setup, LOOK, THEME EDITOR.");
                return true;
            case DeckCmd.RESTORE_DISPLAY:
                return true;   // 2.8: handled by earlyCmd
            case DeckCmd.EXIT_GAME: {
                // 2.12: from a phone, when a game is on screen
                String r = PerfRunner.exitGame(this, prefs, null);
                toast(r == null ? "No game to exit" : "Closing the game");
                return true;
            }
            case DeckCmd.OPEN_TUTORIAL:
                // 2.9: the tutorial, from a phone
                if (root == null) return true;
                if (test != null) removeTest();
                if (quick != null) closeQuick(false);
                openTutorial(false);
                return true;
            case DeckCmd.PREVIEW_SAVER:
                // 2.7: the screensaver preview, so it can be tried from a phone
                if (root == null) return true;
                if (test != null) removeTest();
                if (quick != null) closeQuick(false);
                startPreview();
                return true;
            case DeckCmd.OPEN_INPUTS:
                // 2.6: a remapped button that opens the TV inputs list
                if (root == null) return true;
                if (test != null) removeTest();
                if (quick != null) closeQuick(false);
                openInputs();
                return true;
            case DeckCmd.OPEN_SETUP:
                // 2.4: OPEN SETUP in the PS overlay menu
                if (root == null) return true;
                if (test != null) removeTest();
                if (quick != null) closeQuick(false);
                openSettings(T_LOOK, null);
                return true;
            default:
                return false;
        }
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        if (safeMode) return;
        h.post(this::checkSize);
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (safeMode || icons == null) return;
        if (level >= TRIM_MEMORY_BACKGROUND) {
            releaseIcons();
            if (sounds != null) sounds.release();
        } else if (level >= TRIM_MEMORY_UI_HIDDEN || level == TRIM_MEMORY_RUNNING_LOW || level == TRIM_MEMORY_RUNNING_CRITICAL) {
            icons.trimHalf();
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (safeMode || icons == null) return;
        if (resumed) icons.trimHalf(); else releaseIcons();
    }

    private final Runnable stable = () -> {
        try { prefs.edit().putInt("crashN", 0).apply(); } catch (Throwable ignored) {}
    };

    // ------------------------------------------------------------------ settings

    private void readSettings() {
        grid = "grid".equals(prefs.getString("layout", "wheel"));
        themeIdx = Math.max(0, Math.min(Ui.THEMES.length - 1, prefs.getInt("theme", 0)));
        clock24 = prefs.getBoolean("clock24", false);
        clockSec = prefs.getBoolean("clockSec", false);
        clock2 = prefs.getString("clock2", "");
        if (clock2 == null || ClockFmt.zoneIndex(clock2) < 0) clock2 = "";
        wallSel = Wallpapers.normalize(prefs.getString("wallpaper", Wallpapers.SKYLINE));
        saverStyle = DreamRules.clampStyle(prefs.getInt("saverStyle", DreamRules.CLOCK_AND_WALLPAPER));
        saverDim = DreamRules.clampDim(prefs.getInt("saverDim", DreamRules.DIM_DEFAULT));
        saverMotion = prefs.getBoolean("saverMotion", true);
        saverStats = prefs.getBoolean("saverStats", false);
        reduce = prefs.getBoolean("reduce", false);
        showHidden = prefs.getBoolean("showHidden", false);
        showAll = prefs.getBoolean("showAll", false);
        statsOn = prefs.getBoolean("stats", true);
        gyroOn = prefs.getBoolean("gyro", true);
        ModelObj.lightDetail = prefs.getBoolean("modelLight", true);   // LIGHT (12,000 triangles) unless the user chose FULL
        panFollow = !"opposite".equals(prefs.getString("panDir", "follow"));
        stickRight = !"left".equals(prefs.getString("stick", "right"));
        volume = Math.max(0, Math.min(100, prefs.getInt("volume", 70)));
        packName = prefs.getString("soundPack", "");
        if (packName == null) packName = "";
        int[] def = PRESETS[1];
        String[] k = {"cAh", "cAs", "cAv", "cBh", "cBs", "cBv"};
        for (int i = 0; i < 6; i++) custom[i] = prefs.getInt(k[i], def[i]);
        hapticsOn = prefs.getBoolean("haptics", true);
        humOn = prefs.getBoolean("hum", true);
        rumble = prefs.getInt("rumble", 50);
        feel = Math.max(0, Math.min(2, prefs.getInt("feel", HapticPatterns.CRISP)));
        lightMode = Math.max(0, Math.min(2, prefs.getInt("lightMode", 1)));
        lightBright = prefs.getInt("lightBright", 60);
        lightPulse = prefs.getBoolean("lightPulse", true);
        pointerFx = prefs.getBoolean("pointerFx", true);
        hideCursor = prefs.getBoolean("hideCursor", false);
        pointerStrength = prefs.getInt("pointerStrength", 70);
        glowSize = prefs.getInt("glowSize", 100);
        lightHsv[0] = prefs.getInt("lH", 300);
        lightHsv[1] = prefs.getInt("lS", 85);
        lightHsv[2] = prefs.getInt("lV", 100);
        gyroFollow = !"opposite".equals(prefs.getString("gyroDir", "follow"));
        themedCursor = prefs.getBoolean("themedCursor", true);
        recentsOn = prefs.getBoolean("recentsDock", true);
        recentsCount = Recents.clampCount(prefs.getInt("recentsCount", Recents.DEFAULT_SHOW));
        recentsSystem = "system".equals(prefs.getString("recentsSrc", "deck"));
        psSeen = prefs.getBoolean("psSeen", false);
        playerLedMode = Math.max(0, Math.min(PlayerLeds.NAMES.length - 1, prefs.getInt("playerLeds", PlayerLeds.OFF)));
        recents = Recents.load(prefs.getString("recents", ""));
        stickSpeed = StickRoute.clampSpeed(prefs.getInt("stickSpeed", StickRoute.DEFAULT_SPEED));
        dial.detent = StickRoute.detent(stickSpeed);
        gyroStrength = Math.round(GyroFilter.clampScale(prefs.getInt("gyroStrength", 100)) * 100);
        iconPack = prefs.getString("iconPack", "");
        if (iconPack == null) iconPack = "";
        anim = AnimConfig.flyout();
        anim.load(animStore);
        rebuildCustomTheme();
        Ui.T = Ui.THEMES[themeIdx];
    }

    /** 2.4: the saved theme into Ui.T, for the PS overlay service when Deck's screen is not running. */
    static void themeFromPrefs(SharedPreferences p) {
        try {
            int[] def = PRESETS[1], c = new int[6];
            String[] k = {"cAh", "cAs", "cAv", "cBh", "cBs", "cBv"};
            for (int i = 0; i < 6; i++) c[i] = p.getInt(k[i], def[i]);
            Ui.THEMES[Ui.CUSTOM] = Ui.custom(Ui.hsv(c[0], c[1] / 100f, c[2] / 100f), Ui.hsv(c[3], c[4] / 100f, c[5] / 100f));
            ThemeSpec spec = ThemeSpec.parse(p.getString(PREF_CTHM, ""));   // 2.17: the theme editor's colours win over the two colour mixer
            if (spec != null) Ui.THEMES[Ui.CUSTOM] = themeOf(spec);
            Ui.T = Ui.THEMES[Math.max(0, Math.min(Ui.THEMES.length - 1, p.getInt("theme", 0)))];
        } catch (Throwable ignored) {}
    }

    private void rebuildCustomTheme() {
        Ui.THEMES[Ui.CUSTOM] = Ui.custom(Ui.hsv(custom[0], custom[1] / 100f, custom[2] / 100f),
                Ui.hsv(custom[3], custom[4] / 100f, custom[5] / 100f));
        ThemeSpec spec = prefs == null ? null : ThemeSpec.parse(prefs.getString(PREF_CTHM, ""));
        if (spec != null) Ui.THEMES[Ui.CUSTOM] = themeOf(spec);
    }

    /** 2.17: the theme editor's saved CUSTOM theme (a ThemeSpec as JSON). When present it replaces the two colour mixer's result. */
    static final String PREF_CTHM = "cthm";

    static Ui.Theme themeOf(ThemeSpec s) {
        return new Ui.Theme("CUSTOM", s.a, s.b, s.bg0, s.bg1, s.panel, s.text, s.dim);
    }

    private void saveCustom() {
        String[] k = {"cAh", "cAs", "cAv", "cBh", "cBs", "cBv"};
        SharedPreferences.Editor e = prefs.edit();
        for (int i = 0; i < 6; i++) e.putInt(k[i], custom[i]);
        e.apply();
    }

    boolean fx3d() { return !reduce; }

    @SuppressWarnings("deprecation")
    private void initMetrics() {
        int w = 1920, hh = 1080;
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                Rect b = getWindowManager().getCurrentWindowMetrics().getBounds();
                w = b.width(); hh = b.height();
            } else {
                DisplayMetrics dm = new DisplayMetrics();
                getWindowManager().getDefaultDisplay().getRealMetrics(dm);
                w = dm.widthPixels; hh = dm.heightPixels;
            }
        } catch (Throwable ignored) {}
        if (w < hh) { int t = w; w = hh; hh = t; }
        Ui.init(w, hh);
    }

    private void checkSize() {
        if (root == null || destroyed) return;
        int w = root.getWidth(), hh = root.getHeight();
        if (w <= 0 || hh <= 0) return;
        if (Math.abs(w - Ui.W) > 2 || Math.abs(hh - Ui.H) > 2) {
            AppStore.App a = focusedApp();
            Ui.init(w, hh);
            buildUi();
            rebuildLists(a == null ? null : a.key);
        }
    }

    // ------------------------------------------------------------------ UI construction

    private void buildUi() {
        Context c = this;
        if (preview != null) { try { preview.stop(); } catch (Throwable ignored) {} preview = null; }
        engine.stop();
        engine = new Engine(this);
        engine.start();
        root = new Abs(c);
        root.sizeListener = (w, hh) -> h.post(this::checkSize);


        pano = new Panorama(c);
        root.put(pano, 0, 0, 1920, 1080);
        if (wall != null) wall.release();
        videoLayer = new VideoLayer(c);
        root.put(videoLayer, 0, 0, 1920, 1080);   // 2.17: behind the wallpaper view, plays a video wallpaper
        wall = new WallpaperView(c);
        wall.setVideoLayer(videoLayer);
        wall.setVisibility(View.GONE);
        root.put(wall, 0, 0, 1920, 1080);
        hud = new HudView(c);
        hud.brandName = getString(R.string.app_name).toUpperCase(Locale.ROOT);
        root.put(hud, 0, 0, 1920, 1080);
        stats = new StatsView(c);
        stats.enabled = statsOn;
        root.put(stats, 96, 142, 1728, 54);

        focusLabel = new HudText(c, Ui.HEAD_M, 22, HudText.LEFT).spacing(Ui.LABEL_SPACING);
        root.put(focusLabel, 420, 62, 470, 48);
        status = new HudText(c, Ui.MONO, 17, HudText.RIGHT).dim();
        root.put(status, 880, 70, 410, 36);
        netBtn = new Chip(c);
        netBtn.setGlyph(Chip.G_WIFI);
        root.put(netBtn, 1306, 59, 76, 58);
        sysBtn = new Chip(c);
        sysBtn.setGlyph(Chip.G_GEAR);
        root.put(sysBtn, 1394, 59, 76, 58);
        date = new HudText(c, Ui.MONO, 17, HudText.RIGHT).dim();
        root.put(date, 1476, 70, 130, 36);
        clock = new HudText(c, Ui.MONO, 44, HudText.RIGHT);
        root.put(clock, 1604, 56, 200, 62);
        clock2v = new HudText(c, Ui.MONO, 15, HudText.RIGHT).dim();
        root.put(clock2v, 1544, 102, 260, 24);
        applyClockZone();

        glow = new CursorGlow(c);
        float gr = glowRadius();
        root.put(glow, 0, 0, gr * 2, gr * 2);
        glow.setVisibility(View.GONE);
        wheelBox = new Abs(c);
        root.put(wheelBox, 0, 0, 1920, 1080);
        gridBox = new Abs(c);
        root.put(gridBox, 0, 0, 1920, 1080);
        gridBox.setClipBounds(new Rect(0, Math.round(Ui.px(198)), Math.round(Ui.px(1920)), Math.round(Ui.px(846))));
        gridInner = new Abs(c);
        gridBox.put(gridInner, 0, 0, 1920, 1080);

        title = new HudText(c, Ui.HEAD, 56, HudText.CENTER).spacing(Ui.HEAD_SPACING);
        root.put(title, 200, 668, 1520, 84);
        sub = new HudText(c, Ui.MONO, 22, HudText.CENTER).dim();
        root.put(sub, 200, 754, 1520, 40);
        empty = new HudText(c, Ui.HEAD_M, 22, HudText.CENTER).spacing(Ui.LABEL_SPACING).accent();
        root.put(empty, 200, 440, 1520, 50);

        float cam = camFor(Ui.px(CW));
        for (int i = 0; i < SLOTS; i++) {
            TileView t = new TileView(c);
            wheelBox.put(t, WCX - CW / 2, WCY - CH / 2, CW, CH);
            t.setCameraDistance(cam);
            t.setVisibility(View.INVISIBLE);
            slots[i] = t;
        }

        frame = new FocusFrame(c);
        root.put(frame, 0, 0, 10, 10);
        hudToast = new HudToast(c);
        root.put(hudToast, 0, 0, 1920, 1080);
        flySource = null;
        pushedBack = false;

        wheelPos = new Spring(engine, 230f, 0.74f, 0.0015f);
        tiltX = new Spring(engine, 170f, 0.42f, 0.03f);
        tiltY = new Spring(engine, 170f, 0.42f, 0.03f);
        gridScroll = new Spring(engine, 200f, 1f, 0.3f);
        offX = new Spring(engine, 30f, 1f, 0.2f);
        offY = new Spring(engine, 30f, 1f, 0.2f);
        netBtn.focus = new Spring(engine, 300f, 0.7f, 0.002f);
        sysBtn.focus = new Spring(engine, 300f, 0.7f, 0.002f);
        netBtn.setCameraDistance(camFor(Ui.px(76)));
        sysBtn.setCameraDistance(camFor(Ui.px(76)));
        frameSnap = true;
        panel = null;
        quick = null;
        quickOut = null;
        dockRule = null;
        dock.clear();
        setContentView(root);
        // The opaque panorama covers the screen, so skip the window background fill.
        try { getWindow().setBackgroundDrawable(null); } catch (Throwable ignored) {}
        hud.setMode(mode, glyphStyle, stickRight, false);
        applyCursorIcon();
        applyEffects();
        applyWallpaper();
    }

    static float camFor(float widthPx) {
        float dpi = 320f;
        try { dpi = android.content.res.Resources.getSystem().getDisplayMetrics().densityDpi; } catch (Throwable ignored) {}
        return widthPx * 2.8f * dpi / 72f;
    }

    private void applyEffects() {
        if (root == null) return;
        frame.effects = !reduce;
        frame.detail = !lite;
        focusLabel.effects = !reduce && !lite;
        title.effects = !reduce && !lite;
        status.effects = !reduce && !lite;
        hud.effects = !reduce;
        stats.effects = !reduce;
        pano.scanlines = !lite;
        pano.invalidate();
        if (wall != null) wall.setReduce(reduce);
        pan.scale = reduce ? 0.12 : 1.0;
        pan.dir = panFollow ? 1 : -1;
        applyCursorIcon();
        if (quick != null) quick.effects = !reduce;
        if (reduce) {
            tiltX.snap(0);
            tiltY.snap(0);
        }
        updateGyro();
        engine.kick();
    }

    // ------------------------------------------------------------------ lists

    void rebuildLists(String keepKey) {
        if (root == null) return;
        list = store.visible(showHidden);
        int idx = -1;
        if (keepKey != null) for (int i = 0; i < list.size(); i++) if (list.get(i).key.equals(keepKey)) { idx = i; break; }
        if (idx < 0) idx = Math.min(mainIdx, Math.max(0, list.size() - 1));
        mainIdx = Math.max(0, idx);
        wheelTarget = mainIdx;
        buildDock();
        wheelBox.setVisibility(grid ? View.GONE : View.VISIBLE);
        gridBox.setVisibility(grid ? View.VISIBLE : View.GONE);
        title.setVisibility(grid ? View.GONE : View.VISIBLE);
        sub.setVisibility(grid ? View.GONE : View.VISIBLE);
        if (grid) {
            buildGrid();
        } else {
            if (!gridTiles.isEmpty()) { gridInner.removeAllViews(); gridTiles.clear(); }
            for (TileView t : slots) t.vIndex = NONE;
            wheelPos.snap(wheelTarget);
            bindWheel();
        }
        empty.set(list.isEmpty() ? "NO APPS FOUND  //  TRY ADD APP IN THE DOCK" : "");
        if (list.isEmpty() && zone == Z_MAIN) zone = Z_DOCK;
        if (dockIdx >= dock.size()) dockIdx = Math.max(0, dock.size() - 1);
        stats.setTags(list.size(), grid);
        focusChanged(false);
        engine.kick();
    }

    private void buildDock() {
        for (Chip ch : dock) root.removeView(ch);
        dock.clear();
        Chip s = new Chip(this);
        s.setGlyph(Chip.G_GEAR);
        s.setLabel("SETTINGS");
        s.id = "settings";
        s.action = () -> startFirst(new Intent(Settings.ACTION_SETTINGS));
        dock.add(s);
        Chip add = new Chip(this);
        add.setGlyph(Chip.G_PLUS);
        add.setLabel("ADD APP");
        add.id = "add";
        add.action = this::openAddApp;
        dock.add(add);
        if (!store.inputs.isEmpty()) {
            Chip in = new Chip(this);
            in.setGlyph(Chip.G_INPUT);
            in.setLabel("INPUTS");
            in.id = "inputs";
            in.action = this::openInputs;
            dock.add(in);
        }
        for (AppStore.App a : store.dockApps()) {
            final AppStore.App app = a;
            final Chip ch = new Chip(this);
            IconCache.Icon ic = icons.get(a.key);
            ch.setApp(a, ic);
            if (ic == null) icons.request(a);
            ch.id = "app:" + a.key;
            ch.action = () -> launch(app, ch);
            dock.add(ch);
        }
        // 2.1: recent apps after the pins, behind a divider
        ArrayList<AppStore.App> rec = recentsOn ? recentApps(recentsCount, true) : new ArrayList<AppStore.App>();
        StringBuilder sig = new StringBuilder();
        for (AppStore.App a : rec) {
            final AppStore.App app = a;
            final Chip ch = new Chip(this);
            ch.compact = true;
            IconCache.Icon ic = icons.get(a.key);
            ch.setApp(a, ic);
            if (ic == null) icons.request(a);
            ch.id = "recent:" + a.key;
            ch.action = () -> launch(app, ch);
            dock.add(ch);
            sig.append(a.key).append('|');
        }
        dockSig = sig.toString();
        if (dockRule != null) { root.removeView(dockRule); dockRule = null; }
        int at = root.indexOfChild(empty) + 1;
        int fixed = 0, recentN = rec.size();
        for (Chip ch : dock) if (ch.app == null) fixed++;
        int apps = dock.size() - fixed - recentN;
        float gap = 18, fixedW = 232, recentW = 96, ruleW = recentN > 0 ? 30 : 0;
        float recentSpan = recentN == 0 ? 0 : recentN * (recentW + gap) + ruleW + gap;
        float appW = apps == 0 ? 268 : Math.max(88, Math.min(268, (1728 - fixed * (fixedW + gap) - recentSpan - gap * (apps - 1)) / apps));
        float x = 96;
        for (Chip ch : dock) {
            if (ch.compact && dockRule == null && recentN > 0) {
                dockRule = new Rule(this);
                root.putAt(dockRule, at++, x, 872, ruleW, 88);
                x += ruleW + gap;
            }
            float w = ch.compact ? recentW : (ch.app != null ? appW : fixedW);
            ch.focus = new Spring(engine, 300f, 0.7f, 0.002f);
            ch.setCameraDistance(camFor(Ui.px(w)));
            root.putAt(ch, at++, x, 872, w, 88);
            x += w + gap;
        }
    }

    /** Rebuild only the dock (after recents change), keeping focus sensible. */
    private void refreshDock() {
        if (root == null) return;
        String keep = zone == Z_DOCK && dockIdx < dock.size() ? dock.get(dockIdx).id : null;
        buildDock();
        if (keep != null) {
            int idx = -1;
            for (int k = 0; k < dock.size(); k++) if (dock.get(k).id.equals(keep)) idx = k;
            dockIdx = idx >= 0 ? idx : Math.min(dockIdx, Math.max(0, dock.size() - 1));
        } else if (dockIdx >= dock.size()) {
            dockIdx = Math.max(0, dock.size() - 1);
        }
        focusChanged(false);
        engine.kick();
    }

    /** Recent apps from the chosen source, newest first. skipPinned for the dock. */
    ArrayList<AppStore.App> recentApps(int max, boolean skipPinned) {
        ArrayList<AppStore.App> out = new ArrayList<>();
        if (max <= 0 || store == null) return out;
        HashSet<String> live = new HashSet<>();
        for (AppStore.App a : store.all) if (showHidden || !store.hidden.contains(a.key)) live.add(a.key);
        HashSet<String> ex = new HashSet<>();
        if (skipPinned) ex.addAll(store.pinned);
        List<Recents.Entry> src = recentsSystem && usageGranted && sysEntries != null && !sysEntries.isEmpty() ? sysEntries : recents.list;
        for (String k : Recents.pick(src, max, ex, live)) {
            AppStore.App a = store.find(k);
            if (a != null) out.add(a);
        }
        return out;
    }

    /** Re-read the system usage source if chosen, then refresh the dock if the recents changed. */
    private void refreshRecents() {
        if (root == null || store == null) return;
        if (!recentsSystem) {
            sysEntries = null;
            checkDockRecents();
            return;
        }
        final HashMap<String, String> map = new HashMap<>();
        for (AppStore.App a : store.all) if (!map.containsKey(a.cn.getPackageName())) map.put(a.cn.getPackageName(), a.key);
        final Context app = getApplicationContext();
        try {
            work.execute(() -> {
                final boolean g = SysUsage.granted(app);
                final List<Recents.Entry> l = g ? SysUsage.query(app, map, 7) : null;
                h.post(() -> {
                    if (destroyed) return;
                    usageGranted = g;
                    sysEntries = l;
                    checkDockRecents();
                });
            });
        } catch (Throwable ignored) {}
    }

    private void checkDockRecents() {
        StringBuilder sig = new StringBuilder();
        if (recentsOn) for (AppStore.App a : recentApps(recentsCount, true)) sig.append(a.key).append('|');
        if (!sig.toString().equals(dockSig)) refreshDock();
    }

    private void bindWheel() {
        int n = list.size();
        if (n == 0) {
            for (TileView t : slots) { t.vIndex = NONE; t.setVisibility(View.INVISIBLE); }
            return;
        }
        int w = WheelMath.window(n, SLOTS), first = WheelMath.first(wheelPos.value, n, SLOTS);
        for (TileView t : slots) if (t.vIndex != NONE && (t.vIndex < first || t.vIndex >= first + w)) t.vIndex = NONE;
        for (int v = first; v < first + w; v++) {
            boolean have = false;
            for (TileView t : slots) if (t.vIndex == v) { have = true; break; }
            if (have) continue;
            for (TileView t : slots) {
                if (t.vIndex == NONE) {
                    int ai = WheelMath.wrap(v, n);
                    AppStore.App a = list.get(ai);
                    IconCache.Icon ic = icons.get(a.key);
                    t.bind(a, ic, store.hidden.contains(a.key), ai);
                    t.vIndex = v;
                    if (ic == null) icons.request(a);
                    break;
                }
            }
        }
        for (TileView t : slots) {
            int vis = t.vIndex != NONE ? View.VISIBLE : View.INVISIBLE;
            if (t.getVisibility() != vis) t.setVisibility(vis);
        }
    }

    private void buildGrid() {
        gridInner.removeAllViews();
        gridTiles.clear();
        int rows = (list.size() + COLS - 1) / COLS;
        float cam = camFor(Ui.px(GW));
        for (int i = 0; i < list.size(); i++) {
            AppStore.App a = list.get(i);
            TileView t = new TileView(this);
            t.focus = new Spring(engine, 300f, 0.7f, 0.002f);
            t.setCameraDistance(cam);
            t.bind(a, null, store.hidden.contains(a.key), i);
            gridInner.put(t, GX + (i % COLS) * GPX, GY + (i / COLS) * GPY, GW, GH);
            gridTiles.add(t);
        }
        gridInner.setLayoutParams(new Abs.LP(0, 0, Math.round(Ui.px(1920)),
                Math.round(Ui.px(GY + Math.max(1, rows) * GPY + 200))));
        gridFirstRow = -1;
        updateGridScroll(true);
    }

    private void updateGridScroll(boolean snap) {
        int rows = (list.size() + COLS - 1) / COLS;
        int r = mainIdx / COLS;
        int first = Math.max(0, Math.min(r - 1, Math.max(0, rows - 3)));
        float target = -first * Ui.px(GPY);
        if (snap) gridScroll.snap(target); else gridScroll.to(target);
        if (first != gridFirstRow) {
            gridFirstRow = first;
            bindGridRows();
        }
    }

    private void bindGridRows() {
        int lo = (gridFirstRow - 1) * COLS, hi = (gridFirstRow + 4) * COLS - 1;
        for (int i = 0; i < gridTiles.size(); i++) {
            TileView t = gridTiles.get(i);
            AppStore.App a = t.app;
            if (a == null) continue;
            if (i >= lo && i <= hi) {
                IconCache.Icon ic = icons.reuse(a.key, t.icon);
                t.setIcon(ic);
                if (ic == null) icons.request(a);
            } else {
                t.setIcon(null);
            }
        }
    }

    private void rebindIcons() {
        if (root == null) return;
        if (grid) bindGridRows();
        else for (TileView t : slots) {
            if (t.app == null || t.vIndex == NONE) continue;
            IconCache.Icon ic = icons.reuse(t.app.key, t.icon);
            t.setIcon(ic);
            if (ic == null) icons.request(t.app);
        }
        for (Chip c : dock) {
            if (c.app == null) continue;
            IconCache.Icon ic = icons.reuse(c.app.key, c.icon);
            c.setIcon(ic);
            if (ic == null) icons.request(c.app);
        }
    }

    /** Drop the cache and every bitmap that is not on screen right now. */
    private void releaseIcons() {
        icons.evictAll();
        if (root != null) {
            for (TileView t : slots) if (t.vIndex == NONE || Math.abs(t.vIndex - wheelTarget) > 2) t.setIcon(null);
            int lo = gridFirstRow * COLS, hi = (gridFirstRow + 3) * COLS - 1;
            for (int i = 0; i < gridTiles.size(); i++) if (i < lo || i > hi) gridTiles.get(i).setIcon(null);
        }
        iconsReleased = true;
    }

    @Override
    public void onIcon(String key, IconCache.Icon icon) {
        if (root == null || destroyed) return;
        if (!grid) {
            for (TileView t : slots) if (t.app != null && t.vIndex != NONE && key.equals(t.app.key)) t.setIcon(icon);
        } else {
            int lo = (gridFirstRow - 1) * COLS, hi = (gridFirstRow + 4) * COLS - 1;
            for (int i = Math.max(0, lo); i <= Math.min(gridTiles.size() - 1, hi); i++) {
                TileView t = gridTiles.get(i);
                if (t.app != null && key.equals(t.app.key)) t.setIcon(icon);
            }
        }
        for (Chip c : dock) if (c.app != null && key.equals(c.app.key)) c.setIcon(icon);
        if (quick != null) quick.invalidate();
        if (panel != null && panel.flyout && key.equals(panel.flyKey)) panel.setIcon(icon);
    }

    // ------------------------------------------------------------------ refresh

    private void refresh(boolean force) {
        if (work == null || destroyed) return;
        long now = SystemClock.uptimeMillis();
        if (!force && now - lastRefresh < 3000) return;
        if (refreshing) { refreshAgain = true; return; }
        refreshing = true;
        lastRefresh = now;
        final Context app = getApplicationContext();
        final boolean all = showAll;
        final ArrayList<String> extras = new ArrayList<>(store.extras);
        try {
            work.execute(() -> {
                ArrayList<AppStore.App> fresh = null;
                ArrayList<AppStore.Input> inputs = null;
                try {
                    fresh = AppStore.query(app, all, extras);
                    inputs = AppStore.queryInputs(app);
                } catch (Throwable ignored) {}
                final ArrayList<AppStore.App> f = fresh;
                final ArrayList<AppStore.Input> in = inputs == null ? new ArrayList<AppStore.Input>() : inputs;
                h.post(() -> {
                    refreshing = false;
                    if (destroyed) return;
                    if (f != null && (!f.isEmpty() || store.all.isEmpty())) {
                        AppStore.App cur = focusedApp();
                        if (store.apply(f, in)) rebuildLists(cur == null ? null : cur.key);
                    }
                    if (refreshAgain) { refreshAgain = false; refresh(true); }
                });
            });
        } catch (Throwable t) {
            refreshing = false;
        }
    }

    private void registerPackageReceiver() {
        pkgReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent i) {
                try {
                    Uri d = i.getData();
                    String pkg = d == null ? null : d.getSchemeSpecificPart();
                    if (pkg != null && icons != null) icons.forget(pkg);
                } catch (Throwable ignored) {}
                scanCache = null;
                psApps = null;
                h.removeCallbacks(pkgRefresh);
                h.postDelayed(pkgRefresh, 500);
            }
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

    private final Runnable pkgRefresh = () -> {
        rebindIcons();
        refresh(true);
    };

    // ------------------------------------------------------------------ frame loop

    @Override
    public boolean wantsFrames(long now) {
        return pan.moving || lockArmed || hum.active() || pointerAnim || now - tweenStart < TRAVEL_MS + 30
                || (panel != null && (panel.closing || panel.wantsFrames(now))) || (gyro != null && gyro.moving(now))
                || (quick != null && quick.animating()) || quickOut != null || (hudToast != null && hudToast.animating());
    }

    @Override
    public boolean onSlowFrames() {
        if (lite) return true;
        if (!resumed || panel != null) return false; // only judge plain home screen motion
        // v2: lite only drops scanlines, glitch, sheen and shadows. Pan and tilt keep working.
        lite = true;
        applyEffects();
        return true;
    }

    @Override
    public void onFrame(long now, float dt) {
        if (root == null) return;
        boolean d3 = fx3d();
        if (gyro != null && gyro.active) gyro.frame(dt, now);
        hum.update(now, dt);
        int ha = hum.poll(now);
        if (ha >= 0 && rumbleReady() && humOn) pad.hum(ha * rumble / 100);
        pan.update(dt);
        if (grid) applyGrid(d3, dt); else applyWheel(d3);
        boolean free = panel == null && quick == null;
        applyChip(netBtn, zone == Z_TOP && topIdx == 0 && free, d3);
        applyChip(sysBtn, zone == Z_TOP && topIdx == 1 && free, d3);
        for (int i = 0; i < dock.size(); i++) applyChip(dock.get(i), zone == Z_DOCK && dockIdx == i && free, d3);
        // 2.3 text and flyout animations, all on this frame's time
        if (panel != null) panel.stepAnim(now);
        if (quick != null) quick.stepAnim(now);
        if (quickOut != null) { quickOut.stepAnim(now); if (quickOut.gone()) removeQuickOut(); }
        if (hudToast != null) hudToast.stepAnim();
        applyPanel();
        applyFrame(now);
        float u = Ui.u;
        double px = pan.x * pan.scale * u + offX.value, py = pan.y * pan.scale * u + offY.value;
        if (!pano.isPlain()) pano.setPan(px, py);
        if (wall != null) wall.setPan(px, py);
    }

    private float gyroRx() { return gyro != null && gyro.active ? gyro.filter.rx : 0f; }

    private float gyroRy() { return gyro != null && gyro.active ? gyro.filter.ry : 0f; }

    private void applyWheel(boolean d3) {
        int n = list.size();
        float pos = wheelPos.value;
        if (n > 0) {
            int w = WheelMath.window(n, SLOTS), first = WheelMath.first(pos, n, SLOTS), have = 0;
            boolean stale = false;
            for (TileView t : slots) {
                if (t.vIndex == NONE) continue;
                if (t.vIndex < first || t.vIndex >= first + w) stale = true; else have++;
            }
            if (stale || have < w) bindWheel();
        }
        float u = Ui.u;
        boolean mainFocused = zone == Z_MAIN && panel == null;
        float grx = gyroRx(), gry = gyroRy();
        for (TileView t : slots) {
            if (t.vIndex == NONE) continue;
            float d = t.vIndex - pos, ad = Math.abs(d);
            if (ad > 3.6f) { t.setAlpha(0f); continue; }
            double th = Math.toRadians(d * STEP);
            float near1 = Math.max(0f, 1f - ad);
            float x = (float) Math.sin(th) * WR * u + (mainFocused ? gry * 0.9f * u * near1 : 0f);
            float y = (float) (1 - Math.cos(th)) * 44f * u;
            float sc = 1f / (1f + 0.32f * ad) * (1f + (mainFocused ? 0.06f : 0.02f) * near1);
            float w = Math.max(0f, 1f - 0.35f * ad);
            float ry = d3 ? d * STEP * 0.85f + tiltY.value * w + (mainFocused ? gry * near1 : 0f) : 0f;
            float rx = d3 ? tiltX.value * w + (mainFocused ? grx * near1 : 0f) : 0f;
            float al = Ui.clamp(1f - 0.25f * ad, 0f, 1f) * WheelMath.edgeAlpha(d, n, SLOTS);
            if (ad > 3f) al *= (3.6f - ad) / 0.6f;
            if (!mainFocused) al *= 0.85f;
            t.setTranslationX(x);
            t.setTranslationY(y);
            t.setScaleX(sc);
            t.setScaleY(sc);
            t.setRotationY(ry);
            t.setRotationX(rx);
            t.setAlpha(al);
            t.setTranslationZ((5f - ad) * 4f * u + (ad < 0.5f ? 14f * u : 0f));
            t.setShadow(d3 && !lite && ad < 0.5f);
        }
    }

    private void applyGrid(boolean d3, float dt) {
        gridInner.setTranslationY(gridScroll.value);
        int fc = mainIdx % COLS, fr = mainIdx / COLS;
        float u = Ui.u, grx = gyroRx(), gry = gyroRy();
        boolean mainFocused = zone == Z_MAIN && panel == null;
        boolean pushOn = pointerActive() && d3 && panel == null;
        float radius = 1.5f * GW * u, maxPush = 22f * u * pointerStrength / 100f;
        int lo = (gridFirstRow - 1) * COLS, hi = (gridFirstRow + 4) * COLS - 1;
        boolean anim = false;
        for (int i = 0; i < gridTiles.size(); i++) {
            TileView t = gridTiles.get(i);
            float f = t.focus.value;
            int c = i % COLS, r = i / COLS;
            float sc = 1f + 0.08f * f;
            t.setScaleX(sc);
            t.setScaleY(sc);
            t.setAlpha(mainFocused ? 0.66f + 0.34f * f : 0.6f);
            t.setTranslationZ(f * 20f * u);
            boolean me = i == mainIdx && mainFocused;
            // pointer push: tiles near the cursor move away; the hovered tile stays put and tilts instead
            float cxT = t.getLeft() + t.getWidth() / 2f, cyT = t.getTop() + t.getHeight() / 2f + gridScroll.value;
            if (pushOn && !me && i >= lo && i <= hi) PointerMath.push(cxT - cursorX, cyT - cursorY, radius, maxPush, push);
            else { push[0] = 0f; push[1] = 0f; }
            if (t.pdx != push[0] || t.pdy != push[1]) {
                float ox = t.pdx, oy = t.pdy;
                t.pdx = PointerMath.ease(t.pdx, push[0], 0.22f, dt);
                t.pdy = PointerMath.ease(t.pdy, push[1], 0.22f, dt);
                if (Math.abs(t.pdx - push[0]) < 0.1f && Math.abs(t.pdy - push[1]) < 0.1f) { t.pdx = push[0]; t.pdy = push[1]; }
                if (Math.abs(t.pdx - ox) + Math.abs(t.pdy - oy) > 0.05f) anim = true;
            }
            t.setTranslationX(t.pdx + (me ? gry * 0.9f * u : 0f));
            t.setTranslationY(t.pdy);
            float hrx = 0f, hry = 0f;
            if (me && pushOn) {
                // the hovered tile tilts toward the cursor: that edge goes away from the viewer
                float k = 8f * pointerStrength / 100f;
                hry = k * Ui.clamp((cursorX - cxT) / (t.getWidth() / 2f), -1f, 1f);
                hrx = k * Ui.clamp((cursorY - cyT) / (t.getHeight() / 2f), -1f, 1f);
            }
            if (d3) {
                float wy = r == fr ? Math.max(0f, 1f - Math.abs(c - fc) * 0.3f) : 0f;
                float wx = c == fc ? Math.max(0f, 1f - Math.abs(r - fr) * 0.45f) : 0f;
                if (i == mainIdx) { wy = 1f; wx = 1f; }
                t.setRotationY(tiltY.value * wy + (me ? gry + hry : 0f));
                t.setRotationX(tiltX.value * wx + (me ? grx + hrx : 0f));
            } else {
                t.setRotationY(0f);
                t.setRotationX(0f);
            }
            t.setShadow(d3 && !lite && f > 0.5f);
        }
        pointerAnim = anim;
    }

    private void applyChip(Chip c, boolean focused, boolean d3) {
        if (c == null || c.focus == null) return;
        c.focus.to(focused ? 1f : 0f);
        float f = c.focus.value;
        float sc = 1f + 0.07f * f;
        c.setScaleX(sc);
        c.setScaleY(sc);
        c.setAlpha(0.82f + 0.18f * f);
        c.setRotationY(d3 && focused ? tiltY.value * 0.6f + gyroRy() : 0f);
        c.setRotationX(d3 && focused ? tiltX.value * 0.6f + gyroRx() : 0f);
    }

    private void applyPanel() {
        if (panel == null || panel.open == null) { pushBack(0f); return; }
        if (panel.flyout) {
            // 2.3 flyout: it draws its own motion; the home screen dims (scrim) and pushes back
            panel.setAlpha(1f);
            panel.setScaleX(1f);
            panel.setScaleY(1f);
            pushBack(panel.dim());
            float away = panel.away();
            if (flySource != null && away > 0f) flySource.setAlpha(flySource.getAlpha() * (1f - 0.9f * away));
            if (panel.closing && panel.gone()) removePanelNow();
            return;
        }
        pushBack(0f);
        float v = Ui.clamp(panel.open.value, 0f, 1.2f);
        panel.setAlpha(Ui.clamp(v, 0f, 1f));
        float s = 0.96f + 0.04f * v;
        panel.setScaleX(s);
        panel.setScaleY(s);
        if (panel.closing && !panel.open.active && panel.open.value < 0.05f) removePanelNow();
    }

    /** Behind an open flyout the wheel or grid shrinks a little, as if pushed back. */
    private void pushBack(float k) {
        if (k <= 0f && !pushedBack) return;
        float sc = 1f - 0.04f * Ui.clamp(k, 0f, 1f);
        if (wheelBox != null) { wheelBox.setScaleX(sc); wheelBox.setScaleY(sc); }
        if (gridBox != null) { gridBox.setScaleX(sc); gridBox.setScaleY(sc); }
        pushedBack = k > 0f;
    }

    /** Takes the panel away now (end of its close, or a key during the close). */
    private void removePanelNow() {
        if (panel == null) return;
        Panel p = panel;
        Runnable after = p.afterClose;
        p.afterClose = null;
        root.removeView(p);
        panel = null;
        flySource = null;
        pushBack(0f);
        focusChanged(true);
        engine.kick();
        if (after != null) h.post(after);
    }

    /** Where the reticle should be, in root coordinates. */
    private boolean target(RectF out) {
        tRotX = 0; tRotY = 0; tCam = camFor(Ui.px(CW));
        float u = Ui.u;
        // 2.4.1: a full screen test, the quick menu (draws its own brackets) and a growing flyout own no reticle
        int owner = FrameGate.owner(test != null, quick != null, panel != null && !panel.closing, panel == null || panel.frameReady());
        if (owner == FrameGate.HIDDEN) return false;
        if (panel != null && !panel.closing) {
            panel.rowRect(panel.focus, out);
            float s = panel.getScaleX(), cx = Ui.W / 2f, cy = Ui.H / 2f;
            out.set(cx + (out.left - cx) * s, cy + (out.top - cy) * s, cx + (out.right - cx) * s, cy + (out.bottom - cy) * s);
            frame.setCut(0.30f, 0.08f);
            frame.setTag(null);
            return true;
        }
        switch (zone) {
            case Z_MAIN: {
                if (list.isEmpty()) return false;
                frame.setCut(TileView.CUT_BIG, TileView.CUT_SMALL);
                frame.setTag(mainTag);
                if (!grid) {
                    float w = CW * u * 1.06f, hh = CH * u * 1.06f;
                    out.set(WCX * u - w / 2, WCY * u - hh / 2, WCX * u + w / 2, WCY * u + hh / 2);
                    if (fx3d()) { tRotX = tiltX.value + gyroRx(); tRotY = tiltY.value + gyroRy(); }
                    return true;
                }
                if (mainIdx >= gridTiles.size()) return false;
                TileView t = gridTiles.get(mainIdx);
                float sc = t.getScaleX();
                float cx = t.getLeft() + t.getWidth() / 2f + t.getTranslationX();
                float cy = t.getTop() + t.getHeight() / 2f + gridScroll.value + t.getTranslationY();
                float w = t.getWidth() * sc, hh = t.getHeight() * sc;
                out.set(cx - w / 2, cy - hh / 2, cx + w / 2, cy + hh / 2);
                tRotX = t.getRotationX(); tRotY = t.getRotationY(); tCam = t.getCameraDistance();
                return true;
            }
            case Z_TOP: {
                chipRect(topIdx == 0 ? netBtn : sysBtn, out);
                frame.setCut(0.24f, 0.06f);
                frame.setTag(null);
                return true;
            }
            default: {
                if (dock.isEmpty()) return false;
                chipRect(dock.get(Math.min(dockIdx, dock.size() - 1)), out);
                frame.setCut(0.24f, 0.06f);
                frame.setTag(null);
                return true;
            }
        }
    }

    private void chipRect(Chip c, RectF out) {
        float sc = c.getScaleX();
        float cx = c.getLeft() + c.getWidth() / 2f, cy = c.getTop() + c.getHeight() / 2f;
        float w = c.getWidth() * sc, hh = c.getHeight() * sc;
        out.set(cx - w / 2, cy - hh / 2, cx + w / 2, cy + hh / 2);
        tRotX = c.getRotationX(); tRotY = c.getRotationY(); tCam = c.getCameraDistance();
    }

    private void applyFrame(long now) {
        boolean has = target(tr);
        frame.setVisibility(has ? View.VISIBLE : View.INVISIBLE);
        if (!has) {
            // 2.4.1: nothing waits to lock on behind a test screen, and the frame loop may sleep
            if (test != null) { lockArmed = false; frame.setTag(null); }
            return;
        }
        if (frameSnap) {
            cur.set(tr);
            from.set(tr);
            tweenStart = now - TRAVEL_MS;
            frameSnap = false;
            frame.setLocked(true);
            lockArmed = false;
        }
        // outline travels to the new element with an eased slide, brackets stay open
        float p = Ui.clamp((now - tweenStart) / (float) TRAVEL_MS, 0f, 1f);
        float e = 1f - (1f - p) * (1f - p) * (1f - p);
        cur.set(from.left + (tr.left - from.left) * e, from.top + (tr.top - from.top) * e,
                from.right + (tr.right - from.right) * e, from.bottom + (tr.bottom - from.bottom) * e);
        float m = Ui.px(FocusFrame.MARGIN);
        int w = Math.max(1, Math.round(cur.width() + m * 2)), hh = Math.max(1, Math.round(cur.height() + m * 2));
        Abs.setBoundsNow(frame, 0, 0, w, hh);
        frame.setPivotX(w / 2f);
        frame.setPivotY(hh / 2f);
        frame.setTranslationX(cur.centerX() - w / 2f);
        frame.setTranslationY(cur.centerY() - hh / 2f);
        frame.setCameraDistance(tCam);
        frame.setRotationX(tRotX);
        frame.setRotationY(tRotY);
        frame.setTranslationZ(Ui.px(60));

        // lock-on plays once: after arrival and about 120 ms of idle input, never while held
        boolean wheelSettled = grid || zone != Z_MAIN || panel != null || Math.abs(wheelPos.value - wheelTarget) < 0.06f;
        boolean held = keyHeld || dial.active || navDx != 0 || navDy != 0;
        if (lockArmed && p >= 1f && wheelSettled && !held && now - lastInput >= IDLE_MS) {
            lockArmed = false;
            frame.lock(now);
            fx(SoundMap.LOCK_ON);
            pulseLight();
            flashPlayers();
        }

        boolean ptr = pointerActive();
        if (ptr && fx3d()) frame.setArc(PointerMath.angle(cur.centerX(), cur.centerY(), cursorX, cursorY), 1f);
        else frame.setArc(0f, 0f);

        // top bar, settings and pointer: a small static offset by focus or cursor position
        float ox = 0f, oy = 0f;
        if (panel != null || zone == Z_TOP || ptr) {
            float s = (panFollow ? 1f : -1f) * (float) pan.scale;
            float px = ptr ? cursorX : cur.centerX(), py = ptr ? cursorY : cur.centerY();
            ox = s * Ui.clamp((px - Ui.W / 2f) / (Ui.W / 2f), -1f, 1f) * Ui.px(28);
            oy = s * Ui.clamp((py - Ui.H / 2f) / (Ui.H / 2f), -1f, 1f) * Ui.px(18);
        }
        offX.to(ox);
        offY.to(oy);
    }

    // ------------------------------------------------------------------ focus

    AppStore.App focusedApp() {
        if (zone == Z_MAIN && mainIdx >= 0 && mainIdx < list.size()) return list.get(mainIdx);
        if (zone == Z_DOCK && dockIdx < dock.size()) {
            AppStore.App a = dock.get(dockIdx).app;
            if (a != null) return a;
        }
        return mainIdx >= 0 && mainIdx < list.size() ? list.get(mainIdx) : null;
    }

    private void setZone(int z, int dx, int dy) {
        if (z == Z_MAIN && list.isEmpty()) z = dy < 0 ? Z_TOP : Z_DOCK;
        if (z == Z_DOCK && dock.isEmpty()) return;
        zone = z;
        fx(SoundMap.MOVE);
        moved(dx, dy);
    }

    /** After any focus move, with the D-pad direction that caused it. */
    private void moved(int dx, int dy) {
        if (fx3d()) {
            // +rotationY sends the right edge away, -rotationX the top edge: cards lean into the move
            if (dx != 0) tiltY.kick(dx * 230f);
            if (dy != 0) tiltX.kick(dy * 210f);
        }
        if (panel == null && zone != Z_TOP) pan.step(dx, dy);
        focusChanged(true);
    }

    private void bump(int dx, int dy) {
        if (fx3d()) {
            if (dx != 0) tiltY.kick(-dx * 120f);
            if (dy != 0) tiltX.kick(-dy * 110f);
        }
        engine.kick();
    }

    private void focusChanged(boolean animate) {
        if (root == null) return;
        long now = SystemClock.uptimeMillis();
        if (grid) {
            for (int i = 0; i < gridTiles.size(); i++) {
                TileView t = gridTiles.get(i);
                float tgt = (i == mainIdx && zone == Z_MAIN) ? 1f : 0f;
                if (animate) t.focus.to(tgt); else t.focus.snap(tgt);
            }
            updateGridScroll(!animate);
        } else {
            if (animate) wheelPos.to(wheelTarget); else wheelPos.snap(wheelTarget);
        }
        mainTag = list.isEmpty() ? "" : String.format(Locale.ROOT, "%02d/%02d", mainIdx + 1, list.size());
        String lbl;
        if (quick != null) {
            lbl = quick.focusName();
        } else if (panel != null && !panel.closing) {
            Panel.Row r = panel.current();
            lbl = panel.focus == -1 && panel.hasTabs() ? panel.tabNames.get(panel.tab) : (r == null ? "" : r.label);
        } else if (zone == Z_TOP) {
            lbl = topIdx == 0 ? "NETWORK" : "VISOR SETUP";
        } else if (zone == Z_DOCK && dockIdx < dock.size()) {
            Chip c = dock.get(dockIdx);
            lbl = c.app != null ? c.app.label : c.label;
        } else {
            AppStore.App a = mainIdx < list.size() ? list.get(mainIdx) : null;
            lbl = a == null ? "" : a.label;
        }
        focusLabel.set("> ", lbl.toUpperCase(Locale.ROOT));
        if (animate) focusLabel.glitch();
        if (playerLedMode == PlayerLeds.WHEEL) updatePlayerLeds();
        if (!grid) {
            AppStore.App a = mainIdx < list.size() ? list.get(mainIdx) : null;
            String nt = a == null ? "" : a.label.toUpperCase(Locale.ROOT);
            boolean changed = !nt.equals(title.text);
            title.set(nt);
            if (a != null) {
                String kind = a.system ? "SYSTEM APP" : "USER APP";
                if (store.hidden.contains(a.key)) kind += "  //  HIDDEN";
                String hold = mode == InputMode.GAMEPAD ? (glyphStyle == InputMode.STYLE_SONY ? "SQUARE" : "X") + " FOR OPTIONS" : "HOLD OK FOR OPTIONS";
                sub.set(String.format(Locale.ROOT, "%02d / %02d  //  %s  //  %s", mainIdx + 1, list.size(), kind, hold));
            } else {
                sub.set("");
            }
            if (animate && changed) title.glitch();
        }
        if (animate) {
            from.set(cur);
            tweenStart = now;
            frame.unlock(now);
            lockArmed = true;
        } else {
            frameSnap = true;
        }
        engine.kick();
    }

    // ------------------------------------------------------------------ input mode

    private void trackInput(InputEvent e, int keyCode) {
        lastInput = SystemClock.uptimeMillis();
        InputDevice dev = e.getDevice();
        int devSrc = dev == null ? 0 : dev.getSources();
        boolean virt = dev == null || dev.isVirtual();
        boolean btn = keyCode != 0 && KeyEvent.isGamepadButton(keyCode);
        setMode(InputMode.classify(e.getSource(), devSrc, btn, virt), dev);
    }

    private void setMode(int m, InputDevice dev) {
        if (m == InputMode.GAMEPAD && dev != null && !dev.isVirtual()) {
            int st = InputMode.style(dev.getVendorId());
            boolean changedPad = dev.getId() != padId || st != glyphStyle;
            padId = dev.getId();
            glyphStyle = st;
            if (changedPad && m == mode) {
                hud.setMode(mode, glyphStyle, stickRight, false);
                if (panel != null) panel.setGamepad(true, glyphStyle);
                updateGyro();
                updatePad();
            }
        }
        if (m == mode) return;
        mode = m;
        if (m != InputMode.POINTER) {
            cursorIn = false;
            if (glow != null) glow.setVisibility(View.GONE);
            engine.kick();
        }
        hud.setMode(mode, glyphStyle, stickRight, true);
        if (panel != null) panel.setGamepad(mode == InputMode.GAMEPAD, glyphStyle);
        status.glitch();
        updateStatus();
        focusChanged(false);
        updateGyro();
        updatePad();
        fx(mode == InputMode.GAMEPAD ? SoundMap.MODE_GAMEPAD : SoundMap.MODE_REMOTE);
    }

    private void updateGyro() {
        if (gyro == null) return;
        boolean want = resumed && gyroOn && !reduce && mode == InputMode.GAMEPAD && padId >= 0 && test == null;
        try {
            if (want && (!gyro.active || gyro.deviceId != padId)) gyro.start(padId);
            else if (!want && gyro.active) gyro.stop();
        } catch (Throwable ignored) {}
        engine.kick();
    }

    // ------------------------------------------------------------------ controller extras

    /** Sound plus the matching rumble pattern. */
    void fx(int ev) {
        sounds.play(ev);
        if (ev >= 0 && ev < HapticPatterns.FOR_SOUND.length) haptic(HapticPatterns.FOR_SOUND[ev]);
    }

    private void tickFx(boolean heavy) {
        sounds.play(SoundMap.WHEEL_TICK);
        haptic(heavy ? HapticPatterns.DETENT_HEAVY : HapticPatterns.TICK);
    }

    /** Every 5th item, and the wrap from last to first (or back), gets a heavier detent. */
    private boolean heavyDetent(int dir, int n) {
        return mainIdx % 5 == 0 || (dir > 0 && mainIdx == 0) || (dir < 0 && mainIdx == n - 1);
    }

    private boolean rumbleReady() {
        return pad != null && pad.hasVibrators() && hapticsOn && rumble > 0 && mode == InputMode.GAMEPAD;
    }

    private void haptic(int pattern) {
        if (pattern < 0 || !rumbleReady()) return;
        pad.play(pattern, feel, rumble, humOn ? hum.current() * rumble / 100 : 0);
    }

    private void updatePad() {
        if (pad == null) return;
        boolean want = resumed && mode == InputMode.GAMEPAD && padId >= 0;
        try {
            if (want) {
                if (pad.deviceId != padId) pad.attach(InputDevice.getDevice(padId));
                applyLight();
                updatePlayerLeds();
            } else if (pad.deviceId >= 0) {
                pad.detach();
            }
        } catch (Throwable ignored) {}
    }

    private int lightColor(int brightness) {
        int c = lightMode == 2 ? Ui.hsv(lightHsv[0], lightHsv[1] / 100f, lightHsv[2] / 100f) : Ui.T.a;
        float k = Math.max(0, Math.min(100, brightness)) / 100f;
        return Color.argb(255, Math.round(Color.red(c) * k), Math.round(Color.green(c) * k), Math.round(Color.blue(c) * k));
    }

    void applyLight() {
        if (pad == null || pad.deviceId < 0) return;
        try {
            if (lightMode == 0 || mode != InputMode.GAMEPAD) pad.releaseLight();
            else pad.setLight(lightColor(lightBright));
        } catch (Throwable ignored) {}
    }

    private final Runnable lightRestore = this::applyLight;

    /** Wheel position or battery on the five player LEDs. Only sends when the pattern changes. */
    void updatePlayerLeds() {
        if (pad == null || pad.deviceId < 0 || !pad.hasPlayerLeds()) return;
        try {
            switch (playerLedMode) {
                case PlayerLeds.WHEEL:
                    pad.setPlayerMask(PlayerLeds.wheelMask(mainIdx, list.size(), PlayerLeds.LEDS));
                    break;
                case PlayerLeds.BATTERY:
                    pad.setPlayerMask(PlayerLeds.batteryMask(pad.battery(), PlayerLeds.LEDS));
                    break;
                default:
                    if (!flashOn) pad.restorePlayers(); // OFF and LOCK_FLASH idle: the system's player number
                    break;
            }
        } catch (Throwable ignored) {}
    }

    private boolean flashOn;
    private final Runnable flashEnd = () -> { flashOn = false; updatePlayerLeds(); };

    /** Lock-on flash: all five LEDs blink once. Reduce effects turns it off. */
    private void flashPlayers() {
        if (playerLedMode != PlayerLeds.LOCK_FLASH || reduce || pad == null || !pad.hasPlayerLeds() || mode != InputMode.GAMEPAD) return;
        try {
            flashOn = true;
            pad.setPlayerMask(PlayerLeds.ALL);
            h.removeCallbacks(flashEnd);
            h.postDelayed(flashEnd, 170);
        } catch (Throwable ignored) {
            flashOn = false;
        }
    }

    // ------------------------------------------------------------------ pointer mode

    float glowRadius() { return 1.5f * GW * Math.max(50, Math.min(150, glowSize)) / 100f; }

    boolean pointerActive() { return mode == InputMode.POINTER && cursorIn && pointerFx; }

    void applyCursorIcon() {
        if (root == null) return;
        try {
            PointerIcon pi = null;
            if (hideCursor) pi = PointerIcon.getSystemIcon(this, PointerIcon.TYPE_NULL);
            else if (themedCursor) pi = CursorIcon.get(!reduce);
            root.setPointerIcon(pi);
        } catch (Throwable ignored) {}
    }

    private void enterPointer(InputDevice dev) {
        lastInput = SystemClock.uptimeMillis();
        if (mode != InputMode.POINTER) setMode(InputMode.POINTER, dev);
    }

    private void pointerEvent(MotionEvent e) {
        pointerEvents++;
        int act = e.getActionMasked();
        switch (act) {
            case MotionEvent.ACTION_HOVER_ENTER:
            case MotionEvent.ACTION_HOVER_MOVE: {
                enterPointer(e.getDevice());
                cursorIn = true;
                cursorX = e.getX();
                cursorY = e.getY();
                boolean show = pointerFx && fx3d();
                if (show) {
                    if (glow.getVisibility() != View.VISIBLE) glow.setVisibility(View.VISIBLE);
                    glow.moveTo(cursorX, cursorY);
                } else if (glow.getVisibility() != View.GONE) {
                    glow.setVisibility(View.GONE);
                }
                hoverAt(cursorX, cursorY);
                engine.kick();
                break;
            }
            case MotionEvent.ACTION_HOVER_EXIT:
                cursorIn = false;
                glow.setVisibility(View.GONE);
                engine.kick();
                break;
            case MotionEvent.ACTION_SCROLL: {
                enterPointer(e.getDevice());
                float v = e.getAxisValue(MotionEvent.AXIS_VSCROLL), hs = e.getAxisValue(MotionEvent.AXIS_HSCROLL);
                scrollAcc += Math.abs(hs) > Math.abs(v) ? hs : -v;
                if (Math.abs(scrollAcc) >= 1f) finishAnims();
                while (scrollAcc >= 1f) { scrollAcc -= 1f; pointerScroll(1); }
                while (scrollAcc <= -1f) { scrollAcc += 1f; pointerScroll(-1); }
                break;
            }
            case MotionEvent.ACTION_BUTTON_PRESS: {
                enterPointer(e.getDevice());
                int b = e.getActionButton();
                finishAnims();   // 2.3: a click finishes any running animation, then applies
                if (b == MotionEvent.BUTTON_SECONDARY || b == MotionEvent.BUTTON_BACK) { back(); break; }
                lastButtonPress = SystemClock.uptimeMillis();
                pointerClick(e.getX(), e.getY());
                break;
            }
            default:
                break;
        }
    }

    private boolean inside(View v, float x, float y) {
        return v != null && v.getVisibility() == View.VISIBLE && x >= v.getLeft() && x < v.getRight() && y >= v.getTop() && y < v.getBottom();
    }

    private void pointerFocus(int z, int idx) {
        boolean same = zone == z && (z == Z_TOP ? topIdx == idx : z == Z_DOCK ? dockIdx == idx : mainIdx == idx || !grid);
        if (same) return;
        zone = z;
        if (z == Z_TOP) topIdx = idx;
        else if (z == Z_DOCK) dockIdx = idx;
        else if (grid) mainIdx = idx;
        sounds.play(SoundMap.MOVE);
        focusChanged(true);
    }

    /** Hover to focus. @return true if the cursor is over something focusable. */
    private boolean hoverAt(float x, float y) {
        if (quick != null) {
            int r = quick.hit(x, y);
            if (r >= 0 && quick.focusRow(r)) { sounds.play(SoundMap.MOVE); focusChanged(true); }
            return r >= 0 || r == -2;
        }
        if (panel != null && !panel.closing) {
            int r = panel.hit(x, y);
            if (r >= 0) {
                if (panel.focusRow(r)) { sounds.play(SoundMap.MOVE); focusChanged(true); }
                return true;
            }
            return r == -2;
        }
        if (inside(netBtn, x, y)) { pointerFocus(Z_TOP, 0); return true; }
        if (inside(sysBtn, x, y)) { pointerFocus(Z_TOP, 1); return true; }
        for (int i = 0; i < dock.size(); i++) if (inside(dock.get(i), x, y)) { pointerFocus(Z_DOCK, i); return true; }
        if (list.isEmpty()) return false;
        float u = Ui.u;
        if (grid) {
            if (y < Ui.px(198) || y > Ui.px(846)) return false;
            float gx = (x - GX * u) / (GPX * u), gy = (y - gridScroll.value - GY * u) / (GPY * u);
            int c = (int) Math.floor(gx), r = (int) Math.floor(gy);
            if (c < 0 || c >= COLS || r < 0) return false;
            if ((gx - c) * GPX > GW || (gy - r) * GPY > GH) return false;
            int i = r * COLS + c;
            if (i >= list.size()) return false;
            pointerFocus(Z_MAIN, i);
            return true;
        }
        float w = CW * u * 0.53f, hh = CH * u * 0.53f;
        if (Math.abs(x - WCX * u) < w && Math.abs(y - WCY * u) < hh) { pointerFocus(Z_MAIN, mainIdx); return true; }
        return false;
    }

    /** Wheel tile under the cursor (nearest to the center wins), or null. */
    private TileView wheelTileAt(float x, float y) {
        TileView best = null;
        float bd = Float.MAX_VALUE;
        for (TileView t : slots) {
            if (t.vIndex == NONE || t.getAlpha() < 0.2f) continue;
            t.getHitRect(tmpRect);
            if (!tmpRect.contains((int) x, (int) y)) continue;
            float d = Math.abs(t.vIndex - wheelPos.value);
            if (d < bd) { bd = d; best = t; }
        }
        return best;
    }

    private final Rect tmpRect = new Rect();

    private void pointerClick(float x, float y) {
        if (quick != null) {
            int r = quick.hit(x, y);
            if (r >= 0) { quick.focusRow(r); click(); }
            else if (r <= -QuickMenu.TAB_HIT) { if (quick.tabClick(-(r + QuickMenu.TAB_HIT))) { fx(SoundMap.TOGGLE); focusChanged(true); } }
            else if (r == -1) closeQuick(true);
            return;
        }
        if (panel != null && !panel.closing) {
            int r = panel.hit(x, y);
            if (r >= 0) { panel.focusRow(r); click(); }
            else if (r == -2) { if (panel.selectTab(panel.hitTab)) { fx(SoundMap.TOGGLE); focusChanged(true); } }
            return;
        }
        if (!grid && !list.isEmpty()) {
            TileView t = wheelTileAt(x, y);
            if (t != null && t.vIndex != wheelTarget) {
                // click a neighbor: spin it to the center
                int n = list.size(), d = t.vIndex - wheelTarget;
                zone = Z_MAIN;
                wheelTarget = t.vIndex;
                mainIdx = WheelMath.wrap(wheelTarget, n);
                tickFx(heavyDetent(d, n));
                moved(d > 0 ? 1 : -1, 0);
                return;
            }
            if (t != null) {
                pointerFocus(Z_MAIN, mainIdx);
                click();
                return;
            }
        }
        if (!hoverAt(x, y)) return;
        click();
    }

    private void pointerScroll(int dir) {
        if (quick != null) { quickNav(0, dir); return; }
        if (panel != null && !panel.closing) {
            if (panel.move(dir)) { sounds.play(SoundMap.MOVE); focusChanged(true); } else if (panel.scrollBy(dir)) focusChanged(true);
            return;
        }
        if (list.isEmpty()) return;
        if (zone != Z_MAIN) { zone = Z_MAIN; focusChanged(true); }
        if (!grid) { navLinear(dir); return; }
        int n = list.size(), nr = mainIdx / COLS + dir, c = mainIdx % COLS;
        if (nr < 0 || nr * COLS >= n) { bump(0, dir); return; }
        mainIdx = Math.min(nr * COLS + c, n - 1);
        sounds.play(SoundMap.MOVE);
        moved(0, dir);
    }

    private String pointerStatus() {
        return pointerEvents > 0 ? "YES (" + pointerEvents + " EVENTS)" : "NO, NO HOVER EVENTS YET";
    }

    private void pulseLight() {
        if (!lightPulse || lightMode == 0 || pad == null || !pad.hasLight() || mode != InputMode.GAMEPAD) return;
        try { pad.setLight(lightColor(100)); } catch (Throwable ignored) {}
        h.removeCallbacks(lightRestore);
        h.postDelayed(lightRestore, 160);
    }

    private String padStatus(boolean light) {
        if (pad == null) return "NEEDS ANDROID 12";
        if (pad.deviceId < 0) {
            if (padId >= 0) {
                try { pad.attach(InputDevice.getDevice(padId)); } catch (Throwable ignored) {}
            }
            if (pad.deviceId < 0) return "PRESS A GAMEPAD BUTTON TO DETECT";
        }
        return light ? pad.lightStatus : pad.hapticStatus;
    }

    private String gyroStatus() {
        if (gyro == null) return "NEEDS ANDROID 12";
        try {
            return gyro.active ? "FOUND, ACTIVE" : gyro.probe(padId);
        } catch (Throwable t) {
            return "UNAVAILABLE";
        }
    }

    // ------------------------------------------------------------------ keys

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (safeMode) {
            int k = e.getKeyCode();
            if (k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_ESCAPE) return true;
            return super.dispatchKeyEvent(e);
        }
        if (root == null) return super.dispatchKeyEvent(e);
        stickVol.onKey(e);   // 2.10 stick volume: tracks the hold key before any screen can use the key, never takes it
        if (preview != null) {
            if (preview.wakeKey(e)) stopPreview(true);
            return true;
        }
        if (test != null) {
            int k = e.getKeyCode();
            // volume keys from the remote still reach the system; everything else stays in the test
            boolean vol = k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_VOLUME_DOWN || k == KeyEvent.KEYCODE_VOLUME_MUTE;
            if (vol && !test.fromTestPad(e) && !test.wantsAllKeys()) return super.dispatchKeyEvent(e);
            lastInput = SystemClock.uptimeMillis();
            if (k == KeyEvent.KEYCODE_BUTTON_MODE && !psSeen) { psSeen = true; prefs.edit().putBoolean("psSeen", true).apply(); }
            try { test.key(e); } catch (Throwable t) { closeTest("Test closed after an error"); }
            return true;
        }
        // 2.6 button remap: the rules decide first. A taken button never reaches the code below, a swapped one
        // goes on as the key it is read as (inside Deck only). Any error here lets the key through untouched.
        try {
            if (comboDeck(e)) return true;
            int rr = remapDeck(e);
            if (rr == 1) return true;
            if (rr == 2 && remapSwapped != null) e = remapSwapped;
        } catch (Throwable t) {
            if (remapGate != null) remapGate.reset();
        }
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        boolean first = down && e.getRepeatCount() == 0;
        try {
            // 2.3: never block input. A key press finishes any running animation at once, then applies.
            if (keepView == null && touchVol.onKey(this, e)) return true;   // 2.8 touchpad volume fallback
            if (keepView != null) {
                // 2.8: the KEEP or REVERT prompt of a game profile owns every key
                if (down && first) {
                    if (code == KeyEvent.KEYCODE_BUTTON_A || code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER) ProfileRunner.get(this).keep();
                    else if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_BUTTON_B || code == KeyEvent.KEYCODE_ESCAPE) ProfileRunner.get(this).revert();
                }
                return true;
            }
            if (first) finishAnims();
            if (first) trackInput(e, code);
            else lastInput = SystemClock.uptimeMillis();
            if (down && e.getRepeatCount() > 0) keyHeld = true;
            if (!down) keyHeld = false;
            if (code == KeyEvent.KEYCODE_BUTTON_MODE) {
                // PS button on a DualSense. Some boxes swallow it; Setup shows whether it ever arrived.
                if (!psSeen) { psSeen = true; prefs.edit().putBoolean("psSeen", true).apply(); }
                if (first) { if (quick != null) closeQuick(true); else openQuick(); }
                return true;
            }
            if (quick != null && (code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_BUTTON_START)) {
                if (!down) { closeQuick(false); openSettings(T_LOOK, null); }
                return true;
            }
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_LEFT:  if (down) nav(-1, 0); return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT: if (down) nav(1, 0); return true;
                case KeyEvent.KEYCODE_DPAD_UP:    if (down) nav(0, -1); return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:  if (down) nav(0, 1); return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                case KeyEvent.KEYCODE_BUTTON_A:
                    okKey(e);
                    return true;
                case KeyEvent.KEYCODE_BACK:
                case KeyEvent.KEYCODE_ESCAPE:
                case KeyEvent.KEYCODE_BUTTON_B:
                    backKey(e);
                    return true;
                case KeyEvent.KEYCODE_MENU:
                case KeyEvent.KEYCODE_BUTTON_START:
                    if (!down) {
                        if (panel != null && !panel.closing && "settings".equals(panel.kind)) closePanel();
                        else openSettings(T_LOOK, null);
                    }
                    return true;
                case KeyEvent.KEYCODE_BUTTON_X:
                    if (first && quick == null && (panel == null || panel.closing)) longPress();
                    return true;
                case KeyEvent.KEYCODE_BUTTON_Y:
                    if (first && quick == null && (panel == null || panel.closing)) setLayout(!grid);
                    return true;
                case KeyEvent.KEYCODE_BUTTON_L1:
                case KeyEvent.KEYCODE_BUTTON_R1:
                    if (down) shoulder(code == KeyEvent.KEYCODE_BUTTON_L1 ? -1 : 1);
                    return true;
                case KeyEvent.KEYCODE_BUTTON_SELECT:
                    if (first && quick == null && (panel == null || panel.closing)) setStats(!statsOn);
                    return true;
                case KeyEvent.KEYCODE_BUTTON_L2:
                case KeyEvent.KEYCODE_BUTTON_R2:
                case KeyEvent.KEYCODE_BUTTON_THUMBL:
                case KeyEvent.KEYCODE_BUTTON_THUMBR:
                    return true;
                default:
                    break;
            }
        } catch (Throwable t) {
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    private void shoulder(int d) {
        if (quick != null) { if (quick.tab(d)) { fx(SoundMap.TOGGLE); focusChanged(true); } else bump(d, 0); return; }
        if (panel != null && !panel.closing) {
            if (panel.switchTab(d)) { fx(SoundMap.TOGGLE); moved(d, 0); }
            else bump(d, 0);
            return;
        }
        if (zone == Z_MAIN && !list.isEmpty()) {
            for (int i = 0; i < (grid ? COLS : 5); i++) navLinear(d);
        }
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent e) {
        if (safeMode || root == null) return super.dispatchGenericMotionEvent(e);
        try { if (touchVol.enabled()) touchVol.onMotion(this, e); } catch (Throwable ignored) {}   // 2.8 touchpad volume
        // 2.10 stick volume: while the hold key is down the right stick spins the volume and does nothing else
        try { if (stickVol.held() && preview == null && test == null && stickVol.onMotion(this, e)) return true; } catch (Throwable ignored) {}
        if (preview != null) {
            if (preview.wakeMotion(e)) stopPreview(true);
            return true;
        }
        if (test != null) {
            try {
                if (InputMode.has(e.getSource(), InputMode.CLASS_POINTER)) test.pointer(e); else test.joystick(e);
            } catch (Throwable t) { closeTest("Test closed after an error"); }
            return true;
        }
        if (InputMode.has(e.getSource(), InputMode.CLASS_POINTER)) {
            try { pointerEvent(e); } catch (Throwable ignored) {}
            return true;
        }
        if (InputMode.has(e.getSource(), InputMode.SRC_JOYSTICK) && e.getAction() == MotionEvent.ACTION_MOVE) {
            try { joystick(e); } catch (Throwable ignored) {}
            return true;
        }
        return super.dispatchGenericMotionEvent(e);
    }

    /** Mouse or touchpad clicks also arrive as touch events: click once on release if no button event came. */
    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        if (safeMode || root == null) return super.dispatchTouchEvent(e);
        try { if (touchVol.enabled()) touchVol.onMotion(this, e); } catch (Throwable ignored) {}   // 2.8 touchpad volume
        if (preview != null) {
            if (preview.wakeTouch(e)) stopPreview(true);
            return true;
        }
        if (test != null) {
            try { test.touch(e); } catch (Throwable t) { closeTest("Test closed after an error"); }
            return true;
        }
        try {
            int src = e.getSource();
            if (InputMode.has(src, InputDevice.SOURCE_MOUSE) || InputMode.has(src, InputDevice.SOURCE_TOUCHPAD)) {
                pointerEvents++;
                if (e.getActionMasked() == MotionEvent.ACTION_UP && SystemClock.uptimeMillis() - lastButtonPress > 400) {
                    finishAnims();
                    enterPointer(e.getDevice());
                    cursorX = e.getX();
                    cursorY = e.getY();
                    pointerClick(cursorX, cursorY);
                }
                return true;
            }
        } catch (Throwable ignored) {}
        return super.dispatchTouchEvent(e);
    }

    private static float axis(MotionEvent e, InputDevice dev, int axis) {
        float v = e.getAxisValue(axis);
        if (dev != null) {
            InputDevice.MotionRange r = dev.getMotionRange(axis, e.getSource());
            if (r != null && Math.abs(v) <= r.getFlat()) return 0f;
        }
        return v;
    }

    private void joystick(MotionEvent e) {
        InputDevice dev = e.getDevice();
        boolean zr = dev == null || dev.getMotionRange(MotionEvent.AXIS_Z, e.getSource()) != null;
        float lx = axis(e, dev, MotionEvent.AXIS_X), ly = axis(e, dev, MotionEvent.AXIS_Y);
        float rx = axis(e, dev, zr ? MotionEvent.AXIS_Z : MotionEvent.AXIS_RX);
        float ry = axis(e, dev, zr ? MotionEvent.AXIS_RZ : MotionEvent.AXIS_RY);
        float hx = e.getAxisValue(MotionEvent.AXIS_HAT_X), hy = e.getAxisValue(MotionEvent.AXIS_HAT_Y);
        // Ignore drift and trigger noise: only a real push counts as input (keeps lock-on and mode stable).
        boolean real = Math.max(Math.max(Math.abs(lx), Math.abs(ly)), Math.max(Math.abs(rx), Math.abs(ry))) > StickDial.DEAD_IN
                || Math.abs(hx) > 0.5f || Math.abs(hy) > 0.5f || dial.active || navDx != 0 || navDy != 0;
        if (!real) return;
        lastInput = SystemClock.uptimeMillis();
        setMode(InputMode.GAMEPAD, dev);

        // 2.2: the WHEEL STICK is the spinner. It only scrolls items in what has focus and never
        // changes zone, tab or screen (StickRoute). Rotating it around the rim gives detent ticks.
        dial.update(stickRight ? rx : lx, stickRight ? ry : ly, e.getEventTime());
        if (dial.ticks != 0) spinTicks(dial.ticks);
        if (dial.flickX != 0 || dial.flickY != 0) spinFlick(dial.flickX, dial.flickY);

        // the other stick and the hat behave like a D-pad with repeat
        int ndx = hx > 0.5f ? 1 : (hx < -0.5f ? -1 : 0), ndy = hy > 0.5f ? 1 : (hy < -0.5f ? -1 : 0);
        if (ndx == 0 && ndy == 0) {
            float ax = stickRight ? lx : rx, ay = stickRight ? ly : ry;
            float th = (navDx != 0 || navDy != 0) ? 0.4f : 0.6f;
            if (Math.max(Math.abs(ax), Math.abs(ay)) > th) {
                if (Math.abs(ax) >= Math.abs(ay)) ndx = ax > 0 ? 1 : -1; else ndy = ay > 0 ? 1 : -1;
            }
        }
        setNavDir(ndx, ndy);
    }

    private void setNavDir(int dx, int dy) {
        if (dx == navDx && dy == navDy) return;
        navDx = dx;
        navDy = dy;
        h.removeCallbacks(navRepeat);
        if (dx != 0 || dy != 0) {
            finishAnims();
            nav(dx, dy);
            h.postDelayed(navRepeat, 380);
        } else {
            keyHeld = false;
        }
    }

    private final Runnable navRepeat = new Runnable() {
        @Override public void run() {
            if ((navDx == 0 && navDy == 0) || destroyed) return;
            keyHeld = true;
            lastInput = SystemClock.uptimeMillis();
            nav(navDx, navDy);
            h.postDelayed(this, 110);
        }
    };

    /** Where the spinner stick acts right now. */
    private int spinContext() {
        if (quick != null || (panel != null && !panel.closing)) return StickRoute.CTX_LIST;
        if (zone == Z_MAIN && !list.isEmpty()) return grid ? StickRoute.CTX_GRID : StickRoute.CTX_WHEEL;
        return StickRoute.CTX_OTHER_ZONE;
    }

    /** Spinner rotation: items move at most at the WHEEL STICK SPEED cap. Never leaves the list. */
    private void spinTicks(int t) {
        finishAnims();
        int ctx = spinContext();
        if (StickRoute.action(StickRoute.SPINNER, ctx) == StickRoute.A_NONE) { bump(t > 0 ? 1 : -1, 0); return; }
        long now = SystemClock.uptimeMillis();
        float rate = StickRoute.rate(ctx, stickSpeed);
        int ok = spinLimit.take(t, now, rate);
        if (ok == 0) return;
        int s = ok > 0 ? 1 : -1;
        if (ctx != StickRoute.CTX_LIST) hum.scaleTo(rate);
        for (int i = 0; i < Math.abs(ok); i++) {
            if (ctx != StickRoute.CTX_LIST) hum.onTick(now);   // the hum follows the items that really move
            spinStep(ctx, s);
        }
    }

    /** One spinner item step inside the current list. */
    private void spinStep(int ctx, int s) {
        switch (ctx) {
            case StickRoute.CTX_LIST:
                if (quick != null) {
                    if (quick.move(s)) { tickFx(false); focusChanged(true); } else bump(0, s);
                } else if (panel != null) {
                    if (StickRoute.listStep(panel.focus, panel.rows().size(), s) == panel.focus) bump(0, s);
                    else if (panel.move(s)) { tickFx(false); moved(0, s); } else bump(0, s);
                }
                break;
            case StickRoute.CTX_WHEEL:
                wheelNav(s, 0);                    // left or right only: the wheel wraps forever
                break;
            case StickRoute.CTX_GRID: {
                int n = list.size(), ni = StickRoute.gridStep(mainIdx, n, s);
                if (ni == mainIdx) { bump(s, 0); break; }
                mainIdx = ni;
                tickFx(heavyDetent(s, n));
                moved(s, 0);
                break;
            }
            default:
                break;
        }
    }

    /** A quick push and release of the spinner: one step in that direction, still inside the list. */
    private void spinFlick(int fx, int fy) {
        finishAnims();
        int ctx = spinContext();
        if (StickRoute.action(StickRoute.SPINNER, ctx) == StickRoute.A_NONE) { bump(fx, fy); return; }
        if (spinLimit.take(1, SystemClock.uptimeMillis(), StickRoute.rate(ctx, stickSpeed)) == 0) return;
        switch (ctx) {
            case StickRoute.CTX_LIST:
                if (fy != 0) spinStep(ctx, fy);   // left and right would change tabs or values: ignored
                break;
            case StickRoute.CTX_WHEEL:
                if (fx != 0) wheelNav(fx, 0);     // up and down would leave the wheel: ignored
                break;
            case StickRoute.CTX_GRID: {
                int n = list.size(), ni = StickRoute.gridMove(mainIdx, n, COLS, fx, fy);
                if (ni == mainIdx) { bump(fx, fy); break; }
                mainIdx = ni;
                fx(SoundMap.MOVE);
                moved(fx, fy);
                break;
            }
            default:
                break;
        }
    }

    /** One item next or previous, used by the stick dial and the shoulder buttons. */
    private void navLinear(int s) {
        if (quick != null) { quickNav(0, s); return; }
        if (panel != null && !panel.closing) {
            if (panel.move(s)) { tickFx(false); moved(0, s); } else bump(0, s);
            return;
        }
        if (zone == Z_MAIN) {
            int n = list.size();
            if (n <= 1) { bump(s, 0); return; }
            if (grid) {
                mainIdx = WheelMath.wrap(mainIdx + s, n);
                tickFx(heavyDetent(s, n));
                moved(s, 0);
            } else {
                wheelNav(s, 0);
            }
            return;
        }
        nav(s, 0);
    }

    private void okKey(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (e.getRepeatCount() == 0) {
                okTracking = true;
                okLong = false;
                okDown = e.getDownTime();
            } else if (okTracking && !okLong && e.getEventTime() - okDown >= LONG_MS) {
                okLong = true;
                longPress();
            }
        } else if (e.getAction() == KeyEvent.ACTION_UP) {
            boolean canceled = (e.getFlags() & KeyEvent.FLAG_CANCELED) != 0;
            if (okTracking && !okLong && !canceled) {
                if (e.getEventTime() - okDown >= LONG_MS) longPress(); else click();
            }
            okTracking = false;
        }
    }

    private void backKey(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (e.getRepeatCount() == 0) {
                backTracking = true;
                backLong = false;
                backDown = e.getDownTime();
            } else if (backTracking && !backLong && e.getEventTime() - backDown >= LONG_MS) {
                backLong = true;
                if (quick == null && (panel == null || panel.closing)) openSettings(T_LOOK, null);
            }
        } else if (e.getAction() == KeyEvent.ACTION_UP) {
            boolean canceled = (e.getFlags() & KeyEvent.FLAG_CANCELED) != 0;
            if (backTracking && !backLong && !canceled) {
                if (e.getEventTime() - backDown >= LONG_MS && quick == null && (panel == null || panel.closing)) openSettings(T_LOOK, null);
                else back();
            }
            backTracking = false;
        }
    }

    private void back() {
        if (quick != null) { if (quick.back()) { fx(SoundMap.BACK); focusChanged(true); } else closeQuick(true); return; }
        if (panel != null && !panel.closing) {
            fx(SoundMap.BACK);
            if (panel.onBack != null) panel.onBack.run(); else closePanel();
            return;
        }
        // Home screen: consume Back, just bring focus home.
        if (zone != Z_MAIN && !list.isEmpty()) {
            fx(SoundMap.BACK);
            setZone(Z_MAIN, 0, zone == Z_TOP ? 1 : -1);
        } else {
            bump(0, 0);
        }
    }

    private void nav(int dx, int dy) {
        if (quick != null) { quickNav(dx, dy); return; }
        if (panel != null && !panel.closing) {
            if (dy != 0) {
                if (panel.move(dy)) { fx(SoundMap.MOVE); moved(0, dy); } else bump(0, dy);
            } else if (dx != 0) {
                boolean onTabs = panel.focus == -1;
                if (panel.change(dx)) {
                    fx(SoundMap.TOGGLE);
                    if (onTabs) moved(dx, 0);
                    else if (fx3d()) tiltY.kick(dx * 120f);
                } else {
                    bump(dx, 0);
                }
            }
            return;
        }
        switch (zone) {
            case Z_MAIN:
                if (list.isEmpty()) { setZone(dy < 0 ? Z_TOP : Z_DOCK, dx, dy); return; }
                if (grid) gridNav(dx, dy); else wheelNav(dx, dy);
                break;
            case Z_TOP:
                if (dx != 0) {
                    int n = topIdx + dx;
                    if (n >= 0 && n <= 1) { topIdx = n; fx(SoundMap.MOVE); moved(dx, 0); } else bump(dx, 0);
                } else if (dy > 0) {
                    setZone(list.isEmpty() ? Z_DOCK : Z_MAIN, 0, dy);
                } else bump(0, dy);
                break;
            default:
                if (dx != 0) {
                    int n = dockIdx + dx;
                    if (n >= 0 && n < dock.size()) { dockIdx = n; fx(SoundMap.MOVE); moved(dx, 0); } else bump(dx, 0);
                } else if (dy < 0) {
                    setZone(list.isEmpty() ? Z_TOP : Z_MAIN, 0, dy);
                } else bump(0, dy);
                break;
        }
    }

    private void wheelNav(int dx, int dy) {
        int n = list.size();
        if (dx != 0) {
            if (n <= 1) { bump(dx, 0); return; }
            wheelTarget += dx;
            int shift = WheelMath.rebase(wheelTarget, n);
            if (shift != 0) {
                wheelTarget -= shift;
                wheelPos.shift(shift);
                for (TileView t : slots) if (t.vIndex != NONE) t.vIndex -= shift;
            }
            mainIdx = WheelMath.wrap(wheelTarget, n);
            tickFx(heavyDetent(dx, n));
            moved(dx, 0);
        } else if (dy < 0) {
            setZone(Z_TOP, 0, dy);
        } else {
            if (dock.isEmpty()) bump(0, dy); else setZone(Z_DOCK, 0, dy);
        }
    }

    private void gridNav(int dx, int dy) {
        int n = list.size();
        int r = mainIdx / COLS, c = mainIdx % COLS;
        if (dx != 0) {
            int nc = c + dx, ni = mainIdx + dx;
            if (nc >= 0 && nc < COLS && ni >= 0 && ni < n) { mainIdx = ni; fx(SoundMap.MOVE); moved(dx, 0); } else bump(dx, 0);
            return;
        }
        int nr = r + dy;
        if (nr < 0) { setZone(Z_TOP, 0, dy); return; }
        if (nr * COLS >= n) {
            if (dock.isEmpty()) bump(0, dy); else setZone(Z_DOCK, 0, dy);
            return;
        }
        mainIdx = Math.min(nr * COLS + c, n - 1);
        fx(SoundMap.MOVE);
        moved(0, dy);
    }

    private void click() {
        if (quick != null) {
            fx(SoundMap.SELECT);
            quick.ok();
            return;
        }
        if (panel != null && !panel.closing) {
            Panel.Row r = panel.current();
            boolean sub = r != null && r.sub;
            if (panel.ok() && !sub) fx(SoundMap.SELECT);
            frame.unlock(SystemClock.uptimeMillis());
            lockArmed = true;
            return;
        }
        switch (zone) {
            case Z_MAIN:
                if (mainIdx < list.size()) launch(list.get(mainIdx), grid && mainIdx < gridTiles.size() ? gridTiles.get(mainIdx) : centerSlot());
                break;
            case Z_TOP:
                if (topIdx == 0) openNetwork(); else openSettings(T_LOOK, null);
                break;
            default:
                if (dockIdx < dock.size()) {
                    Chip c = dock.get(dockIdx);
                    if (c.action != null) c.action.run();
                }
                break;
        }
    }

    private View centerSlot() {
        for (TileView t : slots) if (t.vIndex == wheelTarget) return t;
        return null;
    }

    private void longPress() {
        if (quick != null || (panel != null && !panel.closing)) return;
        if (zone == Z_MAIN && mainIdx < list.size()) openTileMenu(list.get(mainIdx));
        else if (zone == Z_DOCK && dockIdx < dock.size() && dock.get(dockIdx).app != null) openDockMenu(dock.get(dockIdx).app);
        else if (zone == Z_TOP) openSettings(T_LOOK, null);
    }

    // ------------------------------------------------------------------ actions

    private void launch(final AppStore.App a, final View from) {
        if (a == null) return;
        // 2.12 performance mode: close the background apps when a game (or a chosen app) starts
        try {
            String pr = PerfRunner.consider(this, prefs, a.cn.getPackageName());
            if (pr != null) toast(pr);
        } catch (Throwable ignored) {}
        // 2.8: LAUNCH WITH PROFILE: apply the display profile first (a new value asks KEEP or REVERT here)
        try {
            if (ProfileRunner.get(this).launchWith(a.cn.getPackageName(), () -> launchNow(a, from))) return;
        } catch (Throwable ignored) {}
        launchNow(a, from);
    }

    private void launchNow(AppStore.App a, View from) {
        if (a == null) return;
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(a.leanback ? Intent.CATEGORY_LEANBACK_LAUNCHER : Intent.CATEGORY_LAUNCHER)
                .setComponent(a.cn)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        Bundle opts = null;
        try {
            if (from != null && from.getWidth() > 0)
                opts = ActivityOptions.makeScaleUpAnimation(from, 0, 0, from.getWidth(), from.getHeight()).toBundle();
        } catch (Throwable ignored) {}
        try {
            startActivity(i, opts);
            fx(SoundMap.LAUNCH);
            noteLaunch(a);
            return;
        } catch (Throwable ignored) {}
        try {
            PackageManager pm = getPackageManager();
            Intent li = pm.getLeanbackLaunchIntentForPackage(a.cn.getPackageName());
            if (li == null) li = pm.getLaunchIntentForPackage(a.cn.getPackageName());
            if (li != null) {
                li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(li);
                fx(SoundMap.LAUNCH);
                noteLaunch(a);
                return;
            }
        } catch (Throwable ignored) {}
        fx(SoundMap.ERROR);
        toast("Could not open " + a.label);
        refresh(true);
    }

    /** Recents source A: launches made through Deck. The dock catches up in onResume. */
    private void noteLaunch(AppStore.App a) {
        recents.record(a.key, System.currentTimeMillis());
        prefs.edit().putString("recents", recents.save()).apply();
    }

    private boolean startFirst(Intent... candidates) {
        for (Intent i : candidates) {
            if (i == null) continue;
            try {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                return true;
            } catch (Throwable ignored) {}
        }
        if (sounds != null) fx(SoundMap.ERROR);
        toast("Not available on this device");
        return false;
    }

    private void openNetwork() {
        startFirst(new Intent(Settings.ACTION_WIFI_SETTINGS),
                new Intent(Settings.ACTION_WIRELESS_SETTINGS),
                new Intent(Settings.ACTION_SETTINGS));
    }

    private boolean canResolve(Intent i) {
        try { return i.resolveActivity(getPackageManager()) != null; } catch (Throwable t) { return false; }
    }

    private Intent stockHomeIntent() {
        return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(STOCK_HOME);
    }

    private boolean stockHomeEnabled() {
        try {
            ApplicationInfo ai = getPackageManager().getApplicationInfo(STOCK_HOME, 0);
            return ai.enabled && canResolve(stockHomeIntent());
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------------------ 2.2 controller tests

    private void openPadTestChooser() {
        final Panel p = new Panel(this, "padtest", "Gamepad test");
        p.onBack = () -> openSettings(T_PAD, "GAMEPAD TEST");
        p.add(new Panel.Row("FULL TEST").info("BUTTONS STAY LIT. PRESS ALL 16 TO FINISH").ok(() -> openPadTest(true)));
        p.add(new Panel.Row("QUICK TEST").info("A BUTTON LIGHTS ONLY WHILE HELD").ok(() -> openPadTest(false)));
        p.add(new Panel.Row("HOW TO EXIT").two("BACK ON THE REMOTE, HOLD START + SELECT FOR 2 S, CLICK EXIT WITH THE CURSOR, OR TURN THE CONTROLLER OFF"));
        p.add(new Panel.Row("OPTIONAL BUTTONS").two("THE PS OR GUIDE BUTTON AND THE TOUCHPAD CLICK ARE SHOWN BUT NOT COUNTED. MANY BOXES SWALLOW THEM."));
        openPanel(p);
    }

    private void openPadTest(boolean full) {
        showTest(new PadTestView(this, this, reduce, full, padId), "GAMEPAD TEST");
    }

    private void openGyroTest() {
        showTest(new GyroTestView(this, this, reduce, padId, gyroFollow, gyroStrength, gyroOn, calStore), "GYRO TEST");
    }

    /** 2.3: the three step gyro calibration. fromTest: go back to the gyro test when it closes. */
    private void openGyroCal(boolean fromTest) {
        calFromTest = fromTest;
        showTest(new GyroCalView(this, this, reduce, padId, calStore), "GYRO CALIBRATION");
    }

    @Override public void testCalibrate() {
        if (test == null) return;
        removeTest();
        openGyroCal(true);
    }

    private void showTest(TestScreen v, String row) {
        if (root == null) return;
        if (remapGate != null) remapGate.reset();
        if (test != null) removeTest();
        if (quick != null) closeQuick(false);
        if (panel != null) { root.removeView(panel); panel = null; }
        flySource = null;
        pushBack(0f);
        if (hudToast != null) hudToast.dismiss();
        h.removeCallbacks(navRepeat);
        navDx = 0;
        navDy = 0;
        keyHeld = false;
        dial.update(0f, 0f, SystemClock.uptimeMillis());   // a stick held while the test opens must not stay "active" behind it
        lockArmed = false;
        testRow = row;
        test = v;
        root.addView(v, new Abs.LP(0, 0, Ui.W, Ui.H));
        if (glow != null) glow.setVisibility(View.GONE);
        try {
            android.hardware.input.InputManager im = getSystemService(android.hardware.input.InputManager.class);
            if (im != null) {
                testDevices = new android.hardware.input.InputManager.InputDeviceListener() {
                    @Override public void onInputDeviceAdded(int id) {}
                    @Override public void onInputDeviceChanged(int id) {}
                    @Override public void onInputDeviceRemoved(int id) { if (test != null) test.deviceRemoved(id); }
                };
                im.registerInputDeviceListener(testDevices, h);
            }
        } catch (Throwable ignored) {}
        updateGyro();
        fx(SoundMap.MENU_OPEN);
    }

    /** Takes the test screen away without opening anything else. */
    private void removeTest() {
        TestScreen v = test;
        if (v == null) return;
        test = null;
        testBack = null;
        try { v.stop(); } catch (Throwable ignored) {}
        if (root != null) root.removeView(v);
        try {
            android.hardware.input.InputManager im = getSystemService(android.hardware.input.InputManager.class);
            if (im != null && testDevices != null) im.unregisterInputDeviceListener(testDevices);
        } catch (Throwable ignored) {}
        testDevices = null;
        updateGyro();
        focusChanged(false);
        if (v instanceof TutorialView) h.postDelayed(this::maybeOfferGyroIntro, 1500);
    }

    @Override public void closeTest(String toast) {
        if (test == null) return;
        boolean backToGyroTest = test instanceof GyroCalView && calFromTest;
        Runnable back = testBack;
        calFromTest = false;
        removeTest();
        fx(SoundMap.MENU_CLOSE);
        if (toast != null) toast(toast);
        if (back != null) back.run();
        else if (backToGyroTest) openGyroTest();
        else openSettings(T_PAD, testRow);
    }

    @Override public void testFx(int ev) { fx(ev); }

    /** 2.4.1: the navigator stick is the one that is not the WHEEL STICK. */
    @Override public boolean testNavStickLeft() { return stickRight; }

    // ------------------------------------------------------------------ PS quick menu

    private void openQuick() {
        if (root == null || quick != null) return;
        if (panel != null) { panel.onBack = null; closePanel(); }
        QuickMenu q = new QuickMenu(this, ccHost);
        q.effects = !reduce;
        q.gamepad = mode == InputMode.GAMEPAD;
        q.style = glyphStyle;
        AnimConfig.reveal(anim, AnimConfig.QUICK, reduce, scratchText);
        q.anim(engine, typeTicks, typeFx, scratchText, AnimConfig.glass(anim), AnimConfig.rtl(anim));
        AnimConfig.flyout(anim, AnimConfig.QUICK, reduce, q.fa.p);
        removeQuickOut();
        root.addView(q, new Abs.LP(0, 0, Ui.W, Ui.H));
        quick = q;
        // 2.4: the same flyout as the app menus (panel grows, text types in), without the icon glide
        q.startOpen(engine.now());
        fx(AnimConfig.sound(anim) ? SoundMap.FLYOUT_OPEN : SoundMap.MENU_OPEN);
        focusChanged(true);
        engine.kick();
    }

    void closeQuick(boolean sound) {
        final QuickMenu q = quick;
        if (q == null) return;
        quick = null;
        removeQuickOut();
        q.startClose(engine.now());
        if (q.gone()) root.removeView(q); else quickOut = q;
        if (sound) fx(AnimConfig.sound(anim) ? SoundMap.FLYOUT_CLOSE : SoundMap.MENU_CLOSE);
        focusChanged(true);
        engine.kick();
    }

    private void removeQuickOut() {
        if (quickOut == null) return;
        if (root != null) root.removeView(quickOut);
        quickOut = null;
    }

    private void quickNav(int dx, int dy) {
        if (quick == null) return;
        if (dy != 0) {
            if (quick.move(dy)) { fx(SoundMap.MOVE); focusChanged(true); } else bump(0, dy);
        } else if (dx != 0) {
            if (quick.change(dx)) fx(SoundMap.TOGGLE); else bump(dx, 0);
        }
    }

    /** 2.8: the control center's tiles. What Deck's own window and the PS overlay share lives in CcHost. */
    private final CcHost ccHost = new CcHost(new CcHost.Env() {
        @Override public Context ctx() { return HomeActivity.this; }
        @Override public SharedPreferences prefs() { return prefs; }
        @Override public AppStore store() { return store; }
        @Override public IconCache.Icon icon(AppStore.App a) {
            IconCache.Icon ic = icons.get(a.key);
            if (ic == null) icons.request(a);
            return ic;
        }
        @Override public String foreground() { return null; }
        @Override public boolean service() { return PsService.live != null; }
        @Override public boolean global(int action) {
            PsService sv = PsService.live;
            return sv != null && sv.global(action);
        }
        @Override public boolean forceStop(String pkg) {
            PsService sv = PsService.live;
            return sv != null && sv.forceStop(pkg);
        }
        @Override public void closeThen(Runnable r, long ms) {
            closeQuick(false);
            h.postDelayed(r, ms);
        }
        @Override public void startThenClose(Runnable r) { closeQuick(false); r.run(); }
        @Override public void setup() { closeQuick(false); openSettings(T_LOOK, null); }
        @Override public void close() { closeQuick(true); }
        @Override public boolean live() { return true; }
        @Override public boolean liveOn(String id) { return id.equals("c.leds") && playerLedMode != PlayerLeds.OFF; }
        @Override public String liveText(String id) {
            switch (id) {
                case "c.mode": return mode == InputMode.GAMEPAD ? "GAMEPAD" : (mode == InputMode.POINTER ? "POINTER" : "REMOTE");
                case "s.pack": {
                    String pk = prefs.getString("soundPack", "");
                    return pk == null || pk.isEmpty() ? "BUILT-IN" : pk.toUpperCase(Locale.ROOT);
                }
                default: return null;
            }
        }
        @Override public String liveAct(String id) {
            switch (id) {
                case "c.mode": return "VISOR FOLLOWS YOUR LAST INPUT";
                case "c.rumble":
                    if (pad == null || !pad.hasVibrators()) return "NO CONTROLLER";
                    if (rumble <= 0) return "RUMBLE IS OFF";
                    pad.play(HapticPatterns.DETENT_HEAVY, feel, rumble, 0);
                    return "RUMBLE";
                case "c.leds":
                    playerLedMode = playerLedMode == PlayerLeds.OFF ? PlayerLeds.WHEEL : PlayerLeds.OFF;
                    prefs.edit().putInt("playerLeds", playerLedMode).apply();
                    updatePlayerLeds();
                    return null;
                case "s.pack": closeQuick(false); openSettings(T_SOUND, null); return null;
                case "d.saver": closeQuick(false); startPreview(); return null;
                default: return null;
            }
        }
        @Override public String profile(String pkg) { closeQuick(false); openProfile(pkg); return null; }
        @Override public String profileQuick() { closeQuick(false); openProfiles(null); return null; }
        @Override public String restoreDisplay() { return ProfileRunner.get(HomeActivity.this).restoreNow(); }
        @Override public String profileText() {
            int n = ProfileRunner.get(HomeActivity.this).profiles().count();
            return n == 0 ? "NONE SET" : n + " SET";
        }
        @Override public String restartDeck() { closeQuick(false); recreate(); return null; }
        @Override public void toast(String t) { HomeActivity.this.toast(t); }
    });

    /** 2.8: the KEEP or REVERT prompt of a game profile, in front of Deck. */
    private KeepView keepView;
    private final ProfileRunner.Ui profileUi = new ProfileRunner.Ui() {
        @Override public void showKeep(GameProfiles.Profile p) {
            if (root == null || keepView != null) return;
            KeepView k = new KeepView(HomeActivity.this);
            k.set(profileLine(p), DisplayRules.KEEP_SECONDS, mode == InputMode.GAMEPAD);
            root.addView(k, new Abs.LP(0, 0, Ui.W, Ui.H));
            keepView = k;
        }
        @Override public void keepTick(int seconds) { if (keepView != null) keepView.setSeconds(seconds); }
        @Override public void hideKeep() {
            if (keepView != null && root != null) root.removeView(keepView);
            keepView = null;
        }
        @Override public void toast(String t) { HomeActivity.this.toast(t); }
    };

    private String profileLine(GameProfiles.Profile p) {
        String name = p.pkg;
        AppStore.App a = null;
        if (store != null) for (AppStore.App x : store.all) if (x.cn.getPackageName().equals(p.pkg)) { a = x; break; }
        if (a != null) name = a.label;
        return name.toUpperCase(Locale.ROOT) + "  " + p.scalePct + " PERCENT";
    }

    private void openPanel(Panel p) {
        Panel old = panel;
        boolean replacing = old != null;
        // 2.3: a refresh of the same screen (after an action) does not replay the text animation
        boolean refresh = replacing && p.kind.equals(old.kind) && p.title.equals(old.title);
        boolean docked = replacing && old.flyout && p.flyout && !old.closing;
        if (old != null) root.removeView(old);
        if (!p.flyout) { flySource = null; pushBack(0f); }
        panel = p;
        p.setGamepad(mode == InputMode.GAMEPAD, glyphStyle);
        String screen = AnimConfig.screenFor(p.kind);
        AnimConfig.reveal(anim, screen, reduce, scratchText);
        AnimConfig.reveal(anim, AnimConfig.CONFIRM, reduce, scratchConfirm);
        p.anim(engine, typeTicks, typeFx, scratchText, scratchConfirm, AnimConfig.glass(anim), AnimConfig.rtl(anim));
        // 2.5: a refresh quiets this one reveal only. 2.4.1 set NONE on the parameters the panel keeps,
        // and the panel stayed on NONE for every later tab switch.
        if (refresh) p.quietNext();
        p.setReduce(reduce);
        if (p.flyout) {
            AnimConfig.flyout(anim, screen, reduce, scratchFly);
            p.fa.p.set(scratchFly);
        }
        p.open = new Spring(engine, 260f, 0.78f, 0.002f);
        boolean still = p.flyout || reduce;   // reduce effects: the panel is there at once
        float start = still ? 1f : (replacing ? 0.7f : 0f);
        p.open.snap(start);
        p.setAlpha(start);
        root.addView(p, root.indexOfChild(frame), new Abs.LP(0, 0, Ui.W, Ui.H));
        p.open.to(1f);
        p.startOpen(engine.now(), docked);
        if (p.flyout && !docked) fx(AnimConfig.sound(anim) ? SoundMap.FLYOUT_OPEN : SoundMap.MENU_OPEN);
        else if (!replacing) fx(SoundMap.MENU_OPEN);
        focusChanged(true);
        engine.kick();
    }

    void closePanel() {
        if (panel == null || panel.closing) return;
        panel.closing = true;
        if (panel.flyout) {
            panel.startClose(engine.now());
            fx(AnimConfig.sound(anim) ? SoundMap.FLYOUT_CLOSE : SoundMap.MENU_CLOSE);
        } else {
            if (reduce) panel.open.snap(0f); else panel.open.to(0f);
            fx(SoundMap.MENU_CLOSE);
        }
        focusChanged(true);
        engine.kick();
    }

    /** Close the panel with its animation, then run next (the preview goes back to its options this way). */
    private void closePanelThen(Runnable next) {
        if (panel == null) { if (next != null) next.run(); return; }
        panel.afterClose = next;
        closePanel();
    }

    /**
     * 2.3 options flyout for one item: the focused tile's (or chip's) icon glides to the dock and
     * the panel grows beside it. Replacing a flyout (Change icon, and back) keeps the icon docked.
     */
    private void openFlyout(Panel p, AppStore.App a) {
        boolean docked = panel != null && panel.flyout && !panel.closing;
        if (docked) {
            flyRect.set(panel.flyFrom);
        } else {
            if (!focusRect(flyRect)) flyRect.set(Ui.W / 2f - Ui.px(280), Ui.H / 2f - Ui.px(158), Ui.W / 2f + Ui.px(280), Ui.H / 2f + Ui.px(158));
            flySource = focusView();
        }
        IconCache.Icon ic = null;
        if (a != null) {
            ic = icons.get(a.key);
            if (ic == null) icons.request(a);
            p.flyKey = a.key;
        }
        p.flyout(flyRect, ic, a == null ? "SAMPLE APP" : a.label, AnimConfig.rightSide(anim));
        openPanel(p);
    }

    /** Where the focused app tile or dock chip is now, in root px. */
    private boolean focusRect(RectF out) {
        float u = Ui.u;
        if (zone == Z_DOCK && dockIdx < dock.size()) { chipRect(dock.get(dockIdx), out); return true; }
        if (list.isEmpty()) return false;
        if (!grid) {
            float w = CW * u * 1.06f, hh = CH * u * 1.06f;
            out.set(WCX * u - w / 2, WCY * u - hh / 2, WCX * u + w / 2, WCY * u + hh / 2);
            return true;
        }
        if (mainIdx >= gridTiles.size()) return false;
        TileView t = gridTiles.get(mainIdx);
        float sc = t.getScaleX();
        float cx = t.getLeft() + t.getWidth() / 2f + t.getTranslationX(), cy = t.getTop() + t.getHeight() / 2f + gridScroll.value + t.getTranslationY();
        out.set(cx - t.getWidth() * sc / 2, cy - t.getHeight() * sc / 2, cx + t.getWidth() * sc / 2, cy + t.getHeight() * sc / 2);
        return true;
    }

    private View focusView() {
        if (zone == Z_DOCK && dockIdx < dock.size()) return dock.get(dockIdx);
        if (zone != Z_MAIN || list.isEmpty()) return null;
        if (grid) return mainIdx < gridTiles.size() ? gridTiles.get(mainIdx) : null;
        return centerSlot();
    }

    /**
     * 2.3: a key press, stick move or click during an animation finishes every animation at
     * once (open, close, typing, toast); the input is then applied as usual.
     */
    private boolean finishAnims() {
        boolean any = false;
        if (panel != null) {
            if (panel.finishAnim()) any = true;
            if (panel.open != null && panel.open.active) { panel.open.snap(panel.closing ? 0f : 1f); any = true; }
            if (panel.closing) { removePanelNow(); any = true; }
            else if (any) applyPanel();
        }
        if (quick != null && quick.finishAnim()) any = true;
        if (quickOut != null) { removeQuickOut(); any = true; }
        if (hudToast != null && hudToast.finishAnim()) any = true;
        if (any) engine.kick();
        return any;
    }

    private void setLayout(boolean g) {
        if (g == grid) return;
        grid = g;
        prefs.edit().putString("layout", grid ? "grid" : "wheel").apply();
        AppStore.App a = mainIdx < list.size() ? list.get(mainIdx) : null;
        rebuildLists(a == null ? null : a.key);
        fx(SoundMap.TOGGLE);
    }

    private void setStats(boolean on) {
        statsOn = on;
        prefs.edit().putBoolean("stats", on).apply();
        stats.enabled = on;
        stats.invalidate();
        h.removeCallbacks(statsTick);
        statsTick.run();
        fx(SoundMap.TOGGLE);
    }

    private static String onOff(boolean b) { return b ? "ON" : "OFF"; }

    private void openSettings(int tab, String row) {
        final Panel p = new Panel(this, "settings", getString(R.string.app_name) + " setup");

        p.tab("LOOK");
        final Panel.Row layout = p.add(new Panel.Row("LAYOUT").values(grid ? 1 : 0, "WHEEL", "GRID"));
        layout.change(() -> setLayout(layout.sel == 1));
        String[] names = new String[Ui.THEMES.length];
        for (int i = 0; i < names.length; i++) names[i] = Ui.THEMES[i].name;
        final Panel.Row theme = p.add(new Panel.Row("THEME").values(themeIdx, names).swatch());
        theme.change(() -> {
            themeIdx = theme.sel;
            prefs.edit().putInt("theme", themeIdx).apply();
            applyTheme();
        });
        p.add(new Panel.Row("CUSTOM COLORS").sub().ok(this::openCustomColors));
        p.add(new Panel.Row("THEME EDITOR").sub().ok(() -> openThemeEditor(null)));
        final Panel.Row clk = p.add(new Panel.Row("CLOCK").values(clock24 ? 1 : 0, "12 HOUR", "24 HOUR"));
        clk.change(() -> {
            clock24 = clk.sel == 1;
            prefs.edit().putBoolean("clock24", clock24).apply();
            updateClock();
        });
        final Panel.Row csec = p.add(new Panel.Row("CLOCK SECONDS").values(clockSec ? 1 : 0, "OFF", "ON"));
        csec.change(() -> {
            clockSec = csec.sel == 1;
            prefs.edit().putBoolean("clockSec", clockSec).apply();
            restartClock();
        });
        String[] zoneNames = new String[ClockFmt.ZONE_NAMES.length + 1];
        zoneNames[0] = "OFF";
        System.arraycopy(ClockFmt.ZONE_NAMES, 0, zoneNames, 1, ClockFmt.ZONE_NAMES.length);
        final Panel.Row z2 = p.add(new Panel.Row("SECOND CLOCK ZONE").values(ClockFmt.zoneIndex(clock2) + 1, zoneNames));
        z2.change(() -> {
            clock2 = z2.sel == 0 ? "" : ClockFmt.ZONE_IDS[z2.sel - 1];
            prefs.edit().putString("clock2", clock2).apply();
            applyClockZone();
            restartClock();
        });
        final Panel.Row st = p.add(new Panel.Row("STATS PANEL").values(statsOn ? 1 : 0, "OFF", "ON"));
        st.change(() -> setStats(st.sel == 1));
        // 2.7 wallpapers: the chooser applies live behind this panel
        final String[] wpacks = Wallpapers.list(wallDir);
        final Panel.Row wst = new Panel.Row("WALLPAPER STATUS").two(wallStatus);
        final Panel.Row wopt = new Panel.Row("WALLPAPER OPTIONS").sub().disabled(!Wallpapers.isPack(wallSel)).ok(() -> openWallpaperOptions(null));
        final Panel.Row wp = p.add(new Panel.Row("WALLPAPER").values(Wallpapers.choiceIndex(wallSel, wpacks), Wallpapers.choices(wpacks)));
        wp.change(() -> {
            wallSel = Wallpapers.selFor(wp.sel, wpacks);
            prefs.edit().putString("wallpaper", wallSel).apply();
            wopt.disabled(!Wallpapers.isPack(wallSel));
            wst.info = wallStatus;
            applyWallpaper();
            p.invalidate();
        });
        p.add(wopt);
        p.add(new Panel.Row("RELOAD WALLPAPERS").ok(() -> installWallpaperZips(false, true, () -> {
            if (panel == p && !p.closing) openSettings(T_LOOK, "RELOAD WALLPAPERS");
        })));
        p.add(new Panel.Row("WALLPAPER FOLDER").two("Android/data/" + getPackageName() + "/files/wallpapers  (PACK FOLDERS OR .ZIP FILES)"));
        p.add(wst);
        wallStatRow = wst;
        wallStatPanel = p;
        p.add(new Panel.Row("SCREENSAVER").info(saverSummary()).sub().ok(() -> openSaver(null)));

        p.tab("MOTION");
        final Panel.Row red = p.add(new Panel.Row("REDUCE EFFECTS").values(reduce ? 1 : 0, "OFF", "ON"));
        red.change(() -> {
            reduce = red.sel == 1;
            prefs.edit().putBoolean("reduce", reduce).apply();
            applyEffects();
            p.setReduce(reduce);
            // an animated wallpaper is decoded again: its first frame as a still under Reduce effects, the animation without
            if (wallSpec != null && wallSpec.type == WallpaperSpec.ANIMATED) applyWallpaper();
        });
        final Panel.Row pd = p.add(new Panel.Row("PAN DIRECTION").values(panFollow ? 0 : 1, "FOLLOW", "OPPOSITE"));
        pd.change(() -> {
            panFollow = pd.sel == 0;
            prefs.edit().putString("panDir", panFollow ? "follow" : "opposite").apply();
            pan.dir = panFollow ? 1 : -1;
        });
        final Panel.Row sk = p.add(new Panel.Row("WHEEL STICK").values(stickRight ? 0 : 1, "RIGHT", "LEFT"));
        sk.change(() -> {
            stickRight = sk.sel == 0;
            prefs.edit().putString("stick", stickRight ? "right" : "left").apply();
            hud.setMode(mode, glyphStyle, stickRight, false);
        });
        final Panel.Row ssp = p.add(new Panel.Row("WHEEL STICK SPEED").values(StickRoute.speedIndex(stickSpeed), StickRoute.SPEED_NAMES));
        ssp.change(() -> {
            stickSpeed = StickRoute.SPEEDS[ssp.sel];
            prefs.edit().putInt("stickSpeed", stickSpeed).apply();
            dial.detent = StickRoute.detent(stickSpeed);
            spinLimit.reset();
        });
        final Panel.Row gy = p.add(new Panel.Row("GYRO SWAY").values(gyroOn ? 1 : 0, "OFF", "ON"));
        final Panel.Row gs = p.add(new Panel.Row("GYRO SENSOR").info(gyroStatus()));
        gy.change(() -> {
            gyroOn = gy.sel == 1;
            prefs.edit().putBoolean("gyro", gyroOn).apply();
            updateGyro();
            gs.info = gyroStatus();
            p.invalidate();
        });
        gs.ok(() -> { gs.info = gyroStatus(); p.invalidate(); });
        final Panel.Row gd = p.add(new Panel.Row("GYRO DIRECTION").values(gyroFollow ? 0 : 1, "FOLLOW", "OPPOSITE"));
        gd.change(() -> {
            gyroFollow = gd.sel == 0;
            prefs.edit().putString("gyroDir", gyroFollow ? "follow" : "opposite").apply();
            if (gyro != null) gyro.follow = gyroFollow;
        });
        final Panel.Row gst = p.add(new Panel.Row("GYRO STRENGTH").slider(25, 200, 25, gyroStrength));
        gst.change(() -> {
            gyroStrength = gst.val;
            prefs.edit().putInt("gyroStrength", gyroStrength).apply();
            if (gyro != null) gyro.filter.scale = gyroStrength / 100f;
        });
        final Panel.Row pf = p.add(new Panel.Row("POINTER EFFECTS").values(pointerFx ? 1 : 0, "OFF", "ON"));
        pf.change(() -> { pointerFx = pf.sel == 1; prefs.edit().putBoolean("pointerFx", pointerFx).apply(); });
        final Panel.Row ps = p.add(new Panel.Row("EFFECT STRENGTH").slider(0, 100, 10, pointerStrength));
        ps.change(() -> { pointerStrength = ps.val; prefs.edit().putInt("pointerStrength", pointerStrength).apply(); });
        final Panel.Row gz = p.add(new Panel.Row("GLOW SIZE").slider(50, 150, 10, glowSize));
        gz.change(() -> {
            glowSize = gz.val;
            prefs.edit().putInt("glowSize", glowSize).apply();
            float gr = glowRadius();
            root.put(glow, 0, 0, gr * 2, gr * 2);
        });
        final Panel.Row tc = p.add(new Panel.Row("THEMED CURSOR").values(themedCursor ? 1 : 0, "OFF", "ON"));
        tc.change(() -> { themedCursor = tc.sel == 1; prefs.edit().putBoolean("themedCursor", themedCursor).apply(); applyCursorIcon(); });
        final Panel.Row hc = p.add(new Panel.Row("HIDE SYSTEM CURSOR").values(hideCursor ? 1 : 0, "OFF", "ON"));
        hc.change(() -> { hideCursor = hc.sel == 1; prefs.edit().putBoolean("hideCursor", hideCursor).apply(); applyCursorIcon(); });
        final Panel.Row pseen = p.add(new Panel.Row("POINTER SEEN").info(pointerStatus()));
        pseen.ok(() -> { pseen.info = pointerStatus(); p.invalidate(); });

        p.tab("ANIMATIONS");
        p.add(new Panel.Row("OPTIONS FLYOUT").info(animSummary()).sub().ok(() -> openAnimOptions(null)));
        p.add(new Panel.Row("HOW IT WORKS").two("EACH ANIMATION HAS ITS OWN OPTIONS. OPTIONS FLYOUT DRIVES EVERY MENU, THE PS MENU AND TOASTS. ANY KEY FINISHES AN ANIMATION AT ONCE."));
        if (reduce) p.add(new Panel.Row("REDUCE EFFECTS IS ON").two("NO ICON GLIDE, PANELS APPEAR AT ONCE, TEXT FADES IN WITHIN 120 MS, NO TICKS. TURN IT OFF IN MOTION."));

        p.tab("SOUND");
        String[] packs = sounds.listPacks();
        String[] choices = new String[packs.length + 1];
        choices[0] = "BUILT-IN";
        int sel = 0;
        for (int i = 0; i < packs.length; i++) {
            choices[i + 1] = packs[i].toUpperCase(Locale.ROOT);
            if (packs[i].equals(packName)) sel = i + 1;
        }
        final String[] packDirs = packs;
        final Panel.Row stat = new Panel.Row("PACK STATUS").info(sounds.status);
        final Panel.Row pk = p.add(new Panel.Row("SOUND PACK").values(sel, choices));
        pk.change(() -> {
            packName = pk.sel == 0 ? "" : packDirs[pk.sel - 1];
            prefs.edit().putString("soundPack", packName).apply();
            sounds.loadPack(packName, true);
            stat.info = sounds.status;
            p.invalidate();
        });
        final Panel.Row vol = p.add(new Panel.Row("VOLUME").slider(0, 100, 10, volume));
        vol.change(() -> {
            volume = vol.val;
            sounds.master = volume / 100f;
            prefs.edit().putInt("volume", volume).apply();
        });
        final Panel.Row mu = p.add(new Panel.Row("MUTE").values(sounds.mute ? 1 : 0, "OFF", "ON"));
        mu.change(() -> {
            sounds.mute = mu.sel == 1;
            prefs.edit().putBoolean("mute", sounds.mute).apply();
        });
        p.add(new Panel.Row("INSTALL PACK").sub().ok(this::openInstallPack));
        p.add(new Panel.Row("RELOAD PACKS").ok(() -> installZips(false, () -> {
            sounds.loadPack(packName, true);
            if (panel == p && !p.closing) openSettings(T_SOUND, "RELOAD PACKS");
        })));
        p.add(new Panel.Row("TEST SOUNDS").sub().ok(() -> openTests(0)));
        p.add(stat);
        p.add(new Panel.Row("ZIP STATUS").two(zipStatus));
        p.add(new Panel.Row("FOLDER").two("Android/data/" + getPackageName() + "/files/soundpacks  (FOLDERS OR .ZIP FILES)"));

        p.tab("PAD");
        final Panel.Row hp = p.add(new Panel.Row("HAPTICS").values(hapticsOn ? 1 : 0, "OFF", "ON"));
        hp.change(() -> { hapticsOn = hp.sel == 1; prefs.edit().putBoolean("haptics", hapticsOn).apply(); haptic(HapticPatterns.TOGGLE); });
        int rs = 0;
        for (int i = 0; i < HapticPatterns.STRENGTHS.length; i++) if (HapticPatterns.STRENGTHS[i] == rumble) rs = i;
        final Panel.Row rb = p.add(new Panel.Row("RUMBLE STRENGTH").values(rs, "OFF", "25%", "50%", "75%", "100%"));
        rb.change(() -> {
            rumble = HapticPatterns.STRENGTHS[rb.sel];
            prefs.edit().putInt("rumble", rumble).apply();
            haptic(HapticPatterns.SELECT);
        });
        final Panel.Row fl = p.add(new Panel.Row("FEEL").values(feel, HapticPatterns.PRESET_NAMES));
        fl.change(() -> { feel = fl.sel; prefs.edit().putInt("feel", feel).apply(); haptic(HapticPatterns.SELECT); });
        final Panel.Row hm = p.add(new Panel.Row("WHEEL HUM").values(humOn ? 1 : 0, "OFF", "ON"));
        hm.change(() -> { humOn = hm.sel == 1; prefs.edit().putBoolean("hum", humOn).apply(); });
        p.add(new Panel.Row("RUMBLE STATUS").info(padStatus(false)));
        p.add(new Panel.Row("TEST HAPTICS").sub().ok(() -> openTests(1)));
        String ls = padStatus(true);
        boolean lightOk = lightExposed();
        final Panel.Row lb = p.add(new Panel.Row("LIGHT BAR").values(lightMode, "OFF", "MATCH THEME", "CUSTOM").disabled(!lightOk));
        lb.change(() -> { lightMode = lb.sel; prefs.edit().putInt("lightMode", lightMode).apply(); applyLight(); });
        p.add(new Panel.Row("LIGHT COLOR").sub().ok(this::openLightColor).disabled(!lightOk));
        final Panel.Row lbr = p.add(new Panel.Row("LIGHT BRIGHTNESS").slider(0, 100, 10, lightBright).disabled(!lightOk));
        lbr.change(() -> { lightBright = lbr.val; prefs.edit().putInt("lightBright", lightBright).apply(); applyLight(); });
        final Panel.Row lp = p.add(new Panel.Row("PULSE ON LOCK-ON").values(lightPulse ? 1 : 0, "OFF", "ON").disabled(!lightOk));
        lp.change(() -> { lightPulse = lp.sel == 1; prefs.edit().putBoolean("lightPulse", lightPulse).apply(); pulseLight(); });
        p.add(new Panel.Row("LIGHT STATUS").two(lightOk ? ls : "LIGHT BAR: " + (pad != null && pad.deviceId >= 0 ? Pad.COMING_SOON : ls)));
        boolean leds = pad != null && pad.deviceId >= 0 && pad.hasPlayerLeds();
        final Panel.Row plr = p.add(new Panel.Row("PLAYER LEDS").values(playerLedMode, PlayerLeds.NAMES).disabled(pad != null && pad.deviceId >= 0 && !leds));
        final Panel.Row pls = new Panel.Row("PLAYER LEDS STATUS").info(pad == null ? "NEEDS ANDROID 12" : (pad.deviceId < 0 ? "PRESS A GAMEPAD BUTTON TO DETECT" : pad.playerStatus));
        plr.change(() -> {
            playerLedMode = plr.sel;
            prefs.edit().putInt("playerLeds", playerLedMode).apply();
            flashOn = false;
            if (pad != null) pad.restorePlayers();
            updatePlayerLeds();
            if (playerLedMode == PlayerLeds.LOCK_FLASH) flashPlayers();
            if (pad != null && pad.deviceId >= 0) pls.info = pad.playerStatus;
            p.invalidate();
        });
        p.add(pls);
        p.add(new Panel.Row("GAMEPAD TEST").sub().ok(this::openPadTestChooser));
        p.add(new Panel.Row("GYRO TEST").sub().ok(this::openGyroTest));
        final Panel.Row md = p.add(new Panel.Row("3D MODEL DETAIL").values(ModelObj.lightDetail ? 1 : 0, "FULL (EVERY TRIANGLE)", "LIGHT (12,000 TRIANGLES)"));
        md.change(() -> {
            ModelObj.lightDetail = md.sel == 1;
            prefs.edit().putBoolean("modelLight", ModelObj.lightDetail).apply();
        });
        p.add(new Panel.Row("GYRO CALIBRATION").sub().ok(() -> openGyroCal(false)));
        final Panel.Row calSt = p.add(new Panel.Row("CALIBRATION").two(calStore.status(padId)));
        p.add(new Panel.Row("RESET CALIBRATION").danger().confirm("PRESS OK AGAIN TO CLEAR IT").ok(() -> {
            InputDevice d = GyroCalStore.pad(padId);
            if (d == null) { fx(SoundMap.ERROR); toast("No controller connected"); return; }
            calStore.clear(d);
            if (gyro != null) gyro.cal = new GyroCal();
            calSt.info = calStore.status(padId);
            p.invalidate();
            toast("Gyro calibration cleared");
        }));
        p.add(new Panel.Row("CONTROLLER REPORT").sub().ok(this::openControllerReport));
        p.add(new Panel.Row("PS BUTTON SEEN").info(psSeen ? "YES" : "NO, PRESS IT ONCE ON THE HOME SCREEN"));
        addPsOverlayRows(p);

        // 2.8 touchpad volume: hold a key and drag a finger on the touchpad
        String[] tvNames = new String[TV_KEYS.length];
        int tvSel = 0;
        for (int i = 0; i < TV_KEYS.length; i++) {
            tvNames[i] = i == 0 ? "OFF" : "HOLD " + TouchVol.holdKeyName(TV_KEYS[i]);
            if (TV_KEYS[i] == touchVol.holdKey) tvSel = i;
        }
        final Panel.Row tvk = p.add(new Panel.Row("TOUCHPAD VOLUME").values(tvSel, tvNames));
        tvk.change(() -> { prefs.edit().putInt(TouchVolInput.K_KEY, TV_KEYS[tvk.sel]).apply(); loadTouchVol(); });
        final Panel.Row tvd = p.add(new Panel.Row("TOUCHPAD DIRECTION").values(touchVol.dir, "UP AND DOWN", "LEFT AND RIGHT"));
        tvd.change(() -> { prefs.edit().putInt(TouchVolInput.K_DIR, tvd.sel).apply(); loadTouchVol(); });
        String[] sn = new String[TouchVol.SENS_STEPS.length];
        for (int i = 0; i < sn.length; i++) sn[i] = TouchVol.sensName(i);
        final Panel.Row tvs = p.add(new Panel.Row("TOUCHPAD SENSITIVITY").values(touchVol.sens, sn));
        tvs.change(() -> { prefs.edit().putInt(TouchVolInput.K_SENS, tvs.sel).apply(); loadTouchVol(); });
        final Panel.Row tvi = p.add(new Panel.Row("TOUCHPAD INVERT").values(touchVol.invert ? 1 : 0, "OFF", "ON"));
        tvi.change(() -> { prefs.edit().putBoolean(TouchVolInput.K_INV, tvi.sel == 1).apply(); loadTouchVol(); });
        final Panel.Row tvz = p.add(new Panel.Row("TOUCHPAD SEEN").two(touchSeenText()));
        tvz.ok(() -> { tvz.info = touchSeenText(); p.invalidate(); });
        // 2.10 stick volume: hold a key and spin the right stick
        String[] svNames = new String[TV_KEYS.length];
        int svSel = 0;
        for (int i = 0; i < TV_KEYS.length; i++) {
            svNames[i] = i == 0 ? "OFF" : "HOLD " + TouchVol.holdKeyName(TV_KEYS[i]);
            if (TV_KEYS[i] == stickVol.holdKey) svSel = i;
        }
        final Panel.Row svk = p.add(new Panel.Row("STICK VOLUME").values(svSel, svNames));
        svk.change(() -> { prefs.edit().putInt(StickVolInput.K_KEY, TV_KEYS[svk.sel]).apply(); loadTouchVol(); });
        final Panel.Row svs = p.add(new Panel.Row("STICK VOLUME SPEED").values(stickVol.speed, StickVol.SPEED_NAMES));
        svs.change(() -> { prefs.edit().putInt(StickVolInput.K_SPEED, svs.sel).apply(); loadTouchVol(); });
        final Panel.Row svi = p.add(new Panel.Row("STICK VOLUME INVERT").values(stickVol.invert ? 1 : 0, "OFF", "ON"));
        svi.change(() -> { prefs.edit().putBoolean(StickVolInput.K_INV, svi.sel == 1).apply(); loadTouchVol(); });

        p.tab("APPS");
        p.add(new Panel.Row("ADD APP").sub().ok(this::openAddApp));
        final Panel.Row sa = p.add(new Panel.Row("SHOW ALL APPS").values(showAll ? 1 : 0, "OFF", "ON"));
        sa.change(() -> {
            showAll = sa.sel == 1;
            prefs.edit().putBoolean("showAll", showAll).apply();
            refresh(true);
        });
        final Panel.Row sh = p.add(new Panel.Row("SHOW HIDDEN APPS").values(showHidden ? 1 : 0, "OFF", "ON"));
        sh.change(() -> {
            showHidden = sh.sel == 1;
            prefs.edit().putBoolean("showHidden", showHidden).apply();
            AppStore.App a = mainIdx < list.size() ? list.get(mainIdx) : null;
            rebuildLists(a == null ? null : a.key);
        });
        final Panel.Row rd = p.add(new Panel.Row("RECENT APPS IN DOCK").values(recentsOn ? 1 : 0, "OFF", "ON"));
        rd.change(() -> { recentsOn = rd.sel == 1; prefs.edit().putBoolean("recentsDock", recentsOn).apply(); refreshDock(); });
        final Panel.Row rc = p.add(new Panel.Row("RECENT COUNT").slider(0, Recents.MAX_SHOW, 1, recentsCount));
        rc.change(() -> { recentsCount = rc.val; prefs.edit().putInt("recentsCount", recentsCount).apply(); refreshDock(); });
        final Panel.Row usage = new Panel.Row("USAGE ACCESS").two(usageStatus());
        final Panel.Row rs2 = p.add(new Panel.Row("RECENTS SOURCE").values(recentsSystem ? 1 : 0, "VISOR", "SYSTEM USAGE"));
        rs2.change(() -> {
            recentsSystem = rs2.sel == 1;
            prefs.edit().putString("recentsSrc", recentsSystem ? "system" : "deck").apply();
            usage.info = usageStatus();
            p.invalidate();
            refreshRecents();
        });
        p.add(usage);
        usage.ok(() -> {
            usageGranted = SysUsage.granted(this);
            usage.info = usageStatus();
            p.invalidate();
            refreshRecents();
        });
        final Panel.Row im = p.add(new Panel.Row("ICON MODE").values(IconCache.originalMode ? 1 : 0, "SHARPER", "ORIGINAL"));
        im.change(() -> {
            IconCache.originalMode = im.sel == 1;
            prefs.edit().putInt("iconMode", im.sel).apply();
            reloadIcons();
        });
        String[] ipacks = IconPacks.list(iconsDir);
        String[] ichoices = new String[ipacks.length + 1];
        ichoices[0] = "NONE (APP ICONS)";
        int isel = 0;
        for (int i = 0; i < ipacks.length; i++) {
            ichoices[i + 1] = ipacks[i].toUpperCase(Locale.ROOT);
            if (ipacks[i].equals(iconPack)) isel = i + 1;
        }
        final String[] ipackDirs = ipacks;
        final Panel.Row ipStat = new Panel.Row("ICON PACK STATUS").two(iconPackStatus());
        final Panel.Row ip = p.add(new Panel.Row("ICON PACK").values(isel, ichoices));
        ip.change(() -> {
            setIconPack(ip.sel == 0 ? "" : ipackDirs[ip.sel - 1]);
            reloadIcons();
            ipStat.info = iconPackStatus();
            p.invalidate();
        });
        p.add(new Panel.Row("RELOAD ICONS").ok(() -> installIconZips(false, () -> {
            toast("Icons reloaded");
            // reopen so new pack folders show up in the ICON PACK row
            if (panel == p && !p.closing) openSettings(T_APPS, "RELOAD ICONS");
        })));
        p.add(ipStat);
        p.add(new Panel.Row("CUSTOM ICON FOLDER").two("Android/data/" + getPackageName() + "/files/icons  (<PACKAGE>.PNG OR .WEBP, OR AN ICON PACK .ZIP)"));
        p.add(new Panel.Row("RESET LAYOUT").danger().confirm("PRESS OK AGAIN TO RESET").ok(() -> {
            store.resetLayout();
            rebuildLists(null);
            refresh(true);
            toast("Layout reset");
        }));

        p.tab("SYSTEM");
        p.add(new Panel.Row("TUTORIAL").info(tutorialSummary()).sub().ok(() -> openTutorial(false)));
        if (stockHomeEnabled()) p.add(new Panel.Row("OPEN GOOGLE TV HOME").ok(() -> startFirst(stockHomeIntent())));
        if (canResolve(new Intent(Settings.ACTION_HOME_SETTINGS)))
            p.add(new Panel.Row("DEFAULT HOME APP").ok(() -> startFirst(new Intent(Settings.ACTION_HOME_SETTINGS))));
        p.add(new Panel.Row("ANDROID SETTINGS").ok(() -> startFirst(new Intent(Settings.ACTION_SETTINGS))));
        p.add(new Panel.Row("DEFAULT HOME").info(homeStatus()));
        p.add(new Panel.Row("HOME ADB COMMANDS").sub().ok(this::openHomeCommands));
        p.add(new Panel.Row("PS MENU").info(ccSummary()).sub().ok(() -> openPsMenu(null)));
        p.add(new Panel.Row("PERFORMANCE MODE").info(PerfMode.NAMES[PerfRunner.mode(prefs)]).sub().ok(() -> openPerf(null)));
        p.add(new Panel.Row("GAME PROFILES").info(profileSummary()).sub().ok(() -> openProfiles(null)));
        p.add(new Panel.Row("RESTORE DISPLAY").ok(() -> toast(ProfileRunner.get(this).restoreNow())));
        p.add(new Panel.Row("VERSION").info(appVersion()));
        p.add(new Panel.Row("FONTS").info(Ui.fontStatus));
        p.add(new Panel.Row("INPUT").info(mode == InputMode.GAMEPAD ? "GAMEPAD" : (mode == InputMode.POINTER ? "POINTER" : "REMOTE")));

        p.tab("REMAP");
        addRemapRows(p);

        p.selectLabel(tab, row);
        openPanel(p);
    }

    // ------------------------------------------------------------------ 2.8 control center, game profiles, touchpad volume

    static final int[] TV_KEYS = {0, KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_L2,
            KeyEvent.KEYCODE_BUTTON_R2, KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_START};

    private final TouchVolInput touchVol = new TouchVolInput();

    private final StickVolInput stickVol = new StickVolInput();

    private void loadTouchVol() {
        if (prefs == null) return;
        touchVol.load(prefs);
        stickVol.load(prefs);
    }

    private String touchSeenText() {
        if (!touchVol.enabled()) return "TOUCHPAD VOLUME IS OFF. CHOOSE A HOLD KEY ABOVE, THEN HOLD IT AND TOUCH THE PAD, THEN COME BACK AND PRESS OK.";
        if (touchVol.events == 0) return "NOTHING YET. HOLD THE KEY, TOUCH THE PAD, THEN PRESS OK HERE. IF IT STAYS EMPTY THE BOX DOES NOT SHOW THE PAD TO VISOR: THE FALLBACK IS THE D-PAD WHILE YOU HOLD THE KEY.";
        return touchVol.lastSeen + "  (" + touchVol.events + " EVENTS)";
    }

    private String ccSummary() {
        CcPrefs c = ccHost.ccPrefs();
        return c.isDefault() ? "DEFAULT" : "CHANGED";
    }

    private String profileSummary() {
        int n = ProfileRunner.get(this).profiles().count();
        return n == 0 ? "NONE" : String.valueOf(n);
    }

    /** Setup, SYSTEM, PS MENU: which tiles show in the control center, and their order. */
    private void openPsMenu(String row) {
        final Panel p = new Panel(this, "psmenu", "PS menu");
        p.onBack = () -> openSettings(T_SYSTEM, "PS MENU");
        final CcPrefs c = ccHost.ccPrefs();
        for (int s = 0; s < CcModel.COUNT; s++) {
            final int sec = s;
            int shown = c.visible(s).size(), total = CcModel.defaults(s).size();
            p.add(new Panel.Row(CcModel.SECTION_NAMES[s]).info(shown + " OF " + total + " SHOWN").sub().ok(() -> openPsMenuSection(sec, null)));
        }
        p.add(new Panel.Row("PROTECTED APPS").info(String.valueOf(ccHost.protectUser().size())));
        p.add(new Panel.Row("CLEAR PROTECT LIST").ok(() -> { ccHost.saveProtect(new java.util.HashSet<String>()); openPsMenu("PROTECTED APPS"); }));
        p.add(new Panel.Row("RESET PS MENU").ok(() -> {
            CcHost.saveTiles(prefs, CcPrefs.defaults());
            toast("PS menu back to the defaults");
            openPsMenu("RESET PS MENU");
        }));
        p.selectLabel(0, row == null ? CcModel.SECTION_NAMES[0] : row);
        openPanel(p);
    }

    private void openPsMenuSection(final int sec, String row) {
        final Panel p = new Panel(this, "psmenu", "PS menu: " + CcModel.SECTION_NAMES[sec]);
        p.onBack = () -> openPsMenu(CcModel.SECTION_NAMES[sec]);
        final CcPrefs c = CcPrefs.parse(ccHost.ccPrefs().save());
        for (final CcModel.Tile t : c.order(sec)) {
            final boolean hideable = CcPrefs.canHide(t.id);
            final Panel.Row r = hideable
                    ? new Panel.Row(t.label).values(c.hidden(t.id) ? 1 : 0, "SHOWN", "HIDDEN", "MOVE UP", "MOVE DOWN")
                    : new Panel.Row(t.label).values(0, "ALWAYS SHOWN", "MOVE UP", "MOVE DOWN");
            r.change(() -> {
                // 0 shown, 1 hidden, 2 up, 3 down. A tile that cannot be hidden has ALWAYS SHOWN, UP, DOWN
                int sel = hideable ? r.sel : (r.sel == 0 ? -1 : r.sel + 1);
                if (sel == 0 || sel == 1) { c.setHidden(t.id, sel == 1); }
                else if (sel == 2 || sel == 3) {
                    if (sel == 2) c.moveUp(t.id); else c.moveDown(t.id);
                }
                CcHost.saveTiles(prefs, c);
                if (sel >= 2) { openPsMenuSection(sec, t.label); }
            });
            p.add(r);
        }
        p.selectLabel(0, row == null ? c.order(sec).get(0).label : row);
        openPanel(p);
    }

    /** Setup, SYSTEM, PERFORMANCE MODE (2.12): close the background apps when a game starts. */
    private void openPerf(String row) {
        final Panel p = new Panel(this, "perf", "Performance mode");
        p.onBack = () -> openSettings(T_SYSTEM, "PERFORMANCE MODE");
        final Panel.Row mode = p.add(new Panel.Row("MODE").values(PerfRunner.mode(prefs), PerfMode.NAMES));
        mode.change(() -> prefs.edit().putInt(PerfRunner.PREF_MODE, mode.sel).apply());
        p.add(new Panel.Row("CHOSEN APPS").info(PerfRunner.chosen(prefs).size() + " CHOSEN").sub().ok(() -> openPerfApps()));
        p.add(new Panel.Row("CLOSE BACKGROUND NOW").confirm("PRESS OK AGAIN TO CLOSE THE BACKGROUND APPS").ok(() -> {
            toast(PerfRunner.closeOthers(this, prefs, null));
            openPerf("CLOSE BACKGROUND NOW");
        }));
        p.add(new Panel.Row("LAST RESULT").two(prefs.getString(PerfRunner.PREF_LAST, "NOTHING CLOSED YET. USE CLOSE BACKGROUND NOW OR LAUNCH A GAME.")));
        p.add(new Panel.Row("ABOUT PERFORMANCE MODE").two("WHEN A GAME STARTS, VISOR ENDS THE BACKGROUND PROCESSES OF YOUR OTHER RECENT APPS SO THE GAME HAS MORE MEMORY AND CPU. FREE RAM IS READ BEFORE AND AFTER AND SHOWN HERE. ANDROID KEEPS SOME APPS ALIVE AND RESTARTS OTHERS, SO THE GAIN CAN BE SMALL. VISOR, THE HOME APP, THE GAME AND PROTECTED APPS STAY. THEY START AGAIN WHEN YOU OPEN THEM, WHICH TAKES A MOMENT."));
        p.add(new Panel.Row("EXIT GAME").two("THE CONTROL CENTER (PS BUTTON) HAS AN EXIT GAME TILE IN QUICK. IT GOES BACK TO VISOR AND CLOSES THE GAME. THE APP LIST NEEDS USAGE ACCESS, SEE docs/performance-mode.md."));
        p.selectLabel(0, row == null ? "MODE" : row);
        openPanel(p);
    }

    private void openPerfApps() {
        final Panel p = new Panel(this, "perfapps", "Performance mode: chosen apps");
        p.onBack = () -> openPerf("CHOSEN APPS");
        final java.util.Set<String> chosen = PerfRunner.chosen(prefs);
        java.util.TreeMap<String, String> sorted = new java.util.TreeMap<>();
        if (store != null) for (AppStore.App a : store.all) {
            if (a.system && !a.leanback) continue;
            String pk = a.cn.getPackageName();
            if (!KillCmd.validPackage(pk) || pk.equals(getPackageName())) continue;
            sorted.put(a.label.toUpperCase(Locale.ROOT) + "\u0000" + pk, pk);
        }
        for (final java.util.Map.Entry<String, String> en : sorted.entrySet()) {
            final String pk = en.getValue();
            final Panel.Row r = new Panel.Row(en.getKey().substring(0, en.getKey().indexOf('\u0000'))).values(chosen.contains(pk) ? 1 : 0, "OFF", "ON");
            r.change(() -> {
                java.util.Set<String> c = PerfRunner.chosen(prefs);
                if (r.sel == 1) c.add(pk); else c.remove(pk);
                PerfRunner.saveChosen(prefs, c);
            });
            p.add(r);
        }
        if (sorted.isEmpty()) p.add(new Panel.Row("NO APPS FOUND").two("VISOR'S APP LIST IS EMPTY"));
        openPanel(p);
    }

    /** Setup, SYSTEM, GAME PROFILES: the list. */
    private void openProfiles(String row) {
        final Panel p = new Panel(this, "profiles", "Game profiles");
        p.onBack = () -> openSettings(T_SYSTEM, "GAME PROFILES");
        final ProfileRunner pr = ProfileRunner.get(this);
        for (final GameProfiles.Profile g : pr.profiles().list()) {
            p.add(new Panel.Row(appLabelFor(g.pkg)).info(g.scalePct + "%  " + GameProfiles.methodName(g.method)).sub().ok(() -> openProfile(g.pkg)));
        }
        p.add(new Panel.Row("ADD A PROFILE").sub().ok(() -> openProfileApps()));
        p.add(new Panel.Row("DISPLAY ACCESS").two(DisplayControl.granted(this)
                ? "GRANTED. VISOR CAN CHANGE THE DISPLAY ITSELF (EXPERIMENTAL, NOT TESTED ON A TV)."
                : "NOT GRANTED. FOR THE VISOR DISPLAY METHOD RUN ONCE: " + DisplayControl.GRANT));
        p.add(new Panel.Row("RESTORE DISPLAY").ok(() -> { toast(pr.restoreNow()); }));
        p.add(new Panel.Row("IF THE PICTURE LOOKS WRONG").two("FROM YOUR PHONE: " + DisplayState.DOC_RESTORE));
        p.selectLabel(0, row == null ? "ADD A PROFILE" : row);
        openPanel(p);
    }

    private String appLabelFor(String pkg) {
        if (store != null) for (AppStore.App a : store.all) if (a.cn.getPackageName().equals(pkg)) return a.label.toUpperCase(Locale.ROOT);
        return pkg;
    }

    private void openProfileApps() {
        final Panel p = new Panel(this, "profiles", "Game profiles: pick an app");
        p.onBack = () -> openProfiles("ADD A PROFILE");
        java.util.TreeMap<String, String> sorted = new java.util.TreeMap<>();
        if (store != null) for (AppStore.App a : store.all) {
            if (a.system && !a.leanback) continue;
            if (!KillCmd.validPackage(a.cn.getPackageName()) || a.cn.getPackageName().equals(getPackageName())) continue;
            sorted.put(a.label.toUpperCase(Locale.ROOT) + "\u0000" + a.cn.getPackageName(), a.cn.getPackageName());
        }
        for (final java.util.Map.Entry<String, String> en : sorted.entrySet()) {
            final String pkg = en.getValue();
            p.add(new Panel.Row(en.getKey().substring(0, en.getKey().indexOf('\u0000'))).ok(() -> {
                ProfileRunner pr = ProfileRunner.get(this);
                if (pr.profiles().get(pkg) == null) { GameProfiles.Profile np = new GameProfiles.Profile(pkg); np.method = GameProfiles.M_DISPLAY_HELPER; pr.profiles().put(np); pr.save(); }
                openProfile(pkg);
            }));
        }
        if (sorted.isEmpty()) p.add(new Panel.Row("NO APPS FOUND").two("VISOR'S APP LIST IS EMPTY"));
        openPanel(p);
    }

    /** One app's profile: scale, method, the restore rules, and the exact commands. */
    private void openProfile(final String pkg) {
        final ProfileRunner pr = ProfileRunner.get(this);
        final GameProfiles.Profile g0 = pr.profiles().get(pkg);
        if (g0 == null) { openProfiles(null); return; }
        final GameProfiles.Profile g = g0.copy();
        final Panel p = new Panel(this, "profile", appLabelFor(pkg));
        p.onBack = () -> openProfiles(appLabelFor(pkg));
        int[] panel = DisplayControl.panelSize(this);
        DisplayState saved = DisplayState.parse(prefs.getString(ProfileRunner.PREF_ORIG, ""));
        final int ow = saved.valid ? saved.w : (panel == null ? 1920 : panel[0]);
        final int oh = saved.valid ? saved.h : (panel == null ? 1080 : panel[1]);
        final int od = saved.valid ? saved.dpi : (DisplayControl.currentDpi(this) > 0 ? DisplayControl.currentDpi(this) : 320);
        String[] scales = new String[DisplayRules.SCALES.length];
        int ssel = 0;
        for (int i = 0; i < scales.length; i++) {
            scales[i] = DisplayRules.label(DisplayRules.SCALES[i], ow, oh, od);
            if (DisplayRules.SCALES[i] == g.scalePct) ssel = i;
        }
        final Panel.Row sc = p.add(new Panel.Row("SCALE").values(ssel, scales));
        String[] methods = new String[4];
        for (int i = 0; i < 4; i++) methods[i] = GameProfiles.methodName(i);
        if (ProfileRunner.deckDisplayBlocked(prefs)) methods[GameProfiles.M_DISPLAY_DECK] += " (BLOCKED ON THIS BOX)";
        methods[GameProfiles.M_DISPLAY_HELPER] += " (RECOMMENDED)";
        final Panel.Row me = p.add(new Panel.Row("METHOD").values(g.method, methods));
        final Panel.Row ar = p.add(new Panel.Row("AUTO RESTORE").values(g.autoRestore ? 1 : 0, "OFF", "ON"));
        final Panel.Row lw = p.add(new Panel.Row("LAUNCH WITH PROFILE").values(g.launchWith ? 1 : 0, "OFF", "ON"));
        final Panel.Row pf = p.add(new Panel.Row("PERFORMANCE MODE FOR THIS GAME").values(g.perf, GameBundle.perfName(0), GameBundle.perfName(1), GameBundle.perfName(2)));
        String[] vols = new String[GameBundle.VOLUMES.length];
        for (int i = 0; i < vols.length; i++) vols[i] = GameBundle.volumeName(GameBundle.VOLUMES[i]);
        final Panel.Row vo = p.add(new Panel.Row("VOLUME FOR THIS GAME").values(GameBundle.volumeIndex(g.volume), vols));
        final Panel.Row what = new Panel.Row("WHAT IT DOES").two(profileWhat(g));
        final Panel.Row cmd = new Panel.Row("ADB COMMAND").two(profileCmd(g, ow, oh, od));
        final Panel.Row ph = new Panel.Row("FROM A PHONE (TERMUX)").two(profilePhone(g));
        Runnable save = () -> {
            g.scalePct = DisplayRules.SCALES[sc.sel];
            g.method = me.sel;
            g.autoRestore = ar.sel == 1;
            g.launchWith = lw.sel == 1;
            g.perf = pf.sel;
            g.volume = GameBundle.VOLUMES[vo.sel];
            pr.profiles().put(g);
            pr.save();
            what.info = profileWhat(g);
            cmd.info = profileCmd(g, ow, oh, od);
            ph.info = profilePhone(g);
            p.invalidate();
        };
        sc.change(save);
        me.change(save);
        ar.change(save);
        lw.change(save);
        pf.change(save);
        vo.change(save);
        p.add(what);
        p.add(ph);
        p.add(cmd);
        p.add(new Panel.Row("REMOVE PROFILE").ok(() -> { pr.profiles().remove(pkg); pr.save(); openProfiles(null); }));
        openPanel(p);
    }

    private String profileWhat(GameProfiles.Profile g) {
        switch (g.method) {
            case GameProfiles.M_GAME_MODE: return "ANDROID'S GAME MODE DOWNSCALE FOR THIS APP ONLY. ONLY FOR APPS THAT DECLARE THEMSELVES A GAME, TAKES EFFECT WHEN THE GAME STARTS, NEEDS ADB. VISOR CANNOT RUN IT.";
            case GameProfiles.M_DISPLAY_DECK: return "VISOR CHANGES THE WHOLE DISPLAY WHILE THE APP IS IN FRONT AND PUTS IT BACK AFTER " + (DisplayRules.GRACE_MS / 1000) + " SECONDS AWAY. ADVANCED AND EXPERIMENTAL. NEEDS THE GRANT COMMAND (GAME PROFILES, DISPLAY ACCESS). SOME TV BOXES HIDE THE CALL (THE ONN DOES" + (ProfileRunner.deckDisplayBlocked(prefs) ? ", AND VISOR HAS SEEN IT REFUSE HERE" : "") + "): THEN USE THE PHONE HELPER METHOD. THE APP RESTARTS ITS SCREEN WHEN THE SIZE CHANGES.";
            case GameProfiles.M_DISPLAY_HELPER: return "THE RECOMMENDED METHOD ON THIS BOX. THE WHOLE DISPLAY, CHANGED FROM YOUR PHONE WITH THE TERMUX HELPER (SEE FROM A PHONE BELOW). RUN OFF WHEN YOU ARE DONE.";
            default: return "ONLY SHOWS THE COMMANDS, TO RUN BY HAND. NOTHING IS CHANGED BY VISOR.";
        }
    }

    private String profileCmd(GameProfiles.Profile g, int ow, int oh, int od) {
        String[] on = ProfileCmd.on(g.method, g.pkg, g.scalePct, DisplayState.of(ow, oh, od));
        String[] off = ProfileCmd.off(g.method, g.pkg);
        if (on == null || off == null) return g.scalePct == 100 ? "100 PERCENT CHANGES NOTHING" : "NOT AVAILABLE";
        StringBuilder b = new StringBuilder("ON: ");
        for (int i = 0; i < on.length; i++) b.append(i == 0 ? "" : "; ").append(ProfileCmd.adb(on[i]));
        b.append("   OFF: ");
        for (int i = 0; i < off.length; i++) b.append(i == 0 ? "" : "; ").append(ProfileCmd.adb(off[i]));
        return b.toString();
    }

    private String profilePhone(GameProfiles.Profile g) {
        String m = g.method == GameProfiles.M_GAME_MODE ? "game" : "display";
        return "bash deck/tools/game-profile.sh TVIP:PORT " + g.pkg + " on " + (g.scalePct >= 100 ? 75 : g.scalePct) + " " + m;
    }

    // ------------------------------------------------------------------ 2.9 tutorial and combos

    private boolean offerScheduled;

    private String tutorialSummary() {
        TutorialProgress tp = TutorialProgress.parse(prefs.getString(TutorialProgress.PREF, ""));
        return tp.doneCount() + " OF " + TutorialModel.count();
    }

    /**
     * The tutorial. fromSetup: closing returns to Setup, SYSTEM. The first run offers it by itself once.
     * Leaving early counts as a skip and it is not offered again by itself.
     */
    private void openTutorial(final boolean auto) {
        if (root == null) return;
        TutorialProgress tp = TutorialProgress.parse(prefs.getString(TutorialProgress.PREF, ""));
        int start = auto ? 0 : tp.resumeIndex();
        showTest(new TutorialView(this, this, reduce, tp, start, str -> prefs.edit().putString(TutorialProgress.PREF, str).apply()), "TUTORIAL");
        if (auto) testBack = () -> { };
        else testBack = () -> openSettings(T_SYSTEM, "TUTORIAL");
    }

    /** True when the connected controller reports a gyroscope (Android 12 and newer can ask). */
    private boolean padHasGyro() {
        try {
            if (android.os.Build.VERSION.SDK_INT < 31) return false;
            android.view.InputDevice d = GyroCalStore.pad(padId);
            if (d == null) return false;
            android.hardware.SensorManager sm = d.getSensorManager();
            return sm != null && !sm.getSensorList(android.hardware.Sensor.TYPE_GYROSCOPE).isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    /** 2.18: the first time the home screen is showing with a gyro controller connected, open the gyro test with a welcome. Once. */
    private void maybeOfferGyroIntro() {
        try {
            if (destroyed || safeMode || root == null || prefs == null) return;
            boolean showing = resumed && panel == null && test == null && quick == null && preview == null;
            TutorialProgress tp = TutorialProgress.parse(prefs.getString(TutorialProgress.PREF, ""));
            if (!GyroIntro.shouldOffer(prefs.getBoolean(GyroIntro.PREF, false), tp.shouldOffer(), showing, padHasGyro())) return;
            prefs.edit().putBoolean(GyroIntro.PREF, true).apply();   // asked once, by showing it
            GyroTestView v = new GyroTestView(this, this, reduce, padId, gyroFollow, gyroStrength, gyroOn, calStore);
            v.setIntro(true);
            showTest(v, "GYRO TEST");
            testBack = () -> { };
        } catch (Throwable ignored) {}
    }

    /** Once, a moment after Deck starts: the tutorial is offered if it was never finished or skipped. */
    private void maybeOfferTutorial() {
        try {
            if (destroyed || safeMode || root == null || !resumed) return;
            if (panel != null || test != null || quick != null || preview != null) return;
            TutorialProgress tp = TutorialProgress.parse(prefs.getString(TutorialProgress.PREF, ""));
            if (!tp.shouldOffer()) { maybeOfferGyroIntro(); return; }
            // asked once: leaving it in any way counts, so a crash or a power cut cannot make it come back every start
            tp.dismiss();
            prefs.edit().putString(TutorialProgress.PREF, tp.save()).apply();
            openTutorial(true);
        } catch (Throwable ignored) {}
    }

    private String comboSummary() {
        int n = ComboTable.parse(prefs.getString(ComboTable.PREF, "")).size();
        return n == 0 ? "NONE" : String.valueOf(n);
    }

    private static String comboKeyName(long k) { return RemapKey.name(k); }

    private void saveCombos(ComboTable t) {
        prefs.edit().putString(ComboTable.PREF, t.save()).apply();
        reloadRemap();
    }

    /** Setup, REMAP, COMBOS: the list. */
    private void openCombos(String row) {
        final Panel p = new Panel(this, "combos", "Button combos");
        p.onBack = () -> openSettings(T_REMAP, "COMBOS");
        final ComboTable t = ComboTable.parse(prefs.getString(ComboTable.PREF, ""));
        for (int i = 0; i < t.size(); i++) {
            final ComboTable.Combo c = t.get(i);
            p.add(new Panel.Row(ComboTable.describe(c, HomeActivity::comboKeyName)).info(RemapAction.name(c.action)).sub().ok(() -> openCombo(c.a, c.b)));
        }
        p.add(new Panel.Row("NEW COMBO").sub().ok(this::showComboLearn));
        p.add(new Panel.Row("HOW COMBOS WORK").two("HOLD ONE BUTTON AND PRESS ANOTHER. THE FIRST STILL GOES TO THE APP, ONLY THE SECOND IS TAKEN. OUTSIDE VISOR THEY NEED THE OVERLAY SERVICE, AND IN GAMES THE SWITCH REMAP IN GAMES."));
        p.add(new Panel.Row("COMBOS OUTSIDE VISOR").info(psStatus() == PsSetup.ON ? "ON" : "OFF, NEEDS THE OVERLAY SERVICE"));
        p.selectLabel(0, row == null ? "NEW COMBO" : row);
        openPanel(p);
    }

    private void showComboLearn() {
        showTest(new ComboLearnView(this, this, reduce, (a, b) -> {
            ComboTable t = ComboTable.parse(prefs.getString(ComboTable.PREF, ""));
            if (!ComboTable.canAdd(t, a, b)) {
                if (test != null) test.exit("That combo cannot be added. The list may be full.");
                return;
            }
            // a new combo has no action yet: the next screen asks for one, and nothing is saved until it is chosen
            testBack = () -> openCombo(a, b);
            if (test != null) test.exit(null);
        }), "NEW COMBO");
        testBack = () -> openCombos("NEW COMBO");
    }

    /** One combo: its action, clear, and where it works. */
    private void openCombo(final long a, final long b) {
        final ComboTable t = ComboTable.parse(prefs.getString(ComboTable.PREF, ""));
        final ComboTable.Combo c = t.find(a, b);
        final Panel p = new Panel(this, "combo", ComboTable.describe(c != null ? c : new ComboTable.Combo(a, b), HomeActivity::comboKeyName));
        p.onBack = () -> openCombos(null);
        p.add(new Panel.Row("ACTION").info(c == null ? "NOT CHOSEN YET" : RemapAction.name(c.action)).sub().ok(() -> openComboActions(a, b)));
        if (c != null) {
            p.add(new Panel.Row("CLEAR THIS COMBO").danger().confirm("PRESS OK AGAIN TO CLEAR IT").ok(() -> {
                saveCombos(ComboTable.parse(prefs.getString(ComboTable.PREF, "")).without(a, b));
                toast("Combo cleared");
                openCombos(null);
            }));
        }
        p.add(new Panel.Row("WHERE IT WORKS").two("IN VISOR, AND IN OTHER APPS WHILE THE OVERLAY SERVICE IS ON. IN GAMES ONLY WITH REMAP IN GAMES. THE FIRST BUTTON ALWAYS REACHES THE APP."));
        openPanel(p);
    }

    private void openComboActions(final long a, final long b) {
        final Panel p = new Panel(this, "comboact", "Combo action");
        p.onBack = () -> openCombo(a, b);
        ComboTable.Combo cur = ComboTable.parse(prefs.getString(ComboTable.PREF, "")).find(a, b);
        String curLabel = null;
        for (final int act : RemapAction.picker(false, hasInputs())) {
            if (act == RemapAction.DEFAULT || act == RemapAction.NOTHING || RemapAction.deckKey(act)) continue;
            Panel.Row row = new Panel.Row(RemapAction.name(act)).id(RemapAction.helpId(act));
            if (cur != null && cur.action == act) { row.info("NOW"); curLabel = RemapAction.name(act); }
            else if (RemapAction.needsService(act) && PsService.live == null) row.info("NEEDS SERVICE");
            row.ok(() -> {
                if (act == RemapAction.APP) openComboApps(a, b); else applyComboAction(a, b, act, "");
            });
            p.add(row);
        }
        p.selectLabel(0, curLabel);
        openPanel(p);
    }

    private void applyComboAction(long a, long b, int action, String arg) {
        ComboTable t = ComboTable.parse(prefs.getString(ComboTable.PREF, ""));
        ComboTable.Combo c = new ComboTable.Combo(a, b);
        c.action = action;
        c.arg = arg == null ? "" : arg;
        c.label = "";
        if (!ComboTable.validAction(c.action, c.arg, c.a, c.b) || !ComboTable.canSet(t, c)) {
            fx(SoundMap.ERROR);
            toast("That action cannot be used for this combo.");
            openCombo(a, b);
            return;
        }
        saveCombos(t.with(c));
        fx(SoundMap.SELECT);
        if (RemapAction.needsService(action) && PsService.live == null) toast("Saved. Outside Visor this needs the overlay service.");
        openCombo(a, b);
    }

    private void openComboApps(final long a, final long b) {
        final Panel p = new Panel(this, "remapapp", "Launch app");
        p.onBack = () -> openComboActions(a, b);
        if (scanCache != null) fillComboApps(p, scanCache, a, b);
        else p.add(new Panel.Row("SCANNING INSTALLED APPS").info("PLEASE WAIT"));
        openPanel(p);
        if (scanCache != null) return;
        final Context app = getApplicationContext();
        try {
            work.execute(() -> {
                ArrayList<AppStore.App> l = null;
                try { l = AppStore.scanAll(app); } catch (Throwable ignored) {}
                final ArrayList<AppStore.App> res = l;
                h.post(() -> {
                    if (destroyed || res == null) return;
                    scanCache = res;
                    if (panel == p && !p.closing) { fillComboApps(p, res, a, b); focusChanged(true); }
                });
            });
        } catch (Throwable ignored) {}
    }

    private void fillComboApps(Panel p, ArrayList<AppStore.App> apps, final long a, final long b) {
        ArrayList<Panel.Row> rows = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        for (final AppStore.App ap : apps) {
            final String pkg = ap.cn.getPackageName();
            if (!seen.add(pkg)) continue;
            rows.add(new Panel.Row(ap.label).ok(() -> applyComboAction(a, b, RemapAction.APP, pkg)));
        }
        if (rows.isEmpty()) rows.add(new Panel.Row("NO LAUNCHABLE APPS FOUND"));
        p.setRows(0, rows);
    }

    // ------------------------------------------------------------------ 2.7 wallpapers

    /**
     * Applies the chosen wallpaper. The built-in skyline is the panorama as before. OFF is a plain dark
     * background (the skyline bitmaps are freed). A pack is decoded on the worker thread, with the
     * panorama plain underneath, and replaces the one decoded wallpaper Deck keeps. A bad pack ends in
     * the status line and the skyline.
     */
    private void applyWallpaper() {
        if (root == null || wall == null || pano == null) return;
        wallGen++;
        final int my = wallGen;
        wall.release();
        wall.setVisibility(View.GONE);
        wallSpec = null;
        wallOpts = null;
        wallReleased = false;
        String sel = Wallpapers.normalize(wallSel);
        if (sel.equals(Wallpapers.SKYLINE)) {
            pano.setPlain(false);
            setWallStatus("BUILT-IN SKYLINE");
            return;
        }
        if (sel.equals(Wallpapers.OFF)) {
            pano.setPlain(true);
            setWallStatus("OFF: PLAIN DARK BACKGROUND");
            return;
        }
        final String name = Wallpapers.packName(sel);
        final File dir = wallDir;
        pano.setPlain(true);   // cheap while the pack decodes and while it sits on top
        setWallStatus("LOADING " + name.toUpperCase(Locale.ROOT));
        if (work == null) { wallFail(name, "NO WORKER", my); return; }
        final boolean rd = reduce;
        final int viewW = Ui.W;
        try {
            work.execute(() -> {
                if (my != wallGen) return;
                WallpaperSpec spec = null;
                AnimSpec opts = null;
                WallpaperScene sc = null;
                String err;
                try {
                    File pd = Wallpapers.packDir(dir, name);
                    spec = Wallpapers.read(pd);
                    err = spec.error;
                    if (err == null) {
                        opts = WallpaperOptions.build(name, spec);
                        opts.load(wallStore);
                        if (my == wallGen) sc = WallpaperLoader.load(pd, spec, opts, viewW, rd);
                    }
                } catch (WallpaperLoader.Fail f) {
                    err = f.getMessage();
                } catch (Throwable t) {
                    err = "COULD NOT LOAD THE PACK";
                }
                final WallpaperScene scene = sc;
                final WallpaperSpec fs = spec;
                final AnimSpec fo = opts;
                final String fe = err == null ? "COULD NOT LOAD THE PACK" : err;
                h.post(() -> {
                    if (destroyed || my != wallGen || wall == null) { if (scene != null) scene.release(); return; }
                    if (scene == null) { wallFail(name, fe, my); return; }
                    wallSpec = fs;
                    wallOpts = fo;
                    wall.setReduce(reduce);
                    wall.setScene(scene);
                    wall.setLook(fo, Ui.T.a);
                    wall.setVisibility(View.VISIBLE);
                    if (!reduce) {
                        wall.setAlpha(0f);
                        wall.animate().alpha(1f).setDuration(300).start();
                    } else {
                        wall.setAlpha(1f);
                    }
                    wall.setRunning(resumed);
                    setWallStatus(WallpaperLoader.describe(scene));
                    if (panel != null && "settings".equals(panel.kind) && !panel.closing) panel.invalidate();
                });
            });
        } catch (Throwable t) {
            wallFail(name, "COULD NOT START THE LOADER", my);
        }
    }

    private void wallFail(String name, String why, int my) {
        if (my != wallGen || pano == null) return;
        pano.setPlain(false);
        wallSpec = null;
        wallOpts = null;
        setWallStatus("BAD PACK " + name.toUpperCase(Locale.ROOT) + ": " + why + "  //  USING THE BUILT-IN SKYLINE");
        if (resumed) toast("Wallpaper " + name + " failed: " + why);
    }

    private void setWallStatus(String st) {
        wallStatus = st;
        if (wallStatRow != null && wallStatPanel != null && panel == wallStatPanel && !wallStatPanel.closing) {
            wallStatRow.info = st;
            wallStatPanel.invalidate();
        }
    }

    /** Deck left the screen: free the decoded wallpaper. onStart decodes it again. */
    private void releaseWallpaper() {
        wallGen++;
        if (wall == null) return;
        boolean had = wall.hasScene() || Wallpapers.isPack(wallSel);
        wall.release();
        wall.setVisibility(View.GONE);
        if (had && Wallpapers.isPack(Wallpapers.normalize(wallSel))) wallReleased = true;
    }

    private void applyWallLook() {
        if (wall != null && wallOpts != null) wall.setLook(wallOpts, Ui.T.a);
    }

    private void prepareWallpaperFolder() {
        final File dir = wallDir;
        if (dir == null || work == null) return;
        try {
            work.execute(() -> {
                try {
                    if (!dir.isDirectory() && !dir.mkdirs()) return;
                    File readme = new File(dir, "README.txt");
                    if (readme.exists()) return;
                    try (FileOutputStream o = new FileOutputStream(readme)) {
                        o.write(("Wallpaper packs for Visor.\n"
                                + "A pack is a folder (or a .zip of one) with a wallpaper.json and its images:\n"
                                + ".png .jpg .jpeg .webp .gif, 8 MB each, 24 MB and 16 files per pack.\n"
                                + "Then pick Setup > LOOK > RELOAD WALLPAPERS and choose it in WALLPAPER.\n"
                                + "See docs/wallpapers.md in the Visor repository.\n").getBytes(StandardCharsets.UTF_8));
                    }
                } catch (Throwable ignored) {}
            });
        } catch (Throwable ignored) {}
    }

    /**
     * Unpacks new wallpaper pack zips on the worker thread. pick = true (deck_cmd reload_wallpapers): if
     * exactly one new pack came in, choose it. reload = true also decodes the wallpaper again.
     */
    private void installWallpaperZips(boolean pick) { installWallpaperZips(pick, pick, null); }

    private void installWallpaperZips(final boolean pick, final boolean reload, final Runnable after) {
        final File dir = wallDir;
        if (work == null || dir == null) { if (reload) applyWallpaper(); if (after != null) after.run(); return; }
        try {
            work.execute(() -> {
                ArrayList<PackZip.Result> rs = null;
                try {
                    if (!dir.isDirectory()) dir.mkdirs();
                    rs = Wallpapers.installZips(dir);
                } catch (Throwable ignored) {}
                final ArrayList<PackZip.Result> res = rs == null ? new ArrayList<PackZip.Result>() : rs;
                h.post(() -> {
                    if (destroyed) return;
                    int okN = 0;
                    String only = null;
                    StringBuilder st = new StringBuilder();
                    for (PackZip.Result r : res) {
                        if (st.length() > 0) st.append("  //  ");
                        if (r.ok) { okN++; only = r.name; st.append("INSTALLED ").append(r.name.toUpperCase(Locale.ROOT)).append(" (").append(r.files).append(" FILES)"); }
                        else st.append(r.zip.toUpperCase(Locale.ROOT)).append(": ").append(r.error);
                    }
                    if (st.length() > 0) wallZipStatus = st.toString();
                    boolean chose = false;
                    if (pick && okN == 1 && only != null && !Wallpapers.sel(only).equals(wallSel)) {
                        wallSel = Wallpapers.sel(only);
                        prefs.edit().putString("wallpaper", wallSel).apply();
                        toast("Wallpaper pack " + only + " installed and selected");
                        chose = true;
                    } else if (okN > 0) {
                        toast(okN == 1 ? "Wallpaper pack " + only + " installed" : okN + " wallpaper packs installed");
                    } else if (!res.isEmpty()) {
                        toast("Wallpaper zip failed: " + res.get(0).error);
                    }
                    if (reload || chose || okN > 0) applyWallpaper();
                    if (after != null) after.run();
                });
            });
        } catch (Throwable t) {
            if (reload) applyWallpaper();
            if (after != null) after.run();
        }
    }

    private String packInfo() {
        WallpaperSpec sp = wallSpec;
        if (sp == null) return "";
        StringBuilder b = new StringBuilder();
        b.append(sp.name.isEmpty() ? "UNNAMED" : sp.name.toUpperCase(Locale.ROOT));
        if (!sp.author.isEmpty()) b.append("  BY ").append(sp.author.toUpperCase(Locale.ROOT));
        if (!sp.version.isEmpty()) b.append("  V").append(sp.version.toUpperCase(Locale.ROOT));
        b.append("  //  ").append(WallpaperSpec.TYPE_NAMES[sp.type].toUpperCase(Locale.ROOT));
        if (!sp.notes.isEmpty()) b.append("  //  ").append(sp.notes.get(0).toUpperCase(Locale.ROOT));
        return b.toString();
    }

    /** Setup, LOOK, WALLPAPER OPTIONS: Deck's common options and the pack's own, each a live row. */
    private void openWallpaperOptions(String row) {
        if (wallOpts == null || wallSpec == null || !Wallpapers.isPack(wallSel)) {
            fx(SoundMap.ERROR);
            toast(Wallpapers.isPack(wallSel) ? "This pack did not load. See WALLPAPER STATUS" : "Pick a wallpaper pack first. The built-in skyline has no options");
            return;
        }
        final Panel p = new Panel(this, "wpopts", "Wallpaper options");
        p.onBack = () -> openSettings(T_LOOK, "WALLPAPER OPTIONS");
        p.add(new Panel.Row("PACK").two(packInfo()));
        for (AnimOption o : wallOpts.options) p.add(wallRow(o));
        p.add(new Panel.Row("RESET OPTIONS").danger().confirm("PRESS OK AGAIN TO RESET THEM").ok(() -> {
            wallOpts.resetAll(wallStore);
            applyWallLook();
            openWallpaperOptions("RESET OPTIONS");
            toast("Wallpaper options reset");
        }));
        p.selectLabel(0, row == null ? "PACK" : row);
        openPanel(p);
    }

    /** One row for one wallpaper option: a slider for numbers in whole steps, a value row for the rest. Changes show at once. */
    private Panel.Row wallRow(final AnimOption o) {
        final Panel.Row r;
        String id = WallpaperOptions.isCommon(o.key) ? "wp." + o.key : "wp.custom";
        if (o.type == AnimOption.INT_RANGE) {
            r = new Panel.Row(o.unit.isEmpty() ? o.label : o.label + " (" + o.unit + ")").slider((int) o.min, (int) o.max, (int) Math.max(1, o.step), o.i()).id(id);
            r.change(() -> {
                o.parse(String.valueOf(r.val));
                wallOpts.save(wallStore, o);
                applyWallLook();
            });
        } else {
            String[] v = new String[o.steps()];
            for (int k = 0; k < v.length; k++) v[k] = o.stepText(k);
            r = new Panel.Row(o.label).values(o.stepIndex(), v).id(id);
            r.change(() -> {
                o.setStep(r.sel);
                wallOpts.save(wallStore, o);
                applyWallLook();
            });
        }
        return r;
    }

    // ------------------------------------------------------------------ 2.7 screensaver

    private String saverSummary() {
        return DreamRules.STYLE_NAMES[DreamRules.clampStyle(saverStyle)];
    }

    /** What Android has set, read from the secure settings. If the box refuses the read, says so. */
    private String saverStatus() {
        try {
            android.content.ContentResolver cr = getContentResolver();
            String comps = Settings.Secure.getString(cr, ScreensaverCmd.COMPONENTS);
            String en = Settings.Secure.getString(cr, ScreensaverCmd.ENABLED);
            String sl = Settings.Secure.getString(cr, ScreensaverCmd.ON_SLEEP);
            long to = Settings.System.getLong(cr, Settings.System.SCREEN_OFF_TIMEOUT, -1L);
            return ScreensaverCmd.describe(comps, en, sl, to);
        } catch (Throwable t) {
            return "THIS BOX WOULD NOT LET VISOR READ THE SCREENSAVER SETTINGS. USE THE ADB STATUS COMMAND";
        }
    }

    /** Setup, LOOK, SCREENSAVER. */
    private void openSaver(String row) {
        final Panel p = new Panel(this, "saver", "Screensaver");
        p.onBack = () -> openSettings(T_LOOK, "SCREENSAVER");
        final Panel.Row sty = p.add(new Panel.Row("SCREENSAVER STYLE").values(DreamRules.clampStyle(saverStyle), DreamRules.STYLE_NAMES));
        sty.change(() -> { saverStyle = sty.sel; prefs.edit().putInt("saverStyle", saverStyle).apply(); });
        final Panel.Row sdim = p.add(new Panel.Row("SCREENSAVER DIM").slider(DreamRules.DIM_MIN, DreamRules.DIM_MAX, DreamRules.DIM_STEP, DreamRules.clampDim(saverDim)));
        sdim.change(() -> { saverDim = sdim.val; prefs.edit().putInt("saverDim", saverDim).apply(); });
        final Panel.Row smo = p.add(new Panel.Row("SCREENSAVER MOTION").values(saverMotion ? 1 : 0, "OFF", "ON"));
        smo.change(() -> { saverMotion = smo.sel == 1; prefs.edit().putBoolean("saverMotion", saverMotion).apply(); });
        final Panel.Row sst = p.add(new Panel.Row("SCREENSAVER STATS").values(saverStats ? 1 : 0, "OFF", "ON"));
        sst.change(() -> { saverStats = sst.sel == 1; prefs.edit().putBoolean("saverStats", saverStats).apply(); });
        p.add(new Panel.Row("PREVIEW SCREENSAVER").ok(this::startPreview));
        final Panel.Row stat = p.add(new Panel.Row("SCREENSAVER STATUS").two(saverStatus()));
        stat.ok(() -> { stat.info = saverStatus(); p.invalidate(); });
        p.add(new Panel.Row("OPEN ANDROID SCREENSAVER SETTINGS").disabled(saverPageMissing).info(saverPageMissing ? "NOT ON THIS TV" : "").ok(() -> {
            // Settings' own page (DREAM_SETTINGS), then the AOSP TV settings screensaver page. Resolving them first
            // would need a manifest <queries> entry, so Deck just tries and reports.
            boolean ok = startFirst(new Intent(Settings.ACTION_DREAM_SETTINGS),
                    new Intent().setClassName("com.android.tv.settings", "com.android.tv.settings.device.display.daydream.DaydreamActivity"));
            if (!ok) { saverPageMissing = true; openSaver("OPEN ANDROID SCREENSAVER SETTINGS"); }
        }));
        p.add(new Panel.Row("SET AS SYSTEM SCREENSAVER").sub().ok(this::openSaverCommands));
        p.selectLabel(0, row == null ? "SCREENSAVER STYLE" : row);
        openPanel(p);
    }

    /** The exact adb commands that make Deck's screensaver the system one (the same as tools/enable-screensaver.sh). */
    private void openSaverCommands() {
        final Panel p = new Panel(this, "savercmds", "Screensaver: adb commands");
        p.onBack = () -> openSaver("SET AS SYSTEM SCREENSAVER");
        p.add(new Panel.Row("NOW").two(saverStatus()));
        String[] on = ScreensaverCmd.on(0);
        p.add(new Panel.Row("1. CHOOSE VISOR AS THE SCREENSAVER").two("adb shell " + on[0]));
        p.add(new Panel.Row("2. TURN SCREENSAVERS ON").two("adb shell " + on[1]));
        p.add(new Panel.Row("3. START IT WHEN THE TV IS IDLE").two("adb shell " + on[2]));
        String[] t15 = ScreensaverCmd.on(15);
        p.add(new Panel.Row("OPTIONAL: START AFTER 15 MINUTES").two("adb shell " + t15[3]));
        p.add(new Panel.Row("FROM A PHONE (TERMUX)").two("bash enable-screensaver.sh TVIP:PORT on 15"));
        p.add(new Panel.Row("UNDO").two("adb shell " + ScreensaverCmd.off()));
        p.add(new Panel.Row("TRY IT NOW").two("adb shell am start -n " + getPackageName() + "/.HomeActivity --es deck_cmd preview_screensaver"));
        openPanel(p);
    }

    /**
     * PREVIEW SCREENSAVER: the real screensaver screen inside Deck. Any key, pointer move, touch or gamepad
     * push ends it (after a 600 ms grace, so the OK that started it cannot). Deck's own wallpaper is
     * released first, so only one decoded wallpaper exists.
     */
    private void startPreview() {
        if (root == null || preview != null || destroyed) return;
        if (test != null) removeTest();
        if (quick != null) closeQuick(false);
        if (panel != null) { panel.onBack = null; panel.afterClose = null; closePanel(); }
        wallGen++;
        if (wall != null) { wall.release(); wall.setVisibility(View.GONE); }
        if (pano != null) pano.setPlain(true);
        wallReleased = true;
        final DreamRoot dr = new DreamRoot(this, prefs, () -> h.post(() -> { if (preview != null) stopPreview(true); }));
        preview = dr;
        root.putPx(dr, 0, 0, root.getWidth() > 0 ? root.getWidth() : Ui.W, root.getHeight() > 0 ? root.getHeight() : Ui.H);
        try {
            dr.start();
        } catch (Throwable t) {
            stopPreview(false);
            toast("The screensaver preview failed");
        }
    }

    /** Ends the preview and brings Deck's wallpaper back. reopen = go back to the SCREENSAVER screen in Setup. */
    private void stopPreview(boolean reopen) {
        DreamRoot dr = preview;
        if (dr == null) return;
        preview = null;
        try { dr.stop(); } catch (Throwable ignored) {}
        if (root != null) root.removeView(dr);
        wallReleased = false;
        applyWallpaper();
        if (engine != null) engine.kick();
        if (reopen && !destroyed && resumed) openSaver("PREVIEW SCREENSAVER");
    }

    // ------------------------------------------------------------------ 2.3 animations

    private String animSummary() {
        AnimOption t = anim.get("text"), sp = anim.get("speed");
        return (t == null ? "" : t.choice()) + "  //  " + (sp == null ? "" : sp.display()) + (AnimConfig.glass(anim) ? "  //  GLASS" : "");
    }

    /** One Setup row from one schema option: sliders for INT_RANGE, value rows for the rest. */
    private Panel.Row animRow(final Panel p, final AnimOption o) {
        final Panel.Row r;
        if (o.type == AnimOption.INT_RANGE) {
            r = new Panel.Row(o.unit.isEmpty() ? o.label : o.label + " (" + o.unit + ")").slider((int) o.min, (int) o.max, (int) Math.max(1, o.step), o.i()).id("anim." + o.key);
            r.change(() -> {
                o.parse(String.valueOf(r.val));
                anim.save(animStore, o);
            });
        } else {
            String[] v = new String[o.steps()];
            for (int k = 0; k < v.length; k++) v[k] = o.stepText(k);
            r = new Panel.Row(o.label).values(o.stepIndex(), v).id("anim." + o.key);
            r.change(() -> {
                o.setStep(r.sel);
                anim.save(animStore, o);
                // look changes show on this screen right away
                if ("style".equals(o.key) || "dir".equals(o.key)) p.style(AnimConfig.glass(anim), AnimConfig.rtl(anim));
            });
        }
        return r;
    }

    /** Setup > ANIMATIONS > OPTIONS FLYOUT: rows generated from the schema, a PREVIEW and per screen switches. */
    private void openAnimOptions(String row) {
        final Panel p = new Panel(this, "anim", "Options flyout");
        p.onBack = () -> openSettings(T_ANIM, "OPTIONS FLYOUT");
        p.add(new Panel.Row("PREVIEW").info("PLAYS IT ON A SAMPLE APP").sub().ok(this::openAnimPreview));
        for (AnimOption o : anim.options) if (!o.key.startsWith("screen.")) p.add(animRow(p, o));
        p.add(new Panel.Row("SCREENS").two("TURN ONE OFF TO SHOW IT WITHOUT ANIMATION. REDUCE EFFECTS STILL WINS."));
        for (AnimOption o : anim.options) if (o.key.startsWith("screen.")) p.add(animRow(p, o));
        p.add(new Panel.Row("RESET TO DEFAULTS").danger().confirm("PRESS OK AGAIN TO RESET THEM").ok(() -> {
            anim.resetAll(animStore);
            openAnimOptions("RESET TO DEFAULTS");
            toast("Animation options reset");
        }));
        p.add(new Panel.Row("SAVED AS").two("SHAREDPREFERENCES anim.flyout.KEY  (SAME KEYS A THEME FILE WILL USE)"));
        p.selectLabel(0, row == null ? "PREVIEW" : row);
        openPanel(p);
    }

    /** PREVIEW: the real flyout on the focused (or first) app, with sample rows. Any row or Back returns. */
    private void openAnimPreview() {
        AppStore.App a = focusedApp();
        if (a == null && !list.isEmpty()) a = list.get(0);
        final Panel p = new Panel(this, "preview", a == null ? "Sample app" : a.label);
        final Runnable back = () -> closePanelThen(() -> openAnimOptions("PREVIEW"));
        p.onBack = back;
        String[] sample = {"MOVE LEFT", "MOVE RIGHT", "PIN TO DOCK", "HIDE", "CHANGE ICON", "APP INFO"};
        for (String sname : sample) p.add(new Panel.Row(sname).ok(back));
        p.add(new Panel.Row("THIS IS A PREVIEW").two("OK OR BACK RETURNS TO THE OPTIONS. NOTHING HERE CHANGES THE APP."));
        // glide from where the app sits on the home screen, as the real menu would
        int z = zone;
        zone = Z_MAIN;
        openFlyout(p, a);
        zone = z;
    }

    // ------------------------------------------------------------------ 2.1 setup screens

    /** True only when a controller is attached and Android exposes an RGB light on it. */
    private boolean lightExposed() { return pad != null && pad.deviceId >= 0 && pad.hasLight(); }

    private String usageStatus() {
        if (!recentsSystem) return "NOT USED (SOURCE IS VISOR LAUNCHES). NO PERMISSION NEEDED.";
        usageGranted = SysUsage.granted(this);
        return usageGranted ? "GRANTED. USING ANDROID USAGE HISTORY." : "NOT GRANTED. RUN: " + SysUsage.GRANT + "  (UNTIL THEN VISOR LAUNCHES ARE USED)";
    }

    private void prepareIconFolder() {
        final File dir = iconsDir;
        if (dir == null || work == null) return;
        try {
            work.execute(() -> {
                try {
                    if (!dir.isDirectory() && !dir.mkdirs()) return;
                    File readme = new File(dir, "README.txt");
                    if (readme.exists()) return;
                    try (FileOutputStream o = new FileOutputStream(readme)) {
                        o.write(("Custom app icons for Visor.\n"
                                + "Name a PNG or WebP after the app's package, for example com.netflix.ninja.png.\n"
                                + "Wide images (16:9) fill the tile like a TV banner. Square ones sit on the icon plate.\n"
                                + "Then pick Setup > APPS > Reload icons. Files over 4 MB are ignored.\n"
                                + "Icon packs: drop a .zip of <package>.png or .webp files here, then choose it in Setup > APPS > Icon pack.\n"
                                + "A single file here always wins over the chosen pack.\n").getBytes(StandardCharsets.UTF_8));
                    }
                } catch (Throwable ignored) {}
            });
        } catch (Throwable ignored) {}
    }

    private String iconPackStatus() {
        String chosen;
        if (iconPack.isEmpty()) chosen = "NONE CHOSEN";
        else {
            File d = IconPacks.packDir(iconsDir, iconPack);
            chosen = d == null ? iconPack.toUpperCase(Locale.ROOT) + " IS MISSING, USING APP ICONS" : iconPack.toUpperCase(Locale.ROOT) + "  " + IconPacks.count(d) + " ICONS";
        }
        return chosen + "  //  SINGLE FILES IN THE ICONS FOLDER WIN  //  " + iconZipStatus;
    }

    void reloadIcons() {
        if (icons == null) return;
        icons.overrideOff = new HashSet<>(OrderList.splitSet(prefs.getString("iconOff", "")));
        icons.evictAll();
        if (root == null) return;
        for (TileView t : slots) t.setIcon(null);
        for (TileView t : gridTiles) t.setIcon(null);
        for (Chip c : dock) if (c.app != null) c.setIcon(null);
        rebindIcons();
    }

    /** Who answers the Home button: RoleManager on Android 10+, plus what HOME resolves to. */
    private String homeStatus() {
        boolean held = false, roleKnown = false;
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                RoleManager rm = getSystemService(RoleManager.class);
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) { roleKnown = true; held = rm.isRoleHeld(RoleManager.ROLE_HOME); }
            }
        } catch (Throwable ignored) {}
        String who = "UNKNOWN";
        try {
            ResolveInfo ri = getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                    PackageManager.MATCH_DEFAULT_ONLY);
            String pkg = ri == null || ri.activityInfo == null ? null : ri.activityInfo.packageName;
            if (pkg == null) who = "NONE";
            else if (pkg.equals(getPackageName())) who = "VISOR";
            else if (pkg.equals("android")) who = "NOT SET (ANDROID ASKS)";
            else {
                CharSequence l = ri.loadLabel(getPackageManager());
                who = "OTHER APP: " + (l == null ? pkg : l.toString()).toUpperCase(Locale.ROOT);
            }
        } catch (Throwable ignored) {}
        if (roleKnown && held && !"VISOR".equals(who)) return "VISOR HAS THE ROLE, HOME OPENS " + who;
        return who;
    }

    private void openHomeCommands() {
        final Panel p = new Panel(this, "homecmds", "Default home: adb commands");
        p.onBack = () -> openSettings(T_SYSTEM, "HOME ADB COMMANDS");
        p.add(new Panel.Row("NOW").two("DEFAULT HOME: " + homeStatus()));
        p.add(new Panel.Row("1. MAKE VISOR THE HOME ROLE").two("adb shell cmd role add-role-holder --user 0 android.app.role.HOME " + getPackageName()));
        p.add(new Panel.Row("2. SET THE HOME ACTIVITY").two("adb shell cmd package set-home-activity " + getPackageName() + "/.HomeActivity"));
        p.add(new Panel.Row("3. ONLY AFTER A DAY WITHOUT TROUBLE AND A REBOOT TEST").two("adb shell pm disable-user --user 0 " + STOCK_HOME));
        p.add(new Panel.Row("UNDO STEP 3").two("adb shell pm enable " + STOCK_HOME));
        p.add(new Panel.Row("WHY HOME STILL OPENS GOOGLE TV").two("WHILE GOOGLE TV'S LAUNCHER IS ENABLED IT WINS THE HOME BUTTON. NOT A VISOR BUG."));
        openPanel(p);
    }

    private void openInstallPack() {
        final Panel p = new Panel(this, "install", "Install sound pack");
        p.onBack = () -> openSettings(T_SOUND, "INSTALL PACK");
        final File dir = sounds.packsDir();
        if (dir == null) {
            p.add(new Panel.Row("STORAGE NOT AVAILABLE").info("TRY AGAIN LATER"));
            openPanel(p);
            return;
        }
        File[] zs = PackZip.zips(dir);
        for (final File z : zs) {
            String as = PackZip.installedAs(z, dir);
            p.add(new Panel.Row("ZIP  " + z.getName()).info(as != null ? "INSTALLED AS " + as.toUpperCase(Locale.ROOT) : "NEW, OK TO INSTALL").ok(() -> {
                final Context app = getApplicationContext();
                work.execute(() -> {
                    final PackZip.Result r = PackZip.extract(z, dir);
                    h.post(() -> {
                        if (destroyed) return;
                        zipStatus = r.ok ? "INSTALLED " + r.name.toUpperCase(Locale.ROOT) + " (" + r.files + " FILES)" : z.getName().toUpperCase(Locale.ROOT) + ": " + r.error;
                        if (r.ok) {
                            packName = r.name;
                            prefs.edit().putString("soundPack", packName).apply();
                            sounds.loadPack(packName, true);
                            toast("Installed " + r.name);
                        } else {
                            fx(SoundMap.ERROR);
                            toast("Install failed: " + r.error);
                        }
                        if (app != null && panel == p && !p.closing) openInstallPack();
                    });
                });
            }));
        }
        p.add(new Panel.Row("BUILT-IN").info(packName.isEmpty() ? "ACTIVE" : "OK TO USE").ok(() -> usePack("")));
        for (final String pk : sounds.listPacks()) {
            p.add(new Panel.Row("PACK  " + pk).info(pk.equals(packName) ? "ACTIVE  " + sounds.status : "OK TO USE").ok(() -> usePack(pk)));
        }
        if (zs.length == 0) p.add(new Panel.Row("NO ZIPS YET").two("PUT A .ZIP IN Android/data/" + getPackageName() + "/files/soundpacks"));
        p.add(new Panel.Row("FROM A PHONE (TERMUX)").two("bash install-soundpack.sh TVIP:PORT mypack.zip"));
        p.add(new Panel.Row("LAST RESULT").two(zipStatus));
        openPanel(p);
    }

    private void usePack(String name) {
        packName = name;
        prefs.edit().putString("soundPack", packName).apply();
        sounds.loadPack(packName, true);
        if (panel != null && "install".equals(panel.kind)) openInstallPack();
    }

    private void openControllerReport() {
        final Panel p = new Panel(this, "report", "Controller report");
        p.onBack = () -> {
            if (report != null) { report.close(); report = null; }
            applyLight();
            openSettings(T_PAD, "CONTROLLER REPORT");
        };
        if (Build.VERSION.SDK_INT < 31) {
            p.add(new Panel.Row("NEEDS ANDROID 12").info("THIS BOX IS OLDER"));
            openPanel(p);
            return;
        }
        if (padId >= 0 && pad != null && pad.deviceId < 0) {
            try { pad.attach(InputDevice.getDevice(padId)); } catch (Throwable ignored) {}
        }
        if (report != null) report.close();
        final PadReport r = PadReport.build(padId, psSeen, appVersion());
        report = r;
        final Panel.Row saved = new Panel.Row("SAVED TO").two("WRITING...");
        p.add(saved);
        boolean rgb = r.rgb != null;
        final Panel.Row result = new Panel.Row("LAST TEST").two(rgb ? "PICK A TEST COLOR" : "LIGHT BAR: NOT EXPOSED BY THIS BOX (COMING SOON)");
        String[] names = {"TEST RED", "TEST GREEN", "TEST BLUE", "TEST OFF"};
        int[] colors = {0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0};
        for (int i = 0; i < 4; i++) {
            final int col = colors[i];
            p.add(new Panel.Row(names[i]).disabled(!rgb).ok(() -> {
                if (pad != null) pad.releaseLight();
                result.info = r.testLight(col);
                p.invalidate();
            }));
        }
        p.add(result);
        for (PadReport.Line l : r.lines) p.add(new Panel.Row(l.title).two(l.body));
        p.add(new Panel.Row("WRITE REPORT AGAIN").ok(this::openControllerReport));
        openPanel(p);
        final String text = r.text();
        final Context app = getApplicationContext();
        try {
            work.execute(() -> {
                final String where = PadReport.write(app, text);
                h.post(() -> { saved.info = where + "  (adb pull it)"; p.invalidate(); });
            });
        } catch (Throwable ignored) {}
    }

    private void openChangeIcon(final AppStore.App a, final Runnable back) {
        final String pkg = a.cn.getPackageName();
        final Panel p = new Panel(this, "icon", "Change icon: " + a.label);
        p.onBack = back;
        File f = IconPacks.pick(iconsDir, iconPack, pkg);
        String where = "Android/data/" + getPackageName() + "/files/icons/";
        p.add(new Panel.Row("PACKAGE").two(pkg + "  (NAME YOUR FILE " + pkg + ".png)"));
        if (f != null && iconsDir != null && !iconsDir.equals(f.getParentFile())) where = where + f.getParentFile().getName() + "/";
        if (f == null) {
            p.add(new Panel.Row("MATCHING FILE").two("NONE FOUND. LOOKED FOR " + pkg + ".png AND .webp" + (iconPack.isEmpty() ? "" : " IN THE ICONS FOLDER AND PACK " + iconPack.toUpperCase(Locale.ROOT))));
            p.add(new Panel.Row("HOW TO ADD ONE").two("adb push myicon.png /sdcard/" + where + pkg + ".png"));
        } else {
            String dims = "";
            try {
                android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                android.graphics.BitmapFactory.decodeFile(f.getPath(), o);
                dims = "  " + o.outWidth + "x" + o.outHeight + (IconPick.wide(o.outWidth, o.outHeight) ? "  WIDE, SHOWN AS BANNER" : "  SHOWN ON THE ICON PLATE");
            } catch (Throwable ignored) {}
            p.add(new Panel.Row("MATCHING FILE").two(where + f.getName() + dims));
            final HashSet<String> off = new HashSet<>(OrderList.splitSet(prefs.getString("iconOff", "")));
            final Panel.Row use = p.add(new Panel.Row("USE CUSTOM ICON").values(off.contains(pkg) ? 0 : 1, "OFF", "ON"));
            use.change(() -> {
                if (use.sel == 1) off.remove(pkg); else off.add(pkg);
                prefs.edit().putString("iconOff", OrderList.join(off)).apply();
                reloadIcons();
            });
        }
        IconCache.Icon cur = icons.get(a.key);
        p.add(new Panel.Row("NOW SHOWING").info(cur == null ? "NOT LOADED" : cur.source));
        p.add(new Panel.Row("RELOAD ICONS").ok(() -> { reloadIcons(); openChangeIcon(a, back); }));
        openFlyout(p, a);
    }

    private String appVersion() {
        try { return "VISOR " + getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Throwable t) { return "VISOR"; }
    }

    private void openCustomColors() {
        final Panel p = new Panel(this, "custom", "Custom colors");
        p.onBack = () -> openSettings(T_LOOK, "CUSTOM COLORS");
        if (themeIdx != Ui.CUSTOM) {
            themeIdx = Ui.CUSTOM;
            prefs.edit().putInt("theme", themeIdx).apply();
            applyTheme();
        }
        final String[] labels = {"PRIMARY HUE", "PRIMARY SATURATION", "PRIMARY BRIGHTNESS",
                "SECONDARY HUE", "SECONDARY SATURATION", "SECONDARY BRIGHTNESS"};
        final Panel.Row[] rows = new Panel.Row[6];
        for (int i = 0; i < 6; i++) {
            boolean hue = i % 3 == 0;
            rows[i] = p.add(new Panel.Row(labels[i]).slider(hue ? 0 : (i % 3 == 2 ? 30 : 0), hue ? 350 : 100, hue ? 10 : 5, custom[i]));
        }
        final Runnable sync = () -> {
            for (int i = 0; i < 6; i++) custom[i] = rows[i].val;
            int a = Ui.hsv(custom[0], custom[1] / 100f, custom[2] / 100f), b = Ui.hsv(custom[3], custom[4] / 100f, custom[5] / 100f);
            rows[0].swatchColor = a;
            rows[3].swatchColor = b;
            saveCustom();
            prefs.edit().remove(PREF_CTHM).apply();   // the two colour mixer takes over again
            rebuildCustomTheme();
            applyTheme();
        };
        for (int i = 0; i < 6; i++) rows[i].change(sync);
        rows[0].swatchColor = Ui.THEMES[Ui.CUSTOM].a;
        rows[3].swatchColor = Ui.THEMES[Ui.CUSTOM].b;
        final Panel.Row pr = p.add(new Panel.Row("PRESET").values(0, PRESET_NAMES));
        pr.change(() -> {
            int[] v = PRESETS[pr.sel];
            if (v == null) return;
            for (int i = 0; i < 6; i++) rows[i].val = Math.max(rows[i].min, Math.min(rows[i].max, v[i]));
            sync.run();
            p.invalidate();
        });
        p.add(new Panel.Row("DONE").ok(() -> openSettings(T_LOOK, "THEME")));
        openPanel(p);
    }

    // ------------------------------------------------------------------ 2.17 theme editor

    private File themesDir() {
        try {
            File ext = getExternalFilesDir(null);
            return ext == null ? null : new File(ext, "themes");
        } catch (Throwable t) {
            return null;
        }
    }

    /** The CUSTOM theme as data: the editor's saved one, or the colours the CUSTOM theme has now (migration). */
    private ThemeSpec currentCustomSpec() {
        ThemeSpec spec = ThemeSpec.parse(prefs.getString(PREF_CTHM, ""));
        if (spec != null) return spec;
        Ui.Theme c = Ui.THEMES[Ui.CUSTOM];
        return ThemeSpec.full("CUSTOM", c.a, c.b, c.bg0, c.bg1, c.panel, c.text, c.dim);
    }

    private static int specColor(ThemeSpec s, int which) {
        switch (which) {
            case 0: return s.a;
            case 1: return s.b;
            case 2: return s.bg0;
            case 3: return s.panel;
            default: return s.text;
        }
    }

    private static ThemeSpec withColor(ThemeSpec s, int which, int col) {
        int a = s.a, b = s.b, bg = s.bg0, pn = s.panel, tx = s.text;
        switch (which) {
            case 0: a = col; break;
            case 1: b = col; break;
            case 2: bg = col; break;
            case 3: pn = col; break;
            default: tx = col; break;
        }
        return ThemeSpec.of(s.name, a, b, bg, pn, tx);
    }

    private void saveSpec(ThemeSpec spec) {
        prefs.edit().putString(PREF_CTHM, spec.toJson()).putInt("theme", Ui.CUSTOM).apply();
        themeIdx = Ui.CUSTOM;
        Ui.THEMES[Ui.CUSTOM] = themeOf(spec);
        applyTheme();
    }

    /** Setup, LOOK, THEME EDITOR: five colours with hue, saturation and brightness, a readability check, and theme files. */
    private void openThemeEditor(String row) {
        final Panel p = new Panel(this, "themeed", "Theme editor");
        p.onBack = () -> openSettings(T_LOOK, "THEME EDITOR");
        final ThemeSpec[] cur = {currentCustomSpec()};
        if (themeIdx != Ui.CUSTOM) saveSpec(cur[0]);
        final Panel.Row which = p.add(new Panel.Row("COLOR").values(0, "ACCENT", "SECOND ACCENT", "BACKGROUND", "PANEL", "TEXT").swatch());
        final float[] hs = new float[3];
        android.graphics.Color.colorToHSV(specColor(cur[0], 0), hs);
        final Panel.Row hue = p.add(new Panel.Row("HUE").slider(0, 350, 10, Math.round(hs[0] / 10f) * 10 % 360));
        final Panel.Row sat = p.add(new Panel.Row("SATURATION").slider(0, 100, 5, Math.round(hs[1] * 100)));
        final Panel.Row val = p.add(new Panel.Row("BRIGHTNESS").slider(0, 100, 5, Math.round(hs[2] * 100)));
        final Panel.Row hex = p.add(new Panel.Row("HEX").info(ThemeSpec.hex(specColor(cur[0], 0))));
        final Panel.Row read = p.add(new Panel.Row("READABILITY").two("READABLE"));
        final Runnable refresh = () -> {
            int c = specColor(cur[0], which.sel);
            hex.info = ThemeSpec.hex(c);
            hex.swatchColor = c;
            which.swatchColor = c;
            String w = cur[0].warning();
            read.info = w == null ? "READABLE: THE TEXT, ACCENT AND PANELS HAVE ENOUGH CONTRAST." : "WARNING: " + w + ". YOU CAN KEEP IT, BUT IT MAY BE HARD TO READ.";
            p.invalidate();
        };
        which.change(() -> {
            float[] h2 = new float[3];
            android.graphics.Color.colorToHSV(specColor(cur[0], which.sel), h2);
            hue.val = Math.round(h2[0] / 10f) * 10 % 360;
            sat.val = Math.round(h2[1] * 100);
            val.val = Math.round(h2[2] * 100);
            refresh.run();
        });
        Runnable edit = () -> {
            int col = Ui.hsv(hue.val, sat.val / 100f, val.val / 100f);
            cur[0] = withColor(cur[0], which.sel, col);
            saveSpec(cur[0]);
            refresh.run();
        };
        hue.change(edit);
        sat.change(edit);
        val.change(edit);
        refresh.run();
        final File dir = themesDir();
        final java.util.List<ThemeSpec> saved = ThemeStore.list(dir);
        String[] names = saved.isEmpty() ? new String[]{"NONE FOUND"} : new String[saved.size()];
        for (int i = 0; i < saved.size(); i++) names[i] = saved.get(i).name;
        final Panel.Row pick = p.add(new Panel.Row("SAVED THEMES").values(0, names));
        p.add(new Panel.Row("LOAD THEME").ok(() -> {
            if (saved.isEmpty()) { toast("No theme files yet. Use SAVE THEME FILE, or install one."); return; }
            ThemeSpec t = saved.get(Math.min(pick.sel, saved.size() - 1));
            saveSpec(t);
            toast("Theme " + t.name + " loaded");
            openThemeEditor("LOAD THEME");
        }));
        final String free = ThemeStore.freeName(dir);
        final Panel.Row nm = p.add(new Panel.Row("THEME NAME").values(0, free, "MY THEME", "NEON", "MIDNIGHT", "SUNSET", "FOREST", "MONO"));
        p.add(new Panel.Row("SAVE THEME FILE").ok(() -> {
            ThemeSpec out = cur[0].copy();
            out.name = ThemeSpec.safeName(nm.values[nm.sel]);
            String err = ThemeStore.export(dir, out);
            toast(err == null ? "Saved " + out.name + ".json in the themes folder" : err);
            openThemeEditor("SAVE THEME FILE");
        }));
        p.add(new Panel.Row("RELOAD THEMES").ok(() -> {
            toast(ThemeStore.list(dir).size() + " theme files found");
            openThemeEditor("RELOAD THEMES");
        }));
        p.add(new Panel.Row("THEMES FOLDER").two("Android/data/" + getPackageName() + "/files/themes  (NAME.JSON, SEE docs/themes.md)"));
        p.add(new Panel.Row("RESET THEME EDITOR").danger().confirm("PRESS OK AGAIN TO GO BACK TO THE TWO COLOR MIXER").ok(() -> {
            prefs.edit().remove(PREF_CTHM).apply();
            rebuildCustomTheme();
            applyTheme();
            toast("The CUSTOM theme is back to CUSTOM COLORS");
            openThemeEditor("RESET THEME EDITOR");
        }));
        p.selectLabel(0, row);
        openPanel(p);
    }

    private void openLightColor() {
        final Panel p = new Panel(this, "light", "Light bar color");
        p.onBack = () -> openSettings(T_PAD, "LIGHT COLOR");
        if (lightMode != 2) {
            lightMode = 2;
            prefs.edit().putInt("lightMode", 2).apply();
        }
        final String[] labels = {"HUE", "SATURATION", "BRIGHTNESS"};
        final Panel.Row[] rows = new Panel.Row[3];
        for (int i = 0; i < 3; i++) rows[i] = p.add(new Panel.Row(labels[i]).slider(0, i == 0 ? 350 : 100, i == 0 ? 10 : 5, lightHsv[i]));
        final Runnable sync = () -> {
            for (int i = 0; i < 3; i++) lightHsv[i] = rows[i].val;
            rows[0].swatchColor = Ui.hsv(lightHsv[0], lightHsv[1] / 100f, lightHsv[2] / 100f);
            prefs.edit().putInt("lH", lightHsv[0]).putInt("lS", lightHsv[1]).putInt("lV", lightHsv[2]).apply();
            applyLight();
        };
        for (Panel.Row r : rows) r.change(sync);
        rows[0].swatchColor = Ui.hsv(lightHsv[0], lightHsv[1] / 100f, lightHsv[2] / 100f);
        final Panel.Row pr = p.add(new Panel.Row("PRESET").values(0, PRESET_NAMES));
        pr.change(() -> {
            int[] v = PRESETS[pr.sel];
            if (v == null) return;
            rows[0].val = v[0]; rows[1].val = v[1]; rows[2].val = v[2];
            sync.run();
            p.invalidate();
        });
        p.add(new Panel.Row("USE THEME CUSTOM PRIMARY").ok(() -> {
            rows[0].val = custom[0]; rows[1].val = custom[1]; rows[2].val = custom[2];
            sync.run();
            p.invalidate();
        }));
        p.add(new Panel.Row("LIGHT STATUS").info(padStatus(true)));
        applyLight();
        openPanel(p);
    }

    private void openTests(int tab) {
        final Panel p = new Panel(this, "test", "Test sounds and haptics");
        p.onBack = () -> openSettings(tab == 0 ? T_SOUND : T_PAD, tab == 0 ? "TEST SOUNDS" : "TEST HAPTICS");
        p.tab("SOUNDS");
        for (int i = 0; i < SoundMap.EVENTS.length; i++) {
            final int ev = i;
            p.add(new Panel.Row(SoundMap.EVENTS[i]).info(sounds.fileFor(i)).ok(() -> {
                boolean m = sounds.mute;
                sounds.mute = false;
                sounds.play(ev);
                sounds.mute = m;
            }));
        }
        p.tab("HAPTICS");
        final Panel.Row st = new Panel.Row("STATUS").info(padStatus(false));
        p.add(st);
        for (int i = 0; i < HapticPatterns.NAMES.length; i++) {
            final int pat = i;
            p.add(new Panel.Row(HapticPatterns.NAMES[i]).ok(() -> testHaptic(pat, st, p)));
        }
        p.add(new Panel.Row("wheel_hum (1.5 s demo)").ok(() -> {
            if (testHaptic(-1, st, p)) humDemo(0);
        }));
        p.select(tab, tab == 1 ? 1 : 0);
        openPanel(p);
    }

    /** Plays one pattern on demand, even in remote mode, if a controller is known. */
    private boolean testHaptic(int pat, Panel.Row st, Panel p) {
        st.info = padStatus(false);
        p.invalidate();
        if (pad == null || !pad.hasVibrators()) return false;
        if (rumble <= 0) { st.info = "RUMBLE STRENGTH IS OFF"; p.invalidate(); return false; }
        if (pat >= 0) pad.play(pat, feel, rumble, 0);
        return true;
    }

    private void humDemo(final int step) {
        if (step > 22 || destroyed || pad == null) return;
        long now = SystemClock.uptimeMillis();
        if (step < 18) {
            hum.onTick(now);
            pad.play(step % 5 == 0 ? HapticPatterns.DETENT_HEAVY : HapticPatterns.TICK, feel, rumble, hum.current() * rumble / 100);
        }
        engine.kick();
        h.postDelayed(() -> humDemo(step + 1), 75);
    }

    private String appStatus(AppStore.App a) {
        boolean onWheel = store.find(a.key) != null, hidden = store.hidden.contains(a.key), pinned = store.pinned.contains(a.key);
        String s = !onWheel ? "NOT ON WHEEL" : (hidden ? "HIDDEN" : "ON WHEEL");
        return pinned ? s + " + DOCK" : s;
    }

    private void openAddApp() {
        final Panel p = new Panel(this, "addapp", "Add app");
        p.onBack = () -> openSettings(T_APPS, "ADD APP");
        if (scanCache != null) fillAddApp(p, scanCache);
        else p.add(new Panel.Row("SCANNING INSTALLED APPS").info("PLEASE WAIT"));
        openPanel(p);
        final Context app = getApplicationContext();
        try {
            work.execute(() -> {
                ArrayList<AppStore.App> l = null;
                try { l = AppStore.scanAll(app); } catch (Throwable ignored) {}
                final ArrayList<AppStore.App> res = l;
                h.post(() -> {
                    if (destroyed || res == null) return;
                    scanCache = res;
                    if (panel == p && !p.closing) {
                        fillAddApp(p, res);
                        focusChanged(true);
                    }
                });
            });
        } catch (Throwable ignored) {}
    }

    private void fillAddApp(Panel p, ArrayList<AppStore.App> apps) {
        ArrayList<Panel.Row> rows = new ArrayList<>();
        for (final AppStore.App a : apps) rows.add(new Panel.Row(a.label).info(appStatus(a)).sub().ok(() -> openAppActions(a)));
        if (rows.isEmpty()) rows.add(new Panel.Row("NO LAUNCHABLE APPS FOUND"));
        p.setRows(0, rows);
        engine.kick();   // 2.4.1: the new rows type in, so the frame loop must be awake
    }

    private void openAppActions(final AppStore.App a) {
        final Panel p = new Panel(this, "appact", a.label);
        p.onBack = this::openAddApp;
        final String pkg = a.cn.getPackageName();
        AppStore.App known = store.find(a.key);
        if (known == null) {
            p.add(new Panel.Row("ADD TO WHEEL").ok(() -> {
                store.addExtra(a);
                rebuildLists(focusedKey());
                toast(a.label + " added");
                openAppActions(a);
            }));
        } else {
            p.add(new Panel.Row("ON THE WHEEL").info(store.hidden.contains(a.key) ? "HIDDEN" : "VISIBLE"));
            if (store.hidden.contains(a.key)) {
                p.add(new Panel.Row("UNHIDE").ok(() -> {
                    store.setHidden(a.key, false);
                    rebuildLists(focusedKey());
                    openAppActions(a);
                }));
            }
        }
        boolean pinned = store.pinned.contains(a.key);
        p.add(new Panel.Row(pinned ? "REMOVE FROM DOCK" : "PIN TO DOCK").ok(() -> {
            if (store.pinned.contains(a.key)) {
                store.setPinned(a.key, false);
            } else {
                if (store.find(a.key) == null) store.addExtra(a);
                if (!store.setPinned(a.key, true)) { fx(SoundMap.ERROR); toast("Dock is full (" + AppStore.MAX_DOCK_APPS + " apps)"); return; }
            }
            rebuildLists(focusedKey());
            openAppActions(a);
        }));
        if (store.extras.contains(pkg)) {
            p.add(new Panel.Row("REMOVE FROM WHEEL").ok(() -> {
                store.removeExtra(pkg);
                store.setPinned(a.key, false);
                refresh(true);
                openAppActions(a);
            }));
        }
        p.add(new Panel.Row("OPEN NOW").ok(() -> launch(a, null)));
        openPanel(p);
    }

    private String focusedKey() {
        AppStore.App f = mainIdx < list.size() ? list.get(mainIdx) : null;
        return f == null ? null : f.key;
    }

    private void openTileMenu(final AppStore.App a) {
        final Panel p = new Panel(this, "tile", a.label);
        p.add(new Panel.Row("MOVE LEFT").ok(() -> moveApp(a, -1)));
        p.add(new Panel.Row("MOVE RIGHT").ok(() -> moveApp(a, 1)));
        final Panel.Row pin = new Panel.Row(store.pinned.contains(a.key) ? "REMOVE FROM DOCK" : "PIN TO DOCK");
        pin.ok(() -> {
            boolean now = !store.pinned.contains(a.key);
            if (!store.setPinned(a.key, now)) { fx(SoundMap.ERROR); toast("Dock is full (" + AppStore.MAX_DOCK_APPS + " apps)"); return; }
            pin.label = now ? "REMOVE FROM DOCK" : "PIN TO DOCK";
            p.invalidate();
            rebuildLists(a.key);
        });
        p.add(pin);
        final Panel.Row hide = new Panel.Row(store.hidden.contains(a.key) ? "UNHIDE" : "HIDE");
        hide.ok(() -> {
            boolean now = !store.hidden.contains(a.key);
            store.setHidden(a.key, now);
            if (now && !showHidden) {
                closePanel();
                rebuildLists(null);
                toast("Hidden. Turn on Show hidden apps in setup to bring it back.");
            } else {
                hide.label = now ? "UNHIDE" : "HIDE";
                p.invalidate();
                rebuildLists(a.key);
            }
        });
        p.add(hide);
        final String pkg = a.cn.getPackageName();
        addPsRow(p, pkg);
        if (store.extras.contains(pkg)) {
            p.add(new Panel.Row("REMOVE FROM WHEEL").ok(() -> {
                store.removeExtra(pkg);
                closePanel();
                refresh(true);
            }));
        }
        p.add(new Panel.Row("CHANGE ICON").sub().ok(() -> openChangeIcon(a, () -> openTileMenu(a))));
        p.add(new Panel.Row("APP INFO").ok(() -> startFirst(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", pkg, null)))));
        if (!a.system) {
            p.add(new Panel.Row("UNINSTALL").danger().ok(() -> {
                closePanel();
                startFirst(new Intent(Intent.ACTION_DELETE, Uri.fromParts("package", pkg, null)),
                        new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null)));
            }));
        }
        openFlyout(p, a);
    }

    private void openDockMenu(final AppStore.App a) {
        final Panel p = new Panel(this, "dock", a.label);
        Chip cur = dockIdx < dock.size() ? dock.get(dockIdx) : null;
        if (cur != null && cur.compact && cur.id.startsWith("recent:")) {
            p.add(new Panel.Row("PIN TO DOCK").ok(() -> {
                if (!store.setPinned(a.key, true)) { fx(SoundMap.ERROR); toast("Dock is full (" + AppStore.MAX_DOCK_APPS + " apps)"); return; }
                closePanel();
                rebuildLists(focusedKey());
            }));
            p.add(new Panel.Row("REMOVE FROM RECENTS").ok(() -> {
                recents.remove(a.key);
                prefs.edit().putString("recents", recents.save()).apply();
                closePanel();
                refreshDock();
            }));
            addPsRow(p, a.cn.getPackageName());
            p.add(new Panel.Row("CHANGE ICON").sub().ok(() -> openChangeIcon(a, () -> openDockMenu(a))));
            p.add(new Panel.Row("APP INFO").ok(() -> startFirst(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", a.cn.getPackageName(), null)))));
            openFlyout(p, a);
            return;
        }
        p.add(new Panel.Row("MOVE LEFT").ok(() -> moveDock(a, -1)));
        p.add(new Panel.Row("MOVE RIGHT").ok(() -> moveDock(a, 1)));
        p.add(new Panel.Row("REMOVE FROM DOCK").ok(() -> {
            store.setPinned(a.key, false);
            closePanel();
            dockIdx = Math.max(0, dockIdx - 1);
            rebuildLists(focusedKey());
        }));
        addPsRow(p, a.cn.getPackageName());
        p.add(new Panel.Row("CHANGE ICON").sub().ok(() -> openChangeIcon(a, () -> openDockMenu(a))));
        p.add(new Panel.Row("APP INFO").ok(() -> startFirst(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", a.cn.getPackageName(), null)))));
        openFlyout(p, a);
    }

    // ------------------------------------------------------------------ 2.4 PS overlay

    private PsRules psRules() {
        return PsRules.load(prefs.getString(PsRules.KEY_PASS, ""), prefs.getString(PsRules.KEY_DECK, ""), prefs.getBoolean(PsRules.KEY_GAMES, true));
    }

    private void savePsRules(PsRules r) {
        prefs.edit().putString(PsRules.KEY_PASS, r.passText()).putString(PsRules.KEY_DECK, r.deckText()).apply();
    }

    /** App options: who gets the PS button while this app is in front. */
    private void addPsRow(Panel p, final String pkg) {
        final boolean game = PsService.isGame(getPackageManager(), pkg);
        final Panel.Row ps = p.add(new Panel.Row("PS BUTTON").values(psRules().keeps(pkg, game) ? 1 : 0, "VISOR", "THE APP"));
        ps.change(() -> {
            PsRules r = psRules();
            r.set(pkg, game, ps.sel == 1);
            savePsRules(r);
            toast(ps.sel == 1 ? "This app keeps the PS button" : "The PS button opens Visor's menu over this app");
        });
    }

    private String a11yList() {
        try { return Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES); } catch (Throwable t) { return null; }
    }

    private boolean a11yOn() {
        try { return Settings.Secure.getInt(getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1; } catch (Throwable t) { return false; }
    }

    private int psStatus() { return PsSetup.status(PsSetup.listed(a11yList()), a11yOn(), PsService.live != null); }

    /** Setup, PAD: status, evidence, switches and the two sub screens. */
    private void addPsOverlayRows(final Panel p) {
        String list = a11yList();
        boolean listed = PsSetup.listed(list), on = a11yOn();
        int st = psStatus();
        boolean seenSvc = prefs.getBoolean(PsService.PREF_SEEN, false);
        p.add(new Panel.Row("PS OVERLAY: " + PsSetup.STATUS[st]).two(PsSetup.statusLine(st, listed, on)));
        p.add(new Panel.Row("PS SEEN BY THE OVERLAY SERVICE").info(st != PsSetup.ON && !seenSvc ? "SERVICE NOT RUNNING" : (seenSvc ? "YES" : "NO, PRESS IT ON THE HOME SCREEN")));
        p.add(new Panel.Row("DOES IT WORK ON THIS BOX").two(PsSetup.verdict(st, psSeen, seenSvc)));
        String l = prefs.getString(PsService.PREF_LAUNCH, "");
        p.add(new Panel.Row("OPENING APPS FROM THE OVERLAY").info("ok".equals(l) ? "WORKS" : ("blocked".equals(l) ? "BLOCKED BY ANDROID" : "NOT TRIED YET")));
        p.add(new Panel.Row("SHOW ENABLE COMMANDS").sub().ok(this::openPsCommands));
        p.add(new Panel.Row("TEST PS OVERLAY").ok(() -> {
            PsService s = PsService.live;
            if (s == null) { fx(SoundMap.ERROR); toast("The PS overlay service is not running. See SHOW ENABLE COMMANDS."); return; }
            if (!s.openTest()) { fx(SoundMap.ERROR); toast("The overlay window could not open"); }
        }));
        final Panel.Row cap = p.add(new Panel.Row("OVERLAY CAPTURE").values(prefs.getBoolean(PsService.PREF_CAPTURE, true) ? 1 : 0, "OFF", "ON"));
        cap.change(() -> prefs.edit().putBoolean(PsService.PREF_CAPTURE, cap.sel == 1).apply());
        final Panel.Row gd = p.add(new Panel.Row("GAMES KEEP THE PS BUTTON").values(prefs.getBoolean(PsRules.KEY_GAMES, true) ? 1 : 0, "OFF", "ON"));
        gd.change(() -> prefs.edit().putBoolean(PsRules.KEY_GAMES, gd.sel == 1).apply());
        p.add(new Panel.Row("PS BUTTON APPS").sub().ok(() -> openPsApps(0)));
    }

    private void openPsCommands() {
        final Panel p = new Panel(this, "pscmds", "PS overlay: adb commands");
        p.onBack = () -> openSettings(T_PAD, "SHOW ENABLE COMMANDS");
        String list = a11yList();
        int st = psStatus();
        p.add(new Panel.Row("NOW").two("PS OVERLAY: " + PsSetup.statusLine(st, PsSetup.listed(list), a11yOn())));
        int others = PsSetup.parse(PsSetup.withoutDeck(list)).size();
        p.add(new Panel.Row("OTHER ACCESSIBILITY SERVICES ON").info(list == null ? "UNKNOWN" : String.valueOf(others)));
        p.add(new Panel.Row("EASIEST: FROM TERMUX ON YOUR PHONE").two("bash enable-ps-overlay.sh TVIP:PORT on"));
        if (others == 0) {
            p.add(new Panel.Row("OR 1. ADD VISOR'S SERVICE").two("adb shell settings put secure enabled_accessibility_services " + PsSetup.SHORT));
            p.add(new Panel.Row("2. TURN ACCESSIBILITY ON").two("adb shell settings put secure accessibility_enabled 1"));
            p.add(new Panel.Row("TURN IT OFF").two("bash enable-ps-overlay.sh TVIP:PORT off"));
            p.add(new Panel.Row("OR BY HAND (VISOR WAS THE ONLY ONE)").two("adb shell settings delete secure enabled_accessibility_services"));
        } else {
            p.add(new Panel.Row("BY HAND: OTHER SERVICES ARE ON, KEEP THEM").two("adb shell settings get secure enabled_accessibility_services"));
            p.add(new Panel.Row("THEN PUT THAT LIST BACK WITH THIS ADDED AT THE END").two(":" + PsSetup.SHORT));
            p.add(new Panel.Row("AND").two("adb shell settings put secure accessibility_enabled 1"));
            p.add(new Panel.Row("TURN IT OFF (KEEPS THE OTHERS)").two("bash enable-ps-overlay.sh TVIP:PORT off"));
        }
        p.add(new Panel.Row("WHY ADB").two("ANDROID 13 AND NEWER BLOCK SIDELOADED ACCESSIBILITY SERVICES IN SETTINGS. ADB IS ALLOWED."));
        p.add(new Panel.Row("STUCK IN AN APP").two("HOLD PS FOR 4 SECONDS: VISOR LETS GO UNTIL YOU LEAVE THAT APP. OR SET OVERLAY CAPTURE OFF."));
        p.add(new Panel.Row("IF THE PS BUTTON NEVER ARRIVES").two("THE BOX EATS IT BEFORE ANY APP. THEN THE OVERLAY CANNOT WORK HERE. TURN IT OFF."));
        p.add(new Panel.Row("AFTER A GOOGLE TV UPDATE").two("IF THE MENU STOPS OPENING, RUN THE ENABLE COMMAND AGAIN."));
        openPanel(p);
    }

    static final String[] PS_FILTERS = {"ALL", "GAMES", "CHANGED"};

    private static final class PsApp {
        final AppStore.App app;
        final boolean game;
        PsApp(AppStore.App app, boolean game) { this.app = app; this.game = game; }
    }

    private ArrayList<PsApp> psApps;

    /** Setup, PAD, PS BUTTON APPS: every launchable app with its switch, filter ALL, GAMES or CHANGED. */
    private void openPsApps(final int filter) {
        final Panel p = new Panel(this, "psapps", "PS button apps");
        p.onBack = () -> openSettings(T_PAD, "PS BUTTON APPS");
        if (psApps != null) fillPsApps(p, filter);
        else p.add(new Panel.Row("SCANNING INSTALLED APPS").info("PLEASE WAIT"));
        openPanel(p);
        if (psApps != null) return;
        final Context app = getApplicationContext();
        try {
            work.execute(() -> {
                ArrayList<PsApp> out = new ArrayList<>();
                try {
                    PackageManager pm = app.getPackageManager();
                    HashSet<String> seen = new HashSet<>();
                    for (AppStore.App a : AppStore.scanAll(app)) {
                        String pkg = a.cn.getPackageName();
                        if (pkg.equals(app.getPackageName()) || !seen.add(pkg)) continue;
                        out.add(new PsApp(a, PsService.isGame(pm, pkg)));
                    }
                } catch (Throwable ignored) {}
                h.post(() -> {
                    if (destroyed) return;
                    psApps = out;
                    if (panel == p && !p.closing) { fillPsApps(p, filter); focusChanged(true); }
                });
            });
        } catch (Throwable ignored) {}
    }

    private void fillPsApps(final Panel p, final int filter) {
        final PsRules r = psRules();
        ArrayList<Panel.Row> rows = new ArrayList<>();
        final Panel.Row f = new Panel.Row("SHOW").values(filter, PS_FILTERS);
        f.change(() -> fillPsApps(p, f.sel));
        rows.add(f);
        int n = 0;
        for (final PsApp pa : psApps) {
            final String pkg = pa.app.cn.getPackageName();
            if (filter == 1 && !pa.game) continue;
            if (filter == 2 && !r.changed(pkg, pa.game)) continue;
            final Panel.Row row = new Panel.Row(psLabel(pa, r)).values(r.keeps(pkg, pa.game) ? 1 : 0, "VISOR", "THE APP");
            row.change(() -> {
                PsRules now = psRules();
                now.set(pkg, pa.game, row.sel == 1);
                savePsRules(now);
                row.label = psLabel(pa, now);
                p.invalidate();
            });
            rows.add(row);
            n++;
        }
        if (n == 0) rows.add(new Panel.Row(filter == 2 ? "NOTHING CHANGED YET" : (filter == 1 ? "NO APP IS MARKED AS A GAME" : "NO LAUNCHABLE APPS FOUND")));
        rows.add(new Panel.Row("DEFAULT").two(prefs.getBoolean(PsRules.KEY_GAMES, true)
                ? "GAMES KEEP THE PS BUTTON, EVERYTHING ELSE OPENS VISOR'S MENU. VISOR ALWAYS TAKES IT ON ITS OWN SCREEN."
                : "EVERY APP OPENS VISOR'S MENU UNLESS YOU SET IT TO THE APP. VISOR ALWAYS TAKES IT ON ITS OWN SCREEN."));
        p.setRows(0, rows);
        engine.kick();   // 2.4.1: same here
    }

    private static String psLabel(PsApp pa, PsRules r) {
        String s = pa.app.label.toUpperCase(Locale.ROOT);
        if (pa.game) s += "  // GAME";
        if (r.changed(pa.app.cn.getPackageName(), pa.game)) s += "  // CHANGED";
        return s;
    }


    // ------------------------------------------------------------------ 2.6 button remap

    private void remapInit() {
        remapGate = new RemapGate(getPackageName());
        comboGate = new ComboGate(getPackageName());
        reloadRemap();
        remapListener = (sp, key) -> { if (RemapPrefs.watches(key)) reloadRemap(); };
        prefs.registerOnSharedPreferenceChangeListener(remapListener);
    }

    private void reloadRemap() {
        RemapGate g = remapGate;
        if (g == null) return;
        g.table = RemapPrefs.liveTable(prefs);
        g.flags = RemapPrefs.flags(prefs);
        ComboGate cg = comboGate;
        if (cg != null) {
            cg.table = RemapPrefs.liveCombos(prefs);
            cg.flags = RemapPrefs.flags(prefs);
        }
    }

    /** reset_remap runs before anything can fail, so it works in safe mode and with a broken D-pad. */
    private boolean earlyCmd(Intent intent) {
        if (intent == null) return false;
        String v;
        try {
            v = intent.getStringExtra(DeckCmd.EXTRA);
        } catch (Throwable t) {
            return false;
        }
        int early = DeckCmd.parse(v);
        if (early == DeckCmd.RESTORE_DISPLAY) {
            // 2.8: puts the display back. Runs before anything can fail, so it works in safe mode too.
            try { intent.removeExtra(DeckCmd.EXTRA); } catch (Throwable ignored) {}
            String r;
            try { r = ProfileRunner.get(this).restoreNow(); } catch (Throwable t) { r = "RUN: " + DisplayState.DOC_RESTORE; }
            toast(r);
            return true;
        }
        if (early != DeckCmd.RESET_REMAP) return false;
        try { intent.removeExtra(DeckCmd.EXTRA); } catch (Throwable ignored) {}
        int n;
        try {
            n = RemapPrefs.resetAll(prefs);
        } catch (Throwable t) {
            return true;
        }
        reloadRemap();
        toast(n == 0 ? "No button remaps were set. The advanced switches are off." : "All button remaps reset (" + n + ")");
        return true;
    }

    /** The decision for one key while Deck is in front. @return 0 pass, 1 taken, 2 swapped (remapSwapped is the key to use). */
    private int remapDeck(KeyEvent e) {
        RemapGate g = remapGate;
        if (g == null) return 0;
        int act = e.getAction();
        if (act != KeyEvent.ACTION_DOWN && act != KeyEvent.ACTION_UP) return 0;
        if (g.table.size() == 0 && !g.busy()) return 0;
        int code = e.getKeyCode();
        long key;
        if (code == 0) {
            InputDevice d = e.getDevice();
            key = RemapKey.pack(0, e.getScanCode(), d == null ? 0 : d.getVendorId(), d == null ? 0 : d.getProductId());
        } else {
            key = RemapKey.pack(code, 0, 0, 0);
        }
        if (!RemapKey.valid(key)) return 0;
        long now = SystemClock.uptimeMillis();
        int r = g.onKey(key, act == KeyEvent.ACTION_DOWN, e.getRepeatCount(), now, getPackageName());
        armRemapTimer(now);
        switch (r) {
            case RemapGate.SWALLOW:
                return 1;
            case RemapGate.ACT: {
                final int a = g.act;
                final String arg = g.arg;
                h.post(() -> runRemapAction(a, arg));
                return 1;
            }
            case RemapGate.SWAP:
                remapSwapped = new KeyEvent(e.getDownTime(), e.getEventTime(), act, g.swapCode, e.getRepeatCount(), e.getMetaState(),
                        e.getDeviceId(), e.getScanCode(), e.getFlags(), e.getSource());
                return 2;
            default:
                return 0;
        }
    }

    /** 2.9: a combo completed by this key (the second of the pair). @return true if the key is taken. */
    private boolean comboDeck(KeyEvent e) {
        ComboGate g = comboGate;
        if (g == null || g.table.size() == 0) return false;
        int act = e.getAction();
        if (act != KeyEvent.ACTION_DOWN && act != KeyEvent.ACTION_UP) return false;
        long key = ComboLearnView.keyOf(e);
        if (!RemapKey.valid(key)) return false;
        int r = g.onKey(key, act == KeyEvent.ACTION_DOWN, e.getRepeatCount(), SystemClock.uptimeMillis(), getPackageName());
        if (r == ComboGate.ACT) {
            final int a = g.act;
            final String arg = g.arg;
            h.post(() -> runRemapAction(a, arg));
            return true;
        }
        return r == ComboGate.SWALLOW;
    }

    private void armRemapTimer(long now) {
        RemapGate g = remapGate;
        h.removeCallbacks(remapTick);
        long d = g == null ? -1 : g.nextDelay(now);
        if (d >= 0) h.postDelayed(remapTick, d + 5);
    }

    /** Long presses and the Back escape wait on this timer. */
    private final Runnable remapTick = new Runnable() {
        @Override public void run() {
            RemapGate g = remapGate;
            if (g == null || destroyed) return;
            try {
                long now = SystemClock.uptimeMillis();
                int r;
                while ((r = g.poll(now, getPackageName())) != RemapGate.NONE) {
                    if (r == RemapGate.ESCAPE) remapEscape(); else runRemapAction(g.act, g.arg);
                }
                armRemapTimer(now);
            } catch (Throwable t) {
                g.reset();
            }
        }
    };

    /** Back held for 6 seconds: the navigation remaps are gone. */
    private void remapEscape() {
        boolean had = RemapPrefs.escape(prefs);
        reloadRemap();
        if (had) { fx(SoundMap.TOGGLE); toast("Navigation remaps cleared"); }
    }

    private boolean hasInputs() { return store != null && !store.inputs.isEmpty(); }

    /** Runs a remap action while Deck is in front. */
    private void runRemapAction(int a, String arg) {
        if (destroyed || root == null) return;
        boolean ok = true;
        try {
            switch (a) {
                case RemapAction.HOME: case RemapAction.DECK_HOME:
                    goTopLevel();
                    break;
                case RemapAction.BACK:
                    back();
                    break;
                case RemapAction.DECK_PS:
                    if (test != null) removeTest();
                    if (quick != null) closeQuick(true); else openQuick();
                    break;
                case RemapAction.DECK_SETUP:
                    if (test != null) removeTest();
                    if (quick != null) closeQuick(false);
                    openSettings(T_LOOK, null);
                    break;
                case RemapAction.APP:
                    ok = RemapRunner.launch(this, arg);
                    break;
                case RemapAction.RECENTS: case RemapAction.QUICK: case RemapAction.POWER_MENU: case RemapAction.SCREENSHOT: {
                    PsService s = PsService.live;
                    if (s == null) { fx(SoundMap.ERROR); toast("Needs the overlay service. See PAD, SHOW ENABLE COMMANDS."); return; }
                    ok = s.runGlobal(a);
                    break;
                }
                case RemapAction.PREV_APP: {
                    String k = RemapAction.previousApp(Recents.pick(recents.list, Recents.MAX_KEEP, null, null), null, getPackageName());
                    if (k == null) { toast("No previous app yet. Visor remembers the apps you open with it."); return; }
                    ok = RemapRunner.launchComponent(this, k);
                    break;
                }
                case RemapAction.TOGGLE_PROFILE:
                    toast("Press it while the game is in front. Visor's profile switch works from the overlay service.");
                    break;
                case RemapAction.EXIT_GAME: {
                    String r = PerfRunner.exitGame(this, prefs, null);
                    toast(r == null ? "No game to exit. Press it while the game is in front, with the overlay service on." : "Closing the game");
                    break;
                }
                case RemapAction.PERF_TOGGLE:
                    PerfRunner.toggle(prefs);
                    toast(PerfRunner.mode(prefs) == PerfMode.OFF ? "Performance mode off" : "Performance mode on");
                    break;
                case RemapAction.PLAY_PAUSE: case RemapAction.NEXT: case RemapAction.PREV: case RemapAction.STOP:
                    ok = RemapRunner.media(this, a);
                    break;
                case RemapAction.VOL_UP: case RemapAction.VOL_DOWN: case RemapAction.MUTE:
                    ok = RemapRunner.volume(this, a);
                    break;
                case RemapAction.ANDROID_SETTINGS:
                    startFirst(new Intent(Settings.ACTION_SETTINGS));
                    break;
                case RemapAction.INPUT_PICKER:
                    openInputs();
                    break;
                default:
                    break;
            }
        } catch (Throwable t) {
            ok = false;
        }
        if (!ok) { fx(SoundMap.ERROR); toast("Could not run " + RemapAction.name(a)); }
    }

    // ---- Setup, REMAP tab

    private static boolean firstFive(long k) {
        for (int c : RemapKey.STANDARD_FIRST) if (RemapKey.pack(c, 0, 0, 0) == k) return true;
        return false;
    }

    private static String remapRowLabel(long key, RemapTable.Rule r) {
        if (RemapKey.known(key)) return RemapKey.name(key);
        return r != null && !r.label.isEmpty() ? r.label : RemapKey.name(key);
    }

    private void addRemapRow(Panel p, final long key, RemapTable t) {
        RemapTable.Rule r = t.get(key);
        p.add(new Panel.Row(remapRowLabel(key, r)).info(r == null ? "DEFAULT" : r.summary(this::appLabel)).sub().id(RemapKey.helpId(key))
                .ok(() -> openRemapKey(key)));
    }

    private void addRemapRows(final Panel p) {
        RemapTable t = RemapPrefs.table(prefs);
        for (int code : RemapKey.STANDARD_FIRST) addRemapRow(p, RemapKey.pack(code, 0, 0, 0), t);
        p.add(new Panel.Row("LEARN A BUTTON").sub().ok(this::showRemapLearn));
        p.add(new Panel.Row("COMBOS").info(comboSummary()).sub().ok(() -> openCombos(null)));
        for (int i = 0; i < t.size(); i++) {
            long k = t.keyAt(i);
            if (RemapKey.cls(k) != RemapKey.STANDARD || firstFive(k)) continue;
            addRemapRow(p, k, t);
        }
        p.add(new Panel.Row("REMAP OUTSIDE VISOR").info(psStatus() == PsSetup.ON ? "ON" : "OFF, NEEDS THE OVERLAY SERVICE"));
        final Panel.Row rg = p.add(new Panel.Row("REMAP IN GAMES").values(prefs.getBoolean(RemapTable.KEY_GAMES, false) ? 1 : 0, "OFF", "ON"));
        rg.change(() -> prefs.edit().putBoolean(RemapTable.KEY_GAMES, rg.sel == 1).apply());
        p.add(new Panel.Row("RESET ALL REMAPS").danger().confirm("PRESS OK AGAIN TO RESET ALL REMAPS").ok(() -> {
            int n = RemapPrefs.resetAll(prefs);
            reloadRemap();
            toast(n == 0 ? "Nothing was remapped. Advanced switches are off." : "All button remaps reset (" + n + ")");
            openSettings(T_REMAP, "RESET ALL REMAPS");
        }));
        p.add(new Panel.Row("ADVANCED REMAP").sub().ok(() -> showRemapWarning(false)));
    }

    private RemapTable.Rule remapRule(long key) {
        RemapTable.Rule r = RemapPrefs.table(prefs).get(key);
        RemapTable.Rule c = r == null ? new RemapTable.Rule() : r.copy();
        if (c.label.isEmpty()) c.label = RemapKey.name(key);
        return c;
    }

    private void saveRemap(long key, RemapTable.Rule r) {
        SharedPreferences.Editor ed = prefs.edit();
        if (!r.active() && (firstFive(key) || RemapKey.cls(key) != RemapKey.STANDARD)) RemapTable.clear(RemapPrefs.sink(ed), key);
        else RemapTable.save(RemapPrefs.sink(ed), key, r);
        ed.apply();
        reloadRemap();
    }

    private String actionText(int a, String arg, boolean isLong, RemapTable.Rule r) {
        if (a == RemapAction.DEFAULT) {
            if (isLong) return RemapAction.SAME_NAME;
            return r.longA != RemapAction.DEFAULT ? "DO NOTHING (A LONG PRESS IS SET)" : "DEFAULT";
        }
        return a == RemapAction.APP ? "LAUNCH " + RemapTable.Rule.shown(a, arg, this::appLabel) : RemapAction.name(a);
    }

    /** An app's name for the remap lists, or null if it is not installed. */
    private String appLabel(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Throwable t) {
            return null;
        }
    }

    private String whereText(long key) {
        int c = RemapKey.cls(key);
        boolean svc = PsService.live != null;
        String out = svc ? "VISOR AND OTHER APPS (SERVICE ON)" : "INSIDE VISOR ONLY (SERVICE OFF)";
        if (c == RemapKey.NAV) {
            out = prefs.getBoolean(RemapTable.KEY_NAV, false) ? "VISOR, AND OTHER APPS WHILE THE SYSTEM WIDE SWITCH IS ON" : "INSIDE VISOR ONLY (SYSTEM WIDE SWITCH IS OFF)";
        }
        return out + ". THE BOX MAY KEEP THE BUTTON. LEARN A BUTTON SHOWS IT.";
    }

    private void openRemapKey(final long key) {
        final RemapTable.Rule r = remapRule(key);
        final Panel p = new Panel(this, "remapkey", remapRowLabel(key, r));
        final boolean adv = RemapKey.cls(key) != RemapKey.STANDARD;
        p.onBack = () -> {
            if (adv) openRemapAdvanced(remapRowLabel(key, r)); else openSettings(T_REMAP, remapRowLabel(key, r));
        };
        p.add(new Panel.Row("SHORT PRESS").info(actionText(r.shortA, r.shortArg, false, r)).sub().ok(() -> openRemapActions(key, false)));
        p.add(new Panel.Row("LONG PRESS").info(actionText(r.longA, r.longArg, true, r)).sub().ok(() -> openRemapActions(key, true)));
        p.add(new Panel.Row("CLEAR THIS BUTTON").danger().confirm("PRESS OK AGAIN TO CLEAR IT").ok(() -> {
            RemapTable.Rule c = remapRule(key);
            c.shortA = RemapAction.DEFAULT; c.shortArg = ""; c.longA = RemapAction.DEFAULT; c.longArg = "";
            saveRemap(key, c);
            toast("Button back to default");
            openRemapKey(key);
        }));
        p.add(new Panel.Row("WHERE IT WORKS").two(whereText(key)));
        openPanel(p);
    }

    /** The action list for one press kind of one button. */
    private void openRemapActions(final long key, final boolean isLong) {
        final RemapTable.Rule r = remapRule(key);
        final Panel p = new Panel(this, "remapact", (isLong ? "Long press: " : "Short press: ") + remapRowLabel(key, r));
        p.onBack = () -> openRemapKey(key);
        int cur = isLong ? r.longA : r.shortA;
        String curLabel = null;
        for (final int a : RemapAction.picker(RemapKey.nav(key), hasInputs())) {
            if (isLong && RemapAction.deckKey(a)) continue;   // reading a button as another one is a short press thing
            boolean same = isLong && a == RemapAction.DEFAULT;
            String label = same ? RemapAction.SAME_NAME : RemapAction.name(a);
            Panel.Row row = new Panel.Row(label).id(same ? "remap.act.same" : RemapAction.helpId(a));
            if (a == cur) { row.info("NOW"); curLabel = label; }
            else if (RemapAction.needsService(a) && PsService.live == null) row.info("NEEDS SERVICE");
            row.ok(() -> {
                if (a == RemapAction.APP) openRemapApps(key, isLong); else applyRemapAction(key, isLong, a, "");
            });
            p.add(row);
        }
        p.selectLabel(0, curLabel);
        openPanel(p);
    }

    private void applyRemapAction(long key, boolean isLong, int a, String arg) {
        RemapTable.Rule r = remapRule(key);
        if (isLong) { r.longA = a; r.longArg = arg; } else { r.shortA = a; r.shortArg = arg; }
        r.fix(key);
        saveRemap(key, r);
        fx(SoundMap.SELECT);
        if (isLong && a != RemapAction.DEFAULT && r.shortA == RemapAction.DEFAULT) {
            toast("A short press now does nothing. Android cannot hand a held button back to the system.");
        } else if (!isLong && RemapAction.deckKey(a)) {
            toast("Works inside Visor only. In other apps this button stays as it is.");
        }
        openRemapKey(key);
    }

    /** Picks the app for LAUNCH APP. Lists every launchable app, sideloaded ones included. */
    private void openRemapApps(final long key, final boolean isLong) {
        final Panel p = new Panel(this, "remapapp", "Launch app");
        p.onBack = () -> openRemapActions(key, isLong);
        if (scanCache != null) fillRemapApps(p, scanCache, key, isLong);
        else p.add(new Panel.Row("SCANNING INSTALLED APPS").info("PLEASE WAIT"));
        openPanel(p);
        if (scanCache != null) return;
        final Context app = getApplicationContext();
        try {
            work.execute(() -> {
                ArrayList<AppStore.App> l = null;
                try { l = AppStore.scanAll(app); } catch (Throwable ignored) {}
                final ArrayList<AppStore.App> res = l;
                h.post(() -> {
                    if (destroyed || res == null) return;
                    scanCache = res;
                    if (panel == p && !p.closing) { fillRemapApps(p, res, key, isLong); focusChanged(true); }
                });
            });
        } catch (Throwable ignored) {}
    }

    private void fillRemapApps(Panel p, ArrayList<AppStore.App> apps, final long key, final boolean isLong) {
        ArrayList<Panel.Row> rows = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        for (final AppStore.App a : apps) {
            final String pkg = a.cn.getPackageName();
            if (!seen.add(pkg)) continue;
            rows.add(new Panel.Row(a.label).ok(() -> applyRemapAction(key, isLong, RemapAction.APP, pkg)));
        }
        if (rows.isEmpty()) rows.add(new Panel.Row("NO LAUNCHABLE APPS FOUND"));
        p.setRows(0, rows);
        engine.kick();
    }

    // ---- LEARN A BUTTON and the warnings

    private void showRemapLearn() {
        final boolean adv = prefs.getBoolean(RemapTable.KEY_ADV, false);
        showTest(new RemapLearnView(this, this, reduce, adv, (key, label) -> assignLearned(key, label)), "LEARN A BUTTON");
        testBack = () -> openSettings(T_REMAP, "LEARN A BUTTON");
    }

    /** ASSIGN AN ACTION on the learn screen: remember the button (with its name) and open its page. */
    private void assignLearned(final long key, String label) {
        RemapTable.Rule r = remapRule(key);
        if (!RemapKey.known(key)) r.label = label;
        if (RemapPrefs.table(prefs).get(key) == null && !firstFive(key) && RemapKey.cls(key) == RemapKey.STANDARD) {
            SharedPreferences.Editor ed = prefs.edit();
            RemapTable.save(RemapPrefs.sink(ed), key, r);
            ed.apply();
            reloadRemap();
        }
        testBack = () -> openRemapKey(key);
        if (test != null) test.exit(null);
    }

    private static final String[] ADV_WARNING = {
            "THIS SCREEN CHANGES THE BOX'S NORMAL BUTTONS: THE D-PAD, OK, BACK, MENU,",
            "THE ASSISTANT, THE MEDIA KEYS AND THE VOLUME KEYS.",
            "",
            "WHAT CAN GO WRONG",
            "TURN THE D-PAD, OK OR BACK INTO SOMETHING ELSE AND YOU CAN LOSE THE WAY TO MOVE AROUND.",
            "ANDROID CANNOT PRESS ANOTHER BUTTON FOR YOU. A REMAP CAN ONLY RUN AN ACTION, SO THE",
            "BUTTON STOPS DOING ITS OLD JOB. INSIDE VISOR YOU CAN SWAP BUTTONS, FOR EXAMPLE OK AND BACK.",
            "IN OTHER APPS THE D-PAD, OK AND BACK STAY NORMAL UNTIL YOU TURN ON A SECOND SWITCH.",
            "",
            "HOW TO GET OUT",
            "HOLD BACK FOR 6 SECONDS, ANYWHERE. THAT CLEARS THE D-PAD, OK AND BACK REMAPS.",
            "IN SETUP, REMAP, RESET ALL REMAPS CLEARS EVERYTHING.",
            "FROM A PHONE: adb shell am start -n com.nokoding.deck/.HomeActivity --es deck_cmd reset_remap"};

    private static final String[] NAV_WARNING = {
            "THIS LETS THE D-PAD, OK AND BACK RUN ACTIONS IN EVERY APP, NOT JUST IN VISOR.",
            "",
            "A BUTTON THAT RUNS AN ACTION IS NO LONGER A D-PAD KEY. THE APP IN FRONT NEVER GETS IT.",
            "A WRONG REMAP CAN LEAVE YOU UNABLE TO MOVE OR GO BACK IN A STREAMING APP.",
            "",
            "THE ESCAPE: HOLD BACK FOR 6 SECONDS. IT WORKS EVEN WHEN BACK IS REMAPPED, AND IT TURNS",
            "THIS SWITCH OFF AND CLEARS THE D-PAD, OK AND BACK REMAPS.",
            "IF BACK DOES NOT WORK AT ALL, USE A PHONE:",
            "adb shell am start -n com.nokoding.deck/.HomeActivity --es deck_cmd reset_remap"};

    /** The full screen warning in front of ADVANCED REMAP (strong = the second one, for the system wide navigation switch). */
    private void showRemapWarning(final boolean strong) {
        final String back = strong ? "SYSTEM WIDE NAVIGATION REMAP" : "ADVANCED REMAP";
        RemapWarnView v = new RemapWarnView(this, this, reduce, strong ? "SYSTEM WIDE NAVIGATION REMAP: LAST WARNING" : "ADVANCED REMAP: READ THIS",
                strong ? NAV_WARNING : ADV_WARNING, strong ? 5000 : 3000, () -> {
            SharedPreferences.Editor ed = prefs.edit().putBoolean(RemapTable.KEY_ADV, true);
            if (strong) ed.putBoolean(RemapTable.KEY_NAV, true);
            ed.apply();
            reloadRemap();
            testBack = () -> openRemapAdvanced(strong ? back : null);
            if (test != null) test.exit(null);
        });
        showTest(v, back);
        testBack = () -> { if (strong) openRemapAdvanced(back); else openSettings(T_REMAP, "ADVANCED REMAP"); };
    }

    private static final String[] HOME_WARNING = {
            "THIS LETS THE HOME BUTTON RUN AN ACTION INSTEAD OF GOING HOME. IT IS EXPERIMENTAL.",
            "",
            "IT ONLY WORKS WHILE VISOR'S OVERLAY SERVICE IS ON, AND ONLY IF THIS BOX LETS THE SERVICE SEE HOME.",
            "NOBODY HAS TESTED THAT YET, SO CHECK WITH HOME KEY CHECK FIRST. A WRONG REMAP CAN LEAVE YOU",
            "WITHOUT A WORKING HOME BUTTON.",
            "",
            "HOW TO GET OUT",
            "PRESS HOME FIVE TIMES QUICKLY. THE LAST PRESS GOES HOME AND THE HOME REMAP SWITCHES OFF.",
            "OR HOLD BACK FOR 6 SECONDS, OR USE RESET ALL REMAPS.",
            "FROM A PHONE: adb shell am start -n com.nokoding.deck/.HomeActivity --es deck_cmd reset_remap"};

    private String homeSeenText() {
        int n = prefs.getInt(PsService.PREF_HOME_N, 0);
        if (n <= 0) return "NOT SEEN YET. TURN ON THE OVERLAY SERVICE, PRESS HOME ON THE REMOTE, THEN OPEN THIS SCREEN AGAIN. IF IT STAYS EMPTY THE BOX KEEPS HOME FROM THE SERVICE AND HOME CANNOT BE REMAPPED.";
        long ago = Math.max(0, (System.currentTimeMillis() - prefs.getLong(PsService.PREF_HOME_AT, 0)) / 1000);
        return "THE SERVICE SAW HOME " + n + " TIMES, THE LAST ONE " + (ago < 120 ? ago + " SECONDS" : (ago / 60) + " MINUTES") + " AGO. REMAPPING IT CAN WORK.";
    }

    private void showHomeWarning() {
        RemapWarnView v = new RemapWarnView(this, this, reduce, "REMAP HOME: LAST WARNING", HOME_WARNING, 5000, () -> {
            prefs.edit().putBoolean(RemapTable.KEY_HOME, true).apply();
            reloadRemap();
            testBack = () -> openRemapAdvanced("REMAP HOME");
            if (test != null) test.exit(null);
        });
        showTest(v, "REMAP HOME");
        testBack = () -> openRemapAdvanced("REMAP HOME");
    }

    private void swapPair(int codeA, int actA, int codeB, int actB) {
        long ka = RemapKey.pack(codeA, 0, 0, 0), kb = RemapKey.pack(codeB, 0, 0, 0);
        RemapTable.Rule a = remapRule(ka), b = remapRule(kb);
        a.shortA = actA; a.shortArg = ""; a.longA = RemapAction.DEFAULT; a.longArg = "";
        b.shortA = actB; b.shortArg = ""; b.longA = RemapAction.DEFAULT; b.longArg = "";
        saveRemap(ka, a.fix(ka));
        saveRemap(kb, b.fix(kb));
    }

    /** Setup, REMAP, ADVANCED REMAP: the switches, two swap presets and every system button. */
    private void openRemapAdvanced(String row) {
        final Panel p = new Panel(this, "remapadv", "Advanced remap");
        p.onBack = () -> openSettings(T_REMAP, "ADVANCED REMAP");
        p.add(new Panel.Row("IF NAVIGATION BREAKS").two("HOLD BACK FOR 6 SECONDS. OR SETUP, REMAP, RESET ALL REMAPS. OR ON A PHONE: DECK_CMD RESET_REMAP."));
        final Panel.Row nav = p.add(new Panel.Row("SYSTEM WIDE NAVIGATION REMAP").values(prefs.getBoolean(RemapTable.KEY_NAV, false) ? 1 : 0, "OFF", "ON"));
        nav.change(() -> {
            if (nav.sel == 1) {
                nav.sel = 0;
                p.invalidate();
                showRemapWarning(true);
            } else {
                prefs.edit().putBoolean(RemapTable.KEY_NAV, false).apply();
                reloadRemap();
            }
        });
        p.add(new Panel.Row("HOME KEY CHECK").two(homeSeenText()));
        final Panel.Row hm = p.add(new Panel.Row("REMAP HOME").values(prefs.getBoolean(RemapTable.KEY_HOME, false) ? 1 : 0, "OFF", "ON"));
        hm.change(() -> {
            if (hm.sel == 1) {
                hm.sel = 0;
                p.invalidate();
                if (prefs.getInt(PsService.PREF_HOME_N, 0) <= 0) { toast("The service has not seen Home yet. See HOME KEY CHECK."); return; }
                showHomeWarning();
            } else {
                SharedPreferences.Editor ed = prefs.edit();
                RemapTable.clearHome(RemapPrefs.sink(ed), prefs.getAll());
                ed.apply();
                reloadRemap();
                openRemapAdvanced("REMAP HOME");
            }
        });
        p.add(new Panel.Row("SWAP OK AND BACK IN VISOR").ok(() -> {
            swapPair(RemapKey.KC_DPAD_CENTER, RemapAction.KEY_BACK, RemapKey.KC_BACK, RemapAction.KEY_OK);
            toast("OK and Back are swapped inside Visor. Hold Back for 6 seconds to undo.");
            openRemapAdvanced("SWAP OK AND BACK IN VISOR");
        }));
        p.add(new Panel.Row("SWAP LEFT AND RIGHT IN VISOR").ok(() -> {
            swapPair(RemapKey.KC_DPAD_LEFT, RemapAction.KEY_RIGHT, RemapKey.KC_DPAD_RIGHT, RemapAction.KEY_LEFT);
            toast("Left and right are swapped inside Visor. Hold Back for 6 seconds to undo.");
            openRemapAdvanced("SWAP LEFT AND RIGHT IN VISOR");
        }));
        RemapTable t = RemapPrefs.table(prefs);
        if (prefs.getBoolean(RemapTable.KEY_HOME, false)) addRemapRow(p, RemapKey.pack(RemapKey.KC_HOME, 0, 0, 0), t);
        for (int code : RemapKey.ADVANCED_LIST) addRemapRow(p, RemapKey.pack(code, 0, 0, 0), t);
        p.add(new Panel.Row("TURN OFF ADVANCED REMAP").danger().confirm("PRESS OK AGAIN TO TURN IT OFF").ok(() -> {
            SharedPreferences.Editor ed = prefs.edit();
            RemapTable.clearAdvanced(RemapPrefs.sink(ed), prefs.getAll());
            ed.apply();
            reloadRemap();
            toast("Advanced remap is off and its buttons are back to default");
            openSettings(T_REMAP, "ADVANCED REMAP");
        }));
        p.selectLabel(0, row);
        openPanel(p);
    }

    private void openInputs() {
        if (store.inputs.isEmpty()) { toast("No inputs found"); return; }
        Panel p = new Panel(this, "inputs", "Inputs");
        for (final AppStore.Input in : store.inputs) {
            p.add(new Panel.Row(in.label).ok(() -> {
                closePanel();
                try {
                    startFirst(new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(in.id)));
                } catch (Throwable t) {
                    toast("Not available on this device");
                }
            }));
        }
        openPanel(p);
    }

    private void moveApp(AppStore.App a, int d) {
        if (store.move(list, a.key, d)) {
            rebuildLists(a.key);
            moved(d, 0);
        } else {
            bump(d, 0);
        }
    }

    private void moveDock(AppStore.App a, int d) {
        if (store.movePinned(a.key, d)) {
            rebuildLists(focusedKey());
            int fixed = dock.size() - store.dockApps().size();
            int i = -1;
            ArrayList<AppStore.App> da = store.dockApps();
            for (int k = 0; k < da.size(); k++) if (da.get(k).key.equals(a.key)) i = k;
            if (i >= 0) dockIdx = fixed + i;
            moved(d, 0);
        } else {
            bump(d, 0);
        }
    }

    private final Runnable panoTheme = () -> { if (pano != null) pano.themeChanged(); };

    private void applyTheme() {
        Ui.T = Ui.THEMES[themeIdx];
        // The panorama repaints two bitmaps, so debounce it while a slider is held.
        h.removeCallbacks(panoTheme);
        h.postDelayed(panoTheme, 250);
        hud.themeChanged();
        stats.themeChanged();
        focusLabel.themeChanged(); status.themeChanged(); clock.themeChanged(); date.themeChanged();
        title.themeChanged(); sub.themeChanged(); empty.themeChanged();
        netBtn.themeChanged(); sysBtn.themeChanged();
        for (Chip c : dock) c.themeChanged();
        for (TileView t : slots) t.themeChanged();
        for (TileView t : gridTiles) t.themeChanged();
        frame.themeChanged();
        glow.themeChanged();
        if (hudToast != null) hudToast.themeChanged();
        if (panel != null) panel.themeChanged();
        if (quick != null) quick.themeChanged();
        if (dockRule != null) dockRule.themeChanged();
        if (wall != null && wallOpts != null) wall.setLook(wallOpts, Ui.T.a);
        clock2v.themeChanged();
        CursorIcon.invalidate();
        applyCursorIcon();
        applyLight();
    }

    /** 2.3: Deck's own HUD toast, animated like the menus. A system toast while Deck is not in front. */
    private void toast(String s) {
        if (hudToast != null && resumed && !destroyed && root != null) {
            AnimConfig.reveal(anim, AnimConfig.TOAST, reduce, scratchText);
            hudToast.show(s, engine, typeTicks, typeFx, scratchText, AnimConfig.glass(anim), reduce);
            engine.kick();
            return;
        }
        try { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); } catch (Throwable ignored) {}
    }

    // ------------------------------------------------------------------ clock, status, stats

    private void refreshFormats() {
        fmtDate = new SimpleDateFormat("EEE dd MMM", Locale.getDefault());
        tz = TimeZone.getDefault();
        fmtDate.setTimeZone(tz);
        dateMinute = -1;
    }

    /** The time as HH:MM[:SS] or H:MM[:SS] AM, the same length all day (ClockFmt), the date and the second clock line. */
    private void updateClock() {
        if (clock == null || fmtDate == null) return;
        long now = System.currentTimeMillis();
        long off = tz.getOffset(now);
        int n = ClockFmt.format(clockBuf, ClockFmt.hour(now, off), ClockFmt.minute(now, off), ClockFmt.second(now, off), clock24, clockSec);
        clock.set(new String(clockBuf, 0, n));
        long minute = Math.floorDiv(now + off, ClockFmt.MINUTE_MS);
        if (minute != dateMinute) {
            dateMinute = minute;
            date.set(fmtDate.format(new Date(now)).toUpperCase(Locale.ROOT).replace(".", ""));
        }
        if (!clock2.isEmpty()) clock2v.set(ClockFmt.zoneLine(clock2, now, clock24, clockSec));
    }

    /** Shows or hides the second clock line. With it on, the main clock moves up 4 px to make room inside the bar. */
    private void applyClockZone() {
        if (clock == null || clock2v == null) return;
        boolean on = !clock2.isEmpty();
        clock2v.setVisibility(on ? View.VISIBLE : View.GONE);
        root.put(clock, 1604, on ? 52 : 56, 200, 62);
        if (!on) clock2v.set("");
    }

    /** One delayed post per tick: the next second boundary with CLOCK SECONDS on, the next minute boundary off. Nothing per frame. */
    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            updateClock();
            long now = System.currentTimeMillis();
            h.postDelayed(this, ClockFmt.nextDelay(now, tz.getOffset(now), clockSec));
        }
    };

    private void restartClock() {
        h.removeCallbacks(clockTick);
        dateMinute = -1;
        if (resumed) clockTick.run(); else updateClock();
    }

    private final Runnable statusTick = new Runnable() {
        @Override public void run() {
            updateStatus();
            if (playerLedMode == PlayerLeds.BATTERY) updatePlayerLeds();
            h.postDelayed(this, 15000);
        }
    };

    private final Runnable statsTick = new Runnable() {
        @Override public void run() {
            if (!statsOn || stats == null || !resumed) return;
            try { stats.sample(am, engine.stats); } catch (Throwable ignored) {}
            h.postDelayed(this, 1000);
        }
    };

    private void updateStatus() {
        if (status == null) return;
        link = "OFF";
        int glyph = Chip.G_OFF;
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            Network n = cm == null ? null : cm.getActiveNetwork();
            NetworkCapabilities nc = n == null ? null : cm.getNetworkCapabilities(n);
            if (nc != null) {
                if (nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) { link = "ETH"; glyph = Chip.G_ETH; }
                else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) { link = "WIFI"; glyph = Chip.G_WIFI; }
                else { link = "NET"; glyph = Chip.G_WIFI; }
            }
        } catch (Throwable ignored) {}
        netBtn.setGlyph(glyph);
        status.set("LINK " + link + "  //  INPUT " + (mode == InputMode.GAMEPAD ? "GAMEPAD" : (mode == InputMode.POINTER ? "POINTER" : "REMOTE")));
    }

    // ------------------------------------------------------------------ crash guard and safe mode

    private static void installCrashGuard(final SharedPreferences p) {
        if (guardInstalled) return;
        guardInstalled = true;
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                long now = System.currentTimeMillis();
                int n = p.getInt("crashN", 0);
                if (now - p.getLong("crashT", 0) > 120000) n = 0;
                p.edit().putInt("crashN", n + 1).putLong("crashT", now).commit();
            } catch (Throwable ignored) {}
            if (prev != null) prev.uncaughtException(t, e);
            else { Process.killProcess(Process.myPid()); System.exit(10); }
        });
    }

    private boolean shouldSafeMode() {
        int n = prefs.getInt("crashN", 0);
        long t = prefs.getLong("crashT", 0);
        return n >= 3 && System.currentTimeMillis() - t < 120000;
    }

    private void showSafeMode() {
        safeMode = true;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(0xFF07040D);
        int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48, getResources().getDisplayMetrics());
        box.setPadding(pad, pad, pad, pad);
        TextView tv = new TextView(this);
        tv.setText(R.string.safe_title);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        tv.setGravity(Gravity.CENTER);
        box.addView(tv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Button firstBtn;
        if (stockHomeEnabled()) {
            firstBtn = safeButton(box, getString(R.string.safe_stock), v -> startFirst(stockHomeIntent(), new Intent(Settings.ACTION_SETTINGS)));
            safeButton(box, getString(R.string.safe_retry), v -> restartDeck(false));
        } else {
            TextView hint = new TextView(this);
            hint.setText(R.string.safe_hint);
            hint.setTextColor(0xFFA99DC6);
            hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            hint.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hp.topMargin = pad / 3;
            box.addView(hint, hp);
            firstBtn = safeButton(box, getString(R.string.safe_settings), v -> startFirst(new Intent(Settings.ACTION_SETTINGS)));
            safeButton(box, getString(R.string.safe_restart), v -> restartDeck(false));
        }
        safeButton(box, getString(R.string.safe_reset), v -> restartDeck(true));
        setContentView(box);
        firstBtn.requestFocus();
    }

    private void restartDeck(boolean reset) {
        if (reset) prefs.edit().clear().commit();
        else prefs.edit().putInt("crashN", 0).commit();
        recreate();
    }

    private Button safeButton(LinearLayout box, String label, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        b.setAllCaps(false);
        b.setFocusable(true);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 360, getResources().getDisplayMetrics()),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics());
        box.addView(b, lp);
        return b;
    }
}
