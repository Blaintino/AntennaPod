package de.danoeh.antennapod.ui.screen.playback.outro;

import android.content.Context;
import androidx.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Stores one outro template per feed as 16-bit little-endian mono PCM at
 * {@link OutroFeatureExtractor#SAMPLE_RATE} in the app's files directory.
 */
public final class OutroStore {
    private OutroStore() {
    }

    private static File file(Context context, long feedId) {
        return new File(new File(context.getFilesDir(), "outro"), "feed_" + feedId + ".pcm");
    }

    public static boolean has(Context context, long feedId) {
        return file(context, feedId).isFile();
    }

    public static void save(Context context, long feedId, float[] samples) throws IOException {
        File target = file(context, feedId);
        File dir = target.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("Could not create " + dir);
        }
        ByteBuffer buffer = ByteBuffer.allocate(samples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (float sample : samples) {
            buffer.putShort((short) Math.max(-32768, Math.min(32767, Math.round(sample * 32767))));
        }
        try (FileOutputStream out = new FileOutputStream(target)) {
            out.write(buffer.array());
        }
    }

    @Nullable
    public static float[] load(Context context, long feedId) throws IOException {
        File source = file(context, feedId);
        if (!source.isFile()) {
            return null;
        }
        byte[] bytes = new byte[(int) source.length()];
        try (FileInputStream in = new FileInputStream(source)) {
            int read = 0;
            while (read < bytes.length) {
                int n = in.read(bytes, read, bytes.length - read);
                if (n < 0) {
                    break;
                }
                read += n;
            }
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] samples = new float[bytes.length / 2];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = buffer.getShort() / 32768f;
        }
        return samples;
    }
}
