package de.kaipressmar.a52srepair.core.model;

/** Simplified Telecom call state. Only DIALING and ACTIVE carry call audio. */
public enum CallPhase {
    RINGING,
    DIALING,
    ACTIVE,
    HOLDING,
    ENDED;

    public boolean carriesAudio() {
        return this == DIALING || this == ACTIVE;
    }
}
