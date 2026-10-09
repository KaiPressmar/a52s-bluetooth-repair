package de.kaipressmar.a52srepair;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import de.kaipressmar.a52srepair.core.repair.PreventiveRebuildMode;
import de.kaipressmar.a52srepair.data.AppSettings;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class S22ProfileTest {
    @Test public void s22KeepsAndroid16ProfileAndConservativeDefault() {
        Context context = ApplicationProvider.getApplicationContext();
        assertEquals("s22", context.getString(R.string.device_profile_key));
        assertEquals("Android 16", context.getString(R.string.device_runtime_name));
        assertEquals(PreventiveRebuildMode.AFTER_PROBLEMS, new AppSettings(context).preventiveMode());
    }
}
