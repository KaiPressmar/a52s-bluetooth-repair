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
        headset = new HeadsetMonitor(this.context);
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

    private CallReport report(long now) {
        CallReport r = engine.report(now);
        return new CallReport(startedAtWall, r.durationMs, r.outcome, r.faults,
                r.routeAttempts, r.volumeRestores, r.preventiveRebuild, r.userLeftBluetooth);
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

        for (RepairCommand command : step.commands) execute(command);
        schedule(step.nextCheckInMs);
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
        List<Integer> states = host.callStates();
        CallAudioSnapshot.Builder builder =
                CallAudioSnapshot.builder()
                        .phase(TelecomMapping.dominantPhase(states))
                        .callCount(states.size())
                        .hfpConnected(headset.hfpConnected())
                        .scoAudioConnected(headset.scoAudioConnected())
                        .voiceOnBluetooth(voice.playsOnBluetooth())
                        .voiceVolume(voice.volume(), voice.maxVolume())
                        .voiceMuted(voice.muted());
        if (audio != null) {
            int mask = audio.getSupportedRouteMask();
            builder.route(TelecomMapping.route(audio.getRoute()))
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
