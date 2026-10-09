package de.kaipressmar.a52srepair;

/**
 * Downlink-side evidence for an active call, collected next to the communication-route probe.
 *
 * The A52s "they hear me, I hear nothing" failure keeps the SCO route selected, so the classic
 * route checks report a healthy call. These signals describe where Android actually plays voice
 * audio and whether that stream is audible. Every field may be unknown on a given build.
 */
final class CallAudioSignals {
    static final CallAudioSignals UNKNOWN =
            new CallAudioSignals(null, -1, -1, -1, false, false);

    /** TRUE/FALSE when Android reports the voice playback device, null when unknown (API < 33). */
    final Boolean voiceRouteOnBluetooth;
    final int voiceVolume;
    final int voiceMinVolume;
    final int voiceMaxVolume;
    final boolean voiceMuted;
    /** Telephony reports an off-hook call while AudioManager still reports MODE_NORMAL. */
    final boolean callModeMismatch;

    CallAudioSignals(
            Boolean voiceRouteOnBluetooth,
            int voiceVolume,
            int voiceMinVolume,
            int voiceMaxVolume,
            boolean voiceMuted,
            boolean callModeMismatch) {
        this.voiceRouteOnBluetooth = voiceRouteOnBluetooth;
        this.voiceVolume = voiceVolume;
        this.voiceMinVolume = voiceMinVolume;
        this.voiceMaxVolume = voiceMaxVolume;
        this.voiceMuted = voiceMuted;
        this.callModeMismatch = callModeMismatch;
    }

    boolean volumeKnown() {
        return voiceVolume >= 0 && voiceMaxVolume > 0;
    }

    /** Voice stream is muted or at index 0, i.e. the caller is inaudible on any route. */
    boolean voiceSilenced() {
        return voiceMuted || (volumeKnown() && voiceVolume <= 0);
    }

    /** Volume index that makes the caller clearly audible without being uncomfortably loud. */
    int restoredVolumeIndex() {
        if (!volumeKnown()) return -1;
        int target = Math.round(voiceMaxVolume * 0.6f);
        return Math.max(Math.max(1, voiceMinVolume), Math.min(voiceMaxVolume, target));
    }

    String describe() {
        return "voiceRouteBt="
                + (voiceRouteOnBluetooth == null ? "unknown" : voiceRouteOnBluetooth)
                + " voiceVolume="
                + (volumeKnown()
                        ? voiceVolume + "/" + voiceMinVolume + ".." + voiceMaxVolume
                        : "unknown")
                + " voiceMuted="
                + voiceMuted
                + " callModeMismatch="
                + callModeMismatch;
    }
}
