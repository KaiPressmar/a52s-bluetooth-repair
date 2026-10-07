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
    private static final long EVENT_DEBOUNCE_MS = 1_200L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private AudioManager audioManager;
    private AudioManager.OnModeChangedListener modeChangedListener;

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
            modeChangedListener =
                    mode -> scheduleSoon(
                            "audio-mode-" + mode);
            audioManager.addOnModeChangedListener(getMainExecutor(), modeChangedListener);
        }

        Diag.log(this, "WATCHDOG START energyMode=event-driven");
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
        handler.postDelayed(tick, EVENT_DEBOUNCE_MS);
    }

    private void runCheck(String reason) {
        handler.removeCallbacks(tick);

        if (!RepairStateStore.monitoringEnabled(this)) {
            stopSelf();
            return;
        }

        BluetoothRepair.Probe probe = BluetoothRepair.probe(this);
        BluetoothHealth health = probe.health;

        // setCommunicationDevice() remains active while our process lives. Release only routes
        // selected by this app as soon as the communication session is over.
        if (!health.inCommunication && RepairStateStore.routeOwned(this)) {
            BluetoothRepair.releaseCommunicationRoute(this);
            probe = BluetoothRepair.probe(this);
            health = probe.health;
        }

        RepairStateStore.saveHealth(this, health);
        Diag.log(
                this,
                "WATCHDOG CHECK reason="
                        + reason
                        + " state="
                        + health.state
                        + " hfpProfile="
                        + health.hfpProfileConnected
                        + " scoAvailable="
                        + health.scoAvailable
                        + " scoSelected="
                        + health.scoSelected
                        + "\n"
                        + Diag.snapshot(this));

        boolean actionableSuspect = health.needsRepair() && !health.speakerphoneOn;
        int suspectCount =
                RepairStateStore.updateConsecutiveSuspect(this, actionableSuspect);

        boolean repairAllowed =
                RepairStateStore.autoRepairEnabled(this)
                        && RepairPolicy.canAutoRepair(
                                health,
                                suspectCount,
                                System.currentTimeMillis(),
                                RepairStateStore.lastRepairAt(this));

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

        long nextDelay =
                WatchdogSchedule.nextDelayMillis(health, suspectCount);
        handler.postDelayed(tick, nextDelay);
        Diag.log(
                this,
                "WATCHDOG NEXT inMs="
                        + nextDelay
                        + " state="
                        + health.state
                        + " suspectCount="
                        + suspectCount);
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
                .setContentTitle(getString(R.string.app_name) + " · Auto-Schutz")
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
            if (modeChangedListener != null) {
                try {
                    audioManager.removeOnModeChangedListener(modeChangedListener);
                } catch (RuntimeException ignored) {
                }
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
