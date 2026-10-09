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
import de.kaipressmar.a52srepair.core.report.CallOutcome;
import de.kaipressmar.a52srepair.core.report.CallReport;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class CallRepairEngineTest {
    private static final long T0 = 1_000_000L;

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
            if (command == RepairCommand.ROUTE_TO_EARPIECE) {
                state = state.toBuilder().route(AudioRoute.EARPIECE).scoAudioConnected(false).build();
            } else if (command == RepairCommand.ROUTE_TO_BLUETOOTH) {
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
                AudioRoute route = command == RepairCommand.ROUTE_TO_EARPIECE
                        ? AudioRoute.EARPIECE : AudioRoute.BLUETOOTH;
                state = state.toBuilder().route(route).scoAudioConnected(false).build();
            }
        };
        h.runFor(120_000L);
        long rebuilds = h.executed.stream().filter(c -> c == RepairCommand.ROUTE_TO_EARPIECE).count();
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

    @Test public void missingBluetoothRouteIsReportedButNotTouched() {
        Harness h = new Harness(
                PreventiveRebuildMode.ALWAYS,
                FaultClassifierTest.carCall().bluetoothRouteAvailable(false).route(AudioRoute.EARPIECE).build());
        h.runFor(30_000L);
        assertTrue(h.executed.isEmpty());
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
