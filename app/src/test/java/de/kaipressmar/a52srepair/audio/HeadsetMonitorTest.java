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
    @Test public void refreshedProxyIgnoresOldBinderCallbacksAndDisconnectedProxyCanBeReacquired() {
        Application app = ApplicationProvider.getApplicationContext();
        shadowOf(app).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT);
        HeadsetMonitor monitor = new HeadsetMonitor(app);
        monitor.open();
        long generation = org.robolectric.util.ReflectionHelpers.getField(monitor, "profileGeneration");
        android.bluetooth.BluetoothProfile.ServiceListener old = org.robolectric.util.ReflectionHelpers.callInstanceMethod(
                monitor, "listener", org.robolectric.util.ReflectionHelpers.ClassParameter.from(long.class, generation));
        monitor.refresh();
        BluetoothHeadset proxy = org.robolectric.shadow.api.Shadow.newInstanceOf(BluetoothHeadset.class);
        android.bluetooth.BluetoothDevice car = android.bluetooth.BluetoothAdapter.getDefaultAdapter().getRemoteDevice("00:11:22:33:44:01");
        shadowOf(proxy).addConnectedDevice(car);
        old.onServiceConnected(android.bluetooth.BluetoothProfile.HEADSET, proxy);
        assertTrue(monitor.connectedDevices().isEmpty());
        long freshGeneration = org.robolectric.util.ReflectionHelpers.getField(monitor, "profileGeneration");
        android.bluetooth.BluetoothProfile.ServiceListener fresh = org.robolectric.util.ReflectionHelpers.callInstanceMethod(
                monitor, "listener", org.robolectric.util.ReflectionHelpers.ClassParameter.from(long.class, freshGeneration));
        fresh.onServiceConnected(android.bluetooth.BluetoothProfile.HEADSET, proxy);
        old.onServiceDisconnected(android.bluetooth.BluetoothProfile.HEADSET);
        assertEquals(java.util.List.of(car), monitor.connectedDevices());
        fresh.onServiceDisconnected(android.bluetooth.BluetoothProfile.HEADSET);
        assertTrue(monitor.connectedDevices().isEmpty());
        assertEquals(false, org.robolectric.util.ReflectionHelpers.getField(monitor, "profileRequested"));
        monitor.close();
    }
    @org.robolectric.annotation.Implements(BluetoothHeadset.class)
    public static class UnavailableHeadset extends org.robolectric.shadows.ShadowBluetoothHeadset {
        @org.robolectric.annotation.Implementation
        protected java.util.List<android.bluetooth.BluetoothDevice> getConnectedDevices() {
            throw new IllegalStateException("Do not log this private device detail");
        }
    }
    @Test @Config(sdk=34, shadows=UnavailableHeadset.class)
    public void failedHfpObservationsRemainUnknownAndLogReasonWithoutFloodOrPrivateMessage() {
        Application app=ApplicationProvider.getApplicationContext();
        shadowOf(app).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT);
        HeadsetMonitor monitor=new HeadsetMonitor(app);
        BluetoothHeadset proxy=org.robolectric.shadow.api.Shadow.newInstanceOf(BluetoothHeadset.class);
        org.robolectric.util.ReflectionHelpers.setField(monitor,"headset",proxy);
        assertTrue(monitor.connectedDevices().isEmpty());assertTrue(monitor.connectedDevices().isEmpty());
        assertEquals(null,monitor.scoAudioConnected());assertEquals(null,monitor.scoAudioConnected());
        String log=de.kaipressmar.a52srepair.diagnostics.DiagnosticLog.readAll(app);
        assertEquals(2,log.lines().filter(x->x.contains("HFP observation error=")).count());
        assertTrue(log.contains("sco-state:IllegalStateException"));
        assertTrue(!log.contains("private device detail"));monitor.close();
    }
    @Test public void voiceSessionDriverUsesTheConnectedTargetAndObservesRealStateChanges() {
        Application app=ApplicationProvider.getApplicationContext();
        shadowOf(app).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT);
        BluetoothHeadset proxy=org.robolectric.shadow.api.Shadow.newInstanceOf(BluetoothHeadset.class);
        android.bluetooth.BluetoothDevice car=android.bluetooth.BluetoothAdapter.getDefaultAdapter().getRemoteDevice("00:11:22:33:44:01");
        shadowOf(proxy).addConnectedDevice(car);
        HeadsetMonitor monitor=new HeadsetMonitor(app);
        org.robolectric.util.ReflectionHelpers.setField(monitor,"headset",proxy);
        assertEquals(false,monitor.supportsVoiceRecognition(null));
        assertEquals(false,monitor.startVoiceRecognition(null));
        assertEquals(false,monitor.stopVoiceRecognition(null));
        assertTrue(monitor.startVoiceRecognition(car));assertEquals(Boolean.TRUE,monitor.scoAudioConnected(car));
        assertTrue(monitor.stopVoiceRecognition(car));assertEquals(Boolean.FALSE,monitor.scoAudioConnected(car));
        shadowOf(app).denyPermissions(Manifest.permission.BLUETOOTH_CONNECT);
        assertEquals(false,monitor.startVoiceRecognition(car));assertEquals(false,monitor.stopVoiceRecognition(car));
        monitor.close();
    }
    @Test public void anotherHeadsetsScoDoesNotVerifyTheSelectedCarsAudio() {
        Application app = ApplicationProvider.getApplicationContext();
        shadowOf(app).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT);
        android.bluetooth.BluetoothAdapter adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter();
        android.bluetooth.BluetoothDevice car = adapter.getRemoteDevice("00:11:22:33:44:01");
        android.bluetooth.BluetoothDevice watch = adapter.getRemoteDevice("00:11:22:33:44:02");
        BluetoothHeadset proxy = org.robolectric.shadow.api.Shadow.newInstanceOf(BluetoothHeadset.class);
        shadowOf(proxy).addConnectedDevice(car);
        shadowOf(proxy).addConnectedDevice(watch);
        assertTrue(proxy.startVoiceRecognition(watch));
        HeadsetMonitor monitor = new HeadsetMonitor(app);
        org.robolectric.util.ReflectionHelpers.setField(monitor, "headset", proxy);
        assertEquals(2, monitor.connectedDevices().size());
        assertEquals(Boolean.TRUE, monitor.scoAudioConnected());
        assertEquals(Boolean.FALSE, monitor.scoAudioConnected(car));
        assertEquals(Boolean.TRUE, monitor.scoAudioConnected(watch));
        assertEquals(null, monitor.scoAudioConnected(adapter.getRemoteDevice("00:11:22:33:44:03")));
        monitor.close();
    }
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
        assertTrue(monitor.connectedDevices().isEmpty());
        monitor.close();
    }

    @Test public void permissionGrantedDuringCallCanStartObservation() {
        Application app = ApplicationProvider.getApplicationContext();
        shadowOf(app).denyPermissions(Manifest.permission.BLUETOOTH_CONNECT);
        AtomicInteger events = new AtomicInteger();
        HeadsetMonitor monitor = new HeadsetMonitor(app, events::incrementAndGet);
        monitor.open();
        shadowOf(app).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT);
        monitor.open();
        shadowOf(Looper.getMainLooper()).idle();
        int before = events.get();
        app.sendBroadcast(new Intent(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED));
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(events.get() > before);
        monitor.close();
    }
}
