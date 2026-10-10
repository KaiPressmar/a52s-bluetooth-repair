package de.kaipressmar.a52srepair.telecom;

import android.telecom.CallAudioState;
import android.telecom.CallEndpoint;
import android.telecom.CallEndpointException;
import android.os.OutcomeReceiver;
import androidx.annotation.RequiresApi;
import java.util.List;
import java.util.ArrayList;
import java.util.function.IntConsumer;

/** Modern endpoint observations override stale legacy route labels, without inventing endpoints. */
@RequiresApi(34)
@SuppressWarnings("deprecation")
final class EndpointAudioState {
    private CallEndpoint current;
    private Integer availableMask;
    private List<CallEndpoint> available = new ArrayList<>();
    private android.os.ParcelUuid lastBluetoothId;
    private long requestGeneration;
    private Boolean microphoneMute;

    void muted(boolean muted) { microphoneMute = muted; }

    void selected(CallEndpoint endpoint) {
        current = endpoint;
        if (endpoint.getEndpointType() == CallEndpoint.TYPE_BLUETOOTH) lastBluetoothId = endpoint.getIdentifier();
    }

    void available(List<CallEndpoint> endpoints) {
        available = new ArrayList<>(endpoints);
        int mask = 0;
        for (CallEndpoint endpoint : endpoints) mask |= route(endpoint.getEndpointType());
        availableMask = mask;
    }

    boolean hasAvailability() { return availableMask != null; }

    int bluetoothCount() {
        int count = 0;
        for (CallEndpoint endpoint : available) {
            if (endpoint.getEndpointType() == CallEndpoint.TYPE_BLUETOOTH) count++;
        }
        return count;
    }

    boolean bluetoothTargetAmbiguous() {
        return availableMask != null && (lastBluetoothId != null
                || (availableMask & CallAudioState.ROUTE_BLUETOOTH) != 0)
                && target(CallAudioState.ROUTE_BLUETOOTH) == null;
    }

    /** Select an actual advertised endpoint; never guess between multiple Bluetooth devices. */
    CallEndpoint target(int telecomRoute) {
        int wanted = telecomRoute == CallAudioState.ROUTE_WIRED_OR_EARPIECE
                ? CallAudioState.ROUTE_EARPIECE : telecomRoute;
        CallEndpoint only = null;
        int count = 0;
        for (CallEndpoint endpoint : available) {
            if (route(endpoint.getEndpointType()) != wanted) continue;
            if (wanted == CallAudioState.ROUTE_BLUETOOTH && lastBluetoothId != null
                    && lastBluetoothId.equals(endpoint.getIdentifier())) return endpoint;
            only = endpoint;
            count++;
        }
        if (wanted == CallAudioState.ROUTE_BLUETOOTH && lastBluetoothId != null) return null;
        return count == 1 ? only : null;
    }

    /** A legacy request supersedes the outstanding modern operation too. */
    void invalidateRequests() { requestGeneration++; }

    interface Requester {
        void request(CallEndpoint endpoint, OutcomeReceiver<Void, CallEndpointException> callback);
    }

    /** Ignore superseded and duplicate outcomes; success still requires audio verification. */
    void request(int route, Requester requester, Runnable changed, IntConsumer failed) {
        long generation = ++requestGeneration;
        CallEndpoint endpoint = target(route);
        if (endpoint == null) throw new IllegalStateException("No unambiguous available call endpoint");
        requester.request(endpoint, new OutcomeReceiver<Void, CallEndpointException>() {
            private boolean completed;
            @Override public void onResult(Void result) {
                if (completed || generation != requestGeneration) return;
                completed = true;
                changed.run();
            }
            @Override public void onError(CallEndpointException error) {
                if (completed || generation != requestGeneration) return;
                completed = true;
                failed.accept(error.getCode());
            }
        });
    }

    CallAudioState merge(CallAudioState legacy) {
        if (current == null && availableMask == null && microphoneMute == null) return legacy;
        int route = current == null ? (legacy == null ? 0 : legacy.getRoute())
                : route(current.getEndpointType());
        int mask = availableMask == null ? (legacy == null ? 0 : legacy.getSupportedRouteMask())
                : availableMask;
        boolean muted = microphoneMute == null ? legacy != null && legacy.isMuted() : microphoneMute;
        return new CallAudioState(muted, route, mask);
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
