package de.danoeh.antennapod.ui.screen.playback.outro;

import java.util.List;

/**
 * Slides a template (sequence of spectral frames) over a stream of frames and reports the
 * first position where the Pearson correlation exceeds a threshold (refined to the local
 * maximum shortly after). Correlation is independent of overall loudness, because a gain
 * change only adds a constant to all log energies.
 */
public class OutroMatcher {
    // Calibrated on a real podcast: the actual outro scored > 0.9, everything else < 0.8
    public static final float DEFAULT_THRESHOLD = 0.85f;
    private final float[] template;
    private final int templateFrames;
    private final float threshold;
    private final float[][] ring;
    private long framesSeen = 0;

    private float bestScore = -1;
    private long bestEndFrame = -1;
    private float candidateScore = -1;
    private long candidateEndFrame = -1;
    private boolean finished = false;

    public OutroMatcher(List<float[]> templateFrames, float threshold) {
        if (templateFrames.isEmpty()) {
            throw new IllegalArgumentException("Empty template");
        }
        this.templateFrames = templateFrames.size();
        this.threshold = threshold;
        this.ring = new float[this.templateFrames][];
        int n = this.templateFrames * OutroFeatureExtractor.BANDS;
        template = new float[n];
        double sum = 0;
        double sumSq = 0;
        for (int f = 0; f < this.templateFrames; f++) {
            for (int b = 0; b < OutroFeatureExtractor.BANDS; b++) {
                float v = templateFrames.get(f)[b];
                template[f * OutroFeatureExtractor.BANDS + b] = v;
                sum += v;
                sumSq += (double) v * v;
            }
        }
        double mean = sum / n;
        double std = Math.sqrt(Math.max(sumSq / n - mean * mean, 0));
        if (std < 1e-6) {
            throw new IllegalArgumentException("Template has no variation");
        }
        for (int i = 0; i < n; i++) {
            template[i] = (float) ((template[i] - mean) / std);
        }
    }

    /**
     * Feeds frames. Returns true once a match is final (see {@link #getMatchEndFrame()}).
     */
    public boolean push(List<float[]> frames) {
        for (float[] frame : frames) {
            if (finished) {
                return true;
            }
            ring[(int) (framesSeen % templateFrames)] = frame;
            framesSeen++;
            if (framesSeen < templateFrames) {
                continue;
            }
            float score = scoreCurrentWindow();
            long endFrame = framesSeen - 1;
            if (score > bestScore) {
                bestScore = score;
                bestEndFrame = endFrame;
            }
            if (candidateEndFrame >= 0) {
                if (score > candidateScore) {
                    candidateScore = score;
                    candidateEndFrame = endFrame;
                } else if (endFrame - candidateEndFrame > Math.max(templateFrames / 2, 5)) {
                    finished = true;
                }
            } else if (score >= threshold) {
                candidateScore = score;
                candidateEndFrame = endFrame;
            }
        }
        return finished;
    }

    private float scoreCurrentWindow() {
        int n = template.length;
        double sum = 0;
        double sumSq = 0;
        double dot = 0;
        long first = framesSeen - templateFrames;
        for (int f = 0; f < templateFrames; f++) {
            float[] frame = ring[(int) ((first + f) % templateFrames)];
            int base = f * OutroFeatureExtractor.BANDS;
            for (int b = 0; b < OutroFeatureExtractor.BANDS; b++) {
                float v = frame[b];
                sum += v;
                sumSq += (double) v * v;
                dot += template[base + b] * v;
            }
        }
        double mean = sum / n;
        double std = Math.sqrt(Math.max(sumSq / n - mean * mean, 0));
        if (std < 1e-6) {
            return 0;
        }
        return (float) (dot / (n * std));
    }

    /** Whether a match above the threshold was found (possibly not yet refined). */
    public boolean hasMatch() {
        return candidateEndFrame >= 0;
    }

    /** Index of the last stream frame covered by the match, or -1. */
    public long getMatchEndFrame() {
        return candidateEndFrame;
    }

    public float getMatchScore() {
        return candidateScore;
    }

    public float getBestScore() {
        return bestScore;
    }

    public long getBestEndFrame() {
        return bestEndFrame;
    }

    public int getTemplateFrames() {
        return templateFrames;
    }
}
