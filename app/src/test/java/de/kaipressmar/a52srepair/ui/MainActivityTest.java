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
    @Test public void updateDialogOffersCanonicalBrowserReleaseAndExplainsAndroidWarnings() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                var release = new de.kaipressmar.a52srepair.update.UpdateRelease(
                        "9.0.0", "v9.0.0", "app.apk", "", "", "https://evil.example", false);
                activity.onCheckedUpdateResult(release, "");
                activity.installUpdate();
                android.app.Dialog dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog();
                assertNotNull(dialog);
                androidx.appcompat.app.AlertDialog alert = (androidx.appcompat.app.AlertDialog) dialog;
                assertEquals(activity.getString(R.string.update_security_help),
                        ((TextView) alert.findViewById(android.R.id.message)).getText().toString());
                alert.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
                assertEquals("https://github.com/KaiPressmar/a52s-bluetooth-repair/releases/tag/v9.0.0",
                        org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity().getDataString());
            });
        }
    }
    @Test public void idleTestPermissionIsExplainedAndOldCallControlsRemainAvailable() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                org.robolectric.Shadows.shadowOf((android.app.Application) activity.getApplicationContext())
                        .grantPermissions(android.Manifest.permission.BLUETOOTH_CONNECT);
                org.robolectric.Shadows.shadowOf((android.app.Application) activity.getApplicationContext())
                        .denyPermissions(android.Manifest.permission.READ_PHONE_STATE);
                assertNotNull(activity.findViewById(R.id.idle_repair));
                activity.findViewById(R.id.idle_repair).performClick();
                androidx.appcompat.app.AlertDialog dialog = (androidx.appcompat.app.AlertDialog)
                        org.robolectric.shadows.ShadowDialog.getLatestDialog();
                assertEquals(activity.getString(R.string.idle_permission_body),
                        ((TextView) dialog.findViewById(android.R.id.message)).getText().toString());
                dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).performClick();
                assertNotNull(activity.findViewById(R.id.manual_repair));
                assertEquals("NOT_RUN", new AppSettings(activity).idleResult());
            });
        }
    }

    @Test public void grantingPhoneStatusWhilePausedDefersTheManualTestUntilResume() {
        Context context=ApplicationProvider.getApplicationContext();
        org.robolectric.Shadows.shadowOf((android.app.Application)context).grantPermissions(
                android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.READ_PHONE_STATE);
        org.robolectric.Shadows.shadowOf(context.getSystemService(android.telecom.TelecomManager.class)).setIsInCall(false);
        org.robolectric.android.controller.ActivityController<MainActivity> controller=
                org.robolectric.Robolectric.buildActivity(MainActivity.class).setup();
        controller.pause();controller.get().onPhoneStatusResult(true);
        assertEquals("NOT_RUN",new AppSettings(context).idleResult());
        controller.resume();assertEquals("RUNNING",new AppSettings(context).idleResult());
        controller.pause().stop().destroy();assertEquals("CANCELLED",new AppSettings(context).idleResult());
    }

    @Test public void navigationSurvivesActivityRecreation() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> ((com.google.android.material.bottomnavigation.BottomNavigationView)
                    activity.findViewById(R.id.bottom_nav)).setSelectedItemId(R.id.nav_history));
            scenario.recreate();
            scenario.onActivity(activity -> assertEquals(R.id.nav_history,
                    ((com.google.android.material.bottomnavigation.BottomNavigationView)
                            activity.findViewById(R.id.bottom_nav)).getSelectedItemId()));
        }
    }

    @Test public void delayedUpdateCannotNavigateAfterStateWasSavedOrActivityDestroyed() {
        new AppSettings(ApplicationProvider.getApplicationContext()).setProtectionEnabled(false);
        org.robolectric.android.controller.ActivityController<MainActivity> controller =
                org.robolectric.Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity activity = controller.get();
        de.kaipressmar.a52srepair.update.UpdateRelease release = new de.kaipressmar.a52srepair.update.UpdateRelease(
                "9.0.0", "v9.0.0", "app.apk", "", "", "", false);
        controller.pause().saveInstanceState(new android.os.Bundle()).stop();
        activity.onCheckedUpdateResult(release, "");
        controller.destroy();
        activity.onCheckedUpdateResult(release, "");
        activity.refreshStatus();
    }
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

    @Test public void interruptedIdleSessionIsUnresolvedOnProcessStartupAndNeverReportedAsTested() {
        Context context=ApplicationProvider.getApplicationContext();
        AppSettings settings=new AppSettings(context);settings.setIdleResult("RUNNING");
        ((de.kaipressmar.a52srepair.App)context).onCreate();
        assertEquals("UNRESOLVED",settings.idleResult());
        assertTrue(de.kaipressmar.a52srepair.diagnostics.DiagnosticLog.readAll(context).contains("teardown unverified"));
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
