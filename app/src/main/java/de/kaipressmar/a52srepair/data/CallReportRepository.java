package de.kaipressmar.a52srepair.data;

import android.content.Context;
import de.kaipressmar.a52srepair.core.report.CallHistory;
import de.kaipressmar.a52srepair.core.report.CallReport;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Persists one line per call (newest first), bounded by {@link CallHistory#MAX_ENTRIES}. */
public final class CallReportRepository {
    private static final String FILE_NAME = "call-reports.txt";
    private static final Object LOCK = new Object();

    private final File file;

    public CallReportRepository(Context context) {
        file = new File(context.getApplicationContext().getFilesDir(), FILE_NAME);
    }

    public CallHistory history() {
        return new CallHistory(read());
    }

    public void add(CallReport report) {
        synchronized (LOCK) {
            write(CallHistory.prepend(read(), report));
        }
    }

    public void clear() {
        synchronized (LOCK) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    private List<CallReport> read() {
        synchronized (LOCK) {
            List<CallReport> out = new ArrayList<>();
            if (!file.exists()) return out;
            try {
                for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                    CallReport report = CallReport.decode(line);
                    if (report != null) out.add(report);
                }
            } catch (IOException ignored) {
                // A corrupt history must never break call handling.
            }
            return out;
        }
    }

    private void write(List<CallReport> reports) {
        StringBuilder text = new StringBuilder();
        for (CallReport report : reports) text.append(report.encode()).append('\n');
        File tmp = new File(file.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(text.toString().getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        } catch (IOException e) {
            return;
        }
        //noinspection ResultOfMethodCallIgnored
        tmp.renameTo(file);
    }
}
