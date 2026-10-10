package de.kaipressmar.a52srepair.core;

import static org.junit.Assert.assertEquals;

import de.kaipressmar.a52srepair.core.diagnosis.Fault;
import de.kaipressmar.a52srepair.core.diagnosis.FaultClassifier;
import de.kaipressmar.a52srepair.core.model.AudioRoute;
import de.kaipressmar.a52srepair.core.model.CallAudioSnapshot;
import de.kaipressmar.a52srepair.core.model.CallPhase;
import org.junit.Test;

public class FaultClassifierTest {
    static CallAudioSnapshot.Builder carCall() {
        return CallAudioSnapshot.builder()
                .phase(CallPhase.ACTIVE)
                .route(AudioRoute.BLUETOOTH)
                .bluetoothRouteAvailable(true)
                .hfpConnected(true)
                .scoAudioConnected(true)
                .voiceOnBluetooth(true)
                .voiceVolume(5, 7);
    }

    @Test public void healthyCarCall() {
        assertEquals(Fault.NONE, FaultClassifier.classify(carCall().build()));
    }

    @Test public void IntentionalMicrophoneMuteIsNotAPlaybackFault() {
        assertEquals(Fault.NONE, FaultClassifier.classify(carCall().microphoneMuted(true).build()));
        org.junit.Assert.assertTrue(carCall().microphoneMuted(true).build().toBuilder().build().microphoneMuted);
    }

    @Test public void ringingAndHoldingNeverCarryFaults() {
        assertEquals(Fault.NONE, FaultClassifier.classify(
                carCall().phase(CallPhase.RINGING).route(AudioRoute.EARPIECE).build()));
        assertEquals(Fault.NONE, FaultClassifier.classify(
                carCall().phase(CallPhase.HOLDING).scoAudioConnected(false).build()));
    }

    @Test public void callOnPhoneWhileCarAvailable() {
        assertEquals(Fault.CALL_NOT_ON_BLUETOOTH, FaultClassifier.classify(
                carCall().route(AudioRoute.EARPIECE).build()));
    }

    @Test public void speakerAndWiredAreDeliberate() {
        assertEquals(Fault.NONE, FaultClassifier.classify(carCall().route(AudioRoute.SPEAKER).build()));
        assertEquals(Fault.NONE, FaultClassifier.classify(carCall().route(AudioRoute.WIRED_HEADSET).build()));
    }

    @Test public void scoLinkDown() {
        assertEquals(Fault.SCO_DISCONNECTED, FaultClassifier.classify(
                carCall().scoAudioConnected(false).build()));
    }

    @Test public void oneWayAudioDownlinkElsewhere() {
        assertEquals(Fault.DOWNLINK_NOT_ON_BLUETOOTH, FaultClassifier.classify(
                carCall().voiceOnBluetooth(false).build()));
    }

    @Test public void silencedVoiceStream() {
        assertEquals(Fault.VOICE_SILENCED, FaultClassifier.classify(carCall().voiceVolume(0, 7).build()));
        assertEquals(Fault.VOICE_SILENCED, FaultClassifier.classify(carCall().voiceMuted(true).build()));
    }

    @Test public void unknownSignalsNeverCreateFaults() {
        assertEquals(Fault.NONE, FaultClassifier.classify(
                carCall().scoAudioConnected(null).voiceOnBluetooth(null).voiceVolume(-1, -1).build()));
    }

    @Test public void hfpWithoutBluetoothRouteIsTheRebootCase() {
        assertEquals(Fault.BLUETOOTH_ROUTE_MISSING, FaultClassifier.classify(
                carCall().bluetoothRouteAvailable(false).route(AudioRoute.EARPIECE).build()));
    }

    @Test public void noCarConnectedIsNoFault() {
        assertEquals(Fault.NONE, FaultClassifier.classify(
                carCall().bluetoothRouteAvailable(false).hfpConnected(false).route(AudioRoute.EARPIECE).build()));
    }
}
