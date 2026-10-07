package de.kaipressmar.a52srepair;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class S22RuntimeProfileTest {
    @Test public void s22BuildKeepsAndroid16RuntimeProfile() {
        Context context = ApplicationProvider.getApplicationContext();

        assertEquals("s22", context.getString(R.string.device_profile_key));
        assertEquals("Android 16", context.getString(R.string.device_runtime_name));
        assertEquals("API 36", context.getString(R.string.device_runtime_api));
    }
}
