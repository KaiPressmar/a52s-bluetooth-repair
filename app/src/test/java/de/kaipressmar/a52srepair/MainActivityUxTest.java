package de.kaipressmar.a52srepair;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class MainActivityUxTest {
    @Test public void dashboardExposesDetectionRepairAndWatchdogFlow() {
        MainActivity activity =
                Robolectric.buildActivity(MainActivity.class).create().start().resume().get();

        String text = allText(activity.findViewById(android.R.id.content));

        assertContains(text, "BLUETOOTH TELEFONIE");
        assertContains(text, "Deine Verbindung.");
        assertContains(text, "HFP");
        assertContains(text, "SCO");
        assertContains(text, "AUTO");
        assertContains(text, "Jetzt prüfen & bei Bedarf reparieren");
        assertContains(text, "Nur Diagnose ausführen");
        assertContains(text, "SCO/HFP neu verbinden");
        assertContains(text, "Auto-Schutz");
        assertContains(text, "Automatische Überwachung starten");
        assertContains(text, "Letzte Prüfung");
        assertContains(text, "Verlauf");
        assertContains(text, "Noch keine Verlaufsdaten");
        assertContains(text, "Systempfad blockiert");
        assertContains(text, "Bluetooth Agent öffnen");
        assertContains(text, "Technische Details anzeigen");
        assertContains(text, "Version ");
    }

    private void assertContains(String actual, String expected) {
        assertTrue(
                "Expected dashboard text: " + expected + "\nActual text:\n" + actual,
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
