package de.kaipressmar.a52srepair;

import android.app.Application;
import android.app.NotificationManager;
import com.google.android.material.color.DynamicColors;
import de.kaipressmar.a52srepair.data.AppSettings;
import java.io.File;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(this);
        migrateFromForegroundServiceVersions();
        de.kaipressmar.a52srepair.update.UpdateLinks.removeLegacyDownloads(this);
    }

    /**
     * Up to v0.15 the app ran a permanent foreground service with notifications. Remove its
     * channels, state and log once, so nothing of the old behavior stays visible.
     */
    private void migrateFromForegroundServiceVersions() {
        AppSettings settings = new AppSettings(this);
        if (settings.migratedToVersionCode() >= 17) return;

        NotificationManager notifications = getSystemService(NotificationManager.class);
        if (notifications != null) {
            notifications.cancelAll();
            for (String channel : new String[] {"monitor", "app-updates"}) {
                notifications.deleteNotificationChannel(channel);
            }
        }
        deleteSharedPreferences("repair-state");
        deleteSharedPreferences("health-history");
        //noinspection ResultOfMethodCallIgnored
        new File(getFilesDir(), "a52s-bt-repair.log").delete();
        settings.setMigratedToVersionCode(17);
    }
}
