package de.kaipressmar.a52srepair.core.repair;

/** Side effects the Android layer executes on behalf of {@link CallRepairEngine}. */
public enum RepairCommand {
    /** Ask Telecom to play the call on the car/headset. */
    ROUTE_TO_BLUETOOTH,
    /** First half of a rebuild: move the call to the earpiece so SCO is torn down. */
    ROUTE_TO_EARPIECE,
    /** Last bounded rebuild uses the older Telecom API, not an AudioManager override. */
    ROUTE_TO_BLUETOOTH_LEGACY,
    ROUTE_TO_EARPIECE_LEGACY,
    /** Reacquire observations only; does not reset the system Bluetooth service. */
    REFRESH_BLUETOOTH_OBSERVATIONS,
    /** Last-resort, target-matched AudioManager request without changing the audio mode. */
    TRY_COMMUNICATION_DEVICE,
    /** Unmute and raise the voice-call stream to an audible level. */
    RESTORE_VOICE_VOLUME
}
