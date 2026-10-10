package de.kaipressmar.a52srepair.audio;

import android.bluetooth.BluetoothDevice;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import de.kaipressmar.a52srepair.core.model.AudioRoute;
import de.kaipressmar.a52srepair.core.model.CallAudioSnapshot;

/** Bounded AudioPolicy experiment. Telephony retains mode ownership and may ignore this request. */
public final class CommunicationDeviceRecovery {
    public static final long REQUEST_LIFETIME_MS = 15_000L;
    private final AudioManager audio;
    private boolean ownsRequest;
    private long releaseAt;

    public CommunicationDeviceRecovery(AudioManager audio) { this.audio = audio; }

    /** Never choose a different peripheral or a media-only Bluetooth output. */
    public boolean request(BluetoothDevice target, long now) {
        if (audio == null || target == null || ownsRequest) return false;
        try {
            AudioDeviceInfo chosen = null;
            String address = target.getAddress();
            for (AudioDeviceInfo device : audio.getAvailableCommunicationDevices()) {
                int type = device.getType();
                if (!device.isSink() || device.getId() == 0
                        || (type != AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                            && type != AudioDeviceInfo.TYPE_BLE_HEADSET)
                        || !address.equalsIgnoreCase(device.getAddress())) continue;
                if (chosen != null) return false;
                chosen = device;
            }
            if (chosen == null) return false;
            // A Binder exception can occur after the remote side accepted a request.
            ownsRequest = true;
            releaseAt = now + REQUEST_LIFETIME_MS;
            if (!audio.setCommunicationDevice(chosen)) {
                close();
                return false;
            }
            return true; // Accepted is not proof of actual routing or audible speech.
        } catch (RuntimeException failure) {
            close();
            return false;
        }
    }

    /** Cancel on call lifecycle/alternative routing, or when the experiment's deadline expires. */
    public void maintain(CallAudioSnapshot state, long now, boolean allowed) {
        if (!ownsRequest) return;
        if (!allowed || now >= releaseAt || !state.phase.carriesAudio() || state.callCount != 1
                || !state.bluetoothRouteAvailable || state.route == AudioRoute.SPEAKER
                || state.route == AudioRoute.WIRED_HEADSET || state.route == AudioRoute.STREAMING) close();
    }

    /** Only cancel this app's own request, never another app's AudioManager selection. */
    public void close() {
        if (!ownsRequest) return;
        try {
            audio.clearCommunicationDevice();
            ownsRequest = false;
        } catch (RuntimeException ignored) {
            // Retain ownership so subsequent snapshots/service destruction retry cleanup.
        }
    }

    public boolean hasRequest() { return ownsRequest; }

    public long nextCleanupInMs(long now) {
        return ownsRequest ? (now >= releaseAt ? 5_000L : releaseAt - now) : Long.MAX_VALUE;
    }
}
