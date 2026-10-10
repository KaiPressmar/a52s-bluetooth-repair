package de.kaipressmar.a52srepair.setup;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import android.Manifest;
import android.app.AppOpsManager;
import android.app.Application;
import android.bluetooth.BluetoothManager;
import android.os.Process;
import androidx.test.core.app.ApplicationProvider;
import de.kaipressmar.a52srepair.data.AppSettings;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {31, 34, 36})
public class ProtectionStatusTest {
    @Test public void setupPermissionAndPauseHavePriorityOverAdapterState() {
        Application app = ApplicationProvider.getApplicationContext();
        shadowOf(app).denyPermissions(Manifest.permission.BLUETOOTH_CONNECT);
        assertEquals(ProtectionStatus.NEEDS_BLUETOOTH_PERMISSION, ProtectionStatus.evaluate(app));
        shadowOf(app).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT);
        AppOpsManager ops = app.getSystemService(AppOpsManager.class);
        shadowOf(ops).setMode(ProtectionStatus.OP_MANAGE_ONGOING_CALLS, Process.myUid(), app.getPackageName(), AppOpsManager.MODE_IGNORED);
        assertEquals(ProtectionStatus.NEEDS_CALL_ACCESS, ProtectionStatus.evaluate(app));
        shadowOf(ops).setMode(ProtectionStatus.OP_MANAGE_ONGOING_CALLS, Process.myUid(), app.getPackageName(), AppOpsManager.MODE_ALLOWED);
        new AppSettings(app).setProtectionEnabled(false);
        assertEquals(ProtectionStatus.PAUSED, ProtectionStatus.evaluate(app));
        new AppSettings(app).setProtectionEnabled(true);
        shadowOf(app.getSystemService(BluetoothManager.class).getAdapter()).setEnabled(false);
        assertEquals(ProtectionStatus.BLUETOOTH_OFF, ProtectionStatus.evaluate(app));
        shadowOf(app.getSystemService(BluetoothManager.class).getAdapter()).setEnabled(true);
        assertEquals(ProtectionStatus.ACTIVE, ProtectionStatus.evaluate(app));
        assertTrue(ProtectionStatus.ACTIVE.setupComplete());
        assertFalse(ProtectionStatus.NEEDS_CALL_ACCESS.setupComplete());
    }
}
