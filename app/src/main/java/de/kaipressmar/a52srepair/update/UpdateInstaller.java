package de.kaipressmar.a52srepair.update;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import androidx.annotation.StringRes;
import androidx.core.content.FileProvider;
import de.kaipressmar.a52srepair.R;
import de.kaipressmar.a52srepair.core.version.SemanticVersion;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.net.URI;

/** Downloads, verifies (source, SHA-256, package, version) and hands the APK to Android. */
public final class UpdateInstaller {
    public interface Listener {
        void onStatus(String message, boolean error);
    }

    /** Verification failure with a user-facing, localized reason. */
    static final class UpdateException extends Exception {
        @StringRes final int reason;

        UpdateException(@StringRes int reason) {
            super(null, null, false, false);
            this.reason = reason;
        }
    }

    static final String TRUSTED_PREFIX =
            "https://github.com/KaiPressmar/a52s-bluetooth-repair/releases/download/";

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private UpdateInstaller() {}

    public static void install(Activity activity, UpdateRelease release, Listener listener) {
        Handler main = new Handler(Looper.getMainLooper());
        if (release == null) {
            listener.onStatus(activity.getString(R.string.update_unavailable), true);
            return;
        }
        if (!activity.getPackageManager().canRequestPackageInstalls()) {
            listener.onStatus(activity.getString(R.string.update_allow_install), false);
            activity.startActivity(
                    new Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + activity.getPackageName())));
            return;
        }
        if (!isTrustedDownloadUrl(release.apkUrl) || !isTrustedDownloadUrl(release.checksumUrl)) {
            listener.onStatus(activity.getString(R.string.update_untrusted), true);
            return;
        }

        Context app = activity.getApplicationContext();
        listener.onStatus(activity.getString(R.string.update_downloading), false);
        EXECUTOR.execute(
                () -> {
                    int reason;
                    try {
                        File apk = downloadAndVerify(app, release);
                        main.post(() -> launchInstaller(activity, apk, listener));
                        return;
                    } catch (UpdateException e) {
                        reason = e.reason;
                    } catch (IOException e) {
                        reason = R.string.update_error_network;
                    } catch (Exception e) {
                        reason = R.string.update_error_package;
                    }
                    String message = activity.getString(R.string.update_error, activity.getString(reason));
                    main.post(() -> listener.onStatus(message, true));
                });
    }

    private static File downloadAndVerify(Context app, UpdateRelease release) throws Exception {
        return downloadAndVerify(app, release, new DownloadSource() {
            @Override public String checksum(String url) throws IOException { return HttpClient.fetchText(url); }
            @Override public void apk(String url, File target) throws IOException { HttpClient.downloadTo(url, target); }
        });
    }

    interface DownloadSource {
        String checksum(String url) throws IOException;
        void apk(String url, File target) throws IOException;
    }

    static File downloadAndVerify(Context app, UpdateRelease release, DownloadSource source) throws Exception {
        if (release == null || !isTrustedDownloadUrl(release.apkUrl)
                || !isTrustedDownloadUrl(release.checksumUrl)
                || !UpdateRelease.expectedApkName(release.version).equals(release.apkName)
                || SemanticVersion.normalize(release.version) == null) {
            throw new UpdateException(R.string.update_untrusted);
        }
        File dir = new File(app.getCacheDir(), "updates");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("update directory unavailable");
        deleteOthers(dir, release.apkName);

        String expected;
        try {
            expected = parseSha256(source.checksum(release.checksumUrl));
        } catch (IllegalArgumentException e) {
            throw new UpdateException(R.string.update_error_checksum);
        }
        File apk = new File(dir, release.apkName);
        boolean verified = false;
        try {
            source.apk(release.apkUrl, apk);
            if (!sha256(apk).equalsIgnoreCase(expected)) throw new UpdateException(R.string.update_error_checksum);
            if (!packageMatches(app, apk, release.version)) throw new UpdateException(R.string.update_error_package);
            verified = true;
            return apk;
        } finally {
            // Partial downloads and failed APK parsing must not leave an installable cache file.
            if (!verified) apk.delete();
        }
    }

    static boolean isTrustedDownloadUrl(String url) {
        if (url == null || !url.startsWith(TRUSTED_PREFIX)) return false;
        try {
            URI uri = URI.create(url);
            String asset = url.substring(TRUSTED_PREFIX.length());
            return uri.getQuery() == null && uri.getFragment() == null
                    && asset.matches("[0-9A-Za-z._+-]+/[0-9A-Za-z._+-]+")
                    && !asset.startsWith("../") && !asset.startsWith("./")
                    && !asset.endsWith("/..") && !asset.endsWith("/.");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** First token of a {@code sha256sum} sidecar; throws for anything but 64 hex digits. */
    static String parseSha256(String text) {
        if (text == null || text.trim().isEmpty()) throw new IllegalArgumentException("empty checksum");
        String token = text.trim().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (!token.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("invalid checksum");
        return token;
    }

    private static void launchInstaller(Activity activity, File apk, Listener listener) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        try {
            Uri uri = FileProvider.getUriForFile(
                    activity, activity.getPackageName() + ".fileprovider", apk);
            activity.startActivity(
                    new Intent(Intent.ACTION_VIEW)
                            .setDataAndType(uri, "application/vnd.android.package-archive")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
            listener.onStatus(activity.getString(R.string.update_verified), false);
        } catch (RuntimeException e) {
            listener.onStatus(activity.getString(R.string.update_installer_failed), true);
        }
    }

    private static boolean packageMatches(Context context, File apk, String expectedVersion) {
        PackageInfo info = context.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), 0);
        return info != null
                && context.getPackageName().equals(info.packageName)
                && info.versionName != null
                && SemanticVersion.compare(info.versionName, expectedVersion) == 0;
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) hex.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return hex.toString();
    }

    private static void deleteOthers(File dir, String keepName) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            //noinspection ResultOfMethodCallIgnored
            if (!file.getName().equals(keepName)) file.delete();
        }
    }
}
