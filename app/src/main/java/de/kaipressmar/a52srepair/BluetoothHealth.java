package de.kaipressmar.a52srepair;

import android.media.AudioManager;

final class BluetoothHealth {
    enum State {
        PERMISSION_REQUIRED,
        BLUETOOTH_OFF,
        IDLE,
        HEALTHY,
        SUSPECT_ROUTING,
        HFP_CONNECTED_NO_SCO,
        CALL_WITHOUT_SCO,
        ERROR
    }

    final State state;
    final boolean inCommunication;
    final boolean hfpProfileConnected;
    final boolean scoAvailable;
    final boolean scoSelected;
    final boolean speakerphoneOn;
    final String summary;
    final String detail;

    BluetoothHealth(
            State state,
            boolean inCommunication,
            boolean hfpProfileConnected,
            boolean scoAvailable,
            boolean scoSelected,
            boolean speakerphoneOn,
            String summary,
            String detail) {
        this.state = state;
        this.inCommunication = inCommunication;
        this.hfpProfileConnected = hfpProfileConnected;
        this.scoAvailable = scoAvailable;
        this.scoSelected = scoSelected;
        this.speakerphoneOn = speakerphoneOn;
        this.summary = summary;
        this.detail = detail;
    }

    boolean needsRepair() {
        return state == State.SUSPECT_ROUTING;
    }

    static BluetoothHealth assess(
            boolean permission,
            boolean bluetoothEnabled,
            int audioMode,
            boolean hfpProfileConnected,
            boolean scoAvailable,
            boolean scoSelected,
            boolean speakerphoneOn) {
        boolean inCommunication =
                audioMode == AudioManager.MODE_IN_CALL
                        || audioMode == AudioManager.MODE_IN_COMMUNICATION;

        if (!permission) {
            return new BluetoothHealth(
                    State.PERMISSION_REQUIRED,
                    inCommunication,
                    false,
                    scoAvailable,
                    scoSelected,
                    speakerphoneOn,
                    "Bluetooth-Berechtigung fehlt",
                    "BLUETOOTH_CONNECT muss erlaubt sein.");
        }

        if (!bluetoothEnabled) {
            return new BluetoothHealth(
                    State.BLUETOOTH_OFF,
                    inCommunication,
                    false,
                    scoAvailable,
                    scoSelected,
                    speakerphoneOn,
                    "Bluetooth ist ausgeschaltet",
                    "Aktiviere Bluetooth, damit Telefonie-Audio geprüft werden kann.");
        }

        if (!inCommunication) {
            return new BluetoothHealth(
                    scoSelected ? State.HEALTHY : State.IDLE,
                    false,
                    hfpProfileConnected,
                    scoAvailable,
                    scoSelected,
                    speakerphoneOn,
                    scoSelected ? "Telefonie-Audio verbunden" : "Bereit – kein aktiver Anruf",
                    hfpProfileConnected
                            ? "Ein HFP-Gerät ist verbunden. Der Telefoniepfad wird bei einem aktiven Anruf gezielt geprüft."
                            : (scoAvailable
                                    ? "Ein Bluetooth-Telefoniegerät ist verfügbar. Die automatische Prüfung wird bei einem aktiven Anruf schärfer."
                                    : "Aktuell ist kein Bluetooth-HFP/SCO-Gerät für Telefonie verbunden."));
        }

        if (scoAvailable && scoSelected) {
            return new BluetoothHealth(
                    State.HEALTHY,
                    true,
                    hfpProfileConnected,
                    true,
                    true,
                    speakerphoneOn,
                    "Telefonie-Audio ist korrekt geroutet",
                    "Ein Bluetooth-SCO/HFP-Gerät ist während der Kommunikation aktiv.");
        }

        if (scoAvailable) {
            return new BluetoothHealth(
                    State.SUSPECT_ROUTING,
                    true,
                    hfpProfileConnected,
                    true,
                    false,
                    speakerphoneOn,
                    "Möglicher HFP/SCO-Routingfehler erkannt",
                    "Ein Bluetooth-Telefoniegerät ist verfügbar, aber nicht als Kommunikationsgerät ausgewählt.");
        }

        if (hfpProfileConnected) {
            return new BluetoothHealth(
                    State.HFP_CONNECTED_NO_SCO,
                    true,
                    true,
                    false,
                    false,
                    speakerphoneOn,
                    "HFP verbunden, aber SCO fehlt",
                    "Das Headset-/Freisprechprofil ist verbunden, Android bietet aber kein routbares SCO/HFP-Kommunikationsgerät an. Das entspricht dem bekannten A52s-Fehlerbild; eine normale App kann den System-Bluetoothdienst nicht privilegiert neu starten.");
        }

        return new BluetoothHealth(
                State.CALL_WITHOUT_SCO,
                true,
                false,
                false,
                false,
                speakerphoneOn,
                "Anruf aktiv – kein Bluetooth-SCO verfügbar",
                "Der Bluetooth-Telefoniepfad wird vom System aktuell nicht als Kommunikationsgerät angeboten.");
    }
}
