package de.kaipressmar.a52srepair.update;

import de.kaipressmar.a52srepair.BuildConfig;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Minimal HTTPS client for GitHub release metadata and assets. */
final class HttpClient {
    private HttpClient() {}

    static String fetchText(String url) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        download(url, out);
        return out.toString(StandardCharsets.UTF_8.name());
    }

    static void downloadTo(String url, File target) throws IOException {
        try (OutputStream out = new FileOutputStream(target)) {
            download(url, out);
        }
    }

    private static void download(String url, OutputStream out) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(30_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "BluetoothRepair/" + BuildConfig.VERSION_NAME);
        connection.setRequestProperty(
                "Accept", "application/vnd.github+json, application/octet-stream");
        try {
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new IOException("HTTP " + code);
            try (InputStream in = new BufferedInputStream(connection.getInputStream())) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            }
        } finally {
            connection.disconnect();
        }
    }
}
