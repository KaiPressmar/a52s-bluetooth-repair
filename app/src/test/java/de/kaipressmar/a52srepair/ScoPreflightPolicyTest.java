package de.kaipressmar.a52srepair;

import android.media.AudioManager;
import org.junit.Test;
import static org.junit.Assert.*;

public class ScoPreflightPolicyTest {
    private BluetoothRepair.Probe probe(
            boolean hfp,
            boolean scoAvailable,
            boolean inCall,
            boolean musicActive) {
        BluetoothHealth health =
                BluetoothHealth.assess(
                        true,
                        true,
                        inCall ? AudioManager.MODE_IN_CALL : AudioManager.MODE_NORMAL,
                        hfp,
                        scoAvailable,
                        false,
                        false,
                        inCall);
        return new BluetoothRepair.Probe(
                health,
                null,
                null,
                false,
                musicActive);
    }

    @Test public void a52sAndroid14IdleHfpScoConnectionIsEligible() {
        assertTrue(
                ScoPreflightPolicy.eligible(
                        true,
                        "a52s",
                        34,
                        probe(true, true, false, false),
                        1_000_000L,
                        0L,
                        0));
    }

    @Test public void activeCallNeverRunsPreflight() {
        assertFalse(
                ScoPreflightPolicy.eligible(
                        true,
                        "a52s",
                        34,
                        probe(true, true, true, false),
                        1_000_000L,
                        0L,
                        0));
    }

    @Test public void musicPlaybackNeverRunsPreflight() {
        assertFalse(
                ScoPreflightPolicy.eligible(
                        true,
                        "a52s",
                        34,
                        probe(true, true, false, true),
                        1_000_000L,
                        0L,
                        0));
    }

    @Test public void noHfpOrNoScoIsNotEligible() {
        assertFalse(
                ScoPreflightPolicy.eligible(
                        true,
                        "a52s",
                        34,
                        probe(false, true, false, false),
                        1_000_000L,
                        0L,
                        0));
        assertFalse(
                ScoPreflightPolicy.eligible(
                        true,
                        "a52s",
                        34,
                        probe(true, false, false, false),
                        1_000_000L,
                        0L,
                        0));
    }

    @Test public void s22AndNonAndroid14BuildsDoNotUseLegacyPreflight() {
        BluetoothRepair.Probe ready = probe(true, true, false, false);
        assertFalse(
                ScoPreflightPolicy.eligible(
                        true, "s22", 36, ready, 1_000_000L, 0L, 0));
        assertFalse(
                ScoPreflightPolicy.eligible(
                        true, "a52s", 35, ready, 1_000_000L, 0L, 0));
    }

    @Test public void intervalAndAttemptLimitPreventRepeatedPulses() {
        BluetoothRepair.Probe ready = probe(true, true, false, false);
        long now = 1_000_000L;
        assertFalse(
                ScoPreflightPolicy.eligible(
                        true,
                        "a52s",
                        34,
                        ready,
                        now,
                        now - ScoPreflightPolicy.MIN_INTERVAL_MS + 1L,
                        0));
        assertFalse(
                ScoPreflightPolicy.eligible(
                        true,
                        "a52s",
                        34,
                        ready,
                        now,
                        0L,
                        ScoPreflightPolicy.MAX_ATTEMPTS_PER_CONNECTION));
    }
}
