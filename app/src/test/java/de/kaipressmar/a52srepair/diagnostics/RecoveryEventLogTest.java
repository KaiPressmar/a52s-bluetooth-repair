package de.kaipressmar.a52srepair.diagnostics;
import static org.junit.Assert.*;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.io.File;
import java.nio.file.Files;
@RunWith(RobolectricTestRunner.class) @Config(sdk=34)
public class RecoveryEventLogTest {
    @Test public void arbitraryPrivateLogContentIsExcludedFromStandardExport() throws Exception {
        Context c=ApplicationProvider.getApplicationContext();
        DiagnosticLog.log(c,"contact=Alice number=+491234567890 device=AA:BB:CC:DD:EE:FF");
        Files.writeString(new File(c.getFilesDir(),"recovery-events-v1.txt").toPath(),
                "contact=Alice +491234567890\nDISPATCH;1;150\n");
        String report=DiagnosticReport.build(c);
        assertFalse(report.contains("Alice"));assertFalse(report.contains("+491234567890"));
        assertFalse(report.contains("AA:BB:CC:DD:EE:FF"));assertTrue(report.contains("DISPATCH;1;150"));
        DiagnosticLog.clear(c);assertFalse(RecoveryEventLog.read(c).contains("DISPATCH"));
    }
    @Test public void eventHistoryIsBoundedAndOversizeInputIsRejected() throws Exception {
        Context c=ApplicationProvider.getApplicationContext();
        for(int i=0;i<220;i++)RecoveryEventLog.record(c,RecoveryEventLog.Event.DISPATCH,i,i);
        assertEquals(201,RecoveryEventLog.read(c).split("\n").length);
        Files.writeString(new File(c.getFilesDir(),"recovery-events-v1.txt").toPath(),"x".repeat(33_000));
        assertEquals(1,RecoveryEventLog.read(c).split("\n").length);
    }
}
