package de.kaipressmar.a52srepair.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import de.kaipressmar.a52srepair.core.device.DeviceFamily;
import de.kaipressmar.a52srepair.core.repair.PreventiveRebuildMode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowBuild;

/** One APK, runtime adaptation: the same build behaves correctly on both phones and Android versions. */
@RunWith(RobolectricTestRunner.class)
public class DeviceInfoTest {
    private final Context context = ApplicationProvider.getApplicationContext();

    @Test @Config(sdk = 34) public void a52sOnAndroid14RebuildsOnEveryCall() {
        ShadowBuild.setModel("SM-A528B");
        ShadowBuild.setDevice("a52sxq");
        assertEquals(DeviceFamily.A52S, DeviceInfo.family());
        assertEquals("Galaxy A52s 5G", DeviceInfo.deviceName());
        assertTrue(DeviceInfo.androidVersion().contains("API 34"));
        assertEquals(PreventiveRebuildMode.ALWAYS, new AppSettings(context).preventiveMode());
    }

    @Test @Config(sdk = 36) public void s22OnAndroid16RebuildsOnlyAfterProblems() {
        ShadowBuild.setModel("SM-S901B");
        ShadowBuild.setDevice("r0s");
        assertEquals(DeviceFamily.S22, DeviceInfo.family());
        assertTrue(DeviceInfo.androidVersion().contains("API 36"));
        assertEquals(PreventiveRebuildMode.AFTER_PROBLEMS, new AppSettings(context).preventiveMode());
    }

    @Test @Config(sdk = 34) public void unknownDeviceShowsModelName() {
        ShadowBuild.setManufacturer("Google");
        ShadowBuild.setModel("Pixel 8");
        ShadowBuild.setDevice("shiba");
        assertEquals("Google Pixel 8", DeviceInfo.deviceName());
        assertEquals(PreventiveRebuildMode.AFTER_PROBLEMS, DeviceInfo.defaultPreventiveMode());
    }

    @Test @Config(sdk = 34) public void userChoiceOverridesDeviceDefault() {
        ShadowBuild.setModel("SM-A528B");
        ShadowBuild.setDevice("a52sxq");
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
                .edit().putString(AppSettings.KEY_PREVENTIVE_MODE, "OFF").commit();
        assertEquals(PreventiveRebuildMode.OFF, new AppSettings(context).preventiveMode());
    }

}
