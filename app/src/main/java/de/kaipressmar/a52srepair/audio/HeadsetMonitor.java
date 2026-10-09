package de.kaipressmar.a52srepair.audio;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import java.util.List;

/** Holds the HFP profile proxy for one call session and answers link-state questions. */
public final class HeadsetMonitor {
    private final Context context;
    private final BluetoothAdapter adapter;
    private BluetoothHeadset headset;

    private final BluetoothProfile.ServiceListener listener =
            new BluetoothProfile.ServiceListener() {
                @Override
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    if (proxy instanceof BluetoothHeadset) headset = (BluetoothHeadset) proxy;
                }

                @Override
                public void onServiceDisconnected(int profile) {
                    headset = null;
                }
            };

    public HeadsetMonitor(Context context) {
        this.context = context.getApplicationContext();
        BluetoothManager manager = this.context.getSystemService(BluetoothManager.class);
        adapter = manager == null ? null : manager.getAdapter();
    }

    public void open() {
        if (adapter == null || !permitted()) return;
        try {
            adapter.getProfileProxy(context, listener, BluetoothProfile.HEADSET);
        } catch (RuntimeException ignored) {
            // Without the proxy the SCO state is reported as unknown.
        }
    }

    public void close() {
        BluetoothHeadset proxy = headset;
        headset = null;
        if (adapter != null && proxy != null) {
            try {
                adapter.closeProfileProxy(BluetoothProfile.HEADSET, proxy);
            } catch (RuntimeException ignored) {
            }
        }
    }

    @SuppressLint("MissingPermission") // Guarded by permitted().
    public boolean hfpConnected() {
        if (adapter == null || !permitted()) return false;
        try {
            return adapter.isEnabled()
                    && adapter.getProfileConnectionState(BluetoothProfile.HEADSET)
                            == BluetoothAdapter.STATE_CONNECTED;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** TRUE/FALSE for the SCO link of any connected HFP device, null when unknown. */
    @SuppressLint("MissingPermission") // Guarded by permitted().
    public Boolean scoAudioConnected() {
        BluetoothHeadset proxy = headset;
        if (proxy == null || !permitted()) return null;
        try {
            List<BluetoothDevice> devices = proxy.getConnectedDevices();
            if (devices.isEmpty()) return null;
            for (BluetoothDevice device : devices) {
                if (proxy.isAudioConnected(device)) return Boolean.TRUE;
            }
            return Boolean.FALSE;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private boolean permitted() {
        return context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
    }
}
