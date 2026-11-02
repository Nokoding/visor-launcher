package com.nokoding.deck;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.media.AudioManager;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The PS button menu, as a control center (2.8). Before 2.8 it was one list: recent apps, volume, Setup.
 * Now it is a strip of sections (QUICK, APPS, DISPLAY, CONTROLLER, SOUND, SYSTEM) and under it a page of
 * tiles: buttons, toggles that light up, a volume slider and status lines. Which tiles show, and in which
 * order, comes from the pure CcModel and CcPrefs (Setup, PS MENU can hide and move them). The grid, the
 * focus movement and the scrolling come from the pure CcLayout.
 *
 * The APPS section is the task manager: the apps used recently, with how long ago, the one in front marked,
 * and OK on a row opens that app's actions (KILL BACKGROUND, FORCE STOP, OPEN, APP INFO, GAME PROFILE).
 * Bulk and stopping actions ask a second press first.
 *
 * It only talks to its Host and the AudioManager, so the same view runs inside Deck and in the PS overlay
 * service's window (PsService). Works with a D-pad remote, a gamepad and a pointer. L1 and R1 (or left and
 * right on the strip) change the section.
 *
 * Animation as in 2.3 and 2.4: the title and the tiles type in with the shared TextReveal, the panel grows
 * out of the right edge (FlyoutAnim), mirror for right to left, GLASS style. Text is measured into TypeDraw
 * pieces when it changes, so animation frames do not allocate. Reduce effects: no type in, a short fade.
 */
final class QuickMenu extends View implements TextReveal.Callback {

    interface Host {
        /** Cached icon or null (the host loads it and calls invalidate()). */
        IconCache.Icon quickIcon(String appKey);
        void quickSetup();
        void quickClose();
        /** The control center's tile settings (never null). */
        CcPrefs ccPrefs();
        /** Recent apps for the APPS section, newest first, already marked (see TaskList.build). */
        List<TaskList.Entry> ccTasks();
        /** A toggle tile's state. */
        boolean ccOn(String tileId);
        /** Status text of a header or button tile (shown at its right), or null. */
        String ccText(String tileId);
        /** False greys the tile out (nothing to switch, feature off). */
        boolean ccEnabled(String tileId);
        /** True when the overlay service runs, so global actions (home, back, screenshot...) can work. */
        boolean ccService();
        /** Runs a tile. Returns a short result for the top right label, or null. */
        String ccAct(String tileId);
        /** The action ids (AA_*) that apply to this app, in order. */
        int[] ccAppActions(TaskList.Entry e);
        /** Runs an app action. Returns a short result for the top right label, or null. */
        String ccAppAct(int action, TaskList.Entry e);
        /** Closes every killable background app in the list. Returns a short result. */
        String ccCloseAll(List<TaskList.Entry> killable);
    }

    // item kinds
    static final int K_BUTTON = 0, K_TOGGLE = 1, K_SLIDER = 2, K_HEADER = 3, K_APP = 4, K_EMPTY = 5;
    // app action ids
    static final int AA_KILL = 0, AA_FORCE = 1, AA_OPEN = 2, AA_INFO = 3, AA_PROFILE = 4, AA_ADB = 5, AA_PROTECT = 6;
    static final String[] AA_NAME = {"KILL BACKGROUND", "FORCE STOP", "OPEN", "APP INFO", "GAME PROFILE", "SHOW ADB COMMAND", "PROTECT FROM CLOSE ALL"};
    static final String[] AA_HELP = {
            "Ends the app's background and cached processes. The app on screen cannot be killed this way.",
            "Experimental: opens the app's App Info page and presses Force stop for you. Needs the overlay service.",
            "Opens the app.",
            "Opens Android's App Info page for the app.",
            "Opens the game profile for this app, to lower the picture size while it runs.",
            "Shows the adb line that force stops this app from your phone.",
            "Keeps the app out of CLOSE ALL BACKGROUND, or puts it back in."};
    static final int SEGS = 20, VIS_ROWS = 7;
    /** hit() results at or below this are section tabs: tab index = -(r + TAB_HIT). */
    static final int TAB_HIT = 100;

    private static final long ARM_MS = 4000;

    private static final class Item {
        final int kind;
        String id = "";
        int span = 1;
        boolean focusable = true, enabled = true, on;
        String help = "";
        TaskList.Entry app;
        int action = -1;
        final TypeDraw.Piece a = new TypeDraw.Piece(), b = new TypeDraw.Piece();
        int kGen = -1;
        boolean kBanner;
        String kText = "";
        String title = "";
        Item(int kind) { this.kind = kind; }
    }

    private final Host host;
    private final AudioManager audio;
    private final ArrayList<Item> items = new ArrayList<>();
    private int[] rowOf = new int[0], colOf = new int[0];
    private int[] spans = new int[0];
    private boolean[] focusable = new boolean[0];
    private int rows, top;
    /** Index of the focused item, or CcLayout.STRIP for the section strip. */
    int focus;
    private int section;
    private final int[] mem = new int[CcModel.COUNT];
    private int[] tabs = new int[0];
    private TaskList.Entry subApp;
    private int[] subActs = new int[0];
    private String armedKey;
    private long armedUntil;
    private List<TaskList.Entry> taskCache = new ArrayList<>();
    boolean effects = true, gamepad;
    int style;
    private int vol, volMax;
    private boolean muted, fixed;
    String volError;
    private String volText = "";
    // 2.3 animation
    private TextReveal reveal;
    private final TextReveal.Params textP = new TextReveal.Params();
    private Panel.Fx fx;
    private boolean rtl, glassy;
    private float slashW;
    private int gen;
    private int[] lens = new int[16];
    private final TypeDraw.Piece titleP = new TypeDraw.Piece(), tagP = new TypeDraw.Piece(), helpP = new TypeDraw.Piece();
    private final Paint cursorP = Ui.fill();
    private float la = 1f;
    // 2.4 flyout open and close
    final FlyoutAnim fa = new FlyoutAnim();
    boolean closing;
    private final Path grown = new Path();
    private float scrimA = 0.55f;

    private final Path shape = new Path(), plate = new Path();
    private final Paint scrim = Ui.fill(), fill = Ui.fill(), edge = Ui.stroke(1), rule = Ui.stroke(1);
    private final Paint hi = Ui.fill(), hiEdge = Ui.stroke(1), bar = Ui.fill(), barOff = Ui.fill(), glass = Ui.fill(), glassEdge = Ui.stroke(1);
    private final Paint lit = Ui.fill(), litEdge = Ui.stroke(1);
    private final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final TextPaint head, label, small, value, tabP;
    private final RectF tmp = new RectF(), tmp2 = new RectF();
    private float px, py, pw, ph;
    static final float ROW = 84, HEAD = 100, STRIP = 58, FOOT = 92, PAD = 24, GAP = 12;

    QuickMenu(Context c, Host host) {
        super(c);
        this.host = host;
        AudioManager a = null;
        try { a = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE); } catch (Throwable ignored) {}
        audio = a;
        head = tp(Ui.HEAD, 26);
        head.setLetterSpacing(Ui.HEAD_SPACING);
        label = tp(Ui.COND_B, 28);
        label.setLetterSpacing(Ui.LABEL_SPACING * 0.5f);
        small = tp(Ui.MONO, 17);
        value = tp(Ui.MONO_B, 21);
        tabP = tp(Ui.HEAD, 15);
        setWillNotDraw(false);
        rebuild();
        themeChanged();
    }

    private static TextPaint tp(android.graphics.Typeface tf, float size) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setTypeface(tf);
        p.setTextSize(Ui.px(size));
        return p;
    }

    @Override public boolean hasOverlappingRendering() { return false; }

    /** 2.4: the overlay window's touch listener reports clicks here (accessibility contract). */
    @Override public boolean performClick() { return super.performClick(); }

    // ---------------------------------------------------------------- building a page

    /** Re-read the tabs, the page, the volume and every state. Call when opening and after Setup changed the tiles. */
    void rebuild() {
        CcPrefs cp = host.ccPrefs();
        ArrayList<Integer> t = new ArrayList<>();
        for (int s = 0; s < CcModel.COUNT; s++) if (!cp.visible(s).isEmpty()) t.add(s);
        if (t.isEmpty()) t.add(CcModel.QUICK);
        tabs = new int[t.size()];
        for (int i = 0; i < tabs.length; i++) tabs[i] = t.get(i);
        boolean ok = false;
        for (int s : tabs) if (s == section) ok = true;
        if (!ok) section = tabs[0];
        subApp = null;
        readVolume();
        buildPage();
        focus = CcLayout.remember(spans, focusable, mem[section]);
        top = 0;
        scrollToFocus();
        layoutPanel();
        invalidate();
    }

    private Item header(String id, String text, String help) {
        Item it = new Item(K_HEADER);
        it.id = id;
        it.span = 2;
        it.focusable = false;
        it.help = help;
        it.title = text;
        return it;
    }

    private Item fromTile(CcModel.Tile t) {
        int k;
        switch (t.kind) {
            case CcModel.TOGGLE: k = K_TOGGLE; break;
            case CcModel.SLIDER: k = K_SLIDER; break;
            case CcModel.HEADER: k = K_HEADER; break;
            default: k = K_BUTTON; break;
        }
        Item it = new Item(k);
        it.id = t.id;
        it.span = t.span;
        it.help = t.help;
        it.focusable = k != K_HEADER;
        it.enabled = tileEnabled(t);
        if (k == K_TOGGLE) it.on = tileOn(t.id);
        return it;
    }

    private boolean tileEnabled(CcModel.Tile t) {
        try {
            if (t.needsService && !host.ccService()) return false;
            return host.ccEnabled(t.id);
        } catch (Throwable x) {
            return false;
        }
    }

    private boolean tileOn(String id) {
        if (id.equals("q.mute")) return muted;
        try { return host.ccOn(id); } catch (Throwable x) { return false; }
    }

    private String tileText(String id) {
        if (id.equals("q.volume") || id.equals("s.volume")) return volText;
        try { return host.ccText(id); } catch (Throwable x) { return null; }
    }

    private void buildPage() {
        items.clear();
        if (subApp != null) {
            Item h = header("sub.head", subApp.label.toUpperCase(Locale.ROOT), "The app you picked. B or BACK returns to the list.");
            h.span = 2;
            items.add(h);
            for (int a : subActs) {
                Item it = new Item(K_BUTTON);
                it.id = "app." + a;
                it.action = a;
                it.span = 2;
                it.help = a >= 0 && a < AA_HELP.length ? AA_HELP[a] : "";
                it.kText = a == AA_PROTECT && subApp.protectedApp ? "ON" : "";
                items.add(it);
            }
        } else {
            for (CcModel.Tile t : host.ccPrefs().visible(section)) {
                Item it = fromTile(t);
                items.add(it);
                if (t.id.equals("a.header")) addApps();
            }
        }
        finishPage();
    }

    private void addApps() {
        List<TaskList.Entry> l = null;
        try { l = host.ccTasks(); } catch (Throwable ignored) {}
        taskCache = l == null ? new ArrayList<>() : l;
        int n = 0;
        for (TaskList.Entry e : taskCache) {
            if (e == null) continue;
            Item it = new Item(K_APP);
            it.id = "app:" + e.pkg;
            it.span = 2;
            it.app = e;
            it.help = "OK opens this app's actions: kill, force stop, open, app info.";
            items.add(it);
            n++;
        }
        if (n == 0) {
            Item it = new Item(K_EMPTY);
            it.span = 2;
            it.focusable = false;
            it.id = "a.empty";
            items.add(it);
        }
    }

    private void finishPage() {
        int n = items.size();
        spans = new int[n];
        focusable = new boolean[n];
        for (int i = 0; i < n; i++) {
            Item it = items.get(i);
            spans[i] = it.span;
            focusable[i] = it.focusable;
        }
        rowOf = CcLayout.rowOf(spans);
        colOf = CcLayout.colOf(spans);
        rows = CcLayout.rowCount(spans);
        gen++;
    }

    void readVolume() {
        if (audio == null) { volError = "NO AUDIO SERVICE"; return; }
        try {
            volMax = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            vol = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            muted = audio.isStreamMute(AudioManager.STREAM_MUSIC);
            fixed = audio.isVolumeFixed();
            volError = null;
        } catch (Throwable t) {
            volError = t.getClass().getSimpleName().toUpperCase(Locale.ROOT);
        }
        volText = volError != null ? volError : VolumeMath.label(vol, volMax, muted, fixed);
    }

    /** Re-read the toggles, the texts and the volume without rebuilding the page (after a tile ran). */
    void refreshStates() {
        readVolume();
        for (Item it : items) {
            if (it.kind == K_TOGGLE) it.on = tileOn(it.id);
        }
        gen++;
        updateHelp();
        invalidate();
    }

    // ---------------------------------------------------------------- 2.3 animation

    void anim(TextReveal.Clock clock, TickScheduler ticks, Panel.Fx f, TextReveal.Params text, boolean glassStyle, boolean rightToLeft) {
        fx = f;
        textP.set(text);
        reveal = new TextReveal(clock, this);
        reveal.share(ticks);
        glassy = glassStyle;
        rtl = rightToLeft;
        textP.rtl = rtl;
        themeChanged();
        tag(tagText);
    }

    /** Title and every tile type in, staggered. */
    void startReveal() { startReveal(0); }

    private void startReveal(long delay) {
        if (reveal == null) return;
        if (pw == 0) layoutPanel();
        int k = 0;
        if (lens.length < items.size() + 1) lens = new int[items.size() + 1];
        lens[k++] = titleP.n;
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            prep(it);
            lens[k++] = it.a.n + it.b.n;
        }
        reveal.p.set(textP);
        reveal.startLengths(lens, k, delay);
    }

    /** 2.4: open with the flyout timeline (fa.p set by the caller), the text after the grow starts. */
    void startOpen(long now) {
        if (pw == 0) layoutPanel();
        closing = false;
        fa.p.glide = false;
        fa.open(now, false);
        startReveal(fa.textDelay());
        invalidate();
    }

    /** 2.4: the text clears, then the panel retracts into the right edge. */
    void startClose(long now) {
        closing = true;
        fa.p.glide = false;
        fa.close(now);
        if (reveal != null) {
            if (reveal.running()) reveal.finish();
            reveal.clear(fa.clearMs());
        }
        invalidate();
    }

    /** One frame. @param now the frame clock, or -1 for text only (2.3 callers). */
    boolean stepAnim(long now) {
        boolean moved = reveal != null && reveal.step();
        if (now >= 0 && fa.step(now)) moved = true;
        if (armedKey != null && android.os.SystemClock.uptimeMillis() > armedUntil) { disarm(); moved = true; }
        if (moved) invalidate();
        return animating();
    }

    boolean animating() { return (reveal != null && reveal.running()) || fa.running || armedKey != null; }

    boolean finishAnim() {
        boolean any = reveal != null && reveal.finish();
        any |= fa.finish();
        if (any) invalidate();
        return any;
    }

    /** Closed and done: the window or view can go. */
    boolean gone() { return closing && fa.gone(); }

    /** The small label top right ("PS BUTTON" by default). */
    void tag(String t) {
        tagText = t;
        tagP.set(t != null ? t : (style == InputMode.STYLE_SONY ? "PS BUTTON" : "MODE BUTTON"), small);
        invalidate();
    }

    private String tagText;

    @Override public void onRevealTick(float rate) { if (fx != null) fx.typeTick(rate); }

    @Override public void onRevealFrame() { invalidate(); }

    @Override public void onRevealDone() { invalidate(); }

    // ---------------------------------------------------------------- text pieces

    private float iconW(Item it, float rowHeight) {
        float sz = rowHeight - Ui.px(12);
        IconCache.Icon ic = it.kind == K_APP ? host.quickIcon(it.app.key) : null;
        return (ic != null && ic.bmp != null && ic.banner ? sz * 16f / 9f : sz) + Ui.px(18);
    }

    private float cellW(Item it) {
        float full = pw - Ui.px(PAD) * 2;
        return it.span >= 2 ? full : (full - Ui.px(GAP)) / 2f;
    }

    /** Builds an item's text pieces when what it shows changed. */
    private void prep(Item it) {
        boolean banner = false;
        if (it.kind == K_APP) {
            IconCache.Icon ic = host.quickIcon(it.app.key);
            banner = ic != null && ic.bmp != null && ic.banner;
        }
        String st = it.kind == K_BUTTON || it.kind == K_HEADER ? (it.id.startsWith("app.") ? it.kText : tileText(it.id)) : null;
        if (st == null) st = "";
        if (it.kind == K_SLIDER) st = volText;
        if (it.kind == K_TOGGLE) st = it.on ? "ON" : "OFF";
        if (it.kGen == gen && it.kBanner == banner && it.kText.equals(st)) return;
        it.kGen = gen;
        it.kBanner = banner;
        it.kText = st;
        float w = cellW(it) - Ui.px(36);
        switch (it.kind) {
            case K_APP: {
                float avail = w - iconW(it, Ui.px(ROW) - Ui.px(10)) - value.measureText("IN FRONT") - Ui.px(24);
                it.a.set(TextUtils.ellipsize(it.app.label.toUpperCase(Locale.ROOT), label, Math.max(Ui.px(80), avail), TextUtils.TruncateAt.END), label);
                it.b.set(it.app.foreground ? "IN FRONT" : TaskList.ago(System.currentTimeMillis(), it.app.lastUsed), value);
                break;
            }
            case K_EMPTY:
                it.a.set("NO RECENT APPS YET. OPEN ONE FROM VISOR.", small);
                it.b.set("", small);
                break;
            case K_HEADER: {
                String l = it.id.equals("sub.head") ? it.title : labelFor(it);
                it.a.set(l, small);
                it.b.set(it.id.equals("sub.head") ? "" : st, small);
                break;
            }
            default: {
                float vw = it.kind == K_SLIDER ? value.measureText(st) + Ui.px(12) : (st.isEmpty() ? 0f : Math.min(value.measureText(st), w * 0.5f) + Ui.px(12));
                CharSequence l = TextUtils.ellipsize(labelFor(it), label, Math.max(Ui.px(60), w - vw), TextUtils.TruncateAt.END);
                it.a.set(l, label);
                it.b.set(TextUtils.ellipsize(st, value, Math.max(Ui.px(40), w * 0.5f), TextUtils.TruncateAt.END), value);
                break;
            }
        }
    }

    private String labelFor(Item it) {
        if (it.action >= 0 && it.action < AA_NAME.length) {
            if (armedKey != null && armedKey.equals(it.id)) return "PRESS AGAIN TO CONFIRM";
            return AA_NAME[it.action];
        }
        CcModel.Tile t = CcModel.byId(it.id);
        if (t != null && armedKey != null && armedKey.equals(it.id)) return "PRESS AGAIN TO CONFIRM";
        return t == null ? it.id : t.label;
    }

    // ---------------------------------------------------------------- volume

    /** dir 1 up, -1 down, 0 toggles mute. Fixed volume boxes forward this to the TV over HDMI-CEC when the system allows it. */
    boolean adjust(int dir) {
        if (audio == null) return false;
        try {
            if (fixed) {
                RemapRunner.volume(getContext(), dir > 0 ? RemapAction.VOL_UP : (dir < 0 ? RemapAction.VOL_DOWN : RemapAction.MUTE));
            } else {
                int a = dir > 0 ? AudioManager.ADJUST_RAISE : (dir < 0 ? AudioManager.ADJUST_LOWER : AudioManager.ADJUST_TOGGLE_MUTE);
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, a, 0);
            }
            refreshStates();
        } catch (Throwable t) {
            volError = t.getClass().getSimpleName().toUpperCase(Locale.ROOT);
        }
        invalidate();
        return true;
    }

    // ---------------------------------------------------------------- theme and layout

    void themeChanged() {
        Ui.Theme t = Ui.T;
        scrimA = glassy ? 0.4f : 0.55f;
        scrim.setColor(Ui.alpha(t.bg0, scrimA));
        fill.setColor(Ui.alpha(t.panel, glassy ? 0.7f : 0.97f));
        cursorP.setColor(t.a);
        edge.setColor(Ui.alpha(t.a, 0.7f));
        edge.setStrokeWidth(Math.max(1f, Ui.px(2)));
        rule.setColor(Ui.alpha(t.dim, 0.3f));
        rule.setStrokeWidth(Math.max(1f, Ui.px(1)));
        hi.setColor(Ui.alpha(t.a, 0.16f));
        hiEdge.setColor(t.a);
        hiEdge.setStrokeWidth(Math.max(1.5f, Ui.px(2.5f)));
        bar.setColor(t.a);
        barOff.setColor(Ui.alpha(t.dim, 0.25f));
        glass.setColor(Ui.alpha(t.bg0, 0.8f));
        glassEdge.setColor(Ui.alpha(t.a, 0.5f));
        glassEdge.setStrokeWidth(Math.max(1f, Ui.px(1.2f)));
        lit.setColor(Ui.alpha(t.a, 0.30f));
        litEdge.setColor(Ui.alpha(t.a, 0.9f));
        litEdge.setStrokeWidth(Math.max(1f, Ui.px(1.5f)));
        head.setColor(t.text);
        label.setColor(t.text);
        small.setColor(t.dim);
        value.setColor(t.a);
        gen++;
        if (pw > 0) layoutPanel();
        invalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { layoutPanel(); }

    private void layoutPanel() {
        pw = Ui.px(960);
        ph = Ui.px(HEAD) + Ui.px(STRIP) + Ui.px(12) + Ui.px(ROW) * VIS_ROWS + Ui.px(FOOT);
        px = Ui.px(1824) - pw;
        py = Math.max(Ui.px(30), (Ui.H - ph) / 2f);
        Ui.cut(shape, px, py, px + pw, py + ph, Ui.px(30), Ui.px(10));
        float a = glassy ? 0.7f : 0.97f;
        fill.setShader(new LinearGradient(px, py, px, py + ph, Ui.alpha(Ui.mix(Ui.T.panel, 0xFFFFFFFF, glassy ? 0.10f : 0.04f), a),
                Ui.alpha(Ui.T.bg0, a), Shader.TileMode.CLAMP));
        titleP.set("CONTROL CENTER", head);
        slashW = head.measureText("// ");
        tagP.set(tagText != null ? tagText : (style == InputMode.STYLE_SONY ? "PS BUTTON" : "MODE BUTTON"), small);
        gen++;
        updateHelp();
    }

    private float gridTop() { return py + Ui.px(HEAD) + Ui.px(STRIP) + Ui.px(12); }

    /** Item rect in view coordinates, scrolled. */
    private void itemRect(int i, RectF out) {
        float full = pw - Ui.px(PAD) * 2, cw = (full - Ui.px(GAP)) / 2f;
        float y = gridTop() + (rowOf[i] - top) * Ui.px(ROW);
        float l = px + Ui.px(PAD);
        Item it = items.get(i);
        if (it.span >= 2) out.set(l, y + Ui.px(4), l + full, y + Ui.px(ROW) - Ui.px(4));
        else {
            float x = l + colOf[i] * (cw + Ui.px(GAP));
            out.set(x, y + Ui.px(4), x + cw, y + Ui.px(ROW) - Ui.px(4));
        }
    }

    private boolean visibleItem(int i) { return rowOf[i] >= top && rowOf[i] < top + VIS_ROWS; }

    private void tabRect(int t, RectF out) {
        float l = px + Ui.px(40), r = px + pw - Ui.px(40), g = Ui.px(8);
        float w = (r - l - g * (tabs.length - 1)) / tabs.length;
        float y = py + Ui.px(HEAD);
        out.set(l + t * (w + g), y, l + t * (w + g) + w, y + Ui.px(STRIP) - Ui.px(12));
    }

    private int tabIndex() {
        for (int i = 0; i < tabs.length; i++) if (tabs[i] == section) return i;
        return 0;
    }

    // ---------------------------------------------------------------- navigation

    private void scrollToFocus() {
        if (focus < 0 || focus >= items.size()) { top = Math.min(top, Math.max(0, rows - VIS_ROWS)); return; }
        top = CcLayout.scrollTop(rows, VIS_ROWS, rowOf[focus], top);
    }

    private void remember() { if (focus >= 0 && subApp == null) mem[section] = focus; }

    /** Up or down. @return false when there is nothing further (the host may bump). */
    boolean move(int d) {
        disarm();
        if (focus == CcLayout.STRIP) {
            if (d < 0) return false;
            int f = CcLayout.firstFocusable(focusable);
            if (f < 0) return false;
            focus = f;
        } else {
            int f = CcLayout.move(spans, focusable, focus, 0, d);
            if (f == focus) return false;
            focus = f;
        }
        remember();
        scrollToFocus();
        updateHelp();
        invalidate();
        return true;
    }

    /** Left or right: another section on the strip, the volume on the slider, the neighbour tile in a row. */
    boolean change(int d) {
        disarm();
        if (focus == CcLayout.STRIP) return selectTab(tabIndex() + d);
        if (focus < 0 || focus >= items.size()) return false;
        Item it = items.get(focus);
        if (it.kind == K_SLIDER) return adjust(d);
        int f = CcLayout.move(spans, focusable, focus, d, 0);
        if (f == focus) return false;
        focus = f;
        remember();
        updateHelp();
        invalidate();
        return true;
    }

    /** L1 and R1: previous or next section. */
    boolean tab(int d) {
        disarm();
        if (subApp != null) return false;
        return selectTab(tabIndex() + d);
    }

    private boolean selectTab(int idx) {
        if (idx < 0 || idx >= tabs.length || subApp != null) return false;
        if (tabs[idx] == section) return false;
        remember();
        section = tabs[idx];
        buildPage();
        if (focus != CcLayout.STRIP) focus = CcLayout.remember(spans, focusable, mem[section]);
        top = 0;
        scrollToFocus();
        updateHelp();
        if (effects) startReveal(); else if (reveal != null) startReveal();
        invalidate();
        return true;
    }

    /** B or Back: leaves the app actions page. @return true when it did (the host then does not close the menu). */
    boolean back() {
        if (subApp == null) return false;
        disarm();
        subApp = null;
        buildPage();
        focus = CcLayout.remember(spans, focusable, mem[section]);
        top = 0;
        scrollToFocus();
        updateHelp();
        if (reveal != null) startReveal();
        invalidate();
        return true;
    }

    private void disarm() {
        if (armedKey == null) return;
        armedKey = null;
        gen++;
    }

    private void arm(String key) {
        armedKey = key;
        armedUntil = android.os.SystemClock.uptimeMillis() + ARM_MS;
        gen++;
    }

    boolean ok() {
        if (focus == CcLayout.STRIP) return false;
        if (focus < 0 || focus >= items.size()) return false;
        Item it = items.get(focus);
        if (!it.focusable || !it.enabled) return false;
        String res = null;
        switch (it.kind) {
            case K_SLIDER: return adjust(0);
            case K_APP: openApp(it.app); return true;
            case K_BUTTON:
            case K_TOGGLE:
                if (it.action >= 0) return runAppAction(it);
                if (it.id.equals("q.mute")) return adjust(0);
                if (it.id.equals("a.closeall")) return closeAll(it);
                if (it.id.equals("y.setup")) { host.quickSetup(); return true; }
                try { res = host.ccAct(it.id); } catch (Throwable t) { res = "FAILED"; }
                done(res);
                return true;
            default: return false;
        }
    }

    private void done(String res) {
        refreshStates();
        if (res == null) return;
        if (res.length() > 22) say(res); else tag(res);
    }

    /** A longer result goes in the help line until the focus moves. */
    private void say(String text) {
        helpP.set(TextUtils.ellipsize(text, small, pw - Ui.px(80), TextUtils.TruncateAt.END), small);
        invalidate();
    }

    private void openApp(TaskList.Entry e) {
        int[] acts = null;
        try { acts = host.ccAppActions(e); } catch (Throwable ignored) {}
        if (acts == null || acts.length == 0) return;
        remember();
        subApp = e;
        subActs = acts;
        buildPage();
        focus = CcLayout.firstFocusable(focusable);
        top = 0;
        scrollToFocus();
        updateHelp();
        if (reveal != null) startReveal();
        invalidate();
    }

    private boolean runAppAction(Item it) {
        TaskList.Entry e = subApp;
        if (e == null) return false;
        boolean confirm = it.action == AA_FORCE;
        if (confirm && !(armedKey != null && armedKey.equals(it.id))) { arm(it.id); invalidate(); return true; }
        disarm();
        String res = null;
        try { res = host.ccAppAct(it.action, e); } catch (Throwable t) { res = "FAILED"; }
        if (it.action == AA_PROTECT) {
            e.protectedApp = !e.protectedApp;
            buildPage();
            focus = Math.min(focus, items.size() - 1);
            gen++;
        }
        done(res);
        return true;
    }

    private boolean closeAll(Item it) {
        List<TaskList.Entry> k = TaskList.killable(taskCache);
        if (k.isEmpty()) { tag("NOTHING TO CLOSE"); return true; }
        if (!(armedKey != null && armedKey.equals(it.id))) { arm(it.id); tag(k.size() + (k.size() == 1 ? " APP" : " APPS")); invalidate(); return true; }
        disarm();
        String res = null;
        try { res = host.ccCloseAll(k); } catch (Throwable t) { res = "FAILED"; }
        done(res);
        // the list changes: read it again
        buildPage();
        focus = Math.max(CcLayout.firstFocusable(focusable), Math.min(focus, items.size() - 1));
        scrollToFocus();
        invalidate();
        return true;
    }

    /**
     * @return item index, -2 inside the panel but not on an item, -1 outside, or -(TAB_HIT + n) on section tab n.
     */
    int hit(float x, float y) {
        for (int i = 0; i < tabs.length; i++) {
            tabRect(i, tmp);
            if (tmp.contains(x, y)) return -(TAB_HIT + i);
        }
        for (int i = 0; i < items.size(); i++) {
            if (!visibleItem(i)) continue;
            Item it = items.get(i);
            if (!it.focusable) continue;
            itemRect(i, tmp);
            if (tmp.contains(x, y)) return i;
        }
        return x >= px && x <= px + pw && y >= py && y <= py + ph ? -2 : -1;
    }

    /** A tab hit from hit(): switch to it. */
    boolean tabClick(int tab) {
        boolean r = selectTab(tab);
        return r;
    }

    boolean focusRow(int i) {
        if (i == focus || i < 0 || i >= items.size() || !items.get(i).focusable) return false;
        focus = i;
        remember();
        updateHelp();
        invalidate();
        return true;
    }

    String focusName() {
        if (focus == CcLayout.STRIP) return "SECTIONS";
        if (focus < 0 || focus >= items.size()) return "";
        Item it = items.get(focus);
        if (it.kind == K_APP) return it.app.label;
        return labelFor(it);
    }

    /** The one sentence under the grid for the focused tile. */
    private void updateHelp() {
        String h;
        if (focus == CcLayout.STRIP) h = "Left and right change the section. Down goes to the tiles.";
        else if (focus >= 0 && focus < items.size()) {
            Item it = items.get(focus);
            h = it.help == null ? "" : it.help;
            if (!it.enabled && it.kind != K_HEADER) h = "Needs the overlay service to run outside Visor. " + h;
        } else h = "";
        float w = pw - Ui.px(80);
        helpP.set(TextUtils.ellipsize(h.toUpperCase(Locale.ROOT), small, w, TextUtils.TruncateAt.END), small);
    }

    private int ca(int color) { return la >= 0.999f ? color : Ui.alpha(color, la); }

    /** Two pieces in reading order, label first. ax and bx are left to right positions; rtl mirrors them. */
    private void pieces(Canvas c, Item it, int k, float ax, float ay, Paint ap, float bx, float by, Paint bp, boolean cur, float l, float r) {
        int ka = Math.min(k, it.a.n), kb = Math.max(0, Math.min(it.b.n, k - it.a.n));
        float xa = rtl ? l + r - ax - it.a.w : ax, xb = rtl ? l + r - bx - it.b.w : bx;
        float ha = TypeDraw.draw(c, it.a, ka, xa, ay, ap, rtl);
        float hb = TypeDraw.draw(c, it.b, kb, xb, by, bp, rtl);
        if (!cur) return;
        if (ka < it.a.n || it.b.n == 0) TypeDraw.cursor(c, ha, ay, ap, cursorP, rtl);
        else TypeDraw.cursor(c, hb, by, bp, cursorP, rtl);
    }

    // ---------------------------------------------------------------- drawing

    @Override
    protected void onDraw(Canvas c) {
        if (pw == 0) layoutPanel();
        Ui.Theme t = Ui.T;
        scrim.setColor(Ui.alpha(t.bg0, scrimA * fa.dim));
        c.drawRect(0, 0, getWidth(), getHeight(), scrim);
        float g = fa.grow;
        if (g <= 0.001f) return;
        boolean full = g >= 0.999f;
        float gl = px + pw * (1f - g);
        if (full) {
            c.drawPath(shape, fill);
            c.drawPath(shape, edge);
        } else {
            // 2.4 flyout: the panel grows out of the right edge, its contents clipped to it
            Ui.cut(grown, gl, py, px + pw, py + ph, Ui.px(30), Ui.px(10));
            c.drawPath(grown, fill);
            c.drawPath(grown, edge);
            drawBrackets(c, tmpOut(gl - Ui.px(8), py - Ui.px(8), px + pw + Ui.px(8), py + ph + Ui.px(8)));
            c.save();
            c.clipRect(gl, py, px + pw, py + ph);
        }
        body(c, t);
        if (!full) c.restore();
    }

    private final RectF out2 = new RectF();

    private RectF tmpOut(float l, float tp, float r, float b) { out2.set(l, tp, r, b); return out2; }

    /** Title, strip, tiles and footer. */
    private void body(Canvas c, Ui.Theme t) {
        float x0 = px + Ui.px(40), x1 = px + pw - Ui.px(40);
        float hy = py + Ui.px(62);
        // title: line 0
        la = reveal == null ? 1f : reveal.alpha(0);
        int tk = reveal == null ? titleP.n : Math.min(titleP.n, reveal.visible(0, titleP.n));
        float sl = slashW, tx = rtl ? x1 - titleP.w : x0 + sl;
        head.setColor(ca(t.a));
        c.drawText("//", rtl ? tx - sl : x0, hy, head);
        head.setColor(ca(t.text));
        float hx = TypeDraw.draw(c, titleP, tk, tx, hy, head, rtl);
        if (reveal != null && reveal.cursor(0)) TypeDraw.cursor(c, hx, hy, head, cursorP, rtl);
        la = 1f;
        small.setColor(t.dim);
        c.drawText(tagP.s, rtl ? x0 : x1 - tagP.w, hy - Ui.px(3), small);
        c.drawLine(x0, py + Ui.px(HEAD) - Ui.px(14), x1, py + Ui.px(HEAD) - Ui.px(14), rule);

        // section strip
        for (int i = 0; i < tabs.length; i++) {
            tabRect(i, tmp);
            boolean cur = tabs[i] == section;
            boolean f = focus == CcLayout.STRIP && cur;
            Ui.cut(plate, tmp.left, tmp.top, tmp.right, tmp.bottom, Ui.px(10), Ui.px(3));
            if (cur) {
                bar.setColor(f ? t.a : Ui.alpha(t.a, 0.8f));
                c.drawPath(plate, bar);
                bar.setColor(t.a);
            } else {
                c.drawPath(plate, glassEdge);
            }
            tabP.setColor(cur ? t.bg0 : t.dim);
            String n = CcModel.SECTION_NAMES[tabs[i]];
            c.drawText(n, tmp.centerX() - tabP.measureText(n) / 2f, tmp.centerY() + Ui.px(5), tabP);
            if (f) drawBrackets(c, tmpOut(tmp.left - Ui.px(3), tmp.top - Ui.px(3), tmp.right + Ui.px(3), tmp.bottom + Ui.px(3)));
        }

        // the tiles, clipped to the grid area
        float gt = gridTop();
        c.save();
        c.clipRect(px, gt, px + pw, gt + Ui.px(ROW) * VIS_ROWS);
        for (int i = 0; i < items.size(); i++) {
            if (!visibleItem(i)) continue;
            drawItem(c, t, i);
        }
        c.restore();
        // scroll marks
        if (rows > VIS_ROWS) {
            float th = Ui.px(ROW) * VIS_ROWS, bh = Math.max(Ui.px(24), th * VIS_ROWS / rows);
            float by = gt + (th - bh) * (top / (float) Math.max(1, rows - VIS_ROWS));
            c.drawRect(px + pw - Ui.px(14), gt, px + pw - Ui.px(10), gt + th, barOff);
            c.drawRect(px + pw - Ui.px(14), by, px + pw - Ui.px(10), by + bh, bar);
        }

        // footer: the help sentence, then the controller hints
        float hl = py + ph - Ui.px(52), fy = py + ph - Ui.px(22);
        c.drawLine(x0, hl - Ui.px(26), x1, hl - Ui.px(26), rule);
        small.setColor(Ui.alpha(t.text, 0.86f));
        c.drawText(helpP.s, rtl ? x1 - helpP.w : x0, hl, small);
        small.setColor(t.dim);
        String hint = gamepad ? (style == InputMode.STYLE_SONY ? "CROSS SELECT   CIRCLE BACK   L1 R1 SECTIONS" : "A SELECT   B BACK   LB RB SECTIONS")
                : "OK SELECT   BACK CLOSE   LEFT RIGHT SECTIONS OR VOLUME";
        c.drawText(hint, x0, fy, small);
    }

    private void drawItem(Canvas c, Ui.Theme t, int i) {
        Item it = items.get(i);
        prep(it);
        itemRect(i, tmp);
        boolean f = i == focus;
        int li = i + 1, units = it.a.n + it.b.n;
        int k = reveal == null ? units : Math.min(units, reveal.visible(li, units));
        la = reveal == null ? 1f : reveal.alpha(li);
        float slide = reveal == null ? 0f : reveal.slide(li) * Ui.u;
        boolean cur = reveal != null && reveal.cursor(li);
        if (la <= 0.001f) { la = 1f; return; }
        float dis = it.enabled || it.kind == K_HEADER || it.kind == K_EMPTY ? 1f : 0.4f;
        if (slide != 0f) { c.save(); c.translate(slide, 0); }
        float l = tmp.left, r = tmp.right;
        boolean tile = it.kind == K_BUTTON || it.kind == K_TOGGLE || it.kind == K_SLIDER;
        if (tile) {
            Ui.cut(plate, l, tmp.top, r, tmp.bottom, Ui.px(14), Ui.px(4));
            if (it.kind == K_TOGGLE && it.on) { c.drawPath(plate, lit); c.drawPath(plate, litEdge); }
            else { c.drawPath(plate, glass); c.drawPath(plate, glassEdge); }
        }
        if (f) {
            if (tile) { c.drawPath(plate, hi); }
            drawBrackets(c, tmp);
        }
        float base = tmp.centerY() - (label.ascent() + label.descent()) / 2f;
        float lx = l + Ui.px(18), rx = r - Ui.px(18);
        label.setColor(ca(Ui.alpha(f ? t.text : Ui.alpha(t.text, 0.8f), dis)));
        value.setColor(ca(Ui.alpha(f || (it.kind == K_TOGGLE && it.on) ? t.a : t.dim, dis)));
        switch (it.kind) {
            case K_APP: {
                float s = tmp.height() - Ui.px(12), iy = tmp.top + Ui.px(6);
                IconCache.Icon ic = host.quickIcon(it.app.key);
                float iw = s;
                bmp.setAlpha(Math.round(255 * la));
                if (ic != null && ic.bmp != null && ic.banner) {
                    iw = s * 16f / 9f;
                    tmp2.set(lx, iy, lx + iw, iy + s);
                    c.drawBitmap(ic.bmp, null, tmp2, bmp);
                } else {
                    boolean plated = !IconCache.originalMode;
                    if (plated) {
                        Ui.cut(plate, lx, iy, lx + s, iy + s, s * 0.18f, s * 0.05f);
                        c.drawPath(plate, glass);
                        c.drawPath(plate, glassEdge);
                    }
                    if (ic != null && ic.bmp != null) {
                        float pad = plated ? s * 0.1f : 0f;
                        tmp2.set(lx + pad, iy + pad, lx + s - pad, iy + s - pad);
                        c.drawBitmap(ic.bmp, null, tmp2, bmp);
                    }
                }
                bmp.setAlpha(255);
                float tx2 = lx + iw + Ui.px(18);
                value.setColor(ca(it.app.foreground ? t.a : t.dim));
                pieces(c, it, k, tx2, base, label, rx - it.b.w, base, value, cur, tx2, rx);
                if (it.app.protectedApp) {
                    small.setColor(ca(t.dim));
                    c.drawText("PROTECTED", rtl ? lx : rx - small.measureText("PROTECTED"), tmp.bottom - Ui.px(8), small);
                    small.setColor(t.dim);
                }
                break;
            }
            case K_EMPTY:
                small.setColor(ca(t.dim));
                pieces(c, it, k, lx, tmp.centerY() + Ui.px(6), small, lx, tmp.centerY() + Ui.px(6), small, cur, lx, rx);
                small.setColor(t.dim);
                break;
            case K_HEADER:
                small.setColor(ca(t.dim));
                pieces(c, it, k, lx, tmp.centerY() + Ui.px(6), small, rx - it.b.w, tmp.centerY() + Ui.px(6), small, cur, lx, rx);
                small.setColor(t.dim);
                break;
            case K_SLIDER: {
                float ly = tmp.top + Ui.px(30);
                pieces(c, it, k, lx, ly - (label.ascent() + label.descent()) / 2f - Ui.px(6), label, rx - it.b.w, ly - (label.ascent() + label.descent()) / 2f - Ui.px(6), value, cur, lx, rx);
                int litSeg = muted ? 0 : VolumeMath.lit(vol, volMax, SEGS);
                float bw = tmp.width() - Ui.px(36), seg = bw / SEGS, by = tmp.bottom - Ui.px(20);
                float frac = units == 0 ? 1f : k / (float) units;
                bar.setColor(ca(Ui.alpha(t.a, frac * dis)));
                barOff.setColor(ca(Ui.alpha(t.dim, 0.25f * frac)));
                for (int q = 0; q < SEGS; q++) {
                    int qq = rtl ? SEGS - 1 - q : q;
                    c.drawRect(lx + qq * seg, by, lx + (qq + 1) * seg - Ui.px(4), by + Ui.px(10), q < litSeg ? bar : barOff);
                }
                bar.setColor(t.a);
                barOff.setColor(Ui.alpha(t.dim, 0.25f));
                break;
            }
            default:
                pieces(c, it, k, lx, base, label, rx - it.b.w, base, value, cur, lx, rx);
                break;
        }
        if (slide != 0f) c.restore();
        la = 1f;
        label.setColor(t.text);
        value.setColor(t.a);
    }

    private void drawBrackets(Canvas c, RectF r) {
        float a = Ui.px(14);
        c.drawLine(r.left, r.top, r.left + a, r.top, hiEdge);
        c.drawLine(r.left, r.top, r.left, r.top + a, hiEdge);
        c.drawLine(r.right, r.top, r.right - a, r.top, hiEdge);
        c.drawLine(r.right, r.top, r.right, r.top + a, hiEdge);
        c.drawLine(r.left, r.bottom, r.left + a, r.bottom, hiEdge);
        c.drawLine(r.left, r.bottom, r.left, r.bottom - a, hiEdge);
        c.drawLine(r.right, r.bottom, r.right - a, r.bottom, hiEdge);
        c.drawLine(r.right, r.bottom, r.right, r.bottom - a, hiEdge);
    }
}
