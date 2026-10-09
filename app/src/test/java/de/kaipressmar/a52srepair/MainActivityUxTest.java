package de.kaipressmar.a52srepair;

import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class MainActivityUxTest {
    @Test public void bottomNavigationContainsOnlyRealDestinations() {
        MainActivity activity =
                Robolectric.buildActivity(MainActivity.class).create().start().resume().get();

        View nav =
                activity.findViewById(android.R.id.content)
                        .findViewWithTag("bottom-navigation");
        assertNotNull(nav);
        assertTrue(nav instanceof ViewGroup);
        assertEquals(4, ((ViewGroup) nav).getChildCount());

        String text = allText(nav);
        assertContains(text, "Übersicht");
        assertContains(text, "Verlauf");
        assertContains(text, "Diagnose");
        assertContains(text, "Einstellungen");
        assertFalse(text.contains("SCO/HFP neu auswählen"));
        assertFalse(text.contains("Jetzt prüfen"));
    }

    @Test public void overviewAvoidsDuplicateAutoStatusAndKeepsEverydayControls() {
        MainActivity activity =
                Robolectric.buildActivity(MainActivity.class).create().start().resume().get();

        String text = allText(activity.findViewById(android.R.id.content));

        assertContains(text, "Telefonie-Audio.");
        assertContains(text, "Automatisch geschützt.");
        assertContains(text, "Jetzt prüfen");
        assertContains(text, "Automatische Überwachung");
        assertContains(text, "HFP-PROFIL");
        assertContains(text, "TELEFONIE-ROUTE");
        assertFalse(text.contains("AUTO\n"));
        assertFalse(text.contains("SCO/HFP neu auswählen"));
        assertFalse(text.contains("Diagnoseprotokoll teilen"));
    }

    @Test public void historyShowsSummaryChartEventsAndOnlyHistoryManagement() {
        MainActivity activity =
                Robolectric.buildActivity(MainActivity.class).create().start().resume().get();

        View history =
                activity.findViewById(android.R.id.content)
                        .findViewWithTag("nav-history");
        assertNotNull(history);
        history.performClick();

        String text = allText(activity.findViewById(android.R.id.content));
        assertContains(text, "Letzte 24 Stunden");
        assertContains(text, "Letzte Ereignisse");
        assertContains(text, "REPARIERT");
        assertContains(text, "Daten");
        assertContains(text, "Verlauf zurücksetzen");
        assertFalse(text.contains("Diagnoseprotokoll teilen"));
        assertFalse(text.contains("Jetzt prüfen"));
    }

    @Test public void diagnosisDestinationOwnsGuidedStatusRecoveryAndSupportData() {
        MainActivity activity =
                Robolectric.buildActivity(MainActivity.class).create().start().resume().get();

        View diagnosis =
                activity.findViewById(android.R.id.content)
                        .findViewWithTag("nav-tools");
        assertNotNull(diagnosis);
        diagnosis.performClick();

        String text = allText(activity.findViewById(android.R.id.content));
        assertContains(text, "Live-Diagnose");
        assertContains(text, "Bluetooth");
        assertContains(text, "HFP-Profil");
        assertContains(text, "Anruf-Erkennung");
        assertContains(text, "SCO/Telefonie");
        assertContains(text, "Fehlersignatur");
        assertContains(text, "Diagnose aktualisieren");
        assertContains(text, "Ich höre den Anrufer nicht");
        assertContains(text, "Samsung Bluetooth Agent");
        assertContains(text, "Technische Details anzeigen");
        assertContains(text, "Diagnoseprotokoll teilen");
        assertFalse(text.contains("App & Updates"));
    }

    @Test public void settingsOwnPreferencesUpdatesNotificationsAndAppInfoOnly() {
        MainActivity activity =
                Robolectric.buildActivity(MainActivity.class).create().start().resume().get();

        View settings =
                activity.findViewById(android.R.id.content)
                        .findViewWithTag("nav-settings");
        assertNotNull(settings);
        settings.performClick();

        String text = allText(activity.findViewById(android.R.id.content));
        assertContains(text, "Schutzverhalten");
        assertContains(text, "Automatisch reparieren");
        assertContains(text, "App & Updates");
        assertContains(text, "Benachrichtigungen");
        assertContains(text, "App-Info");
        assertContains(text, "Geräte-Runtime");
        assertContains(text, "App-Ziel-SDK");
        assertFalse(text.contains("Diagnoseprotokoll teilen"));
        assertFalse(text.contains("Technische Details anzeigen"));
        assertFalse(text.contains("SCO/HFP neu auswählen"));
    }

    @Test public void autoRepairPreferenceCanBeDisabledWithoutStoppingMonitoringSetting() {
        MainActivity activity =
                Robolectric.buildActivity(MainActivity.class).create().start().resume().get();
        RepairStateStore.setAutoRepairEnabled(activity, true);

        View settings =
                activity.findViewById(android.R.id.content)
                        .findViewWithTag("nav-settings");
        settings.performClick();

        Switch toggle = findFirstSwitch(activity.findViewById(android.R.id.content));
        assertNotNull(toggle);
        assertTrue(toggle.isChecked());

        toggle.setChecked(false);

        assertFalse(RepairStateStore.autoRepairEnabled(activity));
    }

    private Switch findFirstSwitch(View root) {
        java.util.ArrayDeque<View> queue = new java.util.ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            View view = queue.remove();
            if (view instanceof Switch) return (Switch) view;
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    queue.add(group.getChildAt(i));
                }
            }
        }
        return null;
    }

    private void assertContains(String actual, String expected) {
        assertTrue(
                "Expected UI text: " + expected + "\nActual text:\n" + actual,
                actual.contains(expected));
    }

    private String allText(View root) {
        StringBuilder text = new StringBuilder();
        java.util.ArrayDeque<View> queue = new java.util.ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            View view = queue.remove();
            if (view instanceof TextView) {
                text.append(((TextView) view).getText()).append('\n');
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    queue.add(group.getChildAt(i));
                }
            }
        }
        return text.toString();
    }
}
