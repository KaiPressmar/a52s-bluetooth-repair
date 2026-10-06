package de.kaipressmar.a52srepair;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        boolean supportedAction =
                Intent.ACTION_BOOT_COMPLETED.equals(action)
                        || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action);

        if (!supportedAction || !RepairStateStore.monitoringEnabled(context)) {
            return;
        }

        Intent service = new Intent(context, MonitorService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(service);
            } else {
                context.startService(service);
            }
            Diag.log(context, "WATCHDOG RESTORE action=" + action);
        } catch (RuntimeException e) {
            Diag.log(context, "WATCHDOG RESTORE FAILED action=" + action + " error=" + e);
        }
    }
}
