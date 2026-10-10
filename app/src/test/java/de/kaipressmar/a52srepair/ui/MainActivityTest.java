package de.kaipressmar.a52srepair.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import de.kaipressmar.a52srepair.R;
import de.kaipressmar.a52srepair.data.AppSettings;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {34, 36})
public class MainActivityTest {
    @Test public void freshInstallAsksForOneTimeSetup() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                TextView title = activity.findViewById(R.id.hero_title);
                assertEquals(activity.getString(R.string.status_setup_title), title.getText().toString());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.setup_card).getVisibility());
            });
        }
    }

    @Test public void bottomNavigationReachesHistoryAndSettings() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                com.google.android.material.bottomnavigation.BottomNavigationView nav =
                        activity.findViewById(R.id.bottom_nav);
                nav.setSelectedItemId(R.id.nav_history);
                assertEquals(View.VISIBLE, activity.findViewById(R.id.history_empty).getVisibility());
                nav.setSelectedItemId(R.id.nav_settings);
                assertNotNull(activity.getSupportFragmentManager()
                        .findFragmentByTag(String.valueOf(R.id.nav_settings)));
            });
        }
    }

    @Test public void legacyForegroundServiceStateIsMigratedAway() {
        Context context = ApplicationProvider.getApplicationContext();
        assertTrue(new AppSettings(context).migratedToVersionCode() >= 17);
    }

    @Test public void manualCheckExplainsMissingAccessWithoutPretendingToRepair() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertNotNull(activity.findViewById(R.id.manual_check));
                assertNotNull(activity.findViewById(R.id.manual_repair));
                activity.findViewById(R.id.manual_check).performClick();
                TextView result = activity.findViewById(R.id.manual_result);
                assertEquals(activity.getString(R.string.manual_need_bluetooth), result.getText().toString());
                activity.findViewById(R.id.manual_repair).performClick();
                assertEquals(activity.getString(R.string.manual_need_bluetooth), result.getText().toString());
            });
        }
    }
}
