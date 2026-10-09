package de.kaipressmar.a52srepair;

import android.content.Context;
import android.os.Handler;

/**
 * Runs a {@link RepairDecision} against Android. Shared by the watchdog, the notification
 * "Ich höre nichts" action and the manual UI so that all three use the same repair sequence.
 */
final class RepairExecutor {
    interface Callback {
        /** Called once the final route/volume request was issued (or refused). */
        void onRequested(BluetoothRepair.RepairResult result);
    }

    private RepairExecutor() {}

    static void execute(
            Context c,
            Handler handler,
            RepairDecision decision,
            CallAudioSignals signals,
            boolean forceReselect,
            boolean cellularCallActive,
            Callback callback) {
        if (decision == null || !decision.isRepair()) {
            callback.onRequested(
                    new BluetoothRepair.RepairResult(
                            false,
                            false,
                            decision == null ? "Keine Reparatur nötig." : decision.reason));
            return;
        }

        switch (decision.action) {
            case RESTORE_CALL_VOLUME:
                callback.onRequested(BluetoothRepair.restoreCallVolume(c, signals));
                return;
            case BOUNCE_COMMUNICATION_ROUTE:
                BluetoothRepair.RepairResult hold = BluetoothRepair.startRouteBounce(c);
                if (!hold.routeSelected) {
                    callback.onRequested(hold);
                    return;
                }
                handler.postDelayed(
                        () -> callback.onRequested(BluetoothRepair.completeRouteBounce(c)),
                        BluetoothRepair.BOUNCE_HOLD_MS);
                return;
            case RESELECT_COMMUNICATION_ROUTE:
            default:
                callback.onRequested(
                        BluetoothRepair.repairCommunicationRoute(
                                c,
                                forceReselect,
                                cellularCallActive));
        }
    }
}
