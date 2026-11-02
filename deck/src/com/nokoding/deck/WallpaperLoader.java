package com.nokoding.deck;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Size;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;

/**
 * 2.7: decodes a wallpaper pack into a WallpaperScene. Run it on a worker thread, never the main one.
 * Every path is wrapped: a bad file, a file too big for this box or an out of memory error ends in a
 * Fail with a plain reason, and the caller shows it in the status line and falls back to the built-in
 * skyline. The sizes are worked out before anything is decoded (WallpaperMem).
 *
 * Static images and layers: BitmapFactory, inSampleSize near the screen width times 1.15, RGB_565 asked
 * for (the decoder keeps ARGB_8888 when the file has alpha). Animated GIF or WebP: ImageDecoder and an
 * AnimatedImageDrawable at 1280x720 or less. If that does not fit, or Reduce effects is on, the first
 * frame is used as a still image.
 */
final class WallpaperLoader {
    private WallpaperLoader() {}

    /** A reason a wallpaper could not be loaded, in plain capitals. */
    static final class Fail extends RuntimeException {
        private static final long serialVersionUID = 1L;
        Fail(String why) { super(why, null, false, false); }
    }

    static WallpaperScene load(File packDir, WallpaperSpec spec, AnimSpec opts, int viewW, boolean reduce) throws Fail {
        WallpaperScene sc = new WallpaperScene(spec, opts);
        try {
            if (spec.type == WallpaperSpec.LAYERS) loadLayers(packDir, spec, sc, viewW);
            else if (spec.type == WallpaperSpec.ANIMATED && !reduce) loadAnimated(packDir, spec, sc, viewW);
            else if (spec.type == WallpaperSpec.VIDEO) loadVideo(packDir, spec, sc, viewW, reduce);
            else if (spec.type == WallpaperSpec.ANIMATED) loadFirstFrame(packDir, spec, sc, viewW, "REDUCE EFFECTS: FIRST FRAME ONLY");
            else loadLayersOf(packDir, spec.files(), sc, viewW);
            return sc;
        } catch (OutOfMemoryError oom) {
            sc.release();
            throw new Fail("OUT OF MEMORY");
        } catch (Fail f) {
            sc.release();
            throw f;
        } catch (Throwable t) {
            sc.release();
            throw new Fail("COULD NOT DECODE THE IMAGE");
        }
    }

    private static void loadLayers(File dir, WallpaperSpec spec, WallpaperScene sc, int viewW) {
        loadLayersOf(dir, spec.files(), sc, viewW);
    }

    private static void loadLayersOf(File dir, ArrayList<String> names, WallpaperScene sc, int viewW) {
        int n = names.size();
        if (n == 0) throw new Fail("NO IMAGE IN THE PACK");
        File[] files = new File[n];
        int[] iw = new int[n], ih = new int[n];
        boolean[] alpha = new boolean[n];
        BitmapFactory.Options probe = new BitmapFactory.Options();
        probe.inJustDecodeBounds = true;
        for (int i = 0; i < n; i++) {
            files[i] = Wallpapers.file(dir, names.get(i));
            if (files[i] == null) throw new Fail("FILE NOT FOUND: " + names.get(i));
            probe.outWidth = 0; probe.outHeight = 0; probe.outMimeType = null;
            BitmapFactory.decodeFile(files[i].getPath(), probe);
            if (probe.outWidth <= 0 || probe.outHeight <= 0) throw new Fail("CANNOT READ IMAGE: " + names.get(i));
            iw[i] = probe.outWidth;
            ih[i] = probe.outHeight;
            alpha[i] = !"image/jpeg".equals(probe.outMimeType);
        }
        int[] sample = WallpaperMem.plan(iw, ih, alpha, viewW, WallpaperMem.STATIC_CAP);
        if (sample == null) throw new Fail("TOO BIG FOR THIS BOX (OVER " + (WallpaperMem.STATIC_CAP >> 20) + " MB DECODED)");
        Bitmap[] out = new Bitmap[n];
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        long got = 0;
        for (int i = 0; i < n; i++) {
            o.inSampleSize = sample[i];
            Bitmap b = BitmapFactory.decodeFile(files[i].getPath(), o);
            if (b == null) throw new Fail("CANNOT DECODE: " + names.get(i));
            out[i] = b;
            got += b.getAllocationByteCount();
            if (got > WallpaperMem.STATIC_CAP * 5 / 4) throw new Fail("TOO BIG FOR THIS BOX (" + (got >> 20) + " MB DECODED)");
        }
        sc.bytes = got;
        sc.setBitmaps(out);
    }

    /**
     * 2.17: reads the video's header, refuses it when it is over VideoLimits, and keeps its first frame as the
     * still image. The video itself is played by VideoLayer; under Reduce effects only the still is used.
     */
    private static void loadVideo(File dir, WallpaperSpec spec, WallpaperScene sc, int viewW, boolean reduce) {
        File f = Wallpapers.file(dir, spec.file);
        if (f == null) throw new Fail("FILE NOT FOUND: " + spec.file);
        android.media.MediaMetadataRetriever r = new android.media.MediaMetadataRetriever();
        try {
            r.setDataSource(f.getPath());
            int w = (int) VideoLimits.parseLong(r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH), 0);
            int h = (int) VideoLimits.parseLong(r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT), 0);
            int rot = (int) VideoLimits.parseLong(r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION), 0);
            long dur = VideoLimits.parseLong(r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION), 0);
            long bit = VideoLimits.parseLong(r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_BITRATE), 0);
            double fps = VideoLimits.parseDouble(r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE), 0);
            long frames = VideoLimits.parseLong(r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT), 0);
            if (fps <= 0 && frames > 0 && dur > 0) fps = frames * 1000.0 / dur;
            String why = VideoLimits.check(w, h, rot, dur, bit, fps, f.length());
            if (why != null) throw new Fail(why);
            int[] size = new int[2];
            VideoMath.rotated(w, h, rot, size);
            int tw = Math.min(size[0], 1280), th = Math.max(1, Math.round(tw * (size[1] / (float) size[0])));
            Bitmap b = r.getScaledFrameAtTime(0, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC, tw, th);
            if (b == null) throw new Fail("CANNOT READ A FRAME OF THE VIDEO");
            sc.bytes = b.getAllocationByteCount();
            sc.setBitmaps(new Bitmap[]{b});
            if (reduce) {
                sc.note = "REDUCE EFFECTS: FIRST FRAME ONLY";
            } else {
                sc.video = f;
                sc.videoW = size[0];
                sc.videoH = size[1];
                sc.note = "VIDEO, EXPERIMENTAL";
            }
        } catch (Fail fail) {
            throw fail;
        } catch (RuntimeException e) {
            throw new Fail("CANNOT READ THE VIDEO");
        } finally {
            try { r.release(); } catch (Throwable ignored) {}
        }
    }

    private static void loadFirstFrame(File dir, WallpaperSpec spec, WallpaperScene sc, int viewW, String note) throws IOException {
        File f = Wallpapers.file(dir, spec.file);
        if (f == null) throw new Fail("FILE NOT FOUND: " + spec.file);
        final int[] t = new int[2];
        final int vw = viewW;
        Bitmap b = ImageDecoder.decodeBitmap(ImageDecoder.createSource(f), (decoder, info, src) -> {
            Size sz = info.getSize();
            if (!WallpaperMem.frameTarget(sz.getWidth(), sz.getHeight(), vw, t)) throw new Fail("TOO BIG FOR THIS BOX");
            decoder.setTargetSampleSize(WallpaperMem.sample(sz.getWidth(), sz.getHeight(), WallpaperMem.targetWidth(vw)));
            decoder.setMemorySizePolicy(ImageDecoder.MEMORY_POLICY_LOW_RAM);
        });
        if (b == null) throw new Fail("CANNOT DECODE: " + spec.file);
        sc.bytes = b.getAllocationByteCount();
        sc.note = note;
        sc.setBitmaps(new Bitmap[]{b});
    }

    private static void loadAnimated(File dir, WallpaperSpec spec, WallpaperScene sc, int viewW) throws IOException {
        File f = Wallpapers.file(dir, spec.file);
        if (f == null) throw new Fail("FILE NOT FOUND: " + spec.file);
        final int[] t = new int[2];
        final boolean[] tooBig = new boolean[1];
        final int vw = viewW;
        Drawable d = null;
        try {
            d = ImageDecoder.decodeDrawable(ImageDecoder.createSource(f), (decoder, info, src) -> {
                Size sz = info.getSize();
                if (info.isAnimated()) {
                    if (!WallpaperMem.animTarget(sz.getWidth(), sz.getHeight(), WallpaperMem.ANIM_CAP, t)) { tooBig[0] = true; throw new Fail("TOO BIG"); }
                    decoder.setTargetSize(Math.max(1, t[0]), Math.max(1, t[1]));
                } else {
                    if (!WallpaperMem.frameTarget(sz.getWidth(), sz.getHeight(), vw, t)) { tooBig[0] = true; throw new Fail("TOO BIG"); }
                    decoder.setTargetSampleSize(WallpaperMem.sample(sz.getWidth(), sz.getHeight(), WallpaperMem.targetWidth(vw)));
                }
                decoder.setMemorySizePolicy(ImageDecoder.MEMORY_POLICY_LOW_RAM);
            });
        } catch (Fail big) {
            if (!tooBig[0]) throw big;
        }
        if (d instanceof AnimatedImageDrawable) {
            AnimatedImageDrawable a = (AnimatedImageDrawable) d;
            a.setRepeatCount(AnimatedImageDrawable.REPEAT_INFINITE);
            sc.anim = a;
            sc.animW = Math.max(1, t[0] > 0 ? t[0] : a.getIntrinsicWidth());
            sc.animH = Math.max(1, t[1] > 0 ? t[1] : a.getIntrinsicHeight());
            sc.bytes = WallpaperMem.animBytes(sc.animW, sc.animH);
            sc.resolve();
            return;
        }
        if (d instanceof BitmapDrawable && ((BitmapDrawable) d).getBitmap() != null) {
            Bitmap b = ((BitmapDrawable) d).getBitmap();
            sc.bytes = b.getAllocationByteCount();
            sc.note = "NOT ANIMATED: SHOWN AS A STILL IMAGE";
            sc.setBitmaps(new Bitmap[]{b});
            return;
        }
        // too big to animate (or an odd drawable): the first frame, sized for a still image
        loadFirstFrame(dir, spec, sc, viewW, "TOO BIG TO ANIMATE: FIRST FRAME ONLY");
    }

    /** A short readable line about a loaded scene, for the status row. */
    static String describe(WallpaperScene sc) {
        if (sc == null) return "";
        StringBuilder b = new StringBuilder();
        String name = sc.spec.name.isEmpty() ? "PACK" : sc.spec.name.toUpperCase(Locale.ROOT);
        b.append(name).append("  ").append(WallpaperSpec.TYPE_NAMES[sc.spec.type].toUpperCase(Locale.ROOT));
        if (sc.spec.type == WallpaperSpec.LAYERS) b.append(' ').append(sc.bitmaps.length);
        if (sc.bytes > 0) b.append("  ").append(Math.max(1, sc.bytes >> 20)).append(" MB");
        if (!sc.note.isEmpty()) b.append("  //  ").append(sc.note);
        else if (!sc.spec.notes.isEmpty()) b.append("  //  ").append(sc.spec.notes.get(0).toUpperCase(Locale.ROOT));
        return b.toString();
    }
}
