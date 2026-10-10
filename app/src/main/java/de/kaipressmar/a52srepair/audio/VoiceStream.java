package de.kaipressmar.a52srepair.audio;

import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import java.util.List;

/** Downlink-side evidence and the volume repair for the voice-call stream. */
public final class VoiceStream {
    private final AudioManager audio;

    public VoiceStream(AudioManager audio) {
        this.audio = audio;
    }

    /** Predicted Bluetooth routing for voice attributes; not proof of audible speech. */
    public Boolean playsOnBluetooth() {
        return evidence().onBluetooth;
    }

    public static final class Evidence {
        public final Boolean onBluetooth;
        public final boolean requiresSco;
        Evidence(Boolean onBluetooth, boolean requiresSco) {
            this.onBluetooth = onBluetooth;
            this.requiresSco = requiresSco;
        }
    }

    public Evidence evidence() {
        if (audio == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return new Evidence(null, true);
        try {
            AudioAttributes voice =
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .build();
            List<AudioDeviceInfo> devices = audio.getAudioDevicesForAttributes(voice);
            if (devices == null) return new Evidence(null, true);
            return evidenceForTypes(devices.stream().mapToInt(AudioDeviceInfo::getType).toArray());
        } catch (RuntimeException e) {
            return new Evidence(null, true);
        }
    }

    static Evidence evidenceForTypes(int... types) {
        if (types.length == 0) return new Evidence(null, true);
        boolean sco = false, ble = false;
        for (int type : types) {
            sco |= type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO;
            ble |= type == AudioDeviceInfo.TYPE_BLE_HEADSET;
        }
        // LE Audio does not establish a classic HFP SCO link. Do not diagnose it as disconnected.
        return new Evidence(sco || ble, sco || !ble);
    }

    public int volume() {
        try {
            return audio == null ? -1 : audio.getStreamVolume(AudioManager.STREAM_VOICE_CALL);
        } catch (RuntimeException e) {
            return -1;
        }
    }

    public int maxVolume() {
        try {
            return audio == null ? -1 : audio.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL);
        } catch (RuntimeException e) {
            return -1;
        }
    }

    public boolean muted() {
        try {
            return audio != null && audio.isStreamMute(AudioManager.STREAM_VOICE_CALL);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Unmutes the voice stream and sets about 60 % volume. Returns the new index or -1. */
    public int restoreAudibleVolume() {
        if (audio == null) return -1;
        try {
            audio.adjustStreamVolume(AudioManager.STREAM_VOICE_CALL, AudioManager.ADJUST_UNMUTE, 0);
            int max = audio.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL);
            int min = audio.getStreamMinVolume(AudioManager.STREAM_VOICE_CALL);
            int target = Math.max(Math.max(1, min), Math.round(max * 0.6f));
            audio.setStreamVolume(AudioManager.STREAM_VOICE_CALL, target, 0);
            return target;
        } catch (RuntimeException e) {
            return -1;
        }
    }
}
