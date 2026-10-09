package de.kaipressmar.a52srepair.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;
import android.Manifest;
import android.app.Application;
import android.bluetooth.BluetoothHeadset;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import androidx.test.core.app.ApplicationProvider;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {34, 36})
public class HeadsetMonitorTest {
    @Test public void audioEventsAreObservedOnlyWhileMonitorIsOpen() {
        Application app = ApplicationProvider.getApplicationContext();
        shadowOf(app).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT);
        AtomicInteger events = new AtomicInteger();
        HeadsetMonitor monitor = new HeadsetMonitor(app, events::incrementAndGet);
        monitor.open();
        monitor.open(); // must not double-register
        shadowOf(Looper.getMainLooper()).idle();
        int before = events.get();
        app.sendBroadcast(new Intent(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED));
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(events.get() > before);
        monitor.close();
        monitor.close();
        int closed = events.get();
        app.sendBroadcast(new Intent(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED));
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(closed, events.get());
    }

    @Test public void deniedBluetoothPermissionLeavesAudioUnknown() {
        Application app = ApplicationProvider.getApplicationContext();
        shadowOf(app).denyPermissions(Manifest.permission.BLUETOOTH_CONNECT);
        HeadsetMonitor monitor = new HeadsetMonitor(app);
        monitor.open();
        assertEquals(null, monitor.scoAudioConnected());
        assertEquals(false, monitor.hfpConnected());
        monitor.close();
    }
}
