package de.kaipressmar.a52srepair.setup;

import android.Manifest;
import android.app.AppOpsManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Process;
import de.kaipressmar.a52srepair.data.AppSettings;

/** What stands between the user and working automatic protection, in setup order. */
public enum ProtectionStatus {
    NEEDS_BLUETOOTH_PERMISSION,
    NEEDS_CALL_ACCESS,
    PAUSED,
    BLUETOOTH_OFF,
    CONFIGURED,
    ACTIVE;

    /** App-op behind {@code MANAGE_ONGOING_CALLS}; Telecom binds our InCallService only if allowed. */
    static final String OP_MANAGE_ONGOING_CALLS = "android:manage_ongoing_calls";

    public static ProtectionStatus evaluate(Context context) {
        if (!hasBluetoothPermission(context)) return NEEDS_BLUETOOTH_PERMISSION;
        if (!hasCallAccess(context)) return NEEDS_CALL_ACCESS;
        if (!new AppSettings(context).protectionEnabled()) return PAUSED;
        if (!bluetoothEnabled(context)) return BLUETOOTH_OFF;
        return de.kaipressmar.a52srepair.telecom.CallAudioService.activeReport() == null ? CONFIGURED : ACTIVE;
    }

    public boolean setupComplete() {
        return this != NEEDS_BLUETOOTH_PERMISSION && this != NEEDS_CALL_ACCESS;
    }

    public static boolean hasBluetoothPermission(Context context) {
        return context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static de.kaipressmar.a52srepair.core.model.Evidence<Boolean> callAccessEvidence(Context context) {
        long now = android.os.SystemClock.elapsedRealtime();
        AppOpsManager ops = context.getSystemService(AppOpsManager.class);
        if (ops == null) return de.kaipressmar.a52srepair.core.model.Evidence.unavailable(now);
        try {
            return de.kaipressmar.a52srepair.core.model.Evidence.known(ops.unsafeCheckOpNoThrow(
                    OP_MANAGE_ONGOING_CALLS, Process.myUid(), context.getPackageName()) == AppOpsManager.MODE_ALLOWED, now);
        } catch (RuntimeException unavailable) {
            return de.kaipressmar.a52srepair.core.model.Evidence.unknown(now);
        }
    }

    public static boolean hasCallAccess(Context context) {
        de.kaipressmar.a52srepair.core.model.Evidence<Boolean> evidence = callAccessEvidence(context);
        return evidence.fresh(android.os.SystemClock.elapsedRealtime(), 1_000L) && Boolean.TRUE.equals(evidence.value);
    }

    static boolean bluetoothEnabled(Context context) {
        BluetoothManager manager = context.getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        try {
            return adapter != null && adapter.isEnabled();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** One-time alternative to the companion link, for users with a computer. */
    public static String adbGrantCommand(Context context) {
        return "adb shell appops set " + context.getPackageName() + " MANAGE_ONGOING_CALLS allow";
    }
}
