package de.kaipressmar.a52srepair.update;

import static org.junit.Assert.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class HttpClientTest {
    private static class Connection extends HttpURLConnection {
        final byte[] data;
        boolean disconnected, closed;
        int code = 200;
        long length = -1;
        Connection(byte[] data) throws Exception { super(new URL("https://example.test/")); this.data = data; }
        public void disconnect() { disconnected = true; }
        public boolean usingProxy() { return false; }
        public void connect() {}
        public int getResponseCode() { return code; }
        public long getContentLengthLong() { return length; }
        public InputStream getInputStream() {
            return new ByteArrayInputStream(data) {
                public void close() throws IOException { closed = true; super.close(); }
            };
        }
    }

    @Test public void textDownloadUsesUtf8TimeoutsAndClosesResources() throws Exception {
        Connection c = new Connection("Grüße".getBytes(StandardCharsets.UTF_8));
        assertEquals("Grüße", HttpClient.fetchText("https://example.test/", url -> c));
        assertEquals(10_000, c.getConnectTimeout());
        assertEquals(30_000, c.getReadTimeout());
        assertTrue(c.closed);
        assertTrue(c.disconnected);
    }

    @Test public void unsuccessfulHttpResponseDisconnectsWithoutReadingBody() throws Exception {
        Connection c = new Connection(new byte[0]);
        c.code = 503;
        try {
            HttpClient.fetchText("https://example.test/", url -> c);
            fail("must fail on HTTP 503");
        } catch (IOException expected) {
            assertEquals("HTTP 503", expected.getMessage());
            assertTrue(c.disconnected);
            assertFalse(c.closed);
        }
    }

    @Test public void sizeLimitAppliesWithAndWithoutDeclaredContentLength() throws Exception {
        for (long length : new long[] {-1, 20}) {
            Connection c = new Connection(new byte[20]);
            c.length = length;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                HttpClient.download("https://example.test/", out, url -> c, 10);
                fail("must reject an oversized download");
            } catch (IOException expected) {
                assertTrue(c.disconnected);
                assertEquals(0, out.size());
                if (length == -1) assertTrue(c.closed);
            }
        }
    }
}
