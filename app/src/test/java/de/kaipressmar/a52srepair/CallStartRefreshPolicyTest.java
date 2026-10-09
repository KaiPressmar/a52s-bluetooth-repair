package de.kaipressmar.a52srepair;

import android.media.AudioManager;
import org.junit.Test;
import static org.junit.Assert.*;

/** Hands-free protection: the driver can only answer via the car, never tap a repair button. */
public class CallStartRefreshPolicyTest {
    private static final long OFFHOOK = 1_000_000L;
    private static final long SETTLED = OFFHOOK + CallStartRefreshPolicy.SETTLE_AFTER_OFFHOOK_MS + 500L;

    private static BluetoothHealth btCall(boolean scoSelected, boolean speaker) {
        return BluetoothHealth.assess(
                true, true, AudioManager.MODE_IN_CALL, true, true, scoSelected, speaker, true);
    }

    private static boolean decide(
            boolean enabled,
            String profile,
            BluetoothHealth health,
            Boolean transport,
            boolean done,
            long now) {
        return CallStartRefreshPolicy.shouldRefresh(
                enabled,
                profile,
                health,
                FailureSignature.classify(health, true),
                transport,
                done,
                OFFHOOK,
                now);
    }

    @Test public void healthyLookingCarCallIsRefreshedOnceAfterSettling() {
        assertTrue(decide(true, "a52s", btCall(true, false), Boolean.TRUE, false, SETTLED));
        assertTrue(decide(true, "a52s", btCall(true, false), null, false, SETTLED));
        assertFalse(decide(true, "a52s", btCall(true, false), Boolean.TRUE, true, SETTLED));
    }

    @Test public void waitsForTelecomSetupAndSkipsCallsAlreadyUnderWay() {
        assertFalse(decide(true, "a52s", btCall(true, false), Boolean.TRUE, false, OFFHOOK + 500L));
        assertFalse(
                decide(
                        true,
                        "a52s",
                        btCall(true, false),
                        Boolean.TRUE,
                        false,
                        OFFHOOK + CallStartRefreshPolicy.LATEST_AFTER_OFFHOOK_MS + 1L));
    }

    @Test public void neverOnSpeakerphoneOrWithoutSelectedSco() {
        assertFalse(decide(true, "a52s", btCall(true, true), Boolean.TRUE, false, SETTLED));
        // Drift (SCO available but not selected) belongs to the confirmed repair path.
        assertFalse(decide(true, "a52s", btCall(false, false), Boolean.TRUE, false, SETTLED));
        // Transport down is a detectable fault with its own repair path.
        assertFalse(decide(true, "a52s", btCall(true, false), Boolean.FALSE, false, SETTLED));
    }

    @Test public void onlyOnA52sAndOnlyWhenEnabled() {
        assertFalse(decide(false, "a52s", btCall(true, false), Boolean.TRUE, false, SETTLED));
        assertFalse(decide(true, "s22", btCall(true, false), Boolean.TRUE, false, SETTLED));
    }

    @Test public void noRefreshWithoutKnownOffhookTime() {
        BluetoothHealth health = btCall(true, false);
        assertFalse(
                CallStartRefreshPolicy.shouldRefresh(
                        true,
                        "a52s",
                        health,
                        FailureSignature.classify(health, true),
                        Boolean.TRUE,
                        false,
                        0L,
                        SETTLED));
    }

    @Test public void detectedDownlinkFaultUsesRepairPathNotRefresh() {
        BluetoothHealth health = btCall(true, false);
        FailureSignature mismatch =
                FailureSignature.classify(
                        health, true, new CallAudioSignals(false, 5, 1, 7, false, false));
        assertFalse(
                CallStartRefreshPolicy.shouldRefresh(
                        true, "a52s", health, mismatch, Boolean.TRUE, false, OFFHOOK, SETTLED));
    }
}
