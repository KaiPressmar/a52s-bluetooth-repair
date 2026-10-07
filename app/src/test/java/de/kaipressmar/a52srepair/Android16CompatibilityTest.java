package de.kaipressmar.a52srepair;

import android.app.Activity;
import android.os.Build;
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
@Config(sdk = 36)
public class Android16CompatibilityTest {
    @Test public void dashboardAndRepairEngineLoadOnAndroid16() {
        Activity activity =
                Robolectric.buildActivity(MainActivity.class).create().start().resume().get();

        assertEquals(36, Build.VERSION.SDK_INT);
        String text = allText(activity.findViewById(android.R.id.content));
        assertTrue(text.contains("Android 16 / API 36"));
        assertTrue(text.contains("HFP"));
        assertTrue(text.contains("SCO"));

        BluetoothHealth health =
                BluetoothHealth.assess(
                        true,
                        true,
                        android.media.AudioManager.MODE_IN_COMMUNICATION,
                        true,
                        true,
                        false,
                        false);
        assertEquals(BluetoothHealth.State.SUSPECT_ROUTING, health.state);
        assertTrue(health.needsRepair());
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
