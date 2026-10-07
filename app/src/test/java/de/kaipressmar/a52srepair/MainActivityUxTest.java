package de.kaipressmar.a52srepair;

import android.view.View;
import android.view.ViewGroup;
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
        assertContains(text, "Werkzeuge");
        assertContains(text, "Einstellungen");
        assertFalse(text.contains("Diagnose"));
        assertFalse(text.contains("Reparatur"));
    }

    @Test public void overviewContainsOnlyEverydayStatusAndControls() {
        MainActivity activity =
                Robolectric.buildActivity(MainActivity.class).create().start().resume().get();

        String text = allText(activity.findViewById(android.R.id.content));

        assertContains(text, "Telefonie-Audio.");
        assertContains(text, "Automatisch geschützt.");
        assertContains(text, "Jetzt prüfen");
        assertContains(text, "Automatische Überwachung");
        assertContains(text, "HFP");
        assertContains(text, "SCO");
        assertFalse(text.contains("Nur Diagnose"));
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
        assertContains(text, "Daten");
        assertContains(text, "Verlauf zurücksetzen");
        assertFalse(text.contains("Diagnoseprotokoll teilen"));
        assertFalse(text.contains("Jetzt prüfen"));
    }

    @Test public void toolsAreReservedForManualDiagnosisAndRecovery() {
        MainActivity activity =
                Robolectric.buildActivity(MainActivity.class).create().start().resume().get();

        View tools =
                activity.findViewById(android.R.id.content)
                        .findViewWithTag("nav-tools");
        assertNotNull(tools);
        tools.performClick();

        String text = allText(activity.findViewById(android.R.id.content));
        assertContains(text, "Nur Diagnose");
        assertContains(text, "SCO/HFP neu auswählen");
        assertContains(text, "Samsung Bluetooth Agent");
        assertContains(text, "Bluetooth-Einstellungen");
        assertContains(text, "Entwickleroptionen");
        assertFalse(text.contains("App & Updates"));
        assertFalse(text.contains("Technische Details anzeigen"));
    }

    @Test public void settingsOwnUpdatesNotificationsSupportAndAppInfo() {
        MainActivity activity =
                Robolectric.buildActivity(MainActivity.class).create().start().resume().get();

        View settings =
                activity.findViewById(android.R.id.content)
                        .findViewWithTag("nav-settings");
        assertNotNull(settings);
        settings.performClick();

        String text = allText(activity.findViewById(android.R.id.content));
        assertContains(text, "App & Updates");
        assertContains(text, "Benachrichtigungen");
        assertContains(text, "Diagnose & Support");
        assertContains(text, "Technische Details anzeigen");
        assertContains(text, "Diagnoseprotokoll teilen");
        assertContains(text, "App-Info");
        assertContains(text, "Android 16 · API 36");
        assertFalse(text.contains("SCO/HFP neu auswählen"));
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
