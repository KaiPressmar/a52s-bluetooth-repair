package de.kaipressmar.a52srepair;

import android.media.AudioManager;
import org.junit.Test;
import static org.junit.Assert.*;

public class HfpAudioTransportPolicyTest {
    private BluetoothHealth health(boolean selected, boolean speaker) {
        return BluetoothHealth.assess(
                true,
                true,
                AudioManager.MODE_IN_CALL,
                true,
                true,
                selected,
                speaker);
    }

    @Test public void selectedBluetoothRouteCanStillBeBrokenWhenScoTransportIsDown() {
        BluetoothHealth selected = health(true, false);

        assertTrue(
                HfpAudioTransportPolicy.isMismatch(
                        selected,
                        Boolean.FALSE));
    }

    @Test public void transportMismatchNeedsTwoSamplesBeforeForcedReselection() {
        BluetoothHealth selected = health(true, false);

        RepairDecision first =
                HfpAudioTransportPolicy.decide(
                        selected,
                        Boolean.FALSE,
                        1,
                        1_000_000L,
                        0L);
        RepairDecision confirmed =
                HfpAudioTransportPolicy.decide(
                        selected,
                        Boolean.FALSE,
                        2,
                        1_000_000L,
                        0L);

        assertEquals(
                RepairDecision.Action.WAIT_FOR_CONFIRMATION,
                first.action);
        assertEquals(
                RepairDecision.Action.RESELECT_COMMUNICATION_ROUTE,
                confirmed.action);
    }

    @Test public void connectedScoTransportVerifiesSuccessfulRepair() {
        assertTrue(
                HfpAudioTransportPolicy.verificationSucceeded(
                        health(true, false),
                        Boolean.TRUE));
    }

    @Test public void knownDisconnectedScoTransportDoesNotClaimRepairSuccess() {
        assertFalse(
                HfpAudioTransportPolicy.verificationSucceeded(
                        health(true, false),
                        Boolean.FALSE));
    }

    @Test public void unknownTransportFallsBackToCommunicationRouteSignal() {
        assertTrue(
                HfpAudioTransportPolicy.verificationSucceeded(
                        health(true, false),
                        null));
    }

    @Test public void intentionalSpeakerphoneNeverCountsAsTransportMismatch() {
        assertFalse(
                HfpAudioTransportPolicy.isMismatch(
                        health(false, true),
                        Boolean.FALSE));
    }
}
