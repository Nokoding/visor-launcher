package com.nokoding.deck;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;

import java.io.File;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.LruCache;

import java.util.HashSet;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Lazy banner and icon loader. Rasterizes once on a background thread (newest request
 * first) and keeps a byte bounded LRU. 2.1: loads the highest density art first
 * (xxxhdpi, then lower), renders adaptive icon layers at 2x the drawn size, sizes banners
 * by their real resolution, and lets a PNG or WebP in files/icons/ replace any app's art.
 */
final class IconCache {

    static final class Icon {
        final Bitmap bmp;
        final boolean banner;
        final int color;
        /** Where it came from, for Setup: OVERRIDE, BANNER, ICON or DEFAULT. */
        String source = "";
        Icon(Bitmap bmp, boolean banner, int color) { this.bmp = bmp; this.banner = banner; this.color = color; }
    }

    interface Listener { void onIcon(String key, Icon icon); }

    private final Context ctx;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Icon> cache;
    private final HashSet<String> pending = new HashSet<>();
    private final HashSet<String> failed = new HashSet<>();
    private final ThreadPoolExecutor exec;
    private Listener listener;
    private int generation;
    final int bannerW, bannerH, iconPx, tileW;
    private final int screenDpi;
    /** Custom icon folder (files/icons) and the packages whose custom icon is switched off. */
    volatile File overrideDir;
    /** 2.2: chosen icon pack folder name inside overrideDir, "" for none. Single files still win. */
    volatile String iconPack = "";
    volatile java.util.Set<String> overrideOff = new HashSet<>();

    IconCache(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        int bytes = 8 * 1024 * 1024;
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null && (am.isLowRamDevice() || am.getMemoryClass() < 160)) bytes = 5 * 1024 * 1024;
        } catch (Throwable ignored) {}
        cache = new LruCache<String, Icon>(bytes) {
            @Override protected int sizeOf(String k, Icon v) {
                return v.bmp == null ? 64 : v.bmp.getAllocationByteCount();
            }
        };
        bannerW = Math.max(96, Math.round(352 * Ui.u));
        bannerH = Math.max(54, Math.round(198 * Ui.u));
        // banners follow their real resolution but stop at 480 design px to keep memory small
        tileW = Math.max(bannerW, Math.round(480 * Ui.u));
        // the wheel draws icons at 0.40 of a 315 px tile, scaled up to 1.06 when focused
        iconPx = IconPick.iconRaster(315 * 0.40f * 1.06f * Ui.u);
        int dpi = DisplayMetrics.DENSITY_XHIGH;
        try { dpi = ctx.getResources().getDisplayMetrics().densityDpi; } catch (Throwable ignored) {}
        screenDpi = dpi;
        exec = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, new LinkedBlockingDeque<Runnable>() {
            @Override public boolean offer(Runnable r) { return offerFirst(r); }
        }, r -> {
            Thread t = new Thread(() -> {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                r.run();
            }, "deck-icons");
            t.setDaemon(true);
            return t;
        });
        exec.allowCoreThreadTimeOut(true);
    }

    void setListener(Listener l) { listener = l; }

    Icon get(String key) { return key == null ? null : cache.get(key); }

    /** Put back an icon that a view kept on screen after a trim, so it is not loaded again. */
    void put(String key, Icon ic) { if (key != null && ic != null) cache.put(key, ic); }

    /** Cache hit, or the icon a view still holds, re-cached. Null means it must be loaded. */
    Icon reuse(String key, Icon held) {
        Icon ic = get(key);
        if (ic == null && held != null) { put(key, held); ic = held; }
        return ic;
    }

    void request(final AppStore.App app) {
        if (app == null || cache.get(app.key) != null || pending.contains(app.key) || failed.contains(app.key)) return;
        pending.add(app.key);
        final int gen = generation;
        try {
            exec.execute(() -> {
                Icon ic = null;
                try { ic = load(app); } catch (Throwable ignored) {}
                final Icon res = ic;
                main.post(() -> {
                    pending.remove(app.key);
                    if (gen != generation) return;
                    if (res == null) { failed.add(app.key); return; }
                    cache.put(app.key, res);
                    if (listener != null) listener.onIcon(app.key, res);
                });
            });
        } catch (Throwable t) {
            pending.remove(app.key);
        }
    }

    void forget(String pkg) {
        for (String k : cache.snapshot().keySet()) if (k.startsWith(pkg + "/")) cache.remove(k);
        failed.clear();
    }

    void trimHalf() { cache.trimToSize(Math.max(1, cache.maxSize() / 2)); }

    void evictAll() {
        generation++;
        cache.evictAll();
        pending.clear();
        failed.clear();
    }

    void shutdown() {
        try { exec.shutdownNow(); } catch (Throwable ignored) {}
    }

    // ---------- background work ----------

    /** The single custom image file for a package in the icons folder, or null. */
    static File overrideFile(File dir, String pkg) { return IconPacks.image(dir, pkg); }

    /**
     * 2.1.1 icon mode. False is SHARPER (default): highest density art, adaptive layers at 2x and a
     * glass plate behind icons. True is ORIGINAL: what Deck 2.0 did, the app's own banner or icon
     * exactly as the system loads it, drawn straight on the tile with no plate.
     */
    static volatile boolean originalMode;

    /** The 2.0 loader: the system's banner, else the system's icon, with no extra processing. */
    private Icon loadOriginal(AppStore.App app) {
        PackageManager pm = ctx.getPackageManager();
        ActivityInfo ai;
        try {
            ai = pm.getActivityInfo(app.cn, 0);
        } catch (Throwable t) {
            return null;
        }
        Drawable banner = null;
        try { banner = ai.loadBanner(pm); } catch (Throwable ignored) {}
        if (banner != null) {
            Bitmap b = rasterBanner(banner, bannerW, bannerH);
            if (b != null) return tag(new Icon(b, true, Ui.T.panel), "ORIGINAL BANNER " + b.getWidth() + "x" + b.getHeight());
        }
        Drawable icon = null;
        try { icon = ai.loadIcon(pm); } catch (Throwable ignored) {}
        if (icon == null) {
            try { icon = pm.getDefaultActivityIcon(); } catch (Throwable ignored) {}
        }
        if (icon == null) return new Icon(null, false, Ui.T.panel);
        Bitmap b = Bitmap.createBitmap(iconPx, iconPx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        icon.setBounds(0, 0, iconPx, iconPx);
        icon.draw(c);
        return tag(new Icon(b, false, dominant(b)), "ORIGINAL ICON " + iconPx + "PX");
    }

    private Icon load(AppStore.App app) {
        String pkg = app.cn.getPackageName();
        if (!overrideOff.contains(pkg)) {
            Icon o = loadOverride(IconPacks.pick(overrideDir, iconPack, pkg));
            if (o != null) return o;
        }
        if (originalMode) {
            Icon o = loadOriginal(app);
            if (o != null) return o;
        }
        PackageManager pm = ctx.getPackageManager();
        ActivityInfo ai;
        try {
            ai = pm.getActivityInfo(app.cn, 0);
        } catch (Throwable t) {
            return null;
        }
        Resources res = null;
        try { res = pm.getResourcesForApplication(ai.applicationInfo); } catch (Throwable ignored) {}
        int bannerRes = ai.getBannerResource();
        Drawable banner = best(res, bannerRes);
        if (banner == null) { try { banner = ai.loadBanner(pm); } catch (Throwable ignored) {} }
        Drawable icon = best(res, ai.getIconResource());
        if (icon == null) { try { icon = ai.loadIcon(pm); } catch (Throwable ignored) {} }
        if (banner != null && IconPick.useBanner(pixels(banner), icon == null ? 0 : pixels(icon), iconPx)) {
            int px = pixels(banner);
            int w = IconPick.bannerRasterW(px, bannerW, tileW);
            Bitmap b = rasterBanner(banner, w, Math.round(w * 9f / 16f));
            if (b != null) return tag(new Icon(b, true, Ui.T.panel), "BANNER " + b.getWidth() + "x" + b.getHeight());
        }
        String src = "ICON";
        if (icon == null) {
            try { icon = pm.getDefaultActivityIcon(); src = "DEFAULT"; } catch (Throwable ignored) {}
        }
        if (icon == null) return new Icon(null, false, Ui.T.panel);
        Bitmap b = rasterIcon(icon, iconPx);
        return tag(new Icon(b, false, dominant(b)), src + " " + iconPx + "PX" + (icon instanceof AdaptiveIconDrawable ? " ADAPTIVE" : ""));
    }

    private static Icon tag(Icon ic, String s) { ic.source = s; return ic; }

    /** The drawable at the highest density the app ships, falling back step by step. */
    @SuppressWarnings("deprecation")
    private Drawable best(Resources res, int id) {
        if (res == null || id == 0) return null;
        for (int d : IconPick.densityChain(screenDpi)) {
            try {
                Drawable dr = d == 0 ? res.getDrawable(id, null) : res.getDrawableForDensity(id, d, null);
                if (dr != null) return dr;
            } catch (Throwable ignored) {
                // that density is missing or broken, try the next one
            }
        }
        return null;
    }

    /** Real pixel width of the art: bitmap width, or SCALABLE for vector and adaptive drawables. */
    private static int pixels(Drawable d) {
        if (d instanceof BitmapDrawable) {
            Bitmap b = ((BitmapDrawable) d).getBitmap();
            return b == null ? 0 : b.getWidth();
        }
        if (d instanceof AdaptiveIconDrawable || d instanceof android.graphics.drawable.VectorDrawable) return IconPick.SCALABLE;
        Drawable cur = d.getCurrent();
        if (cur != d && cur instanceof BitmapDrawable) return pixels(cur);
        int w = d.getIntrinsicWidth();
        return w <= 0 ? IconPick.SCALABLE : w; // other drawables: their size at screen density
    }

    private Bitmap rasterIcon(Drawable icon, int size) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        if (icon instanceof BitmapDrawable) ((BitmapDrawable) icon).setFilterBitmap(true);
        if (icon instanceof AdaptiveIconDrawable) {
            // draw both layers ourselves: the 72 of 108 dp safe zone fills the square, cut HUD mask
            AdaptiveIconDrawable a = (AdaptiveIconDrawable) icon;
            Paint mask = new Paint(Paint.ANTI_ALIAS_FLAG);
            mask.setColor(Color.WHITE);
            c.drawPath(Ui.cut(new Path(), 0, 0, size, size, size * 0.16f, size * 0.05f), mask);
            Paint in = new Paint(Paint.FILTER_BITMAP_FLAG);
            in.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
            int layer = c.saveLayer(0, 0, size, size, in);
            int ext = Math.round(size * 0.25f);
            Drawable bg = a.getBackground(), fg = a.getForeground();
            if (bg != null) { bg.setBounds(-ext, -ext, size + ext, size + ext); bg.draw(c); }
            if (fg != null) { fg.setBounds(-ext, -ext, size + ext, size + ext); fg.draw(c); }
            c.restoreToCount(layer);
        } else {
            icon.setBounds(0, 0, size, size);
            icon.draw(c);
        }
        return b;
    }

    /** A user picture: wide ones fill the tile like a banner, square ones sit on the icon plate. */
    private Icon loadOverride(File f) {
        if (f == null || f.length() <= 0 || f.length() > 4L * 1024 * 1024) return null;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            if (o.outWidth <= 0 || o.outHeight <= 0) return null;
            boolean wide = IconPick.wide(o.outWidth, o.outHeight);
            BitmapFactory.Options d = new BitmapFactory.Options();
            d.inSampleSize = IconPick.sampleSize(o.outWidth, o.outHeight, wide ? tileW : iconPx);
            Bitmap src = BitmapFactory.decodeFile(f.getPath(), d);
            if (src == null) return null;
            Icon ic;
            if (wide) {
                BitmapDrawable bd = new BitmapDrawable(ctx.getResources(), src);
                int w = Math.min(tileW, Math.max(bannerW, src.getWidth()));
                ic = new Icon(rasterBanner(bd, w, Math.round(w * 9f / 16f)), true, Ui.T.panel);
            } else {
                Bitmap b = Bitmap.createBitmap(iconPx, iconPx, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(b);
                float k = Math.min(iconPx / (float) src.getWidth(), iconPx / (float) src.getHeight());
                float w = src.getWidth() * k, h = src.getHeight() * k;
                c.drawBitmap(src, null, new RectF((iconPx - w) / 2f, (iconPx - h) / 2f, (iconPx + w) / 2f, (iconPx + h) / 2f),
                        new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));
                ic = new Icon(b, false, dominant(b));
            }
            src.recycle();
            File dir = overrideDir;
            boolean single = dir != null && dir.equals(f.getParentFile());
            String from = single ? "CUSTOM " : "PACK " + f.getParentFile().getName().toUpperCase(java.util.Locale.ROOT) + ": ";
            return tag(ic, from + f.getName().toUpperCase(java.util.Locale.ROOT));
        } catch (Throwable t) {
            return null;
        }
    }

    /** Center crop to 16:9, then the HUD cut corners. */
    private Bitmap rasterBanner(Drawable d, int w, int h) {
        Bitmap tmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(tmp);
        if (d instanceof BitmapDrawable) ((BitmapDrawable) d).setFilterBitmap(true);
        int iw = d.getIntrinsicWidth(), ih = d.getIntrinsicHeight();
        if (iw > 0 && ih > 0 && Math.abs(iw / (float) ih - 16f / 9f) > 0.05f) {
            float k = Math.max(w / (float) iw, h / (float) ih);
            int dw = Math.round(iw * k), dh = Math.round(ih * k);
            d.setBounds((w - dw) / 2, (h - dh) / 2, (w - dw) / 2 + dw, (h - dh) / 2 + dh);
        } else {
            d.setBounds(0, 0, w, h);
        }
        d.draw(c);
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas oc = new Canvas(out);
        Paint mask = new Paint(Paint.ANTI_ALIAS_FLAG);
        mask.setColor(Color.WHITE);
        Path p = Ui.cut(new Path(), 0, 0, w, h, h * TileView.CUT_BIG, h * TileView.CUT_SMALL);
        oc.drawPath(p, mask);
        Paint src = new Paint(Paint.FILTER_BITMAP_FLAG);
        src.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        oc.drawBitmap(tmp, 0, 0, src);
        tmp.recycle();
        return out;
    }

    private static int dominant(Bitmap b) {
        long r = 0, g = 0, bl = 0, wsum = 0;
        int w = b.getWidth(), h = b.getHeight();
        float[] hsv = new float[3];
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 10; x++) {
                int c = b.getPixel(x * (w - 1) / 9, y * (h - 1) / 9);
                int a = Color.alpha(c);
                if (a < 40) continue;
                Color.colorToHSV(c, hsv);
                int weight = 1 + (int) (hsv[1] * hsv[2] * 20);
                r += Color.red(c) * weight; g += Color.green(c) * weight; bl += Color.blue(c) * weight;
                wsum += weight;
            }
        }
        if (wsum == 0) return Ui.T.panel;
        return Color.rgb((int) (r / wsum), (int) (g / wsum), (int) (bl / wsum));
    }
}
