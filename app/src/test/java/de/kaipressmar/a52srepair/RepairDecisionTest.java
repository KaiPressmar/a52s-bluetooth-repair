package de.kaipressmar.a52srepair;

import android.media.AudioManager;
import org.junit.Test;
import static org.junit.Assert.*;

public class RepairDecisionTest {
    private static final long NOW = 2_000_000L;

    private BluetoothHealth health(
            int mode,
            boolean hfp,
            boolean scoAvailable,
            boolean scoSelected,
            boolean speaker) {
        return BluetoothHealth.assess(
                true,
                true,
                mode,
                hfp,
                scoAvailable,
                scoSelected,
                speaker);
    }

    private RepairDecision decide(
            BluetoothHealth health,
            boolean mediaAlive,
            int suspectCount,
            int degradedCount,
            long lastRepair) {
        return RepairDecision.decide(
                FailureSignature.classify(health, mediaAlive),
                health,
                suspectCount,
                degradedCount,
                NOW,
                lastRepair);
    }

    @Test public void firstRouteDriftSampleWaitsToAvoidTransientRepair() {
        BluetoothHealth drift =
                health(AudioManager.MODE_IN_CALL, true, true, false, false);

        assertEquals(
                RepairDecision.Action.WAIT_FOR_CONFIRMATION,
                decide(drift, true, 1, 0, 0L).action);
    }

    @Test public void confirmedRouteDriftReselectsScoHfp() {
        BluetoothHealth drift =
                health(AudioManager.MODE_IN_CALL, true, true, false, false);

        assertEquals(
                RepairDecision.Action.RESELECT_COMMUNICATION_ROUTE,
                decide(drift, true, 2, 0, 0L).action);
    }

    @Test public void intentionalSpeakerphoneAlwaysWinsOverAutoRepair() {
        BluetoothHealth speaker =
                health(AudioManager.MODE_IN_CALL, true, true, false, true);

        assertEquals(
                RepairDecision.Action.NONE,
                decide(speaker, true, 10, 0, 0L).action);
    }

    @Test public void recentSuccessfulRepairPreventsRouteThrashing() {
        BluetoothHealth drift =
                health(AudioManager.MODE_IN_CALL, true, true, false, false);
        long recent =
                NOW - RepairPolicy.MIN_REPAIR_INTERVAL_MS + 1L;

        assertEquals(
                RepairDecision.Action.NONE,
                decide(drift, true, 3, 0, recent).action);
    }

    @Test public void firstHfpNoScoSampleIsConfirmedBeforeEscalation() {
        BluetoothHealth broken =
                health(AudioManager.MODE_IN_COMMUNICATION, true, false, false, false);

        assertEquals(
                RepairDecision.Action.WAIT_FOR_CONFIRMATION,
                decide(broken, true, 0, 1, 0L).action);
    }

    @Test public void confirmedHfpNoScoWithWorkingMediaEscalatesVendorStack() {
        BluetoothHealth broken =
                health(AudioManager.MODE_IN_COMMUNICATION, true, false, false, false);

        FailureSignature signature = FailureSignature.classify(broken, true);
        RepairDecision decision =
                RepairDecision.decide(signature, broken, 0, 2, NOW, 0L);

        assertEquals(
                FailureSignature.Kind.HFP_CONNECTED_NO_SCO_MEDIA_ALIVE,
                signature.kind);
        assertEquals(
                RepairDecision.Action.ESCALATE_VENDOR_STACK,
                decision.action);
    }

    @Test public void genericCallWithNoHfpDoesNotTriggerDestructiveRecovery() {
        BluetoothHealth generic =
                health(AudioManager.MODE_IN_CALL, false, false, false, false);

        assertEquals(
                RepairDecision.Action.NONE,
                decide(generic, true, 0, 99, 0L).action);
    }

    @Test public void longIdleConnectionNeverTriggersRepair() {
        BluetoothHealth idle =
                health(AudioManager.MODE_NORMAL, true, false, false, false);

        assertEquals(
                RepairDecision.Action.NONE,
                decide(idle, true, 99, 99, 0L).action);
    }

    @Test public void failureCanRecurAfterCooldownAndBeRepairedAgain() {
        BluetoothHealth drift =
                health(AudioManager.MODE_IN_CALL, true, true, false, false);
        long oldRepair =
                NOW - RepairPolicy.MIN_REPAIR_INTERVAL_MS - 1L;

        assertEquals(
                RepairDecision.Action.RESELECT_COMMUNICATION_ROUTE,
                decide(drift, true, 2, 0, oldRepair).action);
    }
}
