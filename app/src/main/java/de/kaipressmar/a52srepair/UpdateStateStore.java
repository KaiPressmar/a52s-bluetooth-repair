package de.kaipressmar.a52srepair;

import android.content.Context;
import android.content.SharedPreferences;

final class UpdateStateStore {
    private static final String PREFS = "update-state";
    private static final String LAST_CHECK = "last_check";
    private static final String VERSION = "version";
    private static final String TAG = "tag";
    private static final String APK_NAME = "apk_name";
    private static final String APK_URL = "apk_url";
    private static final String CHECKSUM_URL = "checksum_url";
    private static final String RELEASE_URL = "release_url";
    private static final String PRERELEASE = "prerelease";
    private static final String ERROR = "error";

    static final long AUTO_CHECK_INTERVAL_MS = 12L * 60L * 60L * 1000L;

    private UpdateStateStore() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean shouldCheck(Context c, long now) {
        long last = prefs(c).getLong(LAST_CHECK, 0L);
        return last == 0L || now - last >= AUTO_CHECK_INTERVAL_MS;
    }

    static void saveCheck(Context c, UpdateRelease release, String error, long now) {
        SharedPreferences.Editor e =
                prefs(c)
                        .edit()
                        .putLong(LAST_CHECK, now)
                        .putString(ERROR, error == null ? "" : error);

        if (release == null) {
            e.remove(VERSION)
                    .remove(TAG)
                    .remove(APK_NAME)
                    .remove(APK_URL)
                    .remove(CHECKSUM_URL)
                    .remove(RELEASE_URL)
                    .remove(PRERELEASE);
        } else {
            e.putString(VERSION, release.version)
                    .putString(TAG, release.tag)
                    .putString(APK_NAME, release.apkName)
                    .putString(APK_URL, release.apkUrl)
                    .putString(CHECKSUM_URL, release.checksumUrl)
                    .putString(RELEASE_URL, release.releaseUrl)
                    .putBoolean(PRERELEASE, release.prerelease);
        }
        e.apply();
    }

    static UpdateRelease cachedRelease(Context c) {
        String version = prefs(c).getString(VERSION, null);
        String apk = prefs(c).getString(APK_URL, null);
        String checksum = prefs(c).getString(CHECKSUM_URL, null);
        if (version == null || apk == null || checksum == null) return null;

        return new UpdateRelease(
                version,
                prefs(c).getString(TAG, "v" + version),
                prefs(c).getString(APK_NAME, ""),
                apk,
                checksum,
                prefs(c).getString(RELEASE_URL, ""),
                prefs(c).getBoolean(PRERELEASE, false));
    }

    static long lastCheckAt(Context c) {
        return prefs(c).getLong(LAST_CHECK, 0L);
    }

    static String lastError(Context c) {
        return prefs(c).getString(ERROR, "");
    }

    static void clear(Context c) {
        prefs(c).edit().clear().apply();
    }
}
