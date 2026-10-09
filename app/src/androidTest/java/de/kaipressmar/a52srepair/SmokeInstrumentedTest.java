package de.kaipressmar.a52srepair;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.media.AudioManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import de.kaipressmar.a52srepair.diagnostics.DiagnosticReport;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SmokeInstrumentedTest {
    @Test public void diagnosticReportIsReadOnly() {
        Context context = ApplicationProvider.getApplicationContext();
        AudioManager audio = context.getSystemService(AudioManager.class);
        int before = audio.getMode();
        assertTrue(DiagnosticReport.build(context).contains("app="));
        assertEquals("Diagnostics must not change the audio mode", before, audio.getMode());
    }
}
