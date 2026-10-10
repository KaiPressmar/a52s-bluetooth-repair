package de.kaipressmar.a52srepair.core;

import static org.junit.Assert.assertEquals;
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
        assertEquals("Galaxy A52s 5G", DeviceFamily.A52S.displayName);
        assertEquals(PreventiveRebuildMode.ALWAYS, PreventiveRebuildMode.defaultFor(DeviceFamily.A52S));
        assertEquals(PreventiveRebuildMode.AFTER_PROBLEMS, PreventiveRebuildMode.defaultFor(DeviceFamily.S22));
        assertEquals(PreventiveRebuildMode.AFTER_PROBLEMS, PreventiveRebuildMode.defaultFor(DeviceFamily.OTHER));
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

    @Test public void versionOrderingIgnoresMetadataAndUsesNumericPrereleaseIdentifiers() {
        assertEquals(0, SemanticVersion.compare("v1.2.3+build.1", "1.2.3+build.2"));
        assertTrue(SemanticVersion.compare("1.2.3-rc.10", "1.2.3-rc.2") > 0);
        assertTrue(SemanticVersion.compare("1.2.3-1", "1.2.3-alpha") < 0);
        assertTrue(SemanticVersion.compare("1.2.3-alpha.1", "1.2.3-alpha") > 0);
        assertTrue(SemanticVersion.compare("2147483648.0.0", "2147483647.99.99") > 0);
        assertTrue(SemanticVersion.compare(null, "1.2.3") < 0);
        assertEquals(0, SemanticVersion.compare(null, "garbage"));
    }

    @Test public void rejectsMalformedVersionsAndLeadingZeroes() {
        for (String tag : new String[] {"1.2.3.4", "01.2.3", "1.2.3-01", "1.2.3-", "1.2.3+", "1.2.3-a..b"}) {
            assertNull(SemanticVersion.normalize(tag));
        }
        assertEquals("1.2.3-beta-1+build.01", SemanticVersion.normalize(" V1.2.3-beta-1+build.01 "));
    }
}
