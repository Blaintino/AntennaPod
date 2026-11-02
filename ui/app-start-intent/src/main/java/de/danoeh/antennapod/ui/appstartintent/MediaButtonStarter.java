package de.danoeh.antennapod.ui.appstartintent;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;

import androidx.annotation.OptIn;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaSessionService;
import androidx.media3.session.PlaybackPendingIntentBuilder;

public abstract class MediaButtonStarter {
    private static final String INTENT = "de.danoeh.antennapod.NOTIFY_BUTTON_RECEIVER";
    private static final String MEDIA3_PLAYBACK_SERVICE =
            "de.danoeh.antennapod.playback.service.Media3PlaybackService";
    public static final String EXTRA_MEDIA_BUTTON_SOURCE = "media_button_source";
    public static final String MEDIA_BUTTON_SOURCE_WIDGET = "widget";
    public static final String EXTRA_SESSION_COMMAND = "session_command";
    public static final String SESSION_COMMAND_LONG_REWIND_PLAY = "long_rewind_play";
    /**
     * Fork: set on buttons of widgets that may only be used during the sleep timer auto-enable window.
     * The service re-checks the window when the button is pressed, so it does not depend on the widget
     * having been redrawn in time (lock screen widget hosts do not reliably do that).
     */
    public static final String EXTRA_SLEEP_WINDOW_ONLY = "sleep_window_only";
    private static final int REQUEST_CODE_SLEEP_WINDOW_ONLY_BASE = 3000;

    public static Intent createIntent(Context context, int eventCode) {
        KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, eventCode);
        Intent startingIntent = new Intent(BuildConfig.USE_MEDIA3_PLAYBACK_SERVICE
                ? Intent.ACTION_MEDIA_BUTTON : INTENT);
        startingIntent.setPackage(context.getPackageName());
        startingIntent.putExtra(Intent.EXTRA_KEY_EVENT, event);
        return startingIntent;
    }

    @OptIn(markerClass = UnstableApi.class)
    public static PendingIntent createPendingIntent(Context context, @Player.Command int command) {
        Bundle extras = new Bundle();
        extras.putString(EXTRA_MEDIA_BUTTON_SOURCE, MEDIA_BUTTON_SOURCE_WIDGET);
        return new PlaybackPendingIntentBuilder(context, command, getMedia3ServiceClass())
                .setStartAsForegroundService(command == Player.COMMAND_PLAY_PAUSE)
                .setExtras(extras)
                .build();
    }

    /**
     * Like {@link #createPendingIntent(Context, int)}, but the service ignores the press outside of the
     * sleep timer auto-enable window (a separate PendingIntent, so it can coexist with the normal one).
     */
    @OptIn(markerClass = UnstableApi.class)
    public static PendingIntent createPendingIntent(Context context, @Player.Command int command,
                                                    boolean sleepWindowOnly) {
        if (!sleepWindowOnly) {
            return createPendingIntent(context, command);
        }
        Bundle extras = new Bundle();
        extras.putString(EXTRA_MEDIA_BUTTON_SOURCE, MEDIA_BUTTON_SOURCE_WIDGET);
        extras.putBoolean(EXTRA_SLEEP_WINDOW_ONLY, true);
        Intent intent = PlaybackPendingIntentBuilder.createMediaButtonIntent(
                context, command, extras, null, getMedia3ServiceClass());
        int requestCode = REQUEST_CODE_SLEEP_WINDOW_ONLY_BASE + command;
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        if (Build.VERSION.SDK_INT >= 26 && command == Player.COMMAND_PLAY_PAUSE) {
            return PendingIntent.getForegroundService(context, requestCode, intent, flags);
        }
        return PendingIntent.getService(context, requestCode, intent, flags);
    }

    public static PendingIntent createSessionCommandPendingIntent(Context context, String customAction) {
        return createSessionCommandPendingIntent(context, customAction, false);
    }

    @OptIn(markerClass = UnstableApi.class)
    public static PendingIntent createSessionCommandPendingIntent(Context context, String customAction,
                                                                  boolean sleepWindowOnly) {
        Bundle extras = new Bundle();
        extras.putString(EXTRA_MEDIA_BUTTON_SOURCE, MEDIA_BUTTON_SOURCE_WIDGET);
        extras.putString(EXTRA_SESSION_COMMAND, customAction);
        extras.putBoolean(EXTRA_SLEEP_WINDOW_ONLY, sleepWindowOnly);
        Intent intent = PlaybackPendingIntentBuilder.createMediaButtonIntent(
                context, Player.COMMAND_PLAY_PAUSE, extras, null, getMedia3ServiceClass());
        int requestCode = sleepWindowOnly ? 1002 : 1001;
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        if (Build.VERSION.SDK_INT >= 26) {
            return PendingIntent.getForegroundService(context, requestCode, intent, flags);
        }
        return PendingIntent.getService(context, requestCode, intent, flags);
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends MediaSessionService> getMedia3ServiceClass() {
        try {
            return (Class<? extends MediaSessionService>) Class.forName(MEDIA3_PLAYBACK_SERVICE);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }
}
