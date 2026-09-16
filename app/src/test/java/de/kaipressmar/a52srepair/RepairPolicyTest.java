package de.kaipressmar.a52srepair;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class RepairPolicyTest {
    private RepairPolicy.State state(boolean bt, boolean sco, boolean selected, boolean active, int samples) {
        return new RepairPolicy.State(bt, sco, selected, active, samples);
    }

    @Test public void bluetoothOff_neverRepairs() {
        assertEquals(RepairPolicy.Action.NONE, RepairPolicy.recommend(state(false, true, false, false, 99)));
    }

    @Test public void noScoEndpoint_neverRepairs() {
        assertEquals(RepairPolicy.Action.NONE, RepairPolicy.recommend(state(true, false, false, false, 99)));
    }

    @Test public void activeCall_neverRepairs() {
        assertEquals(RepairPolicy.Action.NONE, RepairPolicy.recommend(state(true, true, false, true, 99)));
    }

    @Test public void healthyScoRoute_neverRepairs() {
        assertEquals(RepairPolicy.Action.NONE, RepairPolicy.recommend(state(true, true, true, false, 99)));
    }

    @Test public void firstSuspectSample_onlySuggestsRoutingReset() {
        assertEquals(RepairPolicy.Action.RESET_ROUTING, RepairPolicy.recommend(state(true, true, false, false, 1)));
    }

    @Test public void secondSuspectSample_stillOnlySuggestsRoutingReset() {
        assertEquals(RepairPolicy.Action.RESET_ROUTING, RepairPolicy.recommend(state(true, true, false, false, 2)));
    }

    @Test public void threeSuspectSamples_allowScoRestart() {
        assertEquals(RepairPolicy.Action.RESTART_SCO, RepairPolicy.recommend(state(true, true, false, false, 3)));
    }

    @Test public void manySuspectSamples_doNotEscalateBeyondScoRestart() {
        assertEquals(RepairPolicy.Action.RESTART_SCO, RepairPolicy.recommend(state(true, true, false, false, Integer.MAX_VALUE)));
    }

    @Test public void safetyConditionsOverrideEscalation() {
        assertEquals(RepairPolicy.Action.NONE, RepairPolicy.recommend(state(true, true, false, true, Integer.MAX_VALUE)));
        assertEquals(RepairPolicy.Action.NONE, RepairPolicy.recommend(state(false, true, false, false, Integer.MAX_VALUE)));
        assertEquals(RepairPolicy.Action.NONE, RepairPolicy.recommend(state(true, false, false, false, Integer.MAX_VALUE)));
    }
}
