package de.kaipressmar.a52srepair.update;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import android.content.Context;
import android.content.pm.PackageInfo;
import androidx.test.core.app.ApplicationProvider;
import de.kaipressmar.a52srepair.R;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {34, 36})
public class UpdateInstallerTest {
    private final Context context = ApplicationProvider.getApplicationContext();
    private final byte[] bytes = {1, 2, 3, 4};
    private final UpdateRelease release = new UpdateRelease("0.20.2", "v0.20.2",
            "bluetooth-repair-v0.20.2.apk", UpdateInstaller.TRUSTED_PREFIX + "v0.20.2/app.apk",
            UpdateInstaller.TRUSTED_PREFIX + "v0.20.2/app.apk.sha256", "", false);

    private File target() { return new File(context.getCacheDir(), "updates/" + release.apkName); }

    private UpdateInstaller.DownloadSource source(String checksum, boolean interrupt) {
        return new UpdateInstaller.DownloadSource() {
            public String checksum(String url) { return checksum; }
            public void apk(String url, File target) throws IOException {
                Files.write(target.toPath(), bytes);
                if (interrupt) throw new IOException("interrupted download");
            }
        };
    }

    private String checksum() throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private void packageInfo(String packageName, String version) {
        PackageInfo info = new PackageInfo();
        info.packageName = packageName;
        info.versionName = version;
        shadowOf(context.getPackageManager()).setPackageArchiveInfo(target().getAbsolutePath(), info);
    }

    @Test public void verifiedDownloadKeepsOnlyExpectedApk() throws Exception {
        target().getParentFile().mkdirs();
        File stale = new File(target().getParentFile(), "old.apk");
        Files.write(stale.toPath(), bytes);
        packageInfo(context.getPackageName(), release.version);
        assertEquals(target(), UpdateInstaller.downloadAndVerify(context, release, source(checksum(), false)));
        assertArrayEquals(bytes, Files.readAllBytes(target().toPath()));
        assertFalse(stale.exists());
    }

    @Test public void interruptedDownloadIsRemovedAndPreservesNetworkFailure() throws Exception {
        try {
            UpdateInstaller.downloadAndVerify(context, release, source(checksum(), true));
            fail("must fail on an interrupted download");
        } catch (IOException expected) {
            assertFalse(target().exists());
        }
    }

    @Test public void checksumMismatchIsRemoved() throws Exception {
        try {
            UpdateInstaller.downloadAndVerify(context, release, source("0".repeat(64), false));
            fail("must reject mismatched bytes");
        } catch (UpdateInstaller.UpdateException expected) {
            assertEquals(R.string.update_error_checksum, expected.reason);
            assertFalse(target().exists());
        }
    }

    @Test public void wrongPackageAndVersionAreRemoved() throws Exception {
        for (String[] values : new String[][] {{"other.app", release.version}, {context.getPackageName(), "0.20.1"}}) {
            packageInfo(values[0], values[1]);
            try {
                UpdateInstaller.downloadAndVerify(context, release, source(checksum(), false));
                fail("must reject wrong package/version");
            } catch (UpdateInstaller.UpdateException expected) {
                assertEquals(R.string.update_error_package, expected.reason);
                assertFalse(target().exists());
            }
        }
    }

    @Test public void invalidChecksumNeverDownloadsApk() throws Exception {
        try {
            UpdateInstaller.downloadAndVerify(context, release, source("bad", false));
            fail("must reject bad checksum");
        } catch (UpdateInstaller.UpdateException expected) {
            assertEquals(R.string.update_error_checksum, expected.reason);
            assertFalse(target().exists());
        }
    }

    @Test public void invalidCachedFilenameCannotEscapeUpdateDirectory() throws Exception {
        UpdateRelease invalid = new UpdateRelease(release.version, release.tag, "../outside.apk",
                release.apkUrl, release.checksumUrl, "", false);
        try {
            UpdateInstaller.downloadAndVerify(context, invalid, source(checksum(), false));
            fail("must reject invalid filename");
        } catch (UpdateInstaller.UpdateException expected) {
            assertEquals(R.string.update_untrusted, expected.reason);
            assertFalse(new File(context.getCacheDir(), "outside.apk").exists());
        }
    }
}
