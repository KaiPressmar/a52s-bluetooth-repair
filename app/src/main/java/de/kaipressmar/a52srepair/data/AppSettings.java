package de.kaipressmar.a52srepair.data;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import de.kaipressmar.a52srepair.core.repair.PreventiveRebuildMode;

/** User settings (shared with the preference screen) plus small internal app state. */
public final class AppSettings {
    public static final String KEY_PROTECTION_ENABLED = "protection_enabled";
    public static final String KEY_PREVENTIVE_MODE = "preventive_mode";
    public static final String KEY_AUTO_UPDATE_CHECK = "auto_update_check";

    private static final String STATE_PREFS = "app-state";
    private static final String KEY_LAST_SERVICE_BIND = "last_service_bind";
    private static final String KEY_MIGRATED_TO = "migrated_to";

    private final Context context;
    private final SharedPreferences settings;
    private final SharedPreferences state;

    public AppSettings(Context context) {
        this.context = context.getApplicationContext();
        settings = PreferenceManager.getDefaultSharedPreferences(this.context);
        state = this.context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE);
    }

    public boolean protectionEnabled() {
        return settings.getBoolean(KEY_PROTECTION_ENABLED, true);
    }

    public void setProtectionEnabled(boolean enabled) {
        settings.edit().putBoolean(KEY_PROTECTION_ENABLED, enabled).apply();
    }

    public PreventiveRebuildMode preventiveMode() {
        return PreventiveRebuildMode.parse(
                settings.getString(KEY_PREVENTIVE_MODE, null), DeviceInfo.defaultPreventiveMode());
    }

    public boolean autoUpdateCheck() {
        return settings.getBoolean(KEY_AUTO_UPDATE_CHECK, true);
    }

    public boolean idleAuto() { return settings.getBoolean("idle_auto", false); }
    public boolean idleVoiceFallback() { return settings.getBoolean("idle_voice", false); }
    public boolean idlePostCallPending() { return state.getBoolean("idle_pending", false); }
    public void setIdlePostCallPending(boolean pending) { state.edit().putBoolean("idle_pending", pending).apply(); }
    public String idleResult() { return state.getString("idle_result", "NOT_RUN"); }
    public void setIdleResult(String result) { state.edit().putString("idle_result", result).apply(); }
    public void markIdleAttempt(long elapsed) {
        state.edit().putLong("idle_elapsed", elapsed).putLong("idle_wall", System.currentTimeMillis()).apply();
    }
    public boolean idleCooldown(long elapsed, long interval) {
        long previous = state.getLong("idle_elapsed", -1L);
        // A reboot/wall-clock change must never cause a permanent lockout.
        long wallDelta = System.currentTimeMillis() - state.getLong("idle_wall", 0L);
        return previous >= 0L && elapsed >= previous && elapsed - previous < interval
                && wallDelta >= 0L && wallDelta < interval;
    }

    /** Last time Telecom bound the call service: proof that call access works in practice. */
    public long lastServiceBindAt() {
        return state.getLong(KEY_LAST_SERVICE_BIND, 0L);
    }

    public void markServiceBound(long now) {
        state.edit().putLong(KEY_LAST_SERVICE_BIND, now).apply();
    }

    public int migratedToVersionCode() {
        return state.getInt(KEY_MIGRATED_TO, 0);
    }

    public void setMigratedToVersionCode(int versionCode) {
        state.edit().putInt(KEY_MIGRATED_TO, versionCode).apply();
    }
}
