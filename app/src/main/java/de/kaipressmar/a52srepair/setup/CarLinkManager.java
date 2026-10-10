package de.kaipressmar.a52srepair.setup;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.companion.AssociationInfo;
import android.companion.AssociationRequest;
import android.companion.BluetoothDeviceFilter;
import android.companion.CompanionDeviceManager;
import android.content.Context;
import android.content.IntentSender;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import de.kaipressmar.a52srepair.R;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Links the car (or headset) through Android's Companion Device Manager. The system dialog grants
 * the companion role, which includes call access ({@code MANAGE_ONGOING_CALLS}); no root and no
 * computer needed. The "watch" profile is the only public profile that carries that access.
 */
public final class CarLinkManager {
    public interface Callback {
        /** The system confirmation dialog must be launched. */
        void onConfirmationRequired(IntentSender sender);

        void onLinked(String deviceName);

        void onFailed(CharSequence reason);
    }

    /** A paired audio device the user can choose. */
    public static final class PairedDevice {
        public final String name;
        public final String address;

        PairedDevice(String name, String address) {
            this.name = name;
            this.address = address;
        }
    }

    private final Context context;
    private final CompanionDeviceManager cdm;

    public CarLinkManager(Context context) {
        this.context = context.getApplicationContext();
        cdm = this.context.getSystemService(CompanionDeviceManager.class);
    }

    public boolean available() {
        return cdm != null;
    }

    /** Paired car kits and headsets; falls back to all paired devices if none is classified. */
    @SuppressLint("MissingPermission") // Callers check BLUETOOTH_CONNECT first.
    public List<PairedDevice> pairedAudioDevices() {
        List<PairedDevice> audio = new ArrayList<>();
        List<PairedDevice> all = new ArrayList<>();
        BluetoothManager manager = context.getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null) return audio;
        try {
            for (BluetoothDevice device : adapter.getBondedDevices()) {
                String name = device.getName() == null ? device.getAddress() : device.getName();
                PairedDevice paired = new PairedDevice(name, device.getAddress());
                all.add(paired);
                BluetoothClass type = device.getBluetoothClass();
                if (type != null
                        && type.getMajorDeviceClass() == BluetoothClass.Device.Major.AUDIO_VIDEO) {
                    audio.add(paired);
                }
            }
        } catch (RuntimeException ignored) {
            return audio;
        }
        return audio.isEmpty() ? all : audio;
    }

    @SuppressWarnings("deprecation")
    public void link(PairedDevice device, Callback callback) {
        if (cdm == null) {
            callback.onFailed(context.getString(R.string.setup_link_unsupported));
            return;
        }
        try {
            AssociationRequest request =
                    new AssociationRequest.Builder()
                            .addDeviceFilter(
                                    new BluetoothDeviceFilter.Builder().setAddress(device.address).build())
                            .setSingleDevice(true)
                            .setDeviceProfile(AssociationRequest.DEVICE_PROFILE_WATCH)
                            .build();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Executor main = context.getMainExecutor();
                cdm.associate(
                        request,
                        main,
                        new CompanionDeviceManager.Callback() {
                            @Override
                            public void onAssociationPending(IntentSender sender) {
                                callback.onConfirmationRequired(sender);
                            }

                            @Override
                            public void onAssociationCreated(AssociationInfo info) {
                                CharSequence name = info.getDisplayName();
                                callback.onLinked(name == null ? device.name : name.toString());
                            }

                            @Override
                            public void onFailure(CharSequence error) {
                                callback.onFailed(error);
                            }
                        });
            } else {
                cdm.associate(
                        request,
                        new CompanionDeviceManager.Callback() {
                            @Override
                            public void onDeviceFound(IntentSender sender) {
                                callback.onConfirmationRequired(sender);
                            }

                            @Override
                            public void onFailure(CharSequence error) {
                                callback.onFailed(error);
                            }
                        },
                        new Handler(Looper.getMainLooper()));
            }
        } catch (RuntimeException e) {
            callback.onFailed(context.getString(R.string.setup_link_rejected));
        }
    }

    /** Display names (or addresses) of linked devices. */
    @SuppressWarnings("deprecation")
    public List<String> linkedDevices() {
        List<String> out = new ArrayList<>();
        if (cdm == null) return out;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                for (AssociationInfo info : cdm.getMyAssociations()) {
                    CharSequence name = info.getDisplayName();
                    out.add(name != null ? name.toString()
                            : String.valueOf(info.getDeviceMacAddress()));
                }
            } else {
                out.addAll(cdm.getAssociations());
            }
        } catch (RuntimeException ignored) {
        }
        return out;
    }

    @SuppressWarnings("deprecation")
    public void unlinkAll() {
        if (cdm == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                for (AssociationInfo info : cdm.getMyAssociations()) cdm.disassociate(info.getId());
            } else {
                for (String address : cdm.getAssociations()) cdm.disassociate(address);
            }
        } catch (RuntimeException ignored) {
        }
    }
}
