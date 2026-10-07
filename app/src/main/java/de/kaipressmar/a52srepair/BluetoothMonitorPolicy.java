package de.kaipressmar.a52srepair;

/** Pure lifecycle policy for the foreground watchdog. */
final class BluetoothMonitorPolicy {
    private BluetoothMonitorPolicy() {}

    static boolean shouldRun(boolean monitoringEnabled, boolean bluetoothEnabled) {
        return monitoringEnabled && bluetoothEnabled;
    }

    static boolean isBluetoothTrigger(String action) {
        return android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)
                || android.bluetooth.BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED.equals(action)
                || android.bluetooth.BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED.equals(action)
                || android.bluetooth.BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED.equals(action);
    }
}
