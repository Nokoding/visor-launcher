package com.nokoding.deck;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Installs a sound pack (or, since 2.2, an icon pack, since 2.7 a wallpaper pack) from a .zip dropped into its folder.
 * Pure Java (java.util.zip), so the JVM tests cover the safety rules:
 * - any entry that could escape the target (.., absolute, drive letter, NUL) rejects the whole zip
 * - only the pack's file types are copied (sound: .wav .ogg .mp3 and pack.json; icons: .png .webp;
 *   wallpapers: .png .jpg .jpeg .webp .gif and wallpaper.json);
 *   folders, dot files and __MACOSX are skipped
 * - files at the zip root, or all inside one top folder, nothing deeper
 * - caps on file count, per file size, total size and zip size, counted while copying
 * Extraction goes to a hidden temp folder first and is renamed into place at the end.
 */
final class PackZip {
    private PackZip() {}

    static final int MAX_FILES = 64;
    static final long MAX_FILE = 1024 * 1024;          // same as Sounds.MAX_FILE
    static final long MAX_JSON = 64 * 1024;
    static final long MAX_TOTAL = 16L * 1024 * 1024;   // all copied bytes together
    static final long MAX_ZIP = 32L * 1024 * 1024;
    static final String MARKER = ".deck-source";
    static final String TMP_PREFIX = ".unpack-";

    static final int UNSAFE = -1, SKIP = 0, KEEP = 1;

    /** What a kind of pack may contain and how big it may be. */
    static final class Rules {
        final String what;
        final String[] exts;
        final boolean json;
        /** The one JSON file a pack may carry (pack.json, wallpaper.json), lower case. */
        final String jsonName;
        final int maxFiles;
        final long maxFile, maxTotal, maxZip;
        /** 2.17: one extension may be bigger than the rest (a .mp4 in a wallpaper pack). "" for none. */
        String bigExt = "";
        long bigFile;
        Rules big(String ext, long bytes) { bigExt = ext; bigFile = bytes; return this; }
        /** The size cap of one file by its name. */
        /** The most one whole pack may hold: the usual total, or the big file plus one image's worth when a big file is in it. */
        long totalFor(boolean hasBig) { return hasBig && !bigExt.isEmpty() ? bigFile + maxFile : maxTotal; }
        long capFor(String name) {
            return !bigExt.isEmpty() && name != null && name.toLowerCase(Locale.ROOT).endsWith(bigExt) ? Math.max(maxFile, bigFile) : maxFile;
        }
        Rules(String what, String[] exts, boolean json, int maxFiles, long maxFile, long maxTotal, long maxZip) {
            this(what, exts, json ? "pack.json" : "", maxFiles, maxFile, maxTotal, maxZip);
        }
        Rules(String what, String[] exts, String jsonName, int maxFiles, long maxFile, long maxTotal, long maxZip) {
            this.what = what; this.exts = exts; this.json = !jsonName.isEmpty(); this.jsonName = jsonName; this.maxFiles = maxFiles;
            this.maxFile = maxFile; this.maxTotal = maxTotal; this.maxZip = maxZip;
        }
    }

    static final Rules SOUND = new Rules("SOUND", SoundMap.EXTS, true, MAX_FILES, MAX_FILE, MAX_TOTAL, MAX_ZIP);
    /** Icon packs: .png and .webp only, 4 MB per image (the custom icon limit), up to 400 images. */
    static final Rules ICONS = new Rules("ICON", new String[]{".png", ".webp"}, false, 400,
            4L * 1024 * 1024, 64L * 1024 * 1024, 64L * 1024 * 1024);

    /**
     * 2.7 wallpaper packs: wallpaper.json and .png .jpg .jpeg .webp .gif, 8 MB per image, 24 MB per pack
     * and 16 files (the JSON counts). The zip itself may be a little over 24 MB of stored files.
     */
    static final Rules WALLPAPERS = new Rules("WALLPAPER", new String[]{".png", ".jpg", ".jpeg", ".webp", ".gif", ".mp4"}, "wallpaper.json", 16,
            8L * 1024 * 1024, 24L * 1024 * 1024, 49L * 1024 * 1024).big(".mp4", 40L * 1024 * 1024);   // 2.17: a video wallpaper is one .mp4 up to 40 MB

    static final class Result {
        String zip, name, error;
        int files;
        boolean ok;
        @Override public String toString() {
            return ok ? zip + " -> " + name + " (" + files + " files)" : zip + ": " + error;
        }
    }

    /** Splits an entry name into path segments with '/' and '\' as separators. */
    static String[] segments(String entry) {
        String n = entry.replace('\\', '/');
        ArrayList<String> out = new ArrayList<>();
        for (String s : n.split("/")) if (!s.isEmpty() && !s.equals(".")) out.add(s);
        return out.toArray(new String[0]);
    }

    /** UNSAFE, SKIP or KEEP for one zip entry name. */
    static int classify(String entry, boolean directory) { return classify(entry, directory, SOUND); }

    static int classify(String entry, boolean directory, Rules rules) {
        if (entry == null || entry.isEmpty()) return UNSAFE;
        if (entry.indexOf('\0') >= 0) return UNSAFE;
        String n = entry.replace('\\', '/');
        if (n.startsWith("/") || n.startsWith("~")) return UNSAFE;
        if (n.length() >= 2 && n.charAt(1) == ':') return UNSAFE;   // C:\...
        for (String s : n.split("/")) if (s.equals("..")) return UNSAFE;
        if (directory || n.endsWith("/")) return SKIP;
        String[] seg = segments(n);
        if (seg.length == 0) return SKIP;
        if (seg[0].equals("__MACOSX")) return SKIP;
        for (String s : seg) if (s.startsWith(".")) return SKIP;    // .DS_Store, ._move.wav
        return allowedFile(seg[seg.length - 1], rules) ? KEEP : SKIP;
    }

    static boolean allowedFile(String name) { return allowedFile(name, SOUND); }

    static boolean allowedFile(String name, Rules rules) {
        String l = name.toLowerCase(Locale.ROOT);
        if (rules.json && l.equals(rules.jsonName)) return true;
        for (String e : rules.exts) if (l.endsWith(e) && l.length() > e.length()) return true;
        return false;
    }

    static boolean isAudio(String name) { return isMedia(name, SOUND); }

    /** A real pack file (audio for sound packs, an image for icon packs), not pack.json. */
    static boolean isMedia(String name, Rules rules) {
        String l = name.toLowerCase(Locale.ROOT);
        for (String e : rules.exts) if (l.endsWith(e)) return true;
        return false;
    }

    /** Safe folder name: letters, digits, space, dot, dash, underscore; never starts with a dot. */
    static String cleanName(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length() && b.length() < 40; i++) {
            char c = s.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == ' ' || c == '-' || c == '_' || c == '.';
            b.append(ok ? c : '_');
        }
        String r = b.toString().trim();
        while (r.startsWith(".")) r = r.substring(1);
        if (r.contains("..")) r = r.replace("..", "_");
        return r.trim();
    }

    static String baseName(String zipFileName) {
        String n = zipFileName;
        if (n.toLowerCase(Locale.ROOT).endsWith(".zip")) n = n.substring(0, n.length() - 4);
        return n;
    }

    /**
     * Pack name from the kept entries: all at the root uses the zip name, all inside one top
     * folder uses that folder. Anything else (two folders, deeper nesting) returns null.
     */
    static String layoutName(List<String[]> kept, String zipBase) {
        if (kept.isEmpty()) return null;
        boolean root = true, nested = true;
        String top = null;
        for (String[] s : kept) {
            if (s.length != 1) root = false;
            if (s.length != 2) nested = false;
            else if (top == null) top = s[0];
            else if (!top.equals(s[0])) nested = false;
        }
        String n = root ? zipBase : (nested ? top : null);
        if (n == null) return null;
        n = cleanName(n);
        return n.isEmpty() ? "pack" : n;
    }

    static String markerText(File zip) {
        return zip.getName() + "\n" + zip.length() + "\n" + zip.lastModified() + "\n";
    }

    /** True if some pack folder was installed from this exact zip (same name, size and time). */
    static boolean installed(File zip, File packsDir) {
        File[] subs = packsDir.listFiles();
        if (subs == null) return false;
        String want = markerText(zip);
        for (File d : subs) {
            if (!d.isDirectory() || d.getName().startsWith(".")) continue;
            File m = new File(d, MARKER);
            if (m.isFile() && m.length() < 1024 && want.equals(read(m))) return true;
        }
        return false;
    }

    /** Pack folder name a zip was installed as, or null. */
    static String installedAs(File zip, File packsDir) {
        File[] subs = packsDir.listFiles();
        if (subs == null) return null;
        String want = markerText(zip);
        for (File d : subs) {
            if (!d.isDirectory() || d.getName().startsWith(".")) continue;
            File m = new File(d, MARKER);
            if (m.isFile() && m.length() < 1024 && want.equals(read(m))) return d.getName();
        }
        return null;
    }

    static File[] zips(File packsDir) {
        File[] all = packsDir == null ? null : packsDir.listFiles();
        ArrayList<File> out = new ArrayList<>();
        if (all != null) for (File f : all) {
            String n = f.getName();
            if (f.isFile() && !n.startsWith(".") && n.toLowerCase(Locale.ROOT).endsWith(".zip")) out.add(f);
        }
        File[] a = out.toArray(new File[0]);
        java.util.Arrays.sort(a, (x, y) -> x.getName().compareToIgnoreCase(y.getName()));
        return a;
    }

    /** Installs every zip that is not installed yet. Cheap when there is nothing new. */
    static ArrayList<Result> scan(File packsDir) { return scan(packsDir, SOUND); }

    static ArrayList<Result> scan(File packsDir, Rules rules) {
        ArrayList<Result> out = new ArrayList<>();
        for (File z : zips(packsDir)) if (!installed(z, packsDir)) out.add(extract(z, packsDir, rules));
        return out;
    }

    private static Result fail(Result r, String why) {
        r.ok = false;
        r.error = why;
        return r;
    }

    static Result extract(File zip, File packsDir) { return extract(zip, packsDir, SOUND); }

    private static String mb(long bytes) { return (bytes >> 20) + " MB"; }

    static Result extract(File zip, File packsDir, Rules rules) {
        Result r = new Result();
        r.zip = zip.getName();
        if (!zip.isFile()) return fail(r, "ZIP NOT FOUND");
        if (zip.length() > rules.maxZip) return fail(r, "ZIP OVER " + mb(rules.maxZip));
        String none = "NO " + rules.what + " FILES";
        File tmp = null;
        try (ZipFile zf = new ZipFile(zip)) {
            ArrayList<ZipEntry> keep = new ArrayList<>();
            ArrayList<String[]> segs = new ArrayList<>();
            long declared = 0;
            boolean hasBig = false;
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                int c = classify(e.getName(), e.isDirectory(), rules);
                if (c == UNSAFE) return fail(r, "UNSAFE PATH IN ZIP");
                if (c == SKIP) continue;
                keep.add(e);
                segs.add(segments(e.getName()));
                if (keep.size() > rules.maxFiles) return fail(r, "MORE THAN " + rules.maxFiles + " FILES");
                if (e.getSize() > 0) declared += e.getSize();
                if (rules.capFor(e.getName()) > rules.maxFile) hasBig = true;
                if (declared > rules.totalFor(true)) return fail(r, "OVER " + mb(rules.totalFor(true)) + " UNPACKED");
            }
            final long totalCap = rules.totalFor(hasBig);
            if (declared > totalCap) return fail(r, "OVER " + mb(totalCap) + " UNPACKED");
            String name = layoutName(segs, baseName(zip.getName()));
            if (name == null) return fail(r, keep.isEmpty() ? none : "PUT FILES AT THE ROOT OR IN ONE FOLDER");
            r.name = name;
            tmp = new File(packsDir, TMP_PREFIX + name);
            deleteTree(tmp, packsDir);
            if (!tmp.mkdirs()) return fail(r, "CANNOT WRITE FOLDER");
            String tmpCanon = tmp.getCanonicalPath() + File.separator;
            HashSet<String> seen = new HashSet<>();
            long total = 0;
            int media = 0;
            byte[] buf = new byte[16 * 1024];
            for (int i = 0; i < keep.size(); i++) {
                ZipEntry e = keep.get(i);
                String[] s = segs.get(i);
                String fn = s[s.length - 1];
                if (!seen.add(fn.toLowerCase(Locale.ROOT))) continue;   // duplicate name, first wins
                File dest = new File(tmp, fn);
                if (!dest.getCanonicalPath().startsWith(tmpCanon)) { deleteTree(tmp, packsDir); return fail(r, "UNSAFE PATH IN ZIP"); }
                long cap = rules.json && fn.equalsIgnoreCase(rules.jsonName) ? MAX_JSON : rules.capFor(fn);
                long n = 0;
                String over = null;
                try (InputStream in = zf.getInputStream(e); OutputStream o = new FileOutputStream(dest)) {
                    int k;
                    while ((k = in.read(buf)) > 0) {
                        n += k;
                        total += k;
                        if (n > cap) { over = "FILE OVER " + (cap >= 1024 * 1024 ? mb(cap) : "64 KB") + ": " + fn; break; }
                        if (total > totalCap) { over = "OVER " + mb(totalCap) + " UNPACKED"; break; }
                        o.write(buf, 0, k);
                    }
                }
                if (over != null) { deleteTree(tmp, packsDir); return fail(r, over); }
                if (isMedia(fn, rules)) media++;
                r.files++;
            }
            if (media == 0) { deleteTree(tmp, packsDir); return fail(r, none); }
            try (OutputStream o = new FileOutputStream(new File(tmp, MARKER))) {
                o.write(markerText(zip).getBytes(StandardCharsets.UTF_8));
            }
            File fin = new File(packsDir, name);
            if (!fin.getCanonicalPath().startsWith(packsDir.getCanonicalPath() + File.separator)) {
                deleteTree(tmp, packsDir);
                return fail(r, "BAD PACK NAME");
            }
            deleteTree(fin, packsDir);
            if (!tmp.renameTo(fin)) { deleteTree(tmp, packsDir); return fail(r, "CANNOT RENAME FOLDER"); }
            r.ok = true;
            return r;
        } catch (IOException | RuntimeException ex) {
            // Android 14 ZipFile itself throws ZipException for entries with .. or a leading /
            if (tmp != null) deleteTree(tmp, packsDir);
            String m = ex.getMessage();
            return fail(r, "BAD ZIP" + (m == null ? "" : ": " + m.toUpperCase(Locale.ROOT)));
        }
    }

    /** Deletes a file or folder inside root (never root itself, never outside it, never through a link). */
    static void deleteTree(File f, File root) {
        try {
            if (f == null) return;
            Path p = f.toPath();
            if (!Files.exists(p, LinkOption.NOFOLLOW_LINKS)) return;
            String rc = root.getCanonicalPath() + File.separator;
            File parent = f.getAbsoluteFile().getParentFile();
            if (parent == null || !(parent.getCanonicalPath() + File.separator).startsWith(rc)) return;
            if (Files.isSymbolicLink(p)) { Files.deleteIfExists(p); return; }
            if (f.isDirectory()) {
                File[] k = f.listFiles();
                if (k != null) for (File c : k) deleteTree(c, root);
            }
            Files.deleteIfExists(p);
        } catch (IOException | RuntimeException ignored) {
            // leave it
        }
    }

    private static String read(File f) {
        try (InputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) Math.min(4096, f.length())];
            int n = 0, k;
            while (n < b.length && (k = in.read(b, n, b.length - n)) > 0) n += k;
            return new String(b, 0, n, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }
}
