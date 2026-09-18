package de.kaipressmar.a52srepair;

final class AppState {
    final boolean bluetoothEnabled;
    final boolean bluetoothPermission;
    final boolean monitoring;
    final String communicationDevice;
    final String summary;

    AppState(boolean bluetoothEnabled, boolean bluetoothPermission, boolean monitoring, String communicationDevice, String summary) {
        this.bluetoothEnabled = bluetoothEnabled;
        this.bluetoothPermission = bluetoothPermission;
        this.monitoring = monitoring;
        this.communicationDevice = communicationDevice;
        this.summary = summary;
    }

    String healthLabel() {
        if (!bluetoothPermission) return "Berechtigung erforderlich";
        if (!bluetoothEnabled) return "Bluetooth ist ausgeschaltet";
        if ("none".equals(communicationDevice)) return "Bereit – kein Telefonie-Audiogerät aktiv";
        return "Telefonie-Audio verbunden";
    }

    int healthTone() {
        if (!bluetoothPermission || !bluetoothEnabled) return 2;
        if ("none".equals(communicationDevice)) return 1;
        return 0;
    }
}
