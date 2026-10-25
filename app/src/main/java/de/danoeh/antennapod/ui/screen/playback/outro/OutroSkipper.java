package de.danoeh.antennapod.ui.screen.playback.outro;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.util.Log;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.event.playback.PlaybackPositionEvent;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.playback.service.PlaybackController;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.ui.common.Converter;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import org.greenrobot.eventbus.EventBus;

/**
 * Player actions "remember ad outro" and "skip ad": a short piece of audio (the jingle that ends an ad
 * block) is stored per podcast and later searched for in the downloaded file ahead of the current position.
 */
public final class OutroSkipper {
    private static final String TAG = "OutroSkipper";
    private static final long CAPTURE_BEFORE_MS = 7000;
    private static final long CAPTURE_AFTER_MS = 1500;
    private static final float DEFAULT_LENGTH_S = 2.0f;
    private static final float DEFAULT_END_OFFSET_S = -0.5f;
    private static final long SEARCH_START_OFFSET_MS = 500;
    private static final long SEARCH_RANGE_MS = 10 * 60 * 1000;

    private OutroSkipper() {
    }

    /** Opens the dialog to cut the outro out of the audio right before the current position. */
    public static void capture(Context context, FeedMedia media) {
        if (!checkPreconditions(context, media)) {
            return;
        }
        PlaybackController.bindToMedia3Service(context, controller -> {
            boolean loaded = controller.getCurrentMediaItem() != null;
            long position = loaded ? controller.getCurrentPosition() : media.getPosition();
            boolean wasPlaying = loaded && controller.isPlaying();
            if (wasPlaying) {
                controller.pause();
            }
            Context appContext = context.getApplicationContext();
            String path = media.getLocalFileUrl();
            Disposable unused = Single.fromCallable(() -> decodeWindow(appContext, path,
                            Math.max(0, position - CAPTURE_BEFORE_MS), position + CAPTURE_AFTER_MS))
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(window -> showCaptureDialog(context, media, position, window, wasPlaying),
                        error -> {
                            Log.e(TAG, Log.getStackTraceString(error));
                            Toast.makeText(context, context.getString(R.string.outro_error,
                                    error.getMessage()), Toast.LENGTH_LONG).show();
                            resume(context, wasPlaying);
                        });
        });
    }

    /** Searches the stored outro ahead of the current position and jumps right behind it. */
    public static void skip(Context context, FeedMedia media) {
        skip(context, media, () -> { });
    }

    /**
     * Like {@link #skip(Context, FeedMedia)}; {@code onFinished} runs once the search is over (or not started).
     */
    public static void skip(Context context, FeedMedia media, Runnable onFinished) {
        if (!checkPreconditions(context, media)) {
            onFinished.run();
            return;
        }
        final long feedId = media.getItem().getFeedId();
        if (!OutroStore.has(context, feedId)) {
            Toast.makeText(context, R.string.outro_none_saved, Toast.LENGTH_LONG).show();
            onFinished.run();
            return;
        }
        PlaybackController.bindToMedia3Service(context, controller -> {
            long position = controller.getCurrentMediaItem() != null
                    ? controller.getCurrentPosition() : media.getPosition();
            runSearch(context, media, feedId, position, onFinished);
        });
    }

    private static boolean checkPreconditions(Context context, FeedMedia media) {
        if (media == null || media.getItem() == null || !media.localFileAvailable()
                || media.getLocalFileUrl() == null) {
            Toast.makeText(context, R.string.outro_not_downloaded, Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    private static void runSearch(Context context, FeedMedia media, long feedId, long position,
                                  Runnable onFinished) {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        ProgressBar progress = new ProgressBar(context);
        progress.setIndeterminate(true);
        int padding = dp(context, 24);
        progress.setPadding(padding, padding, padding, padding);
        AlertDialog progressDialog = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.outro_searching)
                .setView(progress)
                .setNegativeButton(R.string.cancel_label, (d, w) -> cancelled.set(true))
                .setOnCancelListener(d -> cancelled.set(true))
                .setOnDismissListener(d -> onFinished.run())
                .show();

        // The calling activity may already be gone when the search ends (widget), so use the app context
        Context appContext = context.getApplicationContext();
        String path = media.getLocalFileUrl();
        long start = position + SEARCH_START_OFFSET_MS;
        long end = start + SEARCH_RANGE_MS;
        if (media.getDuration() > 0) {
            end = Math.min(end, media.getDuration());
        }
        final long searchEnd = end;
        Disposable unused = Single.fromCallable(() -> search(appContext, feedId, path, start, searchEnd, cancelled))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    progressDialog.dismiss();
                    if (cancelled.get()) {
                        return;
                    }
                    String score = String.format(Locale.US, "%.2f", result.score);
                    if (result.found) {
                        seekTo(appContext, media, result.timeMs);
                        Toast.makeText(appContext, appContext.getString(R.string.outro_found,
                                Converter.getDurationStringLong((int) result.timeMs), score),
                                Toast.LENGTH_LONG).show();
                    } else {
                        String at = result.timeMs >= 0
                                ? Converter.getDurationStringLong((int) result.timeMs) : "–";
                        Toast.makeText(appContext, appContext.getString(R.string.outro_not_found, score, at),
                                Toast.LENGTH_LONG).show();
                    }
                }, error -> {
                    progressDialog.dismiss();
                    Log.e(TAG, Log.getStackTraceString(error));
                    Toast.makeText(appContext, appContext.getString(R.string.outro_error, error.getMessage()),
                            Toast.LENGTH_LONG).show();
                });
    }

    private static SearchResult search(Context context, long feedId, String path, long startMs, long endMs,
                                       AtomicBoolean cancelled) throws IOException {
        float[] template = OutroStore.load(context, feedId);
        if (template == null) {
            throw new IOException("No template");
        }
        OutroMatcher matcher = new OutroMatcher(OutroFeatureExtractor.extract(template),
                OutroMatcher.DEFAULT_THRESHOLD);
        OutroFeatureExtractor extractor = new OutroFeatureExtractor();
        long firstMs = OutroAudioReader.read(context, path, startMs, endMs, (samples, count) -> {
            List<float[]> frames = extractor.push(samples, 0, count);
            return !matcher.push(frames) && !cancelled.get();
        });
        SearchResult result = new SearchResult();
        result.found = matcher.hasMatch();
        result.score = result.found ? matcher.getMatchScore() : matcher.getBestScore();
        long frame = result.found ? matcher.getMatchEndFrame() : matcher.getBestEndFrame();
        result.timeMs = frame < 0 || firstMs < 0 ? -1 : firstMs
                + OutroFeatureExtractor.frameEndSample(frame) * 1000 / OutroFeatureExtractor.SAMPLE_RATE;
        return result;
    }

    private static void seekTo(Context context, FeedMedia media, long timeMs) {
        PlaybackController.bindToMedia3Service(context, controller -> {
            if (controller.getCurrentMediaItem() != null) {
                controller.seekTo(timeMs);
            } else {
                media.setPosition((int) timeMs);
                DBWriter.setFeedMediaPlaybackInformation(media);
                EventBus.getDefault().post(new PlaybackPositionEvent(media.getPosition(), media.getDuration()));
            }
        });
    }

    private static void resume(Context context, boolean wasPlaying) {
        if (wasPlaying) {
            PlaybackController.bindToMedia3Service(context, controller -> controller.play());
        }
    }

    private static DecodedWindow decodeWindow(Context context, String path, long startMs, long endMs)
            throws IOException {
        DecodedWindow window = new DecodedWindow();
        window.samples = new float[(int) ((endMs - startMs) * OutroFeatureExtractor.SAMPLE_RATE / 1000) + 8192];
        window.firstMs = OutroAudioReader.read(context, path, startMs, endMs, (samples, count) -> {
            int copy = Math.min(count, window.samples.length - window.count);
            System.arraycopy(samples, 0, window.samples, window.count, copy);
            window.count += copy;
            return window.count < window.samples.length;
        });
        if (window.firstMs < 0 || window.count == 0) {
            throw new IOException("No audio decoded");
        }
        return window;
    }

    private static void showCaptureDialog(Context context, FeedMedia media, long position,
                                          DecodedWindow window, boolean wasPlaying) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(context, 24);
        layout.setPadding(padding, dp(context, 8), padding, 0);

        TextView hint = new TextView(context);
        hint.setText(R.string.outro_capture_hint);
        layout.addView(hint);

        TextView lengthLabel = new TextView(context);
        lengthLabel.setPadding(0, dp(context, 16), 0, 0);
        layout.addView(lengthLabel);
        Slider lengthSlider = new Slider(context);
        lengthSlider.setValueFrom(0.3f);
        lengthSlider.setValueTo(5.0f);
        lengthSlider.setStepSize(0.1f);
        lengthSlider.setValue(DEFAULT_LENGTH_S);
        layout.addView(lengthSlider);

        TextView offsetLabel = new TextView(context);
        layout.addView(offsetLabel);
        Slider offsetSlider = new Slider(context);
        offsetSlider.setValueFrom(-1.5f);
        offsetSlider.setValueTo(1.5f);
        offsetSlider.setStepSize(0.1f);
        offsetSlider.setValue(DEFAULT_END_OFFSET_S);
        layout.addView(offsetSlider);

        Runnable updateLabels = () -> {
            lengthLabel.setText(context.getString(R.string.outro_length_label,
                    String.format(Locale.getDefault(), "%.1f", lengthSlider.getValue())));
            offsetLabel.setText(context.getString(R.string.outro_end_offset_label,
                    String.format(Locale.getDefault(), "%+.1f", offsetSlider.getValue())));
        };
        updateLabels.run();
        lengthSlider.addOnChangeListener((s, v, fromUser) -> updateLabels.run());
        offsetSlider.addOnChangeListener((s, v, fromUser) -> updateLabels.run());

        TextView status = new TextView(context);
        status.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.addView(status);

        AudioTrack[] track = new AudioTrack[1];
        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.outro_capture_label)
                .setView(layout)
                .setPositiveButton(R.string.outro_save, null)
                .setNeutralButton(R.string.outro_preview, null)
                .setNegativeButton(R.string.cancel_label, null)
                .create();
        dialog.setOnDismissListener(d -> {
            stopPreview(track);
            resume(context, wasPlaying);
        });
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                stopPreview(track);
                float[] template = cut(window, position, lengthSlider.getValue(), offsetSlider.getValue());
                track[0] = play(template);
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                float[] template = cut(window, position, lengthSlider.getValue(), offsetSlider.getValue());
                try {
                    new OutroMatcher(OutroFeatureExtractor.extract(template), OutroMatcher.DEFAULT_THRESHOLD);
                } catch (IllegalArgumentException e) {
                    status.setText(R.string.outro_silent);
                    return;
                }
                try {
                    OutroStore.save(context, media.getItem().getFeedId(), template);
                    Toast.makeText(context, R.string.outro_saved, Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                } catch (IOException e) {
                    status.setText(context.getString(R.string.outro_error, e.getMessage()));
                }
            });
        });
        dialog.show();
    }

    /**
     * Samples of {@code lengthS} seconds ending at {@code position + endOffsetS}.
     */
    private static float[] cut(DecodedWindow window, long position, float lengthS, float endOffsetS) {
        int rate = OutroFeatureExtractor.SAMPLE_RATE;
        long endSample = (position - window.firstMs) * rate / 1000 + Math.round(endOffsetS * rate);
        int end = (int) Math.max(0, Math.min(window.count, endSample));
        int begin = Math.max(0, end - Math.round(lengthS * rate));
        return Arrays.copyOfRange(window.samples, begin, end);
    }

    private static AudioTrack play(float[] samples) {
        if (samples.length == 0) {
            return null;
        }
        short[] pcm = new short[samples.length];
        for (int i = 0; i < samples.length; i++) {
            pcm[i] = (short) Math.max(-32768, Math.min(32767, Math.round(samples[i] * 32767)));
        }
        AudioTrack track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(OutroFeatureExtractor.SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.length * 2)
                .build();
        track.write(pcm, 0, pcm.length);
        track.play();
        return track;
    }

    private static void stopPreview(AudioTrack[] track) {
        if (track[0] != null) {
            try {
                track[0].stop();
            } catch (IllegalStateException ignored) {
                // Not playing
            }
            track[0].release();
            track[0] = null;
        }
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static class DecodedWindow {
        float[] samples;
        int count;
        long firstMs;
    }

    private static class SearchResult {
        boolean found;
        float score;
        long timeMs;
    }
}
