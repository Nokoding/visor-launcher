package com.nokoding.deck;

/**
 * 2.8 control center: grid layout, focus movement and scrolling for one page. Pure Java.
 *
 * A page is a list of items in order. Span 1 is half width (column 0 or 1), span 2 is full width
 * and takes a row alone. A lone span 1 followed by a span 2 leaves its row's column 1 empty.
 */
final class CcLayout {
    private CcLayout() {}

    /** move() result when the user pressed up from the first focusable row: focus goes to the section strip. */
    static final int STRIP = -1;
    /** Reserved for callers that need a "nothing there" marker; move() itself returns cur instead. */
    static final int EDGE = -2;

    private static int span(int[] spans, int i) { return spans[i] == 2 ? 2 : 1; }

    /** Row of every item. */
    static int[] rowOf(int[] spans) { return place(spans, true); }

    /** Column (0 or 1) of every item; a full width item is column 0. */
    static int[] colOf(int[] spans) { return place(spans, false); }

    private static int[] place(int[] spans, boolean rows) {
        if (spans == null) return new int[0];
        int[] out = new int[spans.length];
        int row = 0, col = 0;
        for (int i = 0; i < spans.length; i++) {
            if (span(spans, i) == 2) {
                if (col != 0) { row++; col = 0; }
                out[i] = rows ? row : 0;
                row++;
            } else {
                out[i] = rows ? row : col;
                col++;
                if (col == 2) { col = 0; row++; }
            }
        }
        return out;
    }

    static int rowCount(int[] spans) {
        if (spans == null || spans.length == 0) return 0;
        int[] r = rowOf(spans);
        return r[r.length - 1] + 1;
    }

    private static boolean foc(boolean[] f, int i) { return f != null && i >= 0 && i < f.length && f[i]; }

    /** Index of the first focusable item, or STRIP if there is none. */
    static int firstFocusable(boolean[] f) {
        if (f == null) return STRIP;
        for (int i = 0; i < f.length; i++) if (f[i]) return i;
        return STRIP;
    }

    /**
     * Moves focus one step. dy wins when both are given. Returns the new item index, STRIP when up
     * is pressed from the first focusable row, or cur when there is nothing in that direction.
     * From STRIP, down gives the first focusable item; other moves from the strip return STRIP
     * (the caller moves along the strip itself).
     */
    static int move(int[] spans, boolean[] focusable, int cur, int dx, int dy) {
        if (spans == null) return STRIP;
        int n = spans.length;
        if (cur == STRIP) return dy > 0 ? firstFocusable(limit(focusable, n)) : STRIP;
        if (cur < 0 || cur >= n) return cur;
        int[] rows = rowOf(spans), cols = colOf(spans);
        if (dy != 0) {
            int dir = dy > 0 ? 1 : -1;
            int lastRow = rows[n - 1];
            for (int r = rows[cur] + dir; r >= 0 && r <= lastRow; r += dir) {
                int pick = pickInRow(spans, focusable, rows, cols, r, cols[cur]);
                if (pick >= 0) return pick;
            }
            return dir < 0 ? STRIP : cur;
        }
        if (dx != 0) {
            if (span(spans, cur) == 2) return cur;
            int j = cur + (dx > 0 ? 1 : -1);
            if (j >= 0 && j < n && rows[j] == rows[cur] && foc(focusable, j)) return j;
        }
        return cur;
    }

    private static boolean[] limit(boolean[] f, int n) {
        if (f == null || f.length <= n) return f;
        boolean[] g = new boolean[n];
        System.arraycopy(f, 0, g, 0, n);
        return g;
    }

    /** The focusable item of row r nearest to column col, or -1 if the row has none. */
    private static int pickInRow(int[] spans, boolean[] focusable, int[] rows, int[] cols, int r, int col) {
        int best = -1;
        for (int i = 0; i < spans.length; i++) {
            if (rows[i] != r || !foc(focusable, i)) continue;
            if (span(spans, i) == 2 || cols[i] == col) return i;
            if (best < 0) best = i;
        }
        return best;
    }

    /** The smallest change to oldTop that keeps focusRow in view, clamped to 0..max(0, rows - visibleRows). */
    static int scrollTop(int rows, int visibleRows, int focusRow, int oldTop) {
        int vis = Math.max(1, visibleRows);
        int top = oldTop;
        if (focusRow < top) top = focusRow;
        else if (focusRow >= top + vis) top = focusRow - vis + 1;
        int max = Math.max(0, rows - vis);
        return Math.max(0, Math.min(max, top));
    }

    /** A remembered focus index if it is in range and focusable, else the first focusable item, else STRIP. */
    static int remember(int[] spans, boolean[] focusable, int remembered) {
        int n = spans == null ? 0 : spans.length;
        if (remembered >= 0 && remembered < n && foc(focusable, remembered)) return remembered;
        return firstFocusable(limit(focusable, n));
    }
}
