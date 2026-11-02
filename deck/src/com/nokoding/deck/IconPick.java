package com.nokoding.deck;

/**
 * Which picture a tile gets and how big it is rasterized. Pure Java.
 * Order: a custom PNG or WebP override, then the TV banner when it is at least as sharp as
 * the icon, then the icon (loaded at the highest density first), then the default icon.
 */
final class IconPick {
    private IconPick() {}

    static final int D_XXXHIGH = 640, D_XXHIGH = 480, D_XHIGH = 320;
    /** Pixel size meaning "scales to any size" (vector or adaptive drawable). */
    static final int SCALABLE = Integer.MAX_VALUE;
    static final int ICON_CAP = 256;

    /** Densities to try in order. 0 means the normal loadIcon or loadBanner call. */
    static int[] densityChain(int screenDpi) {
        if (screenDpi >= D_XXXHIGH) return new int[]{D_XXXHIGH, 0};
        if (screenDpi >= D_XXHIGH) return new int[]{D_XXXHIGH, D_XXHIGH, 0};
        return new int[]{D_XXXHIGH, D_XXHIGH, D_XHIGH, 0};
    }

    /** Raster size for icons: twice the size they are drawn at, capped to keep memory small. */
    static int iconRaster(float drawnPx) {
        int s = Math.round(drawnPx * 2f);
        return Math.max(96, Math.min(ICON_CAP, s));
    }

    /** Banner raster width: the source pixel width, between the old fixed size and the tile width. */
    static int bannerRasterW(int srcPx, int minW, int tileW) {
        if (srcPx <= 0) return minW;
        if (srcPx == SCALABLE) return tileW;
        return Math.max(minW, Math.min(tileW, srcPx));
    }

    /**
     * bannerPx and iconPx are pixel widths of the real bitmaps (0 = missing, SCALABLE = vector).
     * The banner wins when it is at least as sharp as the icon will be on screen.
     */
    static boolean useBanner(int bannerPx, int iconPx, int iconTarget) {
        if (bannerPx <= 0) return false;
        if (iconPx <= 0) return true;
        if (bannerPx == SCALABLE) return true;
        int iconEff = iconPx == SCALABLE ? iconTarget : Math.min(iconPx, iconTarget);
        return bannerPx >= iconEff;
    }

    /** A custom image this wide is shown like a banner (fills the tile), else as an icon on a plate. */
    static boolean wide(int w, int h) { return w > 0 && h > 0 && w >= h * 1.4f; }

    /** BitmapFactory inSampleSize so the longest side ends up at most maxPx. */
    static int sampleSize(int w, int h, int maxPx) {
        int s = 1;
        while (Math.max(w, h) / (s * 2) >= maxPx) s *= 2;
        return s;
    }
}
