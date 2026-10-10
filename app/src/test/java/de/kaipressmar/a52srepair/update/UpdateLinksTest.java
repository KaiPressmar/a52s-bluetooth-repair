package de.kaipressmar.a52srepair.update;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import androidx.test.core.app.ApplicationProvider;
import de.kaipressmar.a52srepair.R;
import java.io.File;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {31, 34, 36})
public class UpdateLinksTest {
    private static UpdateRelease release(String version) {
        return new UpdateRelease(version, "ignored", "ignored", "ignored", "ignored",
                "https://evil.example/installer", false);
    }

    @Test public void browserOnlyOpensCanonicalReleaseEvenWithUntrustedMetadataHtmlUrl() {
        var controller = Robolectric.buildActivity(Activity.class).setup();
        Activity activity = controller.get();
        UpdateLinks.openReleasePage(activity, release("0.20.3"), (message, error) -> fail(message));
        Intent intent = shadowOf(activity).getNextStartedActivity();
        assertEquals(Intent.ACTION_VIEW, intent.getAction());
        assertEquals("https://github.com/KaiPressmar/a52s-bluetooth-repair/releases/tag/v0.20.3", intent.getDataString());
        assertEquals(0, intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION);
        controller.pause().stop().destroy();
        UpdateLinks.openReleasePage(activity, release("0.20.3"), (message, error) -> fail(message));
        assertNull(shadowOf(activity).getNextStartedActivity());
    }

    @Test public void invalidReleaseCannotNavigateOutsideProjectOrOpenInstaller() {
        var controller = Robolectric.buildActivity(Activity.class).setup();
        Activity activity = controller.get();
        for (UpdateRelease release : new UpdateRelease[] {null, release(null), release("../outside"), release("1.2.3?redirect=evil")}) {
            UpdateLinks.openReleasePage(activity, release, (message, error) -> {
                assertTrue(error); assertEquals(activity.getString(R.string.update_untrusted), message);
            });
            assertNull(shadowOf(activity).getNextStartedActivity());
        }
        controller.pause().stop().destroy();
    }

    public static class RejectingActivity extends Activity {
        @Override public void startActivity(Intent intent) { throw new android.content.ActivityNotFoundException(); }
    }

    @Test public void unavailableBrowserGivesFeedbackInsteadOfCrashing() {
        var controller = Robolectric.buildActivity(RejectingActivity.class).setup();
        UpdateLinks.openReleasePage(controller.get(), release("0.20.3"), (message, error) -> {
            assertTrue(error); assertEquals(controller.get().getString(R.string.update_browser_failed), message);
        });
        controller.pause().stop().destroy();
    }

    @Test public void packageInstallationPermissionIsAbsent() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_PERMISSIONS);
        assertFalse(java.util.Arrays.asList(info.requestedPermissions).contains("android.permission.REQUEST_INSTALL_PACKAGES"));
    }

    @Test public void legacyApkCacheCleanupDoesNotRemoveDiagnosticExports() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File updates = new File(context.getCacheDir(), "updates"), diagnostics = new File(context.getCacheDir(), "diagnostics");
        updates.mkdirs(); diagnostics.mkdirs();
        File apk = new File(updates, "old.apk"), report = new File(diagnostics, "report.txt");
        Files.writeString(apk.toPath(), "old"); Files.writeString(report.toPath(), "keep");
        UpdateLinks.removeLegacyDownloads(context);
        UpdateLinks.removeLegacyDownloads(context);
        assertFalse(apk.exists()); assertTrue(report.exists());
    }

    @Test public void diagnosticFingerprintUsesModernSigningInfoAndLegacyFallback() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        Signature signer = new Signature("abcd");
        info.signatures = new Signature[] {signer};
        info.signingInfo = null;
        shadowOf(context.getPackageManager()).installPackage(info);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(signer.toByteArray()));
        assertEquals(hash, UpdateLinks.installedSignerSha256(context));
        info.signingInfo = Shadow.newInstanceOf(SigningInfo.class);
        shadowOf(info.signingInfo).setSignatures(new Signature[] {signer});
        shadowOf(context.getPackageManager()).installPackage(info);
        assertEquals(hash, UpdateLinks.installedSignerSha256(context));
        shadowOf(info.signingInfo).setSignatures(new Signature[0]);
        assertEquals("unavailable", UpdateLinks.installedSignerSha256(context));
    }
}
