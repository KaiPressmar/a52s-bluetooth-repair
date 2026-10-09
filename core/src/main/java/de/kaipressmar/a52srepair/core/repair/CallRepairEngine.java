package de.kaipressmar.a52srepair.core.repair;

import de.kaipressmar.a52srepair.core.diagnosis.Fault;
import de.kaipressmar.a52srepair.core.diagnosis.FaultClassifier;
import de.kaipressmar.a52srepair.core.model.AudioRoute;
import de.kaipressmar.a52srepair.core.model.CallAudioSnapshot;
import de.kaipressmar.a52srepair.core.model.CallPhase;
import de.kaipressmar.a52srepair.core.report.CallOutcome;
import de.kaipressmar.a52srepair.core.report.CallReport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Per-call detection and repair state machine. One instance lives exactly as long as one call
 * session. It never touches Android: the caller feeds snapshots and executes the returned commands.
 *
 * <p>Safety rules:
 * <ul>
 *   <li>Faults must persist for a confirmation window before any action.</li>
 *   <li>At most {@link #MAX_ROUTE_ATTEMPTS} route operations per call, each followed by verification.</li>
 *   <li>Leaving an established Bluetooth audio connection is respected until Bluetooth is
 *       selected again. Ringing and failed SCO connection attempts are not a user exit.
 *       Exception: the first drop to
 *       the phone within {@link #REPAIR_GRACE_MS} after one of our route changes is our repair
 *       failing (Telecom falls back to the earpiece when a fresh SCO link collapses), so it is
 *       repaired once. A second drop is always respected.</li>
 *   <li>Speaker and wired routes are never overridden, not even in the middle of a rebuild.</li>
 * </ul>
 */
public final class CallRepairEngine {
    public static final long CONFIRM_MS = 2_000L;
    public static final long VOLUME_CONFIRM_MS = 1_500L;
    /** Telecom itself may need 2-3 s at call start to bring SCO up; don't race it. */
    public static final long ROUTE_CONFIRM_MS = 3_000L;
    public static final long LEAVE_TIMEOUT_MS = 1_500L;
    public static final long RETURN_TIMEOUT_MS = 6_000L;
    public static final long ROUTE_REQUEST_TIMEOUT_MS = 4_000L;
    public static final long VERIFY_MS = 2_500L;
    public static final long STEADY_BLUETOOTH_MS = 1_000L;
    public static final long PREVENTIVE_WINDOW_MS = 30_000L;
    public static final long COMMAND_ECHO_MS = 2_000L;
    /**
     * A drop from Bluetooth to the phone this soon after one of our route changes is our repair
     * failing, not a user choice. Android 14 Telecom moves the call to the earpiece when SCO
     * disconnects without a request ({@code ActiveBluetoothRoute.handleBtInitiatedDisconnect}).
     */
    public static final long REPAIR_GRACE_MS = 8_000L;
    public static final long FAST_TICK_MS = 1_000L;
    public static final long SLOW_TICK_MS = 5_000L;
    public static final long IDLE_TICK_MS = 15_000L;
    public static final int MAX_ROUTE_ATTEMPTS = 3;
    public static final int MAX_VOLUME_RESTORES = 2;

    /** Result of one evaluation: commands to run now and when to evaluate again. */
    public static final class Step {
        public final List<RepairCommand> commands;
        public final long nextCheckInMs;
        public final String note;

        Step(List<RepairCommand> commands, long nextCheckInMs, String note) {
            this.commands = Collections.unmodifiableList(commands);
            this.nextCheckInMs = nextCheckInMs;
            this.note = note;
        }
    }

    private enum Operation {
        NONE,
        REBUILD_LEAVING,
        REBUILD_RETURNING,
        ROUTE_REQUEST,
        VERIFYING_ROUTE,
        VERIFYING_VOLUME
    }

    private final boolean repairEnabled;
    private final PreventiveRebuildMode preventiveMode;
    private final boolean riskElevated;
    private final long startedAt;

    private Operation operation = Operation.NONE;
    private long operationSince;
    private boolean operationPreventive;
    private boolean returnRequestRepeated;
    private boolean directRouteFailed;
    private long healthySince = -1L;
    private long lastCommandAt = Long.MIN_VALUE / 2;
    private long lastRouteChangeAt = Long.MIN_VALUE / 2;
    private boolean dropForgiven;

    private long audioSince = -1L;
    private AudioRoute lastRoute = AudioRoute.UNKNOWN;
    private long bluetoothSince = -1L;
    private long usableBluetoothSince = -1L;
    private boolean lastBluetoothUsable;
    private boolean everOnBluetooth;
    private boolean bluetoothEverAvailable;
    private boolean userLeftBluetooth;

    private Fault currentFault = Fault.NONE;
    private long faultSince;
    private Fault lastConfirmedFault = Fault.NONE;
    private final Set<Fault> confirmedFaults = EnumSet.noneOf(Fault.class);

    private int routeAttempts;
    private int volumeRestores;
    private boolean preventiveDone;
    private boolean ended;
    private long endedAt;

    public CallRepairEngine(
            boolean repairEnabled,
            PreventiveRebuildMode preventiveMode,
            boolean riskElevated,
            long startedAt) {
        this.repairEnabled = repairEnabled;
        this.preventiveMode = preventiveMode == null ? PreventiveRebuildMode.OFF : preventiveMode;
        this.riskElevated = riskElevated;
        this.startedAt = startedAt;
    }

    public Step onSnapshot(CallAudioSnapshot s, long now) {
        if (ended) return step(IDLE_TICK_MS, "ended");
        if (s.phase == CallPhase.ENDED) {
            finish(now);
            return step(IDLE_TICK_MS, "call ended");
        }

        bluetoothEverAvailable |= s.bluetoothRouteAvailable;
        if (audioSince < 0 && s.phase.carriesAudio()) audioSince = now;
        trackRoute(s, now);

        boolean healthyEvidence = s.phase.carriesAudio() && s.bluetoothRouteAvailable
                && s.route == AudioRoute.BLUETOOTH && FaultClassifier.classify(s) == Fault.NONE
                && (Boolean.TRUE.equals(s.scoAudioConnected) || Boolean.TRUE.equals(s.voiceOnBluetooth));
        if (healthyEvidence) {
            if (healthySince < 0L) healthySince = now;
        } else {
            healthySince = -1L;
        }

        if (routeOperationActive() && (!s.bluetoothRouteAvailable || !s.phase.carriesAudio()
                || s.callCount > 1)) {
            // Bluetooth off/on and held calls must not keep emitting stale route commands.
            endOperation();
            currentFault = Fault.NONE;
            faultSince = now;
            return step(FAST_TICK_MS, "route repair suspended; waiting for call audio and Bluetooth");
        }

        Step operationStep = advanceOperation(s, now);
        if (operationStep != null) return operationStep;

        Fault fault = FaultClassifier.classify(s);
        if (fault != currentFault) {
            // A failed selection changes the visible symptom (phone <-> Bluetooth/SCO),
            // not the underlying outage. Repeated taps must not postpone repair indefinitely.
            if (!connectionFault(fault) || !connectionFault(currentFault)) faultSince = now;
            currentFault = fault;
        }

        if (userLeftBluetooth) {
            return step(IDLE_TICK_MS, "user left Bluetooth – hands off");
        }

        if (fault == Fault.NONE) {
            if (lastConfirmedFault != Fault.NONE) {
                if (healthySince < 0L) return step(FAST_TICK_MS, "recovery not verified: Bluetooth audio evidence missing");
                long steady = now - healthySince;
                if (steady < VERIFY_MS) return step(VERIFY_MS - steady, "confirming stable Bluetooth recovery");
                lastConfirmedFault = Fault.NONE;
                directRouteFailed = false;
            }
            if (s.route == AudioRoute.UNKNOWN) return step(FAST_TICK_MS, "waiting for Telecom route");
            return maybePreventiveRebuild(s, now);
        }

        long confirmMs = confirmWindow(fault);
        long age = now - faultSince;
        if (age < confirmMs) {
            return step(Math.max(250L, confirmMs - age), "confirming " + fault);
        }

        confirmedFaults.add(fault);
        lastConfirmedFault = fault;

        if (s.callCount > 1) return step(FAST_TICK_MS, fault + " observed; repair deferred for multiple calls");

        if (!fault.repairable) return step(SLOW_TICK_MS, fault + " not repairable");
        if (!repairEnabled) return step(SLOW_TICK_MS, fault + " observed (repair disabled)");

        switch (fault) {
            case VOICE_SILENCED:
                if (volumeRestores >= MAX_VOLUME_RESTORES) {
                    return step(IDLE_TICK_MS, "volume restore budget exhausted");
                }
                volumeRestores++;
                begin(Operation.VERIFYING_VOLUME, now, false);
                return step(VERIFY_MS, "restore voice volume", RepairCommand.RESTORE_VOICE_VOLUME);
            case CALL_NOT_ON_BLUETOOTH:
                if (!takeRouteAttempt()) return exhausted();
                if (directRouteFailed) return startRebuild(s, now, false, "rebuild after failed Bluetooth route request");
                begin(Operation.ROUTE_REQUEST, now, false);
                return step(500L, "route call to Bluetooth", RepairCommand.ROUTE_TO_BLUETOOTH);
            case SCO_DISCONNECTED:
            case DOWNLINK_NOT_ON_BLUETOOTH:
            default:
                if (!takeRouteAttempt()) return exhausted();
                return startRebuild(s, now, false, "rebuild Bluetooth audio for " + fault);
        }
    }

    /** Builds the per-call report. Safe to call before or after the call ended. */
    public CallReport report(long now) {
        long end = ended ? endedAt : now;
        return new CallReport(
                startedAt,
                Math.max(0L, end - startedAt),
                outcome(),
                EnumSet.copyOf(confirmedFaultsOrNone()),
                routeAttempts,
                volumeRestores,
                preventiveDone,
                userLeftBluetooth);
    }

    public boolean isEnded() {
        return ended;
    }

    CallOutcome outcome() {
        if (!bluetoothEverAvailable && !everOnBluetooth) {
            return confirmedFaults.contains(Fault.BLUETOOTH_ROUTE_MISSING)
                    ? CallOutcome.BLUETOOTH_UNAVAILABLE
                    : CallOutcome.NOT_BLUETOOTH;
        }
        if (lastConfirmedFault == Fault.BLUETOOTH_ROUTE_MISSING) {
            return CallOutcome.BLUETOOTH_UNAVAILABLE;
        }
        if (lastConfirmedFault != Fault.NONE) return CallOutcome.UNRESOLVED;
        if (!confirmedFaults.isEmpty()) return CallOutcome.REPAIRED;
        if (userLeftBluetooth) return CallOutcome.LEFT_BLUETOOTH;
        return CallOutcome.HEALTHY;
    }

    private Set<Fault> confirmedFaultsOrNone() {
        return confirmedFaults.isEmpty() ? EnumSet.of(Fault.NONE) : confirmedFaults;
    }

    private void trackRoute(CallAudioSnapshot s, long now) {
        if (s.route == AudioRoute.BLUETOOTH) {
            if (lastRoute != AudioRoute.BLUETOOTH) {
                bluetoothSince = now;
                // A fresh Bluetooth selection revokes the previous hands-off decision.
                // Do not reset the per-call repair budget, even after repeated selections.
                if (userLeftBluetooth) {
                    currentFault = Fault.NONE;
                    faultSince = now;
                }
                userLeftBluetooth = false;
            }
            boolean usableEvidence = s.phase.carriesAudio() && s.callCount == 1
                    && s.bluetoothRouteAvailable
                    && !Boolean.FALSE.equals(s.scoAudioConnected)
                    && !Boolean.FALSE.equals(s.voiceOnBluetooth)
                    && (Boolean.TRUE.equals(s.scoAudioConnected) || Boolean.TRUE.equals(s.voiceOnBluetooth));
            if (usableEvidence) {
                if (usableBluetoothSince < 0L) usableBluetoothSince = now;
            } else {
                usableBluetoothSince = -1L;
            }
            // Ringing time and a brief positive pulse cannot establish an answered SCO link.
            // A hands-free answer may fall back without another Bluetooth selection callback.
            lastBluetoothUsable = usableBluetoothSince >= 0L
                    && now - usableBluetoothSince >= VERIFY_MS;
            everOnBluetooth = true;
        } else {
            boolean leftBluetooth = lastRoute == AudioRoute.BLUETOOTH;
            boolean commanded =
                    operation == Operation.REBUILD_LEAVING
                            || now - lastCommandAt < COMMAND_ECHO_MS;
            // Telecom falls back to earpiece or wired headset; with an earpiece, speaker is a choice.
            boolean speakerChosen = s.route == AudioRoute.SPEAKER;
            boolean explicitAlternative = speakerChosen
                    || s.route == AudioRoute.WIRED_HEADSET
                    || s.route == AudioRoute.STREAMING;
            if (explicitAlternative) {
                // These routes are explicit choices, including during our earpiece hop.
                userLeftBluetooth = true;
                endOperation();
            } else if (leftBluetooth
                    && !commanded
                    && s.bluetoothRouteAvailable
                    && s.route != AudioRoute.UNKNOWN) {
                if (!s.phase.carriesAudio() || s.callCount > 1 || !lastBluetoothUsable) {
                    // A Telecom route label is not proof that SCO connected. The dialer may
                    // show Bluetooth for only a few hundred ms before falling back to phone.
                    // Keep diagnosis active so CALL_NOT_ON_BLUETOOTH can be confirmed.
                } else if (!speakerChosen
                        && !dropForgiven
                        && now - lastRouteChangeAt < REPAIR_GRACE_MS) {
                    // The SCO link we just rebuilt collapsed; repair it instead of giving up.
                    dropForgiven = true;
                    endOperation();
                } else {
                    userLeftBluetooth = true;
                }
            }
            bluetoothSince = -1L;
            usableBluetoothSince = -1L;
        }
        lastRoute = s.route;
    }

    private Step advanceOperation(CallAudioSnapshot s, long now) {
        long age = now - operationSince;
        switch (operation) {
            case REBUILD_LEAVING:
                if (s.route != AudioRoute.BLUETOOTH || age >= LEAVE_TIMEOUT_MS) {
                    begin(Operation.REBUILD_RETURNING, now, operationPreventive);
                    return step(500L, "return to Bluetooth", RepairCommand.ROUTE_TO_BLUETOOTH);
                }
                return step(250L, "waiting for Bluetooth teardown");
            case REBUILD_RETURNING:
                if (s.route == AudioRoute.BLUETOOTH
                        && !Boolean.FALSE.equals(s.scoAudioConnected)) {
                    begin(Operation.VERIFYING_ROUTE, now, operationPreventive);
                    return step(VERIFY_MS, "Bluetooth back, verifying");
                }
                if (age >= RETURN_TIMEOUT_MS) {
                    endOperation();
                    return null;
                }
                if (!returnRequestRepeated && age >= RETURN_TIMEOUT_MS / 2
                        && s.route != AudioRoute.BLUETOOTH) {
                    // Telecom may drop the first request while SCO is still tearing down.
                    lastCommandAt = now;
                    lastRouteChangeAt = now;
                    returnRequestRepeated = true;
                    return step(500L, "repeat Bluetooth request", RepairCommand.ROUTE_TO_BLUETOOTH);
                }
                return step(500L, "waiting for Bluetooth audio");
            case ROUTE_REQUEST:
                if (s.route == AudioRoute.BLUETOOTH
                        && !Boolean.FALSE.equals(s.scoAudioConnected)) {
                    begin(Operation.VERIFYING_ROUTE, now, false);
                    return step(VERIFY_MS, "on Bluetooth, verifying");
                }
                if (age >= ROUTE_REQUEST_TIMEOUT_MS) {
                    directRouteFailed = true;
                    endOperation();
                    return null;
                }
                return step(500L, "waiting for Bluetooth route");
            case VERIFYING_ROUTE:
                if (FaultClassifier.classify(s) != Fault.NONE) {
                    endOperation();
                    return null;
                }
                if (age < VERIFY_MS) return step(VERIFY_MS - age, "verifying");
                endOperation();
                return null;
            case VERIFYING_VOLUME:
                if (age < VERIFY_MS) return step(VERIFY_MS - age, "verifying");
                endOperation();
                return null;
            case NONE:
            default:
                return null;
        }
    }

    private static long confirmWindow(Fault fault) {
        if (fault == Fault.VOICE_SILENCED) return VOLUME_CONFIRM_MS;
        if (fault == Fault.CALL_NOT_ON_BLUETOOTH) return ROUTE_CONFIRM_MS;
        return CONFIRM_MS;
    }

    private static boolean connectionFault(Fault fault) {
        return fault == Fault.CALL_NOT_ON_BLUETOOTH || fault == Fault.SCO_DISCONNECTED
                || fault == Fault.DOWNLINK_NOT_ON_BLUETOOTH;
    }

    private boolean routeOperationActive() {
        return operation != Operation.NONE && operation != Operation.VERIFYING_VOLUME;
    }

    private Step maybePreventiveRebuild(CallAudioSnapshot s, long now) {
        // The window starts with call audio (dialing/answered), not with the first ring.
        long sinceStart = audioSince < 0 ? 0L : now - audioSince;
        long tick = sinceStart < PREVENTIVE_WINDOW_MS ? FAST_TICK_MS : SLOW_TICK_MS;

        boolean wanted =
                preventiveMode == PreventiveRebuildMode.ALWAYS
                        || (preventiveMode == PreventiveRebuildMode.AFTER_PROBLEMS && riskElevated);
        if (!repairEnabled
                || !wanted
                || preventiveDone
                || s.callCount > 1
                || s.route != AudioRoute.BLUETOOTH
                || !s.phase.carriesAudio()
                || sinceStart > PREVENTIVE_WINDOW_MS) {
            return step(tick, "healthy");
        }

        long steady = bluetoothSince < 0 ? 0 : now - bluetoothSince;
        if (steady < STEADY_BLUETOOTH_MS) {
            return step(STEADY_BLUETOOTH_MS - steady, "waiting for steady Bluetooth");
        }

        preventiveDone = true;
        return startRebuild(s, now, true, "preventive rebuild at call start");
    }

    private Step startRebuild(CallAudioSnapshot s, long now, boolean preventive, String note) {
        if (!s.earpieceAvailable) {
            // Without an earpiece there is no safe intermediate route; just re-request Bluetooth.
            begin(Operation.REBUILD_RETURNING, now, preventive);
            return step(500L, note + " (no earpiece)", RepairCommand.ROUTE_TO_BLUETOOTH);
        }
        begin(Operation.REBUILD_LEAVING, now, preventive);
        return step(250L, note, RepairCommand.ROUTE_TO_EARPIECE);
    }

    private boolean takeRouteAttempt() {
        if (routeAttempts >= MAX_ROUTE_ATTEMPTS) return false;
        routeAttempts++;
        return true;
    }

    private Step exhausted() {
        return step(IDLE_TICK_MS, "route attempt budget exhausted");
    }

    private void begin(Operation next, long now, boolean preventive) {
        operation = next;
        operationSince = now;
        operationPreventive = preventive;
        if (next == Operation.REBUILD_RETURNING) returnRequestRepeated = false;
        lastCommandAt = now;
        if (next != Operation.VERIFYING_VOLUME) lastRouteChangeAt = now;
    }

    private void endOperation() {
        operation = Operation.NONE;
        operationPreventive = false;
    }

    private void finish(long now) {
        ended = true;
        endedAt = now;
        endOperation();
    }

    private static Step step(long delay, String note, RepairCommand... commands) {
        List<RepairCommand> list = new ArrayList<>(commands.length);
        Collections.addAll(list, commands);
        return new Step(list, delay, note);
    }
}
