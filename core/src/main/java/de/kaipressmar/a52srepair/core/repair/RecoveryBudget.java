package de.kaipressmar.a52srepair.core.repair;

/** Dispatch budget shared by automatic, preventive and manual call recovery. */
public final class RecoveryBudget {
    public static final int MAX_REQUESTS = 4;
    public static final long DEADLINE_MS = 15_000L;
    private int reserved;
    private int dispatched;
    private int rebuilds;
    private long firstAt = -1L;
    private String stopped = "NONE";

    public String refusal(long now, boolean leaving) {
        if (!"NONE".equals(stopped)) return stopped;
        if (firstAt >= 0 && (now < firstAt || now - firstAt >= DEADLINE_MS)) return "DEADLINE";
        if (reserved >= MAX_REQUESTS) return "REQUEST_BUDGET";
        if (leaving && (rebuilds > 0 || MAX_REQUESTS - reserved < 2)) return "REBUILD_BUDGET";
        return "NONE";
    }

    public boolean reserve(long now, boolean leaving) {
        String reason = refusal(now, leaving);
        if (!"NONE".equals(reason)) { stop(reason); return false; }
        if (firstAt < 0) firstAt = now;
        reserved++;
        if (leaving) rebuilds++;
        return true;
    }

    public void dispatched() { dispatched++; }
    public void stop(String reason) { if ("NONE".equals(stopped)) stopped = reason; }
    public int reserved() { return reserved; }
    public int dispatchedCount() { return dispatched; }
    public long firstAt() { return firstAt; }
    public String stoppedReason() { return stopped; }
    public long remainingMs(long now) {
        return firstAt < 0 ? DEADLINE_MS : Math.max(0L, DEADLINE_MS - (now - firstAt));
    }
}
