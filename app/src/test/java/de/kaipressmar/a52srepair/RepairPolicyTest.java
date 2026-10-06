package de.kaipressmar.a52srepair;

import android.media.AudioManager;
import org.junit.Test;
import static org.junit.Assert.*;

public class RepairPolicyTest {
    private BluetoothHealth suspect(boolean speakerphone) {
        return BluetoothHealth.assess(
                true,
                true,
                AudioManager.MODE_IN_CALL,
                true,
                true,
                false,
                speakerphone);
    }

    @Test public void oneSuspectSampleIsNotEnough() {
        assertFalse(RepairPolicy.canAutoRepair(suspect(false), 1, 100_000L, 0L));
    }

    @Test public void twoSuspectSamplesAllowRepairAfterCooldown() {
        assertTrue(RepairPolicy.canAutoRepair(suspect(false), 2, 100_000L, 0L));
    }

    @Test public void speakerphoneBlocksAutomaticRepair() {
        assertFalse(RepairPolicy.canAutoRepair(suspect(true), 3, 100_000L, 0L));
    }

    @Test public void cooldownBlocksRouteThrashing() {
        long now = 100_000L;
        long recentRepair = now - RepairPolicy.MIN_REPAIR_INTERVAL_MS + 1L;
        assertFalse(RepairPolicy.canAutoRepair(suspect(false), 3, now, recentRepair));
    }

    @Test public void cooldownBoundaryAllowsRepair() {
        long now = 100_000L;
        long previousRepair = now - RepairPolicy.MIN_REPAIR_INTERVAL_MS;
        assertTrue(RepairPolicy.canAutoRepair(suspect(false), 2, now, previousRepair));
    }

    @Test public void healthyStateNeverRepairs() {
        BluetoothHealth healthy = BluetoothHealth.assess(
                true, true, AudioManager.MODE_IN_CALL, true, true, true, false);
        assertFalse(RepairPolicy.canAutoRepair(healthy, 99, 100_000L, 0L));
    }
}
