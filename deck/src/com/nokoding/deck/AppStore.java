package com.nokoding.deck;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/** App discovery plus persisted order, hidden and pinned state. */
final class AppStore {

    static final class App {
        final String key;          // flattened component name
        final ComponentName cn;
        final String label;
        final boolean leanback;
        final boolean system;
        App(ComponentName cn, String label, boolean leanback, boolean system) {
            this.cn = cn;
            this.key = cn.flattenToShortString();
            this.label = label == null || label.trim().isEmpty() ? cn.getPackageName() : label.trim();
            this.leanback = leanback;
            this.system = system;
        }
        boolean sameAs(App o) {
            return o != null && key.equals(o.key) && label.equals(o.label)
                    && leanback == o.leanback && system == o.system;
        }
    }

    static final class Input {
        final String id, label;
        Input(String id, String label) { this.id = id; this.label = label; }
    }

    static final int MAX_DOCK_APPS = 4;

    private final Context ctx;
    final SharedPreferences prefs;
    final ArrayList<App> all = new ArrayList<>();
    final ArrayList<String> order = new ArrayList<>();
    final HashSet<String> hidden = new HashSet<>();
    final ArrayList<String> pinned = new ArrayList<>();
    /** Packages the user added by hand with Add app (may lack a launcher category). */
    final ArrayList<String> extras = new ArrayList<>();
    final ArrayList<Input> inputs = new ArrayList<>();

    AppStore(Context ctx, SharedPreferences prefs) {
        this.ctx = ctx.getApplicationContext();
        this.prefs = prefs;
        split(prefs.getString("order", ""), order);
        ArrayList<String> tmp = new ArrayList<>();
        split(prefs.getString("hidden", ""), tmp);
        hidden.addAll(tmp);
        split(prefs.getString("pinned", ""), pinned);
        split(prefs.getString("extras", ""), extras);
    }

    private static void split(String s, List<String> out) { OrderList.split(s, out); }

    private static String join(Iterable<String> list) { return OrderList.join(list); }

    void saveLayout() {
        prefs.edit().putString("order", join(order)).putString("hidden", join(hidden))
                .putString("pinned", join(pinned)).putString("extras", join(extras)).apply();
    }

    void resetLayout() {
        order.clear();
        hidden.clear();
        pinned.clear();
        extras.clear();
        saveLayout();
        sortAll();
    }

    // ---------- cached list for a fast first frame ----------

    boolean loadCached() {
        String s = prefs.getString("cache", "");
        if (s == null || s.isEmpty()) return false;
        ArrayList<App> list = new ArrayList<>();
        for (String line : s.split("\n")) {
            String[] f = line.split("\t");
            if (f.length < 3) continue;
            ComponentName cn = ComponentName.unflattenFromString(f[0]);
            if (cn == null) continue;
            list.add(new App(cn, f[1], f[2].indexOf('L') >= 0, f[2].indexOf('S') >= 0));
        }
        if (list.isEmpty()) return false;
        all.clear();
        all.addAll(list);
        sortAll();
        return true;
    }

    private void saveCache() {
        StringBuilder sb = new StringBuilder();
        for (App a : all) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(a.cn.flattenToString()).append('\t')
              .append(a.label.replace('\t', ' ').replace('\n', ' ')).append('\t')
              .append(a.leanback ? "L" : "-").append(a.system ? "S" : "-");
        }
        prefs.edit().putString("cache", sb.toString()).apply();
    }

    // ---------- discovery (safe to call on a background thread) ----------

    /**
     * Leanback and phone launcher entries, MAIN+INFO entries, hand added packages and,
     * with showAll, every installed package that has any launch intent.
     */
    static ArrayList<App> query(Context ctx, boolean showAll, List<String> extraPkgs) {
        PackageManager pm = ctx.getPackageManager();
        String self = ctx.getPackageName();
        HashMap<String, Boolean> seenPkg = new HashMap<>();
        ArrayList<App> out = new ArrayList<>();
        addQuery(pm, self, Intent.CATEGORY_LEANBACK_LAUNCHER, true, seenPkg, out);
        addQuery(pm, self, Intent.CATEGORY_LAUNCHER, false, seenPkg, out);
        addQuery(pm, self, Intent.CATEGORY_INFO, false, seenPkg, out);
        if (extraPkgs != null) for (String pkg : extraPkgs) addPackage(pm, self, pkg, seenPkg, out);
        if (showAll) {
            List<ApplicationInfo> all = installed(pm);
            for (ApplicationInfo ai : all) if (ai != null) addPackage(pm, self, ai.packageName, seenPkg, out);
        }
        return out;
    }

    /** Every launchable app, sorted by label (for the Add app picker). */
    static ArrayList<App> scanAll(Context ctx) {
        ArrayList<App> out = query(ctx, true, null);
        Collections.sort(out, (x, y) -> x.label.compareToIgnoreCase(y.label));
        return out;
    }

    @SuppressWarnings("deprecation")
    private static List<ApplicationInfo> installed(PackageManager pm) {
        try {
            List<ApplicationInfo> l = pm.getInstalledApplications(0);
            return l == null ? new ArrayList<ApplicationInfo>() : l;
        } catch (Throwable t) {
            return new ArrayList<>();
        }
    }

    private static void addPackage(PackageManager pm, String self, String pkg,
                                   HashMap<String, Boolean> seenPkg, ArrayList<App> out) {
        if (pkg == null || pkg.equals(self) || seenPkg.containsKey(pkg)) return;
        try {
            boolean lb = true;
            Intent li = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (li == null) { lb = false; li = pm.getLaunchIntentForPackage(pkg); }
            if (li == null || li.getComponent() == null) return;
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            if (!ai.enabled) return;
            CharSequence label = pm.getApplicationLabel(ai);
            boolean system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            out.add(new App(li.getComponent(), label == null ? null : label.toString(), lb, system));
            seenPkg.put(pkg, Boolean.TRUE);
        } catch (Throwable ignored) {
            // unknown or broken package: skip
        }
    }

    void addExtra(App a) {
        String pkg = a.cn.getPackageName();
        if (!extras.contains(pkg)) extras.add(pkg);
        if (find(a.key) == null) all.add(a);
        hidden.remove(a.key);
        saveLayout();
        sortAll();
        saveCache();
    }

    boolean removeExtra(String pkg) {
        boolean r = extras.remove(pkg);
        if (r) saveLayout();
        return r;
    }

    private static void addQuery(PackageManager pm, String self, String category, boolean leanback,
                                 HashMap<String, Boolean> seenPkg, ArrayList<App> out) {
        List<ResolveInfo> list;
        try {
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(category);
            list = pm.queryIntentActivities(i, 0);
        } catch (Throwable t) {
            return;
        }
        if (list == null) return;
        for (ResolveInfo ri : list) {
            try {
                if (ri.activityInfo == null) continue;
                String pkg = ri.activityInfo.packageName;
                if (pkg == null || pkg.equals(self)) continue;
                if (!leanback && seenPkg.containsKey(pkg)) continue; // leanback entry wins
                if (leanback && seenPkg.containsKey(pkg)) continue;  // one tile per package
                ApplicationInfo ai = ri.activityInfo.applicationInfo;
                if (ai != null && !ai.enabled) continue;
                CharSequence label = ri.loadLabel(pm);
                boolean system = ai != null && (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                out.add(new App(new ComponentName(pkg, ri.activityInfo.name),
                        label == null ? null : label.toString(), leanback, system));
                seenPkg.put(pkg, Boolean.TRUE);
            } catch (Throwable ignored) {
                // A broken package must not break the list.
            }
        }
    }

    static ArrayList<Input> queryInputs(Context ctx) {
        ArrayList<Input> out = new ArrayList<>();
        try {
            if (!ctx.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LIVE_TV)) return out;
            TvInputManager tim = (TvInputManager) ctx.getSystemService(Context.TV_INPUT_SERVICE);
            if (tim == null) return out;
            List<TvInputInfo> list = tim.getTvInputList();
            if (list == null) return out;
            for (TvInputInfo info : list) {
                try {
                    if (info == null || !info.isPassthroughInput()) continue;
                    CharSequence l = info.loadLabel(ctx);
                    out.add(new Input(info.getId(), l == null ? info.getId() : l.toString()));
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return out;
    }

    /** Apply a fresh query on the main thread. @return true if anything changed. */
    boolean apply(ArrayList<App> fresh, ArrayList<Input> freshInputs) {
        boolean changed = fresh.size() != all.size();
        if (!changed) {
            HashMap<String, App> map = new HashMap<>();
            for (App a : all) map.put(a.key, a);
            for (App a : fresh) if (!a.sameAs(map.get(a.key))) { changed = true; break; }
        }
        boolean inputsChanged = freshInputs.size() != inputs.size();
        if (!inputsChanged) {
            for (int i = 0; i < inputs.size(); i++) {
                if (!inputs.get(i).id.equals(freshInputs.get(i).id)) { inputsChanged = true; break; }
            }
        }
        inputs.clear();
        inputs.addAll(freshInputs);
        if (!changed) return inputsChanged;
        all.clear();
        all.addAll(fresh);
        sortAll();
        saveCache();
        return true;
    }

    private void sortAll() {
        final HashMap<String, Integer> pos = new HashMap<>();
        for (int i = 0; i < order.size(); i++) pos.put(order.get(i), i);
        Collections.sort(all, (x, y) -> {
            Integer px = pos.get(x.key), py = pos.get(y.key);
            if (px != null && py != null) return px - py;
            if (px != null) return -1;
            if (py != null) return 1;
            return x.label.toLowerCase(Locale.ROOT).compareTo(y.label.toLowerCase(Locale.ROOT));
        });
    }

    App find(String key) {
        if (key == null) return null;
        for (App a : all) if (a.key.equals(key)) return a;
        return null;
    }

    ArrayList<App> visible(boolean showHidden) {
        ArrayList<App> out = new ArrayList<>();
        for (App a : all) if (showHidden || !hidden.contains(a.key)) out.add(a);
        return out;
    }

    ArrayList<App> dockApps() {
        ArrayList<App> out = new ArrayList<>();
        for (String k : pinned) {
            App a = find(k);
            if (a != null) out.add(a);
            if (out.size() >= MAX_DOCK_APPS) break;
        }
        return out;
    }

    /** Move an app one step inside the given visible list, then persist the full order. */
    boolean move(ArrayList<App> visibleList, String key, int delta) {
        int i = -1;
        for (int k = 0; k < visibleList.size(); k++) if (visibleList.get(k).key.equals(key)) { i = k; break; }
        int j = i + delta;
        if (i < 0 || j < 0 || j >= visibleList.size()) return false;
        Collections.swap(visibleList, i, j);
        order.clear();
        for (App a : visibleList) order.add(a.key);
        for (App a : all) if (!order.contains(a.key)) order.add(a.key);
        saveLayout();
        sortAll();
        return true;
    }

    boolean movePinned(String key, int delta) {
        // move among the pins that are still installed, so gaps from removed apps do not swallow a press
        ArrayList<String> live = new ArrayList<>();
        for (String k : pinned) if (find(k) != null) live.add(k);
        if (!OrderList.move(live, key, delta)) return false;
        for (String k : pinned) if (!live.contains(k)) live.add(k);
        pinned.clear();
        pinned.addAll(live);
        saveLayout();
        return true;
    }

    void setHidden(String key, boolean h) {
        if (h) hidden.add(key); else hidden.remove(key);
        saveLayout();
    }

    /** @return false if the dock is full. */
    boolean setPinned(String key, boolean p) {
        if (p) {
            ArrayList<String> live = new ArrayList<>();
            for (App a : all) live.add(a.key);
            if (OrderList.pin(pinned, key, MAX_DOCK_APPS, live) < 0) return false;
        } else {
            OrderList.unpin(pinned, key);
        }
        saveLayout();
        return true;
    }
}
