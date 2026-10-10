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
    static final long MAX_TEXT_BYTES = 2L * 1024 * 1024;
    static final long MAX_APK_BYTES = 64L * 1024 * 1024;
    interface ConnectionFactory { HttpURLConnection open(String url) throws IOException; }
    private static final ConnectionFactory CONNECTIONS = url -> (HttpURLConnection) new URL(url).openConnection();
    private HttpClient() {}

    static String fetchText(String url) throws IOException {
        return fetchText(url, CONNECTIONS);
    }

    static String fetchText(String url, ConnectionFactory connections) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        download(url, out, connections, MAX_TEXT_BYTES);
        return out.toString(StandardCharsets.UTF_8.name());
    }

    static void downloadTo(String url, File target) throws IOException {
        try (OutputStream out = new FileOutputStream(target)) {
            download(url, out, CONNECTIONS, MAX_APK_BYTES);
        }
    }

    static void download(String url, OutputStream out, ConnectionFactory connections, long maxBytes) throws IOException {
        HttpURLConnection connection = connections.open(url);
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(30_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "BluetoothRepair/" + BuildConfig.VERSION_NAME);
        connection.setRequestProperty(
                "Accept", "application/vnd.github+json, application/octet-stream");
        try {
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new IOException("HTTP " + code);
            if (connection.getContentLengthLong() > maxBytes) throw new IOException("Download too large");
            try (InputStream in = new BufferedInputStream(connection.getInputStream())) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                long total = 0L;
                while ((read = in.read(buffer)) != -1) {
                    total += read;
                    if (total > maxBytes) throw new IOException("Download too large");
                    out.write(buffer, 0, read);
                }
            }
        } finally {
            connection.disconnect();
        }
    }
}
