package de.kaipressmar.a52srepair.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import de.kaipressmar.a52srepair.core.diagnosis.Fault;
import de.kaipressmar.a52srepair.core.model.AudioRoute;
import de.kaipressmar.a52srepair.core.model.CallAudioSnapshot;
import de.kaipressmar.a52srepair.core.model.CallPhase;
import de.kaipressmar.a52srepair.core.repair.CallRepairEngine;
import de.kaipressmar.a52srepair.core.repair.PreventiveRebuildMode;
import de.kaipressmar.a52srepair.core.repair.RepairCommand;
import de.kaipressmar.a52srepair.core.repair.ManualRepairStatus;
import de.kaipressmar.a52srepair.core.report.CallOutcome;
import de.kaipressmar.a52srepair.core.report.CallReport;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class CallRepairEngineTest {
    private static final long T0 = 1_000_000L;

    private static CallRepairEngine secondManualRebuild() {
        CallRepairEngine e = new CallRepairEngine(false, PreventiveRebuildMode.OFF, false, T0);
        CallAudioSnapshot phone = droppedToPhone();
        e.requestManualRepair(phone, T0);
        e.onSnapshot(phone, T0 + 250L);
        e.onSnapshot(phone, T0 + 750L);
        e.onSnapshot(phone, T0 + 12_750L);
        assertEquals(List.of(RepairCommand.REFRESH_BLUETOOTH_OBSERVATIONS),
                e.requestManualRepair(phone, T0 + 20_000L).step.commands);
        return e;
    }

    @Test public void extendedManualRebuildRefreshesThenUsesAlternateApiAndOneAudioPolicyExperiment() {
        CallRepairEngine e = secondManualRebuild();
        CallAudioSnapshot phone = droppedToPhone();
        assertTrue(e.onSnapshot(phone, T0 + 21_499L).commands.isEmpty());
        assertEquals(List.of(RepairCommand.ROUTE_TO_EARPIECE_LEGACY),
                e.onSnapshot(phone, T0 + 21_500L).commands);
        e.onSnapshot(phone, T0 + 21_750L);
        assertTrue(e.onSnapshot(phone, T0 + 23_749L).commands.isEmpty());
        assertEquals(List.of(RepairCommand.ROUTE_TO_BLUETOOTH_LEGACY, RepairCommand.TRY_COMMUNICATION_DEVICE),
                e.onSnapshot(phone, T0 + 23_750L).commands);
        assertEquals(List.of(RepairCommand.ROUTE_TO_BLUETOOTH_LEGACY),
                e.onSnapshot(phone, T0 + 29_750L).commands);
        e.onSnapshot(phone, T0 + 35_750L);
        assertEquals(ManualRepairStatus.BUDGET_EXHAUSTED, e.requestManualRepair(phone, T0 + 40_000L).status);
        assertEquals(CallOutcome.UNRESOLVED, e.report(T0 + 40_000L).outcome);
    }

    @Test public void extendedRebuildAllowsSlowTeardownButNeverReturnsOverConnectedSco() {
        CallRepairEngine e = secondManualRebuild();
        CallAudioSnapshot stillConnected = droppedToPhone().toBuilder().scoAudioConnected(true).build();
        e.onSnapshot(stillConnected, T0 + 21_500L);
        assertTrue(e.onSnapshot(stillConnected, T0 + 26_000L).commands.isEmpty());
        assertTrue(e.onSnapshot(stillConnected, T0 + 29_500L).commands.isEmpty());
        assertEquals(CallOutcome.UNRESOLVED, e.report(T0 + 30_000L).outcome);
    }

    @Test public void speakerChoiceWhileRefreshingCancelsAllPendingExtendedCommands() {
        CallRepairEngine e = secondManualRebuild();
        CallAudioSnapshot speaker = droppedToPhone().toBuilder().route(AudioRoute.SPEAKER).build();
        assertTrue(e.onSnapshot(speaker, T0 + 21_000L).commands.isEmpty());
        assertTrue(e.onSnapshot(speaker, T0 + 22_000L).commands.isEmpty());
        assertFalse(e.routingOperationActive());
    }

    @Test public void missingEndpointObservationRefreshRemainsReadOnlyWhenProtectionPaused() {
        CallRepairEngine e = new CallRepairEngine(false, PreventiveRebuildMode.OFF, false, T0);
        CallAudioSnapshot missing = droppedToPhone().toBuilder().bluetoothRouteAvailable(false).build();
        e.onSnapshot(missing, T0);
        assertTrue(e.onSnapshot(missing, T0 + 10_000L).commands.isEmpty());
        assertEquals(0, e.report(T0 + 10_000L).routeAttempts);
    }

    @Test public void reportFallbackWaitsBeyondTelecomPendingWindowBeforeRebuilding() {
        CallRepairEngine e = new CallRepairEngine(true, PreventiveRebuildMode.OFF, false, T0);
        CallAudioSnapshot phone = droppedToPhone();
        e.onSnapshot(phone, T0);
        assertEquals(List.of(RepairCommand.ROUTE_TO_BLUETOOTH), e.onSnapshot(phone, T0 + 3_000L).commands);
        CallAudioSnapshot pulse = phone.toBuilder().route(AudioRoute.BLUETOOTH).build();
        e.onSnapshot(pulse, T0 + 3_100L);
        e.onSnapshot(phone, T0 + 3_400L);
        assertTrue(e.onSnapshot(phone, T0 + 7_000L).commands.isEmpty());
        assertEquals(1, e.report(T0 + 7_000L).routeAttempts);
        assertEquals(List.of(RepairCommand.ROUTE_TO_EARPIECE), e.onSnapshot(phone, T0 + 9_000L).commands);
        assertFalse(e.report(T0 + 9_000L).userLeftBluetooth);
        assertEquals(CallOutcome.UNRESOLVED, e.report(T0 + 9_000L).outcome);
    }

    @Test public void failedRebuildWaitsBeforeRepeatedRequestAndBacksOffBeforeNextAttempt() {
        CallRepairEngine e = new CallRepairEngine(true, PreventiveRebuildMode.OFF, false, T0);
        CallAudioSnapshot broken = FaultClassifierTest.carCall().scoAudioConnected(false).voiceOnBluetooth(false).build();
        CallAudioSnapshot phone = droppedToPhone();
        e.onSnapshot(broken, T0);
        e.onSnapshot(broken, T0 + 2_000L);
        e.onSnapshot(phone, T0 + 2_250L);
        assertEquals(List.of(RepairCommand.ROUTE_TO_BLUETOOTH), e.onSnapshot(phone, T0 + 2_750L).commands);
        assertTrue(e.onSnapshot(phone, T0 + 5_750L).commands.isEmpty());
        assertEquals(List.of(RepairCommand.ROUTE_TO_BLUETOOTH), e.onSnapshot(phone, T0 + 8_750L).commands);
        assertTrue(e.onSnapshot(phone, T0 + 14_750L).commands.isEmpty());
        assertTrue(e.onSnapshot(phone, T0 + 19_749L).commands.isEmpty());
        assertEquals(1, e.report(T0 + 19_749L).routeAttempts);
        assertFalse(e.onSnapshot(phone, T0 + 19_750L).commands.isEmpty());
    }

    @Test public void pausingLiveProtectionCancelsAutomaticReturnWithoutResettingBudget() {
        CallRepairEngine e = new CallRepairEngine(true, PreventiveRebuildMode.OFF, false, T0);
        CallAudioSnapshot broken = FaultClassifierTest.carCall().scoAudioConnected(false).build();
        e.onSnapshot(broken, T0);
        assertEquals(List.of(RepairCommand.ROUTE_TO_EARPIECE), e.onSnapshot(broken, T0 + 2_000L).commands);
        e.updatePolicy(false, PreventiveRebuildMode.OFF);
        assertTrue(e.onSnapshot(droppedToPhone(), T0 + 2_250L).commands.isEmpty());
        assertTrue(e.onSnapshot(droppedToPhone(), T0 + 4_000L).commands.isEmpty());
        assertEquals(1, e.report(T0 + 4_000L).routeAttempts);
        e.updatePolicy(true, PreventiveRebuildMode.OFF);
        assertFalse(e.onSnapshot(droppedToPhone(), T0 + 8_000L).commands.isEmpty());
        assertEquals(2, e.report(T0 + 8_000L).routeAttempts);
    }

    @Test public void pausedPolicyRetainsAnExplicitManualRebuild() {
        CallRepairEngine e = new CallRepairEngine(true, PreventiveRebuildMode.ALWAYS, false, T0);
        e.requestManualRepair(FaultClassifierTest.carCall().build(), T0);
        e.updatePolicy(false, PreventiveRebuildMode.OFF);
        e.onSnapshot(droppedToPhone(), T0 + 250L);
        assertEquals(List.of(RepairCommand.ROUTE_TO_BLUETOOTH), e.onSnapshot(droppedToPhone(), T0 + 750L).commands);
        assertEquals(1, e.report(T0 + 750L).manualRepairs);
        assertEquals(0, e.report(T0 + 750L).routeAttempts);
    }

    @Test public void preventiveSettingChangeIsAppliedBeforeAnotherPreventiveAttempt() {
        CallRepairEngine e = new CallRepairEngine(true, PreventiveRebuildMode.ALWAYS, false, T0);
        CallAudioSnapshot ok = FaultClassifierTest.carCall().build();
        e.onSnapshot(ok, T0);
        e.updatePolicy(true, PreventiveRebuildMode.OFF);
        assertTrue(e.onSnapshot(ok, T0 + 2_000L).commands.isEmpty());
        assertFalse(e.report(T0 + 2_000L).preventiveRebuild);
    }

    @Test public void phoneRouteCannotTriggerReturnWhileScoStillConnected() {
        CallRepairEngine e = new CallRepairEngine(false, PreventiveRebuildMode.OFF, false, T0);
        CallAudioSnapshot ok = FaultClassifierTest.carCall().build();
        e.requestManualRepair(ok, T0);
        CallAudioSnapshot phoneWithSco = ok.toBuilder().route(AudioRoute.EARPIECE).build();
        assertTrue(e.onSnapshot(phoneWithSco, T0 + 250L).commands.isEmpty());
        assertTrue(e.onSnapshot(phoneWithSco, T0 + 2_000L).commands.isEmpty());
        CallAudioSnapshot off = phoneWithSco.toBuilder().scoAudioConnected(false).build();
        assertTrue(e.onSnapshot(off, T0 + 2_100L).commands.isEmpty());
        assertEquals(List.of(RepairCommand.ROUTE_TO_BLUETOOTH),
                e.onSnapshot(off, T0 + 2_600L).commands);
    }

    @Test public void teardownNeedsStableDisconnectAndResetsWhenScoReappears() {
        CallRepairEngine e = new CallRepairEngine(false, PreventiveRebuildMode.OFF, false, T0);
        CallAudioSnapshot ok = FaultClassifierTest.carCall().build();
        CallAudioSnapshot off = droppedToPhone();
        e.requestManualRepair(ok, T0);
        assertTrue(e.onSnapshot(off, T0 + 100L).commands.isEmpty());
        assertTrue(e.onSnapshot(off.toBuilder().scoAudioConnected(true).build(), T0 + 400L).commands.isEmpty());
        assertTrue(e.onSnapshot(off, T0 + 500L).commands.isEmpty());
        assertTrue(e.onSnapshot(off, T0 + 999L).commands.isEmpty());
        assertEquals(List.of(RepairCommand.ROUTE_TO_BLUETOOTH), e.onSnapshot(off, T0 + 1_000L).commands);
    }

    @Test public void unknownScoUsesLongerStablePhoneDwellAndRouteChangesResetIt() {
        CallRepairEngine e = new CallRepairEngine(false, PreventiveRebuildMode.OFF, false, T0);
        CallAudioSnapshot ok = FaultClassifierTest.carCall().build();
        CallAudioSnapshot unknown = droppedToPhone().toBuilder().scoAudioConnected(null).build();
        e.requestManualRepair(ok, T0);
        assertTrue(e.onSnapshot(unknown, T0 + 100L).commands.isEmpty());
        assertTrue(e.onSnapshot(unknown, T0 + 1_000L).commands.isEmpty());
        assertTrue(e.onSnapshot(ok, T0 + 1_100L).commands.isEmpty());
        assertTrue(e.onSnapshot(unknown, T0 + 1_200L).commands.isEmpty());
        assertTrue(e.onSnapshot(unknown, T0 + 2_699L).commands.isEmpty());
        assertEquals(List.of(RepairCommand.ROUTE_TO_BLUETOOTH), e.onSnapshot(unknown, T0 + 2_700L).commands);
    }

    @Test public void persistentScoDuringPhoneHopAbortsAndCannotProveManualRecovery() {
        CallRepairEngine e = new CallRepairEngine(false, PreventiveRebuildMode.OFF, false, T0);
        CallAudioSnapshot ok = FaultClassifierTest.carCall().build();
        e.requestManualRepair(ok, T0);
        CallAudioSnapshot phoneWithSco = ok.toBuilder().route(AudioRoute.EARPIECE).build();
        assertTrue(e.onSnapshot(phoneWithSco, T0 + 100L).commands.isEmpty());
        assertTrue(e.onSnapshot(phoneWithSco, T0 + CallRepairEngine.LEAVE_TIMEOUT_MS).commands.isEmpty());
        assertTrue(e.onSnapshot(ok, T0 + 10_000L).commands.isEmpty());
        assertEquals(CallOutcome.UNRESOLVED, e.report(T0 + 20_000L).outcome);
    }

    @Test public void automaticRebuildCannotReconnectOverPersistentOldScoLink() {
        Harness h = new Harness(PreventiveRebuildMode.OFF,
                FaultClassifierTest.carCall().voiceOnBluetooth(false).build()) {
            @Override void react(RepairCommand c) {
                if (c == RepairCommand.ROUTE_TO_EARPIECE || c == RepairCommand.ROUTE_TO_EARPIECE_LEGACY) {
                    state = state.toBuilder().route(AudioRoute.EARPIECE).build();
                }
            }
        };
        h.runFor(60_000L);
        assertEquals(CallRepairEngine.MAX_ROUTE_ATTEMPTS, h.executed.stream()
                .filter(c -> c == RepairCommand.ROUTE_TO_EARPIECE || c == RepairCommand.ROUTE_TO_EARPIECE_LEGACY).count());
        assertFalse(h.executed.contains(RepairCommand.ROUTE_TO_BLUETOOTH));
        assertFalse(h.executed.contains(RepairCommand.ROUTE_TO_BLUETOOTH_LEGACY));
        assertEquals(CallOutcome.UNRESOLVED, h.end().outcome);
    }

    @Test public void repeatedBriefHealthyPulsesCannotPostponeFaultConfirmationForever() {
        Harness h = new Harness(PreventiveRebuildMode.OFF,
                FaultClassifierTest.carCall().scoAudioConnected(false).build()) {
            @Override void react(RepairCommand c) {}
        };
        h.tick();
        h.now += 1_500L;
        h.state = FaultClassifierTest.carCall().build();
        h.tick();
        h.now += 100L;
        h.state = FaultClassifierTest.carCall().scoAudioConnected(false).build();
        h.tick();
        h.now += 600L;
        h.tick();
        assertEquals(List.of(RepairCommand.ROUTE_TO_EARPIECE), h.executed);
    }

    @Test public void healthyGapResetsConnectionConfirmationAfterStabilityWindow() {
        CallRepairEngine e = new CallRepairEngine(true, PreventiveRebuildMode.OFF, false, T0);
        CallAudioSnapshot broken = FaultClassifierTest.carCall().scoAudioConnected(false).build();
        CallAudioSnapshot ok = FaultClassifierTest.carCall().build();
        e.onSnapshot(broken, T0);
        e.onSnapshot(ok, T0 + 500L);
        e.onSnapshot(ok, T0 + 3_000L);
        assertTrue(e.onSnapshot(broken, T0 + 3_100L).commands.isEmpty());
        assertEquals(List.of(RepairCommand.ROUTE_TO_EARPIECE), e.onSnapshot(broken, T0 + 5_100L).commands);
    }

    @Test public void manualRepairDoesNotResetExhaustedAutomaticBudget() {
        Harness h = new Harness(PreventiveRebuildMode.OFF, droppedToPhone()) {
            @Override void react(RepairCommand c) {}
        };
        h.runFor(90_000L);
        assertEquals(CallRepairEngine.MAX_ROUTE_ATTEMPTS, h.engine.report(h.now).routeAttempts);
        assertEquals(ManualRepairStatus.STARTED, h.engine.requestManualRepair(h.state, h.now).status);
        assertEquals(CallRepairEngine.MAX_ROUTE_ATTEMPTS, h.engine.report(h.now).routeAttempts);
        assertEquals(1, h.engine.report(h.now).manualRepairs);
    }

    @Test public void manualRepairWorksWithProtectionOffAndHealthyApiSignals() {
        CallRepairEngine e = new CallRepairEngine(false, PreventiveRebuildMode.OFF, false, T0);
        CallAudioSnapshot ok = FaultClassifierTest.carCall().build();
        e.onSnapshot(ok, T0);
        CallRepairEngine.ManualRepair request = e.requestManualRepair(ok, T0 + 1L);
        assertEquals(ManualRepairStatus.STARTED, request.status);
        assertEquals(List.of(RepairCommand.ROUTE_TO_EARPIECE), request.step.commands);
        assertEquals(CallOutcome.UNRESOLVED, e.report(T0 + 2L).outcome);
        assertEquals(1, e.report(T0 + 2L).manualRepairs);
        assertEquals(0, e.report(T0 + 2L).routeAttempts);
        assertFalse(e.report(T0 + 2L).preventiveRebuild);
        e.onSnapshot(droppedToPhone(), T0 + 250L);
        e.onSnapshot(droppedToPhone(), T0 + 750L);
        e.onSnapshot(ok, T0 + 1_000L);
        e.onSnapshot(ok, T0 + 1_000L + CallRepairEngine.VERIFY_MS);
        assertEquals(CallOutcome.REPAIRED, e.report(T0 + 5_000L).outcome);
        e.onRouteRequestFailed(T0 + 6_000L);
        assertEquals(CallOutcome.UNRESOLVED, e.report(T0 + 6_000L).outcome);
    }

    @Test public void ignoredManualTeardownCannotProveRecoveryOrSendBlindReturn() {
        CallRepairEngine e = new CallRepairEngine(false, PreventiveRebuildMode.OFF, false, T0);
        CallAudioSnapshot ok = FaultClassifierTest.carCall().build();
        e.requestManualRepair(ok, T0);
        assertTrue(e.onSnapshot(ok, T0 + CallRepairEngine.LEAVE_TIMEOUT_MS).commands.isEmpty());
        assertTrue(e.onSnapshot(ok, T0 + 20_000L).commands.isEmpty());
        assertEquals(CallOutcome.UNRESOLVED, e.report(T0 + 20_000L).outcome);
    }

    @Test public void manualRepairHasItsOwnBoundedBudgetAndCooldown() {
        CallRepairEngine e = new CallRepairEngine(false, PreventiveRebuildMode.OFF, false, T0);
        CallAudioSnapshot ok = FaultClassifierTest.carCall().build();
        assertEquals(ManualRepairStatus.STARTED, e.requestManualRepair(ok, T0).status);
        assertEquals(ManualRepairStatus.BUSY, e.requestManualRepair(ok, T0 + 1L).status);
        e.onRouteRequestFailed(T0 + 2L);
        assertEquals(ManualRepairStatus.COOLDOWN, e.requestManualRepair(ok, T0 + 3L).status);
        assertEquals(ManualRepairStatus.STARTED,
                e.requestManualRepair(ok, T0 + CallRepairEngine.MANUAL_COOLDOWN_MS).status);
        e.onRouteRequestFailed(T0 + 20_000L);
        assertEquals(ManualRepairStatus.BUDGET_EXHAUSTED, e.requestManualRepair(ok, T0 + 60_000L).status);
        assertEquals(0, e.report(T0 + 60_000L).routeAttempts);
    }

    @Test public void manualRepairCanRevokeHandsOffButRejectsUnsafeStates() {
        Harness h = new Harness(PreventiveRebuildMode.OFF, FaultClassifierTest.carCall().build());
        h.runFor(10_000L);
        h.state = droppedToPhone();
        h.runFor(5_000L);
        assertTrue(h.engine.report(h.now).userLeftBluetooth);
        assertEquals(ManualRepairStatus.STARTED, h.engine.requestManualRepair(h.state, h.now).status);
        assertFalse(h.engine.report(h.now).userLeftBluetooth);
        CallRepairEngine e = new CallRepairEngine(true, PreventiveRebuildMode.OFF, false, T0);
        CallAudioSnapshot ok = FaultClassifierTest.carCall().build();
        assertEquals(ManualRepairStatus.NO_ACTIVE_CALL, e.requestManualRepair(ok.toBuilder().phase(CallPhase.RINGING).build(), T0).status);
        assertEquals(ManualRepairStatus.MULTIPLE_CALLS, e.requestManualRepair(ok.toBuilder().callCount(2).build(), T0).status);
        assertEquals(ManualRepairStatus.BLUETOOTH_UNAVAILABLE, e.requestManualRepair(ok.toBuilder().bluetoothRouteAvailable(false).build(), T0).status);
        assertEquals(ManualRepairStatus.ALTERNATIVE_ROUTE, e.requestManualRepair(ok.toBuilder().route(AudioRoute.SPEAKER).build(), T0).status);
        assertEquals(ManualRepairStatus.ROUTE_UNKNOWN, e.requestManualRepair(ok.toBuilder().route(AudioRoute.UNKNOWN).build(), T0).status);
        e.onSnapshot(ok.toBuilder().phase(CallPhase.ENDED).build(), T0);
        assertEquals(ManualRepairStatus.NO_ACTIVE_CALL, e.requestManualRepair(ok, T0).status);
        assertEquals(0, e.report(T0).manualRepairs);
    }

    @Test public void manualRepairRestoresConfirmedSilencedVolumeWithinExistingBudget() {
        CallRepairEngine e = new CallRepairEngine(false, PreventiveRebuildMode.OFF, false, T0);
        CallAudioSnapshot quiet = FaultClassifierTest.carCall().voiceVolume(0, 7).build();
        assertEquals(List.of(RepairCommand.ROUTE_TO_EARPIECE, RepairCommand.RESTORE_VOICE_VOLUME),
                e.requestManualRepair(quiet, T0).step.commands);
        assertEquals(1, e.report(T0).volumeRestores);
    }

    @Test public void explicitRouteFailureBacksOffWithoutResettingAutomaticBudget() {
        CallRepairEngine e = new CallRepairEngine(true, PreventiveRebuildMode.OFF, false, T0);
        CallAudioSnapshot phone = droppedToPhone();
        e.onSnapshot(phone, T0);
        assertEquals(List.of(RepairCommand.ROUTE_TO_BLUETOOTH), e.onSnapshot(phone, T0 + 3_000L).commands);
        e.onRouteRequestFailed(T0 + 3_001L);
        assertTrue(e.onSnapshot(phone, T0 + 3_002L).commands.isEmpty());
        assertEquals(List.of(RepairCommand.ROUTE_TO_EARPIECE), e.onSnapshot(phone, T0 + 8_001L).commands);
        assertEquals(2, e.report(T0 + 8_001L).routeAttempts);
    }

    /** Drives the engine like the Android session: tick, execute, let the "system" react. */
    private static class Harness {
        final CallRepairEngine engine;
        final List<RepairCommand> executed = new ArrayList<>();
        CallAudioSnapshot state;
        long now = T0;

        Harness(PreventiveRebuildMode mode, CallAudioSnapshot initial) {
            engine = new CallRepairEngine(true, mode, false, T0);
            state = initial;
        }

        CallRepairEngine.Step tick() {
            CallRepairEngine.Step step = engine.onSnapshot(state, now);
            executed.addAll(step.commands);
            for (RepairCommand command : step.commands) react(command);
            return step;
        }

        /** Default system reaction: Telecom follows route requests and the fault clears. */
        void react(RepairCommand command) {
            if (command == RepairCommand.ROUTE_TO_EARPIECE || command == RepairCommand.ROUTE_TO_EARPIECE_LEGACY) {
                state = state.toBuilder().route(AudioRoute.EARPIECE).scoAudioConnected(false).build();
            } else if (command == RepairCommand.ROUTE_TO_BLUETOOTH || command == RepairCommand.ROUTE_TO_BLUETOOTH_LEGACY) {
                state = FaultClassifierTest.carCall().build();
            } else if (command == RepairCommand.RESTORE_VOICE_VOLUME) {
                state = state.toBuilder().voiceMuted(false).voiceVolume(4, 7).build();
            }
        }

        void runFor(long ms) {
            long until = now + ms;
            while (now < until) {
                CallRepairEngine.Step step = tick();
                now += Math.max(100L, Math.min(step.nextCheckInMs, until - now));
            }
        }

        CallReport end() {
            state = state.toBuilder().phase(CallPhase.ENDED).build();
            tick();
            return engine.report(now);
        }
    }

    @Test public void answeringInCarWithPhoneRouteNeedsNoBluetoothSelection() {
        Harness h = new Harness(PreventiveRebuildMode.OFF,
                droppedToPhone().toBuilder().phase(CallPhase.RINGING).build());
        h.runFor(45_000L);
        assertTrue(h.executed.isEmpty());
        h.state = h.state.toBuilder().phase(CallPhase.ACTIVE).build();
        h.runFor(15_000L); // No manual route selection or repeated tap occurs.
        assertEquals(List.of(RepairCommand.ROUTE_TO_BLUETOOTH), h.executed);
        assertEquals(CallOutcome.REPAIRED, h.end().outcome);
    }

    @Test public void ringingAudioEvidenceCannotEstablishTheAnsweredBluetoothLink() {
        Harness h = new Harness(PreventiveRebuildMode.OFF,
                FaultClassifierTest.carCall().phase(CallPhase.RINGING).build());
        h.runFor(45_000L); // Positive signals while ringing must not count toward call stability.
        h.state = h.state.toBuilder().phase(CallPhase.ACTIVE).build();
        h.tick();
        h.now += 198L;
        h.state = droppedToPhone(); // Auto-answer's first SCO attempt collapses.
        h.runFor(15_000L);
        assertEquals(List.of(RepairCommand.ROUTE_TO_BLUETOOTH), h.executed);
        assertFalse(h.end().userLeftBluetooth);
    }

    @Test public void brieflyHealthyAnsweredLinkCannotLatchHandsOffOnFallback() {
        Harness h = new Harness(PreventiveRebuildMode.OFF, FaultClassifierTest.carCall().build());
        h.tick();
        h.now += CallRepairEngine.STEADY_BLUETOOTH_MS;
        h.tick();
        h.now += 100L;
        h.state = droppedToPhone();
        h.runFor(15_000L);
        assertEquals(List.of(RepairCommand.ROUTE_TO_BLUETOOTH), h.executed);
        assertFalse(h.end().userLeftBluetooth);
    }

    @Test public void ringingFallbackDoesNotDisableRepairAfterAnswer() {
        Harness h = new Harness(PreventiveRebuildMode.OFF,
                FaultClassifierTest.carCall().phase(CallPhase.RINGING)
                        .scoAudioConnected(false).voiceOnBluetooth(false).build());
        h.tick();
        h.now += 748L;
        h.state = h.state.toBuilder().route(AudioRoute.EARPIECE).build();
        h.tick();
        h.now += 3_909L;
        h.state = h.state.toBuilder().phase(CallPhase.ACTIVE).build();
        h.runFor(15_000L);
        assertEquals(List.of(RepairCommand.ROUTE_TO_BLUETOOTH), h.executed);
        assertFalse(h.end().userLeftBluetooth);
    }

    @Test public void unknownAudioBeforeFallbackCannotLatchHandsOff() {
        Harness h = new Harness(PreventiveRebuildMode.OFF,
                FaultClassifierTest.carCall().scoAudioConnected(null).voiceOnBluetooth(null).build());
        h.tick();
        h.now += 2_000L;
        h.tick();
        h.state = droppedToPhone();
        h.runFor(15_000L);
        assertEquals(List.of(RepairCommand.ROUTE_TO_BLUETOOTH), h.executed);
        assertFalse(h.end().userLeftBluetooth);
    }

    @Test public void failedBluetoothSelectionIsRepairedAfterScoFallback() {
        Harness h = new Harness(PreventiveRebuildMode.OFF,
                FaultClassifierTest.carCall().scoAudioConnected(false)
                        .voiceOnBluetooth(false).build());
        h.tick();
        h.now += 177L;
        h.state = droppedToPhone();
        h.runFor(15_000L);
        assertEquals(List.of(RepairCommand.ROUTE_TO_BLUETOOTH), h.executed);
        assertEquals(CallOutcome.REPAIRED, h.end().outcome);
    }

    @Test public void selectingBluetoothAgainResumesRepairAfterChoosingPhone() {
        Harness h = new Harness(PreventiveRebuildMode.OFF, FaultClassifierTest.carCall().build());
        h.runFor(10_000L);
        h.state = droppedToPhone();
        h.runFor(5_000L);
        assertTrue(h.engine.report(h.now).userLeftBluetooth);
        h.state = FaultClassifierTest.carCall().scoAudioConnected(false)
                .voiceOnBluetooth(false).build();
        h.tick();
        h.now += 198L;
        h.state = droppedToPhone();
        h.runFor(15_000L);
        assertEquals(List.of(RepairCommand.ROUTE_TO_BLUETOOTH), h.executed);
        assertFalse(h.end().userLeftBluetooth);
    }

    @Test public void repeatedFailedSelectionsCannotResetTheRepairBudget() {
        Harness h = new Harness(PreventiveRebuildMode.OFF, droppedToPhone()) {
            @Override void react(RepairCommand command) {
                // Vendor stack ignores all route requests.
            }
        };
        h.runFor(60_000L);
        assertEquals(CallRepairEngine.MAX_ROUTE_ATTEMPTS, h.engine.report(h.now).routeAttempts);
        int commandsAfterBudget = h.executed.size();
        for (long selectionDuration : new long[] {198L, 253L, 483L}) {
            h.state = FaultClassifierTest.carCall().scoAudioConnected(false)
                    .voiceOnBluetooth(false).build();
            h.tick();
            h.now += selectionDuration;
            h.state = droppedToPhone();
            h.runFor(10_000L);
        }
        assertEquals(commandsAfterBudget, h.executed.size());
        assertEquals(CallOutcome.UNRESOLVED, h.end().outcome);
    }

    @Test public void wiredChosenDuringRebuildIsNeverOverridden() {
        Harness h = new Harness(PreventiveRebuildMode.ALWAYS, FaultClassifierTest.carCall().build()) {
            @Override void react(RepairCommand command) {
                state = state.toBuilder().route(AudioRoute.WIRED_HEADSET).build();
            }
        };
        h.runFor(30_000L);
        assertEquals(List.of(RepairCommand.ROUTE_TO_EARPIECE), h.executed);
        assertTrue(h.end().userLeftBluetooth);
    }

    @Test public void rapidFailedSelectionsShareOneConfirmationWindow() {
        Harness h = new Harness(PreventiveRebuildMode.OFF, droppedToPhone());
        h.tick();
        for (int i = 0; i < 8; i++) {
            h.now += 300L;
            h.state = FaultClassifierTest.carCall().scoAudioConnected(false).build();
            h.tick();
            h.now += 198L;
            h.state = droppedToPhone();
            h.tick();
            if (!h.executed.isEmpty()) break;
        }
        assertFalse("repeated failed taps must not restart confirmation forever", h.executed.isEmpty());
    }

    @Test public void bluetoothOffDuringRebuildSuspendsCommandsUntilReconnection() {
        Harness h = afterPreventiveRebuild();
        h.state = droppedToPhone().toBuilder()
                .bluetoothRouteAvailable(false).hfpConnected(false).scoAudioConnected(null).build();
        int before = h.executed.size();
        h.runFor(10_000L);
        assertEquals("no route requests while Bluetooth is off", before, h.executed.size());
        h.state = droppedToPhone();
        h.runFor(20_000L);
        assertFalse(h.end().userLeftBluetooth);
        assertTrue(h.executed.size() > before);
    }

    @Test public void missingBluetoothAfterConfirmedFaultIsNotReportedRepaired() {
        CallRepairEngine e = new CallRepairEngine(false, PreventiveRebuildMode.OFF, false, T0);
        CallAudioSnapshot broken = FaultClassifierTest.carCall().scoAudioConnected(false).build();
        e.onSnapshot(broken, T0);
        e.onSnapshot(broken, T0 + CallRepairEngine.CONFIRM_MS);
        e.onSnapshot(droppedToPhone().toBuilder().bluetoothRouteAvailable(false)
                .hfpConnected(false).scoAudioConnected(null).build(), T0 + 5_000L);
        assertEquals(CallOutcome.UNRESOLVED, e.report(T0 + 5_000L).outcome);
    }

    @Test public void ignoredRebuildReturnIsRepeatedOnlyOnce() {
        Harness h = new Harness(PreventiveRebuildMode.ALWAYS, FaultClassifierTest.carCall().build()) {
            @Override void react(RepairCommand c) {
                if (c == RepairCommand.ROUTE_TO_EARPIECE) state = droppedToPhone();
            }
        };
        h.runFor(8_000L);
        assertEquals(2L, h.executed.stream().filter(c -> c == RepairCommand.ROUTE_TO_BLUETOOTH).count());
    }

    @Test public void speakerWithoutEarpieceCancelsRebuild() {
        Harness h = new Harness(PreventiveRebuildMode.ALWAYS,
                FaultClassifierTest.carCall().earpieceAvailable(false).build()) {
            @Override void react(RepairCommand c) {
                state = state.toBuilder().route(AudioRoute.SPEAKER).build();
            }
        };
        h.runFor(20_000L);
        assertEquals(List.of(RepairCommand.ROUTE_TO_BLUETOOTH), h.executed);
        assertTrue(h.end().userLeftBluetooth);
    }

    @Test public void ignoredDirectRequestEscalatesToBluetoothRebuild() {
        Harness h = new Harness(PreventiveRebuildMode.OFF, droppedToPhone()) {
            boolean tornDown;
            @Override void react(RepairCommand c) {
                if (c == RepairCommand.ROUTE_TO_EARPIECE) tornDown = true;
                if (tornDown) super.react(c);
            }
        };
        h.runFor(30_000L);
        assertEquals(List.of(RepairCommand.ROUTE_TO_BLUETOOTH,
                RepairCommand.ROUTE_TO_EARPIECE, RepairCommand.ROUTE_TO_BLUETOOTH), h.executed);
        assertEquals(CallOutcome.REPAIRED, h.end().outcome);
    }

    @Test public void unknownAudioAfterFaultCannotProveRecovery() {
        CallRepairEngine e = new CallRepairEngine(false, PreventiveRebuildMode.OFF, false, T0);
        CallAudioSnapshot broken = FaultClassifierTest.carCall().scoAudioConnected(false).build();
        e.onSnapshot(broken, T0);
        e.onSnapshot(broken, T0 + 2_000L);
        CallAudioSnapshot unknown = broken.toBuilder().scoAudioConnected(null).voiceOnBluetooth(null).build();
        e.onSnapshot(unknown, T0 + 3_000L);
        e.onSnapshot(unknown, T0 + 10_000L);
        assertEquals(CallOutcome.UNRESOLVED, e.report(T0 + 10_000L).outcome);
    }

    @Test public void briefHealthyPulseCannotClearConfirmedFault() {
        CallRepairEngine e = new CallRepairEngine(false, PreventiveRebuildMode.OFF, false, T0);
        CallAudioSnapshot broken = FaultClassifierTest.carCall().scoAudioConnected(false).build();
        e.onSnapshot(broken, T0);
        e.onSnapshot(broken, T0 + 2_000L);
        e.onSnapshot(FaultClassifierTest.carCall().build(), T0 + 3_000L);
        assertEquals(CallOutcome.UNRESOLVED, e.report(T0 + 3_000L).outcome);
        e.onSnapshot(broken, T0 + 3_198L);
        assertEquals(CallOutcome.UNRESOLVED, e.report(T0 + 3_198L).outcome);
    }

    @Test public void stableHealthyEvidenceClearsConfirmedFault() {
        CallRepairEngine e = new CallRepairEngine(false, PreventiveRebuildMode.OFF, false, T0);
        CallAudioSnapshot broken = FaultClassifierTest.carCall().scoAudioConnected(false).build();
        e.onSnapshot(broken, T0);
        e.onSnapshot(broken, T0 + 2_000L);
        CallAudioSnapshot ok = FaultClassifierTest.carCall().build();
        e.onSnapshot(ok, T0 + 3_000L);
        e.onSnapshot(ok, T0 + 3_000L + CallRepairEngine.VERIFY_MS);
        assertEquals(CallOutcome.REPAIRED, e.report(T0 + 6_000L).outcome);
    }

    @Test public void multipleCallsDeferRepairWithoutSpendingBudget() {
        Harness h = new Harness(PreventiveRebuildMode.ALWAYS, droppedToPhone().toBuilder().callCount(2).build());
        h.runFor(20_000L);
        assertTrue(h.executed.isEmpty());
        assertEquals(0, h.engine.report(h.now).routeAttempts);
        h.state = h.state.toBuilder().callCount(1).build();
        h.runFor(20_000L);
        assertFalse(h.executed.isEmpty());
    }

    @Test public void noRouteRequestsWhenTelecomRouteIsUnknown() {
        Harness h = new Harness(PreventiveRebuildMode.ALWAYS,
                FaultClassifierTest.carCall().route(AudioRoute.UNKNOWN).build());
        h.runFor(20_000L);
        assertTrue(h.executed.isEmpty());
    }

    @Test public void healthyCallWithoutPreventiveModeIsLeftAlone() {
        Harness h = new Harness(PreventiveRebuildMode.OFF, FaultClassifierTest.carCall().build());
        h.runFor(60_000L);
        assertTrue(h.executed.isEmpty());
        assertEquals(CallOutcome.HEALTHY, h.end().outcome);
    }

    @Test public void preventiveRebuildRunsExactlyOnceAfterBluetoothIsSteady() {
        Harness h = new Harness(PreventiveRebuildMode.ALWAYS, FaultClassifierTest.carCall().build());
        CallRepairEngine.Step first = h.tick();
        assertTrue("must wait for steady Bluetooth", first.commands.isEmpty());

        h.runFor(60_000L);
        assertEquals(
                List.of(RepairCommand.ROUTE_TO_EARPIECE, RepairCommand.ROUTE_TO_BLUETOOTH),
                h.executed);

        CallReport report = h.end();
        assertTrue(report.preventiveRebuild);
        assertEquals("our own earpiece hop is not a user escape", CallOutcome.HEALTHY, report.outcome);
    }

    @Test public void preventiveRebuildIsSkippedAfterTheStartWindow() {
        Harness h = new Harness(
                PreventiveRebuildMode.ALWAYS,
                FaultClassifierTest.carCall().route(AudioRoute.SPEAKER).build());
        h.runFor(CallRepairEngine.PREVENTIVE_WINDOW_MS + 1_000L);
        h.state = FaultClassifierTest.carCall().build();
        h.runFor(30_000L);
        assertTrue(h.executed.isEmpty());
    }

    @Test public void longRingingDoesNotConsumeThePreventiveWindow() {
        Harness h = new Harness(
                PreventiveRebuildMode.ALWAYS,
                FaultClassifierTest.carCall().phase(CallPhase.RINGING).build());
        h.runFor(CallRepairEngine.PREVENTIVE_WINDOW_MS + 5_000L);
        assertTrue(h.executed.isEmpty());
        h.state = FaultClassifierTest.carCall().build(); // answered in the car
        h.runFor(10_000L);
        assertEquals(
                List.of(RepairCommand.ROUTE_TO_EARPIECE, RepairCommand.ROUTE_TO_BLUETOOTH),
                h.executed);
    }

    @Test public void afterProblemsModeNeedsElevatedRisk() {
        CallRepairEngine calm = new CallRepairEngine(true, PreventiveRebuildMode.AFTER_PROBLEMS, false, T0);
        CallRepairEngine risky = new CallRepairEngine(true, PreventiveRebuildMode.AFTER_PROBLEMS, true, T0);
        CallAudioSnapshot ok = FaultClassifierTest.carCall().build();
        calm.onSnapshot(ok, T0);
        risky.onSnapshot(ok, T0);
        assertTrue(calm.onSnapshot(ok, T0 + 2_000L).commands.isEmpty());
        assertEquals(
                List.of(RepairCommand.ROUTE_TO_EARPIECE),
                risky.onSnapshot(ok, T0 + 2_000L).commands);
    }

    @Test public void noPreventiveRebuildInConferenceOrWaitingCalls() {
        Harness h = new Harness(
                PreventiveRebuildMode.ALWAYS, FaultClassifierTest.carCall().callCount(2).build());
        h.runFor(40_000L);
        assertTrue(h.executed.isEmpty());
    }

    @Test public void callStuckOnPhoneIsRoutedToCarAfterConfirmation() {
        Harness h = new Harness(
                PreventiveRebuildMode.OFF,
                FaultClassifierTest.carCall().route(AudioRoute.EARPIECE).scoAudioConnected(false).build());
        CallRepairEngine.Step first = h.tick();
        assertTrue(first.commands.isEmpty());

        h.runFor(CallRepairEngine.ROUTE_CONFIRM_MS + 10_000L);
        assertEquals(List.of(RepairCommand.ROUTE_TO_BLUETOOTH), h.executed);

        CallReport report = h.end();
        assertEquals(CallOutcome.REPAIRED, report.outcome);
        assertTrue(report.faults.contains(Fault.CALL_NOT_ON_BLUETOOTH));
    }

    @Test public void oneWayAudioTriggersFullRebuild() {
        Harness h = new Harness(
                PreventiveRebuildMode.OFF,
                FaultClassifierTest.carCall().voiceOnBluetooth(false).build());
        h.runFor(20_000L);
        assertEquals(
                List.of(RepairCommand.ROUTE_TO_EARPIECE, RepairCommand.ROUTE_TO_BLUETOOTH),
                h.executed);
        assertEquals(CallOutcome.REPAIRED, h.end().outcome);
    }

    @Test public void silencedVoiceIsRestoredWithoutRouteChange() {
        Harness h = new Harness(
                PreventiveRebuildMode.OFF, FaultClassifierTest.carCall().voiceVolume(0, 7).build());
        h.runFor(10_000L);
        assertEquals(List.of(RepairCommand.RESTORE_VOICE_VOLUME), h.executed);
        assertEquals(CallOutcome.REPAIRED, h.end().outcome);
    }

    @Test public void persistentFaultStopsAfterBudgetAndIsReportedUnresolved() {
        Harness h = new Harness(
                PreventiveRebuildMode.OFF,
                FaultClassifierTest.carCall().scoAudioConnected(false).build()) {
            @Override void react(RepairCommand command) {
                // Broken vendor stack: Telecom follows, but SCO never comes back up.
                if (command == RepairCommand.REFRESH_BLUETOOTH_OBSERVATIONS
                        || command == RepairCommand.TRY_COMMUNICATION_DEVICE) return;
                AudioRoute route = command == RepairCommand.ROUTE_TO_EARPIECE || command == RepairCommand.ROUTE_TO_EARPIECE_LEGACY
                        ? AudioRoute.EARPIECE : AudioRoute.BLUETOOTH;
                state = state.toBuilder().route(route).scoAudioConnected(false).build();
            }
        };
        h.runFor(120_000L);
        long rebuilds = h.executed.stream().filter(c -> c == RepairCommand.ROUTE_TO_EARPIECE
                || c == RepairCommand.ROUTE_TO_EARPIECE_LEGACY).count();
        assertEquals(CallRepairEngine.MAX_ROUTE_ATTEMPTS, rebuilds);
        CallReport report = h.end();
        assertEquals(CallOutcome.UNRESOLVED, report.outcome);
        assertEquals(CallRepairEngine.MAX_ROUTE_ATTEMPTS, report.routeAttempts);
    }

    @Test public void userSwitchingToPhoneIsRespectedAndReported() {
        Harness h = new Harness(PreventiveRebuildMode.OFF, FaultClassifierTest.carCall().build());
        h.runFor(10_000L);
        h.state = h.state.toBuilder().route(AudioRoute.EARPIECE).build();
        h.runFor(60_000L);
        assertTrue("never fight the user's choice", h.executed.isEmpty());
        CallReport report = h.end();
        assertEquals(CallOutcome.LEFT_BLUETOOTH, report.outcome);
        assertTrue(report.userLeftBluetooth);
    }

    /** Runs the preventive rebuild until Telecom reports Bluetooth again. */
    private static Harness afterPreventiveRebuild() {
        Harness h = new Harness(PreventiveRebuildMode.ALWAYS, FaultClassifierTest.carCall().build());
        while (h.executed.size() < 2) {
            h.tick();
            h.now += 250L;
        }
        return h;
    }

    private static CallAudioSnapshot droppedToPhone() {
        // Android 14 Telecom: BT-initiated SCO disconnect in ActiveBluetoothRoute -> earpiece.
        return FaultClassifierTest.carCall()
                .route(AudioRoute.EARPIECE)
                .scoAudioConnected(false)
                .build();
    }

    @Test public void dropToPhoneRightAfterOurRebuildIsRepairedNotTreatedAsUserChoice() {
        Harness h = afterPreventiveRebuild();
        h.runFor(4_000L); // past verification: Bluetooth looked fine
        h.state = droppedToPhone(); // the fresh SCO link collapses, Telecom falls back
        h.runFor(20_000L);

        assertEquals(
                List.of(
                        RepairCommand.ROUTE_TO_EARPIECE,
                        RepairCommand.ROUTE_TO_BLUETOOTH,
                        RepairCommand.ROUTE_TO_BLUETOOTH),
                h.executed);
        CallReport report = h.end();
        assertFalse("our failed repair is not a user choice", report.userLeftBluetooth);
        assertEquals(CallOutcome.REPAIRED, report.outcome);
        assertTrue(report.faults.contains(Fault.CALL_NOT_ON_BLUETOOTH));
    }

    @Test public void secondSwitchToPhoneAfterOurRepairIsRespected() {
        Harness h = afterPreventiveRebuild();
        h.runFor(4_000L);
        h.state = droppedToPhone();
        h.runFor(10_000L); // moved back to the car once
        int commands = h.executed.size();

        h.state = droppedToPhone(); // driver insists on the phone
        h.runFor(60_000L);
        assertEquals("never fight a repeated choice", commands, h.executed.size());
        assertTrue(h.end().userLeftBluetooth);
    }

    @Test public void switchToPhoneLongAfterOurRebuildIsRespected() {
        Harness h = afterPreventiveRebuild();
        h.runFor(CallRepairEngine.REPAIR_GRACE_MS + 5_000L);
        h.state = droppedToPhone();
        h.runFor(60_000L);
        assertEquals(2, h.executed.size());
        assertEquals(CallOutcome.LEFT_BLUETOOTH, h.end().outcome);
    }

    @Test public void speakerRightAfterOurRebuildIsAUserChoice() {
        Harness h = afterPreventiveRebuild();
        h.runFor(4_000L);
        h.state = h.state.toBuilder().route(AudioRoute.SPEAKER).scoAudioConnected(false).build();
        h.runFor(30_000L);
        assertEquals(2, h.executed.size());
        assertEquals(CallOutcome.LEFT_BLUETOOTH, h.end().outcome);
    }

    @Test public void speakerChosenDuringRebuildIsNeverOverridden() {
        Harness h = new Harness(PreventiveRebuildMode.ALWAYS, FaultClassifierTest.carCall().build()) {
            @Override void react(RepairCommand command) {
                if (command == RepairCommand.ROUTE_TO_EARPIECE) {
                    // While SCO tears down, the user taps "Speaker" in the dialer.
                    state = state.toBuilder().route(AudioRoute.SPEAKER).scoAudioConnected(false).build();
                } else {
                    super.react(command);
                }
            }
        };
        h.runFor(60_000L);
        assertEquals(List.of(RepairCommand.ROUTE_TO_EARPIECE), h.executed);
        assertTrue(h.end().userLeftBluetooth);
    }

    @Test public void speakerIsNeverOverridden() {
        Harness h = new Harness(
                PreventiveRebuildMode.ALWAYS,
                FaultClassifierTest.carCall().route(AudioRoute.SPEAKER).build());
        h.runFor(60_000L);
        assertTrue(h.executed.isEmpty());
    }

    @Test public void missingBluetoothRouteRefreshesObservationOnceButNeverForcesUnsupportedRouting() {
        Harness h = new Harness(
                PreventiveRebuildMode.ALWAYS,
                FaultClassifierTest.carCall().bluetoothRouteAvailable(false).route(AudioRoute.EARPIECE).build());
        h.runFor(30_000L);
        assertEquals(List.of(RepairCommand.REFRESH_BLUETOOTH_OBSERVATIONS), h.executed);
        assertEquals(CallOutcome.BLUETOOTH_UNAVAILABLE, h.end().outcome);
    }

    @Test public void observeOnlyModeDetectsButNeverActs() {
        CallRepairEngine engine = new CallRepairEngine(false, PreventiveRebuildMode.ALWAYS, true, T0);
        CallAudioSnapshot broken = FaultClassifierTest.carCall().voiceOnBluetooth(false).build();
        for (long t = T0; t < T0 + 30_000L; t += 500L) {
            assertTrue(engine.onSnapshot(broken, t).commands.isEmpty());
        }
        engine.onSnapshot(broken.toBuilder().phase(CallPhase.ENDED).build(), T0 + 30_000L);
        assertEquals(CallOutcome.UNRESOLVED, engine.report(T0 + 30_000L).outcome);
    }

    @Test public void callWithoutCarIsNotBluetooth() {
        Harness h = new Harness(
                PreventiveRebuildMode.ALWAYS,
                CallAudioSnapshot.builder().route(AudioRoute.EARPIECE).build());
        h.runFor(10_000L);
        assertTrue(h.executed.isEmpty());
        assertEquals(CallOutcome.NOT_BLUETOOTH, h.end().outcome);
    }

    @Test public void transientFaultShorterThanConfirmationIsIgnored() {
        Harness h = new Harness(PreventiveRebuildMode.OFF, FaultClassifierTest.carCall().build());
        h.runFor(5_000L);
        h.state = h.state.toBuilder().scoAudioConnected(false).build();
        h.runFor(CallRepairEngine.CONFIRM_MS / 2);
        h.state = h.state.toBuilder().scoAudioConnected(true).build();
        h.runFor(10_000L);
        assertTrue(h.executed.isEmpty());
        assertEquals(CallOutcome.HEALTHY, h.end().outcome);
    }

    @Test public void withoutEarpieceRebuildFallsBackToBluetoothRequest() {
        Harness h = new Harness(
                PreventiveRebuildMode.ALWAYS,
                FaultClassifierTest.carCall().earpieceAvailable(false).build());
        h.runFor(10_000L);
        assertEquals(List.of(RepairCommand.ROUTE_TO_BLUETOOTH), h.executed);
    }

    @Test public void endedEngineIgnoresLateSnapshots() {
        Harness h = new Harness(PreventiveRebuildMode.ALWAYS, FaultClassifierTest.carCall().build());
        h.end();
        h.state = FaultClassifierTest.carCall().voiceOnBluetooth(false).build();
        h.runFor(20_000L);
        assertTrue(h.executed.isEmpty());
        assertFalse(h.engine.report(h.now).preventiveRebuild);
    }
}
