package de.kaipressmar.a52srepair;

import android.media.AudioManager;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Lifecycle regression scenarios matching the real car use case:
 * Bluetooth turns on -> HFP connects while idle -> a call starts -> route is evaluated/repaired.
 */
public class CarConnectionLifecycleTest {
    @Test public void hfpCarConnectionWhileIdleIsObservedButNeverForceRouted() {
        BluetoothHealth idle =
                BluetoothHealth.assess(
                        true,
                        true,
                        AudioManager.MODE_NORMAL,
                        true,
                        true,
                        false,
                        false);

        FailureSignature signature = FailureSignature.classify(idle, true);
        RepairDecision decision =
                RepairDecision.decide(signature, idle, 0, 0, 1_000_000L, 0L);

        assertEquals(BluetoothHealth.State.IDLE, idle.state);
        assertEquals(FailureSignature.Kind.IDLE, signature.kind);
        assertEquals(RepairDecision.Action.NONE, decision.action);
        assertEquals(
                WatchdogSchedule.HFP_CONNECTED_IDLE_RECHECK_MS,
                WatchdogSchedule.nextDelayMillis(idle, 0, 0));
    }

    @Test public void callStartWithAvailableScoButPhoneRouteTriggersConfirmedRepair() {
        BluetoothHealth routeDrift =
                BluetoothHealth.assess(
                        true,
                        true,
                        AudioManager.MODE_IN_CALL,
                        true,
                        true,
                        false,
                        false);
        FailureSignature signature = FailureSignature.classify(routeDrift, true);

        RepairDecision first =
                RepairDecision.decide(
                        signature,
                        routeDrift,
                        1,
                        0,
                        2_000_000L,
                        0L);
        RepairDecision confirmed =
                RepairDecision.decide(
                        signature,
                        routeDrift,
                        2,
                        0,
                        2_004_000L,
                        0L);

        assertEquals(FailureSignature.Kind.RECOVERABLE_ROUTE_DRIFT, signature.kind);
        assertEquals(RepairDecision.Action.WAIT_FOR_CONFIRMATION, first.action);
        assertEquals(RepairDecision.Action.RESELECT_COMMUNICATION_ROUTE, confirmed.action);
        assertTrue(RepairRetryPolicy.canAttempt(true, confirmed, 0));
    }

    @Test public void telephonyOffhookStillTriggersRepairWhenSamsungAudioModeStaysNormal() {
        BluetoothHealth routeDrift =
                BluetoothHealth.assess(
                        true,
                        true,
                        AudioManager.MODE_NORMAL,
                        true,
                        true,
                        false,
                        false,
                        true);
        FailureSignature signature = FailureSignature.classify(routeDrift, true);
        RepairDecision confirmed =
                RepairDecision.decide(
                        signature,
                        routeDrift,
                        2,
                        0,
                        2_100_000L,
                        0L);

        assertTrue(routeDrift.inCommunication);
        assertEquals(BluetoothHealth.State.SUSPECT_ROUTING, routeDrift.state);
        assertEquals(
                RepairDecision.Action.RESELECT_COMMUNICATION_ROUTE,
                confirmed.action);
    }

    @Test public void selectedRouteButDisconnectedScoTransportIsStillRepairable() {
        BluetoothHealth routeLooksSelected =
                BluetoothHealth.assess(
                        true,
                        true,
                        AudioManager.MODE_IN_CALL,
                        true,
                        true,
                        true,
                        false);

        assertEquals(BluetoothHealth.State.HEALTHY, routeLooksSelected.state);
        assertTrue(
                HfpAudioTransportPolicy.isMismatch(
                        routeLooksSelected,
                        Boolean.FALSE));

        RepairDecision confirmed =
                HfpAudioTransportPolicy.decide(
                        routeLooksSelected,
                        Boolean.FALSE,
                        2,
                        2_200_000L,
                        0L);

        assertEquals(
                RepairDecision.Action.RESELECT_COMMUNICATION_ROUTE,
                confirmed.action);
    }

    @Test public void successfulBluetoothCallRouteEndsIncidentWithoutMoreRepair() {
        BluetoothHealth healthy =
                BluetoothHealth.assess(
                        true,
                        true,
                        AudioManager.MODE_IN_CALL,
                        true,
                        true,
                        true,
                        false);
        FailureSignature signature = FailureSignature.classify(healthy, true);
        RepairDecision decision =
                RepairDecision.decide(
                        signature,
                        healthy,
                        0,
                        0,
                        3_000_000L,
                        0L);

        assertEquals(BluetoothHealth.State.HEALTHY, healthy.state);
        assertEquals(FailureSignature.Kind.HEALTHY_CALL, signature.kind);
        assertEquals(RepairDecision.Action.NONE, decision.action);
        assertFalse(RepairRetryPolicy.shouldRetryAfterVerification(healthy, true, 1));
    }

    @Test public void mediaAliveButMissingScoDuringCallIsDeepVendorFailureNotBlindlyForced() {
        BluetoothHealth broken =
                BluetoothHealth.assess(
                        true,
                        true,
                        AudioManager.MODE_IN_CALL,
                        true,
                        false,
                        false,
                        false);
        FailureSignature signature = FailureSignature.classify(broken, true);

        RepairDecision first =
                RepairDecision.decide(
                        signature,
                        broken,
                        0,
                        1,
                        4_000_000L,
                        0L);
        RepairDecision confirmed =
                RepairDecision.decide(
                        signature,
                        broken,
                        0,
                        2,
                        4_008_000L,
                        0L);

        assertEquals(
                FailureSignature.Kind.HFP_CONNECTED_NO_SCO_MEDIA_ALIVE,
                signature.kind);
        assertEquals(RepairDecision.Action.WAIT_FOR_CONFIRMATION, first.action);
        assertEquals(RepairDecision.Action.ESCALATE_VENDOR_STACK, confirmed.action);
        assertFalse(signature.publicApiRepairPossible);
    }

    @Test public void bluetoothOffFullyPausesPeriodicChecksAndRepairPolicy() {
        BluetoothHealth off =
                BluetoothHealth.assess(
                        true,
                        false,
                        AudioManager.MODE_NORMAL,
                        false,
                        false,
                        false,
                        false);
        FailureSignature signature = FailureSignature.classify(off, false);
        RepairDecision decision =
                RepairDecision.decide(
                        signature,
                        off,
                        99,
                        99,
                        5_000_000L,
                        0L);

        assertEquals(BluetoothHealth.State.BLUETOOTH_OFF, off.state);
        assertEquals(WatchdogSchedule.NO_RECHECK_MS, WatchdogSchedule.nextDelayMillis(off, 0, 0));
        assertEquals(RepairDecision.Action.NONE, decision.action);
    }
}
