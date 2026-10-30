package de.danoeh.antennapod.playback.service;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Fork: remembers who started playback the last few times (widget, notification, lock screen media
 * controls, headphones, app), to find out where accidental play presses come from.
 */
public final class PlayStartLog {
    private static final String PREFS = "PlayStartLog";
    private static final String KEY_ENTRIES = "entries";
    private static final String SEPARATOR = "\n";
    private static final int MAX_ENTRIES = 40;

    private PlayStartLog() {
    }

    public static synchronized void add(Context context, String source) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        List<String> entries = new ArrayList<>(getEntries(context));
        String time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(new Date());
        entries.add(0, time + " – " + source.replace(SEPARATOR, " "));
        while (entries.size() > MAX_ENTRIES) {
            entries.remove(entries.size() - 1);
        }
        prefs.edit().putString(KEY_ENTRIES, String.join(SEPARATOR, entries)).apply();
    }

    /**
     * Newest first.
     */
    public static synchronized List<String> getEntries(Context context) {
        String stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ENTRIES, "");
        if (stored.isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.asList(stored.split(SEPARATOR));
    }

    public static synchronized void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
    }
}
