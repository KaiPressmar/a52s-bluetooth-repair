package de.kaipressmar.a52srepair.telecom;

import static org.junit.Assert.assertEquals;

import android.telecom.Call;
import android.telecom.CallAudioState;
import de.kaipressmar.a52srepair.core.model.AudioRoute;
import de.kaipressmar.a52srepair.core.model.CallPhase;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class TelecomMappingTest {
    @Test public void mapsTelecomRoutes() {
        assertEquals(AudioRoute.BLUETOOTH, TelecomMapping.route(CallAudioState.ROUTE_BLUETOOTH));
        assertEquals(AudioRoute.EARPIECE, TelecomMapping.route(CallAudioState.ROUTE_EARPIECE));
        assertEquals(AudioRoute.SPEAKER, TelecomMapping.route(CallAudioState.ROUTE_SPEAKER));
        assertEquals(AudioRoute.WIRED_HEADSET, TelecomMapping.route(CallAudioState.ROUTE_WIRED_HEADSET));
        assertEquals(AudioRoute.UNKNOWN, TelecomMapping.route(0));
    }

    @Test public void mapsCallStates() {
        assertEquals(CallPhase.ACTIVE, TelecomMapping.phase(Call.STATE_ACTIVE));
        assertEquals(CallPhase.DIALING, TelecomMapping.phase(Call.STATE_DIALING));
        assertEquals(CallPhase.DIALING, TelecomMapping.phase(Call.STATE_CONNECTING));
        assertEquals(CallPhase.RINGING, TelecomMapping.phase(Call.STATE_RINGING));
        assertEquals(CallPhase.HOLDING, TelecomMapping.phase(Call.STATE_HOLDING));
        assertEquals(CallPhase.ENDED, TelecomMapping.phase(Call.STATE_DISCONNECTED));
    }

    @Test public void activeCallDominatesWaitingCall() {
        assertEquals(CallPhase.ACTIVE, TelecomMapping.dominantPhase(
                Arrays.asList(Call.STATE_RINGING, Call.STATE_ACTIVE)));
        assertEquals(CallPhase.ENDED, TelecomMapping.dominantPhase(Collections.emptyList()));
    }
}
