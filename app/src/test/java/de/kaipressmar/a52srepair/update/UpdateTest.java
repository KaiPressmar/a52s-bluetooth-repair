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
        String json = "[" + release("v0.19.0", false, "bluetooth-repair-v0.19.0.apk")
                + "," + release("v0.18.0", false,
                        "bluetooth-repair-v0.18.0.apk", "bluetooth-repair-v0.18.0.apk.sha256",
                        "bluetooth-repair-a52s-5g-v0.18.0.apk", "bluetooth-repair-a52s-5g-v0.18.0.apk.sha256")
                + "," + release("v0.20.0", true,
                        "bluetooth-repair-v0.20.0.apk", "bluetooth-repair-v0.20.0.apk.sha256")
                + "]";
        UpdateRelease best = UpdateRelease.selectNewest(json, "0.17.0");
        assertEquals("0.18.0", best.version);
        assertEquals("bluetooth-repair-v0.18.0.apk", best.apkName);
        assertNull(UpdateRelease.selectNewest(json, "0.18.0"));
    }

    @Test public void apkNameMatchesReleaseWorkflow() {
        assertEquals("bluetooth-repair-v1.0.0.apk", UpdateRelease.expectedApkName("1.0.0"));
    }

    @Test public void legacyPerDeviceAssetsAloneAreIgnored() throws Exception {
        String json = "[" + release("v0.18.0", false,
                "bluetooth-repair-galaxy-s22-v0.18.0.apk", "bluetooth-repair-galaxy-s22-v0.18.0.apk.sha256") + "]";
        assertNull(UpdateRelease.selectNewest(json, "0.17.0"));
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
