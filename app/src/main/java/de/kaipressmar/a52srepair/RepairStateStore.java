package de.kaipressmar.a52srepair;

import android.content.Context;
import android.content.SharedPreferences;

final class RepairStateStore {
    private static final String PREFS = "repair-state";
    private static final String KEY_MONITORING = "monitoring";
    private static final String KEY_AUTO_REPAIR = "auto_repair";
    private static final String KEY_LAST_STATE = "last_state";
    private static final String KEY_LAST_SUMMARY = "last_summary";
    private static final String KEY_LAST_DETAIL = "last_detail";
    private static final String KEY_LAST_CHECK = "last_check";
    private static final String KEY_LAST_REPAIR = "last_repair";
    private static final String KEY_REPAIR_COUNT = "repair_count";
    private static final String KEY_CONSECUTIVE_SUSPECT = "consecutive_suspect";
    private static final String KEY_CONSECUTIVE_DEGRADED = "consecutive_degraded";
    private static final String KEY_ROUTE_OWNED = "route_owned";
    private static final String KEY_PREFLIGHT_ENABLED = "preflight_enabled";
    private static final String KEY_LAST_HFP_READY = "last_hfp_ready";
    private static final String KEY_LAST_PREFLIGHT = "last_preflight";
    private static final String KEY_HFP_AUDIO_TRANSPORT = "hfp_audio_transport";

    private RepairStateStore() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean monitoringEnabled(Context c) {
        return prefs(c).getBoolean(KEY_MONITORING, false);
    }

    static void setMonitoringEnabled(Context c, boolean enabled) {
        prefs(c).edit().putBoolean(KEY_MONITORING, enabled).apply();
    }

    static boolean autoRepairEnabled(Context c) {
        return prefs(c).getBoolean(KEY_AUTO_REPAIR, true);
    }

    static void setAutoRepairEnabled(Context c, boolean enabled) {
        prefs(c).edit().putBoolean(KEY_AUTO_REPAIR, enabled).apply();
    }

    static boolean preflightEnabled(Context c) {
        boolean defaultEnabled =
                "a52s".equalsIgnoreCase(c.getString(R.string.device_profile_key));
        return prefs(c).getBoolean(KEY_PREFLIGHT_ENABLED, defaultEnabled);
    }

    static void setPreflightEnabled(Context c, boolean enabled) {
        prefs(c).edit().putBoolean(KEY_PREFLIGHT_ENABLED, enabled).apply();
    }

    static void markHfpReady(Context c, long now) {
        prefs(c).edit().putLong(KEY_LAST_HFP_READY, now).apply();
    }

    static boolean hfpRecentlyReady(Context c, long now, long maxAgeMs) {
        long at = prefs(c).getLong(KEY_LAST_HFP_READY, 0L);
        return at > 0L && now >= at && now - at <= maxAgeMs;
    }

    static long lastPreflightAt(Context c) {
        return prefs(c).getLong(KEY_LAST_PREFLIGHT, 0L);
    }

    static void markPreflight(Context c, long now) {
        prefs(c).edit().putLong(KEY_LAST_PREFLIGHT, now).apply();
    }

    static void saveHealth(Context c, BluetoothHealth health) {
        prefs(c)
                .edit()
                .putString(KEY_LAST_STATE, health.state.name())
                .putString(KEY_LAST_SUMMARY, health.summary)
                .putString(KEY_LAST_DETAIL, health.detail)
                .putLong(KEY_LAST_CHECK, System.currentTimeMillis())
                .apply();
    }

    static String lastState(Context c) {
        return prefs(c).getString(KEY_LAST_STATE, BluetoothHealth.State.IDLE.name());
    }

    static String lastSummary(Context c) {
        return prefs(c).getString(KEY_LAST_SUMMARY, "Noch keine Hintergrundprüfung");
    }

    static String lastDetail(Context c) {
        return prefs(c).getString(KEY_LAST_DETAIL, "");
    }

    static long lastCheckAt(Context c) {
        return prefs(c).getLong(KEY_LAST_CHECK, 0L);
    }

    static long lastRepairAt(Context c) {
        return prefs(c).getLong(KEY_LAST_REPAIR, 0L);
    }

    static int repairCount(Context c) {
        return prefs(c).getInt(KEY_REPAIR_COUNT, 0);
    }

    static void markRepair(Context c) {
        SharedPreferences p = prefs(c);
        p.edit()
                .putLong(KEY_LAST_REPAIR, System.currentTimeMillis())
                .putInt(KEY_REPAIR_COUNT, p.getInt(KEY_REPAIR_COUNT, 0) + 1)
                .apply();
    }

    static int updateConsecutiveSuspect(Context c, boolean suspect) {
        SharedPreferences p = prefs(c);
        int next = suspect ? p.getInt(KEY_CONSECUTIVE_SUSPECT, 0) + 1 : 0;
        p.edit().putInt(KEY_CONSECUTIVE_SUSPECT, next).apply();
        return next;
    }

    static void clearConsecutiveSuspect(Context c) {
        prefs(c).edit().putInt(KEY_CONSECUTIVE_SUSPECT, 0).apply();
    }

    static int updateConsecutiveDegraded(Context c, boolean degraded) {
        SharedPreferences p = prefs(c);
        int next = degraded ? p.getInt(KEY_CONSECUTIVE_DEGRADED, 0) + 1 : 0;
        p.edit().putInt(KEY_CONSECUTIVE_DEGRADED, next).apply();
        return next;
    }

    static void saveHfpAudioTransport(Context c, Boolean connected) {
        String value =
                connected == null
                        ? "unknown"
                        : (connected ? "connected" : "disconnected");
        prefs(c).edit().putString(KEY_HFP_AUDIO_TRANSPORT, value).apply();
    }

    static Boolean lastHfpAudioTransport(Context c) {
        String value = prefs(c).getString(KEY_HFP_AUDIO_TRANSPORT, "unknown");
        if ("connected".equals(value)) return Boolean.TRUE;
        if ("disconnected".equals(value)) return Boolean.FALSE;
        return null;
    }

    static boolean routeOwned(Context c) {
        return prefs(c).getBoolean(KEY_ROUTE_OWNED, false);
    }

    static void setRouteOwned(Context c, boolean owned) {
        prefs(c).edit().putBoolean(KEY_ROUTE_OWNED, owned).apply();
    }
}
