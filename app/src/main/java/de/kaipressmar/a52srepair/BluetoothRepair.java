package de.kaipressmar.a52srepair;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.telephony.TelephonyManager;

final class BluetoothRepair {
    static final class Probe {
        final BluetoothHealth health;
        final AudioDeviceInfo current;
        final AudioDeviceInfo bluetoothCommunicationDevice;
        final boolean bluetoothMediaOutputAvailable;
        final boolean musicActive;
        final CallAudioSignals signals;
        final FailureSignature signature;

        Probe(
                BluetoothHealth health,
                AudioDeviceInfo current,
                AudioDeviceInfo bluetoothCommunicationDevice,
                boolean bluetoothMediaOutputAvailable,
                boolean musicActive) {
            this(
                    health,
                    current,
                    bluetoothCommunicationDevice,
                    bluetoothMediaOutputAvailable,
                    musicActive,
                    CallAudioSignals.UNKNOWN);
        }

        Probe(
                BluetoothHealth health,
                AudioDeviceInfo current,
                AudioDeviceInfo bluetoothCommunicationDevice,
                boolean bluetoothMediaOutputAvailable,
                boolean musicActive,
                CallAudioSignals signals) {
            this.health = health;
            this.current = current;
            this.bluetoothCommunicationDevice = bluetoothCommunicationDevice;
            this.bluetoothMediaOutputAvailable = bluetoothMediaOutputAvailable;
            this.musicActive = musicActive;
            this.signals = signals == null ? CallAudioSignals.UNKNOWN : signals;
            this.signature =
                    FailureSignature.classify(
                            health,
                            bluetoothMediaOutputAvailable,
                            this.signals);
        }
    }

    static final class RepairResult {
        final boolean attempted;
        final boolean routeSelected;
        final String message;

        RepairResult(boolean attempted, boolean routeSelected, String message) {
            this.attempted = attempted;
            this.routeSelected = routeSelected;
            this.message = message;
        }
    }

    private BluetoothRepair() {}

    static Probe probe(Context c) {
        return probe(c, currentCellularCallActive(c));
    }

    @SuppressWarnings("deprecation")
    static Probe probe(Context c, boolean cellularCallActiveHint) {
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        boolean permission =
                Build.VERSION.SDK_INT < 31
                        || c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                                == PackageManager.PERMISSION_GRANTED;

        boolean bluetoothEnabled = false;
        boolean hfpProfileConnected = false;
        if (permission) {
            try {
                BluetoothManager bm =
                        (BluetoothManager) c.getSystemService(Context.BLUETOOTH_SERVICE);
                BluetoothAdapter adapter = bm == null ? null : bm.getAdapter();
                bluetoothEnabled = adapter != null && adapter.isEnabled();
                if (bluetoothEnabled) {
                    hfpProfileConnected =
                            adapter.getProfileConnectionState(BluetoothProfile.HEADSET)
                                    == BluetoothAdapter.STATE_CONNECTED;
                }
            } catch (SecurityException ignored) {
                permission = false;
            } catch (RuntimeException ignored) {
                // Vendor Bluetooth stacks can transiently fail while profiles reconnect.
            }
        }

        if (am == null) {
            BluetoothHealth health =
                    new BluetoothHealth(
                            BluetoothHealth.State.ERROR,
                            false,
                            hfpProfileConnected,
                            false,
                            false,
                            false,
                            "AudioManager nicht verfügbar",
                            "Android stellt den Audio-Dienst momentan nicht bereit.");
            return new Probe(health, null, null, false, false, CallAudioSignals.UNKNOWN);
        }

        AudioDeviceInfo current = am.getCommunicationDevice();
        AudioDeviceInfo candidate = null;
        try {
            for (AudioDeviceInfo device : am.getAvailableCommunicationDevices()) {
                if (isBluetoothCommunicationDevice(device)) {
                    candidate = device;
                    break;
                }
            }
        } catch (RuntimeException ignored) {
            // Some vendor builds can briefly fail while the audio stack changes state.
        }

        boolean selected = current != null && isBluetoothCommunicationDevice(current);
        boolean available = candidate != null;
        boolean mediaAvailable = false;
        try {
            for (AudioDeviceInfo device : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                if (isBluetoothMediaOutput(device)) {
                    mediaAvailable = true;
                    break;
                }
            }
        } catch (RuntimeException ignored) {
            // Media output is supporting evidence only; never fail the primary diagnosis on it.
        }
        boolean musicActive = false;
        try {
            musicActive = am.isMusicActive();
        } catch (RuntimeException ignored) {
        }

        boolean speakerphoneOn =
                current != null && current.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER;
        if (!speakerphoneOn) {
            try {
                speakerphoneOn = am.isSpeakerphoneOn();
            } catch (RuntimeException ignored) {
                // getCommunicationDevice() is the primary source on modern Android.
            }
        }

        int mode = am.getMode();
        BluetoothHealth health =
                BluetoothHealth.assess(
                        permission,
                        bluetoothEnabled,
                        mode,
                        hfpProfileConnected,
                        available,
                        selected,
                        speakerphoneOn,
                        cellularCallActiveHint);
        CallAudioSignals signals =
                health.inCommunication
                        ? readCallAudioSignals(am, mode, cellularCallActiveHint)
                        : CallAudioSignals.UNKNOWN;
        return new Probe(health, current, candidate, mediaAvailable, musicActive, signals);
    }

    static CallAudioSignals readCallAudioSignals(
            AudioManager am,
            int mode,
            boolean cellularCallActive) {
        Boolean voiceRouteOnBluetooth = null;
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                AudioAttributes voice =
                        new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                                .build();
                java.util.List<AudioDeviceInfo> devices = am.getAudioDevicesForAttributes(voice);
                if (devices != null && !devices.isEmpty()) {
                    boolean bluetooth = false;
                    for (AudioDeviceInfo device : devices) {
                        if (isBluetoothCommunicationDevice(device)) bluetooth = true;
                    }
                    voiceRouteOnBluetooth = bluetooth;
                }
            } catch (RuntimeException ignored) {
                // Unknown is safer than a false mismatch.
            }
        }

        int volume = -1;
        int min = -1;
        int max = -1;
        boolean muted = false;
        try {
            volume = am.getStreamVolume(AudioManager.STREAM_VOICE_CALL);
            min = am.getStreamMinVolume(AudioManager.STREAM_VOICE_CALL);
            max = am.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL);
            muted = am.isStreamMute(AudioManager.STREAM_VOICE_CALL);
        } catch (RuntimeException ignored) {
            volume = -1;
        }

        return new CallAudioSignals(
                voiceRouteOnBluetooth,
                volume,
                min,
                max,
                muted,
                cellularCallActive && mode == AudioManager.MODE_NORMAL);
    }

    @SuppressWarnings("deprecation")
    static boolean currentCellularCallActive(Context c) {
        if (Build.VERSION.SDK_INT < 31
                || c.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
                        != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        try {
            TelephonyManager tm =
                    (TelephonyManager) c.getSystemService(Context.TELEPHONY_SERVICE);
            return tm != null && tm.getCallState() == TelephonyManager.CALL_STATE_OFFHOOK;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    static RepairResult repairCommunicationRoute(Context c, boolean force) {
        return repairCommunicationRoute(c, force, currentCellularCallActive(c));
    }

    static RepairResult repairCommunicationRoute(
            Context c,
            boolean force,
            boolean cellularCallActiveHint) {
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) {
            return new RepairResult(false, false, "AudioManager nicht verfügbar.");
        }

        Probe before = probe(c, cellularCallActiveHint);
        if (!force && !before.health.needsRepair()) {
            return new RepairResult(false, before.health.scoSelected, "Kein reparierbarer Routingfehler erkannt.");
        }

        AudioDeviceInfo target = before.bluetoothCommunicationDevice;
        if (target == null) {
            if (before.health.hfpProfileConnected) {
                return new RepairResult(
                        false,
                        false,
                        "HFP ist verbunden, aber Android stellt kein SCO/HFP-Kommunikationsgerät bereit. Ohne privilegierten Zugriff kann die App den Samsung-Bluetoothdienst nicht neu starten.");
            }
            return new RepairResult(false, false, "Kein Bluetooth-SCO/HFP-Kommunikationsgerät verfügbar.");
        }

        try {
            am.clearCommunicationDevice();
            RepairStateStore.setRouteOwned(c, false);
            boolean selected = am.setCommunicationDevice(target);
            Diag.log(
                    c,
                    "ROUTE REPAIR selected="
                            + selected
                            + " target="
                            + Diag.device(target)
                            + "\n"
                            + Diag.snapshot(c));
            if (selected) {
                RepairStateStore.setRouteOwned(c, true);
                return new RepairResult(
                        true,
                        true,
                        "Bluetooth-Telefoniepfad wurde neu angefordert. Android bestätigt die Route asynchron.");
            }
            return new RepairResult(true, false, "Android hat die Auswahl des Bluetooth-Telefoniepfads abgelehnt.");
        } catch (RuntimeException e) {
            RepairStateStore.setRouteOwned(c, false);
            Diag.log(c, "ROUTE REPAIR ERROR " + e);
            return new RepairResult(true, false, "Routing-Reparatur fehlgeschlagen: " + e.getClass().getSimpleName());
        }
    }

    /** How long the earpiece hold lasts before SCO is requested again during a bounce. */
    static final long BOUNCE_HOLD_MS = 900L;

    /**
     * First half of a route bounce. Clearing and re-selecting the same SCO device is a no-op in
     * AudioService, so a stuck downlink keeps its stale HAL path. Moving the communication route
     * briefly to the earpiece tears SCO down; {@link #completeRouteBounce} brings it back up and
     * forces a fresh SCO/codec negotiation with the car or headset.
     */
    static RepairResult startRouteBounce(Context c) {
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return new RepairResult(false, false, "AudioManager nicht verfügbar.");

        AudioDeviceInfo earpiece = null;
        AudioDeviceInfo bluetooth = null;
        try {
            for (AudioDeviceInfo device : am.getAvailableCommunicationDevices()) {
                if (device.getType() == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE) earpiece = device;
                if (bluetooth == null && isBluetoothCommunicationDevice(device)) bluetooth = device;
            }
        } catch (RuntimeException e) {
            return new RepairResult(false, false, "Kommunikationsgeräte nicht lesbar.");
        }
        if (bluetooth == null) {
            return new RepairResult(
                    false,
                    false,
                    "Kein Bluetooth-SCO/HFP-Kommunikationsgerät verfügbar – Neustart nötig.");
        }
        try {
            boolean held;
            if (earpiece == null) {
                // No earpiece (e.g. tablets): release our request so Telecom re-evaluates.
                am.clearCommunicationDevice();
                held = true;
            } else {
                held = am.setCommunicationDevice(earpiece);
            }
            RepairStateStore.setRouteOwned(c, true);
            Diag.log(
                    c,
                    "ROUTE BOUNCE hold="
                            + held
                            + " via="
                            + (earpiece == null ? "clear" : Diag.device(earpiece)));
            return new RepairResult(true, held, held
                    ? "Telefonie-Audio wird über Bluetooth neu aufgebaut …"
                    : "Android hat den Zwischenschritt über den Hörer abgelehnt.");
        } catch (RuntimeException e) {
            Diag.log(c, "ROUTE BOUNCE ERROR " + e);
            return new RepairResult(true, false, "Neuaufbau fehlgeschlagen: " + e.getClass().getSimpleName());
        }
    }

    /** Second half of a route bounce: select the Bluetooth SCO device again. */
    static RepairResult completeRouteBounce(Context c) {
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return new RepairResult(true, false, "AudioManager nicht verfügbar.");
        AudioDeviceInfo bluetooth = null;
        try {
            for (AudioDeviceInfo device : am.getAvailableCommunicationDevices()) {
                if (isBluetoothCommunicationDevice(device)) {
                    bluetooth = device;
                    break;
                }
            }
            if (bluetooth == null) {
                am.clearCommunicationDevice();
                RepairStateStore.setRouteOwned(c, false);
                Diag.log(c, "ROUTE BOUNCE bluetooth-gone\n" + Diag.snapshot(c));
                return new RepairResult(true, false, "Bluetooth-Telefoniegerät ist verschwunden.");
            }
            boolean selected = am.setCommunicationDevice(bluetooth);
            RepairStateStore.setRouteOwned(c, true);
            Diag.log(
                    c,
                    "ROUTE BOUNCE reselect="
                            + selected
                            + " target="
                            + Diag.device(bluetooth)
                            + "\n"
                            + Diag.snapshot(c));
            return new RepairResult(true, selected, selected
                    ? "Bluetooth-Telefonie neu aufgebaut. Android bestätigt die Route asynchron."
                    : "Android hat die erneute Bluetooth-Auswahl abgelehnt.");
        } catch (RuntimeException e) {
            Diag.log(c, "ROUTE BOUNCE ERROR " + e);
            return new RepairResult(true, false, "Neuaufbau fehlgeschlagen: " + e.getClass().getSimpleName());
        }
    }

    /** Restores an audible call volume when the voice stream was muted or set to zero. */
    static RepairResult restoreCallVolume(Context c, CallAudioSignals signals) {
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return new RepairResult(false, false, "AudioManager nicht verfügbar.");
        try {
            if (signals != null && signals.voiceMuted) {
                am.adjustStreamVolume(
                        AudioManager.STREAM_VOICE_CALL,
                        AudioManager.ADJUST_UNMUTE,
                        0);
            }
            int target = signals == null ? -1 : signals.restoredVolumeIndex();
            if (target < 0) {
                target = Math.round(am.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL) * 0.6f);
            }
            am.setStreamVolume(AudioManager.STREAM_VOICE_CALL, target, AudioManager.FLAG_SHOW_UI);
            Diag.log(c, "CALL VOLUME RESTORE target=" + target + "\n" + Diag.snapshot(c));
            return new RepairResult(true, true, "Gesprächslautstärke wurde wiederhergestellt.");
        } catch (RuntimeException e) {
            Diag.log(c, "CALL VOLUME RESTORE ERROR " + e);
            return new RepairResult(true, false, "Lautstärke konnte nicht gesetzt werden.");
        }
    }

    static void releaseCommunicationRoute(Context c) {
        if (!RepairStateStore.routeOwned(c)) return;

        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            try {
                am.clearCommunicationDevice();
                Diag.log(c, "OWNED COMMUNICATION ROUTE CLEARED\n" + Diag.snapshot(c));
            } catch (RuntimeException e) {
                Diag.log(c, "COMMUNICATION ROUTE CLEAR ERROR " + e);
            }
        }
        RepairStateStore.setRouteOwned(c, false);
    }

    static boolean isBluetoothMediaOutput(AudioDeviceInfo device) {
        if (device == null) return false;
        int type = device.getType();
        return type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                || type == AudioDeviceInfo.TYPE_BLE_HEADSET
                || type == AudioDeviceInfo.TYPE_BLE_SPEAKER
                || type == AudioDeviceInfo.TYPE_BLE_BROADCAST;
    }

    static boolean isBluetoothCommunicationDevice(AudioDeviceInfo device) {
        if (device == null) return false;
        int type = device.getType();
        return type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                || type == AudioDeviceInfo.TYPE_BLE_HEADSET;
    }
}
