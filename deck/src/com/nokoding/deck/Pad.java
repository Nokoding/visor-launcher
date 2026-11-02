package com.nokoding.deck;

import android.annotation.TargetApi;
import android.hardware.lights.Light;
import android.hardware.lights.LightState;
import android.hardware.lights.LightsManager;
import android.hardware.lights.LightsRequest;
import android.os.CombinedVibration;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.VibratorManager;
import android.view.InputDevice;

import java.util.List;

/**
 * Controller extras through public Android 12+ APIs only: per motor rumble
 * (InputDevice.getVibratorManager) and the RGB light (InputDevice.getLightsManager).
 * Adaptive triggers and voice coil haptics have no Android API and are not attempted.
 * Every call is guarded; status strings say why something is missing.
 */
@TargetApi(31)
final class Pad {
    static final String COMING_SOON = "NOT EXPOSED BY THIS BOX (COMING SOON)";
    int deviceId = -1;
    String hapticStatus = "NO GAMEPAD YET", lightStatus = "NO GAMEPAD YET";

    private VibratorManager vm;
    private int idL = -1, idR = -1;
    private boolean ampL, ampR;
    private long busyUntil;

    private LightsManager lm;
    private LightsManager.LightsSession session;
    private Light rgb;
    private int lastColor = 1;

    // 2.1 player LEDs: on/off lights sorted by player number (bit 0 = player-1)
    private final java.util.ArrayList<Light> players = new java.util.ArrayList<>();
    private LightsManager plm;
    private LightsManager.LightsSession pSession;
    private int[] pOrig;
    private int pMask = -1;
    String playerStatus = "NO GAMEPAD YET";
    private InputDevice dev;

    boolean hasVibrators() { return vm != null; }

    boolean hasLight() { return rgb != null; }

    boolean hasPlayerLeds() { return !players.isEmpty(); }

    void attach(InputDevice d) {
        detach();
        if (d == null) {
            hapticStatus = lightStatus = "NO GAMEPAD CONNECTED";
            return;
        }
        deviceId = d.getId();
        dev = d;
        try {
            VibratorManager m = d.getVibratorManager();
            int[] ids = m == null ? null : m.getVibratorIds();
            if (m == null) {
                hapticStatus = "NO VIBRATORMANAGER";
            } else if (ids == null || ids.length == 0) {
                hapticStatus = "NO MOTORS EXPOSED BY THIS CONTROLLER";
            } else {
                vm = m;
                // Android lists controller motors left first, then right.
                idL = ids[0];
                idR = ids.length > 1 ? ids[1] : ids[0];
                ampL = m.getVibrator(idL).hasAmplitudeControl();
                ampR = m.getVibrator(idR).hasAmplitudeControl();
                hapticStatus = ids.length + (ids.length == 1 ? " MOTOR" : " MOTORS") + ", AMPLITUDE "
                        + (ampL && ampR ? "YES" : (ampL || ampR ? "PARTIAL" : "NO (DUTY CYCLE)"));
            }
        } catch (Throwable t) {
            vm = null;
            hapticStatus = "RUMBLE ERROR (" + t.getClass().getSimpleName() + ")";
        }
        try {
            LightsManager m = d.getLightsManager();
            List<Light> lights = m == null ? null : m.getLights();
            if (m == null) {
                lightStatus = COMING_SOON;
            } else if (lights == null || lights.isEmpty()) {
                lightStatus = COMING_SOON;
            } else {
                for (Light l : lights) {
                    if (l.hasRgbControl() && (rgb == null || l.getType() == Light.LIGHT_TYPE_INPUT)) rgb = l;
                    else if (!l.hasRgbControl() && PlayerLeds.index(l.getName()) >= 1) players.add(l);
                }
                java.util.Collections.sort(players, (x, y) -> PlayerLeds.index(x.getName()) - PlayerLeds.index(y.getName()));
                while (players.size() > PlayerLeds.LEDS) players.remove(players.size() - 1);
                if (!players.isEmpty()) plm = m;
                playerStatus = players.isEmpty() ? "NONE EXPOSED" : players.size() + " ON/OFF LEDS FOUND";
                if (rgb == null) {
                    lightStatus = COMING_SOON;
                } else {
                    lm = m;
                    lightStatus = "FOUND";
                }
            }
        } catch (Throwable t) {
            rgb = null;
            lightStatus = COMING_SOON + " (" + t.getClass().getSimpleName() + ")";
        }
    }

    private static VibrationEffect effect(HapticPatterns.Wave w) {
        if (w.t.length == 1) return VibrationEffect.createOneShot(w.t[0], Math.max(1, w.a[0]));
        return VibrationEffect.createWaveform(w.t, w.a, -1);
    }

    /** Plays a pattern. humAmp &gt; 0 keeps the wheel hum running on the left motor underneath. */
    void play(int pattern, int preset, int strength, int humAmp) {
        if (vm == null || pattern < 0) return;
        try {
            HapticPatterns.Wave l = HapticPatterns.build(pattern, true, preset, strength, ampL);
            HapticPatterns.Wave r = HapticPatterns.build(pattern, false, preset, strength, ampR);
            if (l == null && humAmp > 0) l = HapticPatterns.hum(humAmp, ampL);
            if (l == null && r == null) return;
            CombinedVibration.ParallelCombination pc = CombinedVibration.startParallel();
            if (idL == idR) {
                HapticPatterns.Wave one = r != null && (l == null || r.total >= l.total) ? r : l;
                pc.addVibrator(idL, effect(one));
            } else {
                if (l != null) pc.addVibrator(idL, effect(l));
                if (r != null) pc.addVibrator(idR, effect(r));
            }
            vm.vibrate(pc.combine());
            long total = Math.max(l == null ? 0 : l.total, r == null ? 0 : r.total);
            busyUntil = SystemClock.uptimeMillis() + (humAmp > 0 ? 0 : total);
        } catch (Throwable ignored) {}
    }

    /** Wheel hum on the left motor. amp already scaled; 0 cancels. */
    void hum(int amp) {
        if (vm == null) return;
        try {
            if (amp <= 0) {
                if (SystemClock.uptimeMillis() >= busyUntil) vm.cancel();
                return;
            }
            if (SystemClock.uptimeMillis() < busyUntil) return; // let a pattern finish first
            HapticPatterns.Wave w = HapticPatterns.hum(amp, ampL);
            if (w == null) return;
            vm.vibrate(CombinedVibration.startParallel().addVibrator(idL, effect(w)).combine());
        } catch (Throwable ignored) {}
    }

    void setLight(int argb) {
        if (rgb == null || lm == null) return;
        if (argb == lastColor && session != null) return;
        try {
            if (session == null) session = lm.openSession();
            session.requestLights(new LightsRequest.Builder()
                    .addLight(rgb, new LightState.Builder().setColor(argb).build()).build());
            lastColor = argb;
        } catch (Throwable t) {
            lightStatus = "LIGHT ERROR (" + t.getClass().getSimpleName() + ")";
        }
    }

    /**
     * Close the light session. The framework then sets the light to state 0 (off) unless
     * another app holds a session (InputManagerService.closeLightSession).
     */
    void releaseLight() {
        try { if (session != null) session.close(); } catch (Throwable ignored) {}
        session = null;
        lastColor = 1;
    }

    /** Light the player LEDs in mask (bit 0 = player-1). Saves the system's pattern first. */
    void setPlayerMask(int mask) {
        if (players.isEmpty() || plm == null) return;
        if (mask == pMask && pSession != null) return;
        try {
            if (pSession == null) {
                pOrig = new int[players.size()];
                for (int i = 0; i < players.size(); i++) {
                    try {
                        LightState st = plm.getLightState(players.get(i));
                        pOrig[i] = st == null ? 0 : st.getColor();
                    } catch (Throwable t) {
                        pOrig[i] = 0;
                    }
                }
                pSession = plm.openSession();
            }
            LightsRequest.Builder b = new LightsRequest.Builder();
            for (int i = 0; i < players.size(); i++) b.addLight(players.get(i), new LightState.Builder().setColor(PlayerLeds.color(mask, i)).build());
            pSession.requestLights(b.build());
            pMask = mask;
            playerStatus = "IN USE (" + players.size() + " LEDS)";
        } catch (Throwable t) {
            playerStatus = "ERROR " + t.getClass().getSimpleName() + ": " + t.getMessage();
        }
    }

    /**
     * Put back the pattern the system had (the player number) and close the session.
     * closeLightSession sets the lights of the last request to 0, so after restoring we send
     * an empty request first: then closing leaves the restored pattern alone.
     */
    void restorePlayers() {
        if (pSession == null) { pMask = -1; return; }
        try {
            if (pOrig != null && pOrig.length == players.size()) {
                LightsRequest.Builder b = new LightsRequest.Builder();
                for (int i = 0; i < players.size(); i++) b.addLight(players.get(i), new LightState.Builder().setColor(pOrig[i]).build());
                pSession.requestLights(b.build());
            }
            pSession.requestLights(new LightsRequest.Builder().build());
        } catch (Throwable ignored) {}
        try { pSession.close(); } catch (Throwable ignored) {}
        pSession = null;
        pMask = -1;
    }

    /** Controller battery 0..1, or NaN if unknown. */
    float battery() {
        try {
            if (dev == null) return Float.NaN;
            android.hardware.BatteryState b = dev.getBatteryState();
            return b == null || !b.isPresent() ? Float.NaN : b.getCapacity();
        } catch (Throwable t) {
            return Float.NaN;
        }
    }

    void detach() {
        try { if (vm != null) vm.cancel(); } catch (Throwable ignored) {}
        releaseLight();
        restorePlayers();
        players.clear();
        plm = null;
        pOrig = null;
        dev = null;
        vm = null;
        lm = null;
        rgb = null;
        idL = idR = -1;
        deviceId = -1;
        busyUntil = 0;
    }
}
