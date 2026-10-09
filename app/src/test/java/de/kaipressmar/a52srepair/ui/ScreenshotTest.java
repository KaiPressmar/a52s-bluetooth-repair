package de.kaipressmar.a52srepair.ui;

import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import de.kaipressmar.a52srepair.R;
import de.kaipressmar.a52srepair.core.diagnosis.Fault;
import de.kaipressmar.a52srepair.core.report.CallOutcome;
import de.kaipressmar.a52srepair.core.report.CallReport;
import de.kaipressmar.a52srepair.data.CallReportRepository;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.EnumSet;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Renders every screen with sample data to app/build/screenshots for visual review. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34, qualifiers = "de-rDE-w393dp-h852dp-xxhdpi")
public class ScreenshotTest {
    private static final long HOUR = 60L * 60L * 1000L;

    @Before public void sampleData() {
        Application app = ApplicationProvider.getApplicationContext();
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT);
        Shadows.shadowOf(app.getSystemService(android.bluetooth.BluetoothManager.class).getAdapter())
                .setEnabled(true);
        CallReportRepository repo = new CallReportRepository(app);
        repo.clear();
        long now = System.currentTimeMillis();
        repo.add(new CallReport(now - 50 * HOUR, 312_000L, CallOutcome.HEALTHY,
                EnumSet.noneOf(Fault.class), 0, 0, true, false));
        repo.add(new CallReport(now - 30 * HOUR, 95_000L, CallOutcome.NOT_BLUETOOTH,
                EnumSet.noneOf(Fault.class), 0, 0, false, false));
        repo.add(new CallReport(now - 20 * HOUR, 41_000L, CallOutcome.LEFT_BLUETOOTH,
                EnumSet.noneOf(Fault.class), 0, 0, true, true));
        repo.add(new CallReport(now - 3 * HOUR, 248_000L, CallOutcome.REPAIRED,
                EnumSet.of(Fault.DOWNLINK_NOT_ON_BLUETOOTH), 2, 0, true, false));
    }

    @Test public void lightTheme() throws IOException {
        capture("light");
    }

    @Test @Config(qualifiers = "+night") public void darkTheme() throws IOException {
        capture("dark");
    }

    private void capture(String variant) throws IOException {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                BottomNavigationView nav = activity.findViewById(R.id.bottom_nav);
                int[] destinations = {R.id.nav_status, R.id.nav_history, R.id.nav_settings};
                String[] names = {"status", "history", "settings"};
                for (int i = 0; i < destinations.length; i++) {
                    nav.setSelectedItemId(destinations[i]);
                    Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
                    save(activity.getWindow().getDecorView(), names[i] + "-" + variant);
                }
            });
        }
    }

    private static void save(View root, String name) {
        root.measure(
                View.MeasureSpec.makeMeasureSpec(root.getWidth(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(root.getHeight(), View.MeasureSpec.EXACTLY));
        root.layout(0, 0, root.getWidth(), root.getHeight());
        Bitmap bitmap = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(bitmap));
        File dir = new File("build/screenshots");
        assertTrue(dir.exists() || dir.mkdirs());
        try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}
