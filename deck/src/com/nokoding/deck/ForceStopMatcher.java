package com.nokoding.deck;

import java.util.Locale;

/**
 * Finds the Force stop button of an App Info page and the positive button of its confirm
 * dialog, on a tiny node abstraction so it runs on a plain JVM. The accessibility service
 * wraps AccessibilityNodeInfo in Node. Experimental: nothing here has run on the TV.
 *
 * Walk rules: depth first, parents before children, at most 5000 nodes visited and at most
 * 40 levels deep (the root is level 0). A text node that is not clickable returns its nearest
 * clickable ancestor. A match by view id beats a match by text anywhere in the tree.
 *
 * findForceStop: id (last segment after ":id/") contains "force_stop" (this covers
 * force_stop_button, force_stop, button_force_stop), else text or description equal ignoring
 * case to "force stop" or "forcestop". "Force close" is not accepted. A disabled node is
 * returned too, the caller checks enabled().
 *
 * findConfirm: id last segment button1, dialog_button_positive, positive_button or button_ok,
 * else a clickable node whose text or description equals ignoring case ok, yes or confirm, and
 * only when none of those exists, one that says force stop. Never cancel, no or button2.
 *
 * state() rules, checked in this order:
 * 1. CONFIRM when the confirm node was found by a dialog id (button1 and friends) and the
 *    tree has some text or description containing "force stop" (the dialog message, the
 *    dialog button, or the App Info page behind the dialog). A dialog drawn over the page
 *    still contains the page's own Force stop node, so the dialog id wins.
 * 2. APP_INFO when findForceStop is found and enabled.
 * 3. CONFIRM when findForceStop is found but disabled and a confirm node exists by dialog id
 *    or by the text ok, yes or confirm (a dialog without ids, the page is greyed behind it).
 *    The text "force stop" is not a confirm text here, because the page button has it too.
 * 4. ALREADY_STOPPED when findForceStop is found but disabled.
 * 5. CONFIRM when findForceStop is null and findConfirm is found.
 * 6. UNKNOWN otherwise (also for a null root).
 * Known gap: a dialog without view ids whose positive button says "Force stop" over an enabled
 * page button reads as APP_INFO.
 */
final class ForceStopMatcher {
    private ForceStopMatcher() {}

    interface Node {
        String id();
        String text();
        String desc();
        boolean clickable();
        boolean enabled();
        int childCount();
        Node child(int i);
    }

    static final int UNKNOWN = 0, APP_INFO = 1, CONFIRM = 2, ALREADY_STOPPED = 3;
    static final int MAX_NODES = 5000, MAX_DEPTH = 40;

    /** What a search looks for. */
    private interface Rule {
        /** 2 = matches by id, 1 = matches by text, 0 = no match. */
        int rank(Node n);
    }

    private static final class Walk {
        final Rule rule;
        int visited;
        Node byId, byText;

        Walk(Rule rule) { this.rule = rule; }

        void go(Node n, Node clickableAncestor, int depth) {
            if (n == null || depth > MAX_DEPTH || visited >= MAX_NODES || byId != null) return;
            visited++;
            int r = rule.rank(n);
            if (r > 0) {
                Node hit = n.clickable() ? n : clickableAncestor;
                if (hit != null) {
                    if (r == 2) { byId = hit; return; }
                    if (byText == null) byText = hit;
                }
            }
            Node anc = n.clickable() ? n : clickableAncestor;
            int cc = n.childCount();
            for (int i = 0; i < cc && visited < MAX_NODES && byId == null; i++) go(n.child(i), anc, depth + 1);
        }

        Node result() { return byId != null ? byId : byText; }
    }

    private static Node search(Node root, Rule rule) {
        if (root == null) return null;
        Walk w = new Walk(rule);
        w.go(root, null, 0);
        return w.result();
    }

    private static final Rule FORCE_STOP = new Rule() {
        @Override public int rank(Node n) {
            String seg = idSegment(n.id());
            if (seg != null && seg.contains("force_stop")) return 2;
            if (isForceStopText(n.text()) || isForceStopText(n.desc())) return 1;
            return 0;
        }
    };

    private static final Rule CONFIRM_ALL = new Rule() {
        @Override public int rank(Node n) { return confirmRank(n, true); }
    };

    private static final Rule CONFIRM_NO_FS_TEXT = new Rule() {
        @Override public int rank(Node n) { return confirmRank(n, false); }
    };

    private static int confirmRank(Node n, boolean forceStopText) {
        if (isDialogId(idSegment(n.id()))) return 2;
        if (!n.clickable()) return 0;
        if (isConfirmText(n.text(), forceStopText) || isConfirmText(n.desc(), forceStopText)) return 1;
        return 0;
    }

    /** Lower case last path segment of a view id, or null. */
    static String idSegment(String id) {
        if (id == null || id.isEmpty()) return null;
        int i = id.indexOf(":id/");
        String seg;
        if (i >= 0) seg = id.substring(i + 4);
        else {
            int j = id.lastIndexOf('/');
            seg = j >= 0 ? id.substring(j + 1) : id;
        }
        return seg.toLowerCase(Locale.ROOT);
    }

    private static boolean isDialogId(String seg) {
        return "button1".equals(seg) || "dialog_button_positive".equals(seg)
                || "positive_button".equals(seg) || "button_ok".equals(seg);
    }

    private static boolean isForceStopText(String t) {
        if (t == null) return false;
        String s = t.trim();
        return s.equalsIgnoreCase("force stop") || s.equalsIgnoreCase("forcestop");
    }

    private static boolean isConfirmText(String t, boolean forceStopText) {
        if (t == null) return false;
        String s = t.trim();
        return s.equalsIgnoreCase("ok") || s.equalsIgnoreCase("yes") || s.equalsIgnoreCase("confirm")
                || (forceStopText && s.equalsIgnoreCase("force stop"));
    }

    /** The clickable Force stop button of an App Info page, or null. May be disabled. */
    static Node findForceStop(Node root) {
        return search(root, FORCE_STOP);
    }

    /** The positive button of the confirm dialog, or null. */
    static Node findConfirm(Node root) {
        Node n = search(root, CONFIRM_NO_FS_TEXT); // id, then ok, yes, confirm
        return n != null ? n : search(root, CONFIRM_ALL); // last resort: a button that says Force stop
    }

    /** UNKNOWN, APP_INFO, CONFIRM or ALREADY_STOPPED, by the rules in the class comment. */
    static int state(Node root) {
        if (root == null) return UNKNOWN;
        Node fs = findForceStop(root);
        Node byId = search(root, new Rule() {
            @Override public int rank(Node n) { return isDialogId(idSegment(n.id())) ? 2 : 0; }
        });
        if (byId != null && containsForceStopText(root)) return CONFIRM;
        if (fs != null && fs.enabled()) return APP_INFO;
        if (fs != null) {
            return search(root, CONFIRM_NO_FS_TEXT) != null ? CONFIRM : ALREADY_STOPPED;
        }
        return findConfirm(root) != null ? CONFIRM : UNKNOWN;
    }

    private static boolean containsForceStopText(Node root) {
        int[] count = {0};
        return containsForceStop(root, 0, count);
    }

    private static boolean containsForceStop(Node n, int depth, int[] count) {
        if (n == null || depth > MAX_DEPTH || count[0] >= MAX_NODES) return false;
        count[0]++;
        if (hasForceStop(n.text()) || hasForceStop(n.desc())) return true;
        int cc = n.childCount();
        for (int i = 0; i < cc && count[0] < MAX_NODES; i++) if (containsForceStop(n.child(i), depth + 1, count)) return true;
        return false;
    }

    private static boolean hasForceStop(String t) {
        return t != null && t.toLowerCase(Locale.ROOT).contains("force stop");
    }
}
