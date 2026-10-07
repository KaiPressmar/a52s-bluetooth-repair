package de.kaipressmar.a52srepair;

import android.media.AudioManager;
import org.junit.Test;
import static org.junit.Assert.*;

public class RepairRetryPolicyTest {
    private BluetoothHealth suspect() {
        return BluetoothHealth.assess(
                true,
                true,
                AudioManager.MODE_IN_CALL,
                true,
                true,
                false,
                false);
    }

    @Test public void autoRepairAllowsAtMostTwoAttemptsPerIncident() {
        RepairDecision decision =
                new RepairDecision(
                        RepairDecision.Action.RESELECT_COMMUNICATION_ROUTE,
                        "test");

        assertTrue(RepairRetryPolicy.canAttempt(true, decision, 0));
        assertTrue(RepairRetryPolicy.canAttempt(true, decision, 1));
        assertFalse(RepairRetryPolicy.canAttempt(true, decision, 2));
        assertTrue(RepairRetryPolicy.exhausted(2));
    }

    @Test public void disabledAutoRepairNeverAttempts() {
        RepairDecision decision =
                new RepairDecision(
                        RepairDecision.Action.RESELECT_COMMUNICATION_ROUTE,
                        "test");
        assertFalse(RepairRetryPolicy.canAttempt(false, decision, 0));
    }

    @Test public void verificationFailureRetriesOnlyWhileScoRemainsRoutable() {
        assertTrue(
                RepairRetryPolicy.shouldRetryAfterVerification(
                        suspect(),
                        true,
                        1));

        BluetoothHealth noSco =
                BluetoothHealth.assess(
                        true,
                        true,
                        AudioManager.MODE_IN_CALL,
                        true,
                        false,
                        false,
                        false);
        assertFalse(
                RepairRetryPolicy.shouldRetryAfterVerification(
                        noSco,
                        true,
                        1));
    }

    @Test public void speakerphoneAndEndedCallsNeverRetry() {
        BluetoothHealth speaker =
                BluetoothHealth.assess(
                        true,
                        true,
                        AudioManager.MODE_IN_CALL,
                        true,
                        true,
                        false,
                        true);
        assertFalse(
                RepairRetryPolicy.shouldRetryAfterVerification(
                        speaker,
                        true,
                        1));

        BluetoothHealth idle =
                BluetoothHealth.assess(
                        true,
                        true,
                        AudioManager.MODE_NORMAL,
                        true,
                        true,
                        false,
                        false);
        assertFalse(
                RepairRetryPolicy.shouldRetryAfterVerification(
                        idle,
                        true,
                        1));
    }

    @Test public void nonOwnedRouteIsNotAutomaticallyRetried() {
        assertFalse(
                RepairRetryPolicy.shouldRetryAfterVerification(
                        suspect(),
                        false,
                        1));
    }
}
