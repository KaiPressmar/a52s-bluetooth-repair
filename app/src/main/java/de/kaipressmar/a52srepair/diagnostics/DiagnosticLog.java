package de.kaipressmar.a52srepair.diagnostics;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Small rotating diagnostic log (two files of at most 256 KB each). */
public final class DiagnosticLog {
    static final long MAX_BYTES = 256L * 1024L;
    private static final String FILE_NAME = "diagnostics.log";

    private DiagnosticLog() {}

    public static synchronized void log(Context context, String event) {
        File file = current(context);
        if (file.length() > MAX_BYTES) {
            File previous = previous(context);
            //noinspection ResultOfMethodCallIgnored
            previous.delete();
            //noinspection ResultOfMethodCallIgnored
            file.renameTo(previous);
            file = current(context);
        }
        try (Writer w =
                new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8)) {
            w.write(timestamp(System.currentTimeMillis()) + " | elapsedMs=" + android.os.SystemClock.elapsedRealtime()
                    + " " + event + "\n");
        } catch (IOException ignored) {
            // Diagnostics must never affect call handling.
        }
    }

    public static synchronized String readAll(Context context) {
        return read(previous(context)) + read(current(context));
    }

    public static synchronized void clear(Context context) {
        //noinspection ResultOfMethodCallIgnored
        current(context).delete();
        //noinspection ResultOfMethodCallIgnored
        previous(context).delete();
    }

    static String timestamp(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT).format(new Date(millis));
    }

    private static String read(File file) {
        if (!file.exists()) return "";
        try {
            return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private static File current(Context context) {
        return new File(context.getApplicationContext().getFilesDir(), FILE_NAME);
    }

    private static File previous(Context context) {
        return new File(context.getApplicationContext().getFilesDir(), FILE_NAME + ".1");
    }
}
