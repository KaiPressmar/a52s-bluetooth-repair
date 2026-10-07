package de.kaipressmar.a52srepair;

import org.junit.Test;
import static org.junit.Assert.*;

public class DeviceProfileTest {
    @Test public void detectsEuropeanA52s() {
        assertEquals(
                DeviceProfile.Family.A52S,
                DeviceProfile.detect("SM-A528B", "a52sxq"));
        assertTrue(DeviceProfile.matchesTarget("a52s", "SM-A528B", "a52sxq"));
        assertFalse(DeviceProfile.matchesTarget("s22", "SM-A528B", "a52sxq"));
    }

    @Test public void detectsEuropeanS22() {
        assertEquals(
                DeviceProfile.Family.S22,
                DeviceProfile.detect("SM-S901B", "r0s"));
        assertTrue(DeviceProfile.matchesTarget("s22", "SM-S901B", "r0s"));
        assertFalse(DeviceProfile.matchesTarget("a52s", "SM-S901B", "r0s"));
    }

    @Test public void detectsSnapdragonS22DeviceCodename() {
        assertEquals(
                DeviceProfile.Family.S22,
                DeviceProfile.detect("unknown", "r0q"));
    }

    @Test public void unknownDevicesDoNotMatchTailoredBuilds() {
        assertEquals(
                DeviceProfile.Family.OTHER,
                DeviceProfile.detect("SM-S999X", "other"));
        assertFalse(DeviceProfile.matchesTarget("a52s", "SM-S999X", "other"));
        assertFalse(DeviceProfile.matchesTarget("s22", "SM-S999X", "other"));
    }
}
