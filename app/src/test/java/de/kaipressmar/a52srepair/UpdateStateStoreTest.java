package de.kaipressmar.a52srepair;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class UpdateStateStoreTest {
    private Context context;

    @Before public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        UpdateStateStore.clear(context);
    }

    @Test public void firstAutomaticCheckIsDueImmediately() {
        assertTrue(UpdateStateStore.shouldCheck(context, 1_000_000L));
    }

    @Test public void automaticChecksAreRateLimitedForTwelveHours() {
        long now = 1_000_000L;
        UpdateStateStore.saveCheck(context, null, null, now);

        assertFalse(
                UpdateStateStore.shouldCheck(
                        context,
                        now + UpdateStateStore.AUTO_CHECK_INTERVAL_MS - 1L));
        assertTrue(
                UpdateStateStore.shouldCheck(
                        context,
                        now + UpdateStateStore.AUTO_CHECK_INTERVAL_MS));
    }

    @Test public void failedChecksRetryAfterOneHour() {
        long now = 1_000_000L;
        UpdateStateStore.saveCheck(context, null, "offline", now);

        assertFalse(
                UpdateStateStore.shouldCheck(
                        context,
                        now + UpdateStateStore.FAILED_RETRY_INTERVAL_MS - 1L));
        assertTrue(
                UpdateStateStore.shouldCheck(
                        context,
                        now + UpdateStateStore.FAILED_RETRY_INTERVAL_MS));
    }

    @Test public void cachedReleaseSurvivesProcessRestartStyleRead() {
        UpdateRelease release =
                new UpdateRelease(
                        "0.8.0",
                        "v0.8.0",
                        "bluetooth-repair-a52s-5g-v0.8.0.apk",
                        "https://example.test/app.apk",
                        "https://example.test/app.sha256",
                        "https://example.test/release",
                        true);

        UpdateStateStore.saveCheck(context, release, null, 1234L);
        UpdateRelease cached = UpdateStateStore.cachedRelease(context);

        assertNotNull(cached);
        assertEquals("0.8.0", cached.version);
        assertEquals(release.apkName, cached.apkName);
        assertEquals(1234L, UpdateStateStore.lastCheckAt(context));
        assertEquals("", UpdateStateStore.lastError(context));
    }

    @Test public void networkErrorIsRememberedWithoutDestroyingCachedUpdate() {
        UpdateRelease release =
                new UpdateRelease(
                        "0.8.0",
                        "v0.8.0",
                        "bluetooth-repair-a52s-5g-v0.8.0.apk",
                        "https://example.test/app.apk",
                        "https://example.test/app.sha256",
                        "https://example.test/release",
                        true);

        UpdateStateStore.saveCheck(context, release, null, 1000L);
        UpdateStateStore.saveCheck(context, release, "HTTP 503", 2000L);

        assertNotNull(UpdateStateStore.cachedRelease(context));
        assertEquals("HTTP 503", UpdateStateStore.lastError(context));
    }
}
