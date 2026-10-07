package de.kaipressmar.a52srepair;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
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

    @Test public void acceptsOnlyRepositoryReleaseDownloadUrls() {
        assertTrue(
                UpdateManager.isTrustedDownloadUrl(
                        "https://github.com/KaiPressmar/a52s-bluetooth-repair/releases/download/v0.8.0/bluetooth-repair-a52s-5g-v0.8.0.apk"));
        assertFalse(
                UpdateManager.isTrustedDownloadUrl(
                        "https://example.test/bluetooth-repair-a52s-5g-v0.8.0.apk"));
        assertFalse(
                UpdateManager.isTrustedDownloadUrl(
                        "http://github.com/KaiPressmar/a52s-bluetooth-repair/releases/download/v0.8.0/app.apk"));
    }
}
