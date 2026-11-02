package com.nokoding.deck;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Icon packs (2.2). The icons folder (files/icons) holds single custom icons named
 * <package>.png or <package>.webp, and pack folders, each full of files named the same way.
 * A .zip dropped into the icons folder is unpacked into a pack folder by PackZip with the
 * ICONS rules. Precedence for one app: a single file in the icons folder, then the chosen
 * pack, then the app's own art. Pure Java (java.io only).
 */
final class IconPacks {
    private IconPacks() {}

    /** Pack folders in the icons folder, sorted, hidden and temp folders left out. */
    static String[] list(File iconsDir) {
        ArrayList<String> out = new ArrayList<>();
        File[] subs = iconsDir == null ? null : iconsDir.listFiles();
        if (subs != null) for (File f : subs) {
            String n = f.getName();
            if (f.isDirectory() && !n.startsWith(".") && SoundMap.plainName(n)) out.add(n);
        }
        String[] a = out.toArray(new String[0]);
        Arrays.sort(a, String.CASE_INSENSITIVE_ORDER);
        return a;
    }

    /** The package name is used as a file name, so it must be a plain name. */
    static boolean validPackage(String pkg) {
        if (pkg == null || pkg.isEmpty() || pkg.length() > 200) return false;
        for (int i = 0; i < pkg.length(); i++) {
            char c = pkg.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '.' || c == '_';
            if (!ok) return false;
        }
        return !pkg.startsWith(".") && !pkg.contains("..");
    }

    /** <package>.png, else <package>.webp, directly inside dir, or null. */
    static File image(File dir, String pkg) {
        if (dir == null || !validPackage(pkg)) return null;
        File f = new File(dir, pkg + ".png");
        if (f.isFile()) return f;
        f = new File(dir, pkg + ".webp");
        return f.isFile() ? f : null;
    }

    /** The chosen pack's folder, or null for none or a pack that is not there. */
    static File packDir(File iconsDir, String pack) {
        if (iconsDir == null || pack == null || pack.isEmpty() || pack.startsWith(".") || !SoundMap.plainName(pack)) return null;
        File d = new File(iconsDir, pack);
        return d.isDirectory() ? d : null;
    }

    static final int NONE = 0, SINGLE = 1, PACK = 2;

    /** Where the custom image for pkg comes from: SINGLE (a file in the icons folder) wins over PACK. */
    static int source(File iconsDir, String pack, String pkg) {
        if (image(iconsDir, pkg) != null) return SINGLE;
        if (image(packDir(iconsDir, pack), pkg) != null) return PACK;
        return NONE;
    }

    /** The custom image for pkg after precedence, or null to use the app's own icon. */
    static File pick(File iconsDir, String pack, String pkg) {
        File f = image(iconsDir, pkg);
        if (f != null) return f;
        return image(packDir(iconsDir, pack), pkg);
    }

    /** Number of usable images in a pack folder, for Setup. */
    static int count(File dir) {
        String[] names = dir == null ? null : dir.list();
        int n = 0;
        if (names != null) for (String s : names) {
            if (!s.startsWith(".") && PackZip.isMedia(s, PackZip.ICONS) && PackZip.allowedFile(s, PackZip.ICONS)) n++;
        }
        return n;
    }

    /** Unpacks any new icon pack zips. Disk work: call it off the main thread. */
    static ArrayList<PackZip.Result> installZips(File iconsDir) {
        if (iconsDir == null || !iconsDir.isDirectory()) return new ArrayList<>();
        return PackZip.scan(iconsDir, PackZip.ICONS);
    }
}
