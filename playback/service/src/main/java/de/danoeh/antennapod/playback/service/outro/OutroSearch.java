package de.danoeh.antennapod.playback.service.outro;

import android.content.Context;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Searches the stored outro of a podcast in the downloaded file ahead of a position.
 */
public final class OutroSearch {
    private static final long SEARCH_START_OFFSET_MS = 500;
    private static final long SEARCH_RANGE_MS = 10 * 60 * 1000;

    private OutroSearch() {
    }

    public static class Result {
        public boolean found;
        public float score;
        public long timeMs;
    }

    /**
     * Searches from slightly after {@code positionMs} for up to ten minutes (or until {@code durationMs}).
     */
    public static Result search(Context context, long feedId, String path, long positionMs, long durationMs,
                                AtomicBoolean cancelled) throws IOException {
        long startMs = positionMs + SEARCH_START_OFFSET_MS;
        long endMs = startMs + SEARCH_RANGE_MS;
        if (durationMs > 0) {
            endMs = Math.min(endMs, durationMs);
        }
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
        Result result = new Result();
        result.found = matcher.hasMatch();
        result.score = result.found ? matcher.getMatchScore() : matcher.getBestScore();
        long frame = result.found ? matcher.getMatchEndFrame() : matcher.getBestEndFrame();
        result.timeMs = frame < 0 || firstMs < 0 ? -1 : firstMs
                + OutroFeatureExtractor.frameEndSample(frame) * 1000 / OutroFeatureExtractor.SAMPLE_RATE;
        return result;
    }
}
