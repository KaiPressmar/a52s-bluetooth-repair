package de.kaipressmar.a52srepair.data;
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
public class RecoveryJournalRepositoryTest {
    @Test public void dirtyLeaseSurvivesRecreationUntilActualCallEnd() throws Exception {
        Context c=ApplicationProvider.getApplicationContext();
        RecoveryJournalRepository j=new RecoveryJournalRepository(c);
        assertFalse(j.interrupted());
        assertTrue(j.reserve("generation",1,0));
        assertTrue(new RecoveryJournalRepository(c).interrupted());
        assertTrue(j.close("generation"));
        assertFalse(new RecoveryJournalRepository(c).interrupted());
        Files.writeString(new File(c.getFilesDir(),"recovery-journal-v1.txt").toPath(),"corrupt");
        assertTrue(j.interrupted());
    }
    @Test public void storageFailureDoesNotPretendToReserve() throws Exception {
        Context c=ApplicationProvider.getApplicationContext();
        File blocked=new File(c.getFilesDir(),"recovery-journal-v1.txt");
        assertTrue(blocked.mkdir());
        assertTrue(new File(blocked,"child").createNewFile());
        RecoveryJournalRepository j=new RecoveryJournalRepository(c);
        assertFalse(j.reserve("x",1,0));
        assertTrue(j.interrupted());
    }
}
