package com.nokoding.deck;

import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Build;
import android.service.dreams.DreamService;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.WindowManager;

/**
 * 2.7: Deck's own screensaver. Android calls it a dream. Pick it with the adb commands in
 * docs/screensaver.md (tools/enable-screensaver.sh), or in the TV's own screensaver settings if it
 * has them. The view tree is built when the dream starts and freed when it stops.
 *
 * The dream is interactive on purpose. A non interactive DreamService wakes on every generic motion
 * event, and a gamepad stick that rests a little off centre sends them all the time. Deck's own rules
 * (DreamRules) wake it on any key, any touch, any pointer movement and any gamepad push beyond a small
 * deadzone. The Back key and all others are handled here, so nothing is left to the base class.
 */
public final class DeckDream extends DreamService implements DreamRoot.Host {
    private DreamRoot root;
    private SharedPreferences prefs;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        try {
            prefs = getSharedPreferences("deck", MODE_PRIVATE);
            setInteractive(true);
            setFullscreen(true);
            Ui.loadFonts(getAssets());
            initMetrics();
            HomeActivity.themeFromPrefs(prefs);
            root = new DreamRoot(this, prefs, this);
            setContentView(root);
        } catch (Throwable t) {
            finish();
        }
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        try {
            if (root != null) root.start();
        } catch (Throwable t) {
            finish();
        }
    }

    @Override
    public void onDreamingStopped() {
        try { if (root != null) root.stop(); } catch (Throwable ignored) {}
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        try { if (root != null) root.stop(); } catch (Throwable ignored) {}
        root = null;
        super.onDetachedFromWindow();
    }

    @Override public void wake() { wakeUp(); }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        DreamRoot r = root;
        if (r != null && r.wakeKey(e)) wakeUp();
        return true;
    }

    @Override
    public boolean dispatchKeyShortcutEvent(KeyEvent e) { return dispatchKeyEvent(e); }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        DreamRoot r = root;
        if (r != null && r.wakeTouch(e)) wakeUp();
        return true;
    }

    @Override
    public boolean dispatchTrackballEvent(MotionEvent e) { return dispatchGenericMotionEvent(e); }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent e) {
        DreamRoot r = root;
        if (r != null && r.wakeMotion(e)) wakeUp();
        return true;
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
}
