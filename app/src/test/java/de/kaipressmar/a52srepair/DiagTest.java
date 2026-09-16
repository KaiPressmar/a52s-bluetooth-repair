package de.kaipressmar.a52srepair;

import static org.junit.Assert.*;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class DiagTest {
    private Context context;
    private File log;

    @Before public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        log = new File(context.getFilesDir(), "a52s-bt-repair.log");
        log.delete();
    }

    @Test public void logCreatesFileAndPersistsEvent() throws Exception {
        Diag.log(context, "TEST EVENT");
        assertTrue(log.exists());
        String text = new String(Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8);
        assertTrue(text.contains("TEST EVENT"));
    }

    @Test public void logAppendsInsteadOfOverwriting() throws Exception {
        Diag.log(context, "FIRST");
        Diag.log(context, "SECOND");
        String text = new String(Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8);
        assertTrue(text.contains("FIRST"));
        assertTrue(text.contains("SECOND"));
        assertTrue(text.indexOf("FIRST") < text.indexOf("SECOND"));
    }

    @Test public void snapshotContainsStableDiagnosticKeys() {
        String snapshot = Diag.snapshot(context);
        assertTrue(snapshot.contains("device="));
        assertTrue(snapshot.contains("bluetoothEnabled="));
        assertTrue(snapshot.contains("audioMode="));
        assertTrue(snapshot.contains("communicationDevice="));
        assertTrue(snapshot.contains("outputs:"));
    }
}
