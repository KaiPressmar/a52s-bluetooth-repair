package de.kaipressmar.a52srepair.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class UpdateTest {
    private static final String BASE =
            "https://github.com/KaiPressmar/a52s-bluetooth-repair/releases/download/";

    private static String release(String tag, boolean draft, String... assetNames) {
        StringBuilder assets = new StringBuilder();
        for (String name : assetNames) {
            if (assets.length() > 0) assets.append(',');
            assets.append("{\"name\":\"").append(name).append("\",\"browser_download_url\":\"")
                    .append(BASE).append(tag).append('/').append(name).append("\"}");
        }
        return "{\"tag_name\":\"" + tag + "\",\"draft\":" + draft
                + ",\"prerelease\":true,\"html_url\":\"x\",\"assets\":[" + assets + "]}";
    }

    @Test public void selectsNewestCompleteReleaseForFlavor() throws Exception {
        String json = "[" + release("v0.17.0", false, "bluetooth-repair-a52s-5g-v0.17.0.apk")
                + "," + release("v0.16.1", false,
                        "bluetooth-repair-a52s-5g-v0.16.1.apk", "bluetooth-repair-a52s-5g-v0.16.1.apk.sha256")
                + "," + release("v0.18.0", true,
                        "bluetooth-repair-a52s-5g-v0.18.0.apk", "bluetooth-repair-a52s-5g-v0.18.0.apk.sha256")
                + "]";
        UpdateRelease best = UpdateRelease.selectNewest(json, "a52s", "0.16.0");
        assertEquals("0.16.1", best.version);
        assertNull(UpdateRelease.selectNewest(json, "s22", "0.16.0"));
        assertNull(UpdateRelease.selectNewest(json, "a52s", "0.16.1"));
    }

    @Test public void apkNamesMatchReleaseWorkflow() {
        assertEquals("bluetooth-repair-a52s-5g-v1.0.0.apk", UpdateRelease.expectedApkName("a52s", "1.0.0"));
        assertEquals("bluetooth-repair-galaxy-s22-v1.0.0.apk", UpdateRelease.expectedApkName("s22", "1.0.0"));
        assertNull(UpdateRelease.expectedApkName("pixel", "1.0.0"));
    }

    @Test public void checksumParsing() {
        String hash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        assertEquals(hash, UpdateInstaller.parseSha256(hash + "  file.apk\n"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMalformedChecksum() {
        UpdateInstaller.parseSha256("nope");
    }

    @Test public void onlyRepositoryDownloadsAreTrusted() {
        assertTrue(UpdateInstaller.isTrustedDownloadUrl(BASE + "v1/app.apk"));
        assertFalse(UpdateInstaller.isTrustedDownloadUrl("https://example.test/app.apk"));
        assertFalse(UpdateInstaller.isTrustedDownloadUrl(null));
    }
}
