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
@Config(sdk = 34)
public class A52sProfileTest {
    @Test public void a52sTargetsAndroid14AndRebuildsOnEveryCall() {
        Context context = ApplicationProvider.getApplicationContext();
        assertEquals("a52s", context.getString(R.string.device_profile_key));
        assertEquals(34, context.getApplicationInfo().targetSdkVersion);
        assertEquals(PreventiveRebuildMode.ALWAYS, new AppSettings(context).preventiveMode());
    }
}
