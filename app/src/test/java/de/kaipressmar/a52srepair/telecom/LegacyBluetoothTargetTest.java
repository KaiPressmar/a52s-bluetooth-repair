package de.kaipressmar.a52srepair.telecom;

import static org.junit.Assert.*;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.telecom.CallAudioState;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {31, 34, 36})
public class LegacyBluetoothTargetTest {
    static BluetoothDevice device(int number) {
        return BluetoothAdapter.getDefaultAdapter().getRemoteDevice("00:11:22:33:44:0" + number);
    }

    static CallAudioState audio(int route, BluetoothDevice active, BluetoothDevice... devices) {
        return ReflectionHelpers.callConstructor(CallAudioState.class,
                ClassParameter.from(boolean.class, false), ClassParameter.from(int.class, route),
                ClassParameter.from(int.class, CallAudioState.ROUTE_BLUETOOTH | CallAudioState.ROUTE_EARPIECE),
                ClassParameter.from(BluetoothDevice.class, active),
                ClassParameter.from(Collection.class, List.of(devices)));
    }

    @Test public void selectedCarSurvivesPhoneHopWithoutGuessingBetweenTwoDevices() {
        BluetoothDevice car = device(1), watch = device(2);
        LegacyBluetoothTarget t = new LegacyBluetoothTarget();
        t.observe(audio(CallAudioState.ROUTE_BLUETOOTH, car, car, watch));
        assertEquals(car, t.target());
        t.observe(audio(CallAudioState.ROUTE_EARPIECE, null, watch, car));
        assertEquals(car, t.target());
        assertFalse(t.ambiguous());
    }

    @Test public void unavailablePreferredDeviceIsNotRequestedAgain() {
        BluetoothDevice car = device(1), watch = device(2);
        LegacyBluetoothTarget t = new LegacyBluetoothTarget();
        t.observe(audio(CallAudioState.ROUTE_BLUETOOTH, car, car, watch));
        t.observe(audio(CallAudioState.ROUTE_EARPIECE, null, watch));
        assertNull(t.target());
        assertTrue(t.ambiguous());
        t.observe(audio(CallAudioState.ROUTE_EARPIECE, null));
        assertNull(t.target());
        assertEquals(0, t.size());
    }

    @Test public void unselectedMultipleDevicesAreAmbiguousAndOldCallPreferenceIsCleared() {
        BluetoothDevice car = device(1), watch = device(2);
        LegacyBluetoothTarget t = new LegacyBluetoothTarget();
        t.observe(null);
        t.observe(audio(CallAudioState.ROUTE_EARPIECE, car, car, watch));
        assertTrue(t.ambiguous());
        assertNull(t.target());
        t.observe(audio(CallAudioState.ROUTE_BLUETOOTH, watch, car, watch));
        assertEquals(watch, t.target());
        t.clear();
        t.observe(audio(CallAudioState.ROUTE_EARPIECE, null, car, watch));
        assertTrue(t.ambiguous());
    }
}
