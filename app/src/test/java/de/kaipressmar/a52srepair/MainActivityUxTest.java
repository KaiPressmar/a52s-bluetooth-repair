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
    @Test public void bottomNavigationRepresentsDestinationsNotImmediateActions() {
        MainActivity activity =
                Robolectric.buildActivity(MainActivity.class).create().start().resume().get();

        View nav =
                activity.findViewById(android.R.id.content)
                        .findViewWithTag("bottom-navigation");
        assertNotNull(nav);
        assertTrue(nav instanceof ViewGroup);
        assertEquals(3, ((ViewGroup) nav).getChildCount());

        String text = allText(nav);
        assertContains(text, "Übersicht");
        assertContains(text, "Verlauf");
        assertContains(text, "Werkzeuge");
        assertFalse(text.contains("Diagnose"));
        assertFalse(text.contains("Reparatur"));
    }

    @Test public void overviewFocusesOnStatusAndEverydayActions() {
        MainActivity activity =
                Robolectric.buildActivity(MainActivity.class).create().start().resume().get();

        String text = allText(activity.findViewById(android.R.id.content));

        assertContains(text, "Übersicht");
        assertContains(text, "Deine Verbindung.");
        assertContains(text, "Automatisch geschützt.");
        assertContains(text, "Jetzt prüfen");
        assertContains(text, "Auto-Schutz aktivieren");
        assertContains(text, "HFP");
        assertContains(text, "SCO");
        assertFalse(text.contains("Nur Diagnose"));
        assertFalse(text.contains("SCO/HFP neu auswählen"));
    }

    @Test public void historyDestinationShowsHistorySpecificContent() {
        MainActivity activity =
                Robolectric.buildActivity(MainActivity.class).create().start().resume().get();

        View history =
                activity.findViewById(android.R.id.content)
                        .findViewWithTag("nav-history");
        assertNotNull(history);
        history.performClick();

        String text = allText(activity.findViewById(android.R.id.content));
        assertContains(text, "Stabilität statt Log-Datei");
        assertContains(text, "Letzte 24 Stunden");
        assertContains(text, "Historie verwalten");
        assertContains(text, "Verlauf zurücksetzen");
        assertFalse(text.contains("Jetzt prüfen"));
    }

    @Test public void toolsDestinationGroupsAdvancedActionsAndUpdates() {
        MainActivity activity =
                Robolectric.buildActivity(MainActivity.class).create().start().resume().get();

        View tools =
                activity.findViewById(android.R.id.content)
                        .findViewWithTag("nav-tools");
        assertNotNull(tools);
        tools.performClick();

        String text = allText(activity.findViewById(android.R.id.content));
        assertContains(text, "Werkzeuge");
        assertContains(text, "Alles Technische an einem Ort");
        assertContains(text, "Nur Diagnose");
        assertContains(text, "SCO/HFP neu auswählen");
        assertContains(text, "Bluetooth Agent");
        assertContains(text, "App & Updates");
        assertContains(text, "Technische Details");
        assertFalse(text.contains("Deine Verbindung."));
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
