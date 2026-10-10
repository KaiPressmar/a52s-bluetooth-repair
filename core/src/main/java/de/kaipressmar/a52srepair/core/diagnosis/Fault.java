package de.kaipressmar.a52srepair.core.diagnosis;

/** Observable call-audio faults, ordered from "route" to "audio content" problems. */
public enum Fault {
    NONE(false),
    /** Car/headset is a usable route, but Telecom plays the call elsewhere. */
    CALL_NOT_ON_BLUETOOTH(true),
    /** Telecom reports the Bluetooth route, but the HFP SCO link is down. */
    SCO_DISCONNECTED(true),
    /** Uplink on Bluetooth, but the voice downlink is played on another device. */
    DOWNLINK_NOT_ON_BLUETOOTH(true),
    /** Voice-call stream is muted or at volume 0. */
    VOICE_SILENCED(true),
    /** HFP is connected, but Telecom offers no Bluetooth route to request. */
    BLUETOOTH_ROUTE_MISSING(false),
    /** Explicit user report; not a fault inferred from Android's audio signals. */
    USER_REPORTED_AUDIO_PROBLEM(true);

    public final boolean repairable;

    Fault(boolean repairable) {
        this.repairable = repairable;
    }
}
