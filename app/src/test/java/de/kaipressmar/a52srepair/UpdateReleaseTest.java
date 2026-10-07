package de.kaipressmar.a52srepair;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class UpdateReleaseTest {
    private String release(
            String tag,
            boolean draft,
            boolean prerelease,
            String... assets) {
        StringBuilder assetJson = new StringBuilder();
        for (int i = 0; i < assets.length; i += 2) {
            if (assetJson.length() > 0) assetJson.append(",");
            assetJson
                    .append("{\"name\":\"")
                    .append(assets[i])
                    .append("\",\"browser_download_url\":\"")
                    .append(assets[i + 1])
                    .append("\"}");
        }
        return "{\"tag_name\":\""
                + tag
                + "\",\"draft\":"
                + draft
                + ",\"prerelease\":"
                + prerelease
                + ",\"html_url\":\"https://example.test/"
                + tag
                + "\",\"assets\":["
                + assetJson
                + "]}";
    }

    @Test public void semanticVersionComparisonIsNumeric() {
        assertTrue(UpdateRelease.compareVersions("0.10.0", "0.9.9") > 0);
        assertTrue(UpdateRelease.compareVersions("1.0.0", "0.99.99") > 0);
        assertEquals(0, UpdateRelease.compareVersions("0.8.0", "0.8.0"));
    }

    @Test public void stableVersionSortsAfterPrereleaseOfSameBase() {
        assertTrue(UpdateRelease.compareVersions("1.0.0", "1.0.0-rc1") > 0);
        assertTrue(UpdateRelease.compareVersions("1.0.0-rc2", "1.0.0") < 0);
    }

    @Test public void selectsNewestA52sReleaseWithApkAndChecksum() throws Exception {
        String json =
                "["
                        + release(
                                "v0.8.0",
                                false,
                                true,
                                "bluetooth-repair-a52s-5g-v0.8.0.apk",
                                "https://example.test/a52s.apk",
                                "bluetooth-repair-a52s-5g-v0.8.0.apk.sha256",
                                "https://example.test/a52s.sha256")
                        + ","
                        + release(
                                "v0.9.0",
                                false,
                                true,
                                "bluetooth-repair-a52s-5g-v0.9.0.apk",
                                "https://example.test/a52s-new.apk",
                                "bluetooth-repair-a52s-5g-v0.9.0.apk.sha256",
                                "https://example.test/a52s-new.sha256")
                        + "]";

        UpdateRelease update = UpdateRelease.selectNewest(json, "a52s", "0.7.0");

        assertNotNull(update);
        assertEquals("0.9.0", update.version);
        assertEquals("bluetooth-repair-a52s-5g-v0.9.0.apk", update.apkName);
        assertTrue(update.prerelease);
    }

    @Test public void selectsOnlyMatchingS22Asset() throws Exception {
        String json =
                "["
                        + release(
                                "v0.8.0",
                                false,
                                true,
                                "bluetooth-repair-a52s-5g-v0.8.0.apk",
                                "https://example.test/a52s.apk",
                                "bluetooth-repair-a52s-5g-v0.8.0.apk.sha256",
                                "https://example.test/a52s.sha256",
                                "bluetooth-repair-galaxy-s22-v0.8.0.apk",
                                "https://example.test/s22.apk",
                                "bluetooth-repair-galaxy-s22-v0.8.0.apk.sha256",
                                "https://example.test/s22.sha256")
                        + "]";

        UpdateRelease update = UpdateRelease.selectNewest(json, "s22", "0.7.0");

        assertNotNull(update);
        assertEquals("https://example.test/s22.apk", update.apkUrl);
        assertEquals("https://example.test/s22.sha256", update.checksumUrl);
    }

    @Test public void ignoresDraftAndIncompleteReleases() throws Exception {
        String json =
                "["
                        + release(
                                "v1.0.0",
                                true,
                                false,
                                "bluetooth-repair-a52s-5g-v1.0.0.apk",
                                "https://example.test/draft.apk",
                                "bluetooth-repair-a52s-5g-v1.0.0.apk.sha256",
                                "https://example.test/draft.sha")
                        + ","
                        + release(
                                "v0.9.0",
                                false,
                                true,
                                "bluetooth-repair-a52s-5g-v0.9.0.apk",
                                "https://example.test/no-checksum.apk")
                        + "]";

        assertNull(UpdateRelease.selectNewest(json, "a52s", "0.8.0"));
    }

    @Test public void currentOrOlderVersionIsNeverOffered() throws Exception {
        String json =
                "["
                        + release(
                                "v0.8.0",
                                false,
                                true,
                                "bluetooth-repair-a52s-5g-v0.8.0.apk",
                                "https://example.test/a.apk",
                                "bluetooth-repair-a52s-5g-v0.8.0.apk.sha256",
                                "https://example.test/a.sha")
                        + "]";

        assertNull(UpdateRelease.selectNewest(json, "a52s", "0.8.0"));
        assertNull(UpdateRelease.selectNewest(json, "a52s", "0.9.0"));
    }

    @Test public void unknownDeviceFlavorNeverDownloadsAnotherBuild() throws Exception {
        assertNull(UpdateRelease.expectedApkName("other", "1.0.0"));
        assertNull(UpdateRelease.selectNewest("[]", "other", "0.7.0"));
    }
}
