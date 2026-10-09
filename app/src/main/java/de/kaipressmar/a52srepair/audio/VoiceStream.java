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
        if (audio == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null;
        try {
            AudioAttributes voice =
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .build();
            List<AudioDeviceInfo> devices = audio.getAudioDevicesForAttributes(voice);
            if (devices == null || devices.isEmpty()) return null;
            for (AudioDeviceInfo device : devices) {
                int type = device.getType();
                if (type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                        || type == AudioDeviceInfo.TYPE_BLE_HEADSET) {
                    return Boolean.TRUE;
                }
            }
            return Boolean.FALSE;
        } catch (RuntimeException e) {
            return null;
        }
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
