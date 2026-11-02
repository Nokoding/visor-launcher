package com.nokoding.deck;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Setup > REMAP > LEARN A BUTTON. Press any button on the remote: the screen shows its name, key
 * code, scan code, device and source, and two flags: SEEN BY DECK (the key reached Deck's window)
 * and SEEN BY THE OVERLAY SERVICE (the key reached the accessibility key filter). Then ASSIGN AN
 * ACTION opens that button's page. Works for buttons this version does not know: a button with
 * key code 0 is named by scan code and device.
 *
 * While it is open the overlay service only reports what it sees and takes nothing, so every
 * button reaches Deck as it would without a remap. Nothing is remapped while this screen is up.
 * The D-pad, OK and Back drive the top buttons here, they are in ADVANCED REMAP.
 * No animation: it redraws when a key comes, so REDUCE EFFECTS needs nothing special.
 */
@android.annotation.SuppressLint("ViewConstructor")
final class RemapLearnView extends TestScreen {

    interface Assign { void assign(long key, String label); }

    /** The view that is open now, for PsService to report what the key filter saw. Main thread only. */
    static volatile RemapLearnView learning;

    private static final int C_CLEAR = 0, C_ASSIGN = 1, C_EXIT = 2;

    final RemapLearn learn = new RemapLearn();
    private final Assign assign;
    private final boolean advancedOn;
    private String note = "";
    private ArrayList<String> verdictLines = new ArrayList<>();

    RemapLearnView(Context c, Host host, boolean reduce, boolean advancedOn, Assign assign) {
        super(c, host, reduce, true);
        this.assign = assign;
        this.advancedOn = advancedOn;
        setChips(new String[]{"CLEAR", "ASSIGN AN ACTION", "EXIT"}, new float[]{150, 330, 120});
        rebuild();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        learning = this;
    }

    @Override
    protected void onDetachedFromWindow() {
        if (learning == this) learning = null;
        super.onDetachedFromWindow();
    }

    @Override boolean wantsAllKeys() { return true; }

    // the service reports to the screen that is open and in front, not to one Deck left behind another app
    @Override void pause() { if (learning == this) learning = null; }

    @Override void resume() { learning = this; }

    // ---------------------------------------------------------------- what the two witnesses saw

    private static boolean navKey(int code) {
        return RemapKey.clsOfCode(code) == RemapKey.NAV;
    }

    /** Deck's window got a key. */
    @Override
    void rawKey(KeyEvent e) {
        if (e.getAction() != KeyEvent.ACTION_DOWN || e.getRepeatCount() != 0) return;
        if (nav.active()) return;   // the top buttons have the focus: these presses move or press them
        int code = e.getKeyCode();
        if (navKey(code) && code != 0) return;   // they steer the top buttons here
        feed(false, e);
    }

    /** The overlay service got a key at the key filter (called from PsService on the main thread). */
    void fromService(KeyEvent e) {
        if (e.getAction() != KeyEvent.ACTION_DOWN || e.getRepeatCount() != 0) return;
        if (nav.active() || (navKey(e.getKeyCode()) && e.getKeyCode() != 0)) return;
        feed(true, e);
    }

    private void feed(boolean service, KeyEvent e) {
        InputDevice d = null;
        try { d = e.getDevice(); } catch (Throwable ignored) {}
        String name = d == null || d.getName() == null ? "" : d.getName().toUpperCase(Locale.ROOT);
        int vendor = d == null ? 0 : d.getVendorId(), product = d == null ? 0 : d.getProductId();
        if (learn.feed(service, e.getKeyCode(), e.getScanCode(), vendor, product, name, e.getSource(), SystemClock.uptimeMillis())) {
            note = "";
            rebuild();
            invalidate();
        }
    }

    private void rebuild() {
        verdictLines.clear();
        for (String s : learn.verdict(PsService.live != null)) wrap(s, mono, 1700f, verdictLines);
    }

    /** Word wrap in design px. */
    private static void wrap(String text, Paint p, float maxW, ArrayList<String> out) {
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        for (String w : words) {
            String t = line.length() == 0 ? w : line + " " + w;
            if (line.length() > 0 && p.measureText(t) > maxW) { out.add(line.toString()); line.setLength(0); line.append(w); }
            else { line.setLength(0); line.append(t); }
        }
        if (line.length() > 0) out.add(line.toString());
    }

    // ---------------------------------------------------------------- TestScreen

    @Override void bind(InputDevice d) { if (d != null) deviceId = d.getId(); }

    @Override void padKey(KeyEvent e, int code, boolean down, boolean first) {}

    @Override void otherKey(int code, boolean down, boolean first) {}

    @Override void motion(MotionEvent e) {}

    @Override
    void chipClicked(int idx) {
        switch (idx) {
            case C_CLEAR:
                learn.clear();
                note = "";
                rebuild();
                host.testFx(SoundMap.SELECT);
                break;
            case C_ASSIGN:
                if (learn.canAssign(advancedOn)) {
                    host.testFx(SoundMap.SELECT);
                    assign.assign(learn.key, RemapKey.name(learn.key));
                } else {
                    host.testFx(SoundMap.ERROR);
                    note = !learn.any() ? "PRESS A BUTTON FIRST."
                            : (RemapKey.protectedKey(learn.key) ? RemapKey.protectedReason(learn.key)
                            : "THIS ONE LIVES IN ADVANCED REMAP. OPEN IT FROM THE BOTTOM OF THE REMAP TAB.");
                }
                break;
            default:
                exit(null);
                break;
        }
        invalidate();
    }

    @Override boolean animating(long now) { return false; }

    @Override
    void content(Canvas c, long now, float dt) {
        Ui.Theme t = Ui.T;
        head.setColor(t.text);
        c.drawText("LEARN A BUTTON", 96, 112, head);
        small.setColor(t.dim);
        c.drawText("PRESS ANY BUTTON ON YOUR REMOTE. BACK EXITS. THE D-PAD AND OK STEER THE TOP BUTTONS.", 96, 160, small);
        if (!learn.any()) {
            big.setColor(t.a);
            big.setTextAlign(Paint.Align.CENTER);
            c.drawText("PRESS A BUTTON", 960, 420, big);
            big.setTextAlign(Paint.Align.LEFT);
        } else {
            big.setColor(t.a);
            c.drawText(RemapKey.name(learn.key), 96, 290, big);
            row(c, t, 360, "KEY CODE", learn.code == 0 ? "0 (UNKNOWN, NAMED BY SCAN CODE)" : String.valueOf(learn.code));
            row(c, t, 410, "SCAN CODE", String.valueOf(learn.scan));
            row(c, t, 460, "DEVICE", learn.device.isEmpty() ? "UNKNOWN" : learn.device + "  (" + learn.vendor + ":" + learn.product + ")");
            row(c, t, 510, "SOURCE", learn.source == 0 ? "UNKNOWN" : RemapLearn.sourceText(learn.source));
            row(c, t, 560, "ID", RemapKey.id(learn.key));
            flag(c, t, 650, "SEEN BY VISOR", learn.deck ? "YES" : "NO", learn.deck);
            boolean svcOn = PsService.live != null;
            flag(c, t, 710, "SEEN BY THE OVERLAY SERVICE", learn.svc ? "YES" : (svcOn ? "NO" : "NO, THE SERVICE IS OFF"), learn.svc);
        }
        float y = learn.any() ? 800 : 520;
        mono.setColor(t.text);
        for (String s : verdictLines) { c.drawText(s, 96, y, mono); y += 34; }
        if (!note.isEmpty()) {
            mono.setColor(t.a);
            c.drawText(note, 96, y + 12, mono);
        }
        small.setColor(t.dim);
        if (PsService.live == null) {
            c.drawText("THE OVERLAY SERVICE IS OFF, SO ONLY VISOR CAN SEE THE KEY. TURN IT ON FROM A PHONE:  bash enable-ps-overlay.sh TVIP:PORT on", 96, 930, small);
            c.drawText("THE BY HAND COMMANDS ARE IN SETUP, PAD, SHOW ENABLE COMMANDS (THE SAME ONES AS THE PS MENU OVER APPS).", 96, 960, small);
        }
        c.drawText("A BUTTON THAT NEVER SHOWS HERE IS KEPT BY THE BOX. NO APP CAN CATCH IT, SO NO REMAP CAN USE IT.", 96, 990, small);
    }

    private void row(Canvas c, Ui.Theme t, float y, String k, String v) {
        small.setColor(t.dim);
        c.drawText(k, 96, y, small);
        mono.setColor(t.text);
        c.drawText(v, 400, y, mono);
    }

    private void flag(Canvas c, Ui.Theme t, float y, String k, String v, boolean on) {
        label.setColor(t.text);
        c.drawText(k, 96, y, label);
        label.setColor(on ? t.a : t.dim);
        c.drawText(v, 700, y, label);
    }
}
