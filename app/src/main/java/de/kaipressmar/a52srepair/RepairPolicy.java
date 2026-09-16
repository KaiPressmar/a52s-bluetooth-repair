package de.kaipressmar.a52srepair;

/** Pure decision logic: deliberately contains no Android calls so every safety rule is unit-testable. */
final class RepairPolicy {
    enum Action { NONE, RESET_ROUTING, RESTART_SCO }

    static final class State {
        final boolean bluetoothEnabled;
        final boolean scoOutputAvailable;
        final boolean scoSelected;
        final boolean callOrCommunicationActive;
        final int consecutiveSuspectSamples;

        State(boolean bluetoothEnabled, boolean scoOutputAvailable, boolean scoSelected,
              boolean callOrCommunicationActive, int consecutiveSuspectSamples) {
            this.bluetoothEnabled = bluetoothEnabled;
            this.scoOutputAvailable = scoOutputAvailable;
            this.scoSelected = scoSelected;
            this.callOrCommunicationActive = callOrCommunicationActive;
            this.consecutiveSuspectSamples = consecutiveSuspectSamples;
        }
    }

    private RepairPolicy() {}

    static Action recommend(State s) {
        // Never mutate audio when Bluetooth is off, no SCO endpoint exists, or a call is active.
        if (!s.bluetoothEnabled || !s.scoOutputAvailable || s.callOrCommunicationActive) return Action.NONE;
        // A healthy selected SCO route needs no repair.
        if (s.scoSelected) return Action.NONE;
        // Require repeated evidence before suggesting the more disruptive SCO restart.
        if (s.consecutiveSuspectSamples >= 3) return Action.RESTART_SCO;
        return Action.RESET_ROUTING;
    }
}
