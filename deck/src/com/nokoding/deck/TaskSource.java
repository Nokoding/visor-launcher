package com.nokoding.deck;

import android.app.ActivityManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 * 2.8 task manager, the Android side. Lists the apps used recently from Android's usage history
 * (PACKAGE_USAGE_STATS, the same one-time adb grant as the system recents source), kills background
 * processes and opens App Info. getRunningAppProcesses is not used: on modern Android it only returns
 * Deck's own process, so it cannot list other apps.
 *
 * RAM: only what Android reports for the whole box (ActivityManager.MemoryInfo). No per app numbers are
 * shown, because Android does not hand them to a normal app and Deck does not invent them.
 */
final class TaskSource {
    private TaskSource() {}

    /**
     * Apps used in the past week, newest first, marked and cut by TaskList.build.
     * @param store    Deck's app list (only launchable apps are shown, which also keeps icons working)
     * @param fgPkg    the app in front, or null when unknown (inside Deck: Deck itself is in front)
     * @param protect  the full protect list (user list plus defaults)
     */
    static List<TaskList.Entry> recent(Context c, AppStore store, String fgPkg, Set<String> protect, int max) {
        ArrayList<TaskList.Entry> in = new ArrayList<>();
        if (store == null || !SysUsage.granted(c)) return TaskList.build(in, fgPkg, c.getPackageName(), protect, max);
        try {
            HashMap<String, AppStore.App> byPkg = new HashMap<>();
            for (AppStore.App a : store.all) {
                if (a.system && !a.leanback) continue;
                if (!byPkg.containsKey(a.cn.getPackageName())) byPkg.put(a.cn.getPackageName(), a);
            }
            UsageStatsManager um = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
            long now = System.currentTimeMillis();
            List<UsageStats> l = um == null ? null : um.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 7 * 86400000L, now);
            if (l != null) for (UsageStats u : l) {
                AppStore.App a = byPkg.get(u.getPackageName());
                long t = u.getLastTimeUsed();
                if (a == null || t <= 0 || u.getTotalTimeInForeground() <= 0) continue;
                if (isPlainSystem(c, u.getPackageName())) continue;
                in.add(new TaskList.Entry(u.getPackageName(), a.key, a.label, t));
            }
        } catch (Throwable ignored) {
            // not granted or not supported: an empty list, and the page says so
        }
        return TaskList.build(in, fgPkg, c.getPackageName(), protect, max);
    }

    /** Package names of apps used in the past week, newest first, plain system apps left out. Needs the usage grant. */
    static List<String> recentPackages(Context c) {
        ArrayList<String> out = new ArrayList<>();
        try {
            if (!SysUsage.granted(c)) return out;
            UsageStatsManager um = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
            long now = System.currentTimeMillis();
            List<UsageStats> l = um == null ? null : um.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 7 * 86400000L, now);
            if (l == null) return out;
            java.util.Collections.sort(l, (x, y) -> Long.compare(y.getLastTimeUsed(), x.getLastTimeUsed()));
            for (UsageStats u : l) {
                String p = u.getPackageName();
                if (u.getLastTimeUsed() <= 0 || u.getTotalTimeInForeground() <= 0 || out.contains(p) || isPlainSystem(c, p)) continue;
                out.add(p);
            }
        } catch (Throwable ignored) {}
        return out;
    }

    /** A system app that was never updated (settings, the launcher, services): not for the task list. */
    private static boolean isPlainSystem(Context c, String pkg) {
        try {
            int f = c.getPackageManager().getApplicationInfo(pkg, 0).flags;
            return (f & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0 && (f & android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
        } catch (Throwable t) {
            return false;
        }
    }

    /** ActivityManager.killBackgroundProcesses. Only cached and background processes die. @return false if Android refused. */
    static boolean kill(Context c, String pkg) {
        if (!KillCmd.validPackage(pkg)) return false;
        try {
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return false;
            am.killBackgroundProcesses(pkg);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Android's App Info page for an app. */
    static Intent appInfoIntent(String pkg) {
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    static boolean appInfo(Context c, String pkg) {
        if (!KillCmd.validPackage(pkg)) return false;
        try {
            c.startActivity(appInfoIntent(pkg));
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** "RAM 640 OF 1400 MB FREE" from ActivityManager.MemoryInfo, or "NOT AVAILABLE". */
    static String ramText(Context c) {
        try {
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            long tot = mi.totalMem >> 20, av = mi.availMem >> 20;
            return av + " OF " + tot + " MB FREE" + (mi.lowMemory ? "  LOW" : "");
        } catch (Throwable t) {
            return "NOT AVAILABLE";
        }
    }

}
