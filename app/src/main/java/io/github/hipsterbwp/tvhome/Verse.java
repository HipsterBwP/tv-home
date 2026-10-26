package io.github.hipsterbwp.tvhome;

import android.content.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Today's verse from assets/verses.txt ("Reference|Text" per line). Each year gets its own
 * shuffled order, and each day takes the next verse in it, so the pick looks random, changes at
 * midnight, and does not repeat until the whole list has been shown.
 */
final class Verse {
    final String reference, text;

    private Verse(String reference, String text) {
        this.reference = reference;
        this.text = text;
    }

    /** Day key, so callers can tell when the verse should change. */
    static int dayKey(Calendar now) {
        return now.get(Calendar.YEAR) * 1000 + now.get(Calendar.DAY_OF_YEAR);
    }

    static Verse forDay(Context c, Calendar now) {
        List<Verse> all = load(c);
        if (all.isEmpty()) return null;
        Collections.shuffle(all, new Random(now.get(Calendar.YEAR)));
        return all.get((now.get(Calendar.DAY_OF_YEAR) - 1) % all.size());
    }

    private static List<Verse> load(Context c) {
        List<Verse> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(c.getAssets().open("verses.txt"), "UTF-8"))) {
            String line;
            while ((line = r.readLine()) != null) {
                int bar = line.indexOf('|');
                if (bar > 0) out.add(new Verse(line.substring(0, bar).trim(), line.substring(bar + 1).trim()));
            }
        } catch (IOException ignored) {
            // Packaged with the app; an unreadable list just means no verse is shown.
        }
        return out;
    }
}
