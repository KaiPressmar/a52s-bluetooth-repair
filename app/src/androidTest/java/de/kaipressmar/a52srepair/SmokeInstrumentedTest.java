package de.kaipressmar.a52srepair;

import static org.junit.Assert.*;
import android.content.Context;
import android.media.AudioManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SmokeInstrumentedTest {
    @Test public void applicationContextAndAudioServiceAreAvailable() {
        Context c = ApplicationProvider.getApplicationContext();
        assertEquals("de.kaipressmar.a52srepair", c.getPackageName());
        assertNotNull(c.getSystemService(Context.AUDIO_SERVICE));
    }

    @Test public void diagnosticSnapshotDoesNotMutateAudioMode() {
        Context c = ApplicationProvider.getApplicationContext();
        AudioManager am = (AudioManager)c.getSystemService(Context.AUDIO_SERVICE);
        int before = am.getMode();
        String snapshot = Diag.snapshot(c);
        int after = am.getMode();
        assertNotNull(snapshot);
        assertEquals("Diagnostics must be read-only", before, after);
    }
}
