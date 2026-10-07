package de.kaipressmar.a52srepair;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import androidx.core.content.FileProvider;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

final class UpdateManager {
    interface CheckCallback {
        void onResult(UpdateRelease release, boolean networkChecked, String error);
    }

    interface InstallCallback {
        void onStatus(String message, boolean error);
    }

    private static final String RELEASES_URL =
            "https://api.github.com/repos/KaiPressmar/a52s-bluetooth-repair/releases?per_page=20";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean CHECKING = new AtomicBoolean(false);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private UpdateManager() {}

    static void checkForUpdates(Context context, boolean force, CheckCallback callback) {
        Context app = context.getApplicationContext();
        long now = System.currentTimeMillis();

        if (!force && !UpdateStateStore.shouldCheck(app, now)) {
            postCheck(callback, UpdateStateStore.cachedRelease(app), false, UpdateStateStore.lastError(app));
            return;
        }

        if (!CHECKING.compareAndSet(false, true)) {
            postCheck(callback, UpdateStateStore.cachedRelease(app), false, "");
            return;
        }

        EXECUTOR.execute(
                () -> {
                    UpdateRelease release = null;
                    String error = "";
                    try {
                        String json = fetchText(RELEASES_URL, appVersion(app));
                        release =
                                UpdateRelease.selectNewest(
                                        json,
                                        app.getString(R.string.device_profile_key),
                                        appVersion(app));
                        UpdateStateStore.saveCheck(app, release, null, System.currentTimeMillis());
                    } catch (Exception e) {
                        error = e.getClass().getSimpleName() + ": " + safeMessage(e);
                        UpdateStateStore.saveCheck(
                                app,
                                UpdateStateStore.cachedRelease(app),
                                error,
                                System.currentTimeMillis());
                    } finally {
                        CHECKING.set(false);
                    }
                    postCheck(callback, release != null ? release : UpdateStateStore.cachedRelease(app), true, error);
                });
    }

    static void installUpdate(
            Activity activity,
            UpdateRelease release,
            InstallCallback callback) {
        if (release == null) {
            postInstall(callback, "Kein Update verfügbar.", true);
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !activity.getPackageManager().canRequestPackageInstalls()) {
            postInstall(
                    callback,
                    "Android muss Installationen aus dieser Quelle einmal erlauben.",
                    false);
            Intent permission =
                    new Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(permission);
            return;
        }

        Context app = activity.getApplicationContext();
        postInstall(callback, "Update wird sicher heruntergeladen …", false);

        EXECUTOR.execute(
                () -> {
                    try {
                        File dir = new File(app.getCacheDir(), "updates");
                        if (!dir.exists() && !dir.mkdirs()) {
                            throw new IllegalStateException("Update-Verzeichnis konnte nicht angelegt werden");
                        }
                        deleteOldApks(dir, release.apkName);

                        String checksumText = fetchText(release.checksumUrl, appVersion(app));
                        String expected = parseSha256(checksumText);
                        File apk = new File(dir, release.apkName);
                        downloadFile(release.apkUrl, apk, appVersion(app));

                        String actual = sha256(apk);
                        if (!actual.equalsIgnoreCase(expected)) {
                            apk.delete();
                            throw new SecurityException("SHA-256-Prüfsumme stimmt nicht");
                        }

                        verifyDownloadedPackage(app, apk, release.version);

                        MAIN.post(
                                () -> {
                                    try {
                                        Uri uri =
                                                FileProvider.getUriForFile(
                                                        activity,
                                                        activity.getPackageName() + ".fileprovider",
                                                        apk);
                                        Intent install =
                                                new Intent(Intent.ACTION_VIEW)
                                                        .setDataAndType(
                                                                uri,
                                                                "application/vnd.android.package-archive")
                                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                        postInstall(
                                                callback,
                                                "Prüfsumme verifiziert. Android-Installation wird geöffnet.",
                                                false);
                                        activity.startActivity(install);
                                    } catch (RuntimeException e) {
                                        postInstall(
                                                callback,
                                                "Installer konnte nicht geöffnet werden: "
                                                        + e.getClass().getSimpleName(),
                                                true);
                                    }
                                });
                    } catch (Exception e) {
                        postInstall(
                                callback,
                                "Update fehlgeschlagen: "
                                        + e.getClass().getSimpleName()
                                        + (safeMessage(e).isEmpty()
                                                ? ""
                                                : " · " + safeMessage(e)),
                                true);
                    }
                });
    }

    static String parseSha256(String text) {
        if (text == null) throw new IllegalArgumentException("Leere Prüfsumme");
        String trimmed = text.trim();
        if (trimmed.isEmpty()) throw new IllegalArgumentException("Leere Prüfsumme");
        String token = trimmed.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (!token.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Ungültige SHA-256-Prüfsumme");
        }
        return token;
    }

    private static void verifyDownloadedPackage(
            Context context,
            File apk,
            String expectedVersion) throws Exception {
        PackageInfo info =
                context.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), 0);
        if (info == null) {
            throw new SecurityException("APK-Metadaten konnten nicht gelesen werden");
        }
        if (!context.getPackageName().equals(info.packageName)) {
            throw new SecurityException("APK gehört zu einer anderen App");
        }
        if (info.versionName == null
                || UpdateRelease.compareVersions(info.versionName, expectedVersion) != 0) {
            throw new SecurityException("APK-Version passt nicht zum Release");
        }
    }

    private static String fetchText(String url, String version) throws Exception {
        HttpURLConnection connection = open(url, version);
        try {
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("HTTP " + code);
            }
            StringBuilder out = new StringBuilder();
            try (BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    new BufferedInputStream(connection.getInputStream())))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    out.append(line).append('\n');
                }
            }
            return out.toString();
        } finally {
            connection.disconnect();
        }
    }

    private static void downloadFile(String url, File target, String version) throws Exception {
        HttpURLConnection connection = open(url, version);
        try {
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("HTTP " + code);
            }
            try (InputStream in = new BufferedInputStream(connection.getInputStream());
                    FileOutputStream out = new FileOutputStream(target)) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
        } finally {
            connection.disconnect();
        }
    }

    private static HttpURLConnection open(String url, String version) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(30_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "BluetoothRepair/" + version);
        connection.setRequestProperty("Accept", "application/vnd.github+json, application/octet-stream");
        return connection;
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) {
            hex.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        }
        return hex.toString();
    }

    private static void deleteOldApks(File dir, String keepName) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (!file.getName().equals(keepName)) file.delete();
        }
    }

    @SuppressWarnings("deprecation")
    private static String appVersion(Context context) {
        try {
            return context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0)
                    .versionName;
        } catch (Exception e) {
            return "0.0.0";
        }
    }

    private static void postCheck(
            CheckCallback callback,
            UpdateRelease release,
            boolean checked,
            String error) {
        if (callback == null) return;
        MAIN.post(() -> callback.onResult(release, checked, error == null ? "" : error));
    }

    private static void postInstall(
            InstallCallback callback,
            String message,
            boolean error) {
        if (callback == null) return;
        MAIN.post(() -> callback.onStatus(message, error));
    }

    private static String safeMessage(Exception e) {
        return e.getMessage() == null ? "" : e.getMessage();
    }
}
