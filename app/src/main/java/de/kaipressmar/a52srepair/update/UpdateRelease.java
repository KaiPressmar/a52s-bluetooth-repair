package de.kaipressmar.a52srepair.update;

import de.kaipressmar.a52srepair.core.version.SemanticVersion;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** A GitHub release that contains the signed APK (and checksum) for this device flavor. */
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
    public static UpdateRelease selectNewest(
            String releasesJson, String deviceKey, String currentVersion) throws JSONException {
        JSONArray releases = new JSONArray(releasesJson);
        UpdateRelease best = null;

        for (int i = 0; i < releases.length(); i++) {
            JSONObject release = releases.getJSONObject(i);
            if (release.optBoolean("draft", false)) continue;

            String tag = release.optString("tag_name", "");
            String version = SemanticVersion.normalize(tag);
            if (version == null || SemanticVersion.compare(version, currentVersion) <= 0) continue;

            String expectedApk = expectedApkName(deviceKey, version);
            JSONArray assets = release.optJSONArray("assets");
            if (expectedApk == null || assets == null) continue;

            String apkUrl = null;
            String checksumUrl = null;
            for (int a = 0; a < assets.length(); a++) {
                JSONObject asset = assets.getJSONObject(a);
                String name = asset.optString("name", "");
                String url = asset.optString("browser_download_url", "");
                if (expectedApk.equals(name)) apkUrl = url;
                else if ((expectedApk + ".sha256").equals(name)) checksumUrl = url;
            }
            if (apkUrl == null || checksumUrl == null) continue;

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

    public static String expectedApkName(String deviceKey, String version) {
        if ("a52s".equalsIgnoreCase(deviceKey)) return "bluetooth-repair-a52s-5g-v" + version + ".apk";
        if ("s22".equalsIgnoreCase(deviceKey)) return "bluetooth-repair-galaxy-s22-v" + version + ".apk";
        return null;
    }
}
