package de.kaipressmar.a52srepair;

/**
 * Pure classifier for the Bluetooth call-audio failure patterns observed in the field.
 *
 * Keeping this separate from Android framework calls makes the known failure signatures fully
 * testable and prevents the watchdog from treating every non-Bluetooth call route as the same bug.
 */
final class FailureSignature {
    enum Kind {
        IDLE,
        HEALTHY_CALL,
        RECOVERABLE_ROUTE_DRIFT,
        DOWNLINK_ROUTE_MISMATCH,
        DOWNLINK_SILENCED,
        HFP_CONNECTED_NO_SCO_MEDIA_ALIVE,
        HFP_CONNECTED_NO_SCO,
        CALL_WITHOUT_BLUETOOTH_TELEPHONY,
        SYSTEM_UNAVAILABLE,
        ERROR
    }

    enum Confidence {
        NONE,
        LOW,
        MEDIUM,
        HIGH
    }

    final Kind kind;
    final Confidence confidence;
    final boolean publicApiRepairPossible;
    final String label;

    FailureSignature(
            Kind kind,
            Confidence confidence,
            boolean publicApiRepairPossible,
            String label) {
        this.kind = kind;
        this.confidence = confidence;
        this.publicApiRepairPossible = publicApiRepairPossible;
        this.label = label;
    }

    static FailureSignature classify(
            BluetoothHealth health,
            boolean bluetoothMediaOutputAvailable) {
        return classify(health, bluetoothMediaOutputAvailable, CallAudioSignals.UNKNOWN);
    }

    static FailureSignature classify(
            BluetoothHealth health,
            boolean bluetoothMediaOutputAvailable,
            CallAudioSignals signals) {
        if (health == null || health.state == BluetoothHealth.State.ERROR) {
            return new FailureSignature(
                    Kind.ERROR,
                    Confidence.LOW,
                    false,
                    "Audiozustand konnte nicht sicher bestimmt werden");
        }

        if (health.state == BluetoothHealth.State.PERMISSION_REQUIRED
                || health.state == BluetoothHealth.State.BLUETOOTH_OFF) {
            return new FailureSignature(
                    Kind.SYSTEM_UNAVAILABLE,
                    Confidence.NONE,
                    false,
                    "Prüfung aktuell nicht möglich");
        }

        if (!health.inCommunication) {
            return new FailureSignature(
                    Kind.IDLE,
                    Confidence.NONE,
                    false,
                    "Kein aktiver Telefonie-Audiopfad");
        }

        if (health.state == BluetoothHealth.State.HEALTHY) {
            FailureSignature downlink = classifyDownlink(health, signals);
            if (downlink != null) return downlink;
            return new FailureSignature(
                    Kind.HEALTHY_CALL,
                    Confidence.HIGH,
                    false,
                    "Bluetooth-Telefoniepfad ist intakt");
        }

        if (health.state == BluetoothHealth.State.SUSPECT_ROUTING) {
            return new FailureSignature(
                    Kind.RECOVERABLE_ROUTE_DRIFT,
                    Confidence.HIGH,
                    !health.speakerphoneOn,
                    health.speakerphoneOn
                            ? "Bluetooth wäre verfügbar, Lautsprecher ist aber bewusst aktiv"
                            : "SCO/HFP ist verfügbar, aber nicht als Kommunikationsroute aktiv");
        }

        if (health.state == BluetoothHealth.State.HFP_CONNECTED_NO_SCO) {
            if (bluetoothMediaOutputAvailable) {
                return new FailureSignature(
                        Kind.HFP_CONNECTED_NO_SCO_MEDIA_ALIVE,
                        Confidence.HIGH,
                        false,
                        "HFP verbunden und Medien-Bluetooth vorhanden, aber SCO fehlt");
            }
            return new FailureSignature(
                    Kind.HFP_CONNECTED_NO_SCO,
                    Confidence.MEDIUM,
                    false,
                    "HFP verbunden, aber Android bietet kein SCO-Kommunikationsgerät an");
        }

        if (health.state == BluetoothHealth.State.CALL_WITHOUT_SCO) {
            return new FailureSignature(
                    Kind.CALL_WITHOUT_BLUETOOTH_TELEPHONY,
                    Confidence.LOW,
                    false,
                    "Anruf aktiv, aber kein Bluetooth-Telefonieprofil erkennbar");
        }

        return new FailureSignature(
                Kind.ERROR,
                Confidence.LOW,
                false,
                "Unbekannter Telefonie-Audiozustand");
    }

    /**
     * One-way audio: SCO is the selected communication device, so the uplink (microphone) works,
     * but the downlink is either played on another device or silenced.
     */
    private static FailureSignature classifyDownlink(
            BluetoothHealth health,
            CallAudioSignals signals) {
        if (signals == null || health.speakerphoneOn || !health.scoSelected) return null;

        if (Boolean.FALSE.equals(signals.voiceRouteOnBluetooth)) {
            return new FailureSignature(
                    Kind.DOWNLINK_ROUTE_MISMATCH,
                    Confidence.HIGH,
                    true,
                    "Mikrofon läuft über Bluetooth, Gesprächspartner wird aber nicht auf Bluetooth ausgegeben");
        }

        if (signals.voiceSilenced()) {
            return new FailureSignature(
                    Kind.DOWNLINK_SILENCED,
                    Confidence.HIGH,
                    true,
                    "Bluetooth-Telefonie aktiv, aber die Gesprächslautstärke ist stumm");
        }

        return null;
    }

    boolean isDownlinkFault() {
        return kind == Kind.DOWNLINK_ROUTE_MISMATCH || kind == Kind.DOWNLINK_SILENCED;
    }

    boolean matchesKnownSamsungFailure() {
        return kind == Kind.RECOVERABLE_ROUTE_DRIFT
                || kind == Kind.HFP_CONNECTED_NO_SCO_MEDIA_ALIVE
                || isDownlinkFault();
    }
}
