package de.kaipressmar.a52srepair.telecom;

import android.telecom.Call;
import android.telecom.CallAudioState;
import de.kaipressmar.a52srepair.core.model.AudioRoute;
import de.kaipressmar.a52srepair.core.model.CallPhase;
import java.util.List;

/** Maps Telecom constants to the platform-independent core model. */
final class TelecomMapping {
    /** {@code CallAudioState.ROUTE_STREAMING}, API 34. */
    private static final int ROUTE_STREAMING = 0x10;

    private TelecomMapping() {}

    static AudioRoute route(int telecomRoute) {
        switch (telecomRoute) {
            case CallAudioState.ROUTE_BLUETOOTH:
                return AudioRoute.BLUETOOTH;
            case CallAudioState.ROUTE_EARPIECE:
                return AudioRoute.EARPIECE;
            case CallAudioState.ROUTE_SPEAKER:
                return AudioRoute.SPEAKER;
            case CallAudioState.ROUTE_WIRED_HEADSET:
                return AudioRoute.WIRED_HEADSET;
            case ROUTE_STREAMING:
                return AudioRoute.STREAMING;
            default:
                return AudioRoute.UNKNOWN;
        }
    }

    static CallPhase phase(int callState) {
        switch (callState) {
            case Call.STATE_ACTIVE:
                return CallPhase.ACTIVE;
            case Call.STATE_DIALING:
            case Call.STATE_CONNECTING:
            case Call.STATE_PULLING_CALL:
            case Call.STATE_SELECT_PHONE_ACCOUNT:
                return CallPhase.DIALING;
            case Call.STATE_HOLDING:
                return CallPhase.HOLDING;
            case Call.STATE_DISCONNECTING:
            case Call.STATE_DISCONNECTED:
                return CallPhase.ENDED;
            case Call.STATE_NEW:
            case Call.STATE_RINGING:
            case Call.STATE_SIMULATED_RINGING:
            case Call.STATE_AUDIO_PROCESSING:
            default:
                return CallPhase.RINGING;
        }
    }

    /** The phase that matters for audio when several calls exist (e.g. call waiting). */
    static CallPhase dominantPhase(List<Integer> callStates) {
        CallPhase best = CallPhase.ENDED;
        for (int state : callStates) {
            CallPhase phase = phase(state);
            if (rank(phase) > rank(best)) best = phase;
        }
        return best;
    }

    private static int rank(CallPhase phase) {
        switch (phase) {
            case ACTIVE:
                return 4;
            case DIALING:
                return 3;
            case HOLDING:
                return 2;
            case RINGING:
                return 1;
            case ENDED:
            default:
                return 0;
        }
    }
}
