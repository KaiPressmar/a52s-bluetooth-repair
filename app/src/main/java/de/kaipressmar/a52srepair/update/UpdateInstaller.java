package de.kaipressmar.a52srepair.update;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import androidx.core.content.FileProvider;
import de.kaipressmar.a52srepair.core.version.SemanticVersion;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Downloads, verifies (source, SHA-256, package, version) and hands the APK to Android. */
public final class UpdateInstaller {
    public interface Listener {
        void onStatus(String message, boolean error);
    }

    static final String TRUSTED_PREFIX =
            "https://github.com/KaiPressmar/a52s-bluetooth-repair/releases/download/";

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private UpdateInstaller() {}

    public static void install(Activity activity, UpdateRelease release, Listener listener) {
        Handler main = new Handler(Looper.getMainLooper());
        if (release == null) {
            listener.onStatus("Kein Update verfügbar.", true);
            return;
        }
        if (!activity.getPackageManager().canRequestPackageInstalls()) {
            listener.onStatus("Bitte Installation aus dieser App einmalig erlauben.", false);
            activity.startActivity(
                    new Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + activity.getPackageName())));
            return;
        }
        if (!isTrustedDownloadUrl(release.apkUrl) || !isTrustedDownloadUrl(release.checksumUrl)) {
            listener.onStatus("Update-Quelle ist nicht vertrauenswürdig.", true);
            return;
        }

        Context app = activity.getApplicationContext();
        listener.onStatus("Update wird heruntergeladen …", false);
        EXECUTOR.execute(
                () -> {
                    try {
                        File dir = new File(app.getCacheDir(), "updates");
                        if (!dir.exists() && !dir.mkdirs()) {
                            throw new IOException("Update-Verzeichnis nicht verfügbar");
                        }
                        deleteOthers(dir, release.apkName);
                        String expected = parseSha256(HttpClient.fetchText(release.checksumUrl));
                        File apk = new File(dir, release.apkName);
                        HttpClient.downloadTo(release.apkUrl, apk);
                        if (!sha256(apk).equalsIgnoreCase(expected)) {
                            //noinspection ResultOfMethodCallIgnored
                            apk.delete();
                            throw new SecurityException("Prüfsumme stimmt nicht");
                        }
                        verifyPackage(app, apk, release.version);
                        main.post(() -> launchInstaller(activity, apk, listener));
                    } catch (Exception e) {
                        String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                        main.post(() -> listener.onStatus("Update fehlgeschlagen: " + reason, true));
                    }
                });
    }

    static boolean isTrustedDownloadUrl(String url) {
        return url != null && url.startsWith(TRUSTED_PREFIX);
    }

    static String parseSha256(String text) {
        if (text == null || text.trim().isEmpty()) throw new IllegalArgumentException("Leere Prüfsumme");
        String token = text.trim().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (!token.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Ungültige Prüfsumme");
        return token;
    }

    private static void launchInstaller(Activity activity, File apk, Listener listener) {
        try {
            Uri uri = FileProvider.getUriForFile(
                    activity, activity.getPackageName() + ".fileprovider", apk);
            activity.startActivity(
                    new Intent(Intent.ACTION_VIEW)
                            .setDataAndType(uri, "application/vnd.android.package-archive")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
            listener.onStatus("Prüfsumme verifiziert – Installation wird geöffnet.", false);
        } catch (RuntimeException e) {
            listener.onStatus("Installer konnte nicht geöffnet werden.", true);
        }
    }

    private static void verifyPackage(Context context, File apk, String expectedVersion) {
        PackageInfo info = context.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), 0);
        if (info == null) throw new SecurityException("APK nicht lesbar");
        if (!context.getPackageName().equals(info.packageName)) {
            throw new SecurityException("APK gehört zu einer anderen App");
        }
        if (info.versionName == null || SemanticVersion.compare(info.versionName, expectedVersion) != 0) {
            throw new SecurityException("APK-Version passt nicht zum Release");
        }
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
