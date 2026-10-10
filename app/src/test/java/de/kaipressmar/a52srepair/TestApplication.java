package de.kaipressmar.a52srepair;

import androidx.preference.PreferenceManager;
import de.kaipressmar.a52srepair.data.AppSettings;

/** Keep real app initialization but prevent UI tests from making startup network requests. */
public final class TestApplication extends App {
    @Override public void onCreate() {
        super.onCreate();
        PreferenceManager.getDefaultSharedPreferences(this).edit()
                .putBoolean(AppSettings.KEY_AUTO_UPDATE_CHECK, false).commit();
    }
}
