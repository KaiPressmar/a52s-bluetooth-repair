package de.kaipressmar.a52srepair;

import android.media.AudioManager;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * End-to-end policy scenarios derived from the reported field pattern:
 * media remains connected, phone call audio falls back to the handset, and reboot temporarily
 * restores the HFP/SCO path.
 */
public class FieldFailureScenarioTest {
    @Test public void a52sMediaAliveCallAudioDeadIsDetectedButNotFalselyPubliclyRepairable() {
        BluetoothHealth health =
                BluetoothHealth.assess(
                        true,
                        true,
                        AudioManager.MODE_IN_CALL,
                        true,
                        false,
                        false,
                        false);

        FailureSignature signature = FailureSignature.classify(health, true);

        assertTrue(health.inCommunication);
        assertTrue(health.hfpProfileConnected);
        assertFalse(health.scoAvailable);
        assertEquals(
                FailureSignature.Kind.HFP_CONNECTED_NO_SCO_MEDIA_ALIVE,
                signature.kind);
        assertTrue(signature.matchesKnownSamsungFailure());
        assertFalse(signature.publicApiRepairPossible);
    }

    @Test public void routeDriftVariantIsDetectedAndRepairableWithPublicApi() {
        BluetoothHealth health =
                BluetoothHealth.assess(
                        true,
                        true,
                        AudioManager.MODE_IN_CALL,
                        true,
                        true,
                        false,
                        false);

        FailureSignature signature = FailureSignature.classify(health, true);
        RepairDecision first =
                RepairDecision.decide(signature, health, 1, 0, 2_000_000L, 0L);
        RepairDecision confirmed =
                RepairDecision.decide(signature, health, 2, 0, 2_004_000L, 0L);

        assertEquals(
                FailureSignature.Kind.RECOVERABLE_ROUTE_DRIFT,
                signature.kind);
        assertEquals(
                RepairDecision.Action.WAIT_FOR_CONFIRMATION,
                first.action);
        assertEquals(
                RepairDecision.Action.RESELECT_COMMUNICATION_ROUTE,
                confirmed.action);
    }

    @Test public void healthyCallAfterRestartIsNotRepaired() {
        BluetoothHealth restored =
                BluetoothHealth.assess(
                        true,
                        true,
                        AudioManager.MODE_IN_CALL,
                        true,
                        true,
                        true,
                        false);

        FailureSignature signature = FailureSignature.classify(restored, true);
        RepairDecision decision =
                RepairDecision.decide(
                        signature,
                        restored,
                        0,
                        0,
                        3_000_000L,
                        0L);

        assertEquals(FailureSignature.Kind.HEALTHY_CALL, signature.kind);
        assertEquals(RepairDecision.Action.NONE, decision.action);
    }

    @Test public void staleIdleHfpStateDoesNotGenerateFalsePositiveBeforeCallStarts() {
        BluetoothHealth idle =
                BluetoothHealth.assess(
                        true,
                        true,
                        AudioManager.MODE_NORMAL,
                        true,
                        false,
                        false,
                        false);

        assertEquals(BluetoothHealth.State.IDLE, idle.state);
        assertEquals(
                FailureSignature.Kind.IDLE,
                FailureSignature.classify(idle, true).kind);
    }
}
