package de.danoeh.antennapod.ui.screen.playback.outro;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Random;
import org.junit.Test;

public class OutroMatcherTest {
    private static final int SR = OutroFeatureExtractor.SAMPLE_RATE;
    // Synthetic signals (noise + tones) correlate less than real recordings of the same jingle
    private static final float THRESHOLD = 0.45f;

    private static float[] tones(double[] freqs, double secondsEach, double gain) {
        int per = (int) (secondsEach * SR);
        float[] out = new float[per * freqs.length];
        for (int t = 0; t < freqs.length; t++) {
            for (int i = 0; i < per; i++) {
                double x = 2 * Math.PI * freqs[t] * i / SR;
                double env = Math.min(1.0, Math.min(i, per - i) / (0.02 * SR));
                out[t * per + i] = (float) (gain * env * (0.6 * Math.sin(x) + 0.3 * Math.sin(2 * x)
                        + 0.1 * Math.sin(3 * x)));
            }
        }
        return out;
    }

    /** Noise with randomly changing resonances and loudness, roughly like speech. */
    private static float[] speechLike(double seconds, long seed) {
        Random random = new Random(seed);
        float[] out = new float[(int) (seconds * SR)];
        int segment = SR / 10;
        double f1 = 500;
        double f2 = 1500;
        double amp = 0.3;
        double y1 = 0;
        double y2 = 0;
        for (int i = 0; i < out.length; i++) {
            if (i % segment == 0) {
                f1 = 300 + random.nextDouble() * 700;
                f2 = 900 + random.nextDouble() * 1800;
                amp = random.nextDouble() < 0.2 ? 0.02 : 0.15 + random.nextDouble() * 0.3;
            }
            double noise = random.nextGaussian();
            y1 = 0.9 * y1 + 0.1 * noise * Math.sin(2 * Math.PI * f1 * i / SR);
            y2 = 0.9 * y2 + 0.1 * noise * Math.sin(2 * Math.PI * f2 * i / SR);
            out[i] = (float) (amp * (y1 + y2 + 0.05 * noise));
        }
        return out;
    }

    private static void mixIn(float[] target, float[] source, int at) {
        for (int i = 0; i < source.length && at + i < target.length; i++) {
            target[at + i] += source[i];
        }
    }

    private static OutroMatcher search(float[] template, float[] stream, int chunk) {
        OutroMatcher matcher = new OutroMatcher(OutroFeatureExtractor.extract(template), THRESHOLD);
        OutroFeatureExtractor extractor = new OutroFeatureExtractor();
        for (int pos = 0; pos < stream.length; pos += chunk) {
            List<float[]> frames = extractor.push(stream, pos, Math.min(chunk, stream.length - pos));
            if (matcher.push(frames)) {
                break;
            }
        }
        return matcher;
    }

    private static double endSeconds(long frame) {
        return OutroFeatureExtractor.frameEndSample(frame) / (double) SR;
    }

    @Test
    public void findsJingleAtCorrectPositionDespiteLoudnessAndNoise() {
        double[] melody = {523, 659, 784};
        float[] template = tones(melody, 0.5, 0.8);
        float[] stream = speechLike(60, 1);
        int insertAt = (int) (37.2 * SR);
        float[] quieterJingle = tones(melody, 0.5, 0.3);
        mixIn(stream, quieterJingle, insertAt);
        mixIn(stream, speechLike(60, 2), 0);

        OutroMatcher matcher = search(template, stream, 4096);
        assertTrue("score " + matcher.getBestScore(), matcher.hasMatch());
        double expectedEnd = 37.2 + 1.5;
        assertEquals(expectedEnd, endSeconds(matcher.getMatchEndFrame()), 0.05);
    }

    @Test
    public void noMatchWithoutJingle() {
        float[] template = tones(new double[] {523, 659, 784}, 0.5, 0.8);
        float[] stream = speechLike(120, 3);
        OutroMatcher matcher = search(template, stream, 3000);
        assertFalse("best score " + matcher.getBestScore(), matcher.hasMatch());
    }

    @Test
    public void noMatchForDifferentJingle() {
        float[] template = tones(new double[] {523, 659, 784}, 0.5, 0.8);
        float[] stream = speechLike(60, 4);
        mixIn(stream, tones(new double[] {392, 349, 294}, 0.5, 0.5), 20 * SR);
        OutroMatcher matcher = search(template, stream, 5000);
        assertFalse("best score " + matcher.getBestScore(), matcher.hasMatch());
    }

    @Test
    public void findsShortSingleTone() {
        float[] template = tones(new double[] {1046}, 0.4, 0.7);
        float[] stream = speechLike(45, 5);
        mixIn(stream, tones(new double[] {1046}, 0.4, 0.4), 30 * SR);
        OutroMatcher matcher = search(template, stream, 2048);
        assertTrue("score " + matcher.getBestScore(), matcher.hasMatch());
        assertEquals(30.4, endSeconds(matcher.getMatchEndFrame()), 0.05);
    }

    @Test
    public void findsFirstOfTwoOccurrences() {
        double[] melody = {440, 554, 659, 880};
        float[] template = tones(melody, 0.3, 0.6);
        float[] stream = speechLike(90, 6);
        mixIn(stream, tones(melody, 0.3, 0.6), 15 * SR);
        mixIn(stream, tones(melody, 0.3, 0.6), 70 * SR);
        OutroMatcher matcher = search(template, stream, 4096);
        assertTrue(matcher.hasMatch());
        assertEquals(15 + 1.2, endSeconds(matcher.getMatchEndFrame()), 0.05);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsSilentTemplate() {
        new OutroMatcher(OutroFeatureExtractor.extract(new float[SR]), THRESHOLD);
    }
}
