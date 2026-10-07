package de.kaipressmar.a52srepair;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
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
        final FailureSignature signature;

        Probe(
                BluetoothHealth health,
                AudioDeviceInfo current,
                AudioDeviceInfo bluetoothCommunicationDevice,
                boolean bluetoothMediaOutputAvailable,
                boolean musicActive) {
            this.health = health;
            this.current = current;
            this.bluetoothCommunicationDevice = bluetoothCommunicationDevice;
            this.bluetoothMediaOutputAvailable = bluetoothMediaOutputAvailable;
            this.musicActive = musicActive;
            this.signature =
                    FailureSignature.classify(health, bluetoothMediaOutputAvailable);
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
            return new Probe(health, null, null, false, false);
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

        BluetoothHealth health =
                BluetoothHealth.assess(
                        permission,
                        bluetoothEnabled,
                        am.getMode(),
                        hfpProfileConnected,
                        available,
                        selected,
                        speakerphoneOn,
                        cellularCallActiveHint);
        return new Probe(health, current, candidate, mediaAvailable, musicActive);
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
