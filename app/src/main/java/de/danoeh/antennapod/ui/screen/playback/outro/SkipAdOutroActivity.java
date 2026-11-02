package de.danoeh.antennapod.ui.screen.playback.outro;

import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.playback.service.PlayStartLog;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.storage.preferences.SleepTimerPreferences;
import de.danoeh.antennapod.ui.appstartintent.MediaButtonStarter;
import de.danoeh.antennapod.ui.common.ThemeSwitcher;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.Calendar;

/**
 * Translucent activity started by the widget's "skip ad" button: runs {@link OutroSkipper#skip} for the
 * current episode (showing only the progress dialog) and closes itself afterwards.
 */
public class SkipAdOutroActivity extends AppCompatActivity {
    private static final String TAG = "SkipAdOutroActivity";
    private Disposable disposable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(ThemeSwitcher.getTranslucentTheme(this));
        super.onCreate(savedInstanceState);
        if (getIntent().getBooleanExtra(MediaButtonStarter.EXTRA_SLEEP_WINDOW_ONLY, false)
                && !SleepTimerPreferences.isInTimeRange(SleepTimerPreferences.autoEnableFrom(),
                        SleepTimerPreferences.autoEnableTo(), Calendar.getInstance().get(Calendar.HOUR_OF_DAY))) {
            // The lock screen widget was not redrawn in time, see MediaButtonStarter.EXTRA_SLEEP_WINDOW_ONLY
            PlayStartLog.add(this, "Widget-Druck außerhalb des Schlummer-Zeitraums ignoriert");
            finish();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true); // the widget may be on the lock screen
        }
        disposable = Maybe.fromCallable(() -> DBReader.getFeedMedia(
                        PlaybackPreferences.getCurrentlyPlayingFeedMediaId()))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(media -> OutroSkipper.skip(this, media, this::finish),
                    error -> {
                        Log.e(TAG, Log.getStackTraceString(error));
                        finish();
                    },
                    () -> OutroSkipper.skip(this, (FeedMedia) null, this::finish));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (disposable != null) {
            disposable.dispose();
        }
    }
}
