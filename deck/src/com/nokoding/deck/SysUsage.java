package com.nokoding.deck;

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Build;
import android.os.Process;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Recents source B: Android's own usage history. Needs PACKAGE_USAGE_STATS in the manifest
 * and the user to grant it once from adb:
 *   adb shell appops set com.nokoding.deck GET_USAGE_STATS allow
 * Without the grant queryUsageStats just returns nothing, so Deck falls back to source A.
 */
final class SysUsage {
    private SysUsage() {}

    static final String GRANT = "adb shell appops set com.nokoding.deck GET_USAGE_STATS allow";

    @SuppressWarnings("deprecation")
    static boolean granted(Context c) {
        try {
            AppOpsManager ao = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
            if (ao == null) return false;
            int m = Build.VERSION.SDK_INT >= 29
                    ? ao.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName())
                    : ao.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName());
            return m == AppOpsManager.MODE_ALLOWED;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Last use per app over the past few days, as Recents entries keyed by app key.
     * pkgToKey maps a package to the app key Deck shows for it. Run off the main thread.
     */
    static List<Recents.Entry> query(Context c, HashMap<String, String> pkgToKey, int days) {
        ArrayList<Recents.Entry> out = new ArrayList<>();
        try {
            UsageStatsManager um = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
            if (um == null) return out;
            long now = System.currentTimeMillis();
            List<UsageStats> l = um.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - days * 86400000L, now);
            if (l == null) return out;
            for (UsageStats u : l) {
                String key = pkgToKey.get(u.getPackageName());
                long t = u.getLastTimeUsed();
                if (key != null && t > 0 && u.getTotalTimeInForeground() > 0) out.add(new Recents.Entry(key, t));
            }
        } catch (Throwable ignored) {
            // not granted or not supported: empty
        }
        return out;
    }
}
