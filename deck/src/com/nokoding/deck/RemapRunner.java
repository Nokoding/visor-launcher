package com.nokoding.deck;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.KeyEvent;

/**
 * 2.6: the parts of a remap action that Deck's window and the overlay service do the same way.
 * Media and volume go through AudioManager, apps and Android Settings through startActivity.
 * Nothing here injects a key press: Android does not allow that for an app.
 * The rest (global actions, Deck's own screens) differs between the two callers and lives there.
 */
final class RemapRunner {
    private RemapRunner() {}

    /** MEDIA PLAY PAUSE, NEXT, PREVIOUS and STOP, sent to the active media session. @return false if it could not be sent. */
    static boolean media(Context c, int action) {
        int code;
        switch (action) {
            case RemapAction.PLAY_PAUSE: code = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE; break;
            case RemapAction.NEXT: code = KeyEvent.KEYCODE_MEDIA_NEXT; break;
            case RemapAction.PREV: code = KeyEvent.KEYCODE_MEDIA_PREVIOUS; break;
            case RemapAction.STOP: code = KeyEvent.KEYCODE_MEDIA_STOP; break;
            default: return false;
        }
        try {
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return false;
            long t = SystemClock.uptimeMillis();
            am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
            am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0));
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** VOLUME UP, VOLUME DOWN and MUTE through AudioManager, with the system volume bar. */
    static boolean volume(Context c, int action) {
        try {
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return false;
            int dir;
            switch (action) {
                case RemapAction.VOL_UP: dir = AudioManager.ADJUST_RAISE; break;
                case RemapAction.VOL_DOWN: dir = AudioManager.ADJUST_LOWER; break;
                case RemapAction.MUTE: dir = AudioManager.ADJUST_TOGGLE_MUTE; break;
                default: return false;
            }
            am.adjustSuggestedStreamVolume(dir, AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Starts an app by package name, the TV launcher entry first. @return false if there is none or Android refused. */
    static boolean launch(Context c, String pkg) {
        if (!PsRules.plainPackage(pkg)) return false;
        try {
            PackageManager pm = c.getPackageManager();
            Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (i == null) i = pm.getLaunchIntentForPackage(pkg);
            if (i == null) return false;
            c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Starts an app by its flattened component name ("com.foo/.Main"), as Deck's recents keep it. */
    static boolean launchComponent(Context c, String key) {
        try {
            ComponentName cn = ComponentName.unflattenFromString(key);
            if (cn == null) return false;
            return launch(c, cn.getPackageName());
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean androidSettings(Context c) {
        try {
            c.startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Brings Deck forward. cmd is a DeckCmd name or null for a plain Home. */
    static boolean deck(Context c, String cmd) {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN).setClassName(c.getPackageName(), c.getPackageName() + ".HomeActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (cmd == null) i.addCategory(Intent.CATEGORY_HOME); else i.putExtra(DeckCmd.EXTRA, cmd);
            c.startActivity(i);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
