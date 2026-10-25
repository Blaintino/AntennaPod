package de.danoeh.antennapod.ui.appstartintent;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

/**
 * Launches the "skip ad" (jump behind the stored ad outro) activity of the app.
 * Does not require a dependency on the actual implementation of the activity.
 */
public class SkipAdOutroActivityStarter {
    public static final String INTENT = "de.danoeh.antennapod.intents.SKIP_AD_OUTRO";
    private final Intent intent;
    private final Context context;

    public SkipAdOutroActivityStarter(Context context) {
        this.context = context;
        intent = new Intent(INTENT);
        intent.setPackage(context.getPackageName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
    }

    public PendingIntent getPendingIntent() {
        return PendingIntent.getActivity(context, R.id.pending_intent_skip_ad_outro, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
