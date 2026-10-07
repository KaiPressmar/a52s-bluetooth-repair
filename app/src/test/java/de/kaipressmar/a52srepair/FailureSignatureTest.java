package de.kaipressmar.a52srepair;

import android.media.AudioManager;
import org.junit.Test;
import static org.junit.Assert.*;

public class FailureSignatureTest {
    private BluetoothHealth health(
            int mode,
            boolean hfpConnected,
            boolean scoAvailable,
            boolean scoSelected,
            boolean speaker) {
        return BluetoothHealth.assess(
                true,
                true,
                mode,
                hfpConnected,
                scoAvailable,
                scoSelected,
                speaker);
    }

    @Test public void idleHfpConnectionIsNotMisclassifiedAsFailure() {
        BluetoothHealth idle =
                health(AudioManager.MODE_NORMAL, true, false, false, false);

        FailureSignature signature = FailureSignature.classify(idle, true);

        assertEquals(FailureSignature.Kind.IDLE, signature.kind);
        assertFalse(signature.matchesKnownSamsungFailure());
        assertFalse(signature.publicApiRepairPossible);
    }

    @Test public void healthyScoCallIsRecognizedAsHealthy() {
        BluetoothHealth healthy =
                health(AudioManager.MODE_IN_CALL, true, true, true, false);

        FailureSignature signature = FailureSignature.classify(healthy, true);

        assertEquals(FailureSignature.Kind.HEALTHY_CALL, signature.kind);
        assertEquals(FailureSignature.Confidence.HIGH, signature.confidence);
        assertFalse(signature.matchesKnownSamsungFailure());
    }

    @Test public void routableScoButWrongCommunicationRouteIsRecoverableDrift() {
        BluetoothHealth drift =
                health(AudioManager.MODE_IN_COMMUNICATION, true, true, false, false);

        FailureSignature signature = FailureSignature.classify(drift, true);

        assertEquals(FailureSignature.Kind.RECOVERABLE_ROUTE_DRIFT, signature.kind);
        assertEquals(FailureSignature.Confidence.HIGH, signature.confidence);
        assertTrue(signature.matchesKnownSamsungFailure());
        assertTrue(signature.publicApiRepairPossible);
    }

    @Test public void intentionalSpeakerRouteMustNotBeAutoRepairable() {
        BluetoothHealth speaker =
                health(AudioManager.MODE_IN_CALL, true, true, false, true);

        FailureSignature signature = FailureSignature.classify(speaker, true);

        assertEquals(FailureSignature.Kind.RECOVERABLE_ROUTE_DRIFT, signature.kind);
        assertFalse(signature.publicApiRepairPossible);
    }

    @Test public void hfpConnectedMediaAliveButScoMissingMatchesObservedA52sPattern() {
        BluetoothHealth broken =
                health(AudioManager.MODE_IN_CALL, true, false, false, false);

        FailureSignature signature = FailureSignature.classify(broken, true);

        assertEquals(
                FailureSignature.Kind.HFP_CONNECTED_NO_SCO_MEDIA_ALIVE,
                signature.kind);
        assertEquals(FailureSignature.Confidence.HIGH, signature.confidence);
        assertTrue(signature.matchesKnownSamsungFailure());
        assertFalse(signature.publicApiRepairPossible);
    }

    @Test public void hfpWithoutScoButWithoutMediaEvidenceIsOnlyMediumConfidence() {
        BluetoothHealth broken =
                health(AudioManager.MODE_IN_CALL, true, false, false, false);

        FailureSignature signature = FailureSignature.classify(broken, false);

        assertEquals(FailureSignature.Kind.HFP_CONNECTED_NO_SCO, signature.kind);
        assertEquals(FailureSignature.Confidence.MEDIUM, signature.confidence);
        assertFalse(signature.matchesKnownSamsungFailure());
    }

    @Test public void genericCallWithoutHfpIsNotTreatedAsKnownSamsungBug() {
        BluetoothHealth noBluetoothTelephony =
                health(AudioManager.MODE_IN_CALL, false, false, false, false);

        FailureSignature signature =
                FailureSignature.classify(noBluetoothTelephony, true);

        assertEquals(
                FailureSignature.Kind.CALL_WITHOUT_BLUETOOTH_TELEPHONY,
                signature.kind);
        assertEquals(FailureSignature.Confidence.LOW, signature.confidence);
        assertFalse(signature.matchesKnownSamsungFailure());
    }

    @Test public void bluetoothOffNeverProducesRepairSignature() {
        BluetoothHealth off =
                BluetoothHealth.assess(
                        true,
                        false,
                        AudioManager.MODE_IN_CALL,
                        false,
                        false,
                        false,
                        false);

        FailureSignature signature = FailureSignature.classify(off, false);

        assertEquals(FailureSignature.Kind.SYSTEM_UNAVAILABLE, signature.kind);
        assertEquals(FailureSignature.Confidence.NONE, signature.confidence);
    }
}
