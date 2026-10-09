package de.kaipressmar.a52srepair.core.diagnosis;

import de.kaipressmar.a52srepair.core.model.AudioRoute;
import de.kaipressmar.a52srepair.core.model.CallAudioSnapshot;

/** Pure, stateless mapping from one snapshot to the most fundamental visible fault. */
public final class FaultClassifier {
    private FaultClassifier() {}

    public static Fault classify(CallAudioSnapshot s) {
        if (s == null || !s.phase.carriesAudio()) return Fault.NONE;

        // Speaker and wired headsets are deliberate choices, never a Bluetooth fault.
        if (s.route == AudioRoute.SPEAKER
                || s.route == AudioRoute.WIRED_HEADSET
                || s.route == AudioRoute.STREAMING) {
            return Fault.NONE;
        }

        if (s.route == AudioRoute.UNKNOWN) return Fault.NONE;
        if (!s.bluetoothRouteAvailable) {
            return s.hfpConnected ? Fault.BLUETOOTH_ROUTE_MISSING : Fault.NONE;
        }

        if (s.route != AudioRoute.BLUETOOTH) return Fault.CALL_NOT_ON_BLUETOOTH;
        if (Boolean.FALSE.equals(s.scoAudioConnected)) return Fault.SCO_DISCONNECTED;
        if (Boolean.FALSE.equals(s.voiceOnBluetooth)) return Fault.DOWNLINK_NOT_ON_BLUETOOTH;
        if (s.voiceSilenced()) return Fault.VOICE_SILENCED;
        return Fault.NONE;
    }
}
