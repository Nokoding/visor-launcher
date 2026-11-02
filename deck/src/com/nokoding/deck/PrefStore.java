package com.nokoding.deck;

import android.content.SharedPreferences;

/** 2.7: AnimSpec.Store over SharedPreferences, for the wallpaper options (the screensaver reads them too). */
final class PrefStore implements AnimSpec.Store {
    private final SharedPreferences p;

    PrefStore(SharedPreferences p) { this.p = p; }

    @Override public String get(String k) {
        try { return p.getString(k, null); } catch (Throwable t) { return null; }
    }

    @Override public void put(String k, String v) { p.edit().putString(k, v).apply(); }

    @Override public void remove(String k) { p.edit().remove(k).apply(); }
}
