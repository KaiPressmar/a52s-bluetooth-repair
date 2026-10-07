package de.kaipressmar.a52srepair;

import android.app.*;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.*;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.content.pm.PackageManager;

public class MonitorService extends Service {
    static final String CHANNEL_ID = "monitor";
    static final int NOTIFICATION_ID = 1;

    private static final long EVENT_DEBOUNCE_MS = 1_200L;
    private static final long BLUETOOTH_ON_SETTLE_MS = 10_000L;
    private static final long HFP_CONNECTED_SETTLE_MS = 15_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private AudioManager audioManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothHeadset headsetProxy;
    private AudioManager.OnModeChangedListener modeChangedListener;
    private AudioManager.OnCommunicationDeviceChangedListener communicationDeviceChangedListener;
    private boolean bluetoothReceiverRegistered;
    private TelephonyManager telephonyManager;
    private CallStateCallback callStateCallback;
    private boolean cellularCallActive;
    private String pendingReason = "scheduled";
    private int repairAttemptsInIncident;
    private int consecutiveTransportMismatch;
    private long transportMismatchSinceMillis;

    private final Runnable tick =
            new Runnable() {
                @Override
                public void run() {
                    String reason = pendingReason;
                    pendingReason = "scheduled";
                    runCheck(reason);
                }
            };

    private final BluetoothProfile.ServiceListener headsetServiceListener =
            new BluetoothProfile.ServiceListener() {
                @Override
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    if (profile == BluetoothProfile.HEADSET && proxy instanceof BluetoothHeadset) {
                        headsetProxy = (BluetoothHeadset) proxy;
                        scheduleSoon("hfp-proxy-connected");
                    }
                }

                @Override
                public void onServiceDisconnected(int profile) {
                    if (profile == BluetoothProfile.HEADSET) {
                        headsetProxy = null;
                        consecutiveTransportMismatch = 0;
                        transportMismatchSinceMillis = 0L;
                    }
                }
            };

    private final AudioDeviceCallback deviceCallback =
            new AudioDeviceCallback() {
                @Override
                public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
                    scheduleSoon("audio-device-added");
                }

                @Override
                public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
                    scheduleSoon("audio-device-removed");
                }
            };

    private final class CallStateCallback extends TelephonyCallback
            implements TelephonyCallback.CallStateListener {
        @Override
        public void onCallStateChanged(int state) {
            if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
                cellularCallActive = true;
                repairAttemptsInIncident = 0;
                scheduleAfter("telephony-offhook", 350L);
                handler.postDelayed(
                        () -> scheduleSoon("telephony-offhook-settled"),
                        2_000L);
            } else if (state == TelephonyManager.CALL_STATE_IDLE) {
                cellularCallActive = false;
                repairAttemptsInIncident = 0;
                consecutiveTransportMismatch = 0;
                transportMismatchSinceMillis = 0L;
                scheduleSoon("telephony-idle");
            } else if (state == TelephonyManager.CALL_STATE_RINGING) {
                scheduleSoon("telephony-ringing");
            }
        }
    }

    private final BroadcastReceiver bluetoothEvents =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent == null ? null : intent.getAction();
                    if (action == null) return;

                    if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                        int state =
                                intent.getIntExtra(
                                        BluetoothAdapter.EXTRA_STATE,
                                        BluetoothAdapter.ERROR);
                        if (state == BluetoothAdapter.STATE_OFF
                                || state == BluetoothAdapter.STATE_TURNING_OFF) {
                            repairAttemptsInIncident = 0;
                            consecutiveTransportMismatch = 0;
                            transportMismatchSinceMillis = 0L;
                            handler.removeCallbacks(tick);
                            if (RepairStateStore.routeOwned(MonitorService.this)) {
                                BluetoothRepair.releaseCommunicationRoute(MonitorService.this);
                            }
                            updateNotification("Bluetooth aus · Auto-Schutz pausiert");
                            Diag.log(MonitorService.this, "WATCHDOG EVENT bluetooth-off");
                        } else if (state == BluetoothAdapter.STATE_ON) {
                            repairAttemptsInIncident = 0;
                            scheduleAfter("bluetooth-on", EVENT_DEBOUNCE_MS);
                            handler.postDelayed(
                                    () -> scheduleSoon("bluetooth-on-settled"),
                                    BLUETOOTH_ON_SETTLE_MS);
                        }
                        return;
                    }

                    if (BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED.equals(action)) {
                        int state =
                                intent.getIntExtra(
                                        BluetoothAdapter.EXTRA_CONNECTION_STATE,
                                        BluetoothAdapter.STATE_DISCONNECTED);
                        scheduleAfter(
                                state == BluetoothAdapter.STATE_CONNECTED
                                        ? "bluetooth-device-connected"
                                        : "bluetooth-device-state-" + state,
                                EVENT_DEBOUNCE_MS);
                        return;
                    }

                    if (BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED.equals(action)) {
                        int state =
                                intent.getIntExtra(
                                        BluetoothProfile.EXTRA_STATE,
                                        BluetoothProfile.STATE_DISCONNECTED);
                        if (state == BluetoothProfile.STATE_CONNECTED) {
                            repairAttemptsInIncident = 0;
                            scheduleAfter("hfp-connected", EVENT_DEBOUNCE_MS);
                            handler.postDelayed(
                                    () -> scheduleSoon("hfp-connected-settled"),
                                    HFP_CONNECTED_SETTLE_MS);
                        } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                            repairAttemptsInIncident = 0;
                            consecutiveTransportMismatch = 0;
                            transportMismatchSinceMillis = 0L;
                            scheduleSoon("hfp-disconnected");
                        } else {
                            scheduleSoon("hfp-state-" + state);
                        }
                        return;
                    }

                    if (BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED.equals(action)) {
                        int state =
                                intent.getIntExtra(
                                        BluetoothProfile.EXTRA_STATE,
                                        BluetoothHeadset.STATE_AUDIO_DISCONNECTED);
                        // This is the strongest public signal that the HFP/SCO call-audio
                        // transport changed. Always re-probe the real system state.
                        scheduleAfter("hfp-audio-" + state, 350L);
                        return;
                    }

                    if (BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED.equals(action)) {
                        int state =
                                intent.getIntExtra(
                                        BluetoothProfile.EXTRA_STATE,
                                        BluetoothProfile.STATE_DISCONNECTED);
                        scheduleAfter("media-profile-" + state, EVENT_DEBOUNCE_MS);
                    }
                }
            };

    @Override
    public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager == null ? null : bluetoothManager.getAdapter();

        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(
                new NotificationChannel(
                        CHANNEL_ID,
                        "Bluetooth Telefonie-Wächter",
                        NotificationManager.IMPORTANCE_LOW));

        startForeground(
                NOTIFICATION_ID,
                notification("Bluetooth-Status wird initialisiert …"));

        if (audioManager != null) {
            audioManager.registerAudioDeviceCallback(deviceCallback, handler);
            modeChangedListener = mode -> scheduleSoon("audio-mode-" + mode);
            audioManager.addOnModeChangedListener(getMainExecutor(), modeChangedListener);
            communicationDeviceChangedListener =
                    device -> scheduleSoon("communication-route");
            audioManager.addOnCommunicationDeviceChangedListener(
                    getMainExecutor(), communicationDeviceChangedListener);
        }

        registerBluetoothEvents();
        registerHeadsetProxy();
        ensureTelephonyCallStateListener();

        Diag.log(this, "WATCHDOG START triggerMode=bluetooth+hfp+sco+telephony periodicOnlyWhenBtOn");
        checkAppUpdates();
        handler.post(tick);
    }

    @SuppressWarnings("deprecation")
    private void registerBluetoothEvents() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED);
        filter.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);

        try {
            if (Build.VERSION.SDK_INT >= 33) {
                // Bluetooth broadcasts can originate from a privileged Bluetooth process rather
                // than the system UID. We never trust the broadcast payload for repair decisions;
                // every event only triggers a fresh probe of Android's real audio/Bluetooth state.
                registerReceiver(bluetoothEvents, filter, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(bluetoothEvents, filter);
            }
            bluetoothReceiverRegistered = true;
        } catch (RuntimeException e) {
            Diag.log(this, "WATCHDOG bluetooth receiver unavailable " + e.getClass().getSimpleName());
        }
    }

    private void registerHeadsetProxy() {
        if (bluetoothAdapter == null || headsetProxy != null) return;
        try {
            bluetoothAdapter.getProfileProxy(
                    this,
                    headsetServiceListener,
                    BluetoothProfile.HEADSET);
        } catch (RuntimeException e) {
            Diag.log(
                    this,
                    "WATCHDOG HFP proxy-unavailable "
                            + e.getClass().getSimpleName());
        }
    }

    private Boolean hfpAudioTransportConnected() {
        BluetoothHeadset proxy = headsetProxy;
        if (proxy == null) return null;
        if (Build.VERSION.SDK_INT >= 31
                && checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED) {
            return null;
        }

        try {
            for (BluetoothDevice device : proxy.getConnectedDevices()) {
                if (proxy.isAudioConnected(device)) return Boolean.TRUE;
            }
            return Boolean.FALSE;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void ensureTelephonyCallStateListener() {
        if (callStateCallback != null
                || Build.VERSION.SDK_INT < 31
                || checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE)
                        != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        try {
            telephonyManager =
                    (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            if (telephonyManager == null) return;
            cellularCallActive = BluetoothRepair.currentCellularCallActive(this);
            callStateCallback = new CallStateCallback();
            telephonyManager.registerTelephonyCallback(
                    getMainExecutor(),
                    callStateCallback);
            Diag.log(
                    this,
                    "WATCHDOG TELEPHONY listener=registered active="
                            + cellularCallActive);
        } catch (RuntimeException e) {
            callStateCallback = null;
            Diag.log(
                    this,
                    "WATCHDOG TELEPHONY listener-unavailable "
                            + e.getClass().getSimpleName());
        }
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
        scheduleAfter(reason, EVENT_DEBOUNCE_MS);
    }

    private void scheduleAfter(String reason, long delayMs) {
        pendingReason = reason;
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, Math.max(0L, delayMs));
    }

    private void runCheck(String reason) {
        handler.removeCallbacks(tick);

        if (!RepairStateStore.monitoringEnabled(this)) {
            stopSelf();
            return;
        }

        ensureTelephonyCallStateListener();
        BluetoothRepair.Probe probe =
                BluetoothRepair.probe(
                        this,
                        cellularCallActive || BluetoothRepair.currentCellularCallActive(this));
        BluetoothHealth health = probe.health;

        if (health.state == BluetoothHealth.State.BLUETOOTH_OFF) {
            if (RepairStateStore.routeOwned(this)) {
                BluetoothRepair.releaseCommunicationRoute(this);
            }
            repairAttemptsInIncident = 0;
            RepairStateStore.updateConsecutiveSuspect(this, false);
            RepairStateStore.updateConsecutiveDegraded(this, false);
            RepairStateStore.saveHfpAudioTransport(this, null);
            RepairStateStore.saveHealth(this, health);
            HealthHistoryStore.record(this, health, reason);
            updateNotification("Bluetooth aus · Auto-Schutz pausiert");
            Diag.log(this, "WATCHDOG PAUSED bluetooth-off reason=" + reason);
            return;
        }

        if (health.state == BluetoothHealth.State.PERMISSION_REQUIRED) {
            repairAttemptsInIncident = 0;
            RepairStateStore.saveHealth(this, health);
            updateNotification("Bluetooth-Berechtigung fehlt · Auto-Schutz pausiert");
            Diag.log(this, "WATCHDOG PAUSED permission-required");
            return;
        }

        // setCommunicationDevice() remains active while our process lives. Release only routes
        // selected by this app as soon as the communication session is over.
        if (!health.inCommunication && RepairStateStore.routeOwned(this)) {
            BluetoothRepair.releaseCommunicationRoute(this);
            probe = BluetoothRepair.probe(this);
            health = probe.health;
        }

        Boolean hfpAudioConnected = hfpAudioTransportConnected();
        RepairStateStore.saveHfpAudioTransport(this, hfpAudioConnected);
        boolean transportMismatch =
                HfpAudioTransportPolicy.isMismatch(health, hfpAudioConnected);
        long now = System.currentTimeMillis();
        if (transportMismatch) {
            if (transportMismatchSinceMillis == 0L) {
                transportMismatchSinceMillis = now;
            }
        } else {
            transportMismatchSinceMillis = 0L;
        }

        if (!health.inCommunication) {
            repairAttemptsInIncident = 0;
        }

        consecutiveTransportMismatch =
                transportMismatch ? consecutiveTransportMismatch + 1 : 0;

        RepairStateStore.saveHealth(this, health);
        HealthHistoryStore.record(this, health, reason);
        Diag.log(
                this,
                "WATCHDOG CHECK reason="
                        + reason
                        + " state="
                        + health.state
                        + " hfpProfile="
                        + health.hfpProfileConnected
                        + " mediaBt="
                        + probe.bluetoothMediaOutputAvailable
                        + " scoAvailable="
                        + health.scoAvailable
                        + " scoSelected="
                        + health.scoSelected
                        + " hfpAudioTransport="
                        + (hfpAudioConnected == null ? "unknown" : hfpAudioConnected)
                        + " transportMismatch="
                        + transportMismatch
                        + " attempts="
                        + repairAttemptsInIncident
                        + "\n"
                        + Diag.snapshot(this, probe));

        boolean actionableSuspect = health.needsRepair() && !health.speakerphoneOn;
        int suspectCount =
                RepairStateStore.updateConsecutiveSuspect(this, actionableSuspect);
        boolean degraded =
                health.state == BluetoothHealth.State.HFP_CONNECTED_NO_SCO
                        || health.state == BluetoothHealth.State.CALL_WITHOUT_SCO;
        int degradedCount =
                RepairStateStore.updateConsecutiveDegraded(this, degraded);

        if (health.state == BluetoothHealth.State.HFP_CONNECTED_NO_SCO
                && degradedCount == 1
                && RepairStateStore.routeOwned(this)) {
            Diag.log(this, "WATCHDOG RECOVERY releasing stale app-owned route");
            BluetoothRepair.releaseCommunicationRoute(this);
        }

        RepairDecision decision =
                RepairDecision.decide(
                        probe.signature,
                        health,
                        suspectCount,
                        degradedCount,
                        now,
                        RepairStateStore.lastRepairAt(this));

        RepairDecision transportDecision =
                HfpAudioTransportPolicy.decide(
                        health,
                        hfpAudioConnected,
                        consecutiveTransportMismatch,
                        transportMismatchSinceMillis,
                        now,
                        RepairStateStore.lastRepairAt(this));

        if (transportDecision.action
                == RepairDecision.Action.RESELECT_COMMUNICATION_ROUTE) {
            decision = transportDecision;
        } else if (decision.action == RepairDecision.Action.NONE
                && transportDecision.action
                        == RepairDecision.Action.WAIT_FOR_CONFIRMATION) {
            decision = transportDecision;
        }

        Diag.log(
                this,
                "WATCHDOG DECISION signature="
                        + probe.signature.kind
                        + " confidence="
                        + probe.signature.confidence
                        + " action="
                        + decision.action
                        + " reason="
                        + decision.reason);

        boolean repairAllowed =
                RepairRetryPolicy.canAttempt(
                        RepairStateStore.autoRepairEnabled(this),
                        decision,
                        repairAttemptsInIncident);

        if (repairAllowed) {
            BluetoothRepair.RepairResult result =
                    BluetoothRepair.repairCommunicationRoute(
                            this,
                            transportMismatch,
                            cellularCallActive || BluetoothRepair.currentCellularCallActive(this));
            if (result.attempted) repairAttemptsInIncident++;

            Diag.log(
                    this,
                    "WATCHDOG AUTO-REPAIR attempt="
                            + repairAttemptsInIncident
                            + "/"
                            + RepairRetryPolicy.MAX_ATTEMPTS_PER_INCIDENT
                            + " attempted="
                            + result.attempted
                            + " requestAccepted="
                            + result.routeSelected
                            + " message="
                            + result.message);

            if (result.routeSelected) {
                updateNotification(
                        "Routingfehler erkannt · Reparaturversuch "
                                + repairAttemptsInIncident
                                + " wird verifiziert");
                handler.postDelayed(
                        () -> verifyRepair("auto-repair-verify"),
                        RepairVerificationPolicy.FIRST_VERIFY_MS);
            } else if (result.attempted
                    && !RepairRetryPolicy.exhausted(repairAttemptsInIncident)) {
                updateNotification("Bluetooth-Route abgelehnt · zweiter Versuch folgt");
                handler.postDelayed(
                        () -> scheduleSoon("repair-request-retry"),
                        RepairRetryPolicy.RETRY_AFTER_REJECT_MS);
            } else {
                updateNotification(result.message);
            }
        } else if (decision.action == RepairDecision.Action.RESELECT_COMMUNICATION_ROUTE
                && RepairRetryPolicy.exhausted(repairAttemptsInIncident)) {
            updateNotification("Routingfehler bleibt bestehen · automatische Versuche beendet");
        } else if (decision.action == RepairDecision.Action.ESCALATE_VENDOR_STACK) {
            updateNotification("HFP verbunden · SCO-Systempfad blockiert");
        } else {
            updateNotification(health.summary);
        }

        long nextDelay =
                WatchdogSchedule.nextDelayMillis(health, suspectCount, degradedCount);
        if (transportDecision.action
                == RepairDecision.Action.WAIT_FOR_CONFIRMATION) {
            long transportConfirmDelay =
                    HfpAudioTransportPolicy.confirmationDelayMillis(
                            transportMismatchSinceMillis,
                            now);
            if (nextDelay < 0L || transportConfirmDelay < nextDelay) {
                nextDelay = transportConfirmDelay;
            }
        }
        if (nextDelay >= 0L) {
            handler.postDelayed(tick, nextDelay);
        }

        UpdateManager.checkForUpdates(this, false, null);
        Diag.log(
                this,
                "WATCHDOG NEXT inMs="
                        + nextDelay
                        + " state="
                        + health.state
                        + " hfp="
                        + health.hfpProfileConnected
                        + " attempts="
                        + repairAttemptsInIncident);
    }

    private void verifyRepair(String reason) {
        if (!RepairStateStore.monitoringEnabled(this)) return;

        BluetoothRepair.Probe verified =
                BluetoothRepair.probe(
                        this,
                        cellularCallActive || BluetoothRepair.currentCellularCallActive(this));
        BluetoothHealth health = verified.health;
        Boolean hfpAudioConnected = hfpAudioTransportConnected();
        RepairStateStore.saveHfpAudioTransport(this, hfpAudioConnected);
        RepairStateStore.saveHealth(this, health);

        boolean success =
                HfpAudioTransportPolicy.verificationSucceeded(
                        health,
                        hfpAudioConnected);
        HealthHistoryStore.record(this, health, success ? "repair" : "verify");

        Diag.log(
                this,
                "WATCHDOG VERIFY reason="
                        + reason
                        + " state="
                        + health.state
                        + " attempts="
                        + repairAttemptsInIncident
                        + "\n"
                        + Diag.snapshot(this, verified));

        if (success) {
            RepairStateStore.markRepair(this);
            RepairStateStore.clearConsecutiveSuspect(this);
            repairAttemptsInIncident = 0;
            updateNotification("Telefonie-Audio erfolgreich über Bluetooth geroutet");
            return;
        }

        boolean finalVerification = "auto-repair-final".equals(reason);
        boolean selectedButTransportDown =
                health.scoSelected && Boolean.FALSE.equals(hfpAudioConnected);
        boolean selectedButTransportUnknown =
                health.scoSelected && hfpAudioConnected == null;

        if (((selectedButTransportDown || selectedButTransportUnknown)
                        && !finalVerification)
                || RepairVerificationPolicy.needsFinalVerification(
                        health.scoSelected,
                        health.inCommunication,
                        RepairStateStore.routeOwned(this),
                        finalVerification)) {
            updateNotification("Bluetooth-Route wird noch bestätigt …");
            handler.postDelayed(
                    () -> verifyRepair("auto-repair-final"),
                    RepairVerificationPolicy.FINAL_GRACE_MS);
            return;
        }

        if (finalVerification
                && (RepairRetryPolicy.shouldRetryAfterVerification(
                                health,
                                RepairStateStore.routeOwned(this),
                                repairAttemptsInIncident)
                        || (HfpAudioTransportPolicy.isMismatch(
                                        health,
                                        hfpAudioConnected)
                                && repairAttemptsInIncident
                                        < RepairRetryPolicy.MAX_ATTEMPTS_PER_INCIDENT))) {
            updateNotification("Erster Versuch ohne Erfolg · zweiter Reparaturversuch folgt");
            handler.postDelayed(
                    () -> scheduleSoon("repair-verification-retry"),
                    RepairRetryPolicy.RETRY_AFTER_VERIFY_FAILURE_MS);
            return;
        }

        if (selectedButTransportUnknown) {
            updateNotification(
                    "Bluetooth-Route gewählt · HFP-Audiotransport konnte nicht verifiziert werden");
        } else if (health.state == BluetoothHealth.State.HFP_CONNECTED_NO_SCO) {
            updateNotification("HFP verbunden · SCO-Systempfad blockiert");
        } else if (RepairRetryPolicy.exhausted(repairAttemptsInIncident)) {
            updateNotification("Telefonie-Route nicht repariert · automatische Versuche beendet");
        } else {
            updateNotification(health.summary);
        }
    }

    private void checkAppUpdates() {
        UpdateManager.checkForUpdates(
                this,
                false,
                (release, networkChecked, error) -> {
                    if (networkChecked && release != null) {
                        UpdateManager.notifyUpdateAvailable(this, release);
                    }
                });
    }

    private void updateNotification(String text) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(NOTIFICATION_ID, notification(text));
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

        if (telephonyManager != null && callStateCallback != null) {
            try {
                telephonyManager.unregisterTelephonyCallback(callStateCallback);
            } catch (RuntimeException ignored) {
            }
            callStateCallback = null;
            telephonyManager = null;
        }

        if (bluetoothAdapter != null && headsetProxy != null) {
            try {
                bluetoothAdapter.closeProfileProxy(
                        BluetoothProfile.HEADSET,
                        headsetProxy);
            } catch (RuntimeException ignored) {
            }
            headsetProxy = null;
        }

        if (bluetoothReceiverRegistered) {
            try {
                unregisterReceiver(bluetoothEvents);
            } catch (RuntimeException ignored) {
            }
            bluetoothReceiverRegistered = false;
        }

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
            if (communicationDeviceChangedListener != null) {
                try {
                    audioManager.removeOnCommunicationDeviceChangedListener(
                            communicationDeviceChangedListener);
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
