package com.nokoding.deck;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 2.8 control center: which tiles show and in what order (Setup, PS MENU). Pure Java.
 *
 * Saved as one plain ASCII string: "v1|ORDER|HIDDEN". ORDER is the tile ids of every section whose
 * order is not the default, comma separated, in order. HIDDEN is the hidden tile ids, comma
 * separated. Example: "v1|q.mute,q.volume|q.next". Everything default saves as "". Anything that
 * does not read back cleanly (wrong version, too long, empty input) parses as the defaults;
 * unknown or repeated ids are skipped. An id is always read in its own section, so a stray id can
 * never move a tile somewhere else. Headers and the tile y.setup can never be hidden.
 */
final class CcPrefs {
    static final String VERSION = "v1";
    static final int MAX_LEN = 1000;
    /** The tile that must stay reachable so Setup can always be opened. */
    static final String SETUP_ID = "y.setup";

    private final List<List<String>> order = new ArrayList<>();
    private final Set<String> hidden = new HashSet<>();

    private CcPrefs() { resetOrder(); }

    static CcPrefs defaults() { return new CcPrefs(); }

    private void resetOrder() {
        order.clear();
        for (int s = 0; s < CcModel.COUNT; s++) {
            List<String> l = new ArrayList<>();
            for (CcModel.Tile t : CcModel.defaults(s)) l.add(t.id);
            order.add(l);
        }
    }

    /** Never throws; bad input gives the defaults. */
    static CcPrefs parse(String s) {
        CcPrefs p = new CcPrefs();
        try {
            if (s == null || s.isEmpty() || s.length() > MAX_LEN) return p;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c < 32 || c > 126) return p;
            }
            String[] parts = s.split("\\|", -1);
            if (parts.length != 3 || !VERSION.equals(parts[0])) return p;
            // the saved order: ids per section in the order given, then whatever is missing in default order
            List<List<String>> given = new ArrayList<>();
            for (int i = 0; i < CcModel.COUNT; i++) given.add(new ArrayList<String>());
            Set<String> seen = new HashSet<>();
            for (String id : parts[1].split(",")) {
                CcModel.Tile t = CcModel.byId(id);
                if (t == null || !seen.add(id)) continue;
                given.get(t.section).add(id);
            }
            for (int sec = 0; sec < CcModel.COUNT; sec++) {
                List<String> l = given.get(sec);
                for (CcModel.Tile t : CcModel.defaults(sec)) if (!l.contains(t.id)) l.add(t.id);
                p.order.set(sec, l);
            }
            for (String id : parts[2].split(",")) {
                if (canHide(id)) p.hidden.add(id);
            }
        } catch (RuntimeException e) {
            return new CcPrefs();
        }
        return p;
    }

    /** True if the id is a known tile that may be hidden (not a header, not Setup). */
    static boolean canHide(String id) {
        CcModel.Tile t = CcModel.byId(id);
        return t != null && t.kind != CcModel.HEADER && !SETUP_ID.equals(id);
    }

    /** The string to store; empty when everything is default. */
    String save() {
        if (isDefault()) return "";
        StringBuilder o = new StringBuilder();
        for (int s = 0; s < CcModel.COUNT; s++) {
            if (orderIsDefault(s)) continue;
            for (String id : order.get(s)) { if (o.length() > 0) o.append(','); o.append(id); }
        }
        StringBuilder h = new StringBuilder();
        for (int s = 0; s < CcModel.COUNT; s++) {
            for (CcModel.Tile t : CcModel.defaults(s)) {
                if (hidden.contains(t.id)) { if (h.length() > 0) h.append(','); h.append(t.id); }
            }
        }
        return VERSION + "|" + o + "|" + h;
    }

    private boolean orderIsDefault(int s) {
        List<CcModel.Tile> d = CcModel.defaults(s);
        List<String> l = order.get(s);
        if (d.size() != l.size()) return false;
        for (int i = 0; i < d.size(); i++) if (!d.get(i).id.equals(l.get(i))) return false;
        return true;
    }

    boolean isDefault() {
        if (!hidden.isEmpty()) return false;
        for (int s = 0; s < CcModel.COUNT; s++) if (!orderIsDefault(s)) return false;
        return true;
    }

    boolean hidden(String id) { return id != null && hidden.contains(id); }

    /** Hides or shows a tile. Returns false (and changes nothing) for unknown ids, headers and y.setup. */
    boolean setHidden(String id, boolean h) {
        if (!canHide(id)) return false;
        if (h) hidden.add(id); else hidden.remove(id);
        return true;
    }

    boolean moveUp(String id) { return move(id, -1); }

    boolean moveDown(String id) { return move(id, 1); }

    private boolean move(String id, int d) {
        CcModel.Tile t = CcModel.byId(id);
        if (t == null) return false;
        List<String> l = order.get(t.section);
        int i = l.indexOf(id), j = i + d;
        if (i < 0 || j < 0 || j >= l.size()) return false;
        l.set(i, l.get(j));
        l.set(j, id);
        return true;
    }

    void reset() { hidden.clear(); resetOrder(); }

    /** All tiles of the section in the user's order, hidden ones included (empty for a bad section). */
    List<CcModel.Tile> order(int section) {
        List<CcModel.Tile> out = new ArrayList<>();
        if (section < 0 || section >= CcModel.COUNT) return out;
        for (String id : order.get(section)) out.add(CcModel.byId(id));
        return out;
    }

    /**
     * The tiles to show. QUICK and SYSTEM always show at least their default first tile; any other
     * section can come out empty (the caller then hides its tab).
     */
    List<CcModel.Tile> visible(int section) {
        List<CcModel.Tile> out = new ArrayList<>();
        for (CcModel.Tile t : order(section)) if (!hidden.contains(t.id)) out.add(t);
        if (out.isEmpty() && (section == CcModel.QUICK || section == CcModel.SYSTEM)) {
            out.add(CcModel.defaults(section).get(0));
        }
        return out;
    }
}
