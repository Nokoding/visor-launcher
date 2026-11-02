package com.nokoding.deck;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

/**
 * 2.6 full screen warning in front of ADVANCED REMAP (and, stronger, in front of SYSTEM WIDE
 * NAVIGATION REMAP). Plain words: what can go wrong, that the D-pad and OK can stop working, how to
 * reset. It only continues when OK (or A on a controller) is held for the whole time on screen
 * (3 seconds, 5 for the stronger one). Back, EXIT or letting go cancels. Letting go restarts the
 * count. No animation except the hold ring; with REDUCE EFFECTS the ring updates at 30 frames.
 */
@android.annotation.SuppressLint("ViewConstructor")
final class RemapWarnView extends TestScreen {

    private final String title;
    private final String[] lines;
    private final RemapHold go;
    private final Runnable onDone;
    private boolean finished;

    RemapWarnView(Context c, Host host, boolean reduce, String title, String[] lines, long holdMs, Runnable onDone) {
        super(c, host, reduce, true);
        this.title = title;
        this.lines = lines;
        this.go = new RemapHold(holdMs);
        this.onDone = onDone;
        setChips(new String[]{"CANCEL"}, new float[]{170});
    }

    @Override void bind(InputDevice d) { if (d != null) deviceId = d.getId(); }

    @Override
    void padKey(KeyEvent e, int code, boolean down, boolean first) {
        if (code == KeyEvent.KEYCODE_BUTTON_A) hold(down);
    }

    @Override
    void otherKey(int code, boolean down, boolean first) {
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER) hold(down);
    }

    private void hold(boolean down) {
        long now = SystemClock.uptimeMillis();
        if (down) go.down(now); else go.up();
        invalidate();
    }

    @Override void motion(MotionEvent e) {}

    @Override void chipClicked(int idx) { exit(null); }

    @Override boolean animating(long now) { return go.holding(); }

    @Override
    void content(Canvas c, long now, float dt) {
        Ui.Theme t = Ui.T;
        if (!finished && go.due(now)) {
            finished = true;
            post(onDone);   // never remove this view from inside its own draw
        }
        head.setColor(t.a);
        c.drawText(title, 96, 120, head);
        mono.setColor(t.text);
        float y = 210;
        for (String s : lines) {
            if (s.isEmpty()) { y += 18; continue; }
            c.drawText(s, 96, y, mono);
            y += 36;
        }
        // the hold bar
        float p = go.progress(now);
        float bx = 96, by = 900, bw = 1000, bh = 26;
        fill.setColor(Ui.alpha(t.dim, 0.25f));
        rr.set(bx, by, bx + bw, by + bh);
        c.drawRect(rr, fill);
        fill.setColor(t.a);
        rr.set(bx, by, bx + bw * p, by + bh);
        c.drawRect(rr, fill);
        label.setColor(t.text);
        c.drawText("HOLD OK OR A FOR " + (go.ms / 1000) + " SECONDS TO CONTINUE", 96, by - 22, label);
        small.setColor(t.dim);
        c.drawText("LET GO TO START OVER. BACK OR CANCEL LEAVES WITHOUT CHANGING ANYTHING.", 96, by + 62, small);
    }
}
