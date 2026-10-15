package de.kaipressmar.a52srepair.telecom;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.telecom.CallAudioState;
import de.kaipressmar.a52srepair.audio.HeadsetMonitor;
import de.kaipressmar.a52srepair.audio.VoiceStream;
import de.kaipressmar.a52srepair.audio.CommunicationDeviceRecovery;
import de.kaipressmar.a52srepair.core.model.CallAudioSnapshot;
import de.kaipressmar.a52srepair.core.model.CallPhase;
import de.kaipressmar.a52srepair.core.repair.CallRepairEngine;
import de.kaipressmar.a52srepair.core.repair.RepairCommand;
import de.kaipressmar.a52srepair.core.repair.ManualRepairStatus;
import de.kaipressmar.a52srepair.core.report.CallReport;
import de.kaipressmar.a52srepair.data.AppSettings;
import de.kaipressmar.a52srepair.data.CallReportRepository;
import de.kaipressmar.a52srepair.diagnostics.DiagnosticLog;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import de.kaipressmar.a52srepair.core.repair.RecoveryBudget;
import de.kaipressmar.a52srepair.core.model.AudioRoute;
import de.kaipressmar.a52srepair.data.RecoveryJournalRepository;

/**
 * Android driver for one {@link CallRepairEngine}: collects snapshots, runs the engine on the
 * main thread and executes its commands through Telecom. Lives only while calls exist.
 */
final class CallSession {
    /** What the session needs from the bound InCallService. */
    interface Host {
        CallAudioState audioState();

        List<Integer> callStates();

        void requestRoute(int telecomRoute);

        default boolean safeToRepair() { return false; }
        default String targetToken() { return null; }
        default String stableAccessoryId() { return null; }
        default String selectedTargetToken() { return null; }
        default void requestLegacyRoute(int telecomRoute) { requestRoute(telecomRoute); }
        default void refreshRoutingState() {}

        default boolean bluetoothTargetAmbiguous() { return false; }
        default android.bluetooth.BluetoothDevice bluetoothDevice() { return null; }
        default String routingDetails() { return "routingDetails=unavailable"; }
        default void observeHeadsets(List<android.bluetooth.BluetoothDevice> devices) {}
    }

    private static final Executor JOURNAL_WRITER = Executors.newSingleThreadExecutor();
    private final Executor writer;
    private final RecoveryJournalRepository journal;
    private final de.kaipressmar.a52srepair.data.RecoveryPolicyRepository policies;
    private String riskScope;
    private final RecoveryBudget budget = new RecoveryBudget();
    private final String generation = UUID.randomUUID().toString();
    private boolean pendingDispatch;
    private boolean expectedPhoneHop;
    private boolean bluetoothIntent;
    private boolean yielded;
    private boolean interrupted;
    private boolean executing;
    private AudioRoute previousRoute = AudioRoute.UNKNOWN;
    private String targetToken;
    private String controlResult = "NOT_REQUESTED";

    private static final long EVENT_DEBOUNCE_MS = 150L;
    private static final long LOG_HEARTBEAT_MS = 30_000L;

    private final Context context;
    private final Host host;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final HeadsetMonitor headset;
    private final VoiceStream voice;
    private final CommunicationDeviceRecovery communication;
    private final CallReportRepository reports;
    private final CallRepairEngine engine;
    private final AppSettings settings;
    private final SharedPreferences preferences;
    private final SharedPreferences.OnSharedPreferenceChangeListener settingsChanged = (prefs, key) -> {
        if (AppSettings.KEY_PROTECTION_ENABLED.equals(key) || AppSettings.KEY_PREVENTIVE_MODE.equals(key)) onEvent();
    };
    private final long startedAtWall;
    private final long startedAtElapsed;
    private String lastLoggedSnapshot = "";
    private String lastLoggedNote = "";
    private long lastLoggedAt;
    private boolean finished;

    private final Runnable tick = this::tick;

    CallSession(Context context, Host host) { this(context, host, JOURNAL_WRITER); }

    CallSession(Context context, Host host, Executor writer) {
        this.context = context.getApplicationContext();
        this.host = host;
        this.writer = writer;
        policies = new de.kaipressmar.a52srepair.data.RecoveryPolicyRepository(this.context);
        journal = new RecoveryJournalRepository(this.context);
        interrupted = journal.interrupted();
        if (interrupted) budget.stop("INTERRUPTED");
        de.kaipressmar.a52srepair.diagnostics.RecoveryEventLog.record(this.context,
                de.kaipressmar.a52srepair.diagnostics.RecoveryEventLog.Event.START, 0, 0);
        headset = new HeadsetMonitor(this.context, this::onEvent);
        voice = new VoiceStream(this.context.getSystemService(AudioManager.class));
        communication = new CommunicationDeviceRecovery(this.context.getSystemService(AudioManager.class));
        reports = new CallReportRepository(this.context);

        settings = new AppSettings(this.context);
        preferences = PreferenceManager.getDefaultSharedPreferences(this.context);
        startedAtWall = System.currentTimeMillis();
        startedAtElapsed = SystemClock.elapsedRealtime();
        boolean riskElevated = false; // Resolve the current accessory before applying AFTER_PROBLEMS.
        engine = new CallRepairEngine(
                settings.protectionEnabled(), settings.preventiveMode(), riskElevated, 0L);
        DiagnosticLog.log(
                this.context,
                "CALL START protection=" + settings.protectionEnabled()
                        + " preventive=" + settings.preventiveMode()
                        + " riskElevated=" + riskElevated
                        + " bootElapsedMs=" + startedAtElapsed + " audioMode=" + voice.mode());
    }

    void start() {
        preferences.registerOnSharedPreferenceChangeListener(settingsChanged);
        headset.open();
        schedule(0L);
    }

    /** Telecom reported a call or audio-route change: re-evaluate soon. */
    void onEvent() {
        if (!finished) schedule(EVENT_DEBOUNCE_MS);
    }

    /** Keep short Bluetooth selections: debouncing can otherwise hide a failed SCO attempt. */
    void onAudioStateChanged(CallAudioState audio) {
        if (!finished) {
            // Inspect every short route pulse, but do not reenter execution from a synchronous callback.
            observeChoice(snapshot(audio));
            if (executing || pendingDispatch) onEvent();
            else tick(snapshot(audio));
        }
    }

    /** Current call is exportable before it is added to the completed-call history. */
    CallReport report() {
        return report(elapsed());
    }

    /** Read-only manual check; never routes audio or consumes a repair attempt. */
    CallAudioSnapshot inspect() {
        if (finished) return null;
        headset.open(); // Also recover observation after Bluetooth permission is granted mid-call.
        CallAudioSnapshot state = snapshot();
        DiagnosticLog.log(context, "CALL manual check " + state);
        return state;
    }

    ManualRepairStatus repairManually() {
        if (finished) return ManualRepairStatus.NO_ACTIVE_CALL;
        if (!host.safeToRepair() || !settings.protectionEnabled()) return ManualRepairStatus.ALTERNATIVE_ROUTE;
        if (!"NONE".equals(budget.refusal(elapsed(), true))) return ManualRepairStatus.BUDGET_EXHAUSTED;
        engine.updatePolicy(settings.protectionEnabled(), settings.preventiveMode());
        headset.open();
        CallAudioSnapshot state = snapshot();
        long now = elapsed();
        // Platform target selection is checked before any intermediate route is requested.
        if (state.phase.carriesAudio() && state.callCount == 1 && state.bluetoothRouteAvailable
                && host.bluetoothTargetAmbiguous()) return ManualRepairStatus.AMBIGUOUS_DEVICE;
        targetToken = host.targetToken();
        bluetoothIntent = true; // Explicit action can establish intent, never reset the shared budget.
        CallRepairEngine.ManualRepair result = engine.requestManualRepair(state, now);
        DiagnosticLog.log(context, "CALL manual repair result=" + result.status);
        if (result.status == ManualRepairStatus.STARTED && !applyStep(state, now, result.step)) {
            return ManualRepairStatus.COMMAND_FAILED;
        }
        return result.status;
    }

    void onRouteRequestFailed() {
        if (finished) return;
        if ("REQUESTED".equals(controlResult) || "ACKNOWLEDGED".equals(controlResult)) controlResult = "REJECTED";
        engine.onRouteRequestFailed(elapsed());
        onEvent();
    }

    void onRequestAcknowledged() {
        if (finished) return;
        controlResult = "ACKNOWLEDGED";
        onEvent();
    }

    boolean allowsRouteFallback() {
        engine.updatePolicy(settings.protectionEnabled(), settings.preventiveMode());
        return false; // Cross-API mutations must never bypass durable request accounting.
    }

    private CallReport report(long now) {
        CallReport r = engine.report(now);
        de.kaipressmar.a52srepair.core.report.CallOutcome outcome = r.outcome;
        if (r.usedBluetooth() && (interrupted || "REJECTED".equals(controlResult)
                || (!"NONE".equals(budget.stoppedReason()) && !yielded))) {
            outcome = de.kaipressmar.a52srepair.core.report.CallOutcome.UNRESOLVED;
        }
        if (yielded) outcome = de.kaipressmar.a52srepair.core.report.CallOutcome.LEFT_BLUETOOTH;
        return new CallReport(startedAtWall, r.durationMs, outcome, r.faults,
                r.routeAttempts, 0, r.preventiveRebuild && budget.dispatchedCount() > 0, r.userLeftBluetooth || yielded, r.manualRepairs,
                budget.reserved(), budget.dispatchedCount(), controlResult,
                interrupted ? "INTERRUPTED" : budget.stoppedReason(), false);
    }

    void interrupt() { interrupted = true; budget.stop("INTERRUPTED"); finish(false); }

    void finish() { finish(true); }

    private void finish(boolean observedEnd) {
        if (finished) return;
        long now = elapsed();
        engine.onSnapshot(snapshot().toBuilder().phase(CallPhase.ENDED).build(), now);
        finished = true;
        preferences.unregisterOnSharedPreferenceChangeListener(settingsChanged);
        handler.removeCallbacks(tick);
        headset.close();
        communication.close();

        CallReport report = report(now);
        de.kaipressmar.a52srepair.diagnostics.RecoveryEventLog.record(context,
                observedEnd ? de.kaipressmar.a52srepair.diagnostics.RecoveryEventLog.Event.END
                        : de.kaipressmar.a52srepair.diagnostics.RecoveryEventLog.Event.INTERRUPTED,
                budget.dispatchedCount(), now);
        writer.execute(() -> reports.add(report));
        // Only observing the actual end closes a lease. Service destruction is not call completion.
        if (observedEnd) writer.execute(() -> journal.close(generation));
        if (report.outcome.problem && !yielded) {
            policies.recordProblem(riskScope, System.currentTimeMillis());
            settings.setIdlePostCallPending(true);
            DiagnosticLog.log(context, "IDLE post-call candidate recorded; execution requires visible app and idle phone");
        }
        DiagnosticLog.log(context, "CALL END " + report);
    }

    private void tick() {
        if (finished) return;
        tick(snapshot());
    }

    private void tick(CallAudioSnapshot snapshot) {
        long now = elapsed();
        observeChoice(snapshot);
        if (riskScope == null) riskScope = policies.scope(host.stableAccessoryId());
        engine.updateRisk(policies.elevated(riskScope, System.currentTimeMillis()));
        boolean allowed = settings.protectionEnabled() && host.safeToRepair() && !yielded
                && !interrupted && "NONE".equals(budget.stoppedReason());
        engine.updatePolicy(allowed, settings.preventiveMode());
        if (budget.firstAt() >= 0 && budget.remainingMs(now) == 0
                && (engine.routingOperationActive() || pendingDispatch)) budget.stop("DEADLINE");
        if (pendingDispatch) { schedule(250L); return; }
        CallRepairEngine.Step step = engine.onSnapshot(snapshot, now);
        communication.maintain(snapshot, now, settings.protectionEnabled() || engine.routingOperationActive());
        applyStep(snapshot, now, step);
    }

    private boolean applyStep(CallAudioSnapshot snapshot, long now, CallRepairEngine.Step step) {
        boolean accepted = true;

        Integer audioMode = voice.mode();
        String state = snapshot + " audioMode=" + (audioMode == null ? "?" : audioMode)
                + " communicationRequest=" + communication.hasRequest() + " " + host.routingDetails();
        state += " " + de.kaipressmar.a52srepair.diagnostics.AudioDiagnostics.capture(
                context.getSystemService(AudioManager.class), host.bluetoothDevice());
        if (!step.commands.isEmpty() || !state.equals(lastLoggedSnapshot)
                || !step.note.equals(lastLoggedNote) || now - lastLoggedAt >= LOG_HEARTBEAT_MS) {
            de.kaipressmar.a52srepair.diagnostics.RecoveryEventLog.observation(context, snapshot,
                    budget.dispatchedCount(), now);
            lastLoggedSnapshot = state;
            lastLoggedNote = step.note;
            lastLoggedAt = now;
            DiagnosticLog.log(
                    context,
                    "CALL " + state + " -> " + step.note
                            + (step.commands.isEmpty() ? "" : " " + step.commands));
        }

        for (RepairCommand command : step.commands) {
            try {
                if (command == RepairCommand.REFRESH_BLUETOOTH_OBSERVATIONS) execute(command);
                else if (command == RepairCommand.RESTORE_VOICE_VOLUME || command == RepairCommand.TRY_COMMUNICATION_DEVICE) {
                    // No implicit volume changes or AudioPolicy ownership experiments during cellular calls.
                    DiagnosticLog.log(context, "CALL optional mutation suppressed command=" + command);
                } else accepted &= dispatch(command);
            } catch (RuntimeException e) {
                accepted = false;
                // Binder/permission failures must not kill call observation or reset budgets.
                DiagnosticLog.log(context, "CALL command " + command + " failed: " + e.getClass().getSimpleName());
                if (command != RepairCommand.RESTORE_VOICE_VOLUME
                        && command != RepairCommand.REFRESH_BLUETOOTH_OBSERVATIONS
                        && command != RepairCommand.TRY_COMMUNICATION_DEVICE) onRouteRequestFailed();
                break; // Never continue a compound rebuild after its first command failed.
            }
        }
        schedule(step.nextCheckInMs);
        return accepted;
    }

    private void observeChoice(CallAudioSnapshot state) {
        String selected = host.selectedTargetToken();
        if (state.route == AudioRoute.BLUETOOTH) {
            bluetoothIntent = true;
            if (targetToken == null) targetToken = selected == null ? host.targetToken() : selected;
            else if (selected != null && !targetToken.equals(selected)) yielded = true;
            expectedPhoneHop = false;
        } else if (state.route == AudioRoute.SPEAKER || state.route == AudioRoute.WIRED_HEADSET
                || state.route == AudioRoute.STREAMING
                || (previousRoute == AudioRoute.BLUETOOTH && state.route == AudioRoute.EARPIECE && !expectedPhoneHop)) {
            yielded = true;
        }
        previousRoute = state.route;
        if (yielded) budget.stop("USER_CHOICE");
        if (budget.reserved() > 0 && (!state.repairAllowed || state.callCount != 1
                || state.phase != CallPhase.ACTIVE)) budget.stop("INELIGIBLE");
    }

    private boolean eligible(CallAudioSnapshot state, long now) {
        observeChoice(state);
        String offered = host.targetToken();
        return !finished && settings.protectionEnabled() && host.safeToRepair() && state.repairAllowed
                && state.phase == CallPhase.ACTIVE && state.callCount == 1 && state.bluetoothRouteAvailable
                && !host.bluetoothTargetAmbiguous() && bluetoothIntent && !yielded && !interrupted
                && targetToken != null && targetToken.equals(offered)
                && (state.route == AudioRoute.BLUETOOTH || state.route == AudioRoute.EARPIECE)
                && "NONE".equals(budget.stoppedReason()) && budget.remainingMs(now) > 0;
    }

    private boolean dispatch(RepairCommand command) {
        long now = elapsed();
        CallAudioSnapshot fresh = snapshot();
        boolean leaving = command == RepairCommand.ROUTE_TO_EARPIECE
                || command == RepairCommand.ROUTE_TO_EARPIECE_LEGACY;
        if (!eligible(fresh, now) || (leaving && !fresh.earpieceAvailable)) {
            controlResult = "BLOCKED";
            DiagnosticLog.log(context, "CALL mutation blocked: eligibility, intent or ambiguous Bluetooth endpoint");
            onRouteRequestFailed();
            return false;
        }
        if (!budget.reserve(now, leaving)) {
            controlResult = "BLOCKED";
            onRouteRequestFailed();
            return false;
        }
        pendingDispatch = true;
        int reservation = budget.reserved();
        long firstAt = budget.firstAt();
        writer.execute(() -> {
            boolean saved = journal.reserve(generation, reservation, firstAt);
            handler.post(() -> {
                pendingDispatch = false;
                if (finished) return;
                if (!saved) {
                    budget.stop("STORAGE_FAILED"); controlResult = "STORAGE_FAILED";
                    onRouteRequestFailed(); return;
                }
                if (budget.remainingMs(elapsed()) == 0) budget.stop("DEADLINE");
                if (!eligible(snapshot(), elapsed())) {
                    controlResult = "CANCELLED";
                    onRouteRequestFailed(); return;
                }
                executing = true;
                try {
                    if (leaving) expectedPhoneHop = true;
                    budget.dispatched();
                    controlResult = "REQUESTED";
                    DiagnosticLog.log(context, "RECOVERY dispatch sequence=" + reservation + " command=" + command
                            + " remainingMs=" + budget.remainingMs(elapsed()));
                    de.kaipressmar.a52srepair.diagnostics.RecoveryEventLog.record(context,
                            de.kaipressmar.a52srepair.diagnostics.RecoveryEventLog.Event.DISPATCH,
                            reservation, elapsed());
                    execute(command);
                } catch (RuntimeException failure) {
                    controlResult = "REJECTED";
                    onRouteRequestFailed();
                } finally { executing = false; onEvent(); }
            });
        });
        return true;
    }

    private void execute(RepairCommand command) {
        if (command != RepairCommand.RESTORE_VOICE_VOLUME
                && command != RepairCommand.REFRESH_BLUETOOTH_OBSERVATIONS && host.bluetoothTargetAmbiguous()) {
            DiagnosticLog.log(context, "CALL route repair deferred: ambiguous Bluetooth endpoint");
            throw new IllegalStateException("No unambiguous Bluetooth endpoint");
        }
        switch (command) {
            case ROUTE_TO_BLUETOOTH:
                host.requestRoute(CallAudioState.ROUTE_BLUETOOTH);
                break;
            case ROUTE_TO_EARPIECE:
                host.requestRoute(CallAudioState.ROUTE_EARPIECE);
                break;
            case ROUTE_TO_BLUETOOTH_LEGACY:
                host.requestLegacyRoute(CallAudioState.ROUTE_BLUETOOTH);
                break;
            case ROUTE_TO_EARPIECE_LEGACY:
                host.requestLegacyRoute(CallAudioState.ROUTE_EARPIECE);
                break;
            case REFRESH_BLUETOOTH_OBSERVATIONS:
                host.refreshRoutingState();
                headset.refresh();
                DiagnosticLog.log(context, "CALL observation proxy refreshed; system services not reset");
                break;
            case TRY_COMMUNICATION_DEVICE:
            case RESTORE_VOICE_VOLUME:
                // Kept in the core command vocabulary for legacy decoding and off-call experiments.
                // Cellular call execution never changes AudioPolicy ownership or user volume here.
                break;
        }
    }

    @SuppressWarnings("deprecation") // CallAudioState still reflects Telecom routing on API 34-36.
    private CallAudioSnapshot snapshot() {
        return snapshot(host.audioState());
    }

    @SuppressWarnings("deprecation")
    private CallAudioSnapshot snapshot(CallAudioState audio) {
        // Retry failed profile acquisition and recover a mid-call permission grant automatically.
        if (!finished) headset.open();
        host.observeHeadsets(headset.connectedDevices());
        List<Integer> states = host.callStates();
        VoiceStream.Evidence evidence = voice.evidence();
        boolean ambiguous = host.bluetoothTargetAmbiguous();
        // Our temporary AudioPolicy selection can affect predicted attribute routing. It must
        // not manufacture positive recovery evidence for a still-broken cellular call.
        Boolean voiceEvidence = null; // Attribute predictions are logged separately, not health proof.
        CallAudioSnapshot.Builder builder =
                CallAudioSnapshot.builder()
                        .phase(TelecomMapping.dominantPhase(states))
                        .callCount(states.size())
                        .repairAllowed(host.safeToRepair())
                        .hfpConnected(headset.hfpConnected())
                        .scoAudioConnected(!ambiguous && evidence.requiresSco && host.bluetoothDevice() != null
                                ? headset.scoAudioConnected(host.bluetoothDevice()) : null)
                        .voiceOnBluetooth(voiceEvidence)
                        .voiceVolume(voice.volume(), voice.maxVolume())
                        .voiceMuted(voice.muted());
        if (audio != null) {
            int mask = audio.getSupportedRouteMask();
            builder.microphoneMuted(audio.isMuted()).route(TelecomMapping.route(audio.getRoute()))
                    .bluetoothRouteAvailable((mask & CallAudioState.ROUTE_BLUETOOTH) != 0)
                    .earpieceAvailable((mask & CallAudioState.ROUTE_EARPIECE) != 0);
        }
        return builder.build();
    }

    private void schedule(long delayMs) {
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, Math.max(0L, Math.min(delayMs, communication.nextCleanupInMs(elapsed()))));
    }

    private long elapsed() {
        return SystemClock.elapsedRealtime() - startedAtElapsed;
    }
}
