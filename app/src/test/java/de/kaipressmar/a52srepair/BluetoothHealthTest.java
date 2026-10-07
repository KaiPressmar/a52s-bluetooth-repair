package de.kaipressmar.a52srepair;

import android.media.AudioManager;
import org.junit.Test;
import static org.junit.Assert.*;

public class BluetoothHealthTest {
    @Test public void permissionMissingIsActionable() {
        BluetoothHealth h = BluetoothHealth.assess(
                false, false, AudioManager.MODE_NORMAL, false, false, false, false);
        assertEquals(BluetoothHealth.State.PERMISSION_REQUIRED, h.state);
        assertFalse(h.needsRepair());
    }

    @Test public void bluetoothOffIsNotRepaired() {
        BluetoothHealth h = BluetoothHealth.assess(
                true, false, AudioManager.MODE_NORMAL, false, false, false, false);
        assertEquals(BluetoothHealth.State.BLUETOOTH_OFF, h.state);
        assertFalse(h.needsRepair());
    }

    @Test public void idleScoAvailabilityIsNotMisclassifiedAsFailure() {
        BluetoothHealth h = BluetoothHealth.assess(
                true, true, AudioManager.MODE_NORMAL, true, true, false, false);
        assertEquals(BluetoothHealth.State.IDLE, h.state);
        assertFalse(h.needsRepair());
    }

    @Test public void idleHfpWithoutScoIsStillNotFailure() {
        BluetoothHealth h = BluetoothHealth.assess(
                true, true, AudioManager.MODE_NORMAL, true, false, false, false);
        assertEquals(BluetoothHealth.State.IDLE, h.state);
        assertTrue(h.hfpProfileConnected);
        assertFalse(h.needsRepair());
    }

    @Test public void cellularCallHintDetectsCallEvenWhenAudioModeIsStillNormal() {
        BluetoothHealth h =
                BluetoothHealth.assess(
                        true,
                        true,
                        AudioManager.MODE_NORMAL,
                        true,
                        true,
                        false,
                        false,
                        true);

        assertTrue(h.inCommunication);
        assertEquals(BluetoothHealth.State.SUSPECT_ROUTING, h.state);
        assertTrue(h.needsRepair());
    }

    @Test public void cellularCallHintWithMissingScoIdentifiesVendorFailure() {
        BluetoothHealth h =
                BluetoothHealth.assess(
                        true,
                        true,
                        AudioManager.MODE_NORMAL,
                        true,
                        false,
                        false,
                        false,
                        true);

        assertTrue(h.inCommunication);
        assertEquals(BluetoothHealth.State.HFP_CONNECTED_NO_SCO, h.state);
    }

    @Test public void activeCallWithScoSelectedIsHealthy() {
        BluetoothHealth h = BluetoothHealth.assess(
                true, true, AudioManager.MODE_IN_CALL, true, true, true, false);
        assertEquals(BluetoothHealth.State.HEALTHY, h.state);
        assertFalse(h.needsRepair());
    }

    @Test public void activeCallWithAvailableButUnselectedScoIsRepairCandidate() {
        BluetoothHealth h = BluetoothHealth.assess(
                true, true, AudioManager.MODE_IN_CALL, true, true, false, false);
        assertEquals(BluetoothHealth.State.SUSPECT_ROUTING, h.state);
        assertTrue(h.needsRepair());
    }

    @Test public void activeCallWithHfpProfileButNoScoIdentifiesVendorStackFailure() {
        BluetoothHealth h = BluetoothHealth.assess(
                true, true, AudioManager.MODE_IN_CALL, true, false, false, false);
        assertEquals(BluetoothHealth.State.HFP_CONNECTED_NO_SCO, h.state);
        assertTrue(h.hfpProfileConnected);
        assertFalse(h.needsRepair());
    }

    @Test public void activeCommunicationWithoutBluetoothProfileIsDiagnosedButNotRepaired() {
        BluetoothHealth h = BluetoothHealth.assess(
                true, true, AudioManager.MODE_IN_COMMUNICATION, false, false, false, false);
        assertEquals(BluetoothHealth.State.CALL_WITHOUT_SCO, h.state);
        assertFalse(h.needsRepair());
    }
}
