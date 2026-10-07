package de.kaipressmar.a52srepair;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class RepairStateStoreTest {
    private Context context;

    @Before public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("repair-state", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test public void hfpAudioTransportPreservesConnectedDisconnectedAndUnknown() {
        RepairStateStore.saveHfpAudioTransport(context, Boolean.TRUE);
        assertEquals(Boolean.TRUE, RepairStateStore.lastHfpAudioTransport(context));

        RepairStateStore.saveHfpAudioTransport(context, Boolean.FALSE);
        assertEquals(Boolean.FALSE, RepairStateStore.lastHfpAudioTransport(context));

        RepairStateStore.saveHfpAudioTransport(context, null);
        assertNull(RepairStateStore.lastHfpAudioTransport(context));
    }

    @Test public void monitoringAndAutoRepairArePersisted() {
        assertFalse(RepairStateStore.monitoringEnabled(context));
        assertTrue(RepairStateStore.autoRepairEnabled(context));

        RepairStateStore.setMonitoringEnabled(context, true);
        RepairStateStore.setAutoRepairEnabled(context, false);

        assertTrue(RepairStateStore.monitoringEnabled(context));
        assertFalse(RepairStateStore.autoRepairEnabled(context));
    }

    @Test public void suspectCounterRequiresConsecutiveObservations() {
        assertEquals(1, RepairStateStore.updateConsecutiveSuspect(context, true));
        assertEquals(2, RepairStateStore.updateConsecutiveSuspect(context, true));
        assertEquals(0, RepairStateStore.updateConsecutiveSuspect(context, false));
        assertEquals(1, RepairStateStore.updateConsecutiveSuspect(context, true));
    }

    @Test public void routeOwnershipIsPersisted() {
        assertFalse(RepairStateStore.routeOwned(context));
        RepairStateStore.setRouteOwned(context, true);
        assertTrue(RepairStateStore.routeOwned(context));
        RepairStateStore.setRouteOwned(context, false);
        assertFalse(RepairStateStore.routeOwned(context));
    }

    @Test public void healthAndRepairHistoryAreStored() {
        BluetoothHealth health = BluetoothHealth.assess(
                true, true, android.media.AudioManager.MODE_IN_CALL, true, true, false, false);
        RepairStateStore.saveHealth(context, health);
        RepairStateStore.markRepair(context);

        assertEquals(BluetoothHealth.State.SUSPECT_ROUTING.name(), RepairStateStore.lastState(context));
        assertEquals(health.summary, RepairStateStore.lastSummary(context));
        assertTrue(RepairStateStore.lastCheckAt(context) > 0L);
        assertTrue(RepairStateStore.lastRepairAt(context) > 0L);
        assertEquals(1, RepairStateStore.repairCount(context));
    }
}
