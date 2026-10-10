package de.kaipressmar.a52srepair.audio;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.telecom.TelecomManager;
import de.kaipressmar.a52srepair.core.repair.IdleRepairEngine;
import de.kaipressmar.a52srepair.diagnostics.AudioDiagnostics;
import de.kaipressmar.a52srepair.diagnostics.DiagnosticLog;
import de.kaipressmar.a52srepair.telecom.CallAudioService;
import java.util.List;

/** Android ownership and safety boundary for a foreground-only off-call channel test. */
public final class IdleAudioPort implements IdleRepairEngine.Port {
    private final Context context;
    private final AudioManager audio;
    private final TelecomManager telecom;
    private final HeadsetAccess headset;
    private final String operation;
    private BluetoothDevice target;
    private AudioTrack track;
    private boolean modeOwned, modernOwned, legacyOwned, voiceOwned;
    private String lastState = "";

    public IdleAudioPort(Context context, Runnable changed, String operation) {
        this(context, isolatedAudio(context), context.getSystemService(TelecomManager.class),
                new HeadsetMonitor(context, changed), operation);
    }
    private static AudioManager isolatedAudio(Context context) {
        // Separate AudioManager instance => distinct communication-device Binder token. A late
        // idle cleanup must never clear CallSession's subsequent selection in this process.
        return context.createConfigurationContext(context.getResources().getConfiguration())
                .getSystemService(AudioManager.class);
    }
    IdleAudioPort(Context context, AudioManager audio, TelecomManager telecom, HeadsetAccess headset, String operation) {
        this.context = context.getApplicationContext();
        this.audio = audio;
        this.telecom = telecom;
        this.headset = headset;
        this.operation = operation;
    }
    public void open() { headset.open(); }
    public void closeObserver() { headset.close(); }
    void resetIdleObservationTarget() {
        if (!modernOwned && !legacyOwned && !voiceOwned && !modeOwned && track == null) target = null;
    }
    @Override public String blockedReason() {
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
            return "Bluetooth permission missing";
        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED)
            return "phone status permission missing";
        if (audio == null || telecom == null) return "audio/phone status unavailable";
        try {
            if (CallAudioService.activeReport() != null || telecom.isInCall()) return "call present";
            int mode = audio.getMode();
            if (mode != AudioManager.MODE_NORMAL && !(modeOwned && mode == AudioManager.MODE_IN_COMMUNICATION))
                return "audio mode occupied=" + mode;
            if (audio.isMusicActive()) return "media playback active";
            for (AudioDeviceInfo device : audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                int type = device.getType();
                if (type == AudioDeviceInfo.TYPE_WIRED_HEADSET || type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                        || type == AudioDeviceInfo.TYPE_USB_HEADSET || type == AudioDeviceInfo.TYPE_HEARING_AID)
                    return "alternative audio device present";
            }
            return null;
        } catch (RuntimeException e) { return "safety observation failed=" + e.getClass().getSimpleName(); }
    }
    @Override public boolean targetReady() {
        headset.open();
        List<BluetoothDevice> devices = headset.connectedDevices();
        if (devices.size() != 1) return false;
        if (target == null) target = devices.get(0);
        return target.equals(devices.get(0));
    }
    @Override public Boolean scoConnected() { return headset.scoAudioConnected(target); }
    @SuppressLint("MissingPermission")
    private AudioDeviceInfo matchingPort() {
        if (target == null || audio == null) return null;
        AudioDeviceInfo found = null;
        for (AudioDeviceInfo device : audio.getAvailableCommunicationDevices()) {
            if (device.isSink() && device.getId() != 0 && device.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                    && !device.getAddress().isEmpty() && device.getAddress().equalsIgnoreCase(target.getAddress())) {
                if (found != null) return null;
                found = device;
            }
        }
        return found;
    }
    @Override public boolean supports(IdleRepairEngine.Method method) {
        try {
            switch (method) {
                case MODERN: return matchingPort() != null;
                case LEGACY_SCO: return targetReady() && audio != null && audio.isBluetoothScoAvailableOffCall();
                case VOICE_RECOGNITION: return headset.supportsVoiceRecognition(target);
                default: return false;
            }
        } catch (RuntimeException e) { log("SUPPORT_ERROR " + e.getClass().getSimpleName()); return false; }
    }
    @Override @SuppressWarnings("deprecation")
    public boolean start(IdleRepairEngine.Method method) {
        if (blockedReason() != null || !targetReady() || !Boolean.FALSE.equals(scoConnected())) return false;
        try {
            if (method == IdleRepairEngine.Method.VOICE_RECOGNITION) {
                // Keep mode NORMAL here: HFP voice-recognition is a different native state path.
                voiceOwned = true; // Binder can accept a request before throwing.
                boolean accepted = headset.startVoiceRecognition(target);
                if (!accepted) voiceOwned = false;
                return accepted;
            }
            // A silent finite voice playback keeps modern Android's communication client active.
            // No microphone recording or RECORD_AUDIO permission; no focus or volume changes.
            modeOwned = true;
            audio.setMode(AudioManager.MODE_IN_COMMUNICATION);
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(8_000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(16_000).build();
            // MODE_STATIC starts in STATE_NO_STATIC_DATA; writing initializes it.
            if (track.write(new byte[16_000], 0, 16_000) != 16_000 || track.getState() != AudioTrack.STATE_INITIALIZED)
                return false;
            if (track.setLoopPoints(0, 8_000, -1) != AudioTrack.SUCCESS) return false;
            track.play();
            if (method == IdleRepairEngine.Method.MODERN) {
                AudioDeviceInfo port = matchingPort();
                if (port == null) return false;
                modernOwned = true;
                return audio.setCommunicationDevice(port);
            }
            legacyOwned = true;
            audio.startBluetoothSco();
            return true; // Void API: requested, not a connection acknowledgement.
        } catch (RuntimeException e) { log("START_ERROR method=" + method + " exception=" + e.getClass().getSimpleName()); return false; }
    }
    @Override @SuppressWarnings("deprecation")
    @SuppressLint("MissingPermission") // Start checks permission; revocation is caught during cleanup.
    public boolean release() {
        if (modernOwned) {
            try { audio.clearCommunicationDevice(); modernOwned = false; }
            catch (RuntimeException e) { log("CLEAR_ERROR " + e.getClass().getSimpleName()); }
        }
        if (legacyOwned) {
            try { audio.stopBluetoothSco(); legacyOwned = false; }
            catch (RuntimeException e) { log("STOP_SCO_ERROR " + e.getClass().getSimpleName()); }
        }
        if (voiceOwned) {
            try {
                if (telecom != null && telecom.isInCall()) {
                    // HFP's system call handover now owns audio. Do not tear its channel down.
                    voiceOwned = false;
                    log("VOICE_HANDOVER call present; no native stop issued");
                }
                // Never stop voice recognition after a target swap/disappearance.
                else if (targetReady()) {
                    boolean stopped = headset.stopVoiceRecognition(target);
                    if (stopped || Boolean.FALSE.equals(scoConnected())) voiceOwned = false;
                    log("STOP_VOICE accepted=" + stopped);
                } else if (!headset.connectedDevices().contains(target)) voiceOwned = false;
            } catch (RuntimeException e) { log("STOP_VOICE_ERROR " + e.getClass().getSimpleName()); }
        }
        if (track != null) {
            AudioTrack owned = track;
            try { owned.release(); track = null; }
            catch (RuntimeException e) { log("TRACK_RELEASE_ERROR " + e.getClass().getSimpleName()); }
        }
        if (modeOwned) {
            try {
                // MODE_NORMAL removes THIS caller's mode request; it does not restore another
                // process's stale mode. Never set a saved global mode over a new phone call.
                audio.setMode(AudioManager.MODE_NORMAL);
                modeOwned = false;
            } catch (RuntimeException e) { log("MODE_RELEASE_ERROR " + e.getClass().getSimpleName()); }
        }
        return !modernOwned && !legacyOwned && !voiceOwned && track == null && !modeOwned;
    }
    public void observe() {
        String state = "hfpCount=" + headset.connectedDevices().size() + " targetReady=" + targetReady()
                + " sco=" + scoConnected() + " voiceSupported=" + headset.supportsVoiceRecognition(target)
                + " owned=" + modernOwned + '/' + legacyOwned + '/' + voiceOwned + '/' + modeOwned
                + " " + AudioDiagnostics.capture(audio, target);
        if (!state.equals(lastState)) { lastState = state; log("OBSERVE " + state); }
    }
    @Override public void log(String event) { DiagnosticLog.log(context, "IDLE operation=" + operation + " " + event); }
}
