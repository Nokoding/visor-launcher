package com.nokoding.deck;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.SystemClock;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * UI sounds. The built in pack is synthesized at runtime into tiny WAVs in the cache
 * folder (no audio files in the APK). User packs live in
 * getExternalFilesDir(null)/soundpacks/NAME/ and override events one by one.
 */
final class Sounds {
    static final int N = SoundMap.EVENTS.length;
    static final long MAX_FILE = 1024 * 1024;

    private final Context ctx;
    private SoundPool pool;
    private final int[] builtin = new int[N], packIds = new int[N];
    private final float[] packVol = new float[N];
    private final long[] lastPlay = new long[N];
    private int announceId;
    // 2.4: the PS overlay builds its pool when the menu opens, so its first sound may not be
    // loaded yet. playSoon() plays it as soon as the load finishes (if that is quick).
    private final android.util.SparseBooleanArray loaded = new android.util.SparseBooleanArray();
    private int pendingEv = -1;
    private long pendingAt;
    SoundMap map;
    String pack = "", status = "BUILT-IN";
    float master = 0.7f;
    boolean mute;

    Sounds(Context c) { ctx = c.getApplicationContext(); }

    File packsDir() {
        try {
            File base = ctx.getExternalFilesDir(null);
            if (base == null) return null;
            File d = new File(base, "soundpacks");
            if (!d.isDirectory() && !d.mkdirs()) return null;
            File readme = new File(d, "README.txt");
            if (!readme.exists()) {
                try (FileOutputStream o = new FileOutputStream(readme)) {
                    o.write(("Put each sound pack in its own folder here, or drop a .zip here.\n"
                            + "Visor unpacks zips on launch and on Reload packs (files at the zip root or in one folder).\n"
                            + "Name files after events: " + String.join(", ", SoundMap.EVENTS) + "\n"
                            + "with .wav, .ogg or .mp3. Optional pack.json can rename files and set volume.\n")
                            .getBytes(StandardCharsets.UTF_8));
                }
            }
            return d;
        } catch (Throwable t) {
            return null;
        }
    }

    String[] listPacks() {
        ArrayList<String> out = new ArrayList<>();
        File d = packsDir();
        File[] subs = d == null ? null : d.listFiles();
        if (subs != null) for (File f : subs) {
            if (f.isDirectory() && SoundMap.plainName(f.getName()) && !f.getName().startsWith(".")) out.add(f.getName());
        }
        String[] a = out.toArray(new String[0]);
        Arrays.sort(a, String.CASE_INSENSITIVE_ORDER);
        return a;
    }

    /** Unpack any new .zip in the packs folder. Disk work: call it off the main thread. */
    java.util.ArrayList<PackZip.Result> installZips() {
        File d = packsDir();
        if (d == null) return new java.util.ArrayList<>();
        return PackZip.scan(d);
    }

    /** Create the pool and load the synthesized defaults. Safe to call repeatedly. */
    void ensure() {
        if (pool != null) return;
        try {
            AudioAttributes aa = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
            pool = new SoundPool.Builder().setMaxStreams(4).setAudioAttributes(aa).build();
            pool.setOnLoadCompleteListener((sp, id, st) -> {
                if (st == 0) loaded.put(id, true);
                if (st == 0 && pendingEv >= 0 && id == idFor(pendingEv)) {
                    int ev = pendingEv;
                    pendingEv = -1;
                    if (SystemClock.uptimeMillis() - pendingAt < 800) play(ev);
                }
                if (st == 0 && id == announceId && announceId != 0) {
                    announceId = 0;
                    play(SoundMap.PACK_LOADED);
                }
            });
            File dir = new File(ctx.getCacheDir(), "synth2");
            if (!dir.isDirectory()) dir.mkdirs();
            for (int i = 0; i < N; i++) {
                File f = new File(dir, SoundMap.EVENTS[i] + ".wav");
                byte[] w = Synth.wav(Synth.pcm(i));
                if (f.length() != w.length) {
                    try (FileOutputStream o = new FileOutputStream(f)) { o.write(w); }
                }
                builtin[i] = pool.load(f.getPath(), 1);
            }
        } catch (Throwable t) {
            release();
        }
    }

    /** Switch packs. Empty name = built in only. */
    void loadPack(String name, boolean announce) {
        ensure();
        if (pool == null) { status = "AUDIO UNAVAILABLE"; return; }
        for (int i = 0; i < N; i++) {
            if (packIds[i] != 0) { try { pool.unload(packIds[i]); } catch (Throwable ignored) {} }
            packIds[i] = 0;
            packVol[i] = 1f;
        }
        pack = name == null ? "" : name;
        map = null;
        announceId = 0;
        if (pack.isEmpty()) {
            status = "BUILT-IN";
        } else {
            File d = packsDir();
            File p = d == null ? null : new File(d, pack);
            if (p == null || !p.isDirectory()) {
                status = "MISSING, USING BUILT-IN";
            } else {
                String json = null;
                File jf = new File(p, "pack.json");
                if (jf.isFile() && jf.length() < 64 * 1024) json = readText(jf);
                map = SoundMap.resolve(pack, p.list(), json);
                int loaded = 0;
                for (int i = 0; i < N; i++) {
                    String f = map.file[i];
                    if (f == null) continue;
                    File sf = new File(p, f);
                    if (!sf.isFile() || sf.length() > MAX_FILE) continue;
                    try {
                        packIds[i] = pool.load(sf.getPath(), 1);
                        packVol[i] = map.volume[i];
                        if (packIds[i] != 0) loaded++;
                    } catch (Throwable ignored) {
                        packIds[i] = 0;
                    }
                }
                status = map.name.toUpperCase(java.util.Locale.ROOT) + "  " + loaded + "/" + N;
                if (announce && packIds[SoundMap.PACK_LOADED] != 0) { announceId = packIds[SoundMap.PACK_LOADED]; announce = false; }
            }
        }
        if (announce) play(SoundMap.PACK_LOADED);
    }

    String fileFor(int ev) {
        if (map != null && packIds[ev] != 0 && map.file[ev] != null) return map.file[ev];
        return "BUILT-IN";
    }

    private static String readText(File f) {
        try (InputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) f.length()];
            int n = 0, r;
            while (n < b.length && (r = in.read(b, n, b.length - n)) > 0) n += r;
            return new String(b, 0, n, StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return null;
        }
    }

    void play(int ev) { play(ev, 1f); }

    private int idFor(int ev) {
        if (ev < 0 || ev >= N) return 0;
        return packIds[ev] != 0 ? packIds[ev] : builtin[ev];
    }

    /** 2.4: play now if loaded, else when its load completes (within 800 ms). */
    void playSoon(int ev) {
        int id = idFor(ev);
        if (id != 0 && loaded.get(id)) { play(ev); return; }
        pendingEv = ev;
        pendingAt = SystemClock.uptimeMillis();
    }

    /**
     * rate is the SoundPool playback rate (0.5 to 2). 2.3 type ticks use it for pitch jitter;
     * their rate cap lives in TickScheduler, so they skip the 35 ms repeat guard (20 ms instead).
     */
    void play(int ev, float rate) {
        if (mute || pool == null || ev < 0 || ev >= N) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastPlay[ev] < (ev == SoundMap.TYPE_TICK ? 20 : 35)) return;
        lastPlay[ev] = now;
        int id = packIds[ev] != 0 ? packIds[ev] : builtin[ev];
        float v = (packIds[ev] != 0 ? packVol[ev] : SoundMap.BUILTIN_VOL[ev]) * master;
        if (id == 0 || v <= 0f) return;
        float r = rate >= 0.5f && rate <= 2f ? rate : 1f;
        // tick priority 0: a full pool drops a tick, never a menu sound
        try { pool.play(id, v, v, ev == SoundMap.TYPE_TICK ? 0 : 1, 0, r); } catch (Throwable ignored) {}
    }

    void release() {
        try { if (pool != null) pool.release(); } catch (Throwable ignored) {}
        pool = null;
        loaded.clear();
        pendingEv = -1;
        Arrays.fill(builtin, 0);
        Arrays.fill(packIds, 0);
        pack = "";
        map = null;
        status = "BUILT-IN";
    }
}
