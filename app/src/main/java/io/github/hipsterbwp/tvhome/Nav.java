package io.github.hipsterbwp.tvhome;

import android.app.Activity;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;

/**
 * Remote navigation with the app's own tap sound. Android plays its standard click on every
 * focus move it makes, with no per-app way to change it, so the screens move focus here instead
 * and play res/raw/nav_tap. The TV's "Sound effects" setting still turns it off.
 */
final class Nav {
    private Nav() {}

    private static SoundPool pool;
    private static int tap;
    private static boolean loaded;

    /** Handles a D-pad press: moves focus and plays the tap. False means let Android handle it. */
    static boolean move(Activity a, KeyEvent e) {
        int dir;
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_UP: dir = View.FOCUS_UP; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: dir = View.FOCUS_DOWN; break;
            case KeyEvent.KEYCODE_DPAD_LEFT: dir = View.FOCUS_LEFT; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: dir = View.FOCUS_RIGHT; break;
            default: return false;
        }
        View focused = a.getCurrentFocus();
        if (focused == null) return false;
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        View next = focused.focusSearch(dir);
        if (next == null || next == focused || !next.requestFocus(dir)) return true;
        play(a);
        return true;
    }

    private static void play(Context c) {
        if (Settings.System.getInt(c.getContentResolver(), Settings.System.SOUND_EFFECTS_ENABLED, 1) == 0) return;
        if (pool == null) {
            pool = new SoundPool.Builder()
                    .setMaxStreams(2)
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build())
                    .build();
            pool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
                @Override
                public void onLoadComplete(SoundPool p, int id, int status) {
                    loaded = status == 0;
                }
            });
            tap = pool.load(c.getApplicationContext(), R.raw.nav_tap, 1);
            return;
        }
        if (loaded) pool.play(tap, 1f, 1f, 1, 0, 1f);
    }

    /** Loads the sound ahead of the first press. */
    static void warmUp(Context c) {
        if (pool == null) play(c);
    }
}
