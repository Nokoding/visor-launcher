package com.nokoding.deck;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Modal HUD panel with optional tabs (LB / RB or the tab strip), scrolling rows,
 * value rows, sliders and info rows. Works with a remote D-pad and a gamepad.
 *
 * 2.3: text animation (TextReveal: TYPE, FADE or NONE, staggered rows, block cursor, ticks),
 * right to left layout (every row mirrors, text types from the end of the line), SOLID or
 * GLASS style, a retyped confirmation prompt, and the options flyout layout (FlyoutAnim): the
 * item's icon card docks on one side and the panel grows out of it. Row text is ellipsized and
 * measured once into TypeDraw pieces when it changes, so animation frames do not allocate.
 *
 * 2.5: row text is cut by RowFit (one pure function shares the width between label and value, cut
 * text ends in three dots). The panel keeps the user's real text parameters in a RevealPlan, so a
 * refresh quiets one reveal only. A reveal wakes the frame loop itself (TextReveal.Clock.wake). In
 * Setup and its sub screens the footer shows RowHelp's sentence for the focused row, fading in
 * when it changes, and a Marquee scrolls it when it is too long (static dots under Reduce effects).
 */
final class Panel extends View implements TextReveal.Callback {

    /** Plays one type_tick (the host knows the sound pack, volume and mute). */
    interface Fx { void typeTick(float rate); }

    static final class Row {
        String label, info, confirm;
        /** 2.5: what RowHelp looks up. Null means the label. Rows built from data (the animation schema) set it. */
        String id;
        String[] values;      // value row
        int sel;
        boolean slider;       // slider row
        int min, max, step, val;
        int swatchColor;      // 0 = none
        Runnable onOk, onChange;
        boolean armed, swatch, danger, sub, disabled;
        /** Two line row: small label on top, the full info text below (commands, report lines). */
        boolean two;
        // 2.3 draw cache: rebuilt only when something shown changes
        final TypeDraw.Piece pa = new TypeDraw.Piece(), pb = new TypeDraw.Piece();
        String mark;
        float markW;
        private boolean kF, kArmed, kDis, kSw;
        private int kSel = Integer.MIN_VALUE, kVal, kGen = -1;
        private String kLabel, kInfo;

        Row(String label) { this.label = label == null ? "" : label.toUpperCase(Locale.ROOT); }
        Row two(String text) { two = true; info = text == null ? "" : text; return this; }
        /** Shown dimmed; OK, left and right do nothing. */
        Row disabled(boolean d) { disabled = d; if (d) armed = false; return this; }
        Row values(int sel, String... v) { values = v; this.sel = Math.max(0, Math.min(sel, v.length - 1)); return this; }
        Row slider(int min, int max, int step, int val) {
            slider = true; this.min = min; this.max = max; this.step = Math.max(1, step);
            this.val = Math.max(min, Math.min(max, val)); return this;
        }
        Row ok(Runnable r) { onOk = r; return this; }
        Row change(Runnable r) { onChange = r; return this; }
        Row confirm(String c) { confirm = c; return this; }
        Row swatch() { swatch = true; return this; }
        Row danger() { danger = true; return this; }
        Row sub() { sub = true; return this; }
        Row id(String i) { id = i; return this; }
        Row info(String s) { info = s; return this; }
        int units() { return pa.n + pb.n; }
    }

    static final int VIS = 8;
    /** Extra footer height, design px, for the row help line. */
    private static final float HELP_LINE = 34f;
    private static final ArrayList<Row> NONE = new ArrayList<>();

    final String title, kind;
    final ArrayList<String> tabNames = new ArrayList<>();
    final ArrayList<ArrayList<Row>> tabs = new ArrayList<>();
    int tab, focus, scroll;
    boolean closing, gamepad;
    int style;
    Spring open;
    Runnable onBack;
    /** 2.3: runs once the close animation has removed the panel (the preview returns this way). */
    Runnable afterClose;

    // 2.3 animation
    private TextReveal reveal, confirmRev, helpRev;
    private final TextReveal.Params textP = new TextReveal.Params(), confirmP = new TextReveal.Params();
    /** 2.5: starts every reveal from the user's real parameters; a refresh quiets one start only. */
    private final RevealPlan plan = new RevealPlan(textP);
    private TextReveal.Clock clock;
    private boolean reduce;
    // 2.5 row help (footer line)
    private final boolean helpOn;
    private final Marquee mq = new Marquee();
    private final int[] helpLens = new int[1];
    private String helpFull = "", helpCut = "";
    private float helpTextW, helpViewW;
    private int lastMq = Integer.MIN_VALUE;
    private final Runnable mqWake = new Runnable() { @Override public void run() { if (clock != null) clock.wake(); } };
    private RowFit.Measure mLabel, mValue, mSmall, mInfo;
    private final RowFit.Split split = new RowFit.Split();
    private Fx fx;
    private boolean rtl, glass, revealTitle;
    private int revealScroll, gen;
    private int[] lens = new int[VIS + 1];
    private Row confirmRow;
    private final TypeDraw.Piece titleP = new TypeDraw.Piece(), backP = new TypeDraw.Piece();
    // 2.3 flyout
    boolean flyout;
    /** The app whose icon the flyout shows (for late icon loads). */
    String flyKey;
    final FlyoutAnim fa = new FlyoutAnim();
    final RectF flyFrom = new RectF();
    private final RectF dockR = new RectF(), cardR = new RectF();
    private IconCache.Icon icon;
    private String iconLabel = "";
    private CharSequence iconShown = "";
    private boolean flyRight;
    private final Path card = new Path(), inner = new Path();
    private final Paint plate = Ui.fill(), cardLine = Ui.stroke(1), cursorP = Ui.fill(), bracket = Ui.stroke(2);
    private final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final TextPaint cardText;

    private final Path shape = new Path(), tabShape = new Path();
    private final Paint scrim = Ui.fill(), fill = Ui.fill(), edge = Ui.stroke(1), rule = Ui.stroke(1), sw = Ui.fill();
    private final Paint tabFill = Ui.fill(), tabLine = Ui.stroke(1), bar = Ui.fill(), barOff = Ui.fill(), glyph = Ui.stroke(1);
    private final TextPaint head, label, value, small, infoP, tabText;
    private float px, py, pw, ph, rowH, headH, tabH, rowW, slashW;
    private float[] tabW = new float[8];
    private final RectF tmp = new RectF();
    // per row draw state
    private float la = 1f, rL, rR;

    Panel(Context c, String kind, String title) {
        super(c);
        this.kind = kind;
        this.helpOn = RowHelp.covers(kind);
        this.title = title == null ? "" : title.toUpperCase(Locale.ROOT);
        head = tp(Ui.HEAD, 29);
        head.setLetterSpacing(Ui.HEAD_SPACING);
        label = tp(Ui.COND_B, 31);
        label.setLetterSpacing(Ui.LABEL_SPACING * 0.5f);
        value = tp(Ui.MONO_B, 25);
        small = tp(Ui.MONO, 17);
        infoP = tp(Ui.MONO, 20);
        tabText = tp(Ui.HEAD_M, 16);
        tabText.setLetterSpacing(Ui.LABEL_SPACING);
        cardText = tp(Ui.COND_B, 20);
        cardText.setLetterSpacing(Ui.LABEL_SPACING * 0.5f);
        cardText.setTextAlign(Paint.Align.CENTER);
        mLabel = measure(label);
        mValue = measure(value);
        mSmall = measure(small);
        mInfo = measure(infoP);
        glyph.setStrokeCap(Paint.Cap.ROUND);
        bracket.setStrokeCap(Paint.Cap.SQUARE);
        setWillNotDraw(false);
        themeChanged();
    }

    private static RowFit.Measure measure(final TextPaint p) {
        return new RowFit.Measure() { @Override public float width(String s) { return p.measureText(s); } };
    }

    private static TextPaint tp(android.graphics.Typeface tf, float size) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setTypeface(tf);
        p.setTextSize(Ui.px(size));
        return p;
    }

    @Override public boolean hasOverlappingRendering() { return false; }

    void themeChanged() {
        Ui.Theme t = Ui.T;
        scrim.setColor(Ui.alpha(t.bg0, glass ? 0.6f : 0.78f));
        fill.setColor(Ui.alpha(t.panel, glass ? 0.66f : 0.97f));
        edge.setColor(Ui.alpha(t.a, glass ? 0.9f : 0.65f));
        edge.setStrokeWidth(Math.max(1f, Ui.px(2)));
        rule.setColor(Ui.alpha(t.dim, 0.30f));
        rule.setStrokeWidth(Math.max(1f, Ui.px(1)));
        tabFill.setColor(t.a);
        tabLine.setColor(Ui.alpha(t.a, 0.5f));
        tabLine.setStrokeWidth(Math.max(1f, Ui.px(1.5f)));
        bar.setColor(t.a);
        barOff.setColor(Ui.alpha(t.dim, 0.25f));
        glyph.setColor(t.text);
        glyph.setStrokeWidth(Math.max(1.5f, Ui.px(2f)));
        head.setColor(t.text);
        label.setColor(t.text);
        value.setColor(t.a);
        small.setColor(t.dim);
        infoP.setColor(t.dim);
        cursorP.setColor(t.a);
        bracket.setColor(t.a);
        bracket.setStrokeWidth(Math.max(1.5f, Ui.px(2.5f)));
        cardLine.setColor(Ui.alpha(t.a, 0.6f));
        cardLine.setStrokeWidth(Math.max(1f, Ui.px(2f)));
        gen++;
        if (pw > 0) measurePanel();
        invalidate();
    }

    void setGamepad(boolean g, int st) {
        gamepad = g;
        style = st;
        invalidate();
    }

    // ---------------------------------------------------------------- 2.3 animation setup

    /** Text animation for this screen and for its confirmation prompts, the shared tick budget and the look. */
    void anim(TextReveal.Clock clock, TickScheduler ticks, Fx f, TextReveal.Params text, TextReveal.Params confirm, boolean glassStyle, boolean rightToLeft) {
        fx = f;
        this.clock = clock;
        textP.set(text);
        confirmP.set(confirm);
        reveal = new TextReveal(clock, this);
        reveal.share(ticks);
        confirmRev = new TextReveal(clock, this);
        confirmRev.share(ticks);
        helpRev = new TextReveal(clock, this);
        helpRev.share(ticks);
        style(glassStyle, rightToLeft);
    }

    /**
     * 2.5: the next text reveal of this panel shows its text at once, and the one after that is
     * the user's real animation again. HomeActivity calls this when it rebuilds the same screen
     * after an action. (2.4.1 changed the panel's own parameters instead, and the panel stayed on
     * NONE for every later tab switch.)
     */
    void quietNext() { plan.quietNext(); }

    /** Reduce effects: the help line stays still and is cut with dots instead of scrolling. */
    void setReduce(boolean r) {
        if (reduce == r) return;
        reduce = r;
        lastMq = Integer.MIN_VALUE;
        invalidate();
    }

    /** PANEL STYLE and TYPING DIRECTION, also changed live from the ANIMATIONS screen. */
    void style(boolean glassStyle, boolean rightToLeft) {
        glass = glassStyle;
        rtl = rightToLeft;
        textP.rtl = rtl;
        confirmP.rtl = rtl;
        themeChanged();
    }

    /** Options flyout layout: the item's icon card glides from `from` (root px) to the dock spot. */
    void flyout(RectF from, IconCache.Icon ic, String itemLabel, boolean rightSide) {
        flyout = true;
        flyFrom.set(from);
        icon = ic;
        iconLabel = itemLabel == null ? "" : itemLabel.toUpperCase(Locale.ROOT);
        flyRight = rightSide;
        pw = 0;
    }

    void setIcon(IconCache.Icon ic) { icon = ic; invalidate(); }

    /** Starts the open: flyout motion (if any) and the text, delayed until the panel is out. */
    void startOpen(long now, boolean docked) {
        if (pw == 0) measurePanel();
        if (flyout) fa.open(now, docked);
        boolean quiet = plan.quietPending();
        startReveal(true, flyout ? fa.textDelay() : 0);
        helpSync(true);
        if (quiet && helpRev != null) helpRev.finish();   // a refresh does not replay the help line either
    }

    /** Flyout close: the text clears, the panel retracts, the icon glides back. */
    void startClose(long now) {
        if (confirmRev != null) confirmRev.finish();
        if (flyout) {
            fa.close(now);
            if (reveal != null) {
                if (reveal.running()) reveal.finish();
                reveal.clear(fa.clearMs());
            }
        }
        invalidate();
    }

    private void startReveal(boolean withTitle, long delay) {
        if (reveal == null) return;
        if (pw == 0) measurePanel();
        revealTitle = withTitle;
        revealScroll = scroll;
        int k = 0;
        if (withTitle) lens[k++] = titleP.n;
        ArrayList<Row> rs = rows();
        int end = Math.min(rs.size(), scroll + VIS);
        for (int i = scroll; i < end; i++) {
            Row r = rs.get(i);
            prep(r, i == focus);
            lens[k++] = r.units();
        }
        plan.next(reveal.p);
        reveal.startLengths(lens, k, delay);
    }

    private void startConfirm(Row r) {
        if (confirmRev == null) return;
        prep(r, true);
        lens[0] = r.units();
        confirmRow = r;
        confirmRev.p.set(confirmP);
        confirmRev.startLengths(lens, 1, 0);
    }

    /** One frame of animation. @return true while something still moves. */
    boolean stepAnim(long now) {
        boolean moved = false;
        if (reveal != null) moved |= reveal.step();
        if (confirmRev != null) moved |= confirmRev.step();
        if (helpRev != null) moved |= helpRev.step();
        if (flyout) moved |= fa.step(now);
        if (marqueeLive()) {
            if (mq.moving(now)) {
                int o = Math.round(mq.offset(now));
                if (o != lastMq) { lastMq = o; moved = true; }
            } else {
                scheduleMarquee(now);
            }
        }
        if (moved) invalidate();
        return animating();
    }

    /** 2.5: true while the engine should keep sending frames for this panel (animations or a scrolling help line). */
    boolean wantsFrames(long now) {
        return animating() || (marqueeLive() && mq.moving(now)) || (helpRev != null && helpRev.running());
    }

    /** The help line scrolls only while it is too long, shown, not closing and Reduce effects is off. */
    private boolean marqueeLive() {
        return helpOn && !reduce && !closing && mq.scrolls() && !helpFull.isEmpty() && isShown() && getWindowVisibility() == VISIBLE;
    }

    /** During the rest at the start of a cycle no frames run; a timer wakes the loop when the slide begins. */
    private void scheduleMarquee(long now) {
        removeCallbacks(mqWake);
        long left = mq.pauseLeft(now);
        if (left > 0) postDelayed(mqWake, left + 4); else if (clock != null) clock.wake();
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(mqWake);
        super.onDetachedFromWindow();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE && helpOn && clock != null) {
            mq.restart(clock.now());
            lastMq = Integer.MIN_VALUE;
            if (marqueeLive()) scheduleMarquee(clock.now());
        } else {
            removeCallbacks(mqWake);
        }
    }

    boolean animating() {
        return (reveal != null && reveal.running()) || (confirmRev != null && confirmRev.running()) || (flyout && fa.running);
    }

    /** Interrupt: everything jumps to its end. @return true if something was running. */
    boolean finishAnim() {
        boolean any = false;
        if (reveal != null) any |= reveal.finish();
        if (confirmRev != null) any |= confirmRev.finish();
        if (helpRev != null) helpRev.finish();   // the help line never holds an input back
        if (flyout) any |= fa.finish();
        if (any) invalidate();
        return any;
    }

    /** False while the flyout is still growing: the reticle waits. */
    boolean frameReady() { return !flyout || fa.ready(); }

    /** 0..1: how much the home screen behind is dimmed and pushed back. */
    float dim() { return flyout ? fa.dim : 1f; }

    /** 0..1: how far the icon has left its tile (the real tile fades by this much). */
    float away() { return flyout && fa.p.glide ? fa.glide : 0f; }

    boolean gone() { return flyout && fa.gone(); }

    @Override public void onRevealTick(float rate) { if (fx != null) fx.typeTick(rate); }

    @Override public void onRevealFrame() { invalidate(); }

    @Override public void onRevealDone() { invalidate(); }

    // ---------------------------------------------------------------- 2.5 row help

    /** The sentence for what has focus now: a row's help, or the tab's help while the tab strip has focus. */
    private String helpFor() {
        if (!helpOn) return "";
        String s;
        if (focus < 0 && hasTabs()) {
            s = RowHelp.tab(tabNames.get(Math.max(0, Math.min(tab, tabNames.size() - 1))));
        } else {
            Row r = current();
            s = r == null ? null : RowHelp.of(kind, r.id != null ? r.id : r.label);
        }
        return s == null ? "" : s.toUpperCase(Locale.ROOT);
    }

    /** Call after anything that can move focus or change the tab. A new sentence fades in and the marquee restarts. */
    private void helpSync(boolean force) {
        if (!helpOn) return;
        String s = helpFor();
        if (!force && s.equals(helpFull)) return;
        helpFull = s;
        helpLayout();
        long now = clock == null ? 0L : clock.now();
        mq.restart(now);
        lastMq = Integer.MIN_VALUE;
        if (helpRev != null && pw > 0 && !s.isEmpty()) {
            RevealPlan.helpParams(textP, helpRev.p);
            helpLens[0] = s.length();
            helpRev.startLengths(helpLens, 1, 0);
        }
        if (marqueeLive()) scheduleMarquee(now);
        invalidate();
    }

    /** Measures the sentence against the footer width. Needs the panel size. */
    private void helpLayout() {
        if (!helpOn || pw <= 0) return;
        helpViewW = pw - Ui.px(88);
        helpTextW = helpFull.isEmpty() ? 0f : small.measureText(helpFull);
        helpCut = RowFit.ellipsize(helpFull, helpViewW, mSmall);
        mq.set(helpTextW, helpViewW, Ui.px(90), Ui.px(Marquee.DEFAULT_SPEED), Marquee.DEFAULT_PAUSE_MS);
    }

    /** Draws the help sentence on the footer's first line (baseline y), clipped to the footer width. */
    private void drawHelp(Canvas c, float x0, float y, Ui.Theme t, long now) {
        if (helpFull.isEmpty()) return;
        float a = helpRev == null || pw <= 0 ? 1f : helpRev.alpha(0);
        if (a <= 0.001f) return;
        small.setColor(Ui.alpha(t.text, 0.86f * a));
        if (reduce || !mq.scrolls()) {
            c.drawText(reduce ? helpCut : helpFull, x0, y, small);
        } else {
            c.save();
            c.clipRect(x0, y - Ui.px(24), x0 + helpViewW, y + Ui.px(8));
            float off = mq.offset(now);
            c.drawText(helpFull, x0 - off, y, small);
            if (mq.showsSecond(now)) c.drawText(helpFull, x0 - off + mq.cycle(), y, small);
            c.restore();
        }
        small.setColor(t.dim);
    }

    // ---------------------------------------------------------------- rows and tabs

    void tab(String name) {
        tabs.add(new ArrayList<Row>());
        tabNames.add(name == null ? "" : name.toUpperCase(Locale.ROOT));
    }

    Row add(Row r) {
        if (tabs.isEmpty()) tab("");
        tabs.get(tabs.size() - 1).add(r);
        return r;
    }

    boolean hasTabs() { return tabs.size() > 1; }

    ArrayList<Row> rows() { return tabs.isEmpty() ? NONE : tabs.get(Math.max(0, Math.min(tab, tabs.size() - 1))); }

    /** Replace the rows of one tab (used when a background scan finishes). The new rows type in. */
    void setRows(int tabIdx, List<Row> list) {
        if (tabs.isEmpty()) tab("");
        ArrayList<Row> r = tabs.get(Math.max(0, Math.min(tabIdx, tabs.size() - 1)));
        r.clear();
        r.addAll(list);
        if (focus >= r.size()) focus = Math.max(0, r.size() - 1);
        ensureVisible();
        measurePanel();
        if (reveal != null) { reveal.finish(); if (!closing) startReveal(false, 0); }
        helpSync(false);
        invalidate();
    }

    void select(int tabIdx, int row) {
        if (tabs.isEmpty()) return;
        tab = Math.max(0, Math.min(tabIdx, tabs.size() - 1));
        focus = Math.max(hasTabs() ? -1 : 0, Math.min(row, rows().size() - 1));
        ensureVisible();
        helpSync(false);
    }

    /** Select the row with this label (labels are upper case), so row inserts do not break returns. */
    void selectLabel(int tabIdx, String rowLabel) {
        if (tabs.isEmpty()) return;
        int t = Math.max(0, Math.min(tabIdx, tabs.size() - 1));
        int row = hasTabs() ? -1 : 0;
        if (rowLabel != null) {
            String want = rowLabel.toUpperCase(Locale.ROOT);
            ArrayList<Row> r = tabs.get(t);
            for (int i = 0; i < r.size(); i++) if (r.get(i).label.equals(want)) { row = i; break; }
        }
        select(t, row);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) { measurePanel(); }

    private void measurePanel() {
        int most = 1;
        for (ArrayList<Row> r : tabs) most = Math.max(most, Math.min(VIS, r.size()));
        tabH = hasTabs() ? Ui.px(66) : 0f;
        float footH = Ui.px(70);
        // 2.5: one more footer line for the row help sentence
        float helpH = helpOn ? Ui.px(HELP_LINE) : 0f;
        if (flyout) {
            pw = Ui.px(760);
            rowH = Ui.px(64);
            headH = Ui.px(96);
            footH = Ui.px(62);
            ph = headH + tabH + most * rowH + footH + helpH;
            float cw = Ui.px(300), chh = Ui.px(169), cy = Ui.H / 2f;
            float dl = flyRight ? Ui.px(1810) - cw : Ui.px(110);
            dockR.set(dl, cy - chh / 2f, dl + cw, cy + chh / 2f);
            px = flyRight ? dockR.left - Ui.px(40) - pw : dockR.right + Ui.px(40);
            py = Math.max(Ui.px(50), Math.min(Ui.H - Ui.px(50) - ph, cy - ph / 2f));
            cardText.setTextSize(chh * 0.12f);
            iconShown = TextUtils.ellipsize(iconLabel, cardText, cw * 0.84f, TextUtils.TruncateAt.END);
        } else {
            // 2.6: eight tabs need more room for the tab names (ANIMATIONS is the longest), so the panel is a bit wider then
            pw = Ui.px(tabs.size() > 7 ? 1240 : 1120);
            rowH = Ui.px(70);
            headH = Ui.px(108);
            ph = headH + tabH + most * rowH + footH + helpH;
            px = (Ui.W - pw) / 2f;
            py = Math.max(Ui.px(50), (Ui.H - ph) / 2f);
        }
        rowW = pw - Ui.px(56);
        Ui.cut(shape, px, py, px + pw, py + ph, Ui.px(34), Ui.px(10));
        float in = Ui.px(7);
        Ui.cut(inner, px + in, py + in, px + pw - in, py + ph - in, Ui.px(30), Ui.px(8));
        // the tab strip: shrink the caps text if the longest name does not fit its tab
        if (hasTabs()) {
            tabText.setTextSize(Ui.px(16));
            float tw = (pw - Ui.px(88) - Ui.px(10) * (tabs.size() - 1)) / tabs.size() - Ui.px(14), most2 = 0f;
            for (String n : tabNames) most2 = Math.max(most2, tabText.measureText(n));
            if (most2 > tw && most2 > 0) tabText.setTextSize(Ui.px(16) * Math.max(0.68f, tw / most2));
        }
        if (tabW.length < tabNames.size()) tabW = new float[tabNames.size()];
        for (int i = 0; i < tabNames.size(); i++) tabW[i] = tabText.measureText(tabNames.get(i));
        String bk = onBack != null ? "BACK TO RETURN" : "BACK TO CLOSE";
        backP.set(bk, small);
        slashW = head.measureText("// ");
        titleP.set(TextUtils.ellipsize(title, head, flyout ? pw - Ui.px(88) - slashW - backP.w - Ui.px(24) : pw * 0.6f, TextUtils.TruncateAt.END), head);
        gen++;
        helpLayout();
    }

    private void tabRect(int i, RectF out) {
        float x0 = px + Ui.px(44), y0 = py + headH;
        float w = (pw - Ui.px(88) - Ui.px(10) * (tabs.size() - 1)) / Math.max(1, tabs.size());
        out.set(x0 + i * (w + Ui.px(10)), y0, x0 + i * (w + Ui.px(10)) + w, y0 + Ui.px(46));
    }

    /** Focus rectangle in this view's coordinates (which match the root). */
    void rowRect(int i, RectF out) {
        if (pw == 0) measurePanel();
        if (i < 0 && hasTabs()) { tabRect(tab, out); return; }
        int v = Math.max(0, i) - scroll;
        float top = py + headH + tabH + v * rowH;
        out.set(px + Ui.px(28), top + Ui.px(6), px + pw - Ui.px(28), top + rowH - Ui.px(6));
    }

    /** Pointer hit test. @return row index, -2 for a tab (tab index in hitTab), -3 for none. */
    int hitTab = -1;
    int hit(float x, float y) {
        if (pw == 0) measurePanel();
        if (hasTabs()) {
            for (int i = 0; i < tabs.size(); i++) {
                tabRect(i, tmp);
                if (tmp.contains(x, y)) { hitTab = i; return -2; }
            }
        }
        int end = Math.min(rows().size(), scroll + VIS);
        for (int i = scroll; i < end; i++) {
            rowRect(i, tmp);
            if (tmp.contains(x, y)) return i;
        }
        return -3;
    }

    boolean focusRow(int i) {
        if (i == focus || i < 0 || i >= rows().size()) return false;
        disarm();
        focus = i;
        helpSync(false);
        invalidate();
        return true;
    }

    boolean selectTab(int t) {
        if (!hasTabs() || t == tab || t < 0 || t >= tabs.size()) return false;
        disarm();
        tab = t;
        focus = -1;
        scroll = 0;
        startReveal(false, 0);
        helpSync(false);
        invalidate();
        return true;
    }

    /** Pointer wheel scroll inside a long list. */
    boolean scrollBy(int d) {
        int n = rows().size(), ns = Math.max(0, Math.min(scroll + d, Math.max(0, n - VIS)));
        if (ns == scroll) return false;
        scroll = ns;
        if (focus >= 0 && (focus < scroll || focus >= scroll + VIS)) focus = Math.max(scroll, Math.min(focus, scroll + VIS - 1));
        helpSync(false);
        invalidate();
        return true;
    }

    Row current() {
        ArrayList<Row> r = rows();
        return focus >= 0 && focus < r.size() ? r.get(focus) : null;
    }

    private void ensureVisible() {
        if (focus < 0) return;
        if (focus < scroll) scroll = focus;
        if (focus >= scroll + VIS) scroll = focus - VIS + 1;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows().size() - VIS)));
    }

    boolean move(int d) {
        int n = rows().size();
        if (focus == -1) {
            if (d > 0 && n > 0) { focus = 0; ensureVisible(); helpSync(false); invalidate(); return true; }
            return false;
        }
        int nf = focus + d;
        if (nf < 0) {
            if (hasTabs()) { disarm(); focus = -1; helpSync(false); invalidate(); return true; }
            return false;
        }
        if (nf >= n) return false;
        disarm();
        focus = nf;
        ensureVisible();
        helpSync(false);
        invalidate();
        return true;
    }

    private void disarm() {
        Row r = current();
        if (r != null) r.armed = false;
        if (confirmRev != null) confirmRev.finish();
        confirmRow = null;
    }

    boolean switchTab(int d) {
        if (!hasTabs()) return false;
        disarm();
        tab = (tab + d + tabs.size()) % tabs.size();
        if (focus != -1) focus = 0;
        scroll = 0;
        startReveal(false, 0);
        helpSync(false);
        invalidate();
        return true;
    }

    /** Left or right: tab strip, value rows and sliders. */
    boolean change(int d) {
        if (focus == -1) return switchTab(d);
        Row r = current();
        if (r == null || r.disabled) return false;
        if (r.slider) {
            int nv = Math.max(r.min, Math.min(r.max, r.val + d * r.step));
            if (nv == r.val) return false;
            r.val = nv;
        } else if (r.values != null && r.values.length >= 2) {
            r.sel = (r.sel + d + r.values.length) % r.values.length;
        } else {
            return false;
        }
        invalidate();
        if (r.onChange != null) r.onChange.run();
        return true;
    }

    /** @return true if something happened. */
    boolean ok() {
        Row r = current();
        if (r == null || r.disabled) return false;
        if (r.slider) return false;
        if (r.values != null) return change(1);
        if (r.onOk == null) return false;
        if (r.confirm != null && !r.armed) { r.armed = true; startConfirm(r); invalidate(); return true; }
        r.armed = false;
        confirmRow = null;
        invalidate();
        r.onOk.run();
        return true;
    }

    // ---------------------------------------------------------------- drawing

    /** Builds the row's text pieces when what it shows changed (focus, value, info, theme). */
    private void prep(Row r, boolean f) {
        if (r.kGen == gen && r.kF == f && r.kArmed == r.armed && r.kDis == r.disabled && r.kSel == r.sel && r.kVal == r.val
                && r.kLabel == r.label && r.kInfo == r.info && r.kSw == (r.swatchColor != 0)) return;
        r.kGen = gen; r.kF = f; r.kArmed = r.armed; r.kDis = r.disabled; r.kSel = r.sel; r.kVal = r.val; r.kLabel = r.label; r.kInfo = r.info;
        r.kSw = r.swatchColor != 0;
        float w = rowW > 0 ? rowW : Ui.px(1064);
        r.mark = null;
        r.markW = 0f;
        if (r.two) {
            if (!r.disabled && f && r.onOk != null) { r.mark = "[ OK ]"; r.markW = value.measureText(r.mark) + Ui.px(16); }
            r.pa.set(RowFit.ellipsize(r.label, w - Ui.px(40), mSmall), small);
            String body = r.armed && r.confirm != null ? r.confirm : (r.info == null ? "" : r.info);
            r.pb.set(RowFit.ellipsize(body, w - Ui.px(40) - r.markW, mInfo), infoP);
            return;
        }
        String lt = r.armed && r.confirm != null ? r.confirm : r.label;
        float usable = w - Ui.px(40);
        float labelW = label.measureText(lt), valueW = 0f, extra = 0f, minValue = 0f, reserve = 0f;
        float decor = 0f;
        String vt = "";
        if (r.slider) {
            vt = String.valueOf(r.val);
            valueW = value.measureText(vt);
            extra = Ui.px(250) + Ui.px(18) + (r.swatchColor != 0 ? Ui.px(40) : 0f);
            minValue = valueW;
        } else if (r.values != null) {
            vt = r.values[Math.max(0, Math.min(r.sel, r.values.length - 1))];
            // the arrows' room counts focused or not, so a label keeps its length when focus lands on it
            decor = value.measureText("<  ") + value.measureText("  >");
            valueW = decor + value.measureText(vt);
            extra = r.swatch ? Ui.px(22) * 2.6f : 0f;
            minValue = decor + value.measureText(RowFit.DOTS);
        } else {
            if (!r.disabled && (r.sub || (f && r.onOk != null))) {
                r.mark = r.sub ? ">" : "[ OK ]";
                r.markW = value.measureText(r.mark) + Ui.px(16);
            }
            vt = r.info == null ? "" : r.info;
            valueW = vt.isEmpty() ? 0f : infoP.measureText(vt);
            reserve = r.markW;
            minValue = Math.min(valueW, infoP.measureText(RowFit.DOTS));
        }
        RowFit.split(usable, reserve, labelW, valueW, extra, minValue, Ui.px(24), Ui.px(120), split);
        r.pa.set(RowFit.ellipsize(lt, split.labelMax, mLabel), label);
        if (r.slider) {
            r.pb.set(vt, value);
        } else if (r.values != null) {
            String v = RowFit.ellipsize(vt, split.valueMax - decor, mValue);
            r.pb.set(f && !r.disabled ? "<  " + v + "  >" : v, value);
        } else {
            r.pb.set(RowFit.ellipsize(vt, split.valueMax, mInfo), infoP);
        }
    }

    /** x of a piece laid out left to right at x0, mirrored inside the row for right to left. */
    private float mx(float x0, float w) { return rtl ? rL + rR - x0 - w : x0; }

    private void mrect(Canvas c, float l, float t, float r, float b, Paint p) {
        if (rtl) c.drawRect(rL + rR - r, t, rL + rR - l, b, p); else c.drawRect(l, t, r, b, p);
    }

    private int ca(int color) { return la >= 0.999f ? color : Ui.alpha(color, la); }

    private void tint(Paint p, int color) { p.setColor(ca(color)); }

    private int lineFor(int i) {
        int v = i - revealScroll;
        if (v < 0 || v >= VIS) return -1;
        return (revealTitle ? 1 : 0) + v;
    }

    private float hintPad(Canvas c, float x, float y, int btn, String word) {
        float r = Ui.px(13);
        x += Glyphs.draw(c, style, btn, true, x, y - Ui.px(6), r, glyph, small) + Ui.px(8);
        c.drawText(word, x, y, small);
        return x + small.measureText(word) + Ui.px(30);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (pw == 0) measurePanel();
        Ui.Theme t = Ui.T;
        if (!flyout) {
            c.drawRect(0, 0, getWidth(), getHeight(), scrim);
            c.drawPath(shape, fill);
            if (glass) c.drawPath(inner, rule);
            c.drawPath(shape, edge);
            content(c, t);
            return;
        }
        // options flyout: dim, the icon card on its way to the dock, the panel growing out of it
        float dimK = fa.dim;
        scrim.setColor(Ui.alpha(t.bg0, (glass ? 0.55f : 0.72f) * dimK));
        c.drawRect(0, 0, getWidth(), getHeight(), scrim);
        drawCard(c, t);
        float g = fa.grow;
        if (g <= 0.001f) return;
        float l = flyRight ? px + pw * (1f - g) : px, r = flyRight ? px + pw : px + pw * g;
        Ui.cut(shape, l, py, r, py + ph, Ui.px(34), Ui.px(10));
        c.drawPath(shape, fill);
        if (glass && g >= 1f) c.drawPath(inner, rule);
        c.drawPath(shape, edge);
        // tether from the card to the panel
        float ty = dockR.centerY(), tx0 = flyRight ? dockR.left : dockR.right, tx1 = flyRight ? r : l;
        c.drawLine(tx0, ty, tx1, ty, cardLine);
        c.drawRect(tx1 - Ui.px(4), ty - Ui.px(4), tx1 + Ui.px(4), ty + Ui.px(4), cursorP);
        brackets(c, l - Ui.px(8), py - Ui.px(8), r + Ui.px(8), py + ph + Ui.px(8));
        c.save();
        c.clipRect(l, py, r, py + ph);
        content(c, t);
        c.restore();
    }

    private void brackets(Canvas c, float l, float tp, float r, float b) {
        float a = Ui.px(22);
        c.drawLine(l, tp, l + a, tp, bracket);
        c.drawLine(l, tp, l, tp + a, bracket);
        c.drawLine(r, tp, r - a, tp, bracket);
        c.drawLine(r, tp, r, tp + a, bracket);
        c.drawLine(l, b, l + a, b, bracket);
        c.drawLine(l, b, l, b - a, bracket);
        c.drawLine(r, b, r - a, b, bracket);
        c.drawLine(r, b, r, b - a, bracket);
    }

    /** The flying icon card: TileView's look at any size, no shaders, nothing allocated. */
    private void drawCard(Canvas c, Ui.Theme t) {
        float a = fa.iconAlpha;
        if (a <= 0.01f) return;
        float g = fa.glide;
        cardR.set(flyFrom.left + (dockR.left - flyFrom.left) * g, flyFrom.top + (dockR.top - flyFrom.top) * g,
                flyFrom.right + (dockR.right - flyFrom.right) * g, flyFrom.bottom + (dockR.bottom - flyFrom.bottom) * g);
        float w = cardR.width(), h = cardR.height();
        if (w < 2 || h < 2) return;
        Ui.cut(card, cardR.left, cardR.top, cardR.right, cardR.bottom, h * TileView.CUT_BIG, h * TileView.CUT_SMALL);
        int al = Math.round(255 * a);
        boolean banner = icon != null && icon.banner && icon.bmp != null;
        bmp.setAlpha(al);
        if (banner) {
            c.drawBitmap(icon.bmp, null, cardR, bmp);
        } else {
            int base = icon != null && icon.bmp != null ? icon.color : t.b;
            plate.setColor(Ui.alpha(Ui.mix(t.panel, base, 0.30f), a));
            c.drawPath(card, plate);
            if (icon != null && icon.bmp != null) {
                float s = h * 0.44f, top = h * 0.13f, lft = cardR.left + (w - s) / 2f;
                tmp.set(lft, cardR.top + top, lft + s, cardR.top + top + s);
                c.drawBitmap(icon.bmp, null, tmp, bmp);
            }
            cardText.setTextSize(h * 0.12f);
            cardText.setColor(Ui.alpha(t.text, a));
            c.drawText(iconShown, 0, iconShown.length(), cardR.centerX(), cardR.top + h * (icon != null && icon.bmp != null ? 0.83f : 0.56f), cardText);
        }
        cardLine.setAlpha(Math.round(al * 0.6f));
        c.drawPath(card, cardLine);
        cardLine.setColor(Ui.alpha(t.a, 0.6f));
    }

    /** Header, tabs, rows and footer. Shared by the centered panel and the flyout. */
    private void content(Canvas c, Ui.Theme t) {
        float x0 = px + Ui.px(44), x1 = px + pw - Ui.px(44);
        float hy = py + Ui.px(flyout ? 62 : 68);
        rL = x0;
        rR = x1;
        // title, line 0 of the reveal
        int tl = revealTitle && reveal != null ? 0 : -1;
        la = reveal == null || tl < 0 ? 1f : reveal.alpha(tl);
        int tk = reveal == null || tl < 0 ? titleP.n : Math.min(titleP.n, reveal.visible(tl, titleP.n));
        float slash = slashW;
        float sl = reveal == null || tl < 0 ? 0f : reveal.slide(tl) * Ui.u;
        float tx = rtl ? x1 - titleP.w : x0 + slash;
        head.setColor(ca(t.a));
        c.drawText("//", (rtl ? tx - slash : x0) + sl, hy, head);
        head.setColor(ca(t.text));
        float hx = TypeDraw.draw(c, titleP, tk, tx + sl, hy, head, rtl);
        if (reveal != null && tl >= 0 && reveal.cursor(tl)) TypeDraw.cursor(c, hx, hy, head, cursorP, rtl);
        la = 1f;
        small.setColor(t.dim);
        c.drawText(backP.s, rtl ? x0 : x1 - backP.w, hy - Ui.px(4), small);
        c.drawLine(x0, py + headH - Ui.px(14), x1, py + headH - Ui.px(14), rule);

        RectF rr = tmp;
        if (hasTabs()) {
            for (int i = 0; i < tabs.size(); i++) {
                tabRect(i, rr);
                Ui.cut(tabShape, rr.left, rr.top, rr.right, rr.bottom, Ui.px(12), Ui.px(4));
                boolean cur = i == tab;
                if (cur) c.drawPath(tabShape, tabFill); else c.drawPath(tabShape, tabLine);
                tabText.setColor(cur ? t.bg0 : t.dim);
                String n = tabNames.get(i);
                c.drawText(n, rr.centerX() - tabW[i] / 2f, rr.centerY() - (tabText.ascent() + tabText.descent()) / 2f, tabText);
            }
        }

        ArrayList<Row> rows = rows();
        int end = Math.min(rows.size(), scroll + VIS);
        for (int i = scroll; i < end; i++) {
            Row r = rows.get(i);
            rowRect(i, rr);
            boolean f = i == focus;
            prep(r, f);
            // this row's animation state
            TextReveal src = reveal;
            int li = lineFor(i);
            if (r == confirmRow && confirmRev != null && confirmRev.running()) { src = confirmRev; li = 0; }
            int units = r.units();
            int k = src == null ? units : Math.min(units, src.visible(li, units));
            la = src == null ? 1f : src.alpha(li);
            float slide = src == null ? 0f : src.slide(li) * Ui.u;
            boolean cur = src != null && src.cursor(li);
            if (la > 0.001f) {
                if (slide != 0f) { c.save(); c.translate(slide, 0); }
                rL = rr.left + Ui.px(20);
                rR = rr.right - Ui.px(20);
                if (r.two) drawTwo(c, r, rr, f, t, k, cur);
                else drawRow(c, r, rr, f, t, k, cur);
                if (slide != 0f) c.restore();
            }
            la = 1f;
            if (i < end - 1) c.drawLine(rr.left, rr.bottom + Ui.px(6), rr.right, rr.bottom + Ui.px(6), rule);
        }
        if (rows.size() > VIS) {
            rowRect(scroll, rr);
            float top = rr.top, h = VIS * rowH - Ui.px(12);
            float th = h * VIS / rows.size(), ty = top + (h - th) * scroll / Math.max(1, rows.size() - VIS);
            c.drawRect(px + pw - Ui.px(18), top, px + pw - Ui.px(14), top + h, barOff);
            c.drawRect(px + pw - Ui.px(18), ty, px + pw - Ui.px(14), ty + th, bar);
        }

        float fy = py + ph - Ui.px(28);
        if (helpOn) drawHelp(c, x0, fy - Ui.px(HELP_LINE), t, clock == null ? 0L : clock.now());
        small.setColor(t.dim);
        if (gamepad) {
            float x = x0;
            x = hintPad(c, x, fy, Glyphs.A, "SELECT");
            x = hintPad(c, x, fy, Glyphs.B, "BACK");
            if (hasTabs()) {
                x += Glyphs.draw(c, style, Glyphs.LB, true, x, fy - Ui.px(6), Ui.px(13), glyph, small) + Ui.px(6);
                hintPad(c, x, fy, Glyphs.RB, "TABS");
            }
        } else {
            String hint = hasTabs() ? "UP DOWN  SELECT    LEFT RIGHT  CHANGE    UP TOP  TABS    OK  APPLY"
                    : "UP DOWN  SELECT    LEFT RIGHT  CHANGE    OK  APPLY";
            c.drawText(hint, x0, fy, small);
        }
    }

    /** The two pieces of a row type in order: label first, then the value side. */
    private void pieces(Canvas c, Row r, int k, float ax, float ay, Paint ap, float bx, float by, Paint bp, boolean cur) {
        int ka = Math.min(k, r.pa.n), kb = Math.max(0, Math.min(r.pb.n, k - r.pa.n));
        float ha = TypeDraw.draw(c, r.pa, ka, mx(ax, r.pa.w), ay, ap, rtl);
        float hb = TypeDraw.draw(c, r.pb, kb, mx(bx, r.pb.w), by, bp, rtl);
        if (!cur) return;
        if (ka < r.pa.n || r.pb.n == 0) TypeDraw.cursor(c, ha, ay, ap, cursorP, rtl);
        else TypeDraw.cursor(c, hb, by, bp, cursorP, rtl);
    }

    private void drawTwo(Canvas c, Row r, RectF rr, boolean f, Ui.Theme t, int k, boolean cur) {
        float x = rr.left + Ui.px(20);
        boolean done = k >= r.units();
        tint(small, f ? t.a : t.dim);
        tint(infoP, r.disabled ? Ui.alpha(t.dim, 0.6f) : (f ? t.text : Ui.alpha(t.text, 0.82f)));
        if (r.mark != null && done) {
            tint(value, t.a);
            float mw = r.markW - Ui.px(16);
            c.drawText(r.mark, mx(rr.right - Ui.px(20) - mw, mw), rr.top + Ui.px(50), value);
        }
        pieces(c, r, k, x, rr.top + Ui.px(22), small, x, rr.top + Ui.px(50), infoP, cur);
        small.setColor(t.dim);
        infoP.setColor(t.dim);
        value.setColor(t.a);
    }

    private void drawRow(Canvas c, Row r, RectF rr, boolean f, Ui.Theme t, int k, boolean cur) {
        float base = rr.centerY() - (label.ascent() + label.descent()) / 2f;
        int lc = r.danger ? Ui.DANGER : t.text;
        int labelColor = f ? lc : Ui.alpha(lc, 0.74f);
        if (r.disabled) labelColor = Ui.alpha(t.dim, f ? 0.75f : 0.5f);
        if (r.armed) labelColor = Ui.DANGER;
        tint(label, labelColor);
        float vy = rr.centerY() - (value.ascent() + value.descent()) / 2f;
        float right = rr.right - Ui.px(20), lx = rr.left + Ui.px(20);
        int units = r.units();
        boolean done = k >= units;
        float frac = units == 0 ? 1f : k / (float) units;
        if (r.slider) {
            tint(value, f ? t.a : t.dim);
            float vw = r.pb.w;
            int segs = 18;
            float bw = Ui.px(250), seg = bw / segs, bx = right - vw - Ui.px(18) - bw, by = rr.centerY() - Ui.px(8);
            float fr = (r.val - r.min) / (float) Math.max(1, r.max - r.min);
            int on = Math.round(fr * segs);
            float keep = la;
            la = keep * frac;
            tint(bar, t.a);
            tint(barOff, Ui.alpha(t.dim, 0.25f));
            for (int s = 0; s < segs; s++) mrect(c, bx + s * seg, by, bx + (s + 1) * seg - Ui.px(3), by + Ui.px(16), s < on ? bar : barOff);
            if (r.swatchColor != 0) {
                tint(sw, r.swatchColor);
                mrect(c, bx - Ui.px(40), by - Ui.px(3), bx - Ui.px(18), by + Ui.px(19), sw);
            }
            la = keep;
            bar.setColor(t.a);
            barOff.setColor(Ui.alpha(t.dim, 0.25f));
            pieces(c, r, k, lx, base, label, right - vw, vy, value, cur);
        } else if (r.values != null) {
            tint(value, f && !r.disabled ? t.a : Ui.alpha(t.dim, r.disabled ? 0.6f : 1f));
            float vw = r.pb.w;
            if (r.swatch && r.sel >= 0 && r.sel < Ui.THEMES.length && Ui.THEMES[r.sel] != null && done) {
                float sz = Ui.px(22), sy = rr.centerY() - sz / 2f, vx = right - vw;
                tint(sw, Ui.THEMES[r.sel].a);
                mrect(c, vx - sz * 2.6f, sy, vx - sz * 1.6f, sy + sz, sw);
                tint(sw, Ui.THEMES[r.sel].b);
                mrect(c, vx - sz * 1.4f, sy, vx - sz * 0.4f, sy + sz, sw);
            }
            pieces(c, r, k, lx, base, label, right - vw, vy, value, cur);
        } else {
            if (r.mark != null && done) {
                tint(value, t.a);
                float mw = r.markW - Ui.px(16);
                c.drawText(r.mark, mx(right - mw, mw), vy, value);
            }
            tint(infoP, t.dim);
            float iy = rr.centerY() - (infoP.ascent() + infoP.descent()) / 2f;
            pieces(c, r, k, lx, base, label, right - r.markW - r.pb.w, iy, infoP, cur);
        }
        label.setColor(t.text);
        value.setColor(t.a);
        infoP.setColor(t.dim);
    }
}
