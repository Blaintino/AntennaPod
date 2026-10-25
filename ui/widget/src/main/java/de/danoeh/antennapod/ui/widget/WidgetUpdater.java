package de.danoeh.antennapod.ui.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import androidx.media3.common.Player;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.Transformation;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;

import de.danoeh.antennapod.ui.appstartintent.MediaButtonStarter;
import de.danoeh.antennapod.ui.appstartintent.SkipAdOutroActivityStarter;
import de.danoeh.antennapod.ui.common.Converter;
import de.danoeh.antennapod.storage.preferences.SleepTimerPreferences;
import de.danoeh.antennapod.storage.preferences.UserPreferences;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

import de.danoeh.antennapod.model.playback.MediaType;
import de.danoeh.antennapod.model.playback.Playable;
import de.danoeh.antennapod.playback.base.PlayerStatus;
import de.danoeh.antennapod.ui.appstartintent.MainActivityStarter;
import de.danoeh.antennapod.ui.appstartintent.PlaybackSpeedActivityStarter;
import de.danoeh.antennapod.ui.appstartintent.VideoPlayerActivityStarter;
import de.danoeh.antennapod.ui.episodes.ImageResourceUtils;
import de.danoeh.antennapod.ui.episodes.TimeSpeedConverter;
import de.danoeh.antennapod.ui.glide.FastBlurTransformation;

/**
 * Updates the state of the player widget.
 */
public abstract class WidgetUpdater {
    private static final String TAG = "WidgetUpdater";
    private static final int[] PLAYBACK_BUTTON_IDS = {R.id.butPlay, R.id.butPlayExtended, R.id.butRew,
        R.id.butFastForward, R.id.butSkip, R.id.butLongRewind, R.id.butPlaybackSpeed, R.id.butSkipAd};

    public static class WidgetState {
        final Playable media;
        final PlayerStatus status;
        final int position;
        final int duration;
        final float playbackSpeed;

        public WidgetState(Playable media, PlayerStatus status, int position, int duration, float playbackSpeed) {
            this.media = media;
            this.status = status;
            this.position = position;
            this.duration = duration;
            this.playbackSpeed = playbackSpeed;
        }

        public WidgetState(PlayerStatus status) {
            this(null, status, Playable.INVALID_TIME, Playable.INVALID_TIME, 1.0f);
        }
    }

    /**
     * Update the widgets with the given parameters. Must be called in a background thread.
     */
    public static void updateWidget(Context context, WidgetState widgetState) {
        if (!PlayerWidget.isEnabled(context) || widgetState == null) {
            return;
        }

        PendingIntent startMediaPlayer;
        if (widgetState.media != null && widgetState.media.getMediaType() == MediaType.VIDEO) {
            startMediaPlayer = new VideoPlayerActivityStarter(context).getPendingIntent();
        } else {
            startMediaPlayer = new MainActivityStarter(context)
                    .withOpenPlayer().withClearBackStack().getPendingIntent();
        }

        PendingIntent startPlaybackSpeedDialog = new PlaybackSpeedActivityStarter(context).getPendingIntent();

        boolean hasProgress = widgetState.media != null && getProgressString(widgetState.position,
                widgetState.duration, widgetState.playbackSpeed) != null;

        boolean inSleepWindow = SleepTimerPreferences.isInTimeRange(SleepTimerPreferences.autoEnableFrom(),
                SleepTimerPreferences.autoEnableTo(), Calendar.getInstance().get(Calendar.HOUR_OF_DAY));
        boolean anySleepWindowOnly = false;

        ComponentName playerWidget = new ComponentName(context, PlayerWidget.class);
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] widgetIds = manager.getAppWidgetIds(playerWidget);

        for (int id : widgetIds) {
            Bundle options = manager.getAppWidgetOptions(id);
            SharedPreferences prefs = context.getSharedPreferences(PlayerWidget.PREFS_NAME, Context.MODE_PRIVATE);
            boolean vertical = prefs.getBoolean(PlayerWidget.KEY_WIDGET_VERTICAL + id, false);
            RemoteViews views = createViews(context, widgetState,
                    vertical ? R.layout.player_widget_vertical : R.layout.player_widget,
                    startMediaPlayer, startPlaybackSpeedDialog);
            boolean buttonsOnly = prefs.getBoolean(PlayerWidget.KEY_WIDGET_BUTTONS_ONLY + id, false);
            views.setViewVisibility(R.id.txtvTitle,
                    !buttonsOnly && widgetState.media != null ? View.VISIBLE : View.GONE);
            views.setViewVisibility(R.id.txtvProgress, !buttonsOnly && hasProgress ? View.VISIBLE : View.GONE);
            views.setViewVisibility(R.id.txtNoPlaying,
                    !buttonsOnly && widgetState.media == null ? View.VISIBLE : View.GONE);
            int minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
            int columns = getCellsForSize(minWidth);
            if (buttonsOnly) {
                views.setViewVisibility(R.id.layout_center, View.VISIBLE);
            } else if (columns < 3) {
                views.setViewVisibility(R.id.layout_center, View.INVISIBLE);
            } else {
                views.setViewVisibility(R.id.layout_center, View.VISIBLE);
            }
            boolean showPlaybackSpeed = prefs.getBoolean(PlayerWidget.KEY_WIDGET_PLAYBACK_SPEED + id, false);
            boolean showRewind = prefs.getBoolean(PlayerWidget.KEY_WIDGET_REWIND + id, false);
            boolean showFastForward = prefs.getBoolean(PlayerWidget.KEY_WIDGET_FAST_FORWARD + id, false);
            boolean showSkip = prefs.getBoolean(PlayerWidget.KEY_WIDGET_SKIP + id, false);
            boolean showLongRewind = prefs.getBoolean(PlayerWidget.KEY_WIDGET_LONG_REWIND + id, false);
            boolean showSkipAd = prefs.getBoolean(PlayerWidget.KEY_WIDGET_SKIP_AD + id, false);
            boolean showCoverAsBcg = prefs.getBoolean(PlayerWidget.KEY_WIDGET_COVER_BACKGROUND + id, false);

            boolean anyExtraButton = showPlaybackSpeed || showRewind || showSkip || showFastForward || showLongRewind
                    || showSkipAd;
            // Hiding play/pause is only allowed if another button remains, otherwise the widget would be empty
            boolean hidePlay = anyExtraButton && prefs.getBoolean(PlayerWidget.KEY_WIDGET_HIDE_PLAY + id, false);
            if (buttonsOnly || anyExtraButton) {
                views.setInt(R.id.extendedButtonsContainer, "setVisibility", View.VISIBLE);
                views.setInt(R.id.butPlay, "setVisibility", View.GONE);
                views.setInt(R.id.butPlayExtended, "setVisibility", hidePlay ? View.GONE : View.VISIBLE);
                views.setInt(R.id.butPlaybackSpeed, "setVisibility", showPlaybackSpeed ? View.VISIBLE : View.GONE);
                views.setInt(R.id.butRew, "setVisibility", showRewind ? View.VISIBLE : View.GONE);
                views.setInt(R.id.butFastForward, "setVisibility", showFastForward ? View.VISIBLE : View.GONE);
                views.setInt(R.id.butSkip, "setVisibility", showSkip ? View.VISIBLE : View.GONE);
                views.setInt(R.id.butLongRewind, "setVisibility", showLongRewind ? View.VISIBLE : View.GONE);
                views.setInt(R.id.butSkipAd, "setVisibility", showSkipAd ? View.VISIBLE : View.GONE);
            } else {
                views.setInt(R.id.extendedButtonsContainer, "setVisibility", View.GONE);
                views.setInt(R.id.butPlay, "setVisibility", View.VISIBLE);
            }

            if (buttonsOnly) {
                views.setViewVisibility(R.id.imgvCover, View.GONE);
                views.setViewVisibility(R.id.imgvCoverLarge, View.GONE);
                views.setViewVisibility(R.id.imgvBackground, View.GONE);
            } else if (showCoverAsBcg) {
                views.setViewVisibility(R.id.imgvCover, View.GONE);
                views.setViewVisibility(R.id.imgvCoverLarge, View.GONE);
                views.setViewVisibility(R.id.imgvBackground, View.VISIBLE);
                int iconSize = 4 * context.getResources().getDimensionPixelSize(android.R.dimen.app_icon_size);
                Bitmap icon = null;
                if (widgetState.media != null) {
                    icon = loadCover(context, iconSize, widgetState.media, new FastBlurTransformation());
                }
                if (icon != null) {
                    views.setImageViewBitmap(R.id.imgvBackground, icon);
                } else {
                    views.setViewVisibility(R.id.imgvBackground, View.GONE);
                }
            } else {
                int minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
                boolean largeCover = minHeight >= 100 && columns >= 2;
                views.setViewVisibility(R.id.imgvCover, largeCover ? View.GONE : View.VISIBLE);
                views.setViewVisibility(R.id.imgvCoverLarge, largeCover ? View.VISIBLE : View.GONE);
                views.setViewVisibility(R.id.imgvBackground, View.GONE);
                int iconSize = largeCover
                        ? context.getResources().getDimensionPixelSize(R.dimen.widget_cover_large)
                        : context.getResources().getDimensionPixelSize(android.R.dimen.app_icon_size);
                int radius = context.getResources().getDimensionPixelSize(R.dimen.widget_inner_radius);
                Bitmap icon = null;
                if (widgetState.media != null) {
                    icon = loadCover(context, iconSize, widgetState.media, new RoundedCorners(radius));
                }
                if (icon != null) {
                    views.setImageViewBitmap(largeCover ? R.id.imgvCoverLarge : R.id.imgvCover, icon);
                } else {
                    views.setImageViewResource(largeCover ? R.id.imgvCoverLarge : R.id.imgvCover, R.mipmap.ic_launcher);
                }
            }
            boolean sleepWindowOnly = prefs.getBoolean(PlayerWidget.KEY_WIDGET_SLEEP_WINDOW_ONLY + id, false);
            anySleepWindowOnly |= sleepWindowOnly;
            boolean locked = sleepWindowOnly && !inSleepWindow;
            if (locked) {
                for (int buttonId : PLAYBACK_BUTTON_IDS) {
                    views.setOnClickPendingIntent(buttonId, startMediaPlayer);
                }
            }
            if (!locked && widgetState.media != null) {
                setPlaybackButtonIntents(context, views);
                views.setOnClickPendingIntent(R.id.butPlaybackSpeed, startPlaybackSpeedDialog);
            }

            int backgroundColor = prefs.getInt(PlayerWidget.KEY_WIDGET_COLOR + id, PlayerWidget.DEFAULT_COLOR);
            views.setInt(R.id.widgetLayout, "setBackgroundColor", backgroundColor);

            manager.updateAppWidget(id, views);
        }
        if (anySleepWindowOnly) {
            WidgetUpdaterWorker.enqueueAtWindowBoundary(context, millisUntilNextSleepWindowBoundary());
        }
    }

    /**
     * Fork: one RemoteViews per widget, because widgets may use different layouts (horizontal/vertical).
     */
    private static RemoteViews createViews(Context context, WidgetState widgetState, int layout,
                                           PendingIntent startMediaPlayer, PendingIntent startPlaybackSpeedDialog) {
        RemoteViews views = new RemoteViews(context.getPackageName(), layout);

        if (widgetState.media != null) {
            views.setOnClickPendingIntent(R.id.layout_left, startMediaPlayer);
            views.setOnClickPendingIntent(R.id.imgvCover, startMediaPlayer);
            views.setOnClickPendingIntent(R.id.imgvCoverLarge, startMediaPlayer);
            views.setOnClickPendingIntent(R.id.butPlaybackSpeed, startPlaybackSpeedDialog);

            views.setTextViewText(R.id.txtvTitle, widgetState.media.getEpisodeTitle());
            views.setViewVisibility(R.id.txtvTitle, View.VISIBLE);
            views.setViewVisibility(R.id.txtNoPlaying, View.GONE);

            String progressString = getProgressString(widgetState.position,
                    widgetState.duration, widgetState.playbackSpeed);
            if (progressString != null) {
                views.setViewVisibility(R.id.txtvProgress, View.VISIBLE);
                views.setTextViewText(R.id.txtvProgress, progressString);
            }

            if (widgetState.status == PlayerStatus.PLAYING) {
                views.setImageViewResource(R.id.butPlay, R.drawable.ic_widget_pause);
                views.setContentDescription(R.id.butPlay, context.getString(R.string.pause_label));
                views.setImageViewResource(R.id.butPlayExtended, R.drawable.ic_widget_pause);
                views.setContentDescription(R.id.butPlayExtended, context.getString(R.string.pause_label));
            } else {
                views.setImageViewResource(R.id.butPlay, R.drawable.ic_widget_play);
                views.setContentDescription(R.id.butPlay, context.getString(R.string.play_label));
                views.setImageViewResource(R.id.butPlayExtended, R.drawable.ic_widget_play);
                views.setContentDescription(R.id.butPlayExtended, context.getString(R.string.play_label));
            }
            setPlaybackButtonIntents(context, views);
        } else {
            // start the app if they click anything
            views.setOnClickPendingIntent(R.id.layout_left, startMediaPlayer);
            views.setOnClickPendingIntent(R.id.butPlay, startMediaPlayer);
            views.setOnClickPendingIntent(R.id.butPlayExtended, startMediaPlayer);
            views.setViewVisibility(R.id.txtvProgress, View.GONE);
            views.setViewVisibility(R.id.txtvTitle, View.GONE);
            views.setViewVisibility(R.id.txtNoPlaying, View.VISIBLE);
            views.setImageViewResource(R.id.imgvCover, R.mipmap.ic_launcher);
            views.setImageViewResource(R.id.butPlay, R.drawable.ic_widget_play);
            views.setImageViewResource(R.id.butPlayExtended, R.drawable.ic_widget_play);
        }
        return views;
    }

    private static void setPlaybackButtonIntents(Context context, RemoteViews views) {
        views.setOnClickPendingIntent(R.id.butPlay,
                MediaButtonStarter.createPendingIntent(context, Player.COMMAND_PLAY_PAUSE));
        views.setOnClickPendingIntent(R.id.butPlayExtended,
                MediaButtonStarter.createPendingIntent(context, Player.COMMAND_PLAY_PAUSE));
        views.setOnClickPendingIntent(R.id.butRew,
                MediaButtonStarter.createPendingIntent(context, Player.COMMAND_SEEK_BACK));
        views.setOnClickPendingIntent(R.id.butFastForward,
                MediaButtonStarter.createPendingIntent(context, Player.COMMAND_SEEK_FORWARD));
        views.setOnClickPendingIntent(R.id.butSkip,
                MediaButtonStarter.createPendingIntent(context, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM));
        views.setOnClickPendingIntent(R.id.butLongRewind, MediaButtonStarter.createSessionCommandPendingIntent(
                context, MediaButtonStarter.SESSION_COMMAND_LONG_REWIND_PLAY));
        views.setOnClickPendingIntent(R.id.butSkipAd, new SkipAdOutroActivityStarter(context).getPendingIntent());
    }

    private static long millisUntilNextSleepWindowBoundary() {
        Calendar now = Calendar.getInstance();
        long best = Long.MAX_VALUE;
        for (int hour : new int[] {SleepTimerPreferences.autoEnableFrom(), SleepTimerPreferences.autoEnableTo()}) {
            Calendar boundary = (Calendar) now.clone();
            boundary.set(Calendar.HOUR_OF_DAY, hour);
            boundary.set(Calendar.MINUTE, 0);
            boundary.set(Calendar.SECOND, 5);
            boundary.set(Calendar.MILLISECOND, 0);
            if (!boundary.after(now)) {
                boundary.add(Calendar.DAY_OF_MONTH, 1);
            }
            best = Math.min(best, boundary.getTimeInMillis() - now.getTimeInMillis());
        }
        return best;
    }

    private static Bitmap loadCover(Context context, int iconSize, Playable media, Transformation<Bitmap> transform) {
        try {
            return Glide.with(context)
                    .asBitmap()
                    .load(media.getImageLocation())
                    .dontAnimate()
                    .transform(transform)
                    .submit(iconSize, iconSize)
                    .get(500, TimeUnit.MILLISECONDS);
        } catch (Throwable tr1) {
            try {
                return Glide.with(context)
                        .asBitmap()
                        .load(ImageResourceUtils.getFallbackImageLocation(media))
                        .dontAnimate()
                        .transform(transform)
                        .submit(iconSize, iconSize)
                        .get(500, TimeUnit.MILLISECONDS);
            } catch (Throwable tr2) {
                Log.e(TAG, "Error loading the media icon for the widget", tr2);
                return null;
            }
        }
    }

    /**
     * Returns number of cells needed for given size of the widget.
     *
     * @param size Widget size in dp.
     * @return Size in number of cells.
     */
    private static int getCellsForSize(int size) {
        int n = 2;
        while (70 * n - 30 < size) {
            ++n;
        }
        return n - 1;
    }

    private static String getProgressString(int position, int duration, float speed) {
        if (position < 0 || duration <= 0) {
            return null;
        }
        TimeSpeedConverter converter = new TimeSpeedConverter(speed);
        if (UserPreferences.shouldShowRemainingTime()) {
            return Converter.getDurationStringLong(converter.convert(position)) + " / -"
                    + Converter.getDurationStringLong(converter.convert(Math.max(0, duration - position)));
        } else {
            return Converter.getDurationStringLong(converter.convert(position)) + " / "
                    + Converter.getDurationStringLong(converter.convert(duration));
        }
    }
}
