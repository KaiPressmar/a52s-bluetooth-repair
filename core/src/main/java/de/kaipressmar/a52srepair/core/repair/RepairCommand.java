package de.kaipressmar.a52srepair.core.repair;

/** Side effects the Android layer executes on behalf of {@link CallRepairEngine}. */
public enum RepairCommand {
    /** Ask Telecom to play the call on the car/headset. */
    ROUTE_TO_BLUETOOTH,
    /** First half of a rebuild: move the call to the earpiece so SCO is torn down. */
    ROUTE_TO_EARPIECE,
    /** Unmute and raise the voice-call stream to an audible level. */
    RESTORE_VOICE_VOLUME
}
