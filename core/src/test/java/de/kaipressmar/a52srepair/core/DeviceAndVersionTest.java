package de.kaipressmar.a52srepair.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import de.kaipressmar.a52srepair.core.device.DeviceFamily;
import de.kaipressmar.a52srepair.core.repair.PreventiveRebuildMode;
import de.kaipressmar.a52srepair.core.version.SemanticVersion;
import org.junit.Test;

public class DeviceAndVersionTest {
    @Test public void detectsDeviceFamilies() {
        assertEquals(DeviceFamily.A52S, DeviceFamily.detect("SM-A528B", "a52sxq"));
        assertEquals(DeviceFamily.A52S, DeviceFamily.detect(null, "a52sxq"));
        assertEquals(DeviceFamily.S22, DeviceFamily.detect("SM-S901B", "r0s"));
        assertEquals(DeviceFamily.OTHER, DeviceFamily.detect("Pixel 8", "shiba"));
        assertTrue(DeviceFamily.matchesProfile("a52s", "SM-A528B", "a52sxq"));
        assertFalse(DeviceFamily.matchesProfile("s22", "SM-A528B", "a52sxq"));
    }

    @Test public void versionsCompareSemantically() {
        assertEquals("0.16.0", SemanticVersion.normalize("v0.16.0"));
        assertNull(SemanticVersion.normalize("latest"));
        assertTrue(SemanticVersion.compare("0.16.0", "0.15.9") > 0);
        assertTrue(SemanticVersion.compare("0.10.0", "0.9.0") > 0);
        assertTrue(SemanticVersion.compare("1.0.0-beta.1", "1.0.0") < 0);
        assertEquals(0, SemanticVersion.compare("1.2.3", "1.2.3"));
    }

    @Test public void preventiveModeParsingFallsBack() {
        assertEquals(PreventiveRebuildMode.OFF, PreventiveRebuildMode.parse("off", PreventiveRebuildMode.ALWAYS));
        assertEquals(PreventiveRebuildMode.ALWAYS, PreventiveRebuildMode.parse("bogus", PreventiveRebuildMode.ALWAYS));
        assertEquals(PreventiveRebuildMode.ALWAYS, PreventiveRebuildMode.parse(null, PreventiveRebuildMode.ALWAYS));
    }
}
