package de.kaipressmar.a52srepair.audio;

import android.bluetooth.BluetoothDevice;
import java.util.List;

/** Narrow HFP boundary shared by the real observer and off-call failure-injection tests. */
interface HeadsetAccess {
    void open();
    void close();
    List<BluetoothDevice> connectedDevices();
    Boolean scoAudioConnected(BluetoothDevice target);
    boolean supportsVoiceRecognition(BluetoothDevice target);
    boolean startVoiceRecognition(BluetoothDevice target);
    boolean stopVoiceRecognition(BluetoothDevice target);
}
