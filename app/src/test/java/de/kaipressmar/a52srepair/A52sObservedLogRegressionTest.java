package de.kaipressmar.a52srepair;

import android.media.AudioManager;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Regression scenarios matching an observed SM-A528B / Android 14 log:
 * the VW HFP/SCO endpoint is available while idle, then call routing must be driven by an explicit
 * call signal rather than relying only on AudioManager mode.
 */
public class A52sObservedLogRegressionTest {
    @Test public void vwScoEndpointWhileIdleMustNotBeForceRouted() {
        BluetoothHealth idle =
                BluetoothHealth.assess(
                        true,
                        true,
                        AudioManager.MODE_NORMAL,
                        true,
                        true,
                        false,
                        false,
                        false);

        assertEquals(BluetoothHealth.State.IDLE, idle.state);
        assertFalse(idle.needsRepair());
    }

    @Test public void sameRouteBecomesRepairCandidateAsSoonAsTelephonyReportsOffhook() {
        BluetoothHealth call =
                BluetoothHealth.assess(
                        true,
                        true,
                        AudioManager.MODE_NORMAL,
                        true,
                        true,
                        false,
                        false,
                        true);

        assertTrue(call.inCommunication);
        assertEquals(BluetoothHealth.State.SUSPECT_ROUTING, call.state);

        FailureSignature signature = FailureSignature.classify(call, true);
        RepairDecision confirmed =
                RepairDecision.decide(
                        signature,
                        call,
                        2,
                        0,
                        2_000_000L,
                        0L);

        assertEquals(
                RepairDecision.Action.RESELECT_COMMUNICATION_ROUTE,
                confirmed.action);
    }

    @Test public void selectedRouteIsNotEnoughWhenHfpScoTransportIsActuallyDisconnected() {
        BluetoothHealth routeSelected =
                BluetoothHealth.assess(
                        true,
                        true,
                        AudioManager.MODE_NORMAL,
                        true,
                        true,
                        true,
                        false,
                        true);

        assertEquals(BluetoothHealth.State.HEALTHY, routeSelected.state);
        assertTrue(
                HfpAudioTransportPolicy.isMismatch(
                        routeSelected,
                        Boolean.FALSE));

        RepairDecision transportRepair =
                HfpAudioTransportPolicy.decide(
                        routeSelected,
                        Boolean.FALSE,
                        3,
                        3_000_000L,
                        3_006_000L,
                        0L);

        assertEquals(
                RepairDecision.Action.RESELECT_COMMUNICATION_ROUTE,
                transportRepair.action);
    }

    @Test public void repairSuccessRequiresRealHfpAudioTransport() {
        BluetoothHealth routeSelected =
                BluetoothHealth.assess(
                        true,
                        true,
                        AudioManager.MODE_IN_CALL,
                        true,
                        true,
                        true,
                        false);

        assertFalse(
                HfpAudioTransportPolicy.verificationSucceeded(
                        routeSelected,
                        Boolean.FALSE));
        assertTrue(
                HfpAudioTransportPolicy.verificationSucceeded(
                        routeSelected,
                        Boolean.TRUE));
    }
}
