package de.kaipressmar.a52srepair;

import android.content.Context;
import android.media.AudioManager;
import androidx.test.core.app.ApplicationProvider;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class HealthHistoryStoreTest {
    private Context context;

    @Before public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        HealthHistoryStore.clear(context);
    }

    private BluetoothHealth health(
            int mode, boolean hfp, boolean available, boolean selected) {
        return BluetoothHealth.assess(
                true, true, mode, hfp, available, selected, false);
    }

    @Test public void recordsHealthTimeline() {
        HealthHistoryStore.record(
                context,
                health(AudioManager.MODE_IN_COMMUNICATION, true, true, true),
                "manual");

        List<HealthHistoryStore.Entry> entries = HealthHistoryStore.read(context);
        assertEquals(1, entries.size());
        assertEquals(BluetoothHealth.State.HEALTHY, entries.get(0).state);
        assertTrue(entries.get(0).isHealthyCall());
    }

    @Test public void problemCountersDistinguishHealthyAndBrokenCalls() {
        HealthHistoryStore.record(
                context,
                health(AudioManager.MODE_IN_COMMUNICATION, true, true, true),
                "manual");
        HealthHistoryStore.record(
                context,
                health(AudioManager.MODE_IN_COMMUNICATION, true, false, false),
                "manual");

        List<HealthHistoryStore.Entry> entries = HealthHistoryStore.read(context);
        assertEquals(2, entries.size());
        assertEquals(
                1,
                HealthHistoryStore.healthyCallCount(entries, 0L));
        assertEquals(
                1,
                HealthHistoryStore.problemCount(entries, 0L));
    }

    @Test public void repairCounterCanBeScopedToHistoryWindow() {
        HealthHistoryStore.record(
                context,
                health(AudioManager.MODE_IN_COMMUNICATION, true, true, true),
                "repair");
        HealthHistoryStore.record(
                context,
                health(AudioManager.MODE_IN_COMMUNICATION, true, true, false),
                "manual");

        List<HealthHistoryStore.Entry> entries = HealthHistoryStore.read(context);
        assertEquals(1, HealthHistoryStore.repairCount(entries, 0L));
        assertEquals(
                0,
                HealthHistoryStore.repairCount(
                        entries,
                        System.currentTimeMillis() + 60_000L));
    }

    @Test public void identicalBackgroundSamplesAreCoalesced() {
        BluetoothHealth idle =
                health(AudioManager.MODE_NORMAL, true, true, false);
        HealthHistoryStore.record(context, idle, "scheduled");
        HealthHistoryStore.record(context, idle, "scheduled");

        assertEquals(1, HealthHistoryStore.read(context).size());
    }

    @Test public void manualSamplesRemainVisibleEvenWhenStateIsUnchanged() {
        BluetoothHealth idle =
                health(AudioManager.MODE_NORMAL, true, true, false);
        HealthHistoryStore.record(context, idle, "manual");
        HealthHistoryStore.record(context, idle, "manual");

        assertEquals(2, HealthHistoryStore.read(context).size());
    }
}
