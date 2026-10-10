package de.kaipressmar.a52srepair.audio;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import androidx.test.core.app.ApplicationProvider;
import de.kaipressmar.a52srepair.core.model.AudioRoute;
import de.kaipressmar.a52srepair.core.model.CallAudioSnapshot;
import de.kaipressmar.a52srepair.core.model.CallPhase;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.AudioDeviceInfoBuilder;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {31, 34, 36})
public class CommunicationDeviceRecoveryTest {
    @Implements(AudioManager.class)
    public static class FailingAudioManager extends org.robolectric.shadows.ShadowAudioManager {
        boolean failAfterSet;
        boolean failClear;
        @Implementation protected boolean setCommunicationDevice(AudioDeviceInfo device) {
            boolean accepted = super.setCommunicationDevice(device);
            if (failAfterSet) throw new IllegalStateException("Binder failed after acceptance");
            return accepted;
        }
        @Implementation protected void clearCommunicationDevice() {
            if (failClear) throw new IllegalStateException("Binder unavailable");
            super.clearCommunicationDevice();
        }
    }
    private static final String CAR = "00:11:22:33:44:01";
    private static BluetoothDevice car() { return BluetoothAdapter.getDefaultAdapter().getRemoteDevice(CAR); }
    private static AudioManager audio() {
        return ApplicationProvider.<Context>getApplicationContext().getSystemService(AudioManager.class);
    }
    private static AudioDeviceInfo device(int type, String address, int id) {
        AudioDeviceInfo device = AudioDeviceInfoBuilder.newBuilder().setType(type).build();
        Object port = ReflectionHelpers.getField(device, "mPort");
        ReflectionHelpers.setField(port, "mRole", 2); // AudioPort.ROLE_SINK
        ReflectionHelpers.setField(port, "mAddress", address);
        ReflectionHelpers.setField(ReflectionHelpers.getField(port, "mHandle"), "mId", id);
        return device;
    }
    private static CallAudioSnapshot active() {
        return CallAudioSnapshot.builder().phase(CallPhase.ACTIVE).callCount(1)
                .route(AudioRoute.BLUETOOTH).bluetoothRouteAvailable(true).build();
    }
    @Test @Config(sdk = 34, shadows = FailingAudioManager.class)
    public void binderFailureAfterAcceptanceRetainsCleanupAndRetriesWithoutBusyLoop() {
        AudioManager audio = audio();
        FailingAudioManager platform = org.robolectric.shadow.api.Shadow.extract(audio);
        platform.setAvailableCommunicationDevices(List.of(device(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, CAR, 1)));
        platform.failAfterSet = true;
        platform.failClear = true;
        CommunicationDeviceRecovery recovery = new CommunicationDeviceRecovery(audio);
        assertFalse(recovery.request(car(), 0L));
        assertTrue(recovery.hasRequest()); // Remote acceptance cannot be forgotten after an exception.
        recovery.maintain(active(), 15_000L, true);
        assertEquals(5_000L, recovery.nextCleanupInMs(15_000L));
        platform.failClear = false;
        recovery.maintain(active(), 20_000L, true);
        assertFalse(recovery.hasRequest());
        assertNull(audio.getCommunicationDevice());
    }
    @Test public void exactTargetAcceptedWithoutChangingModeMuteAndReleasedAtDeadline() {
        AudioManager audio = audio();
        AudioDeviceInfo car = device(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, CAR, 1);
        shadowOf(audio).setAvailableCommunicationDevices(List.of(
                device(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, "00:11:22:33:44:02", 2), car));
        audio.setMode(AudioManager.MODE_IN_CALL);
        audio.setMicrophoneMute(true);
        CommunicationDeviceRecovery recovery = new CommunicationDeviceRecovery(audio);
        assertTrue(recovery.request(car(), 100L));
        assertSame(car, audio.getCommunicationDevice());
        assertEquals(AudioManager.MODE_IN_CALL, audio.getMode());
        assertTrue(audio.isMicrophoneMute());
        assertEquals(15_000L, recovery.nextCleanupInMs(100L));
        assertFalse(recovery.request(car(), 101L));
        recovery.maintain(active(), 15_099L, true);
        assertTrue(recovery.hasRequest());
        recovery.maintain(active(), 15_100L, true);
        assertFalse(recovery.hasRequest());
        assertNull(audio.getCommunicationDevice());
    }
    @Test public void unavailableUnknownMediaAndDuplicateTargetsAreNeverGuessed() {
        AudioManager audio = audio();
        CommunicationDeviceRecovery recovery = new CommunicationDeviceRecovery(audio);
        assertFalse(recovery.request(null, 0L));
        for (List<AudioDeviceInfo> candidates : List.of(
                List.<AudioDeviceInfo>of(),
                List.of(device(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, CAR, 1)),
                List.of(device(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, "", 1)),
                List.of(device(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, CAR, 0)),
                List.of(device(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, CAR, 1),
                        device(AudioDeviceInfo.TYPE_BLE_HEADSET, CAR, 2)))) {
            shadowOf(audio).setAvailableCommunicationDevices(candidates);
            assertFalse(recovery.request(car(), 0L));
            assertFalse(recovery.hasRequest());
        }
        assertFalse(new CommunicationDeviceRecovery(null).request(car(), 0L));
    }
    @Test public void rejectedRequestNeverBecomesRecoveryAndLifecycleClearsOwnedRequest() {
        AudioManager audio = audio();
        shadowOf(audio).setAvailableCommunicationDevices(List.of(device(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, CAR, 1)));
        CommunicationDeviceRecovery recovery = new CommunicationDeviceRecovery(audio);
        shadowOf(audio).lockCommunicationDevice(true);
        assertFalse(recovery.request(car(), 0L));
        assertFalse(recovery.hasRequest());
        shadowOf(audio).lockCommunicationDevice(false);
        for (CallAudioSnapshot stop : List.of(active().toBuilder().route(AudioRoute.SPEAKER).build(),
                active().toBuilder().phase(CallPhase.HOLDING).build(),
                active().toBuilder().callCount(2).build(),
                active().toBuilder().bluetoothRouteAvailable(false).build())) {
            assertTrue(recovery.request(car(), 0L));
            recovery.maintain(stop, 1L, true);
            assertFalse(recovery.hasRequest());
        }
        assertTrue(recovery.request(car(), 0L));
        recovery.maintain(active(), 1L, false);
        assertFalse(recovery.hasRequest());
        assertTrue(recovery.request(car(), 0L));
        recovery.close();
        recovery.close();
        assertFalse(recovery.hasRequest());
    }
}
