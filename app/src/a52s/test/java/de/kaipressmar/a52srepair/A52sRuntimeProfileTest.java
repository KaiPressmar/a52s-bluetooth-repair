package de.kaipressmar.a52srepair;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class A52sRuntimeProfileTest {
    @Test public void a52sBuildIsExplicitlyValidatedAgainstOfficialAndroid14Runtime() {
        Context context = ApplicationProvider.getApplicationContext();

        assertEquals("a52s", context.getString(R.string.device_profile_key));
        assertEquals("Android 14 · One UI 6.1", context.getString(R.string.device_runtime_name));
        assertEquals("API 34", context.getString(R.string.device_runtime_api));
        assertTrue(
                context.getString(R.string.device_reference_firmware)
                        .contains("A528BXXSBGYI3"));
    }
}
