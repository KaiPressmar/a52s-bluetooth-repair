package de.kaipressmar.a52srepair.telecom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import android.os.ParcelUuid;
import android.telecom.CallAudioState;
import android.telecom.CallEndpoint;
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
}
