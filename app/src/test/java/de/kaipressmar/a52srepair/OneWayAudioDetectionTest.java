package de.kaipressmar.a52srepair;

import android.media.AudioManager;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * One-way call audio on the A52s (SM-A528B, Android 14, A528BXXSBGYI3) with a VW head unit:
 * the caller hears us, we hear nothing. SCO is selected, so the route-only checks of v0.14 rated
 * the call as healthy. These tests pin the downlink-aware classification and repair choice.
 */
public class OneWayAudioDetectionTest {
    private static final long NOW = 5_000_000L;

    private static BluetoothHealth scoSelectedCall() {
        // Matches the field report: audioMode may still be MODE_NORMAL while telephony is off-hook.
        return BluetoothHealth.assess(
                true, true, AudioManager.MODE_NORMAL, true, true, true, false, true);
    }

    private static CallAudioSignals signals(Boolean voiceOnBt, int volume, boolean muted) {
        return new CallAudioSignals(voiceOnBt, volume, 1, 7, muted, true);
    }

    @Test public void routeOnlyCheckStillRatesOneWayCallHealthyWithoutDownlinkSignals() {
        assertEquals(
                FailureSignature.Kind.HEALTHY_CALL,
                FailureSignature.classify(scoSelectedCall(), true).kind);
    }

    @Test public void voicePlaybackOffBluetoothIsDownlinkRouteMismatch() {
        FailureSignature signature =
                FailureSignature.classify(scoSelectedCall(), true, signals(false, 5, false));

        assertEquals(FailureSignature.Kind.DOWNLINK_ROUTE_MISMATCH, signature.kind);
        assertTrue(signature.publicApiRepairPossible);
        assertTrue(signature.matchesKnownSamsungFailure());
    }

    @Test public void mutedOrZeroVoiceStreamIsDownlinkSilenced() {
        assertEquals(
                FailureSignature.Kind.DOWNLINK_SILENCED,
                FailureSignature.classify(scoSelectedCall(), true, signals(true, 0, false)).kind);
        assertEquals(
                FailureSignature.Kind.DOWNLINK_SILENCED,
                FailureSignature.classify(scoSelectedCall(), true, signals(true, 4, true)).kind);
    }

    @Test public void healthyDownlinkAndUnknownSignalsStayHealthy() {
        assertEquals(
                FailureSignature.Kind.HEALTHY_CALL,
                FailureSignature.classify(scoSelectedCall(), true, signals(true, 5, false)).kind);
        assertEquals(
                FailureSignature.Kind.HEALTHY_CALL,
                FailureSignature.classify(scoSelectedCall(), true, CallAudioSignals.UNKNOWN).kind);
    }

    @Test public void speakerphoneIsNeverTreatedAsOneWayAudio() {
        BluetoothHealth speaker =
                BluetoothHealth.assess(
                        true, true, AudioManager.MODE_IN_CALL, true, true, true, true, true);
        assertEquals(
                FailureSignature.Kind.HEALTHY_CALL,
                FailureSignature.classify(speaker, true, signals(false, 0, true)).kind);
    }

    @Test public void downlinkMismatchIsConfirmedThenBounced() {
        BluetoothHealth call = scoSelectedCall();
        FailureSignature signature =
                FailureSignature.classify(call, true, signals(false, 5, false));

        assertEquals(
                RepairDecision.Action.WAIT_FOR_CONFIRMATION,
                RepairDecision.decide(signature, call, 1, 0, NOW, 0L).action);
        RepairDecision confirmed = RepairDecision.decide(signature, call, 2, 0, NOW, 0L);
        assertEquals(RepairDecision.Action.BOUNCE_COMMUNICATION_ROUTE, confirmed.action);
        assertTrue(confirmed.changesRoute());
        assertTrue(RepairRetryPolicy.canAttempt(true, confirmed, 0));
    }

    @Test public void bounceRespectsCooldownButVolumeRestoreDoesNot() {
        BluetoothHealth call = scoSelectedCall();
        long recentRepair = NOW - 10_000L;

        FailureSignature mismatch =
                FailureSignature.classify(call, true, signals(false, 5, false));
        assertEquals(
                RepairDecision.Action.NONE,
                RepairDecision.decide(mismatch, call, 3, 0, NOW, recentRepair).action);

        FailureSignature silenced =
                FailureSignature.classify(call, true, signals(true, 0, false));
        RepairDecision restore = RepairDecision.decide(silenced, call, 2, 0, NOW, recentRepair);
        assertEquals(RepairDecision.Action.RESTORE_CALL_VOLUME, restore.action);
        assertFalse(restore.changesRoute());
        assertTrue(restore.isRepair());
    }

    @Test public void userReportBouncesSelectedScoRoute() {
        RepairDecision decision =
                RepairDecision.forUserReportedOneWayAudio(
                        scoSelectedCall(), signals(true, 5, false));
        assertEquals(RepairDecision.Action.BOUNCE_COMMUNICATION_ROUTE, decision.action);
    }

    @Test public void userReportPrefersVolumeRestoreWhenStreamIsSilenced() {
        assertEquals(
                RepairDecision.Action.RESTORE_CALL_VOLUME,
                RepairDecision.forUserReportedOneWayAudio(
                                scoSelectedCall(), signals(true, 0, false))
                        .action);
    }

    @Test public void userReportReselectsWhenScoAvailableButNotSelected() {
        BluetoothHealth drift =
                BluetoothHealth.assess(
                        true, true, AudioManager.MODE_IN_CALL, true, true, false, false, true);
        assertEquals(
                RepairDecision.Action.RESELECT_COMMUNICATION_ROUTE,
                RepairDecision.forUserReportedOneWayAudio(drift, CallAudioSignals.UNKNOWN)
                        .action);
    }

    @Test public void userReportEscalatesWhenNoScoDeviceExists() {
        BluetoothHealth noSco =
                BluetoothHealth.assess(
                        true, true, AudioManager.MODE_IN_CALL, true, false, false, false, true);
        assertEquals(
                RepairDecision.Action.ESCALATE_VENDOR_STACK,
                RepairDecision.forUserReportedOneWayAudio(noSco, CallAudioSignals.UNKNOWN)
                        .action);
    }

    @Test public void userReportOutsideCallDoesNothing() {
        BluetoothHealth idle =
                BluetoothHealth.assess(
                        true, true, AudioManager.MODE_NORMAL, true, true, false, false, false);
        assertFalse(
                RepairDecision.forUserReportedOneWayAudio(idle, CallAudioSignals.UNKNOWN)
                        .isRepair());
    }

    @Test public void restoredVolumeIsAudibleAndWithinRange() {
        assertEquals(4, signals(true, 0, false).restoredVolumeIndex());
        assertEquals(-1, CallAudioSignals.UNKNOWN.restoredVolumeIndex());
        assertEquals(
                1, new CallAudioSignals(true, 0, 0, 1, false, false).restoredVolumeIndex());
    }

    @Test public void fieldReportIdleStateWithVwHeadUnitNeedsNoAction() {
        // 2026-10-07 21:15:54: VW_BT_1159 connected, hfpProfile=true scoAvailable=true, no call.
        BluetoothHealth idle =
                BluetoothHealth.assess(
                        true, true, AudioManager.MODE_NORMAL, true, true, false, false, false);
        FailureSignature signature = FailureSignature.classify(idle, true);
        assertEquals(FailureSignature.Kind.IDLE, signature.kind);
        assertEquals(
                RepairDecision.Action.NONE,
                RepairDecision.decide(signature, idle, 0, 0, NOW, 0L).action);
    }

    @Test public void downlinkFaultIsConfirmedQuicklyInsteadOfHealthyRecheck() {
        BluetoothHealth call = scoSelectedCall();
        FailureSignature mismatch =
                FailureSignature.classify(call, true, signals(false, 5, false));

        assertEquals(
                WatchdogSchedule.SUSPECT_CONFIRM_MS,
                WatchdogSchedule.nextDelayMillis(call, mismatch, 1, 0));
        assertEquals(
                WatchdogSchedule.DOWNLINK_RECHECK_MS,
                WatchdogSchedule.nextDelayMillis(call, mismatch, 2, 0));
        assertEquals(
                WatchdogSchedule.ACTIVE_HEALTHY_RECHECK_MS,
                WatchdogSchedule.nextDelayMillis(
                        call, FailureSignature.classify(call, true), 0, 0));
    }
}
