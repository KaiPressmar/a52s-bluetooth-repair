package de.kaipressmar.a52srepair;

import org.json.JSONArray;
import org.json.JSONObject;

/** Parsed GitHub release that contains an APK for the active device flavor. */
final class UpdateRelease {
    final String version;
    final String tag;
    final String apkName;
    final String apkUrl;
    final String checksumUrl;
    final String releaseUrl;
    final boolean prerelease;

    UpdateRelease(
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

    static UpdateRelease selectNewest(
            String releasesJson,
            String deviceKey,
            String currentVersion) throws Exception {
        JSONArray releases = new JSONArray(releasesJson);
        UpdateRelease best = null;

        for (int i = 0; i < releases.length(); i++) {
            JSONObject release = releases.getJSONObject(i);
            if (release.optBoolean("draft", false)) continue;

            String tag = release.optString("tag_name", "");
            String version = normalizeVersion(tag);
            if (version == null || compareVersions(version, currentVersion) <= 0) continue;

            String expectedApk = expectedApkName(deviceKey, version);
            if (expectedApk == null) continue;

            String apkUrl = null;
            String checksumUrl = null;
            JSONArray assets = release.optJSONArray("assets");
            if (assets == null) continue;

            for (int a = 0; a < assets.length(); a++) {
                JSONObject asset = assets.getJSONObject(a);
                String name = asset.optString("name", "");
                String url = asset.optString("browser_download_url", "");
                if (expectedApk.equals(name)) {
                    apkUrl = url;
                } else if ((expectedApk + ".sha256").equals(name)) {
                    checksumUrl = url;
                }
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

            if (best == null || compareVersions(candidate.version, best.version) > 0) {
                best = candidate;
            }
        }

        return best;
    }

    static String expectedApkName(String deviceKey, String version) {
        if ("a52s".equalsIgnoreCase(deviceKey)) {
            return "bluetooth-repair-a52s-5g-v" + version + ".apk";
        }
        if ("s22".equalsIgnoreCase(deviceKey)) {
            return "bluetooth-repair-galaxy-s22-v" + version + ".apk";
        }
        return null;
    }

    static String normalizeVersion(String tag) {
        if (tag == null) return null;
        String v = tag.trim();
        if (v.startsWith("v") || v.startsWith("V")) v = v.substring(1);
        return v.matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:[-.][0-9A-Za-z.-]+)?") ? v : null;
    }

    static int compareVersions(String left, String right) {
        int[] a = numericParts(left);
        int[] b = numericParts(right);
        for (int i = 0; i < 3; i++) {
            if (a[i] != b[i]) return Integer.compare(a[i], b[i]);
        }

        boolean aPre = isPrerelease(left);
        boolean bPre = isPrerelease(right);
        if (aPre != bPre) return aPre ? -1 : 1;
        return left.compareToIgnoreCase(right);
    }

    private static int[] numericParts(String version) {
        String base = version == null ? "0.0.0" : version.split("[-]", 2)[0];
        String[] parts = base.split("\\.");
        int[] out = new int[] {0, 0, 0};
        for (int i = 0; i < Math.min(3, parts.length); i++) {
            try {
                out[i] = Integer.parseInt(parts[i].replaceAll("[^0-9].*$", ""));
            } catch (RuntimeException ignored) {
                out[i] = 0;
            }
        }
        return out;
    }

    private static boolean isPrerelease(String version) {
        return version != null && version.contains("-");
    }
}
