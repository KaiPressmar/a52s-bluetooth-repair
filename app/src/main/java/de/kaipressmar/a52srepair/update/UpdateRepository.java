package de.kaipressmar.a52srepair.update;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import de.kaipressmar.a52srepair.BuildConfig;
import de.kaipressmar.a52srepair.core.version.SemanticVersion;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Executor;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Checks the project's GitHub releases at most every 12 hours, only while the app is open.
 * The result is cached so the UI can show an available update without network access.
 */
public final class UpdateRepository {
    public interface Callback {
        void onResult(UpdateRelease newerRelease, String error);
    }

    static final String RELEASES_URL =
            "https://api.github.com/repos/KaiPressmar/a52s-bluetooth-repair/releases?per_page=20";
    static final long CHECK_INTERVAL_MS = 12L * 60L * 60L * 1000L;
    static final long RETRY_INTERVAL_MS = 60L * 60L * 1000L;

    private static final String PREFS = "update-state";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean CHECKING = new AtomicBoolean(false);

    private final Context context;
    private final SharedPreferences prefs;
    private final Executor executor;
    private final ReleaseFetcher fetcher;
    private final Handler main = new Handler(Looper.getMainLooper());

    public UpdateRepository(Context context) {
        this(context, EXECUTOR, () -> HttpClient.fetchText(RELEASES_URL));
    }

    interface ReleaseFetcher { String fetch() throws IOException; }

    UpdateRepository(Context context, Executor executor, ReleaseFetcher fetcher) {
        this.context = context.getApplicationContext();
        this.executor = executor;
        this.fetcher = fetcher;
        prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public UpdateRelease cachedNewerRelease() {
        String version = prefs.getString("version", null);
        String apk = prefs.getString("apk_url", null);
        String checksum = prefs.getString("checksum_url", null);
        if (version == null || !version.matches("[0-9]+\\.[0-9]+\\.[0-9]+")
                || SemanticVersion.normalize(version) == null
                || prefs.getBoolean("prerelease", false)
                || !UpdateRelease.expectedApkName(version).equals(prefs.getString("apk_name", ""))
                || !UpdateInstaller.isTrustedDownloadUrl(apk)
                || !UpdateInstaller.isTrustedDownloadUrl(checksum)) return null;
        if (SemanticVersion.compare(version, BuildConfig.VERSION_NAME) <= 0) return null;
        return new UpdateRelease(
                version,
                prefs.getString("tag", "v" + version),
                prefs.getString("apk_name", ""),
                apk,
                checksum,
                prefs.getString("release_url", ""),
                prefs.getBoolean("prerelease", false));
    }

    public long lastCheckAt() {
        return prefs.getLong("last_check", 0L);
    }

    /** {@code force} bypasses the interval (explicit "check now"). */
    public void check(boolean force, Callback callback) {
        long now = System.currentTimeMillis();
        if (!force && !due(now)) {
            callback.onResult(cachedNewerRelease(), prefs.getString("error", ""));
            return;
        }
        if (!CHECKING.compareAndSet(false, true)) {
            callback.onResult(cachedNewerRelease(), "");
            return;
        }
        executor.execute(
                () -> {
                    String error = "";
                    try {
                        String json = fetcher.fetch();
                        save(UpdateRelease.selectNewest(json, BuildConfig.VERSION_NAME), "");
                    } catch (Exception e) {
                        error = e.getClass().getSimpleName();
                        prefs.edit().putLong("last_check", System.currentTimeMillis())
                                .putString("error", error).apply();
                    } finally {
                        CHECKING.set(false);
                    }
                    String finalError = error;
                    main.post(() -> callback.onResult(cachedNewerRelease(), finalError));
                });
    }

    boolean due(long now) {
        long last = prefs.getLong("last_check", 0L);
        String error = prefs.getString("error", "");
        long interval = error == null || error.isEmpty() ? CHECK_INTERVAL_MS : RETRY_INTERVAL_MS;
        return last == 0L || now < last || now - last >= interval;
    }

    private void save(UpdateRelease release, String error) {
        SharedPreferences.Editor e =
                prefs.edit()
                        .putLong("last_check", System.currentTimeMillis())
                        .putString("error", error);
        if (release == null) {
            e.remove("version").remove("tag").remove("apk_name").remove("apk_url")
                    .remove("checksum_url").remove("release_url").remove("prerelease");
        } else {
            e.putString("version", release.version)
                    .putString("tag", release.tag)
                    .putString("apk_name", release.apkName)
                    .putString("apk_url", release.apkUrl)
                    .putString("checksum_url", release.checksumUrl)
                    .putString("release_url", release.releaseUrl)
                    .putBoolean("prerelease", release.prerelease);
        }
        e.apply();
    }
}
