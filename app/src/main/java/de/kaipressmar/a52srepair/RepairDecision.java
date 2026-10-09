package de.kaipressmar.a52srepair;

/** Pure repair/escalation state machine used by the watchdog. */
final class RepairDecision {
    enum Action {
        NONE,
        WAIT_FOR_CONFIRMATION,
        RESELECT_COMMUNICATION_ROUTE,
        BOUNCE_COMMUNICATION_ROUTE,
        RESTORE_CALL_VOLUME,
        ESCALATE_VENDOR_STACK
    }

    final Action action;
    final String reason;

    RepairDecision(Action action, String reason) {
        this.action = action;
        this.reason = reason;
    }

    static RepairDecision decide(
            FailureSignature signature,
            BluetoothHealth health,
            int consecutiveSuspect,
            int consecutiveDegraded,
            long nowMillis,
            long lastRepairAtMillis) {
        if (signature == null || health == null || !health.inCommunication) {
            return new RepairDecision(Action.NONE, "Kein aktiver Telefoniefehler");
        }

        if (health.speakerphoneOn) {
            return new RepairDecision(
                    Action.NONE,
                    "Lautsprecherroute wurde bewusst gewählt");
        }

        if (signature.kind == FailureSignature.Kind.RECOVERABLE_ROUTE_DRIFT) {
            if (consecutiveSuspect < 2) {
                return new RepairDecision(
                        Action.WAIT_FOR_CONFIRMATION,
                        "Routingabweichung wird kurz bestätigt");
            }
            if (nowMillis - lastRepairAtMillis < RepairPolicy.MIN_REPAIR_INTERVAL_MS) {
                return new RepairDecision(
                        Action.NONE,
                        "Reparatur-Cooldown schützt vor Routing-Schleifen");
            }
            return new RepairDecision(
                    Action.RESELECT_COMMUNICATION_ROUTE,
                    "SCO/HFP ist routbar und kann gezielt neu ausgewählt werden");
        }

        if (signature.isDownlinkFault()) {
            if (consecutiveSuspect < 2) {
                return new RepairDecision(
                        Action.WAIT_FOR_CONFIRMATION,
                        "Einseitiges Gesprächsaudio wird kurz bestätigt");
            }
            if (signature.kind == FailureSignature.Kind.DOWNLINK_SILENCED) {
                // Restoring volume is harmless and not subject to the route-thrashing cooldown.
                return new RepairDecision(
                        Action.RESTORE_CALL_VOLUME,
                        "Gesprächslautstärke ist stumm und wird wiederhergestellt");
            }
            if (nowMillis - lastRepairAtMillis < RepairPolicy.MIN_REPAIR_INTERVAL_MS) {
                return new RepairDecision(
                        Action.NONE,
                        "Reparatur-Cooldown schützt vor Routing-Schleifen");
            }
            return new RepairDecision(
                    Action.BOUNCE_COMMUNICATION_ROUTE,
                    "Downlink läuft nicht über Bluetooth; SCO wird über Hörer neu aufgebaut");
        }

        if (signature.kind == FailureSignature.Kind.HFP_CONNECTED_NO_SCO_MEDIA_ALIVE
                || signature.kind == FailureSignature.Kind.HFP_CONNECTED_NO_SCO) {
            if (consecutiveDegraded < 2) {
                return new RepairDecision(
                        Action.WAIT_FOR_CONFIRMATION,
                        "Fehlendes SCO wird gegen einen transienten Profilwechsel bestätigt");
            }
            return new RepairDecision(
                    Action.ESCALATE_VENDOR_STACK,
                    "HFP ist verbunden, aber Android stellt kein reparierbares SCO-Gerät bereit");
        }

        return new RepairDecision(
                Action.NONE,
                "Kein sicher automatisch reparierbares Fehlerbild");
    }

    /** User pressed "Ich höre nichts": the only reliable signal for an inaudible downlink. */
    static RepairDecision forUserReportedOneWayAudio(
            BluetoothHealth health,
            CallAudioSignals signals) {
        if (health == null || !health.inCommunication) {
            return new RepairDecision(Action.NONE, "Kein aktiver Anruf");
        }
        if (!health.scoAvailable && !health.scoSelected) {
            return new RepairDecision(
                    Action.ESCALATE_VENDOR_STACK,
                    "Kein Bluetooth-Telefoniegerät verfügbar – nur ein Neustart hilft");
        }
        if (signals != null && signals.voiceSilenced()) {
            return new RepairDecision(
                    Action.RESTORE_CALL_VOLUME,
                    "Gesprächslautstärke ist stumm und wird wiederhergestellt");
        }
        if (!health.scoSelected) {
            return new RepairDecision(
                    Action.RESELECT_COMMUNICATION_ROUTE,
                    "Bluetooth-Telefonie ist nicht ausgewählt und wird neu angefordert");
        }
        return new RepairDecision(
                Action.BOUNCE_COMMUNICATION_ROUTE,
                "Gemeldetes einseitiges Audio: SCO wird über Hörer neu aufgebaut");
    }

    boolean changesRoute() {
        return action == Action.RESELECT_COMMUNICATION_ROUTE
                || action == Action.BOUNCE_COMMUNICATION_ROUTE;
    }

    boolean isRepair() {
        return changesRoute() || action == Action.RESTORE_CALL_VOLUME;
    }

    private RepairDecision() {
        action = Action.NONE;
        reason = "";
    }
}
