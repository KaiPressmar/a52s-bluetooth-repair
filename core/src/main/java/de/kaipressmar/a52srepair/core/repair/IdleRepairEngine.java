package de.kaipressmar.a52srepair.core.repair;

/** Bounded off-call channel exercise. A tested link is NOT proof of a repaired cellular call. */
public final class IdleRepairEngine {
    public enum Method { MODERN, DUPLEX, LEGACY_SCO, VOICE_RECOGNITION }
    public enum Result { RUNNING, CHANNEL_TESTED, UNRESOLVED, BLOCKED, CANCELLED, CLEANUP_FAILED }
    public interface Port {
        /** null means safe; unavailable observations must return a blocking reason. */
        String blockedReason();
        boolean targetReady();
        Boolean scoConnected();
        boolean supports(Method method);
        boolean start(Method method);
        /** Additional stream evidence for duplex; SCO alone must not pass that experiment. */
        default boolean transportReady() { return true; }
        default String transportFailure() { return null; }
        /** Release only requests owned by this operation; false prevents further attempts. */
        boolean release();
        void log(String event);
    }
    public static final long DISCOVERY_MS = 5_000L;
    public static final long CONNECT_MS = 10_000L;
    public static final long RELEASE_MS = 5_000L;
    public static final long STABLE_MS = 750L;
    public static final long MAX_MS = 70_000L;
    public static final long DUPLEX_MAX_MS = 90_000L;
    private final Port port;
    private final boolean voiceEnabled;
    private final boolean duplexEnabled;
    private final long started;
    private long deadline, stableSince = -1L, offSince = -1L;
    private int attempt = -1;
    private boolean discovering = true, releasing, tested;
    private Result result = Result.RUNNING;
    public IdleRepairEngine(Port port, boolean voiceEnabled, long now) {
        this(port, voiceEnabled, false, now);
    }
    public IdleRepairEngine(Port port, boolean voiceEnabled, boolean duplexEnabled, long now) {
        this.port = port;
        this.voiceEnabled = voiceEnabled;
        this.duplexEnabled = duplexEnabled;
        started = now;
        deadline = now + DISCOVERY_MS;
        port.log("BEGIN voiceFallback=" + voiceEnabled + " duplex=" + duplexEnabled);
    }
    public Result result() { return result; }
    public void tick(long now) {
        if (result != Result.RUNNING) return;
        String block = port.blockedReason();
        if (block != null) { finish(Result.BLOCKED, block); return; }
        if (now - started >= (duplexEnabled ? DUPLEX_MAX_MS : MAX_MS)) { finish(Result.UNRESOLVED, "operation timeout"); return; }
        Boolean sco = port.scoConnected();
        if (discovering) {
            if (Boolean.TRUE.equals(sco)) { finish(Result.BLOCKED, "audio already occupied"); return; }
            if (port.targetReady() && Boolean.FALSE.equals(sco)) {
                discovering = false;
                next(now);
            } else if (now >= deadline) finish(Result.BLOCKED, "target/SCO not observable");
            return;
        }
        if (!port.targetReady()) { finish(Result.BLOCKED, "target lost or ambiguous"); return; }
        if (releasing) {
            if (!port.release()) {
                if (now >= deadline) finish(Result.CLEANUP_FAILED, "request release failed");
                return;
            }
            if (Boolean.FALSE.equals(sco)) {
                if (offSince < 0L) offSince = now;
                if (now - offSince >= STABLE_MS) {
                    if (tested) finish(Result.CHANNEL_TESTED, "SCO stable on then off; next call unverified");
                    else { releasing = false; next(now); }
                }
            } else offSince = -1L;
            if (result == Result.RUNNING && releasing && now >= deadline) {
                finish(Result.CLEANUP_FAILED, "SCO teardown not confirmed");
            }
            return;
        }
        String failure = port.transportFailure();
        if (failure != null) { port.log("TRANSPORT_FAILED method=" + method() + " reason=" + failure); release(now); return; }
        if (Boolean.TRUE.equals(sco) && port.transportReady()) {
            if (stableSince < 0L) stableSince = now;
            if (now - stableSince >= STABLE_MS) {
                tested = true;
                port.log("LINK_CONFIRMED method=" + method() + " elapsedMs=" + (now - started));
                release(now);
                return;
            }
        } else stableSince = -1L;
        if (now >= deadline) {
            port.log("CONNECT_TIMEOUT method=" + method() + " sco=" + sco);
            release(now);
        }
    }
    private Method method() {
        return attempt < 2 ? Method.MODERN : attempt == 2 ? Method.DUPLEX
                : attempt == 3 ? Method.LEGACY_SCO : Method.VOICE_RECOGNITION;
    }
    private void next(long now) {
        while (++attempt < 5) {
            Method method = method();
            if ((method == Method.VOICE_RECOGNITION && !voiceEnabled)
                    || (method == Method.DUPLEX && !duplexEnabled) || !port.supports(method)) {
                port.log("SKIP method=" + method + " unsupported/disabled");
                continue;
            }
            // No callback can bypass the safety check immediately before a mutation.
            String block = port.blockedReason();
            if (block != null) { finish(Result.BLOCKED, block); return; }
            stableSince = -1L;
            deadline = now + CONNECT_MS;
            boolean accepted = port.start(method);
            port.log("REQUEST method=" + method + " attempt=" + (attempt + 1) + " accepted=" + accepted);
            if (!accepted) release(now);
            return;
        }
        finish(Result.UNRESOLVED, "all eligible methods exhausted");
    }
    private void release(long now) {
        releasing = true;
        offSince = -1L;
        deadline = now + RELEASE_MS;
        port.log("RELEASE method=" + method() + " requestCleared=" + port.release());
    }
    public void cancel(String reason) {
        if (result == Result.RUNNING) finish(Result.CANCELLED, reason);
    }
    private void finish(Result outcome, String reason) {
        boolean cleared = port.release();
        result = cleared ? outcome : Result.CLEANUP_FAILED;
        port.log("END result=" + result + " requestsCleared=" + cleared + " reason=" + reason);
    }
}
