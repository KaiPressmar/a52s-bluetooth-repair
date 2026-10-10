package de.kaipressmar.a52srepair.update;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;
import androidx.test.core.app.ApplicationProvider;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {34, 36})
public class UpdateRepositoryTest {
    private final Context context = ApplicationProvider.getApplicationContext();
    private final SharedPreferences prefs = context.getSharedPreferences("update-state", Context.MODE_PRIVATE);
    private static final String VERSION = "9.0.0";
    private static final String APK = UpdateRelease.expectedApkName(VERSION);

    private String releaseJson() {
        String base = UpdateInstaller.TRUSTED_PREFIX + "v" + VERSION + "/";
        return "[{\"tag_name\":\"v" + VERSION + "\",\"assets\":[{\"name\":\"" + APK
                + "\",\"browser_download_url\":\"" + base + APK + "\"},{\"name\":\"" + APK
                + ".sha256\",\"browser_download_url\":\"" + base + APK + ".sha256\"}]}]";
    }

    private void cache() {
        String base = UpdateInstaller.TRUSTED_PREFIX + "v" + VERSION + "/";
        prefs.edit().putString("version", VERSION).putString("apk_name", APK)
                .putString("apk_url", base + APK).putString("checksum_url", base + APK + ".sha256")
                .putLong("last_check", System.currentTimeMillis()).commit();
    }

    @Test public void successCachesUpdateAndThrottledCheckUsesCacheWithoutNetwork() {
        AtomicInteger fetches = new AtomicInteger();
        UpdateRepository repo = new UpdateRepository(context, Runnable::run, () -> {
            fetches.incrementAndGet(); return releaseJson();
        });
        AtomicReference<UpdateRelease> result = new AtomicReference<>();
        repo.check(true, (r, error) -> { assertEquals("", error); result.set(r); });
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(VERSION, result.get().version);
        repo.check(false, (r, error) -> assertEquals(VERSION, r.version));
        assertEquals(1, fetches.get());
        assertFalse(repo.due(repo.lastCheckAt() + UpdateRepository.CHECK_INTERVAL_MS - 1));
        assertTrue(repo.due(repo.lastCheckAt() + UpdateRepository.CHECK_INTERVAL_MS));
    }

    @Test public void networkFailureRetainsCacheAndUsesShortRetryInterval() {
        cache();
        UpdateRepository repo = new UpdateRepository(context, Runnable::run, () -> { throw new IOException("offline"); });
        AtomicReference<String> failure = new AtomicReference<>();
        repo.check(true, (r, error) -> { assertEquals(VERSION, r.version); failure.set(error); });
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("IOException", failure.get());
        assertFalse(repo.due(repo.lastCheckAt() + UpdateRepository.RETRY_INTERVAL_MS - 1));
        assertTrue(repo.due(repo.lastCheckAt() + UpdateRepository.RETRY_INTERVAL_MS));
    }

    @Test public void completeEmptyResultClearsObsoleteCacheAndClockRollbackDoesNotBlockChecks() {
        cache();
        UpdateRepository repo = new UpdateRepository(context, Runnable::run, () -> "[]");
        assertTrue(repo.due(repo.lastCheckAt() - 1));
        repo.check(true, (r, error) -> assertNull(r));
        shadowOf(Looper.getMainLooper()).idle();
        assertNull(repo.cachedNewerRelease());
        assertFalse(prefs.contains("apk_url"));
    }

    @Test public void corruptOrPrereleaseCacheIsNotOffered() {
        cache();
        UpdateRepository repo = new UpdateRepository(context, Runnable::run, () -> "[]");
        assertNotNull(repo.cachedNewerRelease());
        prefs.edit().putBoolean("prerelease", true).commit();
        assertNull(repo.cachedNewerRelease());
        prefs.edit().putBoolean("prerelease", false).putString("apk_name", "../outside.apk").commit();
        assertNull(repo.cachedNewerRelease());
        cache();
        prefs.edit().putString("apk_url", "https://example.test/app.apk").commit();
        assertNull(repo.cachedNewerRelease());
        cache();
        prefs.edit().putString("version", "0.1.0").putString("apk_name", UpdateRelease.expectedApkName("0.1.0")).commit();
        assertNull(repo.cachedNewerRelease());
    }

    @Test public void concurrentCheckDoesNotStartDuplicateNetworkRequest() {
        AtomicReference<Runnable> pending = new AtomicReference<>();
        AtomicInteger fetches = new AtomicInteger();
        UpdateRepository repo = new UpdateRepository(context, pending::set, () -> {
            fetches.incrementAndGet(); return "[]";
        });
        repo.check(true, (r, error) -> assertNull(r));
        try {
            repo.check(true, (r, error) -> assertNull(r));
            assertEquals(0, fetches.get());
        } finally {
            pending.get().run();
            shadowOf(Looper.getMainLooper()).idle();
        }
        assertEquals(1, fetches.get());
    }
}
