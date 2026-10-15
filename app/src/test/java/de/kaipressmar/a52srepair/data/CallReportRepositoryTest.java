package de.kaipressmar.a52srepair.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import de.kaipressmar.a52srepair.core.diagnosis.Fault;
import de.kaipressmar.a52srepair.core.report.CallHistory;
import de.kaipressmar.a52srepair.core.report.CallOutcome;
import de.kaipressmar.a52srepair.core.report.CallReport;
import java.util.EnumSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class CallReportRepositoryTest {
    private final Context context = ApplicationProvider.getApplicationContext();

    private static CallReport report(long at) {
        return new CallReport(at, 1_000L, CallOutcome.REPAIRED,
                EnumSet.of(Fault.DOWNLINK_NOT_ON_BLUETOOTH), 1, 0, true, false);
    }

    @Test public void persistsNewestFirstAndClears() {
        CallReportRepository repo = new CallReportRepository(context);
        repo.clear();
        repo.add(report(1L));
        repo.add(report(2L));
        CallHistory history = new CallReportRepository(context).history();
        assertEquals(2, history.all().size());
        assertEquals(2L, history.all().get(0).startedAt);
        repo.clear();
        assertTrue(repo.history().all().isEmpty());
    }

    @Test public void importsLegacyWithoutOverwritingItAndMarksAudioUnknown() throws Exception {
        CallReportRepository repo=new CallReportRepository(context); repo.clear();
        java.io.File old=new java.io.File(context.getFilesDir(),"call-reports.txt");
        String original="1;1;100;REPAIRED;;1;0;1;0;0\n";
        java.nio.file.Files.writeString(old.toPath(),original);
        assertTrue(repo.history().all().get(0).legacyEvidence);
        assertFalse(repo.history().all().get(0).audibleConfirmed);
        repo.add(report(2));
        assertEquals(2,new CallReportRepository(context).history().all().size());
        assertEquals(original,java.nio.file.Files.readString(old.toPath()));
        repo.add(report(3));
        assertEquals(3,repo.history().all().size());
        repo.clear(); assertFalse(old.exists());
    }

    @Test public void protectionIsOnByDefault() {
        assertTrue(new AppSettings(context).protectionEnabled());
    }
}
