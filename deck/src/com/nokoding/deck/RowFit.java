package com.nokoding.deck;

/**
 * 2.5: how a settings row shares its width between the label (left) and the value (right).
 *
 * Bug it fixes (A2): the label was cut at 55 percent of the row and the value was not cut at all,
 * so a long value (an app name in PS BUTTON APPS plus THE APP, a long sound pack name) ran into
 * a long label, and on a narrow flyout row a slider's bar could run into its label.
 *
 * The rule: when both fit, nothing is cut. When they do not, a short side keeps its full width and
 * the other side gets the rest. When both are long, the value gets 60 percent of the text room and
 * the label 40. Whatever is cut ends in three plain dots, never mid glyph, and the cut text always
 * fits the room it was given. A focused value keeps its arrows: the arrows' width is a minimum the
 * value block never goes under, and the budget is computed with the arrows whether the row has
 * focus or not, so a label never changes length when focus lands on its row.
 *
 * RIGHT TO LEFT mirrors the row (label on the right, value on the left). The widths are the
 * same either way. mirror() gives the mirrored left edge. Pure Java, no Android types.
 */
final class RowFit {
    private RowFit() {}

    /** Width of a string in the paint it will be drawn with. */
    interface Measure { float width(String s); }

    static final String DOTS = "...";
    /** Value block cap, as a share of the width the row has for text. */
    static final float VALUE_CAP = 0.60f;

    /** The result of split(). */
    static final class Split {
        /** Width the label may use, 0 when there is no label. */
        float labelMax;
        /** Width the value text may use (its arrows included), 0 when there is no value. */
        float valueMax;
        /** Total width taken by label, gap, fixed extras and value. */
        float used;
    }

    /**
     * Cuts s to at most max wide, ending in three plain dots when something was removed.
     * Returns s itself when it fits, "" when even the dots do not fit.
     */
    static String ellipsize(String s, float max, Measure m) {
        if (s == null || s.isEmpty()) return "";
        if (m.width(s) <= max) return s;
        float dots = m.width(DOTS);
        if (max < dots) return "";
        int lo = 0, hi = s.length() - 1;   // largest n with width(prefix n) + dots <= max
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (m.width(s.substring(0, mid)) + dots <= max) lo = mid; else hi = mid - 1;
        }
        int n = lo;
        while (n > 0 && m.width(s.substring(0, n)) + dots > max) n--;
        if (n > 0 && Character.isHighSurrogate(s.charAt(n - 1))) n--;   // never split an emoji in half
        while (n > 0 && s.charAt(n - 1) == ' ') n--;
        return s.substring(0, n) + DOTS;
    }

    /**
     * Splits the row's text width.
     *
     * @param usable     width from the label's edge to the value side's edge
     * @param reserve    fixed width at the far end of the value side (the OK or arrow mark)
     * @param labelW     full width of the label text
     * @param valueW     full width of the value text, arrows included (0 when the row has none)
     * @param extra      fixed width that sits left of the value text and cannot shrink (a slider bar, a swatch)
     * @param minValue   the least the value may be cut down to (its arrows plus the dots)
     * @param gap        the smallest gap between label and value block
     * @param minLabel   the least label width worth keeping when the label is long
     */
    static void split(float usable, float reserve, float labelW, float valueW, float extra, float minValue,
                      float gap, float minLabel, Split out) {
        usable = fin(usable);
        float avail = Math.max(0f, usable - Math.max(0f, fin(reserve)));
        labelW = Math.max(0f, fin(labelW));
        valueW = Math.max(0f, fin(valueW));
        extra = Math.max(0f, fin(extra));
        minValue = fin(minValue);
        gap = fin(gap);
        minLabel = fin(minLabel);
        boolean hasValue = valueW > 0f || extra > 0f;
        float g = hasValue && labelW > 0f ? Math.max(0f, gap) : 0f;
        float rest = Math.max(0f, avail - extra - g);   // room for the label text and the value text
        float l, v;
        if (!hasValue) {
            l = Math.min(labelW, avail);
            v = 0f;
        } else if (labelW + valueW <= rest) {
            l = labelW;
            v = valueW;
        } else {
            float labelShare = (1f - VALUE_CAP) * rest, valueShare = VALUE_CAP * rest;
            if (labelW <= labelShare) { l = labelW; v = rest - l; }
            else if (valueW <= valueShare) { v = valueW; l = rest - v; }
            else { l = labelShare; v = valueShare; }
            float floorV = Math.min(Math.max(0f, minValue), valueW);
            float floorL = Math.min(labelW, Math.max(0f, minLabel));
            if (v < floorV) { v = Math.min(floorV, rest); l = rest - v; }
            else if (l < floorL) { float take = Math.min(floorL - l, v - floorV); l += take; v -= take; }
            l = Math.max(0f, Math.min(l, labelW));
            v = Math.max(0f, Math.min(v, valueW));
        }
        out.labelMax = l;
        out.valueMax = hasValue ? v : 0f;
        out.used = l + g + (hasValue ? extra + v : 0f);
    }

    /** NaN becomes 0 and infinity becomes a huge number, so a bad measurement can never poison a layout. */
    private static float fin(float x) { return x != x ? 0f : Math.max(-1e7f, Math.min(1e7f, x)); }

    /** Left edge of a piece laid out left to right at x0 with width w, mirrored inside [rowL, rowR] when rtl. */
    static float mirror(float x0, float w, float rowL, float rowR, boolean rtl) {
        return rtl ? rowL + rowR - x0 - w : x0;
    }
}
