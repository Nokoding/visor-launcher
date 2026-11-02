package com.nokoding.deck;

import android.content.Context;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

/**
 * 2.9: "make a combo". Hold one button, then press another while you hold the first one. It uses ComboLearn
 * (pure) for the steps and ComboTable.whyNot for the reason a button cannot be used. Like LEARN A BUTTON it is a
 * TestScreen: every key comes here, Back on the remote leaves, nothing runs behind it. Combos are made inside Deck.
 */
@android.annotation.SuppressLint("ViewConstructor")
final class ComboLearnView extends TestScreen {

    interface Done {
        void combo(long a, long b);
    }

    private final ComboLearn learn = new ComboLearn();
    private final Done done;
    private boolean fired;
    private long first;

    ComboLearnView(Context c, Host host, boolean reduce, Done done) {
        super(c, host, reduce, true);
        this.done = done;
        setChips(new String[]{"CANCEL"}, new float[]{170});
        learn.start(SystemClock.uptimeMillis());
    }

    static long keyOf(KeyEvent e) {
        int code = e.getKeyCode();
        if (code != 0) return RemapKey.pack(code, 0, 0, 0);
        InputDevice d = e.getDevice();
        return RemapKey.pack(0, e.getScanCode(), d == null ? 0 : d.getVendorId(), d == null ? 0 : d.getProductId());
    }

    @Override
    void rawKey(KeyEvent e) {
        int act = e.getAction();
        if (act != KeyEvent.ACTION_DOWN && act != KeyEvent.ACTION_UP) return;
        if (act == KeyEvent.ACTION_DOWN && e.getRepeatCount() > 0) return;
        long k = keyOf(e);
        if (!RemapKey.valid(k)) return;
        long now = SystemClock.uptimeMillis();
        boolean down = act == KeyEvent.ACTION_DOWN;
        // after a refusal or a timeout the next button press starts it again
        if (down && learn.finished() && learn.state() != ComboLearn.DONE) { learn.start(now); first = 0; }
        int before = learn.state();
        int st = learn.onKey(k, down, now);
        if (down && before == ComboLearn.WAIT_FIRST && st == ComboLearn.WAIT_SECOND) first = k;
        if (st == ComboLearn.DONE && !fired) {
            fired = true;
            final long a = learn.a, b = learn.b;
            host.testFx(SoundMap.SELECT);
            postDelayed(() -> done.combo(a, b), 600);
        } else if (st == ComboLearn.REJECTED) {
            host.testFx(SoundMap.ERROR);
        }
        invalidate();
    }

    @Override boolean wantsAllKeys() { return true; }

    @Override void bind(InputDevice d) {}

    @Override void padKey(KeyEvent e, int code, boolean down, boolean first) {}

    @Override void otherKey(int code, boolean down, boolean first) {}

    @Override void motion(MotionEvent e) {}

    @Override void chipClicked(int idx) { exit(null); }

    @Override boolean animating(long now) { return !learn.finished(); }

    // 2.10: big text, a TV is read from the couch
    private final android.text.TextPaint bodyP = tp(Ui.MONO, 36), noteP = tp(Ui.MONO, 28), statP = tp(Ui.HEAD, 60);

    @Override
    void content(Canvas c, long now, float dt) {
        learn.poll(now);
        Ui.Theme t = Ui.T;
        head.setColor(t.a);
        c.drawText("MAKE A COMBO", 96, 120, head);
        bodyP.setColor(t.text);
        c.drawText("HOLD ONE BUTTON, THEN PRESS ANOTHER", 96, 240, bodyP);
        c.drawText("WHILE YOU STILL HOLD THE FIRST.", 96, 290, bodyP);
        noteP.setColor(t.dim);
        c.drawText("THE FIRST BUTTON STILL GOES TO THE APP. ONLY THE SECOND ONE IS TAKEN.", 96, 360, noteP);
        c.drawText("USABLE: X, Y, L1, R1, L2, R2, THE STICK CLICKS, CREATE, OPTIONS", 96, 400, noteP);
        c.drawText("AND SPARE REMOTE BUTTONS.", 96, 440, noteP);
        String line;
        int st = learn.state();
        switch (st) {
            case ComboLearn.WAIT_SECOND:
                line = "HOLDING " + RemapKey.name(first) + ". NOW PRESS THE SECOND BUTTON.";
                break;
            case ComboLearn.DONE:
                line = RemapKey.name(learn.a) + " + " + RemapKey.name(learn.b);
                break;
            case ComboLearn.REJECTED:
                line = learn.why + ".";
                break;
            case ComboLearn.TIMEOUT:
                line = "TOO SLOW. LET GO AND TRY AGAIN.";
                break;
            default:
                line = "WAITING FOR THE FIRST BUTTON";
                break;
        }
        statP.setTextSize(60f);
        float lw = statP.measureText(line);
        if (lw > 1728f) statP.setTextSize(60f * 1728f / lw);   // a long button name shrinks the line instead of running off the screen
        statP.setColor(t.a);
        c.drawText(line, 96, 620, statP);
        if (learn.finished() && st != ComboLearn.DONE) {
            noteP.setColor(t.dim);
            c.drawText(st == ComboLearn.REJECTED ? "LET GO, THEN PRESS TWO OTHER BUTTONS." : "PRESS ANY BUTTON TO START AGAIN.", 96, 700, noteP);
        }
    }
}
