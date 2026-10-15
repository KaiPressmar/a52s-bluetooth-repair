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
    private static final String FILE_NAME = "call-reports-v2.txt";
    private static final Object LOCK = new Object();

    private final Context context;
    private final File file;
    private final File legacy;

    public CallReportRepository(Context context) {
        this.context = context.getApplicationContext();
        file = new File(context.getApplicationContext().getFilesDir(), FILE_NAME);
        legacy = new File(context.getApplicationContext().getFilesDir(), "call-reports.txt");
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
            legacy.delete();
            new RecoveryPolicyRepository(context).clear();
        }
    }

    private List<CallReport> read() {
        synchronized (LOCK) {
            List<CallReport> out = new ArrayList<>();
            File source = file.exists() ? file : legacy;
            if (!source.exists()) return out;
            try {
                for (String line : Files.readAllLines(source.toPath(), StandardCharsets.UTF_8)) {
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
        if (!tmp.renameTo(file)) {
            de.kaipressmar.a52srepair.diagnostics.DiagnosticLog.log(
                    context, "REPORT atomic replacement failed");
        }
    }
}
