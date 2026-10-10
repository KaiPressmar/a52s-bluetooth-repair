package de.kaipressmar.a52srepair.telecom;

import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.telecom.CallAudioState;
import de.kaipressmar.a52srepair.audio.HeadsetMonitor;
import de.kaipressmar.a52srepair.audio.VoiceStream;
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

        default boolean bluetoothTargetAmbiguous() { return false; }
    }

    private static final long EVENT_DEBOUNCE_MS = 150L;
    private static final long LOG_HEARTBEAT_MS = 30_000L;

    private final Context context;
    private final Host host;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final HeadsetMonitor headset;
    private final VoiceStream voice;
    private final CallReportRepository reports;
    private final CallRepairEngine engine;
    private final long startedAtWall;
    private final long startedAtElapsed;
    private String lastLoggedSnapshot = "";
    private String lastLoggedNote = "";
    private long lastLoggedAt;
    private boolean finished;

    private final Runnable tick = this::tick;

    CallSession(Context context, Host host) {
        this.context = context.getApplicationContext();
        this.host = host;
        headset = new HeadsetMonitor(this.context, this::onEvent);
        voice = new VoiceStream(this.context.getSystemService(AudioManager.class));
        reports = new CallReportRepository(this.context);

        AppSettings settings = new AppSettings(this.context);
        startedAtWall = System.currentTimeMillis();
        startedAtElapsed = SystemClock.elapsedRealtime();
        boolean riskElevated = reports.history().riskElevated(startedAtWall);
        engine = new CallRepairEngine(
                settings.protectionEnabled(), settings.preventiveMode(), riskElevated, 0L);
        DiagnosticLog.log(
                this.context,
                "CALL START protection=" + settings.protectionEnabled()
                        + " preventive=" + settings.preventiveMode()
                        + " riskElevated=" + riskElevated);
    }

    void start() {
        headset.open();
        schedule(0L);
    }

    /** Telecom reported a call or audio-route change: re-evaluate soon. */
    void onEvent() {
        if (!finished) schedule(EVENT_DEBOUNCE_MS);
    }

    /** Keep short Bluetooth selections: debouncing can otherwise hide a failed SCO attempt. */
    void onAudioStateChanged(CallAudioState audio) {
        if (!finished) tick(snapshot(audio));
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
        headset.open();
        CallAudioSnapshot state = snapshot();
        long now = elapsed();
        // Platform target selection is checked before any intermediate route is requested.
        if (state.phase.carriesAudio() && state.callCount == 1 && state.bluetoothRouteAvailable
                && host.bluetoothTargetAmbiguous()) return ManualRepairStatus.AMBIGUOUS_DEVICE;
        CallRepairEngine.ManualRepair result = engine.requestManualRepair(state, now);
        DiagnosticLog.log(context, "CALL manual repair result=" + result.status);
        if (result.status == ManualRepairStatus.STARTED && !applyStep(state, now, result.step)) {
            return ManualRepairStatus.COMMAND_FAILED;
        }
        return result.status;
    }

    void onRouteRequestFailed() {
        if (finished) return;
        engine.onRouteRequestFailed(elapsed());
        onEvent();
    }

    private CallReport report(long now) {
        CallReport r = engine.report(now);
        return new CallReport(startedAtWall, r.durationMs, r.outcome, r.faults,
                r.routeAttempts, r.volumeRestores, r.preventiveRebuild, r.userLeftBluetooth, r.manualRepairs);
    }

    void finish() {
        if (finished) return;
        long now = elapsed();
        engine.onSnapshot(snapshot().toBuilder().phase(CallPhase.ENDED).build(), now);
        finished = true;
        handler.removeCallbacks(tick);
        headset.close();

        CallReport report = report(now);
        reports.add(report);
        DiagnosticLog.log(context, "CALL END " + report);
    }

    private void tick() {
        if (finished) return;
        tick(snapshot());
    }

    private void tick(CallAudioSnapshot snapshot) {
        long now = elapsed();
        CallRepairEngine.Step step = engine.onSnapshot(snapshot, now);
        applyStep(snapshot, now, step);
    }

    private boolean applyStep(CallAudioSnapshot snapshot, long now, CallRepairEngine.Step step) {
        boolean accepted = true;

        String state = snapshot.toString();
        if (!step.commands.isEmpty() || !state.equals(lastLoggedSnapshot)
                || !step.note.equals(lastLoggedNote) || now - lastLoggedAt >= LOG_HEARTBEAT_MS) {
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
                execute(command);
            } catch (RuntimeException e) {
                accepted = false;
                // Binder/permission failures must not kill call observation or reset budgets.
                DiagnosticLog.log(context, "CALL command " + command + " failed: " + e.getClass().getSimpleName());
                if (command != RepairCommand.RESTORE_VOICE_VOLUME) onRouteRequestFailed();
            }
        }
        schedule(step.nextCheckInMs);
        return accepted;
    }

    private void execute(RepairCommand command) {
        switch (command) {
            case ROUTE_TO_BLUETOOTH:
                host.requestRoute(CallAudioState.ROUTE_BLUETOOTH);
                break;
            case ROUTE_TO_EARPIECE:
                host.requestRoute(CallAudioState.ROUTE_WIRED_OR_EARPIECE);
                break;
            case RESTORE_VOICE_VOLUME:
                int volume = voice.restoreAudibleVolume();
                DiagnosticLog.log(context, "CALL voice volume restored to " + volume);
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
        List<Integer> states = host.callStates();
        VoiceStream.Evidence evidence = voice.evidence();
        CallAudioSnapshot.Builder builder =
                CallAudioSnapshot.builder()
                        .phase(TelecomMapping.dominantPhase(states))
                        .callCount(states.size())
                        .hfpConnected(headset.hfpConnected())
                        .scoAudioConnected(evidence.requiresSco ? headset.scoAudioConnected() : null)
                        .voiceOnBluetooth(evidence.onBluetooth)
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
        handler.postDelayed(tick, Math.max(0L, delayMs));
    }

    private long elapsed() {
        return SystemClock.elapsedRealtime() - startedAtElapsed;
    }
}
