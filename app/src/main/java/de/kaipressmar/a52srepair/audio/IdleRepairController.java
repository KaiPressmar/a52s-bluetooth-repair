package de.kaipressmar.a52srepair.audio;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import de.kaipressmar.a52srepair.core.repair.IdleRepairEngine;
import de.kaipressmar.a52srepair.data.AppSettings;
import de.kaipressmar.a52srepair.diagnostics.DiagnosticLog;
import java.lang.ref.WeakReference;

/** Visible-activity lifetime; no hidden background audio, foreground service or microphone use. */
public final class IdleRepairController {
    public static final long MANUAL_COOLDOWN_MS = 30_000L;
    public static final long AUTO_COOLDOWN_MS = 10 * 60_000L;
    private static WeakReference<IdleRepairController> current = new WeakReference<>(null);
    private final Context context;
    private final AppSettings settings;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable changed;
    interface PortFactory { IdleAudioPort create(Runnable changed, String operation); }
    private final PortFactory factory;
    private IdleAudioPort port;
    private IdleRepairEngine engine;
    private boolean visible;
    private boolean automaticOperation;
    private boolean operationVoiceEnabled;
    private boolean operationDuplexEnabled;
    private String observedTarget = "";
    private int cleanupRetries;
    private final Runnable tick = this::tick;
    private final Runnable retryCleanup = this::retryCleanup;

    public IdleRepairController(Context context, Runnable changed) {
        this(context, changed, (event, operation) -> new IdleAudioPort(context, event, operation));
    }
    IdleRepairController(Context context, Runnable changed, PortFactory factory) {
        this.context = context.getApplicationContext();
        this.changed = changed;
        this.factory = factory;
        settings = new AppSettings(this.context);
    }
    public void resume() {
        visible = true;
        current = new WeakReference<>(this);
        handler.post(tick);
    }
    public void pause() {
        visible = false;
        handler.removeCallbacks(tick);
        cancel("activity no longer visible");
        if (port != null && engine == null) { port.closeObserver(); port = null; }
        if (current.get() == this) current.clear();
    }
    public static void callStarted() {
        IdleRepairController controller = current.get();
        if (controller != null) controller.cancel("Telecom call added");
    }
    public boolean running() { return engine != null && engine.result() == IdleRepairEngine.Result.RUNNING; }
    /** null means started; a stable machine reason is also exported to diagnostics. */
    public String startManually() { return start("manual", false); }
    private String start(String trigger, boolean automatic) {
        if (!visible) return "NOT_VISIBLE";
        if (engine != null) return "BUSY";
        long now = SystemClock.elapsedRealtime();
        if (settings.idleCooldown(now, automatic ? AUTO_COOLDOWN_MS : MANUAL_COOLDOWN_MS)) return "COOLDOWN";
        if (port != null) port.closeObserver();
        port = factory.create(() -> handler.post(tick), Long.toString(now));
        String block = port.blockedReason();
        if (block != null) {
            settings.setIdleResult("BLOCKED: " + block);
            DiagnosticLog.log(context, "IDLE refused trigger=" + trigger + " reason=" + block);
            port.closeObserver(); port = null;
            changed.run();
            return "BLOCKED";
        }
        port.open();
        settings.markIdleAttempt(now);
        settings.setIdleResult("RUNNING");
        port.log("TRIGGER source=" + trigger + " pendingPostCall=" + settings.idlePostCallPending());
        settings.setIdlePostCallPending(false);
        automaticOperation = automatic;
        operationVoiceEnabled = settings.idleVoiceFallback();
        // Microphone test is never automatic, even with a previously granted permission.
        operationDuplexEnabled = !automatic && settings.idleDuplex();
        engine = new IdleRepairEngine(port, operationVoiceEnabled, operationDuplexEnabled, now);
        cleanupRetries = 0;
        handler.removeCallbacks(tick);
        handler.post(tick);
        changed.run();
        return null;
    }
    private void tick() {
        handler.removeCallbacks(tick);
        if (!visible) return;
        if (running()) {
            if ((automaticOperation && (!settings.idleAuto() || !settings.protectionEnabled()))
                    || (operationVoiceEnabled && !settings.idleVoiceFallback())
                    || (operationDuplexEnabled && !settings.idleDuplex())) {
                cancel("operation disabled in settings");
                return;
            }
            port.observe();
            engine.tick(SystemClock.elapsedRealtime());
            if (!running()) complete();
            else handler.postDelayed(tick, 250L);
            return;
        }
        if (engine != null) return; // Cleanup failures may not be followed by new requests.
        if (settings.idleAuto() && settings.protectionEnabled()) {
            if (port == null) { port = factory.create(() -> handler.post(tick), "watch"); port.open(); }
            // A sole connected HFP target is deliberately required by the operation too.
            boolean ready = port.targetReady();
            if (!ready) { observedTarget = ""; port.resetIdleObservationTarget(); }
            else if (!"present".equals(observedTarget) || settings.idlePostCallPending()) {
                if (port.blockedReason() == null && !settings.idleCooldown(SystemClock.elapsedRealtime(), AUTO_COOLDOWN_MS)) {
                    observedTarget = "present";
                    if (start(settings.idlePostCallPending() ? "after failed call" : "connected while visible", true) == null) return;
                }
            }
        } else if (port != null) { port.closeObserver(); port = null; observedTarget = ""; }
        handler.postDelayed(tick, 1_000L);
    }
    public void cancel(String reason) {
        if (!running()) return;
        engine.cancel(reason);
        complete();
    }
    private void complete() {
        settings.setIdleResult(engine.result().name());
        changed.run();
        if (engine.result() == IdleRepairEngine.Result.CLEANUP_FAILED) {
            handler.removeCallbacks(retryCleanup);
            handler.postDelayed(retryCleanup, 1_000L);
        } else {
            port.closeObserver(); port = null; engine = null;
            if (visible) handler.postDelayed(tick, 1_000L);
        }
    }
    private void retryCleanup() {
        if (port == null) return;
        boolean cleared = port.release();
        port.log("CLEANUP_RETRY attempt=" + (++cleanupRetries) + " requestsCleared=" + cleared);
        if (!cleared && cleanupRetries < 3) handler.postDelayed(retryCleanup, 1_000L);
        else {
            port.closeObserver(); port = null;
            // Failed cleanup blocks this controller for its lifetime, including pause/resume.
            // Retain the terminal engine: clearing a Binder request does not prove teardown.
        }
    }
}
