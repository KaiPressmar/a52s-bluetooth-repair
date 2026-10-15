package de.kaipressmar.a52srepair.data;

import android.content.Context;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Durable write-ahead lease. A dirty previous lease suppresses all mutations after rebind. */
public final class RecoveryJournalRepository {
    private static final Object LOCK = new Object();
    private final AtomicFile file;
    public RecoveryJournalRepository(Context context) {
        file = new AtomicFile(new File(context.getFilesDir(), "recovery-journal-v1.txt"));
    }
    public boolean interrupted() {
        synchronized (LOCK) {
            if (!file.getBaseFile().exists() && !new File(file.getBaseFile() + ".bak").exists()) return false;
            try { return !new String(file.readFully(), StandardCharsets.UTF_8).startsWith("1;CLOSED;"); }
            catch (IOException error) { return true; }
        }
    }
    public boolean reserve(String generation, int count, long firstAt) {
        return write("1;OPEN;" + generation + ";" + count + ";" + firstAt);
    }
    public boolean close(String generation) { return write("1;CLOSED;" + generation); }
    private boolean write(String value) {
        synchronized (LOCK) {
            FileOutputStream stream = null;
            try {
                stream = file.startWrite();
                stream.write(value.getBytes(StandardCharsets.UTF_8));
                file.finishWrite(stream);
                return value.equals(new String(file.readFully(), StandardCharsets.UTF_8));
            } catch (IOException error) {
                if (stream != null) file.failWrite(stream);
                return false;
            }
        }
    }
}
