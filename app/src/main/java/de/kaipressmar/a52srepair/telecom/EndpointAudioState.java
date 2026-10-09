package de.kaipressmar.a52srepair.telecom;

import android.telecom.CallAudioState;
import android.telecom.CallEndpoint;
import androidx.annotation.RequiresApi;
import java.util.List;

/** Modern endpoint observations override stale legacy route labels, without inventing endpoints. */
@RequiresApi(34)
@SuppressWarnings("deprecation")
final class EndpointAudioState {
    private CallEndpoint current;
    private Integer availableMask;

    void selected(CallEndpoint endpoint) { current = endpoint; }

    void available(List<CallEndpoint> endpoints) {
        int mask = 0;
        for (CallEndpoint endpoint : endpoints) mask |= route(endpoint.getEndpointType());
        availableMask = mask;
    }

    CallAudioState merge(CallAudioState legacy) {
        if (current == null && availableMask == null) return legacy;
        int route = current == null ? (legacy == null ? 0 : legacy.getRoute())
                : route(current.getEndpointType());
        int mask = availableMask == null ? (legacy == null ? 0 : legacy.getSupportedRouteMask())
                : availableMask;
        return new CallAudioState(legacy != null && legacy.isMuted(), route, mask);
    }

    private static int route(int type) {
        switch (type) {
            case CallEndpoint.TYPE_BLUETOOTH: return CallAudioState.ROUTE_BLUETOOTH;
            case CallEndpoint.TYPE_EARPIECE: return CallAudioState.ROUTE_EARPIECE;
            case CallEndpoint.TYPE_SPEAKER: return CallAudioState.ROUTE_SPEAKER;
            case CallEndpoint.TYPE_WIRED_HEADSET: return CallAudioState.ROUTE_WIRED_HEADSET;
            case CallEndpoint.TYPE_STREAMING: return CallAudioState.ROUTE_STREAMING;
            default: return 0;
        }
    }
}
