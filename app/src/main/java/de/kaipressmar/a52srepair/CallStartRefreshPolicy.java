package de.kaipressmar.a52srepair;

/**
 * Hands-free mitigation for one-way call audio that no public API can observe.
 *
 * While driving, the user can only take the call through the car; they cannot tap a repair
 * button. If the A52s vendor HAL plays a silent downlink although SCO, routing and volume all look
 * correct, the only fully automatic remedy is to rebuild SCO once at the start of every Bluetooth
 * call (route bounce earpiece → SCO, ≈1 s gap). This policy decides when that preventive bounce
 * runs.
 */
final class CallStartRefreshPolicy {
    /** Let Telecom finish its own SCO setup before bouncing it. */
    static final long SETTLE_AFTER_OFFHOOK_MS = 2_000L;
    /** Later than this the conversation is under way; a bounce would only interrupt it. */
    static final long LATEST_AFTER_OFFHOOK_MS = 20_000L;

    private CallStartRefreshPolicy() {}

    static boolean shouldRefresh(
            boolean enabled,
            String deviceProfileKey,
            BluetoothHealth health,
            FailureSignature signature,
            Boolean hfpAudioTransportConnected,
            boolean alreadyRefreshedThisCall,
            long offhookAtMillis,
            long nowMillis) {
        if (!enabled
                || !"a52s".equalsIgnoreCase(deviceProfileKey)
                || alreadyRefreshedThisCall
                || health == null
                || signature == null
                || offhookAtMillis <= 0L) {
            return false;
        }

        long sinceOffhook = nowMillis - offhookAtMillis;
        if (sinceOffhook < SETTLE_AFTER_OFFHOOK_MS || sinceOffhook > LATEST_AFTER_OFFHOOK_MS) {
            return false;
        }

        // Only a call that looks healthy: detectable faults use their own confirmed repair path.
        return health.inCommunication
                && health.scoSelected
                && !health.speakerphoneOn
                && signature.kind == FailureSignature.Kind.HEALTHY_CALL
                && !Boolean.FALSE.equals(hfpAudioTransportConnected);
    }
}
