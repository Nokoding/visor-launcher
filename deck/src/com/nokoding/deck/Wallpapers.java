package com.nokoding.deck;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;

/**
 * 2.7 wallpaper packs. The wallpapers folder (files/wallpapers) holds pack folders, each with a
 * wallpaper.json and its images. A .zip dropped into the folder is unpacked into a pack folder by
 * PackZip with the WALLPAPERS rules (zip slip, size and file caps, one pack per zip, a .deck-source
 * marker so the same zip is not unpacked twice). A folder pushed by hand gets the same caps here.
 *
 * The saved choice is one string: "skyline" (the built-in panorama, the default), "off" (a plain
 * dark background) or "pack:NAME". Pure Java (java.io and org.json only).
 */
final class Wallpapers {
    private Wallpapers() {}

    static final String SKYLINE = "skyline", OFF = "off", PACK = "pack:";

    /** Pack folder names: letters, digits, space, dot, dash and underscore, up to 60 characters, no leading dot. */
    static boolean validName(String n) {
        if (n == null || n.isEmpty() || n.length() > 60 || n.startsWith(".")) return false;
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == ' ' || c == '.' || c == '-' || c == '_';
            if (!ok) return false;
        }
        return !n.contains("..");
    }

    static boolean isPack(String sel) { return sel != null && sel.startsWith(PACK) && sel.length() > PACK.length(); }

    static String packName(String sel) { return isPack(sel) ? sel.substring(PACK.length()) : ""; }

    static String sel(String packName) { return PACK + packName; }

    /** A saved choice that is not one of the three shapes reads as the built-in skyline. */
    static String normalize(String sel) {
        if (sel == null) return SKYLINE;
        if (sel.equals(OFF)) return OFF;
        if (isPack(sel) && validName(packName(sel))) return sel;
        return SKYLINE;
    }

    /** Pack folders, sorted, hidden and temp folders left out. */
    static String[] list(File dir) {
        ArrayList<String> out = new ArrayList<>();
        File[] subs = dir == null ? null : dir.listFiles();
        if (subs != null) for (File f : subs) {
            String n = f.getName();
            if (f.isDirectory() && validName(n)) out.add(n);
        }
        String[] a = out.toArray(new String[0]);
        Arrays.sort(a, String.CASE_INSENSITIVE_ORDER);
        return a;
    }

    static File packDir(File dir, String name) {
        if (dir == null || !validName(name)) return null;
        File d = new File(dir, name);
        return d.isDirectory() ? d : null;
    }

    /** Unpacks any new wallpaper pack zips. Disk work: call it off the main thread. */
    static ArrayList<PackZip.Result> installZips(File dir) {
        if (dir == null || !dir.isDirectory()) return new ArrayList<>();
        return PackZip.scan(dir, PackZip.WALLPAPERS);
    }

    /** Names of the files in a pack folder that count as pack files (images and the JSON), no dot files. */
    static ArrayList<File> packFiles(File packDir) {
        ArrayList<File> out = new ArrayList<>();
        File[] all = packDir == null ? null : packDir.listFiles();
        if (all != null) for (File f : all) {
            String n = f.getName();
            if (n.startsWith(".") || !f.isFile()) continue;
            if (PackZip.allowedFile(n, PackZip.WALLPAPERS)) out.add(f);
        }
        return out;
    }

    /**
     * Reads a pack folder: checks the caps (a folder pushed by hand did not go through PackZip), reads
     * wallpaper.json and checks it against the files that are there. Never throws. error is set when
     * the pack cannot be used.
     */
    static WallpaperSpec read(File packDir) {
        if (packDir == null || !packDir.isDirectory()) return WallpaperSpec.bad("PACK FOLDER IS MISSING");
        String folder = packDir.getName();
        try {
            ArrayList<File> files = packFiles(packDir);
            if (files.size() > PackZip.WALLPAPERS.maxFiles) return WallpaperSpec.bad("MORE THAN " + PackZip.WALLPAPERS.maxFiles + " FILES");
            long total = 0;
            boolean hasBig = false;
            File json = null;
            HashSet<String> names = new HashSet<>();
            for (File f : files) {
                String n = f.getName();
                long len = f.length();
                total += len;
                if (n.equalsIgnoreCase("wallpaper.json")) {
                    if (len > PackZip.MAX_JSON) return WallpaperSpec.bad("wallpaper.json IS OVER 64 KB");
                    json = f;
                } else {
                    if (len > PackZip.WALLPAPERS.capFor(n)) return WallpaperSpec.bad("FILE OVER " + (PackZip.WALLPAPERS.capFor(n) >> 20) + " MB: " + n);
                    if (len == 0) return WallpaperSpec.bad("EMPTY FILE: " + n);
                    names.add(n);
                    if (PackZip.WALLPAPERS.capFor(n) > PackZip.WALLPAPERS.maxFile) hasBig = true;
                }
            }
            long cap = PackZip.WALLPAPERS.totalFor(hasBig);
            if (total > cap) return WallpaperSpec.bad("OVER " + (cap >> 20) + " MB IN TOTAL");
            if (json == null) return WallpaperSpec.fallback(names, folder);
            String text = readText(json);
            if (text == null) return WallpaperSpec.bad("wallpaper.json COULD NOT BE READ");
            return WallpaperSpec.parse(text, names, folder);
        } catch (RuntimeException e) {
            return WallpaperSpec.bad("PACK COULD NOT BE READ");
        }
    }

    private static String readText(File f) {
        try (InputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) Math.min(f.length(), PackZip.MAX_JSON + 1)];
            int n = 0, k;
            while (n < b.length && (k = in.read(b, n, b.length - n)) > 0) n += k;
            return new String(b, 0, n, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    /** A file in the pack by the name the spec used (case insensitive), or null. */
    static File file(File packDir, String name) {
        if (packDir == null || name == null) return null;
        File f = new File(packDir, name);
        if (f.isFile()) return f;
        String want = name.toLowerCase(Locale.ROOT);
        String[] all = packDir.list();
        if (all != null) for (String s : all) if (s.toLowerCase(Locale.ROOT).equals(want) && !s.startsWith(".")) return new File(packDir, s);
        return null;
    }

    /** The names for the chooser: BUILT-IN SKYLINE, OFF, then each pack. */
    static String[] choices(String[] packs) {
        String[] c = new String[packs.length + 2];
        c[0] = "BUILT-IN SKYLINE";
        c[1] = "OFF";
        for (int i = 0; i < packs.length; i++) c[i + 2] = packs[i].toUpperCase(Locale.ROOT);
        return c;
    }

    /** Index in choices() for a saved choice. A pack that is gone reads as the skyline. */
    static int choiceIndex(String sel, String[] packs) {
        sel = normalize(sel);
        if (sel.equals(OFF)) return 1;
        if (isPack(sel)) for (int i = 0; i < packs.length; i++) if (packs[i].equals(packName(sel))) return i + 2;
        return 0;
    }

    /** The saved choice for a chooser index. */
    static String selFor(int index, String[] packs) {
        if (index == 1) return OFF;
        if (index >= 2 && index - 2 < packs.length) return sel(packs[index - 2]);
        return SKYLINE;
    }

}
