package com.nokoding.deck;

import java.util.HashSet;
import java.util.Set;

/**
 * 2.9: what the user has seen in the tutorial, and the first run rule. Pure Java.
 *
 * Saved as one string in the prefs key {@link #PREF}: "v1|FINISHED|DISMISSED|id,id,id" where FINISHED
 * is the content version the user finished (0 = never), DISMISSED is 0 or 1 and the ids are the
 * lessons seen, in lesson order. Parsing never throws: null, empty, garbage, a wrong version or a
 * huge string all give "nothing seen, never finished". Unknown ids and duplicates are dropped.
 */
final class TutorialProgress {
    static final String PREF = "tutorial";
    /** Bump when the lessons change enough that people should be offered the tutorial again. */
    static final int CONTENT_VERSION = 1;

    private static final String VERSION = "v1";
    private static final int MAX_LEN = 4096;

    private final Set<String> seen = new HashSet<>();
    private int finishedVersion;
    private boolean dismissed;

    private TutorialProgress() {}

    /** Parses a saved string. Always returns an object. */
    static TutorialProgress parse(String s) {
        TutorialProgress p = new TutorialProgress();
        if (s == null || s.isEmpty() || s.length() > MAX_LEN) return p;
        String[] parts = s.split("\\|", -1);
        if (parts.length != 4 || !VERSION.equals(parts[0])) return p;
        int fin = parseSmallInt(parts[1]);
        if (fin < 0) return p;
        if (!"0".equals(parts[2]) && !"1".equals(parts[2])) return p;
        p.finishedVersion = fin;
        p.dismissed = "1".equals(parts[2]);
        for (String id : parts[3].split(",")) {
            if (TutorialModel.indexOf(id) >= 0) p.seen.add(id);
        }
        return p;
    }

    private static int parseSmallInt(String s) {
        if (s.isEmpty() || s.length() > 6) return -1;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') return -1;
        }
        return Integer.parseInt(s);
    }

    String save() {
        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < TutorialModel.count(); i++) {
            String id = TutorialModel.get(i).id;
            if (!seen.contains(id)) continue;
            if (ids.length() > 0) ids.append(',');
            ids.append(id);
        }
        return VERSION + "|" + finishedVersion + "|" + (dismissed ? "1" : "0") + "|" + ids;
    }

    boolean seen(String id) {
        return id != null && seen.contains(id);
    }

    /** Marks a lesson as seen. Unknown ids are ignored. */
    void mark(String id) {
        if (TutorialModel.indexOf(id) >= 0) seen.add(id);
    }

    int doneCount() {
        return seen.size();
    }

    boolean allSeen() {
        return seen.size() == TutorialModel.count();
    }

    /** Forgets which lessons were seen. The first run rule (finished, skipped) is left alone. */
    void reset() {
        seen.clear();
    }

    /** True when Deck should show the tutorial by itself: never finished (this version) and never skipped. */
    boolean shouldOffer() {
        return finishedVersion < CONTENT_VERSION && !dismissed;
    }

    /** The user got to the end of the tutorial. */
    void finish() {
        finishedVersion = CONTENT_VERSION;
    }

    /** The user pressed skip: stop offering it. What was seen stays as it was. */
    void dismiss() {
        dismissed = true;
    }

    boolean dismissed() {
        return dismissed;
    }

    /** Index of the first lesson not seen, or 0 when all or none are seen. */
    int resumeIndex() {
        if (seen.isEmpty() || allSeen()) return 0;
        for (int i = 0; i < TutorialModel.count(); i++) {
            if (!seen.contains(TutorialModel.get(i).id)) return i;
        }
        return 0;
    }
}
