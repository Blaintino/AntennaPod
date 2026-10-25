package de.danoeh.antennapod.ui.screen.playback.outro;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns mono audio (8 kHz) into a sequence of spectral frames: for every frame of
 * {@link #FRAME_SIZE} samples (hop {@link #HOP_SIZE}) the log energy in {@link #BANDS}
 * logarithmically spaced frequency bands. Samples can be pushed in arbitrary chunks.
 */
public class OutroFeatureExtractor {
    public static final int SAMPLE_RATE = 8000;
    public static final int FRAME_SIZE = 256;
    public static final int HOP_SIZE = 128;
    public static final int BANDS = 24;
    private static final double MIN_FREQUENCY = 150;
    private static final double MAX_FREQUENCY = 3800;

    private static final double[] WINDOW = new double[FRAME_SIZE];
    private static final int[] BAND_EDGES = new int[BANDS + 1];

    static {
        for (int i = 0; i < FRAME_SIZE; i++) {
            WINDOW[i] = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / (FRAME_SIZE - 1));
        }
        double binWidth = (double) SAMPLE_RATE / FRAME_SIZE;
        for (int b = 0; b <= BANDS; b++) {
            double freq = MIN_FREQUENCY * Math.pow(MAX_FREQUENCY / MIN_FREQUENCY, (double) b / BANDS);
            BAND_EDGES[b] = (int) Math.round(freq / binWidth);
        }
        for (int b = 1; b <= BANDS; b++) {
            if (BAND_EDGES[b] <= BAND_EDGES[b - 1]) {
                BAND_EDGES[b] = BAND_EDGES[b - 1] + 1;
            }
        }
    }

    private final float[] pending = new float[FRAME_SIZE];
    private int pendingCount = 0;
    private long framesProduced = 0;

    /**
     * Adds samples and returns all frames that became complete.
     */
    public List<float[]> push(float[] samples, int offset, int length) {
        List<float[]> frames = new ArrayList<>();
        for (int i = offset; i < offset + length; i++) {
            pending[pendingCount++] = samples[i];
            if (pendingCount == FRAME_SIZE) {
                frames.add(computeFrame(pending));
                framesProduced++;
                System.arraycopy(pending, HOP_SIZE, pending, 0, FRAME_SIZE - HOP_SIZE);
                pendingCount = FRAME_SIZE - HOP_SIZE;
            }
        }
        return frames;
    }

    public long getFramesProduced() {
        return framesProduced;
    }

    /**
     * Convenience: all frames of a complete signal.
     */
    public static List<float[]> extract(float[] samples) {
        return new OutroFeatureExtractor().push(samples, 0, samples.length);
    }

    /**
     * Sample index (relative to the first pushed sample) where frame {@code frameIndex} ends.
     */
    public static long frameEndSample(long frameIndex) {
        return frameIndex * HOP_SIZE + FRAME_SIZE;
    }

    private static float[] computeFrame(float[] samples) {
        double[] re = new double[FRAME_SIZE];
        double[] im = new double[FRAME_SIZE];
        for (int i = 0; i < FRAME_SIZE; i++) {
            re[i] = samples[i] * WINDOW[i];
        }
        fft(re, im);
        float[] bands = new float[BANDS];
        for (int b = 0; b < BANDS; b++) {
            double energy = 0;
            for (int k = BAND_EDGES[b]; k < BAND_EDGES[b + 1] && k < FRAME_SIZE / 2; k++) {
                energy += re[k] * re[k] + im[k] * im[k];
            }
            bands[b] = (float) Math.log10(energy + 1e-6);
        }
        return bands;
    }

    /**
     * In-place iterative radix-2 FFT, length must be a power of two.
     */
    private static void fft(double[] re, double[] im) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) {
                j ^= bit;
            }
            j ^= bit;
            if (i < j) {
                double t = re[i];
                re[i] = re[j];
                re[j] = t;
                t = im[i];
                im[i] = im[j];
                im[j] = t;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double angle = -2 * Math.PI / len;
            double stepRe = Math.cos(angle);
            double stepIm = Math.sin(angle);
            for (int i = 0; i < n; i += len) {
                double curRe = 1;
                double curIm = 0;
                for (int k = 0; k < len / 2; k++) {
                    int a = i + k;
                    int b = i + k + len / 2;
                    double prodRe = re[b] * curRe - im[b] * curIm;
                    double prodIm = re[b] * curIm + im[b] * curRe;
                    re[b] = re[a] - prodRe;
                    im[b] = im[a] - prodIm;
                    re[a] += prodRe;
                    im[a] += prodIm;
                    double nextRe = curRe * stepRe - curIm * stepIm;
                    curIm = curRe * stepIm + curIm * stepRe;
                    curRe = nextRe;
                }
            }
        }
    }
}
