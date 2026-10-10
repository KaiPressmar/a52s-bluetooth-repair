package de.kaipressmar.a52srepair.telecom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertNull;
import android.os.ParcelUuid;
import android.telecom.CallAudioState;
import android.telecom.CallEndpoint;
import android.telecom.CallEndpointException;
import android.os.OutcomeReceiver;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {34, 36})
@SuppressWarnings("deprecation")
public class EndpointAudioStateTest {
    @Test public void disappearedSelectedCarNeverSilentlySwitchesToSoleOtherHeadset() {
        EndpointAudioState s = new EndpointAudioState();
        CallEndpoint car = endpoint(CallEndpoint.TYPE_BLUETOOTH);
        CallEndpoint watch = endpoint(CallEndpoint.TYPE_BLUETOOTH);
        s.available(List.of(car, watch));
        s.selected(car);
        s.available(List.of(watch));
        assertNull(s.target(CallAudioState.ROUTE_BLUETOOTH));
        org.junit.Assert.assertTrue(s.bluetoothTargetAmbiguous());
        s.selected(watch); // An explicit platform selection may establish the new target.
        assertSame(watch, s.target(CallAudioState.ROUTE_BLUETOOTH));
    }

    @Test public void legacyFallbackInvalidatesLateModernOutcomeWithoutInventingSuccess() {
        EndpointAudioState s = new EndpointAudioState();
        s.available(List.of(endpoint(CallEndpoint.TYPE_BLUETOOTH)));
        List<OutcomeReceiver<Void, CallEndpointException>> callbacks = new ArrayList<>();
        AtomicInteger events = new AtomicInteger();
        s.request(CallAudioState.ROUTE_BLUETOOTH, (e, c) -> callbacks.add(c),
                events::incrementAndGet, code -> events.incrementAndGet());
        s.invalidateRequests();
        callbacks.get(0).onResult(null);
        callbacks.get(0).onError(new CallEndpointException(null, CallEndpointException.ERROR_REQUEST_TIME_OUT));
        assertEquals(0, events.get());
    }
    private static CallEndpoint endpoint(int type) {
        return new CallEndpoint("Test", type, new ParcelUuid(UUID.randomUUID()));
    }

    @Test public void noModernCallbacksPreserveLegacyState() {
        CallAudioState legacy = new CallAudioState(false, CallAudioState.ROUTE_BLUETOOTH, 15);
        assertSame(legacy, new EndpointAudioState().merge(legacy));
    }

    @Test public void modernSpeakerOverridesStaleLegacyBluetooth() {
        EndpointAudioState s = new EndpointAudioState();
        s.selected(endpoint(CallEndpoint.TYPE_SPEAKER));
        s.available(List.of(endpoint(CallEndpoint.TYPE_SPEAKER), endpoint(CallEndpoint.TYPE_BLUETOOTH)));
        CallAudioState state = s.merge(new CallAudioState(false, CallAudioState.ROUTE_BLUETOOTH, 15));
        assertEquals(CallAudioState.ROUTE_SPEAKER, state.getRoute());
        assertEquals(CallAudioState.ROUTE_SPEAKER | CallAudioState.ROUTE_BLUETOOTH, state.getSupportedRouteMask());
    }

    @Test public void modernOnlyCallbacksProvideAudioStateWithoutLegacy() {
        EndpointAudioState s = new EndpointAudioState();
        s.available(List.of(endpoint(CallEndpoint.TYPE_BLUETOOTH), endpoint(CallEndpoint.TYPE_EARPIECE)));
        s.selected(endpoint(CallEndpoint.TYPE_BLUETOOTH));
        assertEquals(CallAudioState.ROUTE_BLUETOOTH, s.merge(null).getRoute());
        s.selected(endpoint(CallEndpoint.TYPE_EARPIECE));
        assertEquals(CallAudioState.ROUTE_EARPIECE, s.merge(null).getRoute());
    }

    @Test public void emptyEndpointListDoesNotKeepStaleBluetoothAvailability() {
        EndpointAudioState s = new EndpointAudioState();
        s.selected(endpoint(CallEndpoint.TYPE_BLUETOOTH));
        s.available(List.of());
        assertEquals(0, s.merge(new CallAudioState(false, CallAudioState.ROUTE_BLUETOOTH, 15))
                .getSupportedRouteMask());
    }

    @Test public void unknownEndpointDoesNotPretendToBeBluetooth() {
        EndpointAudioState s = new EndpointAudioState();
        s.selected(endpoint(CallEndpoint.TYPE_UNKNOWN));
        assertEquals(0, s.merge(new CallAudioState(false, CallAudioState.ROUTE_BLUETOOTH, 15)).getRoute());
    }

    @Test public void modernMicrophoneMuteOverridesStaleLegacyWithoutUnmuting() {
        EndpointAudioState s = new EndpointAudioState();
        s.selected(endpoint(CallEndpoint.TYPE_BLUETOOTH));
        s.muted(true);
        org.junit.Assert.assertTrue(s.merge(new CallAudioState(false, CallAudioState.ROUTE_BLUETOOTH, 15)).isMuted());
        org.junit.Assert.assertTrue(s.merge(null).isMuted());
    }

    @Test public void rebuildReturnsToThePreviouslySelectedActualBluetoothEndpoint() {
        EndpointAudioState s = new EndpointAudioState();
        CallEndpoint car = endpoint(CallEndpoint.TYPE_BLUETOOTH);
        CallEndpoint other = endpoint(CallEndpoint.TYPE_BLUETOOTH);
        CallEndpoint phone = endpoint(CallEndpoint.TYPE_EARPIECE);
        s.available(List.of(other, phone, car));
        assertNull(s.target(CallAudioState.ROUTE_BLUETOOTH)); // No guessing between devices.
        org.junit.Assert.assertTrue(s.bluetoothTargetAmbiguous());
        s.selected(car);
        s.selected(phone);
        assertSame(car, s.target(CallAudioState.ROUTE_BLUETOOTH));
        org.junit.Assert.assertFalse(s.bluetoothTargetAmbiguous());
        assertSame(phone, s.target(CallAudioState.ROUTE_WIRED_OR_EARPIECE));
        s.available(List.of());
        assertNull(s.target(CallAudioState.ROUTE_BLUETOOTH));
    }

    @Test public void endpointOutcomesIgnoreSupersededAndDuplicateCallbacks() {
        EndpointAudioState s = new EndpointAudioState();
        s.available(List.of(endpoint(CallEndpoint.TYPE_BLUETOOTH), endpoint(CallEndpoint.TYPE_EARPIECE)));
        List<OutcomeReceiver<Void, CallEndpointException>> callbacks = new ArrayList<>();
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();
        s.request(CallAudioState.ROUTE_WIRED_OR_EARPIECE, (e, c) -> callbacks.add(c), success::incrementAndGet, failure::set);
        s.request(CallAudioState.ROUTE_BLUETOOTH, (e, c) -> callbacks.add(c), success::incrementAndGet, failure::set);
        callbacks.get(0).onError(new CallEndpointException(null, CallEndpointException.ERROR_REQUEST_TIME_OUT));
        assertEquals(0, failure.get());
        callbacks.get(1).onError(new CallEndpointException(null, CallEndpointException.ERROR_ENDPOINT_DOES_NOT_EXIST));
        callbacks.get(1).onResult(null);
        assertEquals(CallEndpointException.ERROR_ENDPOINT_DOES_NOT_EXIST, failure.get());
        assertEquals(0, success.get());
    }
}
