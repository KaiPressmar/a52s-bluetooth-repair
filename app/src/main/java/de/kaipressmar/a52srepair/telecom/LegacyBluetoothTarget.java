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

    void observe(CallAudioState audio) {
        if (audio == null) return;
        available = new ArrayList<>(audio.getSupportedBluetoothDevices());
        BluetoothDevice active = audio.getActiveBluetoothDevice();
        if (audio.getRoute() == CallAudioState.ROUTE_BLUETOOTH && active != null
                && available.contains(active)) preferred = active;
    }

    BluetoothDevice target() {
        if (preferred != null) return available.contains(preferred) ? preferred : null;
        return available.size() == 1 ? available.get(0) : null;
    }

    boolean ambiguous() { return !available.isEmpty() && target() == null; }
    int size() { return available.size(); }
    void clear() { preferred = null; available.clear(); }
}
