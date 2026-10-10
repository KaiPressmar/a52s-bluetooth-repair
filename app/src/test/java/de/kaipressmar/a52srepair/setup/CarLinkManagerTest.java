package de.kaipressmar.a52srepair.setup;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import android.Manifest;
import android.app.Application;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.companion.CompanionDeviceManager;
import android.content.IntentSender;
import androidx.test.core.app.ApplicationProvider;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowBluetoothDevice;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {31, 34, 36})
public class CarLinkManagerTest {
    @org.robolectric.annotation.Implements(CompanionDeviceManager.class)
    public static class RejectingCompanion extends org.robolectric.shadows.ShadowCompanionDeviceManager {
        @org.robolectric.annotation.Implementation(minSdk = 33)
        protected void associate(android.companion.AssociationRequest request, java.util.concurrent.Executor executor,
                CompanionDeviceManager.Callback callback) { throw new SecurityException("access revoked"); }
        @org.robolectric.annotation.Implementation(maxSdk = 32)
        protected void associate(android.companion.AssociationRequest request, CompanionDeviceManager.Callback callback,
                android.os.Handler handler) { throw new SecurityException("access revoked"); }
    }

    @Test @Config(shadows = RejectingCompanion.class)
    public void rejectedSystemAssociationReturnsFailureInsteadOfCrashing() {
        Application app = ApplicationProvider.getApplicationContext();
        java.util.concurrent.atomic.AtomicReference<CharSequence> failure = new java.util.concurrent.atomic.AtomicReference<>();
        new CarLinkManager(app).link(new CarLinkManager.PairedDevice("Car", "00:11:22:33:44:55"), new CarLinkManager.Callback() {
            public void onConfirmationRequired(IntentSender sender) {}
            public void onLinked(String name) {}
            public void onFailed(CharSequence reason) { failure.set(reason); }
        });
        assertEquals(app.getString(de.kaipressmar.a52srepair.R.string.setup_link_rejected), failure.get());
    }
    @Test public void unclassifiedPairedDevicesAreUsableAndAssociationCallbacksAreForwarded() {
        Application app = ApplicationProvider.getApplicationContext();
        shadowOf(app).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT);
        BluetoothDevice device = ShadowBluetoothDevice.newInstance("00:11:22:33:44:55");
        shadowOf(device).setName("Car");
        shadowOf(app.getSystemService(BluetoothManager.class).getAdapter()).setBondedDevices(Set.of(device));
        CarLinkManager manager = new CarLinkManager(app);
        List<CarLinkManager.PairedDevice> paired = manager.pairedAudioDevices();
        assertEquals(1, paired.size());
        assertEquals("Car", paired.get(0).name);
        java.util.concurrent.atomic.AtomicReference<CharSequence> failure = new java.util.concurrent.atomic.AtomicReference<>();
        manager.link(paired.get(0), new CarLinkManager.Callback() {
            public void onConfirmationRequired(IntentSender sender) {}
            public void onLinked(String name) {}
            public void onFailed(CharSequence reason) { failure.set(reason); }
        });
        CompanionDeviceManager cdm = app.getSystemService(CompanionDeviceManager.class);
        assertNotNull(shadowOf(cdm).getLastAssociationRequest());
        shadowOf(cdm).getLastAssociationCallback().onFailure("cancelled");
        assertEquals("cancelled", failure.get());
    }

    @Test public void linkedDevicesCanBeListedAndRemovedOnOldAndModernApis() {
        Application app = ApplicationProvider.getApplicationContext();
        CompanionDeviceManager cdm = app.getSystemService(CompanionDeviceManager.class);
        shadowOf(cdm).addAssociation("00:11:22:33:44:55");
        CarLinkManager manager = new CarLinkManager(app);
        assertEquals(1, manager.linkedDevices().size());
        manager.unlinkAll();
        assertTrue(manager.linkedDevices().isEmpty());
    }
}
