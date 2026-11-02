package com.nokoding.deck;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import java.util.Locale;

/**
 * 2.3: Deck's own toast, a small angled HUD panel near the bottom whose line types in with the
 * shared TextReveal (so toasts follow the same animation options). It replaces Android toasts
 * while Deck is in front; HomeActivity falls back to a system toast when it is not.
 * The text is measured once per toast; frames do not allocate.
 */
final class HudToast extends View implements TextReveal.Callback {
    static final long HOLD_MS = 2600, FADE_MS = 200;

    private TextReveal reveal;
    private Panel.Fx fx;
    private boolean rtl, glass, reduce;
    private final TypeDraw.Piece piece = new TypeDraw.Piece();
    private final Path shape = new Path();
    private final Paint fill = Ui.fill(), edge = Ui.stroke(1), cursorP = Ui.fill(), tick = Ui.fill();
    private final TextPaint text;
    private float bx, by, bw, bh;
    private final Runnable hide = this::fadeOut;

    HudToast(Context c) {
        super(c);
        text = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        text.setTypeface(Ui.MONO);
        text.setTextSize(Ui.px(22));
        setWillNotDraw(false);
        setVisibility(GONE);
        themeChanged();
    }

    @Override public boolean hasOverlappingRendering() { return false; }

    void themeChanged() {
        Ui.Theme t = Ui.T;
        fill.setColor(Ui.alpha(t.panel, glass ? 0.72f : 0.96f));
        edge.setColor(Ui.alpha(t.a, 0.8f));
        edge.setStrokeWidth(Math.max(1f, Ui.px(2)));
        cursorP.setColor(t.a);
        tick.setColor(t.a);
        text.setColor(t.text);
        invalidate();
    }

    void show(String msg, TextReveal.Clock clock, TickScheduler ticks, Panel.Fx f, TextReveal.Params p, boolean glassStyle, boolean reduceFx) {
        if (msg == null || msg.isEmpty()) return;
        fx = f;
        glass = glassStyle;
        reduce = reduceFx;
        rtl = p.rtl;
        themeChanged();
        float max = Ui.px(1400);
        piece.set(TextUtils.ellipsize(msg.toUpperCase(Locale.ROOT), text, max, TextUtils.TruncateAt.END), text);
        bh = Ui.px(64);
        bw = piece.w + Ui.px(88);
        bx = (Ui.W - bw) / 2f;
        by = Ui.px(900);
        Ui.cut(shape, bx, by, bx + bw, by + bh, Ui.px(18), Ui.px(6));
        if (reveal == null) reveal = new TextReveal(clock, this);
        reveal.share(ticks);
        reveal.p.set(p);
        animate().cancel();
        setAlpha(1f);
        setVisibility(VISIBLE);
        bringToFront();
        reveal.startLengths(new int[]{piece.n}, 1, 0);
        removeCallbacks(hide);
        postDelayed(hide, HOLD_MS + reveal.totalMs());
        invalidate();
    }

    private void fadeOut() {
        if (getVisibility() != VISIBLE) return;
        if (reduce) { setVisibility(GONE); return; }
        animate().alpha(0f).setDuration(FADE_MS).withEndAction(() -> setVisibility(GONE)).start();
    }

    void dismiss() {
        removeCallbacks(hide);
        animate().cancel();
        setVisibility(GONE);
    }

    boolean stepAnim() { return reveal != null && reveal.step() && reveal.running(); }

    boolean animating() { return getVisibility() == VISIBLE && reveal != null && reveal.running(); }

    boolean finishAnim() { return reveal != null && reveal.finish(); }

    @Override public void onRevealTick(float rate) { if (fx != null) fx.typeTick(rate); }

    @Override public void onRevealFrame() { invalidate(); }

    @Override public void onRevealDone() { invalidate(); }

    @Override
    protected void onDraw(Canvas c) {
        if (bw <= 0) return;
        float a = reveal == null ? 1f : reveal.alpha(0), sl = reveal == null ? 0f : reveal.slide(0) * Ui.u;
        c.drawPath(shape, fill);
        c.drawPath(shape, edge);
        c.drawRect(bx + Ui.px(22), by + bh / 2f - Ui.px(5), bx + Ui.px(32), by + bh / 2f + Ui.px(5), tick);
        int k = reveal == null ? piece.n : Math.min(piece.n, reveal.visible(0, piece.n));
        text.setColor(Ui.alpha(Ui.T.text, a));
        float base = by + bh / 2f - (text.ascent() + text.descent()) / 2f;
        float hx = TypeDraw.draw(c, piece, k, bx + Ui.px(52) + sl, base, text, rtl);
        if (reveal != null && reveal.cursor(0)) TypeDraw.cursor(c, hx, base, text, cursorP, rtl);
    }
}
