package de.kaipressmar.a52srepair.update;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import de.kaipressmar.a52srepair.R;
import java.net.URI;
import java.security.MessageDigest;
import java.util.Locale;

/** Read-only release discovery and explicit browser handoff; no package installation privileges. */
public final class UpdateLinks {
    public interface Listener { void onStatus(String message, boolean error); }
    static final String TRUSTED_PREFIX =
            "https://github.com/KaiPressmar/a52s-bluetooth-repair/releases/download/";
    private UpdateLinks() {}

    public static void removeLegacyDownloads(Context context) {
        java.io.File directory = new java.io.File(context.getCacheDir(), "updates");
        java.io.File[] files = directory.listFiles();
        if (files == null) return;
        for (java.io.File file : files) if (file.isFile()) file.delete();
        directory.delete();
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

    @SuppressWarnings("deprecation")
    private static Signature[] signers(PackageInfo info) {
        return info.signingInfo == null ? info.signatures : info.signingInfo.getApkContentsSigners();
    }

    /** Certificate fingerprints are public identity evidence, not Play Protect verdicts. */
    public static String installedSignerSha256(Context context) {
        try {
            Signature[] certificates = signers(context.getPackageManager().getPackageInfo(context.getPackageName(),
                    PackageManager.GET_SIGNING_CERTIFICATES | PackageManager.GET_SIGNATURES));
            if (certificates == null || certificates.length == 0) return "unavailable";
            java.util.List<String> fingerprints = new java.util.ArrayList<>();
            for (Signature certificate : certificates) {
                byte[] hash = MessageDigest.getInstance("SHA-256").digest(certificate.toByteArray());
                StringBuilder hex = new StringBuilder();
                for (byte value : hash) hex.append(String.format(Locale.ROOT, "%02x", value & 0xff));
                fingerprints.add(hex.toString());
            }
            return String.join(",", fingerprints);
        } catch (Exception e) { return "unavailable"; }
    }

    /** Canonical project page; never navigate to an arbitrary API-provided html_url. */
    public static void openReleasePage(Activity activity, UpdateRelease release, Listener listener) {
        if (release == null || release.version == null || !release.version.matches("[0-9]+\\.[0-9]+\\.[0-9]+")) {
            listener.onStatus(activity.getString(R.string.update_untrusted), true);
            return;
        }
        if (activity.isFinishing() || activity.isDestroyed()) return;
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(
                    "https://github.com/KaiPressmar/a52s-bluetooth-repair/releases/tag/v" + release.version)));
        } catch (RuntimeException e) {
            listener.onStatus(activity.getString(R.string.update_browser_failed), true);
        }
    }

}
