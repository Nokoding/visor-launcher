package com.nokoding.deck;

import android.content.Context;
import android.graphics.Canvas;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.util.ArrayList;

/**
 * 2.9: the tutorial. Full screen lessons (TutorialModel) that teach the controls and the features, with a
 * TRY IT step on some of them. It is a TestScreen, so while it is open every key comes here and nothing
 * navigates or launches behind it, and the usual exits work (Back on the remote, Start and Select held for
 * 2 seconds, the EXIT chip). A key that a pending TRY IT step is waiting for counts for the step and does not
 * change the page, so "press OK" can be tried without skipping the lesson.
 *
 * Nothing risky can be tried here: the escape lesson has no try step. Progress is saved as it goes
 * (TutorialProgress), so SETUP, TUTORIAL can continue where you stopped.
 */
@android.annotation.SuppressLint("ViewConstructor")
final class TutorialView extends TestScreen {

    interface Sink {
        void save(String progress);
    }

    private final TutorialProgress prog;
    private final Sink sink;
    private int index;
    private TutorialModel.Lesson lesson;
    private String[][] wrapped = new String[0][];
    private TutorialModel.TryState tryState;
    private boolean closed;
    // 2.10: a TV is read from the couch, so the text is big. Design pixels, the canvas is scaled to the screen.
    private static final float BODY = 40f, LINE = 60f, WRAP_W = 1728f;
    private final android.text.TextPaint bodyP = tp(Ui.MONO, BODY), titleP = tp(Ui.HEAD, 84), tagP = tp(Ui.MONO, 28),
            tryP = tp(Ui.COND_B, 54), hintP = tp(Ui.MONO, 26);

    TutorialView(Context c, Host host, boolean reduce, TutorialProgress progress, int start, Sink sink) {
        super(c, host, reduce, true);
        this.prog = progress;
        this.sink = sink;
        setChips(new String[]{"PREVIOUS", "NEXT", "SKIP TRY", "EXIT"}, new float[]{200, 150, 200, 130});
        wrapped = new String[TutorialModel.count()][];
        go(Math.max(0, Math.min(TutorialModel.count() - 1, start)), false);
    }

    private boolean last() { return index == TutorialModel.count() - 1; }

    private void go(int i, boolean sound) {
        index = i;
        lesson = TutorialModel.get(i);
        if (lesson == null) return;
        prog.mark(lesson.id);
        tryState = lesson.tryIt != null && lesson.tryIt.kind != TutorialModel.NONE ? new TutorialModel.TryState(lesson.tryIt) : null;
        chipLabel[1] = last() ? "FINISH" : "NEXT";
        if (wrapped[i] == null) wrapped[i] = wrap(lesson.lines);
        save();
        if (sound) host.testFx(SoundMap.MOVE);
        invalidate();
    }

    private void save() {
        try { sink.save(prog.save()); } catch (Throwable ignored) {}
    }

    /** Lines are written short, but a long one is split at a space so it never runs off the screen. */
    private String[] wrap(String[] lines) {
        ArrayList<String> out = new ArrayList<>();
        float max = WRAP_W;
        for (String s : lines) {
            String rest = s;
            while (rest.length() > 0 && bodyP.measureText(rest) > max) {
                int cut = rest.length();
                while (cut > 0 && bodyP.measureText(rest, 0, cut) > max) cut--;
                int sp = rest.lastIndexOf(' ', cut);
                if (sp <= 0) sp = cut;
                out.add(rest.substring(0, sp));
                rest = rest.substring(sp).trim();
            }
            out.add(rest);
        }
        return out.toArray(new String[0]);
    }

    private void next() {
        if (last()) { finish(); return; }
        go(index + 1, true);
    }

    private void prev() {
        if (index > 0) go(index - 1, true);
    }

    private void finish() {
        if (closed) return;
        closed = true;
        prog.finish();
        save();
        exit(null);
    }

    /** A try step waits for this key: it counts for the step and does not change the page. */
    private boolean tryKey(int code) {
        return tryState != null && tryState.consumes(code);
    }

    @Override
    void rawKey(KeyEvent e) {
        if (tryState == null || tryState.done()) return;
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if (down && e.getRepeatCount() > 0) return;
        if (tryState.onKey(e.getKeyCode(), down)) {
            host.testFx(SoundMap.TOGGLE);
            invalidate();
        }
    }

    private void navKey(int code, boolean first) {
        if (!first || tryKey(code)) return;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_RIGHT: case KeyEvent.KEYCODE_BUTTON_R1: case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER: case KeyEvent.KEYCODE_NUMPAD_ENTER:
                next();
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT: case KeyEvent.KEYCODE_BUTTON_L1: case KeyEvent.KEYCODE_BUTTON_B:
                prev();
                break;
            default:
                break;
        }
    }

    @Override void bind(InputDevice d) { if (d != null) deviceId = d.getId(); }

    @Override void padKey(KeyEvent e, int code, boolean down, boolean first) { navKey(code, first); }

    @Override void otherKey(int code, boolean down, boolean first) { navKey(code, first); }

    @Override void motion(MotionEvent e) {}

    @Override
    void chipClicked(int idx) {
        if (idx == 0) prev();
        else if (idx == 1) next();
        else if (idx == 2) {
            if (tryState != null && !tryState.done()) { tryState.skip(); host.testFx(SoundMap.TOGGLE); invalidate(); }
        } else {
            // leaving early is a skip: it is not offered again by itself
            if (!closed) {
                closed = true;
                if (!prog.allSeen()) prog.dismiss();
                save();
                exit(null);
            }
        }
    }

    @Override boolean animating(long now) { return false; }

    @Override
    void content(Canvas c, long now, float dt) {
        if (lesson == null) return;
        Ui.Theme t = Ui.T;
        head.setColor(t.a);
        c.drawText("// TUTORIAL", 96, 110, head);
        // the lesson number, and for a controller lesson a note beside it (never at the bottom, where the hints are)
        tagP.setColor(t.dim);
        String cnt = (index + 1) + " OF " + TutorialModel.count() + (lesson.gamepad ? "   //   ABOUT A GAME CONTROLLER" : "");
        c.drawText(cnt, 96, 158, tagP);
        titleP.setColor(t.text);
        c.drawText(lesson.title.toUpperCase(java.util.Locale.ROOT), 96, 268, titleP);

        boolean hasTry = tryState != null;
        float top = 350f, bottom = hasTry ? 700f : 880f;
        String[] ls = wrapped[index];
        float lineH = Math.min(LINE, (bottom - top) / Math.max(1, ls.length));
        float scale = Math.min(1f, lineH / (LINE * 0.86f));   // a very long lesson shrinks a little instead of overlapping
        bodyP.setTextSize(BODY * scale);
        bodyP.setColor(t.text);
        float y = top + 40f * scale;
        for (String s : ls) {
            c.drawText(s, 96, y, bodyP);
            y += lineH;
        }
        // the TRY IT box
        if (hasTry) {
            boolean ok = tryState.done();
            float x = 96, w = 1728, h = 160, by = 730;
            Ui.cut(path, x, by, x + w, by + h, 18, 6);
            fill.setColor(ok ? Ui.alpha(t.a, 0.22f) : Ui.alpha(t.panel, 0.95f));
            c.drawPath(path, fill);
            line.setColor(ok ? t.a : Ui.alpha(t.dim, 0.7f));
            c.drawPath(path, line);
            tagP.setColor(ok ? t.a : t.dim);
            c.drawText(ok ? "TRY IT   //   DONE" : "TRY IT", x + 32, by + 52, tagP);
            tryP.setColor(t.text);
            c.drawText(ok ? lesson.tryIt.done : lesson.tryIt.prompt, x + 32, by + 122, tryP);
        }
        // progress strip
        float sx = 96, sy = 912, sw = 1728f / TutorialModel.count();
        for (int i = 0; i < TutorialModel.count(); i++) {
            TutorialModel.Lesson l = TutorialModel.get(i);
            boolean cur = i == index, seen = l != null && prog.seen(l.id);
            fill.setColor(cur ? t.a : (seen ? Ui.alpha(t.a, 0.45f) : Ui.alpha(t.dim, 0.3f)));
            rr.set(sx + i * sw, sy, sx + (i + 1) * sw - 10, sy + (cur ? 16 : 10));
            c.drawRect(rr, fill);
        }
        hintP.setColor(t.dim);
        c.drawText("A OR OK NEXT   LEFT RIGHT OR LB RB CHANGE PAGE   SKIP TRY SKIPS THE STEP   BACK OR EXIT LEAVES", 96, 972, hintP);
    }
}
