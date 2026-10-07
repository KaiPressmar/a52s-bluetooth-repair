package de.kaipressmar.a52srepair;

import org.junit.Test;
import static org.junit.Assert.*;

public class UpdateManagerTest {
    @Test public void parsesStandardSha256Sidecar() {
        String hash =
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        assertEquals(
                hash,
                UpdateManager.parseSha256(
                        hash + "  bluetooth-repair-a52s-5g-v0.8.0.apk\n"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMalformedChecksum() {
        UpdateManager.parseSha256("not-a-checksum");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsEmptyChecksum() {
        UpdateManager.parseSha256("  ");
    }
}
