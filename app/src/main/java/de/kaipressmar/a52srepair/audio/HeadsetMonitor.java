package de.kaipressmar.a52srepair.audio;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;
import java.util.List;

/** Holds the HFP profile proxy for one call session and answers link-state questions. */
public final class HeadsetMonitor {
    private final Context context;
    private final BluetoothAdapter adapter;
    private BluetoothHeadset headset;
    private final Runnable onChanged;
    private boolean opened;
    private boolean receiverRegistered;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ignored, Intent intent) {
            // Read current APIs on the next snapshot; never trust intent extras as audio proof.
            if (opened && permitted()) onChanged.run();
        }
    };

    private final BluetoothProfile.ServiceListener listener =
            new BluetoothProfile.ServiceListener() {
                @Override
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    if (!opened) {
                        closeProxy(proxy);
                        return;
                    }
                    if (proxy instanceof BluetoothHeadset) headset = (BluetoothHeadset) proxy;
                    onChanged.run();
                }

                @Override
                public void onServiceDisconnected(int profile) {
                    headset = null;
                    if (opened) onChanged.run();
                }
            };

    public HeadsetMonitor(Context context) {
        this(context, () -> {});
    }

    public HeadsetMonitor(Context context, Runnable onChanged) {
        this.context = context.getApplicationContext();
        this.onChanged = onChanged;
        BluetoothManager manager = this.context.getSystemService(BluetoothManager.class);
        adapter = manager == null ? null : manager.getAdapter();
    }

    public void open() {
        if (opened) return;
        opened = true;
        if (adapter == null || !permitted()) return;
        IntentFilter filter = new IntentFilter(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED);
        filter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        try {
            // Bluetooth runs under its own privileged UID. These are protected system actions.
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED);
            receiverRegistered = true;
        } catch (RuntimeException ignored) {
            // Existing in-call snapshot checks remain available if registration fails.
        }
        try {
            adapter.getProfileProxy(context, listener, BluetoothProfile.HEADSET);
        } catch (RuntimeException ignored) {
            // Without the proxy the SCO state is reported as unknown.
        }
    }

    public void close() {
        opened = false;
        if (receiverRegistered) {
            receiverRegistered = false;
            try { context.unregisterReceiver(receiver); } catch (RuntimeException ignored) {}
        }
        BluetoothHeadset proxy = headset;
        headset = null;
        closeProxy(proxy);
    }

    private void closeProxy(BluetoothProfile proxy) {
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
