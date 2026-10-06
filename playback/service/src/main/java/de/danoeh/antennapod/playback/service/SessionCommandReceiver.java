package de.danoeh.antennapod.playback.service;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionCommand;
import androidx.media3.session.SessionToken;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import de.danoeh.antennapod.playback.service.internal.MediaLibrarySessionCallback;

public class SessionCommandReceiver extends BroadcastReceiver {
    private static final String TAG = "SessionCommandReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : null;
        if (!MediaLibrarySessionCallback.SESSION_COMMAND_TOGGLE_SLEEP_TIMER.customAction.equals(action)
                && !MediaLibrarySessionCallback.SESSION_COMMAND_LONG_REWIND_SLEEP.customAction.equals(action)) {
            return;
        }
        PendingResult pendingResult = goAsync();
        SessionToken sessionToken = new SessionToken(context,
                new ComponentName(context, Media3PlaybackService.class));
        ListenableFuture<MediaController> controllerFuture =
                new MediaController.Builder(context, sessionToken).buildAsync();
        controllerFuture.addListener(() -> {
            MediaController controller = null;
            try {
                controller = controllerFuture.get();
                controller.sendCustomCommand(new SessionCommand(action, Bundle.EMPTY), Bundle.EMPTY);
            } catch (Exception e) {
                Log.e(TAG, "Unable to send session command " + action, e);
            } finally {
                if (controller != null) {
                    controller.release();
                }
                pendingResult.finish();
            }
        }, MoreExecutors.directExecutor());
    }
}
