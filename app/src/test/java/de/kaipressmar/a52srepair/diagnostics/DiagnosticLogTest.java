package de.kaipressmar.a52srepair.diagnostics;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import java.io.File;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class DiagnosticLogTest {
    private final Context context = ApplicationProvider.getApplicationContext();

    @Test public void rotatesInsteadOfGrowingForever() {
        DiagnosticLog.clear(context);
        String line = new String(new char[1024]).replace('\0', 'x');
        for (int i = 0; i < 600; i++) DiagnosticLog.log(context, line);
        File current = new File(context.getFilesDir(), "diagnostics.log");
        File previous = new File(context.getFilesDir(), "diagnostics.log.1");
        assertTrue(previous.exists());
        assertTrue(current.length() <= DiagnosticLog.MAX_BYTES + 2048);
        assertTrue(DiagnosticLog.readAll(context).length() <= 2 * (DiagnosticLog.MAX_BYTES + 2048));
    }

    @Test public void reportContainsEnvironmentAndCalls() {
        String report = DiagnosticReport.build(context);
        assertTrue(report.contains("family="));
        assertTrue(report.contains("bootElapsedMs="));
        assertTrue(report.contains("audioMode="));
        assertTrue(report.contains("== Calls"));
    }
}
