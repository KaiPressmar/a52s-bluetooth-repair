package de.kaipressmar.a52srepair.diagnostics;

import android.bluetooth.BluetoothDevice;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import java.util.List;

/** Public API observations only. No device names, addresses, phone numbers or recorded audio. */
public final class AudioDiagnostics {
    private AudioDiagnostics() {}
    public static String capture(AudioManager audio, BluetoothDevice target) {
        if (audio == null) return "audioManager=absent";
        try {
            StringBuilder out = new StringBuilder("mode=").append(audio.getMode())
                    .append(" micMuted=").append(audio.isMicrophoneMute())
                    .append(" musicActive=").append(audio.isMusicActive())
                    .append(" offCallSco=").append(audio.isBluetoothScoAvailableOffCall())
                    .append(" selected=").append(device(audio.getCommunicationDevice(), target));
            List<AudioDeviceInfo> ports = audio.getAvailableCommunicationDevices();
            out.append(" ports=[");
            for (AudioDeviceInfo port : ports) out.append(device(port, target)).append(';');
            return out.append(']').toString();
        } catch (RuntimeException e) { return "audioObservationError=" + e.getClass().getSimpleName(); }
    }
    public static String device(AudioDeviceInfo device, BluetoothDevice target) {
        if (device == null) return "none";
        boolean match = target != null && !device.getAddress().isEmpty()
                && device.getAddress().equalsIgnoreCase(target.getAddress());
        return "{id=" + device.getId() + ",type=" + device.getType() + ",sink=" + device.isSink()
                + ",targetMatch=" + match + ",addressPresent=" + !device.getAddress().isEmpty() + '}';
    }
}
