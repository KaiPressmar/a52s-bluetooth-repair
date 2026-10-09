package de.kaipressmar.a52srepair.ui;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import androidx.core.content.ContextCompat;
import androidx.test.core.app.ApplicationProvider;
import de.kaipressmar.a52srepair.R;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * Renders the adaptive launcher icon like launchers do (squircle, circle, themed) to
 * app/build/screenshots. {@code icon-squircle-512.png} is the README artwork.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34)
public class IconRenderTest {
    private final Context context = ApplicationProvider.getApplicationContext();

    @Test public void renderLauncherIcon() throws IOException {
        save(render(512, false, false), "icon-squircle-512");
        save(render(512, true, false), "icon-circle-512");
        save(render(512, true, true), "icon-themed-512");
        save(render(144, false, false), "icon-squircle-144");
    }

    /** Adaptive icons draw a 108dp canvas and show the inner 72dp through the mask. */
    private Bitmap render(int size, boolean circle, boolean themed) {
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Path mask = new Path();
        if (circle) {
            mask.addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW);
        } else {
            mask.addRoundRect(new RectF(0, 0, size, size), size * 0.23f, size * 0.23f, Path.Direction.CW);
        }
        canvas.clipPath(mask);

        int full = Math.round(size * 108f / 72f);
        int offset = (size - full) / 2;
        Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
        background.setColor(themed
                ? Color.rgb(0xDC, 0xE1, 0xFF)
                : ContextCompat.getColor(context, R.color.ic_launcher_background));
        canvas.drawRect(0, 0, size, size, background);

        Drawable layer = ContextCompat.getDrawable(context,
                themed ? R.drawable.ic_launcher_monochrome : R.drawable.ic_launcher_foreground);
        assertTrue(layer != null);
        if (themed) layer.setColorFilter(new PorterDuffColorFilter(Color.rgb(0x1B, 0x2C, 0x6B), PorterDuff.Mode.SRC_IN));
        layer.setBounds(offset, offset, offset + full, offset + full);
        layer.draw(canvas);
        return bitmap;
    }

    private static void save(Bitmap bitmap, String name) throws IOException {
        File dir = new File("build/screenshots");
        assertTrue(dir.exists() || dir.mkdirs());
        try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }
}
