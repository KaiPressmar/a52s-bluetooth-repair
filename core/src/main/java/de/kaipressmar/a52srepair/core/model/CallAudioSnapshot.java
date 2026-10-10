package de.kaipressmar.a52srepair.core.model;

import java.util.Locale;

/**
 * Immutable view of the call-audio state at one moment, combining Telecom's call audio state with
 * the AudioManager/Bluetooth evidence. {@code null} Boolean values mean "unknown on this device".
 */
public final class CallAudioSnapshot {
    public final CallPhase phase;
    public final AudioRoute route;
    public final boolean bluetoothRouteAvailable;
    public final boolean earpieceAvailable;
    public final boolean hfpConnected;
    public final Boolean scoAudioConnected;
    public final Boolean voiceOnBluetooth;
    public final int voiceVolume;
    public final int voiceMaxVolume;
    public final boolean voiceMuted;
    public final boolean microphoneMuted;
    public final int callCount;

    private CallAudioSnapshot(Builder b) {
        phase = b.phase;
        route = b.route;
        bluetoothRouteAvailable = b.bluetoothRouteAvailable;
        earpieceAvailable = b.earpieceAvailable;
        hfpConnected = b.hfpConnected;
        scoAudioConnected = b.scoAudioConnected;
        voiceOnBluetooth = b.voiceOnBluetooth;
        voiceVolume = b.voiceVolume;
        voiceMaxVolume = b.voiceMaxVolume;
        voiceMuted = b.voiceMuted;
        microphoneMuted = b.microphoneMuted;
        callCount = b.callCount;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .phase(phase)
                .route(route)
                .bluetoothRouteAvailable(bluetoothRouteAvailable)
                .earpieceAvailable(earpieceAvailable)
                .hfpConnected(hfpConnected)
                .scoAudioConnected(scoAudioConnected)
                .voiceOnBluetooth(voiceOnBluetooth)
                .voiceVolume(voiceVolume, voiceMaxVolume)
                .voiceMuted(voiceMuted)
                .microphoneMuted(microphoneMuted)
                .callCount(callCount);
    }

    /** Voice stream is muted or at index 0: the caller is inaudible on every route. */
    public boolean voiceSilenced() {
        return voiceMuted || (voiceMaxVolume > 0 && voiceVolume == 0);
    }

    @Override
    public String toString() {
        return String.format(
                Locale.ROOT,
                "phase=%s route=%s btAvailable=%s earpiece=%s hfp=%s sco=%s voiceOnBt=%s volume=%s muted=%s calls=%d micMuted=%s",
                phase,
                route,
                bluetoothRouteAvailable,
                earpieceAvailable,
                hfpConnected,
                scoAudioConnected == null ? "?" : scoAudioConnected,
                voiceOnBluetooth == null ? "?" : voiceOnBluetooth,
                voiceMaxVolume > 0 ? voiceVolume + "/" + voiceMaxVolume : "?",
                voiceMuted,
                callCount,
                microphoneMuted);
    }

    public static final class Builder {
        private CallPhase phase = CallPhase.ACTIVE;
        private AudioRoute route = AudioRoute.UNKNOWN;
        private boolean bluetoothRouteAvailable;
        private boolean earpieceAvailable = true;
        private boolean hfpConnected;
        private Boolean scoAudioConnected;
        private Boolean voiceOnBluetooth;
        private int voiceVolume = -1;
        private int voiceMaxVolume = -1;
        private boolean voiceMuted;
        private boolean microphoneMuted;
        private int callCount = 1;

        public Builder phase(CallPhase value) { phase = value; return this; }
        public Builder route(AudioRoute value) { route = value; return this; }
        public Builder bluetoothRouteAvailable(boolean value) { bluetoothRouteAvailable = value; return this; }
        public Builder earpieceAvailable(boolean value) { earpieceAvailable = value; return this; }
        public Builder hfpConnected(boolean value) { hfpConnected = value; return this; }
        public Builder scoAudioConnected(Boolean value) { scoAudioConnected = value; return this; }
        public Builder voiceOnBluetooth(Boolean value) { voiceOnBluetooth = value; return this; }
        public Builder voiceVolume(int volume, int max) { voiceVolume = volume; voiceMaxVolume = max; return this; }
        public Builder voiceMuted(boolean value) { voiceMuted = value; return this; }
        public Builder microphoneMuted(boolean value) { microphoneMuted = value; return this; }
        public Builder callCount(int value) { callCount = value; return this; }

        public CallAudioSnapshot build() {
            return new CallAudioSnapshot(this);
        }
    }
}
