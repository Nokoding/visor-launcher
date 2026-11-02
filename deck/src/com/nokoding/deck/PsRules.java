package com.nokoding.deck;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 2.4 pass-through list: which apps keep the PS button for themselves. Pure Java.
 *
 * Default: apps Android marks as games (ApplicationInfo.CATEGORY_GAME or the old FLAG_IS_GAME)
 * keep it, everything else gives it to Deck. "Games keep the PS button by default" (psGames,
 * on) turns the game rule off. The user can flip any app; only flips away from the default
 * are stored, in two lists of package names, one per line, sorted:
 *   psPass  apps that keep the PS button although the default says Deck
 *   psDeck  apps that give it to Deck although the default says the app (games)
 * Deck itself always takes it while it is in front, whatever the lists say.
 */
final class PsRules {
    static final String KEY_PASS = "psPass", KEY_DECK = "psDeck", KEY_GAMES = "psGames";
    /** android.content.pm.ApplicationInfo values (no Android imports here). */
    static final int CATEGORY_GAME = 0, FLAG_IS_GAME = 1 << 25;
    static final int MAX_ENTRIES = 2000;

    final HashSet<String> pass = new HashSet<>(), deck = new HashSet<>();
    boolean gameDefault = true;

    static boolean isGame(int category, int flags) {
        return category == CATEGORY_GAME || (flags & FLAG_IS_GAME) != 0;
    }

    /** Package names only: letters, digits, dot and underscore, starting with a letter. */
    static boolean plainPackage(String s) {
        if (s == null || s.isEmpty() || s.length() > 200) return false;
        if (!Character.isLetter(s.charAt(0))) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!(c == '.' || c == '_' || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9'))) return false;
        }
        return true;
    }

    static PsRules load(String passText, String deckText, boolean games) {
        PsRules r = new PsRules();
        read(passText, r.pass);
        read(deckText, r.deck);
        r.deck.removeAll(r.pass);   // a broken store with both: the app keeps it (never trap a game)
        r.gameDefault = games;
        return r;
    }

    private static void read(String s, Set<String> out) {
        if (s == null || s.isEmpty()) return;
        for (String line : s.split("\n")) {
            String p = line.trim();
            if (plainPackage(p) && out.size() < MAX_ENTRIES) out.add(p);
        }
    }

    static String write(Collection<String> set) {
        ArrayList<String> l = new ArrayList<>(set);
        Collections.sort(l);
        return OrderList.join(l);
    }

    String passText() { return write(pass); }

    String deckText() { return write(deck); }

    /** What the default says for this app: true = the app keeps the PS button. */
    boolean defaultKeeps(boolean isGame) { return isGame && gameDefault; }

    /** True if the app keeps the PS button (Deck lets it through). */
    boolean keeps(String pkg, boolean isGame) {
        if (pkg == null) return false;
        if (pass.contains(pkg)) return true;
        if (deck.contains(pkg)) return false;
        return defaultKeeps(isGame);
    }

    /** True if the user flipped this app away from the default. */
    boolean changed(String pkg, boolean isGame) {
        return pkg != null && keeps(pkg, isGame) != defaultKeeps(isGame);
    }

    /**
     * Set one app. Stores only a difference from the default. @return true if the stored lists
     * changed.
     */
    boolean set(String pkg, boolean isGame, boolean appKeeps) {
        if (!plainPackage(pkg)) return false;
        boolean wasPass = pass.contains(pkg), wasDeck = deck.contains(pkg);
        pass.remove(pkg);
        deck.remove(pkg);
        if (appKeeps != defaultKeeps(isGame)) {
            if (pass.size() + deck.size() >= MAX_ENTRIES) {
                if (wasPass) pass.add(pkg);
                if (wasDeck) deck.add(pkg);
                return false;
            }
            (appKeeps ? pass : deck).add(pkg);
        }
        return wasPass != pass.contains(pkg) || wasDeck != deck.contains(pkg);
    }

    /** Flip one app. @return the new state, true = the app keeps the PS button. */
    boolean toggle(String pkg, boolean isGame) {
        boolean now = !keeps(pkg, isGame);
        set(pkg, isGame, now);
        return keeps(pkg, isGame);
    }

    /**
     * Every package that keeps the PS button, for the service's one set lookup.
     * @param games packages Android marks as games
     */
    HashSet<String> effective(Collection<String> games) {
        HashSet<String> out = new HashSet<>();
        if (gameDefault && games != null) out.addAll(games);
        out.removeAll(deck);
        out.addAll(pass);
        return out;
    }
}
