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

final class BluetoothRepair {
    static final class Probe {
        final BluetoothHealth health;
        final AudioDeviceInfo current;
        final AudioDeviceInfo bluetoothCommunicationDevice;

        Probe(BluetoothHealth health, AudioDeviceInfo current, AudioDeviceInfo bluetoothCommunicationDevice) {
            this.health = health;
            this.current = current;
            this.bluetoothCommunicationDevice = bluetoothCommunicationDevice;
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

    @SuppressWarnings("deprecation")
    static Probe probe(Context c) {
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
            return new Probe(health, null, null);
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
        boolean speakerphoneOn;
        try {
            speakerphoneOn = am.isSpeakerphoneOn();
        } catch (RuntimeException e) {
            speakerphoneOn = false;
        }

        BluetoothHealth health =
                BluetoothHealth.assess(
                        permission,
                        bluetoothEnabled,
                        am.getMode(),
                        hfpProfileConnected,
                        available,
                        selected,
                        speakerphoneOn);
        return new Probe(health, current, candidate);
    }

    static RepairResult repairCommunicationRoute(Context c, boolean force) {
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) {
            return new RepairResult(false, false, "AudioManager nicht verfügbar.");
        }

        Probe before = probe(c);
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
                RepairStateStore.markRepair(c);
                RepairStateStore.clearConsecutiveSuspect(c);
                return new RepairResult(true, true, "Bluetooth-Telefoniepfad wurde neu ausgewählt.");
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

    static boolean isBluetoothCommunicationDevice(AudioDeviceInfo device) {
        if (device == null) return false;
        int type = device.getType();
        return type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                || type == AudioDeviceInfo.TYPE_BLE_HEADSET;
    }
}
