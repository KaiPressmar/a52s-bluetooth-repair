package de.kaipressmar.a52srepair;

import android.media.AudioManager;
import org.junit.Test;
import static org.junit.Assert.*;

public class WatchdogScheduleTest {
    private BluetoothHealth health(
            int mode,
            boolean hfpConnected,
            boolean scoAvailable,
            boolean scoSelected) {
        return BluetoothHealth.assess(
                true,
                true,
                mode,
                hfpConnected,
                scoAvailable,
                scoSelected,
                false);
    }

    @Test public void idleUsesHourlyHeartbeat() {
        BluetoothHealth idle =
                health(AudioManager.MODE_NORMAL, true, true, false);
        assertEquals(
                60L * 60L * 1000L,
                WatchdogSchedule.nextDelayMillis(idle, 0, 0));
    }

    @Test public void firstSuspectIsConfirmedQuicklyInsteadOfPollingContinuously() {
        BluetoothHealth suspect =
                health(AudioManager.MODE_IN_COMMUNICATION, true, true, false);
        assertEquals(
                4_000L,
                WatchdogSchedule.nextDelayMillis(suspect, 1, 0));
    }

    @Test public void confirmedSuspectBacksOffAfterRepairOpportunity() {
        BluetoothHealth suspect =
                health(AudioManager.MODE_IN_COMMUNICATION, true, true, false);
        assertEquals(
                5L * 60L * 1000L,
                WatchdogSchedule.nextDelayMillis(suspect, 2, 0));
    }

    @Test public void healthyCallUsesSparseSafetyRecheck() {
        BluetoothHealth healthy =
                health(AudioManager.MODE_IN_COMMUNICATION, true, true, true);
        assertEquals(
                5L * 60L * 1000L,
                WatchdogSchedule.nextDelayMillis(healthy, 0, 0));
    }

    @Test public void firstMissingScoSampleIsConfirmedBeforeBackingOff() {
        BluetoothHealth noSco =
                health(AudioManager.MODE_IN_CALL, true, false, false);
        assertEquals(BluetoothHealth.State.HFP_CONNECTED_NO_SCO, noSco.state);
        assertEquals(
                8_000L,
                WatchdogSchedule.nextDelayMillis(noSco, 0, 1));
    }

    @Test public void confirmedMissingScoStateBacksOff() {
        BluetoothHealth noSco =
                health(AudioManager.MODE_IN_CALL, true, false, false);
        assertEquals(
                5L * 60L * 1000L,
                WatchdogSchedule.nextDelayMillis(noSco, 0, 2));
    }
}
