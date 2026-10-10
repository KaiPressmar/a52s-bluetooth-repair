package de.kaipressmar.a52srepair.update;

import de.kaipressmar.a52srepair.core.version.SemanticVersion;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** A GitHub release that contains the signed universal APK and its checksum. */
public final class UpdateRelease {
    public final String version;
    public final String tag;
    public final String apkName;
    public final String apkUrl;
    public final String checksumUrl;
    public final String releaseUrl;
    public final boolean prerelease;

    public UpdateRelease(
            String version,
            String tag,
            String apkName,
            String apkUrl,
            String checksumUrl,
            String releaseUrl,
            boolean prerelease) {
        this.version = version;
        this.tag = tag;
        this.apkName = apkName;
        this.apkUrl = apkUrl;
        this.checksumUrl = checksumUrl;
        this.releaseUrl = releaseUrl;
        this.prerelease = prerelease;
    }

    /** Newest release newer than {@code currentVersion} with a complete APK + checksum pair. */
    public static UpdateRelease selectNewest(String releasesJson, String currentVersion)
            throws JSONException {
        JSONArray releases = new JSONArray(releasesJson);
        UpdateRelease best = null;

        for (int i = 0; i < releases.length(); i++) {
            JSONObject release = releases.optJSONObject(i);
            if (release == null || release.optBoolean("draft", false)
                    || release.optBoolean("prerelease", false)) continue;

            String tag = release.optString("tag_name", "");
            String version = SemanticVersion.normalize(tag);
            if (version == null || !version.matches("[0-9]+\\.[0-9]+\\.[0-9]+")
                    || SemanticVersion.compare(version, currentVersion) <= 0) continue;

            String expectedApk = expectedApkName(version);
            JSONArray assets = release.optJSONArray("assets");
            if (assets == null) continue;

            String apkUrl = null;
            String checksumUrl = null;
            for (int a = 0; a < assets.length(); a++) {
                JSONObject asset = assets.optJSONObject(a);
                if (asset == null) continue;
                String name = asset.optString("name", "");
                String url = asset.optString("browser_download_url", "");
                if (expectedApk.equals(name)) apkUrl = url;
                else if ((expectedApk + ".sha256").equals(name)) checksumUrl = url;
            }
            if (!UpdateInstaller.isTrustedDownloadUrl(apkUrl)
                    || !UpdateInstaller.isTrustedDownloadUrl(checksumUrl)) continue;

            UpdateRelease candidate =
                    new UpdateRelease(
                            version,
                            tag,
                            expectedApk,
                            apkUrl,
                            checksumUrl,
                            release.optString("html_url", ""),
                            release.optBoolean("prerelease", false));
            if (best == null || SemanticVersion.compare(candidate.version, best.version) > 0) {
                best = candidate;
            }
        }
        return best;
    }

    /**
     * Universal APK name (since 0.17.0). Releases also carry copies under the former per-device
     * names so that 0.16.x installs of either variant update to the universal build.
     */
    public static String expectedApkName(String version) {
        return "bluetooth-repair-v" + version + ".apk";
    }
}
