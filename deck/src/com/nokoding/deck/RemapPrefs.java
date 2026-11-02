package com.nokoding.deck;

import android.content.SharedPreferences;

/** 2.6: glue between the saved remap rules in SharedPreferences and the pure classes. Used by HomeActivity and PsService. */
final class RemapPrefs {
    private RemapPrefs() {}

    static RemapTable.Sink sink(final SharedPreferences.Editor ed) {
        return new RemapTable.Sink() {
            @Override public void put(String key, String value) { ed.putString(key, value); }
            @Override public void putBool(String key, boolean value) { ed.putBoolean(key, value); }
            @Override public void remove(String key) { ed.remove(key); }
        };
    }

    /** Everything saved, as a table. Never throws: a failure reads as no rules. */
    static RemapTable table(SharedPreferences p) {
        try {
            return RemapTable.load(p.getAll());
        } catch (Throwable t) {
            return RemapTable.EMPTY;
        }
    }

    /** 2.8: the master switch (control center, CONTROLLER, REMAPS ON). Off turns every remap off without deleting any. */
    static final String KEY_MASTER = "rmMaster";

    static boolean master(SharedPreferences p) {
        try { return p.getBoolean(KEY_MASTER, true); } catch (Throwable t) { return true; }
    }

    /** The table the key handlers use: empty while the master switch is off. Setup screens keep using table(). */
    static RemapTable liveTable(SharedPreferences p) {
        return master(p) ? table(p) : RemapTable.EMPTY;
    }

    /** 2.9: the combos the key handlers use: none while the master switch is off. */
    static ComboTable liveCombos(SharedPreferences p) {
        if (!master(p)) return ComboTable.EMPTY;
        try { return ComboTable.parse(p.getString(ComboTable.PREF, "")); } catch (Throwable t) { return ComboTable.EMPTY; }
    }

    static int flags(SharedPreferences p) {
        int f = 0;
        if (p.getBoolean(RemapTable.KEY_ADV, false)) f |= RemapGate.F_ADVANCED;
        if (p.getBoolean(RemapTable.KEY_NAV, false)) f |= RemapGate.F_NAV;
        if (p.getBoolean(RemapTable.KEY_GAMES, false)) f |= RemapGate.F_GAMES;
        if (p.getBoolean(RemapTable.KEY_HOME, false)) f |= RemapGate.F_HOME;
        return f;
    }

    static boolean watches(String prefKey) {
        return prefKey != null && (prefKey.startsWith(RemapTable.PREFIX) || prefKey.equals(RemapTable.KEY_ADV)
                || prefKey.equals(RemapTable.KEY_NAV) || prefKey.equals(RemapTable.KEY_HOME) || prefKey.equals(RemapTable.KEY_GAMES) || prefKey.equals(KEY_MASTER) || prefKey.equals(ComboTable.PREF));
    }

    /** The escape and reset_remap: clears the navigation remaps. @return true if anything was cleared. */
    static boolean escape(SharedPreferences p) {
        boolean had = p.getBoolean(RemapTable.KEY_NAV, false);
        SharedPreferences.Editor ed = p.edit();
        int n = RemapTable.clearNav(sink(ed), p.getAll());
        ed.apply();
        return n > 0 || had;
    }

    /** 2.17 Home escape: five quick Home presses switched the Home remap off. */
    static void homeEscape(SharedPreferences p) {
        SharedPreferences.Editor ed = p.edit();
        RemapTable.clearHome(sink(ed), p.getAll());
        ed.apply();
    }

    /** RESET ALL REMAPS and reset_remap. @return how many rules were removed. */
    static int resetAll(SharedPreferences p) {
        SharedPreferences.Editor ed = p.edit();
        int n = RemapTable.resetAll(sink(ed), p.getAll());
        // 2.9: the combos go too, so RESET ALL REMAPS and the adb command leave nothing behind
        try {
            ComboTable t = ComboTable.parse(p.getString(ComboTable.PREF, ""));
            n += t.size();
        } catch (Throwable ignored) {}
        ed.remove(ComboTable.PREF);
        ed.apply();
        return n;
    }
}
