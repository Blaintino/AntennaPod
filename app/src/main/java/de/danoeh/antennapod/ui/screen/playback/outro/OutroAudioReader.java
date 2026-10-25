package de.danoeh.antennapod.ui.screen.playback.outro;

import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Decodes a local audio file and delivers mono samples at {@link OutroFeatureExtractor#SAMPLE_RATE}.
 */
public final class OutroAudioReader {
    private static final long TIMEOUT_US = 10_000;
    private static final int CHUNK = 4096;

    public interface SampleConsumer {
        /**
         * Receives {@code count} samples. Return false to stop decoding.
         */
        boolean onSamples(float[] samples, int count);
    }

    private OutroAudioReader() {
    }

    /**
     * Decodes from {@code startMs} until {@code endMs} (or the end of the file).
     *
     * @return the media time (ms) of the first delivered sample, or -1 if nothing was delivered
     */
    public static long read(Context context, String pathOrUri, long startMs, long endMs,
                            SampleConsumer consumer) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        try {
            if (pathOrUri.startsWith("content://")) {
                extractor.setDataSource(context, Uri.parse(pathOrUri), null);
            } else {
                extractor.setDataSource(pathOrUri);
            }
            int track = -1;
            MediaFormat format = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat f = extractor.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    track = i;
                    format = f;
                    break;
                }
            }
            if (track < 0) {
                throw new IOException("No audio track");
            }
            extractor.selectTrack(track);
            extractor.seekTo(Math.max(0, startMs) * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);

            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
            codec.configure(format, null, null, 0);
            codec.start();

            int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            int encoding = AudioFormat.ENCODING_PCM_16BIT;
            Resampler resampler = new Resampler(sampleRate);
            float[] out = new float[CHUNK];
            int outCount = 0;
            long firstSampleMs = -1;
            boolean inputDone = false;
            boolean stop = false;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

            while (!stop) {
                if (!inputDone) {
                    int inIndex = codec.dequeueInputBuffer(TIMEOUT_US);
                    if (inIndex >= 0) {
                        ByteBuffer inBuffer = codec.getInputBuffer(inIndex);
                        int size = inBuffer == null ? -1 : extractor.readSampleData(inBuffer, 0);
                        if (size < 0 || extractor.getSampleTime() / 1000 > endMs + 1000) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                    }
                }
                int outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US);
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat outFormat = codec.getOutputFormat();
                    sampleRate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    channels = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    if (outFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        encoding = outFormat.getInteger(MediaFormat.KEY_PCM_ENCODING);
                    }
                    resampler = new Resampler(sampleRate);
                    continue;
                } else if (outIndex < 0) {
                    continue;
                }
                ByteBuffer outBuffer = codec.getOutputBuffer(outIndex);
                if (outBuffer != null && info.size > 0) {
                    outBuffer.position(info.offset);
                    outBuffer.limit(info.offset + info.size);
                    outBuffer.order(ByteOrder.nativeOrder());
                    int bytesPerSample = encoding == AudioFormat.ENCODING_PCM_FLOAT ? 4 : 2;
                    int frames = info.size / (bytesPerSample * channels);
                    for (int i = 0; i < frames && !stop; i++) {
                        float mono = 0;
                        for (int c = 0; c < channels; c++) {
                            mono += encoding == AudioFormat.ENCODING_PCM_FLOAT
                                    ? outBuffer.getFloat() : outBuffer.getShort() / 32768f;
                        }
                        mono /= channels;
                        long timeMs = (info.presentationTimeUs + i * 1_000_000L / sampleRate) / 1000;
                        if (timeMs < startMs) {
                            continue;
                        }
                        if (timeMs >= endMs) {
                            stop = true;
                            break;
                        }
                        if (firstSampleMs < 0) {
                            firstSampleMs = timeMs;
                        }
                        if (resampler.push(mono)) {
                            out[outCount++] = resampler.value();
                            if (outCount == CHUNK) {
                                if (!consumer.onSamples(out, outCount)) {
                                    stop = true;
                                }
                                outCount = 0;
                            }
                        }
                    }
                }
                codec.releaseOutputBuffer(outIndex, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break;
                }
            }
            if (outCount > 0 && !stop) {
                consumer.onSamples(out, outCount);
            }
            return firstSampleMs;
        } finally {
            if (codec != null) {
                try {
                    codec.stop();
                } catch (IllegalStateException ignored) {
                    // Codec was not started
                }
                codec.release();
            }
            extractor.release();
        }
    }

    /** Box-filter decimation to the target sample rate. */
    private static class Resampler {
        private final double ratio;
        private double phase = 0;
        private double sum = 0;
        private int count = 0;
        private float value;

        Resampler(int inputRate) {
            ratio = (double) inputRate / OutroFeatureExtractor.SAMPLE_RATE;
        }

        boolean push(float sample) {
            sum += sample;
            count++;
            phase += 1;
            if (phase >= ratio) {
                phase -= ratio;
                value = (float) (sum / count);
                sum = 0;
                count = 0;
                return true;
            }
            return false;
        }

        float value() {
            return value;
        }
    }
}
