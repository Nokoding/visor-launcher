package com.nokoding.deck;

/**
 * 2.17 video wallpapers: the matrix that places the video the way ParallaxMath places an image. A TextureView stretches
 * its video to the whole view, so to draw the video at scale s with its top left at (left, top), the view's
 * transform is a scale of s * videoW / viewW by s * videoH / viewH (about 0, 0) and then a move of (left, top).
 * Pure Java.
 */
final class VideoMath {
    private VideoMath() {}

    /** out = {sx, sy, tx, ty}. Returns false (out untouched) for a size that is zero or negative. */
    static boolean transform(float[] out, int viewW, int viewH, int videoW, int videoH, double scale, double left, double top) {
        if (viewW <= 0 || viewH <= 0 || videoW <= 0 || videoH <= 0 || out == null || out.length < 4) return false;
        if (Double.isNaN(scale) || Double.isNaN(left) || Double.isNaN(top)) return false;
        out[0] = (float) (scale * videoW / viewW);
        out[1] = (float) (scale * videoH / viewH);
        out[2] = (float) left;
        out[3] = (float) top;
        return true;
    }

    /** The video size after the header's rotation is applied. out = {w, h}. */
    static void rotated(int w, int h, int rotation, int[] out) {
        boolean swap = rotation == 90 || rotation == 270;
        out[0] = swap ? h : w;
        out[1] = swap ? w : h;
    }
}
