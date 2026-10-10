package de.kaipressmar.a52srepair.telecom;

import android.bluetooth.BluetoothDevice;
import android.telecom.CallAudioState;
import java.util.ArrayList;
import java.util.List;

/** Preserve the selected device across a phone hop; never guess between peripherals. */
@SuppressWarnings("deprecation")
final class LegacyBluetoothTarget {
    private BluetoothDevice preferred;
    private List<BluetoothDevice> available = new ArrayList<>();
    private List<BluetoothDevice> headsets = new ArrayList<>();

    void observe(CallAudioState audio) {
        if (audio == null) return;
        available = new ArrayList<>(audio.getSupportedBluetoothDevices());
        BluetoothDevice active = audio.getActiveBluetoothDevice();
        if (audio.getRoute() == CallAudioState.ROUTE_BLUETOOTH && active != null) preferred = active;
    }

    void observeHeadsets(List<BluetoothDevice> devices) {
        headsets = devices == null ? new ArrayList<>() : new ArrayList<>(devices);
    }

    private List<BluetoothDevice> candidates() { return available.isEmpty() ? headsets : available; }
    void requested(BluetoothDevice device) { preferred = device; }

    BluetoothDevice target() {
        List<BluetoothDevice> devices = candidates();
        if (preferred != null) return devices.contains(preferred) ? preferred : null;
        return devices.size() == 1 ? devices.get(0) : null;
    }

    boolean ambiguous() { return (preferred != null || !candidates().isEmpty()) && target() == null; }
    int size() { return available.size(); }
    int headsetCount() { return headsets.size(); }
    void clear() { preferred = null; available.clear(); headsets.clear(); }
}
