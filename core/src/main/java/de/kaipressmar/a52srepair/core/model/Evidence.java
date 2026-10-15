package de.kaipressmar.a52srepair.core.model;

/** An observation that never turns missing or stale data into a negative fact. */
public final class Evidence<T> {
    public enum Status { KNOWN, UNKNOWN, UNAVAILABLE }
    public final T value;
    public final Status status;
    public final long observedAt;
    private Evidence(T value, Status status, long observedAt) {
        this.value = value; this.status = status; this.observedAt = observedAt;
    }
    public static <T> Evidence<T> known(T value, long now) {
        if (value == null) throw new IllegalArgumentException("Known evidence requires a value");
        return new Evidence<>(value, Status.KNOWN, now);
    }
    public static <T> Evidence<T> unknown(long now) { return new Evidence<>(null, Status.UNKNOWN, now); }
    public static <T> Evidence<T> unavailable(long now) { return new Evidence<>(null, Status.UNAVAILABLE, now); }
    public boolean fresh(long now, long maxAge) {
        return status == Status.KNOWN && now >= observedAt && now - observedAt <= maxAge;
    }
}
