package de.kaipressmar.a52srepair;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.*;

public class MonitorService extends Service {
    static final String CHANNEL_ID = "monitor";
    static final int NOTIFICATION_ID = 1;
    private static final long IDLE_INTERVAL_MS = 30_000L;
    private static final long CALL_INTERVAL_MS = 5_000L;
    private static final long REPAIR_COOLDOWN_MS = 60_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private AudioManager audioManager;

    private final Runnable tick =
            new Runnable() {
                @Override
                public void run() {
                    runCheck("scheduled");
                }
            };

    private final AudioDeviceCallback deviceCallback =
            new AudioDeviceCallback() {
                @Override
                public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
                    scheduleSoon("device-added");
                }

                @Override
                public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
                    scheduleSoon("device-removed");
                }
            };

    @Override
    public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(
                new NotificationChannel(
                        CHANNEL_ID,
                        "Bluetooth Telefonie-Wächter",
                        NotificationManager.IMPORTANCE_LOW));

        startForeground(
                NOTIFICATION_ID,
                notification("Automatische Prüfung wird gestartet …"));

        if (audioManager != null) {
            audioManager.registerAudioDeviceCallback(deviceCallback, handler);
        }

        Diag.log(this, "WATCHDOG START");
        handler.post(tick);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!RepairStateStore.monitoringEnabled(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    private void scheduleSoon(String reason) {
        Diag.log(this, "WATCHDOG EVENT " + reason);
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, 750L);
    }

    private void runCheck(String reason) {
        handler.removeCallbacks(tick);

        if (!RepairStateStore.monitoringEnabled(this)) {
            stopSelf();
            return;
        }

        BluetoothRepair.Probe probe = BluetoothRepair.probe(this);
        BluetoothHealth health = probe.health;
        RepairStateStore.saveHealth(this, health);
        Diag.log(
                this,
                "WATCHDOG CHECK reason="
                        + reason
                        + " state="
                        + health.state
                        + "\n"
                        + Diag.snapshot(this));

        boolean actionableSuspect = health.needsRepair() && !health.speakerphoneOn;
        int suspectCount =
                RepairStateStore.updateConsecutiveSuspect(this, actionableSuspect);

        boolean repairAllowed =
                RepairStateStore.autoRepairEnabled(this)
                        && actionableSuspect
                        && suspectCount >= 2
                        && System.currentTimeMillis() - RepairStateStore.lastRepairAt(this)
                                >= REPAIR_COOLDOWN_MS;

        if (repairAllowed) {
            BluetoothRepair.RepairResult result =
                    BluetoothRepair.repairCommunicationRoute(this, false);
            Diag.log(
                    this,
                    "WATCHDOG AUTO-REPAIR attempted="
                            + result.attempted
                            + " selected="
                            + result.routeSelected
                            + " message="
                            + result.message);
            updateNotification(
                    result.routeSelected
                            ? "Routingfehler erkannt und Reparatur ausgelöst"
                            : result.message);
            handler.postDelayed(
                    () -> {
                        BluetoothRepair.Probe verified = BluetoothRepair.probe(this);
                        RepairStateStore.saveHealth(this, verified.health);
                        Diag.log(
                                this,
                                "WATCHDOG VERIFY state="
                                        + verified.health.state
                                        + "\n"
                                        + Diag.snapshot(this));
                        updateNotification(verified.health.summary);
                    },
                    1_500L);
        } else {
            updateNotification(health.summary);
        }

        handler.postDelayed(
                tick,
                health.inCommunication ? CALL_INTERVAL_MS : IDLE_INTERVAL_MS);
    }

    private void updateNotification(String text) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(NOTIFICATION_ID, notification(text));
    }

    private Notification notification(String text) {
        Intent launch = new Intent(this, MainActivity.class);
        PendingIntent pi =
                PendingIntent.getActivity(
                        this,
                        0,
                        launch,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("A52s Bluetooth Repair · Auto-Schutz")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (audioManager != null) {
            try {
                audioManager.unregisterAudioDeviceCallback(deviceCallback);
            } catch (RuntimeException ignored) {
            }
        }
        BluetoothRepair.releaseCommunicationRoute(this);
        Diag.log(this, "WATCHDOG STOP");
        super.onDestroy();
    }

    @Override
    public android.os.IBinder onBind(Intent intent) {
        return null;
    }
}
