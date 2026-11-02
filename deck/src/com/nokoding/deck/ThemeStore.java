package com.nokoding.deck;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 2.17 themes folder: Android/data/com.nokoding.deck/files/themes/NAME.json. Plain java.io so it is tested on the
 * JVM. Caps: 8 KB a file, 40 files. A bad file is skipped, never an error. File names are checked: no path, no dots
 * that climb, only the characters ThemeSpec.safeName allows.
 */
final class ThemeStore {
    private ThemeStore() {}

    static final int MAX_FILES = 40;
    static final String EXT = ".json";

    /** The themes in the folder, sorted by name. Never throws, a missing folder gives an empty list. */
    static List<ThemeSpec> list(File dir) {
        ArrayList<ThemeSpec> out = new ArrayList<>();
        try {
            File[] fs = dir == null ? null : dir.listFiles();
            if (fs == null) return out;
            Arrays.sort(fs, (x, y) -> x.getName().compareToIgnoreCase(y.getName()));
            int seen = 0;
            for (File f : fs) {
                if (seen++ >= MAX_FILES) break;
                String n = f.getName();
                if (!f.isFile() || !n.toLowerCase(Locale.ROOT).endsWith(EXT) || f.length() > ThemeSpec.MAX_FILE) continue;
                ThemeSpec s = ThemeSpec.parse(read(f));
                if (s == null) continue;
                String base = n.substring(0, n.length() - EXT.length());
                if (ThemeSpec.cleanName(base)) s.name = base.toUpperCase(Locale.ROOT);   // the file name wins, so the list is unambiguous
                out.add(s);
            }
        } catch (RuntimeException ignored) {}
        return out;
    }

    /** Writes NAME.json. @return null on success, otherwise a short reason in capitals. */
    static String export(File dir, ThemeSpec s) {
        if (dir == null || s == null) return "NO FOLDER";
        String n = ThemeSpec.safeName(s.name);
        try {
            if (!dir.isDirectory() && !dir.mkdirs()) return "CANNOT MAKE THE THEMES FOLDER";
            File[] have = dir.listFiles();
            if (have != null && have.length >= MAX_FILES && !new File(dir, n + EXT).exists()) return "THE THEMES FOLDER IS FULL";
            File tmp = new File(dir, "." + n + ".tmp"), dst = new File(dir, n + EXT);
            try (FileOutputStream o = new FileOutputStream(tmp)) {
                o.write(s.toJson().getBytes("UTF-8"));
            }
            if (dst.exists() && !dst.delete()) return "CANNOT REPLACE THE OLD FILE";
            return tmp.renameTo(dst) ? null : "CANNOT SAVE THE FILE";
        } catch (IOException | RuntimeException e) {
            return "CANNOT SAVE THE FILE";
        }
    }

    /** The next free name like THEME 1, THEME 2. */
    static String freeName(File dir) {
        for (int i = 1; i < 100; i++) {
            String n = "THEME " + i;
            if (dir == null || !new File(dir, n + EXT).exists()) return n;
        }
        return "THEME";
    }

    private static String read(File f) {
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) Math.min(f.length(), ThemeSpec.MAX_FILE)];
            int n = 0, r;
            while (n < b.length && (r = in.read(b, n, b.length - n)) > 0) n += r;
            return new String(b, 0, n, "UTF-8");
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
